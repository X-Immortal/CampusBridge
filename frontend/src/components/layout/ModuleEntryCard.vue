<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  to: {
    type: [String, Object],
    default: ''
  },
  icon: {
    type: Object,
    default: null
  },
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  active: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const tag = computed(() => {
  if (props.disabled || !props.to) return 'span'
  return RouterLink
})
</script>

<template>
  <component
    :is="tag"
    class="module-entry-card"
    :class="{ 'is-active': active, 'is-disabled': disabled }"
    :to="disabled ? undefined : to"
    :aria-current="active ? 'page' : undefined"
    :aria-disabled="disabled ? 'true' : undefined"
  >
    <span class="module-entry-card__icon">
      <component
        :is="icon"
        v-if="icon"
        :size="21"
        aria-hidden="true"
      />
    </span>
    <span class="module-entry-card__body">
      <strong class="module-entry-card__title">{{ title }}</strong>
      <span class="module-entry-card__desc">{{ description }}</span>
    </span>
  </component>
</template>

<style scoped>
.module-entry-card {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-content: start;
  gap: 12px;
  min-height: 116px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card, 8px);
  background: var(--color-surface);
  color: var(--color-text);
  text-decoration: none;
  box-shadow: none;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease,
    color 180ms ease;
}

.module-entry-card:hover:not(.is-disabled) {
  border-color: color-mix(in srgb, var(--color-brand) 34%, var(--color-border));
  background: color-mix(in srgb, var(--color-surface) 96%, var(--color-brand-soft));
  box-shadow: var(--shadow-card);
}

.module-entry-card.is-active {
  border-color: color-mix(in srgb, var(--color-brand) 50%, var(--color-border));
  background: color-mix(in srgb, var(--color-surface) 92%, var(--color-brand-soft));
}

.module-entry-card.is-active::before {
  content: "";
  position: absolute;
  top: 12px;
  left: 12px;
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: var(--color-brand);
}

.module-entry-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.module-entry-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.module-entry-card__body {
  display: grid;
  align-content: start;
  gap: 5px;
}

.module-entry-card__title {
  margin: 0;
  color: var(--color-text);
  font-size: 0.98rem;
  font-weight: 800;
  line-height: 1.3;
}

.module-entry-card__desc {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.84rem;
  line-height: 1.5;
}
</style>
