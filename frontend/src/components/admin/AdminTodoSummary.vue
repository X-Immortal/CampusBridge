<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronRight, Inbox, ListTodo, RefreshCw } from '@lucide/vue'

const props = defineProps({
  todos: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['navigate'])

const router = useRouter()

const items = computed(() => props.todos || [])

const hasItems = computed(() => items.value.some((item) => Number(item?.count) > 0))

const totalCount = computed(() =>
  items.value.reduce((acc, item) => acc + (Number(item?.count) || 0), 0)
)

function toneClass(type) {
  switch (type) {
    case 'VERIFICATION':
      return 'admin-todo-row--warning'
    case 'REPORT':
      return 'admin-todo-row--info'
    case 'DISPUTED_TASK':
      return 'admin-todo-row--danger'
    default:
      return 'admin-todo-row--brand'
  }
}

function navigate(item) {
  if (!item?.route) return
  if (item.route.startsWith('/')) {
    router.push(item.route).catch(() => {})
  }
  emit('navigate', item)
}
</script>

<template>
  <section class="admin-todo-summary">
    <header class="admin-todo-summary__header">
      <div>
        <p class="admin-todo-summary__kicker">
          待办摘要
        </p>
        <h3>管理员今日待处理事项</h3>
      </div>
      <span
        v-if="hasItems"
        class="admin-todo-summary__badge"
      >
        共 {{ totalCount }} 项待处理
      </span>
    </header>

    <div
      v-if="loading && !items.length"
      class="admin-todo-summary__loading"
      role="status"
    >
      <RefreshCw
        class="spinning"
        :size="22"
        aria-hidden="true"
      />
      <span>正在拉取待办事项...</span>
    </div>

    <div
      v-else-if="!items.length"
      class="admin-todo-summary__empty"
    >
      <Inbox
        :size="28"
        aria-hidden="true"
      />
      <p>暂无待办事项。</p>
    </div>

    <ul
      v-else
      class="admin-todo-summary__list"
    >
      <li
        v-for="item in items"
        :key="item.type"
        class="admin-todo-row"
        :class="toneClass(item.type)"
      >
        <button
          type="button"
          class="admin-todo-row__btn"
          @click="navigate(item)"
        >
          <div class="admin-todo-row__copy">
            <ListTodo
              :size="18"
              aria-hidden="true"
            />
            <span class="admin-todo-row__label">{{ item.label }}</span>
          </div>
          <div class="admin-todo-row__meta">
            <strong>{{ item.count }}</strong>
            <ChevronRight
              :size="16"
              aria-hidden="true"
            />
          </div>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.admin-todo-summary {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.admin-todo-summary__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.admin-todo-summary__kicker {
  margin: 0 0 4px;
  color: var(--color-brand);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.admin-todo-summary h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.05rem;
  font-weight: 900;
}

.admin-todo-summary__badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
  font-size: 0.78rem;
  font-weight: 800;
}

.admin-todo-summary__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.admin-todo-row__btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-bg);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.admin-todo-row__btn:hover {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  box-shadow: 0 4px 12px var(--shadow-card);
}

.admin-todo-row__copy {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text);
  font-size: 0.92rem;
  font-weight: 800;
}

.admin-todo-row__meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text);
}

.admin-todo-row__meta strong {
  font-size: 1.2rem;
  font-weight: 900;
}

.admin-todo-row--warning .admin-todo-row__copy svg {
  color: var(--color-warning);
}

.admin-todo-row--info .admin-todo-row__copy svg {
  color: var(--color-info);
}

.admin-todo-row--danger .admin-todo-row__copy svg {
  color: var(--color-danger);
}

.admin-todo-row--brand .admin-todo-row__copy svg {
  color: var(--color-brand);
}

.admin-todo-row--warning .admin-todo-row__meta strong {
  color: var(--color-warning);
}

.admin-todo-row--info .admin-todo-row__meta strong {
  color: var(--color-info);
}

.admin-todo-row--danger .admin-todo-row__meta strong {
  color: var(--color-danger);
}

.admin-todo-row--brand .admin-todo-row__meta strong {
  color: var(--color-brand);
}

.admin-todo-summary__loading,
.admin-todo-summary__empty {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 160px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  text-align: center;
}

.admin-todo-summary__empty p {
  margin: 0;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .admin-todo-summary {
    padding: 16px;
  }
  .admin-todo-summary__header {
    flex-direction: column;
  }
}
</style>
