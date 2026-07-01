<script setup>
import { computed } from 'vue'
import { LineChart, TriangleAlert } from '@lucide/vue'

const props = defineProps({
  trends: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const CHART_WIDTH = 320
const CHART_HEIGHT = 160
const PADDING_X = 24
const PADDING_Y = 20

const series = computed(() => {
  const trends = props.trends
  if (!trends || !Array.isArray(trends.days) || trends.days.length === 0) {
    return null
  }
  return [
    {
      key: 'newUsers',
      label: '新增用户',
      values: trends.newUsers || [],
      color: 'var(--color-brand)'
    },
    {
      key: 'newTasks',
      label: '新增任务',
      values: trends.newTasks || [],
      color: 'var(--color-info)'
    },
    {
      key: 'newPosts',
      label: '新增帖子',
      values: trends.newPosts || [],
      color: 'var(--color-accent)'
    }
  ]
})

const xLabels = computed(() => {
  const trends = props.trends
  if (!trends || !Array.isArray(trends.days)) return []
  return trends.days
})

const chartMetrics = computed(() => {
  if (!series.value) return null
  const all = series.value.flatMap((item) => item.values.map((value) => Number(value) || 0))
  const max = all.length ? Math.max(...all) : 0
  const safeMax = max <= 0 ? 1 : max
  const stepX = xLabels.value.length > 1
    ? (CHART_WIDTH - PADDING_X * 2) / (xLabels.value.length - 1)
    : 0
  return { max, safeMax, stepX }
})

function buildPoints(seriesItem) {
  if (!chartMetrics.value) return ''
  const { safeMax, stepX } = chartMetrics.value
  return seriesItem.values
    .map((value, index) => {
      const number = Number(value) || 0
      const x = PADDING_X + index * stepX
      const y = CHART_HEIGHT - PADDING_Y - (number / safeMax) * (CHART_HEIGHT - PADDING_Y * 2)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

function buildDots(seriesItem) {
  if (!chartMetrics.value) return []
  const { safeMax, stepX } = chartMetrics.value
  return seriesItem.values.map((value, index) => {
    const number = Number(value) || 0
    const x = PADDING_X + index * stepX
    const y = CHART_HEIGHT - PADDING_Y - (number / safeMax) * (CHART_HEIGHT - PADDING_Y * 2)
    return { x, y, value: number, key: `${seriesItem.key}-${index}` }
  })
}

const hasData = computed(() => Boolean(series.value))
</script>

<template>
  <div class="admin-trend-chart">
    <header class="admin-trend-chart__header">
      <div>
        <p class="admin-trend-chart__kicker">
          近 7 日趋势
        </p>
        <h3>平台核心数据新增走势</h3>
      </div>
      <ul
        v-if="hasData"
        class="admin-trend-chart__legend"
        aria-label="趋势图例"
      >
        <li
          v-for="item in series"
          :key="item.key"
        >
          <i
            :style="{ background: item.color }"
            aria-hidden="true"
          />
          <span>{{ item.label }}</span>
        </li>
      </ul>
    </header>

    <div
      v-if="loading && !hasData"
      class="admin-trend-chart__loading"
      role="status"
    >
      <LineChart
        :size="24"
        aria-hidden="true"
      />
      <span>正在加载趋势数据...</span>
    </div>

    <div
      v-else-if="!hasData"
      class="admin-trend-chart__empty"
    >
      <TriangleAlert
        :size="28"
        aria-hidden="true"
      />
      <p>暂无趋势数据，请稍后查看。</p>
    </div>

    <template v-else>
      <svg
        class="admin-trend-chart__svg"
        :viewBox="`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`"
        role="img"
        aria-label="近 7 日新增用户、任务和帖子趋势"
      >
        <line
          v-for="line in 4"
          :key="`grid-${line}`"
          class="admin-trend-chart__grid"
          :x1="PADDING_X"
          :x2="CHART_WIDTH - PADDING_X"
          :y1="PADDING_Y + ((CHART_HEIGHT - PADDING_Y * 2) / 4) * (line - 1)"
          :y2="PADDING_Y + ((CHART_HEIGHT - PADDING_Y * 2) / 4) * (line - 1)"
        />
        <g
          v-for="item in series"
          :key="item.key"
        >
          <polyline
            class="admin-trend-chart__line"
            :points="buildPoints(item)"
            :stroke="item.color"
            fill="none"
          />
          <circle
            v-for="dot in buildDots(item)"
            :key="dot.key"
            :cx="dot.x"
            :cy="dot.y"
            r="2.5"
            :fill="item.color"
          >
            <title>{{ item.label }}：{{ dot.value }}</title>
          </circle>
        </g>
        <g class="admin-trend-chart__axis">
          <text
            v-for="(label, index) in xLabels"
            :key="`label-${index}`"
            :x="PADDING_X + index * chartMetrics.stepX"
            :y="CHART_HEIGHT - 4"
            text-anchor="middle"
          >
            {{ label }}
          </text>
        </g>
      </svg>

      <ul
        class="admin-trend-chart__summary"
        aria-label="最新一天的新增数据"
      >
        <li
          v-for="item in series"
          :key="`summary-${item.key}`"
        >
          <i
            :style="{ background: item.color }"
            aria-hidden="true"
          />
          <span>{{ item.label }}</span>
          <strong>{{ item.values[item.values.length - 1] ?? 0 }}</strong>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.admin-trend-chart {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.admin-trend-chart__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.admin-trend-chart__kicker {
  margin: 0 0 4px;
  color: var(--color-brand);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.admin-trend-chart h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.05rem;
  font-weight: 900;
}

.admin-trend-chart__legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--color-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
}

.admin-trend-chart__legend li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.admin-trend-chart__legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.admin-trend-chart__svg {
  width: 100%;
  height: auto;
  max-height: 220px;
}

.admin-trend-chart__grid {
  stroke: var(--color-divider);
  stroke-dasharray: 2 4;
}

.admin-trend-chart__line {
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.admin-trend-chart__axis text {
  fill: var(--color-text-muted);
  font-size: 9px;
  font-weight: 600;
}

.admin-trend-chart__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 12px 14px;
  list-style: none;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  font-size: 0.85rem;
  font-weight: 700;
}

.admin-trend-chart__summary li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.admin-trend-chart__summary i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.admin-trend-chart__summary strong {
  margin-left: auto;
  color: var(--color-text);
  font-weight: 900;
}

.admin-trend-chart__loading,
.admin-trend-chart__empty {
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

.admin-trend-chart__empty p {
  margin: 0;
}

@media (max-width: 720px) {
  .admin-trend-chart {
    padding: 16px;
  }
  .admin-trend-chart__header {
    flex-direction: column;
  }
  .admin-trend-chart__summary {
    grid-template-columns: 1fr;
  }
}
</style>
