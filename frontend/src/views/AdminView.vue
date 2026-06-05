<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BadgeCheck,
  CheckCircle2,
  FileWarning,
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
import {
  MODERATION_DECISION_OPTIONS,
  MODERATION_STATUS_OPTIONS,
  optionLabel,
  REPORT_TARGET_TYPE_OPTIONS,
  VERIFICATION_METHODS
} from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const { state } = useAuth()

const sections = [
  { key: 'verifications', label: '认证审核', icon: IdCard },
  { key: 'reports', label: '举报裁决', icon: Scale }
]

const verificationPage = reactive({
  page: 1,
  size: 20
})
const verifications = ref([])
const verificationsLoading = ref(false)
const verificationFeedback = ref('')
const verificationFeedbackType = ref('success')
const verificationRejectDrafts = reactive({})
const verificationActionLoading = ref('')

const reports = ref([])
const reportsLoading = ref(false)
const reportFeedback = ref('')
const reportFeedbackType = ref('success')
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


const activeSection = ref('verifications')

const isAdmin = computed(() => String(state.user?.role || '').toUpperCase() === 'ADMIN')
const pendingVerificationCount = computed(() => verifications.value.length)
const reportCount = computed(() => reports.value.length)
function switchSection(sectionKey) {
  activeSection.value = sectionKey
}

function setVerificationFeedback(message, type = 'success') {
  verificationFeedback.value = message
  verificationFeedbackType.value = type
}

function setReportFeedback(message, type = 'success') {
  reportFeedback.value = message
  reportFeedbackType.value = type
}

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

async function loadVerifications() {
  verificationsLoading.value = true
  verificationFeedback.value = ''
  try {
    const result = await api.getPendingVerifications({
      page: verificationPage.page,
      size: verificationPage.size
    })
    verifications.value = normalizeItems(result).map(normalizeVerification)
  } catch (error) {
    setVerificationFeedback(error.message, 'danger')
  } finally {
    verificationsLoading.value = false
  }
}

async function approveVerification(record) {
  if (!record?.id) return
  const loadingKey = `approve:${record.id}`
  verificationActionLoading.value = loadingKey
  verificationFeedback.value = ''
  try {
    await api.approveVerification(record.id)
    verifications.value = verifications.value.filter((item) => String(item.id) !== String(record.id))
    setVerificationFeedback(`认证 #${record.id} 已通过。`)
  } catch (error) {
    setVerificationFeedback(error.message, 'danger')
  } finally {
    verificationActionLoading.value = ''
  }
}

async function rejectVerification(record) {
  if (!record?.id) return
  const reason = String(verificationRejectDrafts[record.id] || '').trim()
  const loadingKey = `reject:${record.id}`
  verificationActionLoading.value = loadingKey
  verificationFeedback.value = ''
  try {
    await api.rejectVerification(record.id, reason ? { reason } : {})
    verifications.value = verifications.value.filter((item) => String(item.id) !== String(record.id))
    delete verificationRejectDrafts[record.id]
    setVerificationFeedback(`认证 #${record.id} 已驳回。`)
  } catch (error) {
    setVerificationFeedback(error.message, 'danger')
  } finally {
    verificationActionLoading.value = ''
  }
}

async function loadReports() {
  reportsLoading.value = true
  reportFeedback.value = ''
  try {
    const result = await api.getAdminReports(reportFilters)
    applyReportResult(result)
  } catch (error) {
    setReportFeedback(error.message, 'danger')
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
  setReportFeedback(`已载入举报 #${report.id} 到裁决表单。`)
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
    setReportFeedback('请检查举报裁决表单中标出的内容。', 'danger')
    return
  }

  reportSubmitting.value = true
  reportFeedback.value = ''
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
    setReportFeedback(`举报 #${reportForm.reportId} 已裁决。`)
  } catch (error) {
    setReportFeedback(error.message, 'danger')
  } finally {
    reportSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.allSettled([loadVerifications(), loadReports()])
})
</script>

<template>
  <DashboardShell active-label="管理后台">
    <main
      class="admin-page"
      :aria-busy="verificationsLoading || reportsLoading"
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
          <h1>审核认证、裁决举报与处理任务</h1>
          <span>面向管理员角色的集中处理台，直接对接管理员 API 文档中的专属端点。</span>
        </div>
        <StatusBadge
          :label="isAdmin ? '管理员权限' : '非管理员账号'"
          :tone="isAdmin ? 'success' : 'danger'"
        />
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
            <button
              class="icon-action"
              type="button"
              aria-label="刷新待审认证"
              :disabled="verificationsLoading"
              @click="loadVerifications"
            >
              <RefreshCw
                :class="{ spinning: verificationsLoading }"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>

          <div class="inline-controls">
            <label class="field-control">
              <span>页码</span>
              <input
                v-model.number="verificationPage.page"
                type="number"
                min="1"
              >
            </label>
            <label class="field-control">
              <span>每页</span>
              <input
                v-model.number="verificationPage.size"
                type="number"
                min="1"
                max="100"
              >
            </label>
          </div>

          <div class="inline-controls">
            <span
              v-if="verificationFeedback"
              class="report-sync-hint"
              :class="verificationFeedbackType === 'danger' ? 'report-sync-hint--error' : ''"
            >
              {{ verificationFeedback }}
            </span>
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

          <article
            v-for="record in verifications"
            v-else
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
            <span
              v-if="reportFeedback"
              class="report-sync-hint"
              :class="reportFeedbackType === 'danger' ? 'report-sync-hint--error' : ''"
            >
              {{ reportFeedback }}
            </span>
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
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.055);
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
  background: linear-gradient(135deg, #eef2ff, #f0fdfa);
  color: #4f46e5;
}

.title-copy {
  display: grid;
  gap: 6px;
}

.title-copy p,
.section-kicker {
  margin: 0;
  color: #4f46e5;
  font-size: 0.82rem;
  font-weight: 900;
}

.title-copy h1,
.panel-heading h2 {
  margin: 0;
  color: #0f172a;
  font-weight: 900;
  line-height: 1.15;
}

.title-copy h1 {
  font-size: 1.5rem;
}

.title-copy span,
.panel-copy {
  color: #475569;
  line-height: 1.6;
}

.page-feedback,
.operation-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 13px;
  color: #075985;
  line-height: 1.5;
}

.page-feedback--danger,
.operation-feedback--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.operation-feedback {
  border-radius: 8px;
}

.operation-feedback--success {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
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
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
}

.admin-metric span {
  color: #64748b;
  font-size: 0.86rem;
  font-weight: 800;
}

.admin-metric strong {
  color: #0f172a;
  font-size: 1.65rem;
  line-height: 1;
}

.admin-metric--warning {
  background: #fffbeb;
}

.admin-metric--info {
  background: #eff6ff;
}

.admin-metric--accent {
  background: #f0fdfa;
}

.admin-section-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: fit-content;
  max-width: 100%;
  padding: 4px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #f8fafc;
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
  color: #334155;
  font-weight: 900;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.admin-section-switch button.active {
  border-color: #c7d2fe;
  background: #eef2ff;
  color: #4338ca;
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

.field-control {
  display: grid;
  gap: 7px;
}

.field-control--wide {
  grid-column: 1 / -1;
}

.field-control span {
  color: #334155;
  font-size: 0.84rem;
  font-weight: 900;
}

.field-control input,
.field-control select,
.field-control textarea {
  width: 100%;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: #0f172a;
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
  border-color: #818cf8;
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.12);
}

.field-control input[aria-invalid="true"],
.field-control textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.field-control small {
  color: #64748b;
  line-height: 1.45;
}

.field-error {
  color: #b91c1c !important;
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
  color: #334155;
  font-weight: 900;
}

.toggle-row input {
  width: 18px;
  height: 18px;
  accent-color: #4f46e5;
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
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #4f46e5;
}

.primary-action {
  padding: 0 14px;
  border: 1px solid #4f46e5;
  background: #4f46e5;
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(79, 70, 229, 0.16);
}

.ghost-action {
  padding: 0 12px;
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #4f46e5;
}

.danger-action {
  padding: 0 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.icon-action:hover:not(:disabled),
.ghost-action:hover:not(:disabled) {
  border-color: #c7d2fe;
  background: #eef2ff;
}

.primary-action:hover:not(:disabled) {
  background: #4338ca;
}

.danger-action:hover:not(:disabled) {
  border-color: #fca5a5;
  background: #fee2e2;
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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
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
  color: #0f172a;
  font-size: 1rem;
}

.record-card-head span,
.report-card small,
.moderation-record-card span {
  color: #64748b;
  line-height: 1.45;
}

.report-card p,
.task-moderation-card p,
.moderation-record-card p {
  margin: 0;
  color: #334155;
  line-height: 1.55;
}

.report-card {
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.report-card:hover {
  border-color: #c4b5fd;
  box-shadow: 0 2px 10px rgba(116, 109, 195, 0.08);
}

.report-card--active {
  border-color: #a78bfa;
  background: #f5f3ff;
  box-shadow: 0 0 0 3px rgba(116, 109, 195, 0.15);
}

.readonly-id-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.readonly-id-row input {
  flex: 1;
  cursor: default;
  background: #f8fafc;
}

.readonly-id-row .ghost-action {
  flex-shrink: 0;
  font-size: 0.82rem;
}

.report-sync-hint {
  margin-left: auto;
  font-size: 0.83rem;
  color: #059669;
  font-weight: 600;
  white-space: nowrap;
}

.report-sync-hint--error {
  color: #dc2626;
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
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
}

.record-meta-grid dt {
  color: #64748b;
  font-size: 0.78rem;
}

.record-meta-grid dd {
  margin: 0;
  color: #0f172a;
  font-weight: 900;
}

.evidence-link {
  width: fit-content;
  color: #4338ca;
  font-weight: 900;
}

.loading-state,
.empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: #0f172a;
}

.empty-state p {
  max-width: 420px;
  margin: 0;
  line-height: 1.55;
}

.moderation-record-card {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.moderation-record-card strong {
  color: #166534;
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
}
</style>
