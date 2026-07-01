<script setup>
import { computed } from 'vue'
import { ChartPie, TriangleAlert } from '@lucide/vue'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  kicker: {
    type: String,
    default: ''
  },
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  emptyHint: {
    type: String,
    default: '暂无数据'
  }
})

const total = computed(() =>
  props.data.reduce((acc, item) => acc + (Number(item?.count) || 0), 0)
)

const sortedData = computed(() => {
  return [...props.data].sort((a, b) => (Number(b?.count) || 0) - (Number(a?.count) || 0))
})

const topItem = computed(() => sortedData.value[0] || null)

const hasData = computed(() => sortedData.value.some((item) => Number(item?.count) > 0))

function percent(value) {
  if (!total.value) return 0
  return Math.round((Number(value) || 0) / total.value * 100)
}

function toneClass(index) {
  const tones = ['brand', 'info', 'success', 'warning', 'danger', 'accent']
  return `admin-dist-row--${tones[index % tones.length]}`
}
</script>

<template>
  <section class="admin-distribution-chart">
    <header class="admin-distribution-chart__header">
      <div>
        <p
          v-if="kicker"
          class="admin-distribution-chart__kicker"
        >
          {{ kicker }}
        </p>
        <h3>{{ title }}</h3>
      </div>
      <span
        v-if="topItem"
        class="admin-distribution-chart__badge"
      >
        最高：{{ topItem.label }} · {{ topItem.count }}
      </span>
    </header>

    <div
      v-if="loading && !hasData"
      class="admin-distribution-chart__loading"
      role="status"
    >
      <ChartPie
        :size="24"
        aria-hidden="true"
      />
      <span>正在加载分布...</span>
    </div>

    <div
      v-else-if="!hasData"
      class="admin-distribution-chart__empty"
    >
      <TriangleAlert
        :size="28"
        aria-hidden="true"
      />
      <p>{{ emptyHint }}</p>
    </div>

    <ul
      v-else
      class="admin-distribution-chart__list"
    >
      <li
        v-for="(item, index) in sortedData"
        :key="item.status || item.label || index"
        class="admin-dist-row"
        :class="toneClass(index)"
      >
        <div class="admin-dist-row__head">
          <span class="admin-dist-row__label">{{ item.label || item.status || '未分类' }}</span>
          <strong class="admin-dist-row__value">
            {{ item.count }}
            <em>{{ percent(item.count) }}%</em>
          </strong>
        </div>
        <div
          class="admin-dist-row__track"
          aria-hidden="true"
        >
          <i :style="{ width: `${percent(item.count)}%` }" />
        </div>
      </li>
    </ul>

    <footer
      v-if="hasData"
      class="admin-distribution-chart__footer"
    >
      <span>合计</span>
      <strong>{{ total }}</strong>
    </footer>
  </section>
</template>

<style scoped>
.admin-distribution-chart {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.admin-distribution-chart__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.admin-distribution-chart__kicker {
  margin: 0 0 4px;
  color: var(--color-brand);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.admin-distribution-chart h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.05rem;
  font-weight: 900;
}

.admin-distribution-chart__badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.78rem;
  font-weight: 800;
}

.admin-distribution-chart__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.admin-dist-row {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-bg);
}

.admin-dist-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-text);
  font-size: 0.88rem;
  font-weight: 800;
}

.admin-dist-row__value {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  color: var(--color-text);
  font-size: 0.95rem;
  font-weight: 900;
}

.admin-dist-row__value em {
  font-style: normal;
  color: var(--color-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
}

.admin-dist-row__track {
  position: relative;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: var(--color-divider);
  overflow: hidden;
}

.admin-dist-row__track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
  transition: width 200ms ease;
}

.admin-dist-row--brand .admin-dist-row__track i {
  background: var(--color-brand);
}

.admin-dist-row--info .admin-dist-row__track i {
  background: var(--color-info);
}

.admin-dist-row--success .admin-dist-row__track i {
  background: var(--color-success);
}

.admin-dist-row--warning .admin-dist-row__track i {
  background: var(--color-warning);
}

.admin-dist-row--danger .admin-dist-row__track i {
  background: var(--color-danger);
}

.admin-dist-row--accent .admin-dist-row__track i {
  background: var(--color-accent);
}

.admin-distribution-chart__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--color-divider);
  color: var(--color-text-muted);
  font-size: 0.86rem;
  font-weight: 800;
}

.admin-distribution-chart__footer strong {
  color: var(--color-text);
  font-size: 1rem;
  font-weight: 900;
}

.admin-distribution-chart__loading,
.admin-distribution-chart__empty {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 200px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  text-align: center;
}

.admin-distribution-chart__empty p {
  margin: 0;
}

@media (max-width: 720px) {
  .admin-distribution-chart {
    padding: 16px;
  }
  .admin-distribution-chart__header {
    flex-direction: column;
  }
}
</style>
