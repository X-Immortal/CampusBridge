<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  Award, BookOpen, BriefcaseBusiness,
  CheckCircle2, ClipboardCheck, Clock3, FileCheck2, FileWarning,
  Inbox, Info, LoaderCircle, MapPin, MessageCircle, Package,
  Pencil, RefreshCw, Repeat2, ShieldAlert, UserRound, Utensils, X
} from '@lucide/vue'
import StatusBadge from './StatusBadge.vue'
import UserProfileModal from './UserProfileModal.vue'
import { optionLabel, TASK_STATUS_OPTIONS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'
import { useGuestAction } from '../composables/useGuestAction'

const props = defineProps({ taskId: { type: [Number, String], default: null } })
const emit = defineEmits(['close', 'updated'])

const { isAuthenticated, currentUserId, verificationStatus } = useAuth()
const { showToast } = useToast()
const { requireLoginAction } = useGuestAction()

const TASK_TYPE_META = {
  ERRAND: { label: '跑腿代取', icon: Package, tone: 'purple' },
  DINING: { label: '生活服务', icon: Utensils, tone: 'orange' },
  STUDY: { label: '学习互助', icon: BookOpen, tone: 'blue' },
  AGENCY: { label: '委托代办', icon: BriefcaseBusiness, tone: 'green' }
}

// --- state ---
const taskDetail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const operationLoading = ref('')
const profileUserId = ref(null)
const showProfileModal = ref(false)
const profileRelatedId = ref(null)
const cancelPanelOpen = ref(false)
const cancelReason = ref('')
const cancelReasonError = ref('')
const applicationPanelOpen = ref(false)
const applicationMessage = ref('')
const applicationMessageError = ref('')
const applicationSubmitting = ref(false)
const completionDescription = ref('')
const completionAttachments = ref([])
const completionUploading = ref(false)
const completionErrors = reactive({})
const completionSubmitting = ref(false)
const resolutionAction = ref('')
const resolutionReason = ref('')
const resolutionReasonError = ref('')
const candidatePanelOpen = ref(false)
const candidateApplications = ref([])
const candidatesLoaded = ref(false)
const candidatesLoading = ref(false)
const candidatesError = ref('')
const reportPanelOpen = ref(false)
const reportSubmitting = ref(false)
const reportForm = reactive({ targetType: 'TASK', targetId: '', reason: '', description: '' })
const reportErrors = reactive({})
const reportsLoading = ref(false)
const reportsError = ref('')
const myReports = ref([])

const editableStatuses = new Set(['DRAFT', 'OPEN'])
const cancelableStatuses = new Set(['DRAFT', 'OPEN', 'ACCEPTED', 'IN_PROGRESS'])
const repostableStatuses = new Set(['CANCELLED', 'DRAFT'])

// --- computed ---
const detailTask = computed(() => taskDetail.value?.task || null)
const detailApplications = computed(() => taskDetail.value?.applications || [])
const completionProof = computed(() => taskDetail.value?.completionProof || detailTask.value?.completionProof || null)
const proofAttachmentItems = computed(() => {
  if (!completionProof.value) return []
  const attrs = completionProof.value.attachmentUrls || completionProof.value.attachments || []
  return attrs.map((item, i) => typeof item === 'string' ? { url: item, name: `凭证 ${i + 1}` } : normalizeAttachmentItem(item, i))
})
const isDetailOwner = computed(() => {
  const rid = detailTask.value?.requesterId
  return rid != null && currentUserId.value != null && String(rid) === String(currentUserId.value)
})
const isDetailProvider = computed(() => {
  const t = detailTask.value
  const pid = t?.providerId ?? t?.helperId ?? t?.acceptedApplicantId
  return pid != null && currentUserId.value != null && String(pid) === String(currentUserId.value)
})
const canEditDetailTask = computed(() => isDetailOwner.value && editableStatuses.has(detailTask.value?.status))
const canCancelDetailTask = computed(() => isDetailOwner.value && cancelableStatuses.has(detailTask.value?.status))
const canWithdrawDetailTask = computed(() => isDetailProvider.value && ['ACCEPTED', 'IN_PROGRESS'].includes(detailTask.value?.status))
const canRepostDetailTask = computed(() => isDetailOwner.value && repostableStatuses.has(detailTask.value?.status))
const hasDetailActions = computed(() => canEditDetailTask.value || canCancelDetailTask.value || canWithdrawDetailTask.value || canRepostDetailTask.value)
const isProviderWithdrawal = computed(() => canWithdrawDetailTask.value && !isDetailOwner.value)
const cancelActionTitle = computed(() => isProviderWithdrawal.value ? '退出任务' : '取消任务')
const cancelActionWillDeductPoint = computed(() => detailTask.value?.status === 'IN_PROGRESS')
const canStartDetailTask = computed(() => isDetailProvider.value && detailTask.value?.status === 'ACCEPTED')
const canSubmitCompletion = computed(() => isDetailProvider.value && detailTask.value?.status === 'IN_PROGRESS')
const canReviewCompletion = computed(() => isDetailOwner.value && detailTask.value?.status === 'PENDING_CONFIRMATION')
const shouldShowCompletionPanel = computed(() => ['ACCEPTED', 'IN_PROGRESS', 'PENDING_CONFIRMATION', 'COMPLETED', 'REVIEWED', 'DISPUTED'].includes(detailTask.value?.status))
const canOpenConversation = computed(() => (isDetailOwner.value || isDetailProvider.value) && ['ACCEPTED', 'IN_PROGRESS', 'PENDING_CONFIRMATION', 'COMPLETED', 'REVIEWED', 'DISPUTED'].includes(detailTask.value?.status))
const detailConversationPeerUserId = computed(() => {
  const t = detailTask.value
  if (!t || !currentUserId.value) return null
  const rid = t.requesterId ?? t.ownerId
  const pid = t.providerId ?? t.helperId ?? t.acceptedApplicantId
  return String(currentUserId.value) === String(rid) ? pid : rid
})
const completionStageItems = computed(() => buildCompletionStageItems(detailTask.value?.status))
const completionPanelCopy = computed(() => {
  const s = detailTask.value?.status
  if (s === 'ACCEPTED') return '任务已被接取，等待执行方开始。'
  if (s === 'IN_PROGRESS') return isDetailProvider.value ? '任务进行中，完成后请提交凭证。' : '任务进行中，等待执行方提交完成。'
  if (s === 'PENDING_CONFIRMATION') return isDetailOwner.value ? '执行方已提交完成，请确认或驳回。' : '已提交完成，等待发布者确认。'
  if (s === 'COMPLETED' || s === 'REVIEWED') return '任务已完成。'
  return ''
})
const resolutionMeta = computed(() => ({
  CONFIRM_COMPLETION: { title: '确认完成', tone: 'success' },
  REJECT_COMPLETION: { title: '驳回完成', tone: 'danger' },
  DISPUTE: { title: '发起争议', tone: 'danger' }
}[resolutionAction.value] || null))
const proofDescription = computed(() => completionProof.value?.description || '')
const proofSubmittedAt = computed(() => completionProof.value?.submittedAt || null)
const hasCurrentUserApplied = computed(() => detailApplications.value.some(a => String(a.applicantId) === String(currentUserId.value)))
const canApplyDetailTask = computed(() => detailTask.value?.status === 'OPEN' && currentUserId.value && !isDetailOwner.value && !hasCurrentUserApplied.value)
const applicationGate = computed(() => {
  if (!currentUserId.value) return { blocked: true, reason: '请先登录后再申请任务。' }
  if (verificationStatus.value !== 'VERIFIED') return { blocked: true, reason: '请先完成校内认证后再申请任务。' }
  return { blocked: false, reason: '' }
})
const candidates = computed(() => candidateApplications.value.map(normalizeCandidate))
const acceptedCandidate = computed(() => candidates.value.find(c => c.status === 'ACCEPTED') || null)
const sortedCandidates = computed(() => [...candidates.value].sort((a, b) => candidateScore(b) - candidateScore(a)))
const candidateStats = computed(() => {
  const items = candidates.value
  return {
    total: items.length,
    pendingCount: items.filter(c => c.status === 'PENDING').length,
    verifiedCount: items.filter(c => c.verified).length
  }
})
const isMineView = computed(() => false)
const selectedDetailReport = computed(() => null)
const detailTaskReports = computed(() => [])
function reportStatusLabel(s) { return s || '未知' }
function reportStatusTone() { return 'muted' }
function applicationStatusLabel(s) { return s || '未知' }
function applicationStatusTone() { return 'muted' }
function reportTargetLabel(s) { return s || '' }

// --- utility functions ---
function formatDateTime(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
function taskStatusLabel(s) { return optionLabel(TASK_STATUS_OPTIONS, s) }
function taskStatusTone(s) { return TASK_STATUS_OPTIONS.find(o => o.value === s)?.tone || 'muted' }
function taskTypeMeta(t) { return TASK_TYPE_META[t?.category] || { label: t?.category || '未知', icon: Package, tone: 'muted' } }
function taskCategoryLabel(t) { return taskTypeMeta(t).label }
function taskRewardValue(t) {
  if (!t) return '免费'; if (t.rewardType === 'FREE') return '免费互助'
  if (t.rewardType === 'NEGOTIABLE') return '双方协商'
  if (t.rewardType === 'POINTS') return `${t.rewardAmount || 0} 积分`
  if (t.rewardType === 'CASH') return `¥${t.rewardAmount || 0}`
  return t.rewardDescription || t.rewardType || '免费'
}
function taskRewardUnit(t) {
  if (!t || t.rewardType === 'FREE' || t.rewardType === 'NEGOTIABLE') return ''
  if (t.rewardType === 'CASH' && t.rewardAmount) return '元'
  if (t.rewardType === 'POINTS' && t.rewardAmount) return '积分'; return ''
}
function taskRewardTone(t) {
  if (!t) return ''; if (t.rewardType === 'FREE') return 'free'
  if (t.rewardType === 'NEGOTIABLE') return 'negotiable'; return 'points'
}
function taskContactCopy(task = {}) { return task.onlyInAppContact ? '仅站内沟通' : '可协商站外沟通' }
function taskSafetyCopy(task = {}) { return task.allowOfflineContact ? '公开地点交接' : '不建议线下接触' }
function formatRelativeTime(value) {
  if (!value) return '暂无记录'
  const d = new Date(value); if (Number.isNaN(d.getTime())) return value
  const m = Math.max(0, Math.round((Date.now() - d.getTime()) / 60000))
  if (m < 1) return '刚刚发布'; if (m < 60) return `${m} 分钟前发布`
  const h = Math.round(m / 60); if (h < 24) return `${h} 小时前发布`
  return `${Math.round(h / 24)} 天前发布`
}
function shortDescription(text) {
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return !s ? '暂无描述' : s.length > 120 ? s.slice(0, 120) + '...' : s
}
function deadlineCopy(value) {
  if (!value) return '未设置'
  const d = new Date(value); if (Number.isNaN(d.getTime())) return value
  const diff = d.getTime() - Date.now(); if (diff < 0) return '已过期'
  const h = Math.floor(diff / 3600000)
  return h < 24 ? `${h} 小时内` : `${Math.floor(h / 24)} 天后 (${formatDateTime(value)})`
}
function normalizeAttachmentItem(item, i) {
  if (!item) return { url: '', name: `附件 ${i + 1}` }
  return { url: item.fileUrl || item.url || '', name: item.fileName || item.name || `附件 ${i + 1}`, id: item.id || i }
}
function buildCompletionStageItems(status) {
  const stages = [
    { key: 'ACCEPTED', label: '已接取', done: false, active: false },
    { key: 'IN_PROGRESS', label: '进行中', done: false, active: false },
    { key: 'PENDING_CONFIRMATION', label: '待确认', done: false, active: false },
    { key: 'COMPLETED', label: '已完成', done: false, active: false }
  ]
  const idx = stages.findIndex(s => s.key === status)
  if (idx >= 0) {
    for (let i = 0; i < idx; i++) stages[i].done = true
    if (status === 'COMPLETED' || status === 'REVIEWED') stages.forEach(s => s.done = true)
    else stages[idx].active = true
  }
  return stages
}
function normalizeTaskDetail(r) {
  if (!r) return null
  return r.task ? { ...r, task: r.task } : { task: r, applications: [], completionProof: null, attachments: [], history: [], reviews: [] }
}

// --- watchers ---
watch(() => props.taskId, (id) => {
  if (id) {
    cancelPanelOpen.value = false
    applicationPanelOpen.value = false
    candidatePanelOpen.value = false
    reportPanelOpen.value = false
    resolutionAction.value = ''
    loadTaskDetail(id)
  } else {
    taskDetail.value = null
    detailError.value = ''
  }
}, { immediate: true })

// --- actions ---
async function loadTaskDetail(id) {
  if (!id) return
  detailLoading.value = true; detailError.value = ''
  try { taskDetail.value = normalizeTaskDetail(await api.getTaskDetail(id)) }
  catch (e) { detailError.value = e.message || '加载失败' }
  finally { detailLoading.value = false }
}
function clearTask() { emit('close') }
function openProfileModal(uid, tid) { if (!uid) return; profileUserId.value = uid; profileRelatedId.value = tid; showProfileModal.value = true }

// cancel
function openCancelPanel() { cancelReason.value = ''; cancelReasonError.value = ''; cancelPanelOpen.value = true }
function closeCancelPanel() { cancelPanelOpen.value = false }
async function submitCancelTask() {
  if (!cancelReason.value.trim()) { cancelReasonError.value = '请填写原因'; return }
  operationLoading.value = 'cancel'
  try {
    if (canWithdrawDetailTask.value && !isDetailOwner.value) await api.withdrawFromTask(detailTask.value.id, { reason: cancelReason.value.trim() })
    else await api.cancelTask(detailTask.value.id, cancelReason.value.trim())
    closeCancelPanel(); emit('updated'); await loadTaskDetail(detailTask.value.id)
  } catch (e) { cancelReasonError.value = e.message || '操作失败' }
  finally { operationLoading.value = '' }
}

// application
function openApplicationPanel() { applicationMessage.value = ''; applicationMessageError.value = ''; applicationPanelOpen.value = true }
function closeApplicationPanel() { applicationPanelOpen.value = false }
async function submitTaskApplication() {
  if (!applicationMessage.value.trim()) { applicationMessageError.value = '请填写申请说明'; return }
  applicationSubmitting.value = true
  try { await api.createTaskApplication(detailTask.value.id, { message: applicationMessage.value.trim() }); closeApplicationPanel(); emit('updated'); await loadTaskDetail(detailTask.value.id) }
  catch (e) { applicationMessageError.value = e.message || '申请失败' }
  finally { applicationSubmitting.value = false }
}

// completion
function removeCompletionAttachment(i) { completionAttachments.value.splice(i, 1) }
function focusCompletionForm() {
  const el = document.getElementById('completion-description')
  if (el) { el.focus(); el.scrollIntoView({ behavior: 'smooth', block: 'center' }) }
}
async function withdrawPendingApplication(task) {
  if (!task?.myApplicationId || task.myApplicationStatus !== 'PENDING') return
  if (!confirm('确认取消这次接单申请？')) return
  operationLoading.value = `withdraw-${task.myApplicationId}`
  try {
    await api.withdrawTaskApplication(task.myApplicationId)
    showToast('申请已取消', { type: 'success' })
    emit('updated')
    await loadTaskDetail(detailTask.value.id)
  } catch (e) { showToast(e.message || '操作失败', { type: 'danger' }) }
  finally { operationLoading.value = '' }
}
async function handleCompletionFiles(e) {
  const files = Array.from(e.target.files || []).filter(f => f.type.startsWith('image/'))
  if (completionAttachments.value.length + files.length > 9) { showToast('最多上传9张', { type: 'warning' }); return }
  completionUploading.value = true
  try {
    for (const f of files) { const r = await api.uploadAttachment({ file: f, expectedOwnerType: 'COMPLETION_PROOF', type: 'IMAGE' }); completionAttachments.value.push({ id: r?.id, file: f, name: r?.fileName || f.name, url: r?.fileUrl || r?.url }) }
  } catch (err) { showToast(err.message || '上传失败', { type: 'danger' }) }
  finally { completionUploading.value = false; e.target.value = '' }
}
async function startDetailTask() {
  operationLoading.value = 'start'
  try { await api.transitionTaskStatus(detailTask.value.id, { action: 'START' }); emit('updated'); await loadTaskDetail(detailTask.value.id) }
  catch (e) { showToast(e.message || '操作失败', { type: 'danger' }) }
  finally { operationLoading.value = '' }
}
async function submitCompletionProof() {
  if (!completionDescription.value.trim()) { completionErrors.description = '请填写完成说明'; return }
  completionSubmitting.value = true
  try { await api.submitTaskCompletion(detailTask.value.id, { description: completionDescription.value.trim(), attachmentIds: completionAttachments.value.map(a => a.id).filter(Boolean) }); completionDescription.value = ''; completionAttachments.value = []; emit('updated'); await loadTaskDetail(detailTask.value.id) }
  catch (e) { showToast(e.message || '提交失败', { type: 'danger' }) }
  finally { completionSubmitting.value = false }
}
async function submitQuickConfirmCompletion() {
  operationLoading.value = 'confirm'
  try { await api.confirmTaskCompletion(detailTask.value.id); emit('updated'); await loadTaskDetail(detailTask.value.id) }
  catch (e) { showToast(e.message || '操作失败', { type: 'danger' }) }
  finally { operationLoading.value = '' }
}
function openCompletionResolution(a) { resolutionAction.value = a; resolutionReason.value = ''; resolutionReasonError.value = '' }
function closeCompletionResolution() { resolutionAction.value = '' }
async function submitCompletionResolution() {
  if (!resolutionReason.value.trim()) { resolutionReasonError.value = '请填写原因'; return }
  operationLoading.value = 'resolve'
  try {
    if (resolutionAction.value === 'REJECT_COMPLETION') await api.rejectTaskCompletion(detailTask.value.id, { reason: resolutionReason.value.trim() })
    else if (resolutionAction.value === 'DISPUTE') await api.disputeTaskCompletion(detailTask.value.id, { reason: resolutionReason.value.trim() })
    resolutionAction.value = ''; emit('updated'); await loadTaskDetail(detailTask.value.id)
  } catch (e) { resolutionReasonError.value = e.message || '操作失败' }
  finally { operationLoading.value = '' }
}
async function openTaskConversation() {
  if (!detailConversationPeerUserId.value) return
  try { await api.createConversation({ relatedType: 'TASK', relatedId: detailTask.value.id, peerUserId: detailConversationPeerUserId.value }) } catch { void 0 }
  try {
    const convs = await api.getConversations()
    const t = (convs?.items || convs || []).find(c => String(c.relatedId) === String(detailTask.value.id) && String(c.peerUserId) === String(detailConversationPeerUserId.value))
    if (t) window.location.href = `/workspace/messages?conversationId=${t.id}`
  } catch { void 0 }
}
function openEditTask() { window.location.href = `/tasks/${detailTask.value.id}/edit` }
function openRepostTask() { window.location.href = `/tasks/${detailTask.value.id}/resend` }
function openCandidatePanel() { candidatePanelOpen.value = true; loadCandidates(detailTask.value?.id) }
function closeCandidatePanel() { candidatePanelOpen.value = false }
async function openReportPanel() {
  if (!detailTask.value?.id) return
  if (!requireLoginAction('提交举报')) return
  reportPanelOpen.value = true
  hydrateReportForm()
}
function closeReportPanel() { reportPanelOpen.value = false; clearReportErrors() }
function hydrateReportForm() {
  reportForm.targetType = 'TASK'
  reportForm.targetId = detailTask.value?.id ? String(detailTask.value.id) : ''
  reportForm.reason = ''
  reportForm.description = ''
  clearReportErrors()
}
function clearReportErrors() { Object.keys(reportErrors).forEach(k => delete reportErrors[k]) }
function setReportError(field, msg) { reportErrors[field] = msg }
function validateReportForm() {
  clearReportErrors()
  if (!String(reportForm.reason).trim()) setReportError('reason', '请填写举报原因。')
  if (reportForm.reason.trim().length > 100) setReportError('reason', '举报原因不能超过 100 个字符。')
  if (reportForm.description.trim().length > 1000) setReportError('description', '详细描述不能超过 1000 个字符。')
  return Object.keys(reportErrors).length === 0
}
async function submitReport() {
  if (!validateReportForm()) return
  reportSubmitting.value = true
  try {
    await api.createReport({ targetType: reportForm.targetType, targetId: Number(reportForm.targetId), reason: reportForm.reason.trim(), description: reportForm.description.trim() })
    showToast('举报已提交，管理员会根据站内记录处理。', { type: 'success' })
    closeReportPanel()
  } catch (e) { showToast(e.message || '举报提交失败', { type: 'danger' }) }
  finally { reportSubmitting.value = false }
}

/* candidate helpers */
function candidateInitial(c = {}) { return String((c.name || c.username || c.applicantId || '候')).trim().slice(0, 1).toUpperCase() }
function candidateAccountCopy(c = {}) { return c.username ? `@${c.username}` : c.applicantId ? `用户 ID ${c.applicantId}` : '账号信息待同步' }
function candidateProfileCopy(c = {}) { const p = [c.campus, c.college].filter(Boolean); return p.length ? p.join(' · ') : '档案信息待补充' }
function candidateSubmittedCopy(c = {}) { return c.createdAt ? `申请于 ${formatDateTime(c.createdAt)}` : '申请时间待同步' }
function candidateCardClass(c = {}) { return `candidate-card--${String(c.status || 'PENDING').toLowerCase()}` }
function candidateScore(c) { return (c.verified ? 10 : 0) + Math.min(10, c.creditLevel || 1) + (c.completedCount || 0) }
async function loadCandidates(taskId) {
  if (!taskId || !isAuthenticated.value || !isDetailOwner.value) return
  candidatesLoading.value = true; candidatesError.value = ''
  try {
    const result = await api.getTaskApplications(taskId)
    candidateApplications.value = Array.isArray(result) ? result : (result?.applications || result?.items || [])
    candidatesLoaded.value = true
  } catch (e) { candidateApplications.value = []; candidatesLoaded.value = true; candidatesError.value = e.message || '加载失败' }
  finally { candidatesLoading.value = false }
}
async function acceptCandidate(candidate) {
  if (!detailTask.value?.id || !candidate?.applicationId) return
  operationLoading.value = `accept-${candidate.applicationId}`
  try {
    await api.acceptTaskApplication(detailTask.value.id, candidate.applicationId)
    showToast('已选择帮助者', { type: 'success' })
    closeCandidatePanel()
    emit('updated')
    await loadTaskDetail(detailTask.value.id)
  } catch (e) { showToast(e.message || '操作失败', { type: 'danger' }) }
  finally { operationLoading.value = '' }
}
async function loadMyReports() {
  if (!isAuthenticated.value) return
  reportsLoading.value = true; reportsError.value = ''
  try {
    const result = await api.getReports({ page: 1, size: 50 })
    myReports.value = Array.isArray(result) ? result : (result?.items || result?.records || [])
  } catch (e) { reportsError.value = e.message || '加载失败' }
  finally { reportsLoading.value = false }
}
function normalizeCandidate(app = {}) {
  const aid = app.applicantId ?? app.userId ?? app.applicant?.id
  return {
    applicationId: app.applicationId ?? app.id, applicantId: aid,
    name: app.nickname || app.applicantName || app.username || app.applicant?.nickname || (aid ? `同学 ${aid}` : '候选同学'),
    username: app.username || app.applicant?.username || '', avatarUrl: app.avatarUrl || app.applicant?.avatarUrl || '',
    campus: app.campus || app.applicant?.campus || '', college: app.college || app.department || app.applicant?.college || '',
    tags: [], verified: Boolean(app.verified ?? app.isVerified ?? app.applicant?.verified),
    creditLevel: Math.max(1, Number(app.creditLevel || app.level || app.credit?.level || 1) || 1),
    points: Number(app.points || app.creditPoints || app.credit?.points || 0),
    completedCount: Number(app.completedCount || app.completedTasks || app.completedTaskCount || 0),
    message: app.message || '', status: String(app.status || 'PENDING').toUpperCase(),
    createdAt: app.createdAt || app.appliedAt || ''
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.taskId"
      class="task-detail-modal-layer"
      @click.self="clearTask"
    >
      <section
        class="panel-card detail-panel task-detail-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-detail-modal-title"
      >
        <div class="detail-topbar">
          <div>
            <p class="section-kicker">
              任务详情
            </p>
            <h2 id="task-detail-modal-title">
              {{ detailTask?.title || '正在加载任务详情' }}
            </h2>
          </div>
          <button
            class="icon-action"
            type="button"
            aria-label="关闭任务详情"
            @click="clearTask"
          >
            <X
              :size="18"
              aria-hidden="true"
            />
          </button>
        </div>

        <div
          v-if="detailLoading"
          class="loading-state"
          role="status"
        >
          <LoaderCircle
            class="spinning"
            :size="24"
            aria-hidden="true"
          />
          <span>正在加载任务详情...</span>
        </div>

        <div
          v-else-if="detailError"
          class="detail-error"
          role="status"
        >
          <Info
            :size="18"
            aria-hidden="true"
          />
          <span>{{ detailError }}</span>
        </div>

        <template v-else-if="detailTask">
          <article class="task-detail-showcase">
            <header class="detail-card-header">
              <div class="detail-chip-row">
                <StatusBadge
                  :label="taskStatusLabel(detailTask.status)"
                  :tone="taskStatusTone(detailTask.status)"
                />
                <span
                  class="detail-type-chip"
                  :class="`detail-type-chip--${taskTypeMeta(detailTask).tone}`"
                >
                  {{ taskCategoryLabel(detailTask) }}
                </span>
              </div>
              <span class="detail-task-id">任务 ID #{{ detailTask.id }}</span>
            </header>

            <div class="detail-title-block">
              <span
                class="detail-type-icon"
                :class="`detail-type-icon--${taskTypeMeta(detailTask).tone}`"
              >
                <component
                  :is="taskTypeMeta(detailTask).icon"
                  :size="28"
                  aria-hidden="true"
                />
              </span>
              <div>
                <h3>{{ detailTask.title || '未命名任务' }}</h3>
                <p>{{ shortDescription(detailTask.description) }}</p>
              </div>
            </div>

            <div
              class="detail-reward-strip"
              :class="`detail-reward-strip--${taskRewardTone(detailTask)}`"
            >
              <span>奖励</span>
              <strong>{{ taskRewardValue(detailTask) }}</strong>
              <small>{{ taskRewardUnit(detailTask) }}</small>
            </div>

            <dl class="detail-info-list">
              <div>
                <dt>
                  <UserRound
                    :size="16"
                    aria-hidden="true"
                  />
                  发布者
                </dt>
                <dd>
                  {{ detailTask.requesterName || `同学 ${detailTask.requesterId || ''}` || '未知' }}
                  <button
                    v-if="detailTask.requesterId"
                    class="inline-profile-btn"
                    type="button"
                    @click="openProfileModal(detailTask.requesterId, detailTask.id)"
                  >
                    <UserRound :size="14" /> 查看
                  </button>
                </dd>
              </div>
              <div>
                <dt>
                  <MapPin
                    :size="16"
                    aria-hidden="true"
                  />
                  任务地点
                </dt>
                <dd>
                  {{ detailTask.campus || '未填校区' }} · {{ detailTask.location || '未填地点' }}
                  <template v-if="detailTask.category === 'ERRAND' && detailTask.deliveryLocation">
                    → {{ detailTask.deliveryLocation }}
                  </template>
                </dd>
              </div>
              <div>
                <dt>
                  <Clock3
                    :size="16"
                    aria-hidden="true"
                  />
                  截止时间
                </dt>
                <dd>{{ deadlineCopy(detailTask.deadline) }} · {{ formatDateTime(detailTask.deadline) }}</dd>
              </div>
              <div>
                <dt>任务说明</dt>
                <dd>{{ detailTask.description || '求助者暂未补充说明。' }}</dd>
              </div>
              <div>
                <dt>完成标准</dt>
                <dd>{{ detailTask.completionStandard || '暂未填写完成标准。' }}</dd>
              </div>
              <div>
                <dt>完成凭证</dt>
                <dd>{{ detailTask.requiresProof ? '需要凭证（按完成标准提交图片或说明）' : '无需凭证' }}</dd>
              </div>
              <div>
                <dt>沟通方式</dt>
                <dd>
                  <span class="detail-soft-pill">{{ taskContactCopy(detailTask) }}</span>
                </dd>
              </div>
              <div>
                <dt>安全提示</dt>
                <dd>
                  <span class="detail-warning-pill">
                    <ShieldAlert
                      :size="14"
                      aria-hidden="true"
                    />
                    {{ taskSafetyCopy(detailTask) }}
                  </span>
                </dd>
              </div>
              <div>
                <dt>发布时间</dt>
                <dd>{{ formatDateTime(detailTask.createdAt) }}（{{ formatRelativeTime(detailTask.createdAt) }}）</dd>
              </div>
            </dl>

            <section
              v-if="shouldShowCompletionPanel"
              class="completion-panel"
              aria-labelledby="task-completion-title"
            >
              <header class="completion-heading">
                <div>
                  <p class="section-kicker">
                    完成确认
                  </p>
                  <h3 id="task-completion-title">
                    凭证与结果处理
                  </h3>
                  <p>{{ completionPanelCopy }}</p>
                </div>
                <StatusBadge
                  :label="taskStatusLabel(detailTask.status)"
                  :tone="taskStatusTone(detailTask.status)"
                />
              </header>

              <div
                class="completion-steps"
                aria-label="任务完成流程"
              >
                <span
                  v-for="stage in completionStageItems"
                  :key="stage.label"
                  :class="{ done: stage.done, active: stage.active }"
                >
                  <CheckCircle2
                    v-if="stage.done"
                    :size="15"
                    aria-hidden="true"
                  />
                  <Clock3
                    v-else
                    :size="15"
                    aria-hidden="true"
                  />
                  {{ stage.label }}
                </span>
              </div>

              <section
                v-if="completionProof"
                class="proof-card"
                aria-labelledby="completion-proof-title"
              >
                <header class="proof-card-heading">
                  <div>
                    <strong id="completion-proof-title">已提交的完成凭证</strong>
                    <span>{{ proofSubmittedAt ? formatDateTime(proofSubmittedAt) : '提交时间待同步' }}</span>
                  </div>
                  <FileCheck2
                    :size="20"
                    aria-hidden="true"
                  />
                </header>
                <p>{{ proofDescription || '帮助者暂未填写完成说明。' }}</p>
                <div
                  v-if="proofAttachmentItems.length"
                  class="proof-attachment-list"
                >
                  <a
                    v-for="attachment in proofAttachmentItems"
                    :key="attachment.id || attachment.url || attachment.name"
                    :href="attachment.url || undefined"
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FileCheck2
                      :size="14"
                      aria-hidden="true"
                    />
                    {{ attachment.name }}
                  </a>
                </div>
                <span
                  v-else
                  class="proof-empty"
                >
                  暂无附件，仅提交了文字说明。
                </span>
              </section>

              <section
                v-if="canStartDetailTask"
                class="completion-action-box"
              >
                <div>
                  <strong>开始执行任务</strong>
                  <span>开始后状态会从已选人进入进行中，完成后即可提交凭证。</span>
                </div>
                <button
                  class="primary-action"
                  type="button"
                  :disabled="operationLoading === 'start'"
                  @click="startDetailTask"
                >
                  <LoaderCircle
                    v-if="operationLoading === 'start'"
                    class="spinning"
                    :size="16"
                    aria-hidden="true"
                  />
                  <CheckCircle2
                    v-else
                    :size="16"
                    aria-hidden="true"
                  />
                  开始执行
                </button>
              </section>

              <form
                v-if="canSubmitCompletion"
                class="completion-form"
                @submit.prevent="submitCompletionProof"
              >
                <label
                  class="completion-field"
                  for="completion-description"
                >
                  <span>完成说明</span>
                  <textarea
                    id="completion-description"
                    v-model="completionDescription"
                    maxlength="1000"
                    placeholder="说明完成结果、交付位置、时间或对方可核验的信息。"
                    :aria-invalid="Boolean(completionErrors.description)"
                    :disabled="completionSubmitting || completionUploading"
                  />
                  <small>{{ completionDescription.length }}/1000 · 必填，提交后任务进入待确认。</small>
                  <p
                    v-if="completionErrors.description"
                    class="field-error"
                  >
                    {{ completionErrors.description }}
                  </p>
                </label>

                <div class="completion-upload-grid completion-upload-grid--single">
                  <label class="file-pick-control">
                    <FileCheck2
                      :size="22"
                      aria-hidden="true"
                    />
                    <span>
                      <strong>{{ completionUploading ? '正在上传...' : '上传凭证附件' }}</strong>
                      <small>图片或文件，单个不超过 4MB，最多 9 个。</small>
                    </span>
                    <input
                      type="file"
                      multiple
                      :disabled="completionSubmitting || completionUploading"
                      @change="handleCompletionFiles"
                    >
                  </label>
                </div>

                <div
                  v-if="completionAttachments.length"
                  class="attachment-preview-list"
                >
                  <span
                    v-for="(attachment, index) in completionAttachments"
                    :key="attachment.id || attachment.url || attachment.name"
                    :class="{ 'attachment-preview-list__item--pending': !attachment.id }"
                  >
                    <FileCheck2
                      :size="14"
                      aria-hidden="true"
                    />
                    {{ attachment.name }}
                    <small v-if="attachment.id">#{{ attachment.id }}</small>
                    <small v-else>未上传</small>
                    <button
                      type="button"
                      :aria-label="`移除 ${attachment.name}`"
                      :disabled="completionSubmitting || completionUploading"
                      @click="removeCompletionAttachment(index)"
                    >
                      <X
                        :size="13"
                        aria-hidden="true"
                      />
                    </button>
                  </span>
                </div>

                <p
                  v-if="completionErrors.attachments"
                  class="field-error"
                >
                  {{ completionErrors.attachments }}
                </p>

                <button
                  class="primary-action completion-submit"
                  type="submit"
                  :disabled="completionSubmitting || completionUploading"
                >
                  <LoaderCircle
                    v-if="completionSubmitting"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <FileCheck2
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  提交完成凭证
                </button>
              </form>

              <section
                v-if="canReviewCompletion"
                class="completion-action-box"
              >
                <div>
                  <strong>处理完成结果</strong>
                  <span>驳回会退回进行中，帮助者可重新提交凭证；争议将交由平台处理。</span>
                </div>
                <div class="review-actions">
                  <button
                    class="ghost-action"
                    type="button"
                    :disabled="Boolean(operationLoading)"
                    @click="openCompletionResolution('REJECT_COMPLETION')"
                  >
                    驳回
                  </button>
                  <button
                    class="danger-action"
                    type="button"
                    :disabled="Boolean(operationLoading)"
                    @click="openCompletionResolution('DISPUTE')"
                  >
                    发起争议
                  </button>
                </div>
              </section>

              <section
                v-else-if="canDisputeCompletion"
                class="completion-action-box"
              >
                <div>
                  <strong>异常处理</strong>
                  <span>任务执行或凭证核验出现分歧时，可以发起争议并填写原因。</span>
                </div>
                <button
                  class="danger-action"
                  type="button"
                  :disabled="Boolean(operationLoading)"
                  @click="openCompletionResolution('DISPUTE')"
                >
                  发起争议
                </button>
              </section>

              <section
                v-if="resolutionMeta"
                class="resolution-panel"
                :class="`resolution-panel--${resolutionMeta.tone}`"
                aria-labelledby="completion-resolution-title"
              >
                <h3 id="completion-resolution-title">
                  {{ resolutionMeta.title }}
                </h3>
                <p>{{ resolutionMeta.copy }}</p>
                <label
                  v-if="resolutionAction !== 'CONFIRM_COMPLETION'"
                  class="completion-field"
                  for="completion-resolution-reason"
                >
                  <span>处理原因</span>
                  <textarea
                    id="completion-resolution-reason"
                    v-model="resolutionReason"
                    maxlength="300"
                    placeholder="请写清楚不符合完成标准或产生争议的具体原因。"
                    :aria-invalid="Boolean(resolutionReasonError)"
                    :disabled="Boolean(operationLoading)"
                  />
                  <small>{{ resolutionReason.length }}/300 · 驳回和争议必须填写原因。</small>
                  <p
                    v-if="resolutionReasonError"
                    class="field-error"
                  >
                    {{ resolutionReasonError }}
                  </p>
                </label>
                <div class="resolution-actions">
                  <button
                    class="ghost-action"
                    type="button"
                    :disabled="Boolean(operationLoading)"
                    @click="closeCompletionResolution"
                  >
                    取消
                  </button>
                  <button
                    class="primary-action"
                    type="button"
                    :disabled="Boolean(operationLoading)"
                    @click="submitCompletionResolution"
                  >
                    <LoaderCircle
                      v-if="operationLoading === resolutionAction"
                      class="spinning"
                      :size="16"
                      aria-hidden="true"
                    />
                    {{ resolutionMeta.button }}
                  </button>
                </div>
              </section>
            </section>
          </article>

          <div
            class="workflow-entry-grid"
            :class="{ 'workflow-entry-grid--compact': isMineView }"
          >
            <section
              v-if="!isDetailOwner"
              class="workflow-entry workflow-entry--application"
              :class="{ 'workflow-entry--locked': applicationGate }"
              aria-labelledby="task-application-entry-title"
            >
              <span class="workflow-entry__icon">
                <Award
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <div class="workflow-entry__body">
                <p class="section-kicker">
                  接单申请
                </p>
                <h3 id="task-application-entry-title">
                  申请成为帮助者
                </h3>
                <p>
                  {{ applicationGate?.copy || '填写时间或经验，等待选择。' }}
                </p>
              </div>
              <div class="workflow-entry__actions">
                <StatusBadge
                  v-if="applicationGate"
                  :label="applicationGate.title"
                  :tone="applicationGate.tone"
                />
                <button
                  v-if="detailTask.myApplicationStatus === 'PENDING'"
                  class="ghost-action workflow-entry__button"
                  type="button"
                  :disabled="operationLoading === `withdraw-application-${detailTask.myApplicationId}`"
                  @click="withdrawPendingApplication(detailTask)"
                >
                  <LoaderCircle
                    v-if="operationLoading === `withdraw-application-${detailTask.myApplicationId}`"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <X
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  取消申请
                </button>
                <button
                  v-else
                  class="primary-action workflow-entry__button"
                  type="button"
                  :disabled="applicationSubmitting"
                  @click="openApplicationPanel"
                >
                  <LoaderCircle
                    v-if="applicationSubmitting"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <Award
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  {{ canApplyDetailTask ? '填写申请' : '查看状态' }}
                </button>
              </div>
            </section>

            <section
              v-if="!isMineView"
              class="workflow-entry workflow-entry--report"
              aria-labelledby="task-report-entry-title"
            >
              <span class="workflow-entry__icon">
                <FileWarning
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <div class="workflow-entry__body">
                <p class="section-kicker">
                  举报与争议
                </p>
                <h3 id="task-report-entry-title">
                  提交任务举报
                </h3>
                <p>
                  {{
                    selectedDetailReport
                      ? `举报 #${selectedDetailReport.id}：${reportStatusLabel(selectedDetailReport.status)}。`
                      : '违规、纠纷或站外交易可提交举报。'
                  }}
                </p>
              </div>
              <div class="workflow-entry__actions">
                <StatusBadge
                  v-if="selectedDetailReport"
                  :label="reportStatusLabel(selectedDetailReport.status)"
                  :tone="reportStatusTone(selectedDetailReport.status)"
                />
                <button
                  class="ghost-action workflow-entry__button"
                  type="button"
                  :disabled="!isAuthenticated || reportSubmitting"
                  @click="openReportPanel"
                >
                  <FileWarning
                    :size="17"
                    aria-hidden="true"
                  />
                  {{ selectedDetailReport ? '查看/再举报' : '提交举报' }}
                </button>
              </div>
            </section>

            <section
              v-else
              class="workflow-entry workflow-entry--confirmation"
              aria-labelledby="task-confirmation-entry-title"
            >
              <span class="workflow-entry__icon">
                <FileCheck2
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <div class="workflow-entry__body">
                <p class="section-kicker">
                  状态确认
                </p>
                <h3 id="task-confirmation-entry-title">
                  {{ isDetailOwner ? '确认任务结果' : '推进已接任务' }}
                </h3>
                <p>
                  {{ isDetailOwner ? '帮助者提交后确认完成。' : '进行中后提交完成说明。' }}
                </p>
              </div>
              <div class="workflow-entry__actions">
                <StatusBadge
                  :label="taskStatusLabel(detailTask.status)"
                  :tone="taskStatusTone(detailTask.status)"
                />
                <button
                  v-if="canStartDetailTask"
                  class="primary-action workflow-entry__button"
                  type="button"
                  :disabled="operationLoading === 'start'"
                  @click="startDetailTask"
                >
                  <LoaderCircle
                    v-if="operationLoading === 'start'"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <CheckCircle2
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  开始任务
                </button>
                <button
                  v-else-if="canSubmitCompletion"
                  class="primary-action workflow-entry__button"
                  type="button"
                  @click="focusCompletionForm"
                >
                  <FileCheck2
                    :size="17"
                    aria-hidden="true"
                  />
                  提交凭证
                </button>
                <button
                  v-else-if="canReviewCompletion"
                  class="primary-action workflow-entry__button"
                  type="button"
                  :disabled="operationLoading === 'quick-confirm'"
                  @click="submitQuickConfirmCompletion"
                >
                  <LoaderCircle
                    v-if="operationLoading === 'quick-confirm'"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <CheckCircle2
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  确认完成
                </button>
              </div>
            </section>

            <section
              v-if="isDetailOwner"
              class="workflow-entry workflow-entry--candidates"
              aria-labelledby="task-candidates-entry-title"
            >
              <span class="workflow-entry__icon">
                <ClipboardCheck
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <div class="workflow-entry__body">
                <p class="section-kicker">
                  候选管理
                </p>
                <h3 id="task-candidates-entry-title">
                  接单候选
                </h3>
                <p>
                  {{ candidateStats.total }} 位候选，{{ candidateStats.pendingCount }} 位待选。
                </p>
              </div>
              <div class="workflow-entry__actions">
                <StatusBadge
                  v-if="acceptedCandidate"
                  :label="`已选择 ${acceptedCandidate.name}`"
                  tone="success"
                />
                <button
                  class="primary-action workflow-entry__button"
                  type="button"
                  :disabled="!detailTask?.id"
                  @click="openCandidatePanel"
                >
                  <ClipboardCheck
                    :size="16"
                    aria-hidden="true"
                  />
                  管理候选
                </button>
              </div>
            </section>
          </div>

          <footer
            v-if="canOpenConversation || hasDetailActions"
            class="detail-action-row"
          >
            <button
              v-if="canOpenConversation"
              class="primary-action detail-primary-action"
              type="button"
              :disabled="operationLoading === 'conversation'"
              @click="openTaskConversation"
            >
              <LoaderCircle
                v-if="operationLoading === 'conversation'"
                class="spinning"
                :size="17"
                aria-hidden="true"
              />
              <MessageCircle
                v-else
                :size="17"
                aria-hidden="true"
              />
              打开任务会话
            </button>
            <button
              v-if="canEditDetailTask"
              class="ghost-action"
              type="button"
              @click="openEditTask"
            >
              <Pencil
                :size="17"
                aria-hidden="true"
              />
              编辑任务
            </button>
            <button
              v-if="canRepostDetailTask"
              class="ghost-action"
              type="button"
              @click="openRepostTask"
            >
              <Repeat2
                :size="17"
                aria-hidden="true"
              />
              重新发布
            </button>
            <button
              v-if="canCancelDetailTask || canWithdrawDetailTask"
              class="danger-action"
              type="button"
              @click="openCancelPanel"
            >
              <X
                :size="17"
                aria-hidden="true"
              />
              {{ cancelActionTitle }}
            </button>
          </footer>
        </template>
      </section>
    </div>

    <div
      v-if="cancelPanelOpen && detailTask"
      class="workflow-modal-layer"
      @click.self="closeCancelPanel"
    >
      <section
        class="panel-card workflow-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-cancel-title"
      >
        <header class="workflow-modal__header">
          <div>
            <p class="section-kicker">
              任务状态变更
            </p>
            <h3 id="task-cancel-title">
              {{ cancelActionTitle }}
            </h3>
            <p v-if="cancelActionWillDeductPoint">
              任务已经开始，本次操作会扣除 1 积分，并通知对方。
            </p>
            <p v-else-if="isProviderWithdrawal">
              退出后任务将重新开放接单，发布者会收到通知。
            </p>
            <p v-else>
              取消后任务不能继续执行；已经选人时，接单者会收到通知。
            </p>
          </div>
          <button
            class="icon-action"
            type="button"
            :aria-label="`关闭${cancelActionTitle}窗口`"
            @click="closeCancelPanel"
          >
            <X
              :size="18"
              aria-hidden="true"
            />
          </button>
        </header>

        <form
          class="application-form"
          @submit.prevent="submitCancelTask"
        >
          <label
            class="application-field"
            for="task-cancel-reason"
          >
            <span>{{ ['DRAFT', 'OPEN'].includes(detailTask.status) && !isProviderWithdrawal ? '原因（可选）' : '原因' }}</span>
            <textarea
              id="task-cancel-reason"
              v-model.trim="cancelReason"
              maxlength="300"
              :aria-invalid="Boolean(cancelReasonError)"
              placeholder="请说明取消或退出任务的原因"
            />
            <small>{{ cancelReason.length }}/300</small>
            <p
              v-if="cancelReasonError"
              class="field-error"
            >
              {{ cancelReasonError }}
            </p>
          </label>
          <div class="workflow-modal__footer">
            <button
              class="ghost-action"
              type="button"
              @click="closeCancelPanel"
            >
              返回
            </button>
            <button
              class="danger-action"
              type="submit"
              :disabled="operationLoading === 'cancel'"
            >
              <LoaderCircle
                v-if="operationLoading === 'cancel'"
                class="spinning"
                :size="17"
                aria-hidden="true"
              />
              确认{{ cancelActionTitle }}
            </button>
          </div>
        </form>
      </section>
    </div>

    <div
      v-if="applicationPanelOpen && detailTask"
      class="workflow-modal-layer"
      @click.self="closeApplicationPanel"
    >
      <section
        class="panel-card workflow-modal application-panel"
        :class="{ 'application-panel--locked': applicationGate }"
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-application-title"
      >
        <header class="workflow-modal__header application-heading">
          <div>
            <p class="section-kicker">
              接单申请
            </p>
            <h3 id="task-application-title">
              申请成为帮助者
            </h3>
          </div>
          <div class="workflow-modal__actions">
            <StatusBadge
              v-if="applicationGate"
              :label="applicationGate.title"
              :tone="applicationGate.tone"
            />
            <button
              class="icon-action"
              type="button"
              aria-label="关闭接单申请"
              @click="closeApplicationPanel"
            >
              <X
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>
        </header>

        <form
          v-if="canApplyDetailTask"
          class="application-form"
          @submit.prevent="submitTaskApplication"
        >
          <label
            class="application-field"
            for="application-message"
          >
            <span>接单说明</span>
            <textarea
              id="application-message"
              v-model="applicationMessage"
              maxlength="300"
              placeholder="例如：我下午会经过南门，可以顺路取到图书馆服务台。"
              :aria-invalid="Boolean(applicationMessageError)"
              :disabled="applicationSubmitting"
            />
            <small>{{ applicationMessage.length }}/300 · 建议说明可完成时间、路线或相关经验。</small>
            <p
              v-if="applicationMessageError"
              class="field-error"
            >
              {{ applicationMessageError }}
            </p>
          </label>
          <div class="workflow-modal__footer">
            <button
              class="ghost-action"
              type="button"
              :disabled="applicationSubmitting"
              @click="closeApplicationPanel"
            >
              取消
            </button>
            <button
              class="primary-action application-submit"
              type="submit"
              :disabled="applicationSubmitting"
            >
              <LoaderCircle
                v-if="applicationSubmitting"
                class="spinning"
                :size="17"
                aria-hidden="true"
              />
              <Award
                v-else
                :size="17"
                aria-hidden="true"
              />
              提交申请
            </button>
          </div>
        </form>

        <div
          v-else-if="applicationGate"
          class="application-gate"
        >
          <p class="application-copy">
            {{ applicationGate.copy }}
          </p>
          <RouterLink
            v-if="applicationGate.to"
            class="ghost-action application-gate-action"
            :to="applicationGate.to"
          >
            {{ applicationGate.action }}
          </RouterLink>
        </div>
      </section>
    </div>
    <div
      v-if="candidatePanelOpen && detailTask && isDetailOwner"
      class="workflow-modal-layer"
      @click.self="closeCandidatePanel"
    >
      <section
        class="panel-card workflow-modal workflow-modal--wide candidates-section"
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-candidates-title"
      >
        <header class="workflow-modal__header candidate-heading">
          <div>
            <p class="section-kicker">
              候选管理
            </p>
            <h3 id="task-candidates-title">
              接单候选
            </h3>
            <p>查看申请者档案、信用信息和留言，选中后任务会进入已选人状态。</p>
          </div>
          <div class="workflow-modal__actions">
            <button
              class="ghost-action"
              type="button"
              :disabled="candidatesLoading || !detailTask?.id"
              @click="loadCandidates(detailTask.id)"
            >
              <RefreshCw
                :class="{ spinning: candidatesLoading }"
                :size="16"
                aria-hidden="true"
              />
              刷新
            </button>
            <button
              class="icon-action"
              type="button"
              aria-label="关闭候选管理"
              @click="closeCandidatePanel"
            >
              <X
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>
        </header>

        <div class="candidate-summary">
          <div>
            <ClipboardCheck
              :size="16"
              aria-hidden="true"
            />
            {{ candidateStats.total }} 位候选
          </div>
          <div>
            <Clock3
              :size="16"
              aria-hidden="true"
            />
            {{ candidateStats.pendingCount }} 位待选择
          </div>
          <div>
            <ShieldCheck
              :size="16"
              aria-hidden="true"
            />
            {{ candidateStats.verifiedCount }} 位已认证
          </div>
        </div>

        <div
          v-if="acceptedCandidate"
          class="accepted-candidate"
        >
          <CheckCircle2
            :size="18"
            aria-hidden="true"
          />
          已选择 {{ acceptedCandidate.name }} 接单，其他待处理申请会自动婉拒。
        </div>

        <div
          v-if="candidatesLoading"
          class="loading-state compact-loading"
          role="status"
        >
          <LoaderCircle
            class="spinning"
            :size="22"
            aria-hidden="true"
          />
          <span>正在加载候选申请...</span>
        </div>

        <div
          v-else-if="candidatesError"
          class="detail-error"
          role="status"
        >
          <Info
            :size="18"
            aria-hidden="true"
          />
          <span>{{ candidatesError }}</span>
          <button
            class="ghost-action"
            type="button"
            @click="loadCandidates(detailTask.id)"
          >
            重试
          </button>
        </div>

        <div
          v-else-if="!sortedCandidates.length"
          class="candidate-empty"
        >
          <Inbox
            :size="32"
            aria-hidden="true"
          />
          <strong>还没有同学申请接单</strong>
          <span>新的申请会显示在这里，包含留言、档案和信用信息。</span>
        </div>

        <div
          v-else
          class="candidate-list"
        >
          <article
            v-for="candidate in sortedCandidates"
            :key="candidate.applicationId || candidate.applicantId"
            class="candidate-card"
            :class="candidateCardClass(candidate)"
          >
            <header class="candidate-top">
              <span class="candidate-avatar">
                <img
                  v-if="candidate.avatarUrl"
                  :src="candidate.avatarUrl"
                  alt=""
                >
                <span v-else>{{ candidateInitial(candidate) }}</span>
              </span>
              <span class="candidate-identity">
                <strong>{{ candidate.name }}</strong>
                <span>{{ candidateAccountCopy(candidate) }} · {{ candidateProfileCopy(candidate) }}</span>
              </span>
              <StatusBadge
                :label="applicationStatusLabel(candidate.status)"
                :tone="applicationStatusTone(candidate.status)"
              />
            </header>

            <dl class="candidate-metrics">
              <div>
                <dt>信用等级</dt>
                <dd>Lv.{{ candidate.creditLevel }}</dd>
              </div>
              <div>
                <dt>信用分</dt>
                <dd>{{ candidate.points || 0 }}</dd>
              </div>
              <div>
                <dt>完成任务</dt>
                <dd>{{ candidate.completedCount || 0 }}</dd>
              </div>
            </dl>

            <div class="candidate-tags">
              <span
                v-if="candidate.verified"
                class="candidate-tag candidate-tag--verified"
              >
                校园认证
              </span>
              <span
                v-if="candidate.campus"
                class="candidate-tag"
              >
                {{ candidate.campus }}
              </span>
              <span
                v-if="candidate.college"
                class="candidate-tag"
              >
                {{ candidate.college }}
              </span>
              <span
                v-for="tag in candidate.tags"
                :key="tag"
                class="candidate-tag"
              >
                {{ tag }}
              </span>
              <span
                v-if="!candidate.verified && !candidate.campus && !candidate.college && !candidate.tags.length"
                class="candidate-tag candidate-tag--muted"
              >
                档案待补充
              </span>
            </div>

            <p class="candidate-message">
              {{ candidate.message || '申请者暂未填写补充说明。' }}
            </p>

            <footer class="candidate-actions">
              <span>{{ candidateSubmittedCopy(candidate) }}</span>
              <button
                v-if="candidate.status === 'PENDING' && detailTask.status === 'OPEN'"
                class="primary-action"
                type="button"
                :disabled="operationLoading === `accept-${candidate.applicationId}`"
                @click="acceptCandidate(candidate)"
              >
                <LoaderCircle
                  v-if="operationLoading === `accept-${candidate.applicationId}`"
                  class="spinning"
                  :size="16"
                  aria-hidden="true"
                />
                <CheckCircle2
                  v-else
                  :size="16"
                  aria-hidden="true"
                />
                选择 TA
              </button>
              <span v-else>
                {{ candidate.status === 'ACCEPTED' ? '当前接单者' : '申请已处理' }}
              </span>
            </footer>
          </article>
        </div>
      </section>
    </div>

    <div
      v-if="reportPanelOpen && detailTask"
      class="workflow-modal-layer"
      @click.self="closeReportPanel"
    >
      <section
        class="panel-card workflow-modal workflow-modal--wide report-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-report-title"
      >
        <header class="workflow-modal__header report-heading">
          <div>
            <p class="section-kicker">
              举报任务
            </p>
            <h3 id="task-report-title">
              向管理员提交举报
            </h3>
            <p>举报会记录目标类型、目标 ID、原因和详细描述，管理员可在后台裁决并标记任务争议。</p>
          </div>
          <div class="workflow-modal__actions">
            <button
              class="ghost-action"
              type="button"
              :disabled="reportsLoading"
              @click="loadMyReports"
            >
              <RefreshCw
                :class="{ spinning: reportsLoading }"
                :size="16"
                aria-hidden="true"
              />
              刷新记录
            </button>
            <button
              class="icon-action"
              type="button"
              aria-label="关闭举报窗口"
              @click="closeReportPanel"
            >
              <X
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>
        </header>

        <div class="report-context">
          <span class="report-context__info">
            <strong>{{ detailTask.title || '当前任务' }}</strong>
            <small>任务 #{{ detailTask.id }} · {{ taskCategoryLabel(detailTask) }}</small>
          </span>
          <StatusBadge
            :label="taskStatusLabel(detailTask.status)"
            :tone="taskStatusTone(detailTask.status)"
          />
        </div>

        <form
          class="report-form"
          @submit.prevent="submitReport"
        >
          <input
            type="hidden"
            :value="reportForm.targetType"
          >
          <input
            type="hidden"
            :value="reportForm.targetId"
          >

          <label
            class="report-field report-field--wide"
            for="report-reason"
          >
            <span>举报原因</span>
            <input
              id="report-reason"
              v-model.trim="reportForm.reason"
              maxlength="100"
              :aria-invalid="Boolean(reportErrors.reason)"
              placeholder="例如：违规代考、站外交易、骚扰信息"
            >
            <small>{{ reportForm.reason.length }}/100 · 必填，尽量简短清楚。</small>
            <p
              v-if="reportErrors.reason"
              class="field-error"
            >
              {{ reportErrors.reason }}
            </p>
          </label>

          <label
            class="report-field report-field--wide"
            for="report-description"
          >
            <span>详细描述</span>
            <textarea
              id="report-description"
              v-model.trim="reportForm.description"
              maxlength="1000"
              :aria-invalid="Boolean(reportErrors.description)"
              placeholder="补充发生时间、关键沟通记录、涉及用户或管理员需要核查的信息。"
            />
            <small>{{ reportForm.description.length }}/1000 · 可选。</small>
            <p
              v-if="reportErrors.description"
              class="field-error"
            >
              {{ reportErrors.description }}
            </p>
          </label>

          <div class="workflow-modal__footer report-field--wide">
            <button
              class="ghost-action"
              type="button"
              :disabled="reportSubmitting"
              @click="closeReportPanel"
            >
              取消
            </button>
            <button
              class="primary-action"
              type="submit"
              :disabled="reportSubmitting"
            >
              <LoaderCircle
                v-if="reportSubmitting"
                class="spinning"
                :size="17"
                aria-hidden="true"
              />
              <FileWarning
                v-else
                :size="17"
                aria-hidden="true"
              />
              提交举报
            </button>
          </div>
        </form>

        <section class="report-history-block">
          <header>
            <strong>我的举报记录</strong>
            <span>{{ detailTaskReports.length }} 条与当前任务相关</span>
          </header>

          <div
            v-if="reportsLoading"
            class="loading-state compact-loading"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="22"
              aria-hidden="true"
            />
            <span>正在加载举报记录...</span>
          </div>

          <div
            v-else-if="reportsError"
            class="detail-error"
            role="status"
          >
            <Info
              :size="18"
              aria-hidden="true"
            />
            <span>{{ reportsError }}</span>
          </div>

          <div
            v-else-if="!detailTaskReports.length"
            class="candidate-empty"
          >
            <FileWarning
              :size="30"
              aria-hidden="true"
            />
            <strong>暂无当前任务举报</strong>
            <span>提交后可以在这里看到处理状态。</span>
          </div>

          <article
            v-for="report in detailTaskReports"
            v-else
            :key="report.id"
            class="report-history-card"
          >
            <span>
              <strong>#{{ report.id }} · {{ reportTargetLabel(report.targetType) }}</strong>
              <small>{{ formatDateTime(report.createdAt) }}</small>
            </span>
            <StatusBadge
              :label="reportStatusLabel(report.status)"
              :tone="reportStatusTone(report.status)"
            />
            <p>{{ report.reason || '未填写原因。' }}</p>
          </article>
        </section>
      </section>
    </div>
  </Teleport>

  <UserProfileModal
    :user-id="profileUserId"
    :visible="showProfileModal"
    related-type="TASK"
    :related-id="profileRelatedId"
    @close="showProfileModal = false"
  />
</template>

<style scoped>
.tasks-page {
  display: grid;
  gap: 20px;
}

.tasks-page--mine {
  gap: 14px;
  width: 100%;
  color: var(--color-text);
}

.panel-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.section-kicker {
  margin: 0;
  color: var(--color-brand-text);
  font-size: 0.92rem;
  font-weight: 900;
}

.filter-heading h2,
.list-toolbar h2,
.detail-topbar h2 {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
  line-height: 1.15;
}

.list-toolbar span {
  color: var(--color-text-muted);
  line-height: 1.55;
}

.primary-action,
.ghost-action,
.danger-action,
.icon-action,
.search-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 8px;
  font-weight: 900;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.primary-action,
.search-submit {
  min-width: 132px;
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid var(--color-brand);
  background: var(--color-brand);
  color: var(--color-surface);
  box-shadow: 0 14px 26px var(--shadow-button);
}

.primary-action:hover:not(:disabled),
.search-submit:hover:not(:disabled) {
  background: var(--color-brand-hover);
}

.ghost-action {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand-text);
}

.ghost-action:hover:not(:disabled) {
  border-color: var(--color-border);
  background: var(--color-brand-soft);
}

.danger-action {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.danger-action:hover:not(:disabled) {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.icon-action {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand-text);
}

.icon-action:hover:not(:disabled) {
  border-color: var(--color-border);
  background: var(--color-brand-soft);
}

.primary-action:disabled,
.ghost-action:disabled,
.danger-action:disabled,
.icon-action:disabled,
.search-submit:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.primary-action:focus-visible,
.ghost-action:focus-visible,
.danger-action:focus-visible,
.icon-action:focus-visible,
.search-submit:focus-visible,
.search-field:focus-within,
.filter-field input:focus,
.filter-field select:focus,
.size-field select:focus,
.task-card:focus-visible {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
  outline: 0;
}

.tasks-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.tasks-main-column {
  display: grid;
  gap: 18px;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 20px;
  padding: 24px;
}

.filter-panel {
  gap: 18px;
}

.filter-panel--mine {
  gap: 18px;
  padding: 18px;
  border-color: var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.filter-heading,
.list-toolbar,
.detail-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.filter-heading div,
.list-toolbar div,
.detail-topbar div {
  display: grid;
  gap: 5px;
}

.filter-heading div {
  justify-items: start;
}

.filter-toggle {
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.filter-toggle:hover .section-kicker {
  color: var(--color-brand-hover);
}

.filter-toggle-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.filter-chevron {
  color: var(--color-text-muted);
  transition: transform 220ms ease;
}

.filter-chevron--open {
  transform: rotate(180deg);
}

.search-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 0 12px 0 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.search-field input,
.filter-field input,
.filter-field select,
.size-field select {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
}

.search-field input::placeholder,
.filter-field input::placeholder {
  color: var(--color-text-muted);
}

.filter-panel--mine .search-field {
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 46px;
  padding: 0 16px;
  border-color: var(--color-border);
  color: var(--color-text-muted);
}

.search-submit {
  min-height: 38px;
  padding: 0 13px;
  box-shadow: none;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.filter-panel--mine .filter-grid {
  gap: 18px 28px;
  padding-top: 2px;
  border-top: 1px solid var(--color-border);
}

.filter-field {
  display: grid;
  gap: 7px;
}

.filter-field span {
  color: var(--color-text);
  font-size: 0.92rem;
  font-weight: 900;
}

.filter-panel--mine .filter-field > span:first-child {
  color: var(--color-text);
  font-size: 0.94rem;
}

.filter-field input,
.filter-field select,
.size-field select {
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.select-shell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.select-shell:focus-within {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.filter-field .select-shell input,
.filter-field .select-shell select {
  min-height: 44px;
  padding: 0;
  border: 0;
  box-shadow: none;
  outline: 0;
  background: transparent;
}

.filter-field .select-shell input:focus,
.filter-field .select-shell select:focus {
  border-color: transparent;
  box-shadow: none;
  outline: 0;
}

.select-shell select {
  appearance: auto;
}

.select-shell--plain {
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  padding: 0 12px;
}

.filter-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.filter-panel--mine .filter-actions {
  justify-content: flex-end;
}

.filter-panel--mine .ghost-action,
.filter-panel--mine .primary-action {
  min-width: 132px;
  min-height: 46px;
  padding-inline: 18px;
}

.filter-panel--mine .primary-action {
  border-color: var(--color-brand);
  background: linear-gradient(180deg, var(--color-brand), var(--color-brand-hover));
  box-shadow: var(--shadow-button-hover);
}

.filter-panel--mine .ghost-action {
  border-color: var(--color-border);
  color: var(--color-brand);
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-strip--mine {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-tile {
  display: grid;
  gap: 6px;
  min-height: 88px;
  padding: 16px;
  border: 1px solid var(--color-info-bg);
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-info-text);
}

.metric-tile--mine {
  position: relative;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-areas:
    "icon label"
    "icon value";
  align-items: center;
  gap: 3px 18px;
  min-height: 96px;
  padding: 18px 20px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.metric-tile--mine::after {
  content: "";
  position: absolute;
  inset: -42px -20px auto auto;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: var(--color-surface);
}

.metric-icon {
  grid-area: icon;
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 16px;
}

.metric-tile--warning {
  border-color: var(--color-warning-soft);
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.metric-tile--accent {
  border-color: var(--color-accent-soft);
  background: var(--color-accent-bg);
  color: var(--color-accent-text);
}

.metric-tile--purple {
  border-color: var(--color-border);
  background: linear-gradient(105deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand);
}

.metric-tile--purple .metric-icon {
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-border));
}

.metric-tile--orange {
  border-color: var(--color-warning-soft);
  background: linear-gradient(105deg, var(--color-warning-bg), var(--color-warning-bg));
  color: var(--color-warning-text);
}

.metric-tile--orange .metric-icon {
  background: var(--color-warning-bg);
}

.metric-tile--teal {
  border-color: var(--color-accent-soft);
  background: linear-gradient(105deg, var(--color-accent-bg), var(--color-accent-bg));
  color: var(--color-accent-text);
}

.metric-tile--teal .metric-icon {
  background: var(--color-accent-bg);
}

.metric-tile--green {
  border-color: var(--color-success-soft);
  background: linear-gradient(105deg, var(--color-success-bg), var(--color-success-bg));
  color: var(--color-success-text);
}

.metric-tile--green .metric-icon {
  background: var(--color-success-bg);
}

.metric-tile span {
  font-size: 0.9rem;
  font-weight: 800;
}

.metric-tile--mine > span:not(.metric-icon) {
  grid-area: label;
  font-size: 1rem;
  font-weight: 900;
}

.metric-tile strong {
  font-size: 2rem;
  line-height: 1;
}

.metric-tile--mine strong {
  grid-area: value;
  font-size: 2.1rem;
}

.detail-error {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 14px;
  line-height: 1.55;
}

.detail-error {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.list-panel {
  gap: 18px;
}

.list-panel--mine {
  gap: 18px;
  padding: 18px 24px 16px;
  border-color: var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.list-panel--mine .list-toolbar {
  align-items: center;
}

.list-panel--mine .list-toolbar h2 {
  color: var(--color-text);
  font-size: 1.18rem;
}

.toolbar-actions {
  display: flex !important;
  align-items: center;
  gap: 10px;
}

.size-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
  font-weight: 900;
}

.list-panel--mine .size-field {
  color: var(--color-text);
}

.size-field select {
  min-width: 76px;
}

.list-panel--mine .size-field select {
  min-width: 72px;
  border-color: var(--color-border);
}

.loading-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  color: var(--color-brand-text);
  font-weight: 900;
}

.empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 240px;
  padding: 24px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state--mine {
  gap: 8px;
  min-height: 236px;
  padding: 20px 24px 18px;
  border-color: var(--color-border);
  background:
    radial-gradient(circle at 45% 0%, var(--color-brand-glow), transparent 24%),
    var(--color-surface);
}

.empty-illustration {
  position: relative;
  width: 224px;
  height: 98px;
}

.empty-illustration__clipboard {
  position: absolute;
  left: 68px;
  top: 8px;
  width: 72px;
  height: 78px;
  border: 6px solid var(--color-brand-border);
  border-radius: 9px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.empty-illustration__clipboard span {
  position: absolute;
  left: 17px;
  width: 32px;
  height: 6px;
  border-radius: 999px;
  background: var(--color-brand-soft);
}

.empty-illustration__clipboard span:nth-child(1) {
  top: 18px;
}

.empty-illustration__clipboard span:nth-child(2) {
  top: 35px;
}

.empty-illustration__clipboard span:nth-child(3) {
  top: 52px;
  width: 24px;
}

.empty-illustration__clip {
  position: absolute;
  left: 90px;
  top: 0;
  width: 30px;
  height: 20px;
  border-radius: 10px 10px 5px 5px;
  background: var(--color-brand-border);
  box-shadow: inset 0 8px 0 rgba(255, 255, 255, 0.45);
}

.empty-illustration__building {
  position: absolute;
  bottom: 0;
  width: 32px;
  height: 42px;
  border-radius: 6px 6px 0 0;
  background: var(--color-brand-soft);
}

.empty-illustration__building::before {
  content: "";
  position: absolute;
  inset: 10px 9px auto;
  height: 8px;
  border-radius: 3px;
  background: var(--color-brand-soft);
  box-shadow: 0 14px 0 var(--color-brand-soft);
}

.empty-illustration__building--left {
  left: 28px;
}

.empty-illustration__building--right {
  right: 40px;
  height: 30px;
}

.empty-illustration__plane {
  position: absolute;
  right: 24px;
  top: 18px;
  width: 0;
  height: 0;
  border-top: 8px solid transparent;
  border-bottom: 6px solid transparent;
  border-left: 28px solid var(--color-brand-border);
  transform: rotate(-12deg);
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: var(--color-text);
}

.empty-state--mine h3 {
  color: var(--color-text);
  font-size: 1.16rem;
}

.empty-state--mine p {
  color: var(--color-text-muted);
  font-weight: 800;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14px;
  margin-top: 8px;
}

.empty-actions .primary-action,
.empty-actions .ghost-action {
  min-width: 150px;
  min-height: 46px;
  padding-inline: 18px;
}

.empty-actions .primary-action {
  border-color: var(--color-brand);
  background: linear-gradient(180deg, var(--color-brand), var(--color-brand-hover));
}

.task-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.task-list-groups {
  display: grid;
  gap: 18px;
}

.task-list-groups--single {
  gap: 12px;
}

.task-list-group {
  display: grid;
  gap: 12px;
}

.task-list-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 2px 0;
}

.task-list-group__header h3,
.task-list-group__header p {
  margin: 0;
}

.task-list-group__header h3 {
  color: var(--color-text);
  font-size: 1.04rem;
  line-height: 1.3;
}

.task-list-group__empty {
  padding: 14px 16px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-weight: 800;
}

.task-card {
  position: relative;
  display: grid;
  gap: 14px;
  width: 100%;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: inherit;
  text-align: left;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.task-card:hover,
.task-card.selected {
  border-color: var(--color-border);
  background: var(--color-surface-muted);
  box-shadow: 0 14px 28px var(--shadow-card);
}

.task-card.selected::before {
  content: "";
  position: absolute;
  inset: 14px auto 14px 0;
  width: 3px;
  border-radius: 999px;
  background: var(--color-brand-text);
}

.deadline-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--color-border);
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  font-weight: 900;
}

.deadline-chip--info {
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.deadline-chip--warning {
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.deadline-chip--danger {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.task-card-main {
  display: grid;
  gap: 10px;
}

.task-card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.task-card-titleline {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.task-type-icon,
.detail-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 8px;
}

.task-type-icon {
  width: 46px;
  height: 46px;
}

.task-type-icon--purple,
.detail-type-icon--purple,
.detail-type-chip--purple {
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
}

.task-type-icon--orange,
.detail-type-icon--orange,
.detail-type-chip--orange {
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.task-type-icon--blue,
.detail-type-icon--blue,
.detail-type-chip--blue {
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.task-type-icon--green,
.detail-type-icon--green,
.detail-type-chip--green {
  background: var(--color-success-bg);
  color: var(--color-accent-text);
}

.task-type-icon--amber,
.detail-type-icon--amber,
.detail-type-chip--amber {
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.task-card-heading strong {
  color: var(--color-text);
  font-size: 1.14rem;
  line-height: 1.35;
}

.task-description {
  color: var(--color-text-muted);
  line-height: 1.58;
}

.task-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  color: var(--color-text-muted);
  font-size: 0.92rem;
}

.task-meta-grid span,
.task-card-footer,
.boundary-list span,
.guide-item {
  display: flex;
  align-items: center;
  gap: 7px;
}

.task-meta-grid svg,
.task-card-footer svg {
  flex: 0 0 auto;
  color: var(--color-brand-text);
}

.task-card-footer {
  flex-wrap: wrap;
  justify-content: space-between;
  color: var(--color-text-muted);
  font-size: 0.88rem;
}

.task-card-footer span:first-child {
  color: var(--color-brand-text);
  font-weight: 900;
}

.mine-subtabs {
  display: flex;
  gap: 8px;
  padding-bottom: 8px;
}

.mine-subtabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 20px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease;
}

.mine-subtabs button:hover:not(.active) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.mine-subtabs button.active {
  border-color: var(--color-brand-text);
  background: var(--color-brand-text);
  color: var(--color-surface);
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
  color: var(--color-text-muted);
  font-weight: 900;
}

.pagination-bar--mine {
  justify-content: center;
  gap: 14px;
  padding-top: 0;
}

.pagination-bar--mine .page-button {
  min-width: 110px;
  border-color: var(--color-border);
  color: var(--color-text-muted);
}

.pagination-bar--mine .page-button:not(:disabled) {
  color: var(--color-brand);
}

.page-current {
  display: inline-grid;
  place-items: center;
  min-width: 38px;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-brand-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-brand);
  box-shadow: var(--shadow-card);
}

.page-button {
  min-width: 110px;
}

.task-detail-column {
  grid-column: 2;
  position: sticky;
  top: 118px;
  display: grid;
  gap: 18px;
}

.detail-panel {
  gap: 18px;
}

.task-detail-modal-layer {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: transparent;
}

.workflow-modal-layer {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.25);
}

.task-detail-modal {
  grid-template-rows: auto minmax(0, 1fr) auto;
  align-content: stretch;
  width: min(72vw, 960px);
  height: min(90vh, 880px);
  max-width: calc(100vw - 48px);
  max-height: calc(100vh - 48px);
  overflow: hidden;
  padding: clamp(24px, 3vw, 34px);
  font-family: "PingFang SC", "Microsoft YaHei", "Hiragino Sans GB", "Noto Sans SC", sans-serif;
  box-shadow: var(--shadow-elevated);
}

.workflow-modal {
  width: min(520px, calc(100vw - 48px));
  max-height: min(80vh, 680px);
  gap: 14px;
  overflow: auto;
  padding: 20px;
  font-family: "PingFang SC", "Microsoft YaHei", "Hiragino Sans GB", "Noto Sans SC", sans-serif;
  box-shadow: var(--shadow-elevated);
}

.workflow-modal--wide {
  width: min(680px, calc(100vw - 48px));
}

.workflow-modal__header,
.workflow-modal__actions,
.workflow-modal__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.workflow-modal__header h3 {
  margin: 4px 0 0;
  color: var(--color-text);
  font-size: 1.04rem;
}

.workflow-modal__actions {
  align-items: center;
  flex: 0 0 auto;
}

.workflow-modal__footer {
  justify-content: flex-end;
}

.task-detail-showcase {
  display: grid;
  gap: 18px;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.detail-card-header,
.detail-chip-row,
.detail-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-chip-row {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.detail-type-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 0.82rem;
  font-weight: 900;
}

.detail-task-id {
  color: var(--color-text-muted);
  font-size: 0.86rem;
  font-weight: 900;
  white-space: nowrap;
}

.detail-title-block {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.detail-type-icon {
  width: 58px;
  height: 58px;
}

.detail-title-block h3,
.detail-title-block p {
  margin: 0;
}

.detail-title-block h3 {
  color: var(--color-text);
  font-size: 1.35rem;
  line-height: 1.25;
  font-weight: 900;
}

.detail-title-block p {
  margin-top: 8px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.detail-reward-strip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 12px;
  padding: 0 0 16px;
  border-bottom: 1px solid var(--color-border);
}

.detail-reward-strip span,
.detail-reward-strip small {
  color: var(--color-text-muted);
  font-weight: 900;
}

.detail-reward-strip strong {
  color: var(--color-danger);
  font-size: 1.5rem;
  line-height: 1;
  font-weight: 900;
}

.detail-reward-strip--points strong {
  color: var(--color-brand);
}

.detail-reward-strip--free strong {
  color: var(--color-accent-text);
}

.detail-reward-strip--negotiable strong {
  color: var(--color-warning-text);
}

.detail-info-list {
  display: grid;
  gap: 13px;
  margin: 0;
}

.detail-info-list div {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.detail-info-list dt {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  font-weight: 900;
}

.detail-info-list dt svg {
  color: var(--color-brand-text);
}

.detail-info-list dd {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.65;
}

.inline-profile-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 6px;
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface-muted);
  color: var(--color-brand-text);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease;
  white-space: nowrap;
}

.inline-profile-btn:hover {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
}

.detail-soft-pill,
.detail-warning-pill {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  gap: 6px;
  min-height: 28px;
  padding: 0 9px;
  border-radius: 8px;
  font-size: 0.86rem;
  font-weight: 900;
}

.detail-soft-pill {
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.detail-warning-pill {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.detail-action-row {
  padding-top: 18px;
  border-top: 1px solid var(--color-border);
}

.detail-primary-action,
.detail-secondary-action {
  min-height: 44px;
  flex: 1 1 0;
}

.detail-status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.detail-status-row > span:last-child {
  color: var(--color-brand-text);
  font-weight: 900;
}

.owner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.card-delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid var(--color-danger-text);
  border-radius: 6px;
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
  font-size: 0.8rem;
  font-weight: 800;
  cursor: pointer;
  margin-left: auto;
  transition: border-color 150ms ease, background 150ms ease;
}

.card-edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid var(--color-brand-border);
  border-radius: 6px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.8rem;
  font-weight: 800;
  cursor: pointer;
  margin-left: auto;
  transition: border-color 150ms ease, background 150ms ease;
}

.card-edit-btn:hover {
  filter: brightness(0.98);
}

.card-edit-btn + .card-delete-btn {
  margin-left: 0;
}

.card-delete-btn:hover {
  border-color: var(--color-danger-text);
  filter: brightness(0.98);
}
.conversation-entry {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border: 1px solid var(--color-success-soft);
  border-radius: 8px;
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.conversation-entry > div {
  display: grid;
  gap: 3px;
}

.conversation-entry strong {
  color: var(--color-success-text);
}

.conversation-entry span {
  color: var(--color-success-text);
  line-height: 1.5;
}

.workflow-entry-grid {
  display: grid;
  gap: 12px;
}

.workflow-entry-grid--compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.workflow-entry {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: none;
  box-sizing: border-box;
  padding: 13px 14px;
  border: 1px solid var(--color-info-bg);
  border-radius: 8px;
  background: var(--color-surface);
}

.workflow-entry-grid--compact .workflow-entry {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 11px 12px;
}

.workflow-entry-grid--compact .workflow-entry__icon {
  width: 36px;
  height: 36px;
}

.workflow-entry-grid--compact .workflow-entry__actions {
  grid-column: 1 / -1;
  justify-content: flex-start;
}

.workflow-entry-grid--compact .workflow-entry__body {
  gap: 3px;
}

.workflow-entry-grid--compact .workflow-entry__body h3 {
  font-size: 0.96rem;
}

.workflow-entry-grid--compact .workflow-entry__body p:last-child {
  font-size: 0.88rem;
  line-height: 1.4;
}

.workflow-entry--locked {
  border-color: var(--color-border);
  background: var(--color-surface-muted);
}

.workflow-entry__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-info);
}

.workflow-entry__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.workflow-entry__body h3,
.workflow-entry__body p {
  margin: 0;
}

.workflow-entry__body h3 {
  color: var(--color-text);
  font-size: 1rem;
}

.workflow-entry__body p:last-child {
  color: var(--color-text-muted);
  line-height: 1.5;
}

.workflow-entry__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.workflow-entry__button {
  min-width: 112px;
  padding-inline: 14px;
  white-space: nowrap;
}

.application-panel,
.candidates-section {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-info-bg);
  border-radius: 8px;
  background: var(--color-surface);
}

.application-panel {
  width: min(100%, 640px);
  justify-self: start;
}

.workflow-modal.application-panel {
  width: min(520px, calc(100vw - 48px));
  justify-self: center;
}

.application-panel .application-field textarea {
  min-height: 96px;
}

.application-panel--locked {
  border-color: var(--color-border);
  background: var(--color-surface-muted);
}

.completion-panel {
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-info-bg);
  border-radius: 8px;
  background: var(--color-surface);
}

.completion-heading,
.proof-card-heading,
.completion-action-box,
.resolution-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.completion-heading > div,
.proof-card-heading > div,
.completion-action-box > div {
  display: grid;
  gap: 4px;
}

.completion-heading h3,
.proof-card-heading strong,
.completion-action-box strong,
.resolution-panel h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 1rem;
}

.completion-heading p,
.proof-card-heading span,
.completion-action-box span,
.resolution-panel p,
.proof-card p,
.proof-empty {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.55;
}

.completion-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.completion-steps span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: 0.88rem;
  font-weight: 900;
}

.completion-steps span.done {
  border-color: var(--color-info-soft);
  background: var(--color-brand-soft);
  color: var(--color-info-text);
}

.completion-steps span.active {
  border-color: var(--color-accent-soft);
  background: var(--color-accent-bg);
  color: var(--color-accent-text);
}

.proof-card,
.completion-action-box,
.resolution-panel {
  display: grid;
  gap: 12px;
  margin-top: 20px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.proof-card-heading svg {
  color: var(--color-brand-text);
}

.proof-attachment-list,
.attachment-preview-list,
.review-actions {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 9px;
}

.proof-attachment-list a,
.attachment-preview-list span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  max-width: 100%;
  padding: 0 10px;
  border: 1px solid var(--color-info-bg);
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-info-text);
  font-size: 0.88rem;
  font-weight: 900;
}

.proof-attachment-list a:hover {
  border-color: var(--color-brand-border-hover);
  background: var(--color-info-bg);
}

.proof-empty {
  display: inline-flex;
  width: fit-content;
  padding: 8px 10px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.completion-form {
  display: grid;
  gap: 13px;
}

.completion-field {
  display: grid;
  gap: 8px;
}

.completion-field > span:first-child {
  color: var(--color-text);
  font-weight: 900;
}

.completion-field textarea,
.completion-field input {
  width: 100%;
  border: 1px solid var(--color-info-soft);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 11px 12px;
  line-height: 1.5;
  outline: 0;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.completion-field textarea {
  min-height: 112px;
  resize: vertical;
}

.completion-field textarea:focus,
.completion-field input:focus,
.file-pick-control:focus-within {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.completion-field textarea[aria-invalid="true"],
.completion-field input[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.completion-field small {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.completion-upload-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.file-pick-control {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-height: 84px;
  padding: 16px;
  border: 1px dashed var(--color-info-soft);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-info-text);
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.file-pick-control:hover {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
}

.file-pick-control span {
  display: grid;
  gap: 3px;
}

.file-pick-control strong {
  color: var(--color-info-text);
}

.file-pick-control small {
  color: var(--color-text-muted);
}

.file-pick-control input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.attachment-preview-list span {
  background: var(--color-accent-bg);
  border-color: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.attachment-preview-list small {
  color: inherit;
  font-size: 0.78rem;
  opacity: 0.82;
}

.attachment-preview-list__item--pending {
  background: var(--color-warning-bg) !important;
  border-color: var(--color-warning-soft) !important;
  color: var(--color-warning-text) !important;
}

.attachment-preview-list button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: -5px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: inherit;
}

.attachment-preview-list button:hover {
  background: var(--color-accent-bg);
}

.completion-submit {
  width: fit-content;
  justify-self: end;
}

.resolution-panel--success {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
}

.resolution-panel--warning {
  border-color: var(--color-warning-soft);
  background: var(--color-warning-bg);
}

.resolution-panel--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
}

.application-heading,
.candidate-heading,
.candidate-top,
.candidate-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.application-heading h3,
.candidate-heading h3 {
  margin: 4px 0 0;
  color: var(--color-text);
  font-size: 1rem;
}

.application-copy,
.candidate-heading p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.application-gate {
  display: grid;
  gap: 12px;
}

.application-form {
  display: grid;
  gap: 12px;
}

.application-field {
  display: grid;
  gap: 8px;
}

.application-field span {
  color: var(--color-text);
  font-weight: 900;
}

.application-field textarea {
  width: 100%;
  min-height: 112px;
  padding: 11px 12px;
  border: 1px solid var(--color-info-soft);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  line-height: 1.5;
  outline: 0;
  resize: vertical;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.application-field textarea:focus {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.application-field textarea[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.application-field small {
  color: var(--color-text-muted);
}

.application-submit {
  width: fit-content;
}

.application-gate-action {
  width: fit-content;
}

.cancel-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.cancel-panel h3,
.cancel-panel p {
  margin: 0;
}

.cancel-panel h3 {
  color: var(--color-warning-text);
  font-size: 1rem;
}

.cancel-panel p {
  line-height: 1.55;
}

.cancel-reason-field {
  display: grid;
  gap: 8px;
}

.cancel-reason-field span {
  font-weight: 900;
}

.cancel-reason-field textarea {
  width: 100%;
  min-height: 94px;
  padding: 11px 12px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: 0;
  resize: vertical;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.cancel-reason-field textarea:focus {
  border-color: var(--color-warning);
  box-shadow: 0 0 0 4px var(--shadow-focus);
}

.cancel-reason-field textarea[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.cancel-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.field-error {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.9rem;
  font-weight: 800;
  line-height: 1.45;
}

.detail-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.detail-facts div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.detail-facts dt {
  color: var(--color-text-muted);
  font-size: 0.84rem;
}

.detail-facts dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
  line-height: 1.45;
}

.detail-section {
  display: grid;
  gap: 9px;
}

.detail-section h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 1rem;
}

.detail-section p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.65;
  white-space: pre-wrap;
}

.candidates-section {
  border-color: var(--color-info-bg);
  background: var(--color-info-bg);
}

.report-panel {
  border-color: var(--color-border);
  background: var(--color-surface);
}

.report-heading > div:first-child {
  display: grid;
  gap: 5px;
}

.report-heading p:last-child {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.55;
}

.report-context {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-warning-bg);
}

.report-context__info {
  display: grid;
  gap: 4px;
}

.report-context strong {
  color: var(--color-text);
}

.report-context small {
  color: var(--color-warning-text);
}

.report-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.report-field {
  display: grid;
  gap: 8px;
}

.report-field--wide {
  grid-column: 1 / -1;
}

.report-field span {
  color: var(--color-text);
  font-weight: 900;
}

.report-field input,
.report-field select,
.report-field textarea {
  width: 100%;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: 0;
}

.report-field input,
.report-field select {
  height: 42px;
  padding: 0 12px;
}

.report-field textarea {
  min-height: 112px;
  padding: 11px 12px;
  line-height: 1.55;
  resize: vertical;
}

.report-field input:focus,
.report-field select:focus,
.report-field textarea:focus {
  border-color: var(--color-warning);
  box-shadow: 0 0 0 4px var(--shadow-focus);
}

.report-field input[aria-invalid="true"],
.report-field select[aria-invalid="true"],
.report-field textarea[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.report-field small {
  color: var(--color-text-muted);
}

.report-history-block {
  display: grid;
  gap: 12px;
  padding-top: 2px;
}

.report-history-block > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.report-history-block > header strong {
  color: var(--color-text);
}

.report-history-block > header span {
  color: var(--color-text-muted);
}

.report-history-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 12px;
  padding: 12px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-surface);
}

.report-history-card > span {
  display: grid;
  gap: 4px;
}

.report-history-card strong {
  color: var(--color-text);
}

.report-history-card small {
  color: var(--color-text-muted);
}

.report-history-card p {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.compact-loading {
  min-height: 116px;
}

.candidate-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}

.candidate-summary div {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid var(--color-info-bg);
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-info-text);
  font-weight: 900;
}

.accepted-candidate {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 12px;
  border: 1px solid var(--color-success-soft);
  border-radius: 8px;
  background: var(--color-success-bg);
  color: var(--color-success-text);
  font-weight: 900;
  line-height: 1.5;
}

.candidate-empty {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 150px;
  padding: 20px;
  border: 1px dashed var(--color-info-soft);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  text-align: center;
}

.candidate-empty strong {
  color: var(--color-text);
}

.candidate-list {
  display: grid;
  gap: 12px;
}

.candidate-card {
  display: grid;
  gap: 13px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.candidate-card--accepted {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
}

.candidate-card--rejected,
.candidate-card--withdrawn {
  opacity: 0.78;
}

.candidate-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 46px;
  height: 46px;
  border: 1px solid var(--color-info-bg);
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-info);
  overflow: hidden;
}

.candidate-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.candidate-identity {
  display: grid;
  flex: 1;
  gap: 3px;
}

.candidate-identity strong {
  color: var(--color-text);
  font-size: 1rem;
}

.candidate-identity span,
.candidate-actions span {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.candidate-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.candidate-metrics div {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.candidate-metrics dt {
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.candidate-metrics dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
}

.candidate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.candidate-tag {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
  font-size: 0.82rem;
  font-weight: 900;
}

.candidate-tag--verified {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.candidate-tag--muted {
  background: var(--color-border);
  color: var(--color-text-muted);
}

.candidate-message {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.62;
  white-space: pre-wrap;
}

.boundary-list {
  display: grid;
  gap: 8px;
}

.boundary-list span {
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  font-weight: 800;
}

.boundary-list span.active {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.guide-list {
  display: grid;
  gap: 9px;
}

.guide-item {
  align-items: flex-start;
  padding: 10px;
  border: 1px solid var(--color-info-bg);
  border-radius: 8px;
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.guide-item span {
  display: grid;
  gap: 2px;
}

.guide-item strong {
  color: var(--color-info-text);
}

.guide-item small {
  line-height: 1.45;
}

.history-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 20px;
  color: var(--color-text-muted);
}

.history-list li {
  padding-left: 4px;
}

.history-list span {
  display: block;
  color: var(--color-text);
  font-weight: 900;
}

.history-list small {
  color: var(--color-text-muted);
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1240px) {
  .tasks-layout {
    grid-template-columns: 1fr;
  }

  .task-detail-column {
    grid-column: 1;
    position: static;
  }
}

@media (max-width: 900px) {
  .filter-grid,
  .metric-strip,
  .metric-strip--mine,
  .workflow-entry-grid--compact,
  .task-meta-grid,
  .detail-info-list div,
  .detail-facts,
  .candidate-summary,
  .candidate-metrics,
  .report-form,
  .completion-steps,
  .completion-upload-grid {
    grid-template-columns: 1fr;
  }

  .filter-heading,
  .list-toolbar,
  .detail-topbar,
  .task-card-heading,
  .detail-card-header,
  .detail-action-row,
  .workflow-entry,
  .application-heading,
  .completion-heading,
  .completion-action-box,
  .conversation-entry,
  .candidate-heading,
  .report-heading,
  .candidate-top,
  .candidate-actions,
  .proof-card-heading,
  .resolution-actions {
    display: grid;
  }

  .toolbar-actions,
  .filter-actions,
  .workflow-entry__actions {
    justify-content: flex-start;
  }

  .filter-panel--mine .filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .workflow-modal-layer {
    padding: 12px;
  }

  .task-detail-modal {
    width: calc(100vw - 24px);
    height: calc(100vh - 24px);
    max-width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }

  .workflow-modal,
  .workflow-modal.application-panel {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }

  .report-history-card {
    grid-template-columns: 1fr;
  }

  .search-field {
    grid-template-columns: auto minmax(0, 1fr);
    padding: 12px;
  }

  .search-submit {
    grid-column: 1 / -1;
    width: 100%;
  }

  .primary-action,
  .ghost-action,
  .danger-action,
  .page-button,
  .completion-submit,
  .workflow-entry__button {
    width: 100%;
  }

  .pagination-bar {
    display: grid;
  }

  .pagination-bar--mine {
    grid-template-columns: 1fr auto 1fr;
  }

  .empty-actions {
    display: grid;
    width: 100%;
  }

  .empty-actions .primary-action,
  .empty-actions .ghost-action {
    width: 100%;
  }

  .panel-card {
    padding: 18px;
  }
}
</style>
