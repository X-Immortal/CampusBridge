<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  BadgeCheck,
  Bell,
  ChevronRight,
  Clock3,
  Compass,
  LoaderCircle,
  Search,
  ShieldCheck,
  Sparkles,
  Users
} from '@lucide/vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { sampleContent, sampleTasks } from '../data/mockData'
import api, { normalizePagedResult } from '../services/api'
import { useAuth } from '../stores/auth'

const { state, isAuthenticated, verificationStatus, refreshSession } = useAuth()

const keyword = ref('')
const contentState = ref({
  items: sampleContent,
  loading: false,
  error: '',
  fromFallback: true
})

const metrics = computed(() => {
  const open = sampleTasks.filter((task) => task.status === 'OPEN').length
  const inProgress = sampleTasks.filter((task) => ['ACCEPTED', 'IN_PROGRESS'].includes(task.status)).length
  const pending = sampleTasks.filter((task) => task.status === 'PENDING_CONFIRMATION').length
  return [
    { label: '可接任务', value: open, helper: 'OPEN 状态' },
    { label: '进行中', value: inProgress, helper: 'ACCEPTED / IN_PROGRESS' },
    { label: '待确认', value: pending, helper: 'PENDING_CONFIRMATION' },
    { label: '站内优先', value: '100%', helper: '仅站内沟通模式' }
  ]
})

const filteredItems = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return contentState.value.items
  return contentState.value.items.filter((item) =>
    [item.title, item.summary, item.categoryName, item.authorName].some((value) =>
      String(value || '')
        .toLowerCase()
        .includes(query)
    )
  )
})

function itemRoute(item) {
  if (item.itemType === 'TASK') return `/tasks/${item.sourceId}`
  return '/tasks'
}

function formatDate(value) {
  if (!value) return '暂无时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

async function loadContent() {
  contentState.value.loading = true
  contentState.value.error = ''
  try {
    const result = await api.listContent({ page: 1, size: 8, sortBy: 'CREATED_AT' })
    const paged = normalizePagedResult(result, sampleContent)
    contentState.value.items = paged.items.length ? paged.items : sampleContent
    contentState.value.fromFallback = paged.fromFallback || !paged.items.length
  } catch (error) {
    contentState.value.error = error.message
    contentState.value.items = sampleContent
    contentState.value.fromFallback = true
  } finally {
    contentState.value.loading = false
  }
}

onMounted(() => {
  loadContent()
  if (isAuthenticated.value) refreshSession()
})
</script>

<template>
  <section class="page home-page">
    <div class="hero-band">
      <div class="hero-copy">
        <p class="eyebrow">
          <ShieldCheck
            :size="16"
            aria-hidden="true"
          />
          P3 契约驱动的校园互助工作台
        </p>
        <h1 class="page-title">
          把发单、接单、确认和站内沟通放到同一条清晰流程里。
        </h1>
        <p class="page-subtitle">
          前端围绕 P3 的 Post / Task 分层设计和统一内容流搭建，优先覆盖 FE01-FE10 的联调入口。
        </p>
        <div class="hero-actions">
          <RouterLink
            class="button"
            to="/tasks/new"
          >
            <Sparkles
              :size="18"
              aria-hidden="true"
            />
            发布任务
          </RouterLink>
          <RouterLink
            class="button button--secondary"
            to="/tasks"
          >
            <Compass
              :size="18"
              aria-hidden="true"
            />
            浏览任务
          </RouterLink>
        </div>
      </div>

      <aside
        class="trust-strip"
        aria-label="当前账号状态"
      >
        <div>
          <span class="trust-kicker">登录状态</span>
          <strong>{{ isAuthenticated ? state.user?.username || '已登录用户' : '游客模式' }}</strong>
        </div>
        <StatusBadge
          :status="verificationStatus"
          :label="verificationStatus === 'VERIFIED' ? '校内已认证' : '需完成校内认证'"
          :tone="verificationStatus === 'VERIFIED' ? 'success' : 'warning'"
        />
        <RouterLink
          class="button button--secondary"
          to="/auth"
        >
          <BadgeCheck
            :size="17"
            aria-hidden="true"
          />
          认证档案
        </RouterLink>
      </aside>
    </div>

    <div class="grid grid--four metrics-grid">
      <article
        v-for="metric in metrics"
        :key="metric.label"
        class="metric-card"
      >
        <span class="metric-label">{{ metric.label }}</span>
        <strong class="metric-value">{{ metric.value }}</strong>
        <span class="metric-label">{{ metric.helper }}</span>
      </article>
    </div>

    <section class="search-panel">
      <div class="search-box">
        <Search
          :size="20"
          aria-hidden="true"
        />
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索任务、帖子、校区或奖励方式"
          aria-label="搜索内容流"
        >
      </div>
      <RouterLink
        class="button button--secondary"
        to="/tasks"
      >
        查看全部
        <ChevronRight
          :size="17"
          aria-hidden="true"
        />
      </RouterLink>
    </section>

    <section
      v-if="contentState.error || contentState.fromFallback"
      class="alert"
      role="status"
    >
      <Bell
        :size="18"
        aria-hidden="true"
      />
      <span>
        {{ contentState.error ? `列表接口暂不可用：${contentState.error}` : '后端列表接口当前返回为空，已展示界面预览数据。' }}
      </span>
    </section>

    <section class="grid grid--two">
      <div class="stack">
        <div class="panel-title">
          <h2>统一内容流</h2>
          <span
            v-if="contentState.loading"
            class="muted loading-text"
          >
            <LoaderCircle
              :size="16"
              aria-hidden="true"
            />
            加载中
          </span>
        </div>

        <EmptyState
          v-if="!filteredItems.length && !contentState.loading"
          title="没有匹配结果"
          description="换一个关键词试试，或直接发布一个新的互助任务。"
        />

        <template v-else>
          <RouterLink
            v-for="item in filteredItems"
            :key="`${item.itemType}-${item.sourceId}`"
            class="content-card card"
            :to="itemRoute(item)"
          >
            <div class="meta-row">
              <StatusBadge
                :status="item.itemType"
                :label="item.itemType === 'TASK' ? '任务' : '帖子'"
                :tone="item.itemType === 'TASK' ? 'info' : 'accent'"
              />
              <span>{{ item.categoryName || '未分类' }}</span>
              <span>{{ item.authorName || '匿名用户' }}</span>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.summary }}</p>
            <div class="content-footer">
              <span>
                <Clock3
                  :size="15"
                  aria-hidden="true"
                />
                {{ formatDate(item.sortTime || item.createdAt) }}
              </span>
              <strong v-if="item.rewardLabel">{{ item.rewardLabel }}</strong>
            </div>
          </RouterLink>
        </template>
      </div>

      <div class="stack">
        <section class="panel panel--quiet">
          <div class="panel-title">
            <h2>联调优先级</h2>
          </div>
          <div class="roadmap">
            <article>
              <span>FE01</span>
              <strong>注册 / 登录 / 校内认证</strong>
              <p>对接 BE01、BE02，带 Token 与 X-User-Id 兜底。</p>
            </article>
            <article>
              <span>FE02-FE06</span>
              <strong>任务发布到完成确认</strong>
              <p>覆盖发布、筛选、详情、申请、候选选择、状态流转与凭证。</p>
            </article>
            <article>
              <span>FE07-FE10</span>
              <strong>沟通、评价、举报与搜索</strong>
              <p>页面先就位，等待后端列表类接口从占位返回补齐为真实数据。</p>
            </article>
          </div>
        </section>

        <section class="panel panel--quiet">
          <div class="panel-title">
            <h2>可信互助原则</h2>
          </div>
          <div class="principles">
            <p>
              <ShieldCheck
                :size="18"
                aria-hidden="true"
              />
              发单、接单前突出校内认证状态。
            </p>
            <p>
              <Users
                :size="18"
                aria-hidden="true"
              />
              候选人展示信用、认证和申请理由，辅助选择。
            </p>
            <p>
              <Bell
                :size="18"
                aria-hidden="true"
              />
              对驳回、争议、超时等异常路径保留明确原因输入。
            </p>
          </div>
        </section>
      </div>
    </section>
  </section>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 22px;
}

.hero-band {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: stretch;
  padding: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: #ffffff;
  box-shadow: var(--shadow-soft);
}

.hero-copy {
  display: grid;
  gap: 18px;
  align-content: center;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.trust-strip {
  display: grid;
  align-content: center;
  gap: 16px;
  padding: 20px;
  border-left: 4px solid var(--color-action);
  border-radius: var(--radius-card);
  background: #f1f7fb;
}

.trust-strip strong {
  display: block;
  margin-top: 6px;
  color: var(--color-primary);
  font-size: 1.35rem;
}

.trust-kicker {
  color: var(--color-muted);
  font-size: 0.82rem;
  font-weight: 700;
}

.metrics-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.search-panel {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 220px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #f8fafc;
  color: var(--color-muted);
}

.search-box input {
  width: 100%;
  min-height: 44px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
}

.loading-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.content-card h3 {
  margin: 0;
  color: var(--color-primary);
  font-size: 1.15rem;
}

.content-card p {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.content-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-muted);
  font-size: 0.88rem;
}

.content-footer span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.content-footer strong {
  color: var(--color-action);
}

.roadmap,
.principles {
  display: grid;
  gap: 14px;
}

.roadmap article {
  padding: 14px 0;
  border-top: 1px solid var(--color-border);
}

.roadmap article:first-child {
  border-top: 0;
}

.roadmap span {
  color: var(--color-action);
  font-size: 0.82rem;
  font-weight: 800;
}

.roadmap strong {
  display: block;
  margin-top: 4px;
  color: var(--color-primary);
}

.roadmap p,
.principles p {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.55;
}

.principles p {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

@media (max-width: 980px) {
  .hero-band,
  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .search-panel {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
