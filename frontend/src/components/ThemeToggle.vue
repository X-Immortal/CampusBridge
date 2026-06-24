<script setup>
import { computed } from 'vue'
import { Moon, Sun, Monitor } from '@lucide/vue'
import { useTheme } from '../composables/useTheme'

const { theme, setTheme } = useTheme()

const icon = computed(() => {
  if (theme.value === 'dark') return Moon
  if (theme.value === 'light') return Sun
  return Monitor
})

const title = computed(() => {
  if (theme.value === 'dark') return '当前为夜间模式，点击切换'
  if (theme.value === 'light') return '当前为日间模式，点击切换'
  return '当前跟随系统，点击切换'
})

const order = ['light', 'dark', 'system']

function cycleTheme() {
  const next = order[(order.indexOf(theme.value) + 1) % order.length]
  setTheme(next)
}
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="title"
    :title="title"
    @click="cycleTheme"
  >
    <component
      :is="icon"
      :size="20"
      aria-hidden="true"
    />
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition:
    color var(--transition-base),
    border-color var(--transition-base),
    background-color var(--transition-base),
    box-shadow var(--transition-base);
}

.theme-toggle:hover {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  box-shadow: var(--shadow-focus);
}

.theme-toggle:focus-visible {
  outline: 3px solid var(--color-brand-glow);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .theme-toggle {
    width: 38px;
    height: 38px;
  }
}
</style>
