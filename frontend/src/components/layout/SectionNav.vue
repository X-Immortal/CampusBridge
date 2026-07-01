<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useAuth } from '../../stores/auth'
import { canShowNavItem } from '../../app/navigation'

const props = defineProps({
  items: {
    type: Array,
    required: true
  },
  activeKey: {
    type: String,
    default: ''
  },
  variant: {
    type: String,
    default: 'tabs',
    validator: (value) => ['tabs', 'rail', 'compact'].includes(value)
  },
  ariaLabel: {
    type: String,
    default: '二级导航'
  }
})

const route = useRoute()
const auth = useAuth()

const visibleItems = computed(() => props.items.filter((item) => canShowNavItem(item, auth)))

const activeItemKey = computed(() => {
  if (props.activeKey) return props.activeKey
  const matched = visibleItems.value.find((item) => {
    if (!item.to) return false
    if (item.key && route.meta?.subSection === item.key) return true
    if (route.path === item.to) return true
    if (typeof item.to === 'string' && item.to !== '/' && route.path.startsWith(item.to)) return true
    return false
  })
  return matched?.key || ''
})

function itemClasses(item) {
  const isActive = activeItemKey.value === item.key
  return {
    'section-nav__item': true,
    'section-nav__item--active': isActive,
    'section-nav__item--disabled': item.disabled || item.locked
  }
}

function isActive(item) {
  return activeItemKey.value === item.key
}
</script>

<template>
  <nav
    class="section-nav"
    :class="`section-nav--${variant}`"
    :aria-label="ariaLabel"
  >
    <component
      :is="item.disabled || item.locked ? 'span' : RouterLink"
      v-for="item in visibleItems"
      :key="item.key"
      :class="itemClasses(item)"
      :to="item.disabled || item.locked ? undefined : item.to"
      :aria-current="isActive(item) ? 'page' : undefined"
      :aria-disabled="item.disabled || item.locked ? 'true' : undefined"
      :title="item.description"
    >
      <component
        :is="item.icon"
        v-if="item.icon && (variant === 'rail' || variant === 'compact')"
        :size="variant === 'rail' ? 20 : 18"
        aria-hidden="true"
      />
      <span class="section-nav__label">{{ item.label }}</span>
      <span
        v-if="item.description && variant === 'rail'"
        class="section-nav__description"
      >{{ item.description }}</span>
      <span
        v-if="item.badge !== undefined && item.badge !== null && item.badge !== 0"
        class="section-nav__badge"
      >{{ item.badge > 99 ? '99+' : item.badge }}</span>
    </component>
  </nav>
</template>

<style scoped>
.section-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* tabs: 顶部胶囊式 Tab */
.section-nav--tabs {
  flex-wrap: wrap;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  width: fit-content;
  max-width: 100%;
}

.section-nav--tabs .section-nav__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 900;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.section-nav--tabs .section-nav__item:hover,
.section-nav--tabs .section-nav__item:focus-visible {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  outline: 0;
}

.section-nav--tabs .section-nav__item--active {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  box-shadow: inset 0 -2px 0 0 var(--color-brand);
}

.section-nav--tabs .section-nav__item--disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* rail: 局部侧栏导航 */
.section-nav--rail {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.section-nav--rail .section-nav__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.96rem;
  font-weight: 900;
  text-decoration: none;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.section-nav--rail .section-nav__description {
  grid-column: 2;
  color: var(--color-text-muted);
  font-size: 0.78rem;
  font-weight: 600;
}

.section-nav--rail .section-nav__item:hover,
.section-nav--rail .section-nav__item:focus-visible {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  outline: 0;
}

.section-nav--rail .section-nav__item--active {
  position: relative;
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.section-nav--rail .section-nav__item--active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--color-brand);
}

.section-nav--rail .section-nav__item--disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* compact: 横向滚动分段控件 */
.section-nav--compact {
  flex-wrap: nowrap;
  overflow-x: auto;
  scrollbar-width: thin;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.section-nav--compact .section-nav__item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 900;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.section-nav--compact .section-nav__item:hover,
.section-nav--compact .section-nav__item:focus-visible {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  outline: 0;
}

.section-nav--compact .section-nav__item--active {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.section-nav--compact .section-nav__item--disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.section-nav__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-brand);
  color: var(--color-surface);
  font-size: 0.72rem;
  font-weight: 800;
}

@media (max-width: 760px) {
  .section-nav--tabs,
  .section-nav--rail {
    flex-wrap: nowrap;
    overflow-x: auto;
    width: 100%;
  }

  .section-nav--tabs .section-nav__item,
  .section-nav--rail .section-nav__item {
    flex: 0 0 auto;
  }

  .section-nav--rail {
    flex-direction: row;
    align-items: center;
  }

  .section-nav--rail .section-nav__description {
    display: none;
  }

  .section-nav--rail .section-nav__item--active::before {
    top: auto;
    right: 8px;
    bottom: 4px;
    left: 8px;
    width: auto;
    height: 3px;
  }
}
</style>
