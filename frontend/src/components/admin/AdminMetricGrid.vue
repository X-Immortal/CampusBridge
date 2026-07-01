<script setup>
import { computed } from 'vue'
import {
  BadgeCheck,
  CheckCircle2,
  IdCard,
  Scale,
  ShieldAlert,
  Users,
  ScrollText,
  ListChecks
} from '@lucide/vue'

const props = defineProps({
  summary: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const metrics = computed(() => {
  const summary = props.summary || {}
  return [
    {
      key: 'userTotal',
      label: '用户总数',
      value: numberOrZero(summary.userTotal),
      icon: Users,
      tone: 'brand'
    },
    {
      key: 'verifiedUserTotal',
      label: '已认证用户',
      value: numberOrZero(summary.verifiedUserTotal),
      icon: BadgeCheck,
      tone: 'success'
    },
    {
      key: 'taskTotal',
      label: '任务总数',
      value: numberOrZero(summary.taskTotal),
      icon: ScrollText,
      tone: 'info'
    },
    {
      key: 'activeTaskTotal',
      label: '进行中任务',
      value: numberOrZero(summary.activeTaskTotal),
      icon: ListChecks,
      tone: 'accent'
    },
    {
      key: 'completedTaskTotal',
      label: '已完成任务',
      value: numberOrZero(summary.completedTaskTotal),
      icon: CheckCircle2,
      tone: 'success'
    },
    {
      key: 'postTotal',
      label: '帖子总数',
      value: numberOrZero(summary.postTotal),
      icon: ScrollText,
      tone: 'info'
    },
    {
      key: 'pendingVerificationTotal',
      label: '待认证申请',
      value: numberOrZero(summary.pendingVerificationTotal),
      icon: IdCard,
      tone: 'warning'
    },
    {
      key: 'pendingReportTotal',
      label: '待处理举报',
      value: numberOrZero(summary.pendingReportTotal),
      icon: Scale,
      tone: 'warning'
    },
    {
      key: 'disputedTaskTotal',
      label: '争议任务',
      value: numberOrZero(summary.disputedTaskTotal),
      icon: ShieldAlert,
      tone: 'danger'
    }
  ]
})

function numberOrZero(value) {
  if (value === null || value === undefined || value === '') return 0
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}
</script>

<template>
  <div
    class="admin-metric-grid"
    :class="{ 'admin-metric-grid--loading': loading && !summary }"
  >
    <article
      v-for="metric in metrics"
      :key="metric.key"
      class="admin-metric-card"
      :class="`admin-metric-card--${metric.tone}`"
    >
      <div class="admin-metric-card__icon">
        <component
          :is="metric.icon"
          :size="20"
          aria-hidden="true"
        />
      </div>
      <div class="admin-metric-card__body">
        <span class="admin-metric-card__label">{{ metric.label }}</span>
        <strong class="admin-metric-card__value">
          <template v-if="loading && !summary">
            <span class="admin-metric-card__skeleton">--</span>
          </template>
          <template v-else>
            {{ metric.value }}
          </template>
        </strong>
      </div>
    </article>
  </div>
</template>

<style scoped>
.admin-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.admin-metric-grid--loading {
  opacity: 0.85;
}

.admin-metric-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-height: 86px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 12px 24px var(--shadow-card);
}

.admin-metric-card__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.admin-metric-card__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.admin-metric-card__label {
  color: var(--color-text-muted);
  font-size: 0.82rem;
  font-weight: 700;
}

.admin-metric-card__value {
  color: var(--color-text);
  font-size: 1.55rem;
  font-weight: 900;
  line-height: 1.1;
}

.admin-metric-card__skeleton {
  display: inline-block;
  min-width: 32px;
  color: var(--color-text-muted);
  font-weight: 800;
}

.admin-metric-card--brand .admin-metric-card__icon {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.admin-metric-card--success .admin-metric-card__icon {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.admin-metric-card--info .admin-metric-card__icon {
  background: var(--color-info-bg);
  color: var(--color-info);
}

.admin-metric-card--accent .admin-metric-card__icon {
  background: var(--color-accent-bg);
  color: var(--color-accent);
}

.admin-metric-card--warning .admin-metric-card__icon {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.admin-metric-card--danger .admin-metric-card__icon {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

@media (max-width: 720px) {
  .admin-metric-card {
    min-height: 76px;
    padding: 12px 14px;
  }
  .admin-metric-card__value {
    font-size: 1.35rem;
  }
}
</style>
