<script setup>
import { ArrowUp } from '@lucide/vue'
import { useScrollTop } from '../composables/useScrollTop'

const { visible, scrollToTop } = useScrollTop()
</script>

<template>
  <Transition name="back-to-top">
    <button
      v-if="visible"
      class="back-to-top"
      type="button"
      aria-label="回到顶部"
      @click="scrollToTop"
    >
      <ArrowUp
        :size="22"
        aria-hidden="true"
      />
    </button>
  </Transition>
</template>

<style scoped>
.back-to-top {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 50;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: 1px solid var(--color-brand-border);
  border-radius: 50%;
  background: var(--color-brand);
  color: var(--color-text-on-primary);
  cursor: pointer;
  box-shadow: var(--shadow-elevated);
  transition:
    background-color var(--transition-base),
    border-color var(--transition-base),
    transform var(--transition-base);
}

.back-to-top:hover,
.back-to-top:focus-visible {
  background: var(--color-brand-hover);
  border-color: var(--color-brand-border-hover);
  transform: translateY(-2px);
  outline: 0;
}

.back-to-top:focus-visible {
  outline: 3px solid var(--color-brand-glow);
  outline-offset: 2px;
}

@media (max-width: 760px) {
  .back-to-top {
    right: 16px;
    bottom: 16px;
    width: 42px;
    height: 42px;
  }
}

.back-to-top-enter-active,
.back-to-top-leave-active {
  transition:
    opacity var(--transition-base),
    transform var(--transition-base);
}

.back-to-top-enter-from,
.back-to-top-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@media (prefers-reduced-motion: reduce) {
  .back-to-top-enter-active,
  .back-to-top-leave-active {
    transition: none;
  }
}
</style>
