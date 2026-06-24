<script setup>
import { X } from '@lucide/vue'
import { useToast } from '../composables/useToast'

const { toasts, closeToast } = useToast()

const typeIcon = {
  success: '✓',
  warning: '▲',
  danger: '✕',
  info: 'ℹ'
}
</script>

<template>
  <Teleport to="body">
    <div
      class="toast-container"
      role="region"
      aria-label="通知"
    >
      <TransitionGroup name="toast">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="toast-item"
          :class="`toast-item--${toast.type}`"
          role="status"
          aria-live="polite"
        >
          <span
            class="toast-icon"
            aria-hidden="true"
          >{{ typeIcon[toast.type] || typeIcon.info }}</span>
          <span class="toast-message">{{ toast.message }}</span>
          <button
            v-if="toast.closable"
            class="toast-close"
            type="button"
            aria-label="关闭通知"
            @click="closeToast(toast.id)"
          >
            <X
              :size="16"
              aria-hidden="true"
            />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 80;
  display: grid;
  gap: 10px;
  width: min(360px, calc(100vw - 32px));
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-elevated);
  pointer-events: auto;
  transition:
    background-color var(--transition-base),
    border-color var(--transition-base),
    color var(--transition-base);
}

.toast-item--info {
  border-color: var(--color-info-soft);
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.toast-item--success {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.toast-item--warning {
  border-color: var(--color-warning-soft);
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.toast-item--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.toast-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 0.82rem;
  font-weight: 800;
  line-height: 1;
  background: currentColor;
  color: inherit;
}

.toast-icon::before {
  content: '';
}

.toast-message {
  flex: 1;
  line-height: 1.5;
  font-size: 0.95rem;
  font-weight: 600;
}

.toast-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: currentColor;
  cursor: pointer;
  opacity: 0.7;
  transition:
    background-color var(--transition-base),
    opacity var(--transition-base);
}

.toast-close:hover,
.toast-close:focus-visible {
  background: rgba(0, 0, 0, 0.08);
  opacity: 1;
  outline: 0;
}

.toast-enter-active,
.toast-leave-active,
.toast-move {
  transition:
    opacity var(--transition-base),
    transform var(--transition-base);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

@media (max-width: 760px) {
  .toast-container {
    top: 12px;
    right: 50%;
    transform: translateX(50%);
    width: min(340px, calc(100vw - 24px));
  }

  .toast-item {
    padding: 10px 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active,
  .toast-move {
    transition: none;
  }
}
</style>
