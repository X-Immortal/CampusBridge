<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, LayoutGrid, Monitor, Moon, Palette, Sun, X } from '@lucide/vue'
import { useTheme } from '../composables/useTheme'
import { usePreferences } from '../composables/usePreferences'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:open'])

const panelRef = ref(null)
const { theme, setTheme } = useTheme()
const { accent, density, setAccent, setDensity } = usePreferences()

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

const accents = [
  { key: 'purple', label: '默认紫', color: '#4f46e5' },
  { key: 'blue', label: '清新蓝', color: '#0369a1' },
  { key: 'orange', label: '活力橙', color: '#ea580c' },
  { key: 'green', label: '自然绿', color: '#15803d' }
]

const densities = [
  { key: 'compact', label: '紧凑', hint: '更小间距' },
  { key: 'comfortable', label: '舒适', hint: '默认' },
  { key: 'spacious', label: '宽松', hint: '更大间距' }
]

const modes = [
  { key: 'light', label: '日间', icon: Sun },
  { key: 'dark', label: '夜间', icon: Moon },
  { key: 'system', label: '跟随系统', icon: Monitor }
]

function closePanel() {
  isOpen.value = false
}

function handleDocumentPointerDown(event) {
  if (!isOpen.value || !panelRef.value) return
  if (!panelRef.value.contains(event.target)) closePanel()
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') closePanel()
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <div
    ref="panelRef"
    class="theme-preferences"
  >
    <Transition name="fade">
      <div
        v-if="isOpen"
        class="preferences-dropdown"
        role="dialog"
        aria-label="外观设置"
        @click.stop
      >
        <div class="preferences-head">
          <Palette
            :size="18"
            aria-hidden="true"
          />
          <span>外观设置</span>
          <button
            class="preferences-close"
            type="button"
            aria-label="关闭偏好面板"
            @click="closePanel"
          >
            <X
              :size="16"
              aria-hidden="true"
            />
          </button>
        </div>

        <div class="preferences-section">
          <span class="preferences-label">
            <Sun
              :size="14"
              aria-hidden="true"
            />
            外观模式
          </span>
          <div class="preferences-options">
            <button
              v-for="m in modes"
              :key="m.key"
              type="button"
              class="preference-option preference-option--mode"
              :class="{ active: theme === m.key }"
              :aria-label="m.label"
              :aria-pressed="theme === m.key"
              @click="setTheme(m.key)"
            >
              <component
                :is="m.icon"
                :size="16"
                aria-hidden="true"
              />
              <span>{{ m.label }}</span>
              <Check
                v-if="theme === m.key"
                class="check-mark"
                :size="14"
                aria-hidden="true"
              />
            </button>
          </div>
        </div>

        <div class="preferences-section">
          <span class="preferences-label">
            <Palette
              :size="14"
              aria-hidden="true"
            />
            强调色
          </span>
          <div class="preferences-options preferences-options--swatches">
            <button
              v-for="a in accents"
              :key="a.key"
              type="button"
              class="preference-swatch"
              :class="{ active: accent === a.key }"
              :style="{ '--swatch-color': a.color }"
              :aria-label="a.label"
              :aria-pressed="accent === a.key"
              @click="setAccent(a.key)"
            >
              <span
                class="swatch-dot"
                :style="{ backgroundColor: a.color }"
              />
              <span class="swatch-label">{{ a.label }}</span>
              <Check
                v-if="accent === a.key"
                class="check-mark"
                :size="14"
                aria-hidden="true"
              />
            </button>
          </div>
        </div>

        <div class="preferences-section">
          <span class="preferences-label">
            <LayoutGrid
              :size="14"
              aria-hidden="true"
            />
            界面密度
          </span>
          <div class="preferences-options">
            <button
              v-for="d in densities"
              :key="d.key"
              type="button"
              class="preference-option preference-option--density"
              :class="{ active: density === d.key }"
              :aria-label="d.label"
              :aria-pressed="density === d.key"
              @click="setDensity(d.key)"
            >
              <span class="density-preview">A</span>
              <span class="density-label">{{ d.label }}</span>
              <small class="density-hint">{{ d.hint }}</small>
              <Check
                v-if="density === d.key"
                class="check-mark"
                :size="14"
                aria-hidden="true"
              />
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.theme-preferences {
  position: relative;
}

.preferences-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 60;
  display: grid;
  gap: 10px;
  width: min(280px, calc(100vw - 32px));
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  box-shadow: var(--shadow-dropdown);
  color: var(--color-text);
}

.preferences-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px 10px;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.98rem;
  font-weight: 800;
  color: var(--color-primary);
}

.preferences-head span {
  flex: 1;
}

.preferences-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  transition:
    background-color var(--transition-base),
    color var(--transition-base);
}

.preferences-close:hover,
.preferences-close:focus-visible {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.preferences-section {
  display: grid;
  gap: 8px;
}

.preferences-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 4px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
}

.preferences-options {
  display: grid;
  gap: 6px;
}

.preferences-options--swatches {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.preference-option,
.preference-swatch {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.94rem;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--transition-base),
    background-color var(--transition-base),
    color var(--transition-base);
}

.preference-option:hover,
.preference-swatch:hover,
.preference-option:focus-visible,
.preference-swatch:focus-visible {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  outline: 0;
}

.preference-option.active,
.preference-swatch.active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.preference-option--mode {
  gap: 8px;
}

.preference-option--density {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 4px 10px;
  padding: 8px 10px;
}

.density-preview {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--radius-control);
  background: var(--color-surface-muted);
  color: var(--color-brand);
  font-weight: 800;
  grid-row: span 2;
}

.density-label {
  grid-column: 2;
  grid-row: 1;
}

.density-hint {
  grid-column: 2;
  grid-row: 2;
  color: var(--color-text-muted);
  font-size: 0.78rem;
  font-weight: 500;
}

.preference-swatch {
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 64px;
  padding: 8px;
}

.swatch-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.25);
}

.swatch-label {
  font-size: 0.82rem;
}

.check-mark {
  margin-left: auto;
  color: var(--color-brand);
}

.preference-option--density .check-mark {
  grid-column: 3;
  grid-row: span 2;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-base), transform var(--transition-base);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 760px) {
  .preferences-dropdown {
    right: -70px;
    width: min(260px, calc(100vw - 32px));
  }
}

@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
}
</style>
