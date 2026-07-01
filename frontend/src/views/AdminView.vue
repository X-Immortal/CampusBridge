<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BadgeCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  FileWarning,
  GaugeCircle,
  IdCard,
  LoaderCircle,
  RefreshCw,
  Scale,
  ShieldAlert,
  ShieldCheck,
  XCircle
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import AdminMetricGrid from '../components/admin/AdminMetricGrid.vue'
import AdminTrendChart from '../components/admin/AdminTrendChart.vue'
import AdminDistributionChart from '../components/admin/AdminDistributionChart.vue'
import AdminTodoSummary from '../components/admin/AdminTodoSummary.vue'
import {
  MODERATION_DECISION_OPTIONS,
  MODERATION_STATUS_OPTIONS,
  optionLabel,
  REPORT_TARGET_TYPE_OPTIONS,
  VERIFICATION_METHODS
} from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'

const { state } = useAuth()
const { showToast } = useToast()
const route = useRoute()
const router = useRouter()

const sections = [
  { key: 'dashboard', label: '数据概览', icon: GaugeCircle },
  { key: 'verifications', label: '认证审核', icon: IdCard },
  { key: 'reports', label: '举报裁决', icon: Scale }
]

const allowedSectionKeys = new Set(sections.map((section) => section.key))

const verificationPage = reactive({
  page: 1,
  size: 20
})
const verificationTotal = ref(0)
const verifications = ref([])
const verificationsLoading = ref(false)
const verificationRejectDrafts = reactive({})
const verificationActionLoading = ref('')

const verificationTotalPages = computed(() =>
  Math.max(1, Math.ceil(verificationTotal.value / verificationPage.size))
)

const visibleVerificationPages = computed(() => {
  const total = verificationTotalPages.value
  const current = verificationPage.page
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  const pages = new Set([1, total])
  for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) {
    pages.add(i)
  }
  if (current > 3) pages.add(Math.max(2, current - 2))
  if (current < total - 2) pages.add(Math.min(total - 1, current + 2))
  return Array.from(pages).sort((a, b) => a - b)
})

const reports = ref([])
const reportsLoading = ref(false)
const reportFilters = reactive({
  status: '',
  page: 1,
  size: 20
})
const reportForm = reactive({
  reportId: '',
  status: 'APPROVED',
  decision: '',
  markTaskDisputed: false
})
const reportErrors = reactive({})
const reportSubmitting = ref(false)
const activeReportId = ref('')
const lastModerationRecord = ref(null)

const dashboardLoading = ref(false)
const dashboardError = ref('')
const dashboardSummary = ref(null)
const dashboardTrends = ref(null)
const taskStatusStats = ref([])
const reportStatusStats = ref([])
const reportTargetTypeStats = ref([])
const dashboardTodos = ref([])
const dashboardUpdatedAt = ref('')
const dashboardTrendDays = ref(7)

const DEFAULT_SECTION = 'dashboard'
const initialSection = (() => {
  const requested = String(route.query?.section || '').toLowerCase()
  return allowedSectionKeys.has(requested) ? requested : DEFAULT_SECTION
})()

const activeSection = ref(initialSection)

const isAdmin = computed(() => String(state.user?.role || '').toUpperCase() === 'ADMIN')
const pendingVerificationCount = computed(() => verificationTotal.value)
const reportCount = computed(() => reports.value.length)
function switchSection(sectionKey) {
  if (!allowedSectionKeys.has(sectionKey)) return
  activeSection.value = sectionKey
  if (route.query?.section !== sectionKey) {
    router.replace({ query: sectionKey === DEFAULT_SECTION ? {} : { ...route.query, section: sectionKey } })
  }
}

watch(
  () => route.query?.section,
  (section) => {
    const next = String(section || '').toLowerCase()
    if (!next) {
      if (activeSection.value !== DEFAULT_SECTION) activeSection.value = DEFAULT_SECTION
      return
    }
    if (allowedSectionKeys.has(next) && activeSection.value !== next) {
      activeSection.value = next
    }
  }
)

function normalizeItems(result) {
  if (Array.isArray(result)) return result
  return result?.items || result?.records || result?.content || result?.list || result?.tasks || []
}

function normalizeVerification(record = {}) {
  return {
    ...record,
    id: record.id ?? record.verificationId,
    userId: record.userId ?? record.applicantId,
    method: record.method || 'MANUAL_REVIEW',
    status: record.status || 'PENDING'
  }
}

function normalizeReport(report = {}) {
  return {
    ...report,
    id: report.id ?? report.reportId,
    status: report.status || 'PENDING'
  }
}

function applyReportResult(result) {
  reports.value = normalizeItems(result).map(normalizeReport)
}

function formatDateTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function methodLabel(method) {
  return optionLabel(VERIFICATION_METHODS, method)
}

function targetTypeLabel(type) {
  return optionLabel(REPORT_TARGET_TYPE_OPTIONS, type)
}

function moderationStatusLabel(status) {
  return optionLabel(MODERATION_STATUS_OPTIONS, status)
}

function recordSummary(record = {}) {
  const parts = [
    record.id ? `记录 #${record.id}` : '',
    record.reportId ? `举报 #${record.reportId}` : '',
    record.targetType ? targetTypeLabel(record.targetType) : '',
    record.targetId ? `目标 #${record.targetId}` : ''
  ].filter(Boolean)
  return parts.join(' · ') || '处理记录已生成'
}

function reportTargetCopy(report = {}) {
  return `${targetTypeLabel(report.targetType)} #${report.targetId || '未设置'}`
}

function clearReportErrors() {
  Object.keys(reportErrors).forEach((key) => {
    delete reportErrors[key]
  })
}

function setReportError(key, message) {
  reportErrors[key] = message
}

function extractTotal(result) {
  if (result == null) return 0
  if (Array.isArray(result)) return result.length
  return Number(result?.total ?? result?.totalElements ?? result?.totalCount ?? result?.count ?? 0)
}

async function loadVerifications() {
  verificationsLoading.value = true
  try {
    const result = await api.getPendingVerifications({
      page: verificationPage.page,
      size: verificationPage.size
    })
    verificationTotal.value = extractTotal(result)
    verifications.value = normalizeItems(result).map(normalizeVerification)
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    verificationsLoading.value = false
  }
}

function goToVerificationPage(page) {
  const pageNum = Number(page)
  if (!pageNum || pageNum < 1 || pageNum > verificationTotalPages.value) return
  verificationPage.page = pageNum
  loadVerifications()
}

async function approveVerification(record) {
  if (!record?.id) return
  const loadingKey = `approve:${record.id}`
  verificationActionLoading.value = loadingKey
  try {
    await api.approveVerification(record.id)
    verifications.value = verifications.value.filter((item) => String(item.id) !== String(record.id))
    showToast(`认证 #${record.id} 已通过。`)
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    verificationActionLoading.value = ''
  }
}

async function rejectVerification(record) {
  if (!record?.id) return
  const reason = String(verificationRejectDrafts[record.id] || '').trim()
  const loadingKey = `reject:${record.id}`
  verificationActionLoading.value = loadingKey
  try {
    await api.rejectVerification(record.id, reason ? { reason } : {})
    verifications.value = verifications.value.filter((item) => String(item.id) !== String(record.id))
    delete verificationRejectDrafts[record.id]
    showToast(`认证 #${record.id} 已驳回。`)
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    verificationActionLoading.value = ''
  }
}

async function loadReports() {
  reportsLoading.value = true
  try {
    const result = await api.getAdminReports(reportFilters)
    applyReportResult(result)
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    reportsLoading.value = false
  }
}

function useReportForResolution(report) {
  activeReportId.value = String(report.id || '')
  reportForm.reportId = String(report.id || '')
  reportForm.decision = report.reason
    ? `已核查举报原因：${report.reason}`
    : reportForm.decision
  if (report.targetType === 'TASK') {
    reportForm.markTaskDisputed = true
  }
  showToast(`已载入举报 #${report.id} 到裁决表单。`)
}

function clearActiveReport() {
  activeReportId.value = ''
  reportForm.reportId = ''
  reportForm.decision = ''
  reportForm.markTaskDisputed = false
  Object.keys(reportErrors).forEach(k => delete reportErrors[k])
}

function validateReportForm() {
  clearReportErrors()

  if (!String(reportForm.reportId).trim()) {
    setReportError('reportId', '请填写举报 ID。')
  }
  if (!String(reportForm.decision).trim()) {
    setReportError('decision', '请填写裁决说明。')
  }
  if (reportForm.decision.trim().length > 500) {
    setReportError('decision', '裁决说明不能超过 500 个字符。')
  }
  return Object.keys(reportErrors).length === 0
}

function buildResolvePayload() {
  return {
    status: reportForm.status,
    decision: reportForm.decision.trim(),
    markTaskDisputed: Boolean(reportForm.markTaskDisputed)
  }
}

async function submitReportResolution() {
  if (!validateReportForm()) {
    showToast('请检查举报裁决表单中标出的内容。', { type: 'danger' })
    return
  }

  reportSubmitting.value = true
  try {
    const record = await api.resolveReport(reportForm.reportId, buildResolvePayload())
    lastModerationRecord.value = record || {
      reportId: reportForm.reportId,
      status: reportForm.status,
      decision: reportForm.decision
    }
    reports.value = reports.value.map((report) =>
      String(report.id) === String(reportForm.reportId)
        ? { ...report, status: reportForm.status }
        : report
    )
    showToast(`举报 #${reportForm.reportId} 已裁决。`)
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    reportSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.allSettled([loadVerifications(), loadReports(), loadDashboard()])
})

async function loadDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    const [summary, trends, taskStatus, reportStatus, reportTargetType, todos] = await Promise.all([
      api.getAdminDashboardSummary(),
      api.getAdminDashboardTrends({ days: dashboardTrendDays.value }),
      api.getAdminTaskStatusStats(),
      api.getAdminReportStatusStats(),
      api.getAdminReportTargetTypeStats(),
      api.getAdminDashboardTodos()
    ])
    dashboardSummary.value = summary || null
    dashboardTrends.value = trends || null
    taskStatusStats.value = Array.isArray(taskStatus) ? taskStatus : []
    reportStatusStats.value = Array.isArray(reportStatus) ? reportStatus : []
    reportTargetTypeStats.value = Array.isArray(reportTargetType) ? reportTargetType : []
    dashboardTodos.value = Array.isArray(todos) ? todos : []
    dashboardUpdatedAt.value = summary?.updatedAt || new Date().toISOString()
  } catch (error) {
    dashboardError.value = error?.message || '后台数据加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

function formatDashboardUpdatedAt(value) {
  if (!value) return '尚未刷新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function handleTodoNavigate(item) {
  if (!item?.route) return
  showToast(`已跳转到「${item.label}」入口。`)
}
</script>

<template>
  <DashboardShell active-label="管理后台">
    <main
      class="admin-page"
      :aria-busy="verificationsLoading || reportsLoading || dashboardLoading"
    >
      <section class="page-title-card admin-title-card">
        <div class="title-icon">
          <ShieldCheck
            :size="34"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>管理员工作台</p>
          <h1>{{ activeSection === 'dashboard' ? '平台运行总览与数据仪表盘' : '审核认证、裁决举报与处理任务' }}</h1>
          <span>
            <template v-if="activeSection === 'dashboard'">
              实时统计平台核心指标，最近更新于 {{ formatDashboardUpdatedAt(dashboardUpdatedAt) }}。
            </template>
            <template v-else>
              面向管理员角色的集中处理台，直接对接管理员 API 文档中的专属端点。
            </template>
          </span>
        </div>
        <div class="admin-title-card__actions">
          <button
            v-if="activeSection === 'dashboard'"
            class="icon-action"
            type="button"
            :disabled="dashboardLoading"
            :aria-label="dashboardLoading ? '正在刷新数据' : '刷新数据总览'"
            @click="loadDashboard"
          >
            <RefreshCw
              :class="{ spinning: dashboardLoading }"
              :size="18"
              aria-hidden="true"
            />
            <span>{{ dashboardLoading ? '刷新中' : '刷新' }}</span>
          </button>
          <StatusBadge
            :label="isAdmin ? '管理员权限' : '非管理员账号'"
            :tone="isAdmin ? 'success' : 'danger'"
          />
        </div>
      </section>

      <section
        v-if="!isAdmin"
        class="page-feedback page-feedback--danger"
        role="status"
      >
        <ShieldAlert
          :size="18"
          aria-hidden="true"
        />
        <span>当前登录账号不是 ADMIN 角色，后端会拒绝访问管理员接口。请使用管理员账号继续测试。</span>
      </section>

      <section
        class="admin-metric-strip"
        aria-label="管理员处理概览"
      >
        <article class="admin-metric admin-metric--warning">
          <span>待审认证</span>
          <strong>{{ pendingVerificationCount }}</strong>
        </article>
        <article class="admin-metric admin-metric--info">
          <span>全站举报</span>
          <strong>{{ reportCount }}</strong>
        </article>
      </section>

      <section
        class="admin-section-switch"
        role="tablist"
        aria-label="管理员功能切换"
      >
        <button
          v-for="section in sections"
          :key="section.key"
          type="button"
          role="tab"
          :aria-selected="activeSection === section.key"
          :class="{ active: activeSection === section.key }"
          @click="switchSection(section.key)"
        >
          <component
            :is="section.icon"
            :size="17"
            aria-hidden="true"
          />
          {{ section.label }}
        </button>
      </section>

      <section
        v-if="activeSection === 'dashboard'"
        class="admin-dashboard"
        aria-labelledby="admin-dashboard-title"
      >
        <div
          v-if="dashboardError"
          class="page-feedback page-feedback--danger"
          role="alert"
        >
          <ShieldAlert
            :size="18"
            aria-hidden="true"
          />
          <div class="admin-dashboard__feedback">
            <span>后台数据加载失败：{{ dashboardError }}</span>
            <button
              class="ghost-action"
              type="button"
              :disabled="dashboardLoading"
              @click="loadDashboard"
            >
              <RefreshCw
                :class="{ spinning: dashboardLoading }"
                :size="16"
                aria-hidden="true"
              />
              重试
            </button>
          </div>
        </div>

        <h2
          id="admin-dashboard-title"
          class="visually-hidden"
        >
          数据总览仪表盘
        </h2>

        <AdminMetricGrid
          :summary="dashboardSummary"
          :loading="dashboardLoading"
        />

        <div class="admin-dashboard__charts">
          <AdminTrendChart
            :trends="dashboardTrends"
            :loading="dashboardLoading"
          />
          <AdminDistributionChart
            kicker="任务状态分布"
            title="当前任务状态占比"
            :data="taskStatusStats"
            :loading="dashboardLoading"
            empty-hint="任务状态数据为空"
          />
        </div>

        <div class="admin-dashboard__charts">
          <AdminDistributionChart
            kicker="举报状态分布"
            title="举报处理进度"
            :data="reportStatusStats"
            :loading="dashboardLoading"
            empty-hint="举报数据为空"
          />
          <AdminDistributionChart
            kicker="举报对象分布"
            title="高风险对象来源"
            :data="reportTargetTypeStats"
            :loading="dashboardLoading"
            empty-hint="暂无举报对象分布"
          />
        </div>

        <AdminTodoSummary
          :todos="dashboardTodos"
          :loading="dashboardLoading && !dashboardTodos.length"
          @navigate="handleTodoNavigate"
        />
      </section>

      <section
        v-if="activeSection === 'verifications'"
        class="admin-layout admin-layout--verifications"
        aria-labelledby="admin-verifications-title"
      >
        <section class="panel-card admin-list-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                认证审核
              </p>
              <h2 id="admin-verifications-title">
                待审核校园身份
              </h2>
            </div>
          </div>

          <div
            v-if="verificationsLoading && !verifications.length"
            class="loading-state"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="24"
              aria-hidden="true"
            />
            <span>正在同步待审认证...</span>
          </div>

          <div
            v-else-if="!verifications.length"
            class="empty-state"
          >
            <BadgeCheck
              :size="36"
              aria-hidden="true"
            />
            <h3>暂无待审认证</h3>
            <p>新的 PENDING 认证申请会出现在这里。</p>
          </div>

          <template v-else>
            <nav
              class="pagination-bar"
              aria-label="认证审核翻页"
            >
              <button
                class="pagination-btn"
                type="button"
                :disabled="verificationPage.page <= 1"
                @click="goToVerificationPage(verificationPage.page - 1)"
              >
                <ChevronLeft
                  :size="17"
                  aria-hidden="true"
                />
                上一页
              </button>

              <div class="pagination-pages">
                <button
                  v-for="pageNum in visibleVerificationPages"
                  :key="pageNum"
                  class="pagination-num"
                  :class="{ active: pageNum === verificationPage.page }"
                  type="button"
                  :aria-current="pageNum === verificationPage.page ? 'page' : undefined"
                  @click="goToVerificationPage(pageNum)"
                >
                  {{ pageNum }}
                </button>
              </div>

              <button
                class="pagination-btn"
                type="button"
                :disabled="verificationPage.page >= verificationTotalPages"
                @click="goToVerificationPage(verificationPage.page + 1)"
              >
                下一页
                <ChevronRight
                  :size="17"
                  aria-hidden="true"
                />
              </button>
            </nav>

            <article
              v-for="record in verifications"
              :key="record.id"
              class="verification-card"
            >
              <header class="record-card-head">
                <div>
                  <strong>{{ record.realNameCipher || '待核验姓名' }}</strong>
                  <span>认证 #{{ record.id }} · 用户 #{{ record.userId || '未知' }}</span>
                </div>
                <StatusBadge
                  :label="methodLabel(record.method)"
                  tone="info"
                />
              </header>

              <dl class="record-meta-grid">
                <div>
                  <dt>学校</dt>
                  <dd>{{ record.school || '未填写' }}</dd>
                </div>
                <div>
                  <dt>院系</dt>
                  <dd>{{ record.department || '未填写' }}</dd>
                </div>
                <div>
                  <dt>学号</dt>
                  <dd>{{ record.studentNumberCipher || '未填写' }}</dd>
                </div>
                <div>
                  <dt>提交时间</dt>
                  <dd>{{ formatDateTime(record.createdAt) }}</dd>
                </div>
              </dl>

              <a
                v-if="record.evidenceUrl"
                class="evidence-link"
                :href="record.evidenceUrl"
                target="_blank"
                rel="noreferrer"
              >
                查看认证材料
              </a>

              <label
                class="field-control field-control--wide"
                :for="`verification-reject-${record.id}`"
              >
                <span>驳回原因</span>
                <textarea
                  :id="`verification-reject-${record.id}`"
                  v-model.trim="verificationRejectDrafts[record.id]"
                  maxlength="200"
                  placeholder="可选；不填写则使用后端默认原因。"
                />
              </label>

              <footer class="record-actions">
                <button
                  class="primary-action"
                  type="button"
                  :disabled="Boolean(verificationActionLoading)"
                  @click="approveVerification(record)"
                >
                  <LoaderCircle
                    v-if="verificationActionLoading === `approve:${record.id}`"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <CheckCircle2
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  通过
                </button>
                <button
                  class="danger-action"
                  type="button"
                  :disabled="Boolean(verificationActionLoading)"
                  @click="rejectVerification(record)"
                >
                  <LoaderCircle
                    v-if="verificationActionLoading === `reject:${record.id}`"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <XCircle
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  驳回
                </button>
              </footer>
            </article>
          </template>
        </section>
      </section>

      <section
        v-else-if="activeSection === 'reports'"
        class="admin-layout admin-layout--reports"
        aria-labelledby="admin-reports-title"
      >
        <aside class="panel-card admin-list-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                举报记录
              </p>
              <h2>全站举报列表</h2>
            </div>
            <button
              class="icon-action"
              type="button"
              aria-label="刷新举报列表"
              :disabled="reportsLoading"
              @click="loadReports"
            >
              <RefreshCw
                :class="{ spinning: reportsLoading }"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>

          <div class="inline-controls">
            <label class="field-control">
              <span>状态</span>
              <select v-model="reportFilters.status">
                <option value="">
                  全部状态
                </option>
                <option
                  v-for="status in MODERATION_STATUS_OPTIONS"
                  :key="status.value"
                  :value="status.value"
                >
                  {{ status.label }}
                </option>
              </select>
            </label>
          </div>

          <div
            v-if="reportsLoading && !reports.length"
            class="loading-state"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="24"
              aria-hidden="true"
            />
            <span>正在同步举报列表...</span>
          </div>

          <div
            v-else-if="!reports.length"
            class="empty-state"
          >
            <FileWarning
              :size="36"
              aria-hidden="true"
            />
            <h3>暂无举报记录</h3>
            <p>用户提交的举报审核后会出现在这里。</p>
          </div>

          <article
            v-for="report in reports"
            v-else
            :key="report.id"
            class="report-card"
            :class="{ 'report-card--active': String(report.id) === activeReportId }"
            role="button"
            tabindex="0"
            @click="useReportForResolution(report)"
            @keydown.enter="useReportForResolution(report)"
          >
            <header class="record-card-head">
              <div>
                <strong>举报 #{{ report.id }}</strong>
                <span>{{ reportTargetCopy(report) }}</span>
              </div>
              <StatusBadge
                :label="moderationStatusLabel(report.status)"
                :tone="MODERATION_STATUS_OPTIONS.find((item) => item.value === report.status)?.tone || 'muted'"
              />
            </header>
            <p>{{ report.reason || '未填写简要原因。' }}</p>
            <small>{{ report.description || '暂无详细描述。' }}</small>
          </article>
        </aside>

        <section
          class="panel-card admin-form-panel"
          aria-labelledby="admin-reports-title"
        >
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                举报裁决
              </p>
              <h2 id="admin-reports-title">
                处理指定举报
              </h2>
            </div>
            <StatusBadge
              :label="optionLabel(MODERATION_DECISION_OPTIONS, reportForm.status)"
              :tone="MODERATION_DECISION_OPTIONS.find((item) => item.value === reportForm.status)?.tone || 'success'"
            />
          </div>

          <form
            class="admin-form"
            @submit.prevent="submitReportResolution"
          >
            <label
              class="field-control"
              for="resolve-report-id"
            >
              <span>举报 ID</span>
              <span class="readonly-id-row">
                <input
                  id="resolve-report-id"
                  :value="reportForm.reportId"
                  type="number"
                  readonly
                  placeholder="点击左边举报自动填充"
                >
                <button
                  v-if="activeReportId"
                  class="ghost-action"
                  type="button"
                  @click="clearActiveReport"
                >
                  清除
                </button>
              </span>
              <small
                v-if="reportErrors.reportId"
                class="field-error"
              >
                {{ reportErrors.reportId }}
              </small>
            </label>

            <label
              class="field-control"
              for="resolve-report-status"
            >
              <span>裁决结果</span>
              <select
                id="resolve-report-status"
                v-model="reportForm.status"
              >
                <option
                  v-for="status in MODERATION_DECISION_OPTIONS"
                  :key="status.value"
                  :value="status.value"
                >
                  {{ status.label }}
                </option>
              </select>
            </label>

            <label
              class="field-control field-control--wide"
              for="resolve-report-decision"
            >
              <span>裁决说明</span>
              <textarea
                id="resolve-report-decision"
                v-model.trim="reportForm.decision"
                maxlength="500"
                :aria-invalid="Boolean(reportErrors.decision)"
                placeholder="写清楚核查结论、处理动作和依据。"
              />
              <small>{{ reportForm.decision.length }}/500</small>
              <small
                v-if="reportErrors.decision"
                class="field-error"
              >
                {{ reportErrors.decision }}
              </small>
            </label>

            <label
              class="toggle-row"
              for="mark-task-disputed"
            >
              <input
                id="mark-task-disputed"
                v-model="reportForm.markTaskDisputed"
                type="checkbox"
              >
              <span>若举报目标是任务，尝试标记为争议任务</span>
            </label>

            <button
              class="primary-action field-control--wide"
              type="submit"
              :disabled="reportSubmitting"
            >
              <LoaderCircle
                v-if="reportSubmitting"
                class="spinning"
                :size="17"
                aria-hidden="true"
              />
              <Scale
                v-else
                :size="17"
                aria-hidden="true"
              />
              提交裁决
            </button>
          </form>

          <section
            v-if="lastModerationRecord"
            class="moderation-record-card"
          >
            <span>最新处理记录</span>
            <strong>{{ recordSummary(lastModerationRecord) }}</strong>
            <p>{{ lastModerationRecord.decision || reportForm.decision }}</p>
          </section>
        </section>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.admin-page {
  display: grid;
  gap: 18px;
}

.page-title-card,
.panel-card,
.page-feedback {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.admin-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 22px;
  min-height: 112px;
  padding: 20px 28px;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-accent-bg));
  color: var(--color-brand);
}

.title-copy {
  display: grid;
  gap: 6px;
}

.title-copy p,
.section-kicker {
  margin: 0;
  color: var(--color-brand);
  font-size: 0.82rem;
  font-weight: 900;
}

.title-copy h1,
.panel-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
  line-height: 1.15;
}

.title-copy h1 {
  font-size: 1.5rem;
}

.title-copy span,
.panel-copy {
  color: var(--color-text-muted);
  line-height: 1.6;
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 13px;
  color: var(--color-info-text);
  line-height: 1.5;
}

.page-feedback--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.admin-metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.admin-metric {
  display: grid;
  gap: 8px;
  min-height: 82px;
  padding: 16px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
}

.admin-metric span {
  color: var(--color-text-muted);
  font-size: 0.86rem;
  font-weight: 800;
}

.admin-metric strong {
  color: var(--color-text);
  font-size: 1.65rem;
  line-height: 1;
}

.admin-metric--warning {
  background: var(--color-warning-bg);
}

.admin-metric--info {
  background: var(--color-brand-soft);
}

.admin-metric--accent {
  background: var(--color-accent-bg);
}

.admin-section-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: fit-content;
  max-width: 100%;
  padding: 4px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-bg);
}

.admin-section-switch button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-weight: 900;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.admin-section-switch button.active {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
}

.admin-layout {
  display: grid;
  gap: 18px;
  align-items: start;
}

.admin-layout--reports,
.admin-layout--tasks {
  grid-template-columns: minmax(320px, 0.82fr) minmax(420px, 1fr);
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 20px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.inline-controls,
.record-actions,
.manual-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px;
}

/* ---- 翻页组件 ---- */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding-top: 8px;
}

.pagination-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-weight: 900;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease;
}

.pagination-btn:hover:not(:disabled) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.pagination-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.pagination-pages {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 38px;
  height: 38px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 900;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease;
}

.pagination-num:hover {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.pagination-num.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-surface);
}

.field-control {
  display: grid;
  gap: 7px;
}

.field-control--wide {
  grid-column: 1 / -1;
}

.field-control span {
  color: var(--color-text-secondary);
  font-size: 0.84rem;
  font-weight: 900;
}

.field-control input,
.field-control select,
.field-control textarea {
  width: 100%;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: 0;
}

.field-control input,
.field-control select {
  height: 42px;
  padding: 0 12px;
}

.field-control textarea {
  min-height: 96px;
  padding: 11px 12px;
  line-height: 1.55;
  resize: vertical;
}

.field-control input:focus,
.field-control select:focus,
.field-control textarea:focus {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.field-control input[aria-invalid="true"],
.field-control textarea[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.field-control small {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.field-error {
  color: var(--color-danger) !important;
  font-weight: 800;
}

.admin-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.toggle-row {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 40px;
  color: var(--color-text-secondary);
  font-weight: 900;
}

.toggle-row input {
  width: 18px;
  height: 18px;
  accent-color: var(--color-brand);
}

.icon-action,
.ghost-action,
.primary-action,
.danger-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  border-radius: 8px;
  font-weight: 900;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.icon-action {
  width: 40px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand);
}

.primary-action {
  padding: 0 14px;
  border: 1px solid var(--color-brand);
  background: var(--color-brand);
  color: var(--color-surface);
  box-shadow: 0 14px 26px var(--shadow-button);
}

.ghost-action {
  padding: 0 12px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand);
}

.danger-action {
  padding: 0 12px;
  border: 1px solid var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.icon-action:hover:not(:disabled),
.ghost-action:hover:not(:disabled) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.primary-action:hover:not(:disabled) {
  background: var(--color-brand-hover);
}

.danger-action:hover:not(:disabled) {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
}

.icon-action:disabled,
.ghost-action:disabled,
.primary-action:disabled,
.danger-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.verification-card,
.report-card,
.task-moderation-card,
.moderation-record-card {
  display: grid;
  gap: 12px;
  padding: 15px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.record-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.record-card-head > div {
  display: grid;
  gap: 4px;
}

.record-card-head strong {
  color: var(--color-text);
  font-size: 1rem;
}

.record-card-head span,
.report-card small,
.moderation-record-card span {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.report-card p,
.task-moderation-card p,
.moderation-record-card p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.report-card {
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.report-card:hover {
  border-color: var(--color-brand-border);
  box-shadow: 0 2px 10px var(--shadow-card);
}

.report-card--active {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  box-shadow: var(--shadow-focus);
}

.readonly-id-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.readonly-id-row input {
  flex: 1;
  cursor: default;
  background: var(--color-bg);
}

.readonly-id-row .ghost-action {
  flex-shrink: 0;
  font-size: 0.82rem;
}

.record-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 9px;
  margin: 0;
}

.record-meta-grid div {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.record-meta-grid dt {
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.record-meta-grid dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
}

.evidence-link {
  width: fit-content;
  color: var(--color-brand-hover);
  font-weight: 900;
}

.loading-state,
.empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: var(--color-text);
}

.empty-state p {
  max-width: 420px;
  margin: 0;
  line-height: 1.55;
}

.moderation-record-card {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
}

.moderation-record-card strong {
  color: var(--color-success-text);
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .admin-layout--reports,
  .admin-layout--tasks {
    grid-template-columns: 1fr;
  }

  .record-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-title-card,
  .admin-metric-strip,
  .admin-form,
  .record-meta-grid {
    grid-template-columns: 1fr;
  }

  .admin-section-switch,
  .inline-controls,
  .record-actions,
  .manual-actions,
  .primary-action,
  .ghost-action,
  .danger-action {
    width: 100%;
  }

  .admin-section-switch button {
    flex: 1 1 140px;
  }

  .pagination-bar {
    flex-wrap: wrap;
  }
}

.admin-title-card__actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.admin-title-card__actions .icon-action {
  gap: 6px;
  width: auto;
  padding: 0 12px;
  color: var(--color-brand);
}

.admin-dashboard {
  display: grid;
  gap: 16px;
}

.admin-dashboard__charts {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
}

.admin-dashboard__feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.admin-dashboard .visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1100px) {
  .admin-dashboard__charts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .admin-title-card__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
