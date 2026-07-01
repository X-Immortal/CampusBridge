<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  CalendarClock,
  CheckCircle2,
  ClipboardCheck,
  Clock3,
  Circle,
  CircleDollarSign,
  Eye,
  Gift,
  Handshake,
  Lightbulb,
  ListChecks,
  LoaderCircle,
  LockKeyhole,
  MapPin,
  MessageCircle,
  Pencil,
  RefreshCw,
  Repeat2,
  Save,
  Send,
  Shield,
  ShieldCheck,
  Star,
  Target
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import ModuleTopbar from '../components/layout/ModuleTopbar.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { optionLabel, REWARD_TYPE_OPTIONS, TASK_CATEGORY_OPTIONS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'

const DRAFT_STORAGE_KEY = 'campusbridge.taskPublishDraft.v1'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, verificationStatus, refreshSession } = useAuth()
const { showToast } = useToast()

const loadingSource = ref(false)
const submitting = ref(false)
const refreshing = ref(false)
const lastCreatedTask = ref(null)
const sourceTask = ref(null)
const savedDraft = ref(readSavedDraft())
const previewCardRef = ref(null)
const errors = reactive({})

const taskForm = reactive({
  category: 'ERRAND',
  title: '',
  description: '',
  campus: '',
  location: '',
  deliveryLocation: '',
  deadline: defaultDeadline(),
  rewardType: 'FREE',
  rewardDescription: '',
  rewardAmount: '',
  completionStandard: '',
  requiresProof: true,
  onlyInAppContact: true,
  allowOfflineContact: false,
  isInstantTask: false,
  routeHint: '',
  riskDisclosureConfirmed: false,
  sensitiveReason: ''
})

const categoryCards = [
  {
    ...TASK_CATEGORY_OPTIONS[0],
    icon: CalendarClock,
    note: '适合快递、带物、顺手帮拿'
  },
  {
    ...TASK_CATEGORY_OPTIONS[1],
    icon: ClipboardCheck,
    note: '适合代交材料、排队、流程协助'
  }
]

const rewardIcons = {
  FREE: Gift,
  POINTS: Star,
  CASH: CircleDollarSign,
  NEGOTIABLE: Handshake
}

const minDeadline = computed(() => formatLocalDateTime(new Date(Date.now() + 10 * 60 * 1000)))
const canPublish = computed(() => isAuthenticated.value && verificationStatus.value === 'VERIFIED')
const publishMode = computed(() => {
  if (route.name === 'task-edit') return 'edit'
  if (route.name === 'task-repost') return 'repost'
  return 'create'
})
const sourceTaskId = computed(() => {
  const taskId = route.params.taskId || route.query.draftId
  return taskId ? String(taskId) : ''
})
const isEditMode = computed(() => publishMode.value === 'edit')
const isRepostMode = computed(() => publishMode.value === 'repost')
const isRestoringServerDraft = computed(() => publishMode.value === 'create' && Boolean(route.query.draftId))
const isEditingDraft = computed(() => sourceTask.value?.status === 'DRAFT')
const canChangeFixedFields = computed(() => !isEditMode.value)
const shellActiveLabel = computed(() => {
  if (isRestoringServerDraft.value) return '任务草稿'
  return isEditMode.value || isRepostMode.value ? '我的任务' : '发布任务'
})
const pageMeta = computed(() => {
  if (isRestoringServerDraft.value) {
    return {
      kicker: '任务草稿',
      title: '继续编辑草稿',
      copy: '已恢复草稿中的任务信息，确认内容后可以直接发布。',
      status: '编辑中'
    }
  }

  if (isEditMode.value) {
    return {
      kicker: '任务编辑',
      title: '在接单前把任务改准确',
      copy: '保留原任务脉络，只修改当前接口允许更新的标题、说明、截止、奖励、验收和偏好。',
      status: '编辑中'
    }
  }

  if (isRepostMode.value) {
    return {
      kicker: '任务重发',
      title: '沿用原内容重新发布',
      copy: '适合取消、驳回或过期后的需求复用；发布前请重新确认截止时间和完成标准。',
      status: '重发中'
    }
  }

  return {
    kicker: '任务发布',
    title: '发布校园任务',
    copy: '用清晰的信息描述你的需求，让同学更快理解并接单。',
    status: canPublish.value ? '可发布' : '需认证'
  }
})
const primaryActionLabel = computed(() => {
  if (isEditMode.value) return '保存修改'
  if (isRepostMode.value) return '重新发布'
  return '发布任务'
})
const draftSummary = computed(() => {
  if (!savedDraft.value?.savedAt) return ''
  return `保存于 ${formatDisplayDate(savedDraft.value.savedAt)}`
})

const publishGate = computed(() => {
  if (!isAuthenticated.value) {
    return {
      tone: 'danger',
      title: '登录后才能发布任务',
      copy: '任务发布会绑定当前账号，用于后续接单、会话和任务记录追溯。',
      action: '去登录',
      to: '/auth'
    }
  }

  if (verificationStatus.value !== 'VERIFIED') {
    return {
      tone: 'warning',
      title: '请先完成校内认证',
      copy: '通过认证后，其他同学会看到可信状态，平台也能拦截未认证发单。',
      action: '去认证中心',
      to: '/home'
    }
  }

  return null
})

const publishProgressItems = computed(() => [
  { label: '选择任务类型', done: Boolean(taskForm.category) },
  { label: '填写任务标题', done: Boolean(taskForm.title.trim()) },
  { label: '设置地点', done: Boolean(taskForm.campus.trim() && taskForm.location.trim()) },
  { label: '设置截止时间', done: Boolean(taskForm.deadline) },
  { label: '填写任务说明', done: Boolean(taskForm.description.trim()) },
  { label: '设置完成标准', done: Boolean(taskForm.completionStandard.trim()) }
])
const completedPublishStepCount = computed(
  () => publishProgressItems.value.filter((item) => item.done).length
)
const previewTitle = computed(() => taskForm.title.trim() || '代取菜鸟驿站的快递')
const previewLocation = computed(() => {
  const campus = taskForm.campus.trim() || '仙林校区'
  const location = taskForm.location.trim() || '快递站 3 号架'
  if (taskForm.category === 'ERRAND') {
    const delivery = taskForm.deliveryLocation.trim() || '送达地点未填'
    return `${campus} · ${location} → ${delivery}`
  }
  return `${campus} · ${location}`
})
const previewReward = computed(() => {
  if (taskForm.rewardDescription.trim()) return taskForm.rewardDescription.trim()
  return optionLabel(REWARD_TYPE_OPTIONS, taskForm.rewardType)
})
const previewDeadline = computed(() => formatDisplayDate(taskForm.deadline))
const selectedRewardDescription = computed(
  () => REWARD_TYPE_OPTIONS.find((item) => item.value === taskForm.rewardType)?.description || ''
)

function pad(value) {
  return String(value).padStart(2, '0')
}

function defaultDeadline() {
  return formatLocalDateTime(new Date(Date.now() + 2 * 60 * 60 * 1000))
}

function formatLocalDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDisplayDate(value) {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function readSavedDraft() {
  try {
    return JSON.parse(localStorage.getItem(DRAFT_STORAGE_KEY) || 'null')
  } catch {
    return null
  }
}

function clearSavedDraft() {
  localStorage.removeItem(DRAFT_STORAGE_KEY)
  savedDraft.value = null
}

function cloneForm() {
  return {
    category: taskForm.category,
    title: taskForm.title,
    description: taskForm.description,
    campus: taskForm.campus,
    location: taskForm.location,
    deliveryLocation: taskForm.deliveryLocation,
    deadline: taskForm.deadline,
    rewardType: taskForm.rewardType,
    rewardDescription: taskForm.rewardDescription,
    rewardAmount: taskForm.rewardAmount,
    completionStandard: taskForm.completionStandard,
    requiresProof: taskForm.requiresProof,
    onlyInAppContact: taskForm.onlyInAppContact,
    allowOfflineContact: taskForm.allowOfflineContact,
    isInstantTask: taskForm.isInstantTask,
    routeHint: taskForm.routeHint,
    riskDisclosureConfirmed: taskForm.riskDisclosureConfirmed,
    sensitiveReason: taskForm.sensitiveReason
  }
}

function hasDraftContent() {
  return [
    taskForm.title,
    taskForm.description,
    taskForm.campus,
    taskForm.location,
    taskForm.deliveryLocation,
    taskForm.rewardDescription,
    taskForm.completionStandard,
    taskForm.routeHint,
    taskForm.sensitiveReason
  ].some((value) => value.trim())
}

function serverDraftMissingFields() {
  const requiredFields = [
    { label: '标题', ready: Boolean(taskForm.title.trim()) },
    { label: '说明', ready: Boolean(taskForm.description.trim()) },
    { label: '校区', ready: Boolean(taskForm.campus.trim()) },
    { label: '地点', ready: Boolean(taskForm.location.trim()) },
    { label: '截止时间', ready: Boolean(taskForm.deadline) },
    { label: '奖励方式', ready: Boolean(taskForm.rewardType) },
    { label: '完成标准', ready: Boolean(taskForm.completionStandard.trim()) }
  ]
  return requiredFields.filter((field) => !field.ready).map((field) => field.label)
}

function canSyncDraftToServer() {
  return serverDraftMissingFields().length === 0
}

function restoreSavedDraft() {
  if (!savedDraft.value?.form) return
  Object.assign(taskForm, savedDraft.value.form)
  clearErrors()
  showToast('已恢复本机草稿，发布前请重新确认时间和地点。', { type: 'success' })
}

function discardSavedDraft() {
  clearSavedDraft()
  showToast('本机草稿已清除。', { type: 'success' })
}

function persistLocalDraft() {
  const draft = {
    savedAt: new Date().toISOString(),
    mode: publishMode.value,
    sourceTaskId: sourceTaskId.value || null,
    serverDraftId: isEditingDraft.value ? sourceTaskId.value : null,
    form: cloneForm()
  }

  localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft))
  savedDraft.value = draft
  return draft
}

async function handleSaveDraft() {
  if (!hasDraftContent()) {
    showToast('至少填写标题、说明、地点或完成标准中的一项后再保存草稿。', { type: 'warning' })
    return
  }

  persistLocalDraft()

  const missingFields = serverDraftMissingFields()

  if (!canPublish.value) {
    showToast('草稿已保存在本机；登录并完成认证后，补齐必填项可同步到服务器。', { type: 'success' })
    return
  }

  if (isEditingDraft.value) {
    showToast('草稿修改已保存在本机；当前后端没有单独的草稿更新接口，可补齐后直接发布。', { type: 'success' })
    return
  }

  if (!canSyncDraftToServer()) {
    showToast(`草稿已保存在本机。补齐${missingFields.join('、')}后，才能同步到服务器草稿。`, {
      type: 'warning'
    })
    return
  }

  submitting.value = true
  try {
    const serverDraft = await api.saveTaskDraft(buildDraftPayload())
    lastCreatedTask.value = normalizeTaskResult(serverDraft)
    showToast('草稿已保存到任务系统，本机也保留了一份便于继续编辑。', { type: 'success' })
  } catch {
    showToast('草稿已保存在本机，但同步到服务器失败；稍后可恢复本机草稿继续编辑。', { type: 'warning' })
  } finally {
    submitting.value = false
  }
}

function handlePreviewTask() {
  previewCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function normalizeDeadline(value) {
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
    return `${value}:00`
  }
  return value
}

function optionalText(value) {
  const text = value.trim()
  return text || undefined
}

function optionalNumber(value) {
  if (value === '' || value === null || value === undefined) return undefined
  const num = Number(value)
  return Number.isFinite(num) && num > 0 ? num : undefined
}

function booleanFlag(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value === 1
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes'].includes(normalized)) return true
    if (['false', '0', 'no'].includes(normalized)) return false
  }
  return Boolean(value)
}

function normalizeTaskResult(result) {
  const task = result?.task || result || {}
  return {
    ...task,
    requiresProof: booleanFlag(task.requiresProof, true),
    onlyInAppContact: booleanFlag(task.onlyInAppContact, true),
    allowOfflineContact: booleanFlag(task.allowOfflineContact),
    isInstantTask: booleanFlag(task.isInstantTask),
    riskDisclosureConfirmed: booleanFlag(task.riskDisclosureConfirmed)
  }
}

function clearError(field) {
  delete errors[field]
}

function clearErrors() {
  Object.keys(errors).forEach((field) => {
    delete errors[field]
  })
}

function setError(field, message) {
  errors[field] = message
}

function describedBy(field, hintId) {
  const ids = []
  if (hintId) ids.push(hintId)
  if (errors[field]) ids.push(`${field}-error`)
  return ids.join(' ') || undefined
}

function selectCategory(category) {
  if (!canChangeFixedFields.value) return
  taskForm.category = category
  clearError('category')
}

function selectRewardType(rewardType) {
  taskForm.rewardType = rewardType
  clearError('rewardType')
}

function rewardIcon(rewardType) {
  return rewardIcons[rewardType] || Gift
}

function resetForm() {
  Object.assign(taskForm, {
    category: 'ERRAND',
    title: '',
    description: '',
    campus: '',
    location: '',
    deliveryLocation: '',
    deadline: defaultDeadline(),
    rewardType: 'FREE',
    rewardDescription: '',
    rewardAmount: '',
    completionStandard: '',
    requiresProof: true,
    onlyInAppContact: true,
    allowOfflineContact: false,
    isInstantTask: false,
    routeHint: '',
    riskDisclosureConfirmed: false,
    sensitiveReason: ''
  })
}

function toDateTimeInputValue(value) {
  if (!value) return defaultDeadline()
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 16)
  return formatLocalDateTime(date)
}

function isPastDateTime(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) || date <= new Date()
}

function applyTaskToForm(task) {
  if (!task) return
  const nextDeadline = toDateTimeInputValue(task.deadline)

  Object.assign(taskForm, {
    category: task.category || 'ERRAND',
    title: task.title || '',
    description: task.description || '',
    campus: task.campus || '',
    location: task.location || '',
    deliveryLocation: task.deliveryLocation || '',
    deadline: isRepostMode.value && isPastDateTime(nextDeadline) ? defaultDeadline() : nextDeadline,
    rewardType: task.rewardType || 'FREE',
    rewardDescription: task.rewardDescription || '',
    rewardAmount: task.rewardAmount ?? '',
    completionStandard: task.completionStandard || '',
    requiresProof: booleanFlag(task.requiresProof, true),
    onlyInAppContact: booleanFlag(task.onlyInAppContact, true),
    allowOfflineContact: booleanFlag(task.allowOfflineContact),
    isInstantTask: booleanFlag(task.isInstantTask),
    routeHint: task.routeHint || '',
    riskDisclosureConfirmed: booleanFlag(task.riskDisclosureConfirmed),
    sensitiveReason: task.sensitiveReason || ''
  })
}

async function loadSourceTask() {
  if (!sourceTaskId.value) {
    sourceTask.value = null
    return
  }

  loadingSource.value = true
  try {
    const result = await api.getTaskDetail(sourceTaskId.value)
    const task = normalizeTaskResult(result)
    if (isRestoringServerDraft.value && task.status !== 'DRAFT') {
      throw new Error('要编辑的草稿不存在或已经发布。')
    }
    sourceTask.value = task
    applyTaskToForm(task)
    clearErrors()
  } catch (error) {
    sourceTask.value = null
    showToast(error.message || '原任务加载失败，请返回任务详情后重试。', { type: 'danger' })
  } finally {
    loadingSource.value = false
  }
}

function validateTextLength(field, value, maxLength, message) {
  if (value.trim().length > maxLength) {
    setError(field, message)
  }
}

function validateRewardAmount() {
  if (taskForm.rewardType !== 'POINTS' && taskForm.rewardType !== 'CASH') return
  const val = taskForm.rewardAmount
  if (!val) return
  const num = Number(val)
  if (!Number.isFinite(num) || num <= 0 || !Number.isInteger(num)) {
    setError('rewardAmount', '请输入有效的正整数。')
    return
  }
  if (taskForm.rewardType === 'POINTS' && num > 10) {
    setError('rewardAmount', '积分数量不能超过 10。')
    return
  }
  if (num > 999999) {
    setError('rewardAmount', '数值不能超过 999999。')
  }
}

function validateForm() {
  clearErrors()

  if (!taskForm.category) setError('category', '请选择任务分类。')
  if (!taskForm.title.trim()) setError('title', '请填写任务标题。')
  if (!taskForm.description.trim()) setError('description', '请说明任务背景和需要帮助的内容。')
  if (!isEditMode.value && !taskForm.campus.trim()) setError('campus', '请填写校区。')
  if (!isEditMode.value && !taskForm.location.trim()) setError('location', '请填写具体地点。')
  if (!taskForm.deadline) setError('deadline', '请选择截止时间。')
  if (!taskForm.rewardType) setError('rewardType', '请选择奖励方式。')
  if (!taskForm.completionStandard.trim()) setError('completionStandard', '请填写完成标准。')

  validateTextLength('title', taskForm.title, 50, '标题最多 50 个字。')
  validateTextLength('description', taskForm.description, 2000, '任务说明最多 2000 个字。')
  validateTextLength('campus', taskForm.campus, 50, '校区最多 50 个字。')
  validateTextLength('location', taskForm.location, 120, '地点最多 120 个字。')
  validateTextLength('deliveryLocation', taskForm.deliveryLocation, 120, '送达地点最多 120 个字。')
  validateTextLength('rewardDescription', taskForm.rewardDescription, 200, '奖励说明最多 200 个字。')
  validateRewardAmount()
  validateTextLength('completionStandard', taskForm.completionStandard, 500, '完成标准最多 500 个字。')
  validateTextLength('routeHint', taskForm.routeHint, 300, '顺路信息最多 300 个字。')

  if (taskForm.deadline) {
    const deadline = new Date(taskForm.deadline)
    if (Number.isNaN(deadline.getTime()) || deadline <= new Date()) {
      setError('deadline', '截止时间必须晚于当前时间。')
    }
  }

  if (!isEditMode.value && taskForm.isInstantTask && !taskForm.routeHint.trim()) {
    setError('routeHint', '开启顺路/即时协助时，请填写顺路信息。')
  }

  return Object.keys(errors).length === 0
}

async function focusFirstInvalidField() {
  await nextTick()
  const target = document.querySelector('[aria-invalid="true"]')
  if (target instanceof HTMLElement) target.focus()
}

function buildPayload() {
  return {
    category: taskForm.category,
    title: taskForm.title.trim(),
    description: taskForm.description.trim(),
    campus: taskForm.campus.trim(),
    location: taskForm.location.trim(),
    deliveryLocation: taskForm.category === 'ERRAND' ? taskForm.deliveryLocation.trim() || undefined : undefined,
    deadline: normalizeDeadline(taskForm.deadline),
    rewardType: taskForm.rewardType,
    rewardDescription: optionalText(taskForm.rewardDescription),
    rewardAmount: optionalNumber(taskForm.rewardAmount),
    completionStandard: taskForm.completionStandard.trim(),
    requiresProof: taskForm.requiresProof,
    onlyInAppContact: taskForm.onlyInAppContact,
    allowOfflineContact: taskForm.allowOfflineContact,
    isInstantTask: taskForm.isInstantTask,
    routeHint: taskForm.isInstantTask ? optionalText(taskForm.routeHint) : undefined,
    riskDisclosureConfirmed: taskForm.riskDisclosureConfirmed,
    sensitiveReason: optionalText(taskForm.sensitiveReason),
    attachmentIds: []
  }
}

function buildDraftPayload() {
  return {
    category: taskForm.category || undefined,
    title: optionalText(taskForm.title),
    description: optionalText(taskForm.description),
    campus: optionalText(taskForm.campus),
    location: optionalText(taskForm.location),
    deliveryLocation: taskForm.category === 'ERRAND' ? optionalText(taskForm.deliveryLocation) : undefined,
    deadline: taskForm.deadline ? normalizeDeadline(taskForm.deadline) : undefined,
    rewardType: taskForm.rewardType || undefined,
    rewardDescription: optionalText(taskForm.rewardDescription),
    completionStandard: optionalText(taskForm.completionStandard),
    requiresProof: taskForm.requiresProof,
    onlyInAppContact: taskForm.onlyInAppContact,
    allowOfflineContact: taskForm.allowOfflineContact,
    isInstantTask: taskForm.isInstantTask,
    routeHint: optionalText(taskForm.routeHint),
    riskDisclosureConfirmed: taskForm.riskDisclosureConfirmed,
    sensitiveReason: optionalText(taskForm.sensitiveReason),
    attachmentIds: []
  }
}

function buildUpdatePayload() {
  return {
    title: taskForm.title.trim(),
    description: taskForm.description.trim(),
    deadline: normalizeDeadline(taskForm.deadline),
    rewardType: taskForm.rewardType,
    rewardDescription: optionalText(taskForm.rewardDescription),
    rewardAmount: optionalNumber(taskForm.rewardAmount),
    completionStandard: taskForm.completionStandard.trim(),
    requiresProof: taskForm.requiresProof,
    onlyInAppContact: taskForm.onlyInAppContact,
    allowOfflineContact: taskForm.allowOfflineContact
  }
}

async function handleRefreshVerification() {
  if (!isAuthenticated.value) {
    await router.push('/auth')
    return
  }

  refreshing.value = true
  try {
    await refreshSession()
    showToast('认证状态已刷新。', { type: 'success' })
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    refreshing.value = false
  }
}

async function handleSubmitTask() {
  lastCreatedTask.value = null

  if (loadingSource.value) {
    showToast('原任务仍在加载，请稍后再保存。', { type: 'warning' })
    return
  }

  if ((isEditMode.value || isRepostMode.value || isRestoringServerDraft.value) && !sourceTask.value) {
    showToast('没有可用的原任务内容，请返回任务详情后重试。', { type: 'danger' })
    return
  }

  if (!canPublish.value) {
    showToast(publishGate.value?.title || '当前状态暂不可发布任务。', { type: 'warning' })
    return
  }

  if (!validateForm()) {
    showToast('请先修正表单中的提示项。', { type: 'warning' })
    await focusFirstInvalidField()
    return
  }

  submitting.value = true
  try {
    if (isEditMode.value) {
      if (isEditingDraft.value) {
        persistLocalDraft()
        showToast('草稿修改已保存在本机。当前后端草稿更新会返回错误，前端已避免覆盖失败。', { type: 'warning' })
        return
      }

      const updatedTask = await api.updateTask(sourceTaskId.value, buildUpdatePayload())
      lastCreatedTask.value = normalizeTaskResult(updatedTask)
      clearSavedDraft()
      showToast('任务修改已保存，3 秒后返回任务详情。', { type: 'success' })
      scheduleRedirectToTask(sourceTaskId.value)
      return
    }

    if (isRepostMode.value) {
      const resentTask = await api.resendTask(sourceTaskId.value)
      lastCreatedTask.value = normalizeTaskResult(resentTask)
      clearSavedDraft()
      showToast('任务已重新发布，3 秒后返回任务详情。', { type: 'success' })
      scheduleRedirectToTask(sourceTaskId.value)
      return
    }

    const createdTask = await api.createTask(buildPayload())
    lastCreatedTask.value = normalizeTaskResult(createdTask)
    if (isRestoringServerDraft.value) {
      try {
        await api.deleteTaskDraft(sourceTaskId.value)
      } catch {
        showToast('任务已发布，但原草稿未能自动删除，请稍后在草稿栏手动删除。', { type: 'warning' })
      }
    }
    clearSavedDraft()
    resetForm()
    clearErrors()
    const newTaskId = createdTask?.id || createdTask?.taskId
    showToast(`任务已发布，等待合适的同学申请接单。${newTaskId ? '3 秒后查看任务详情。' : ''}`, { type: 'success' })
    if (newTaskId) {
      scheduleRedirectToTask(newTaskId)
    }
  } catch (error) {
    showToast(error.message || '任务保存失败，请稍后再试。', { type: 'danger' })
  } finally {
    submitting.value = false
  }
}

let redirectTimer = null

function scheduleRedirectToTask(taskId) {
  if (redirectTimer) clearTimeout(redirectTimer)
  redirectTimer = setTimeout(() => {
    router.push({ name: 'task-detail', params: { taskId: String(taskId) } })
  }, 3000)
}

onBeforeUnmount(() => {
  if (redirectTimer) clearTimeout(redirectTimer)
})

watch(
  () => [route.name, route.params.taskId, route.query.draftId],
  async () => {
    lastCreatedTask.value = null
    clearErrors()
    if (sourceTaskId.value) {
      await loadSourceTask()
      return
    }

    sourceTask.value = null
    resetForm()
    savedDraft.value = readSavedDraft()
  },
  { immediate: true }
)
</script>

<template>
  <DashboardShell :active-label="shellActiveLabel">
    <main
      class="publish-page"
      :aria-busy="submitting"
    >
      <ModuleTopbar
        :eyebrow="pageMeta.kicker"
        :title="pageMeta.title"
        :description="pageMeta.copy"
      >
        <template #extra>
          <button
            class="ghost-action"
            type="button"
            @click="router.push({ name: 'task-list' })"
          >
            <ArrowLeft
              :size="16"
              aria-hidden="true"
            />
            返回广场
          </button>
          <StatusBadge
            :label="pageMeta.status"
            :tone="canPublish ? 'success' : 'warning'"
          />
        </template>
      </ModuleTopbar>

      <section
        v-if="loadingSource"
        class="page-feedback page-feedback--info"
        role="status"
      >
        <LoaderCircle
          class="spinning"
          :size="18"
          aria-hidden="true"
        />
        <span>正在载入原任务内容...</span>
      </section>

      <section
        v-if="savedDraft && !isEditMode"
        class="draft-alert"
        role="status"
      >
        <Save
          :size="20"
          aria-hidden="true"
        />
        <div>
          <strong>发现未发布草稿</strong>
          <span>{{ draftSummary || '可以恢复上次填写的任务内容。' }}</span>
        </div>
        <div class="draft-actions">
          <button
            class="ghost-action"
            type="button"
            @click="restoreSavedDraft"
          >
            恢复草稿
          </button>
          <button
            class="ghost-action"
            type="button"
            @click="discardSavedDraft"
          >
            清除
          </button>
        </div>
      </section>

      <section
        v-if="publishGate"
        class="gate-alert"
        :class="`gate-alert--${publishGate.tone}`"
        role="status"
      >
        <LockKeyhole
          :size="20"
          aria-hidden="true"
        />
        <div>
          <strong>{{ publishGate.title }}</strong>
          <span>{{ publishGate.copy }}</span>
        </div>
        <button
          class="gate-action"
          type="button"
          @click="router.push(publishGate.to)"
        >
          {{ publishGate.action }}
        </button>
      </section>

      <section class="publish-layout">
        <form
          class="panel-card publish-form"
          novalidate
          @submit.prevent="handleSubmitTask"
        >
          <section class="form-section">
            <div class="section-heading">
              <span>1</span>
              <div>
                <h2>基本信息</h2>
              </div>
            </div>

            <div
              class="category-selector"
              role="radiogroup"
              aria-label="任务分类"
            >
              <button
                v-for="category in categoryCards"
                :key="category.value"
                class="choice-control category-choice"
                :class="{ selected: taskForm.category === category.value }"
                type="button"
                :disabled="!canChangeFixedFields"
                role="radio"
                :aria-checked="taskForm.category === category.value"
                @click="selectCategory(category.value)"
              >
                <component
                  :is="category.icon"
                  :size="22"
                  aria-hidden="true"
                />
                <strong>{{ category.label }}</strong>
                <span>{{ category.note }}</span>
              </button>
            </div>
            <p
              v-if="errors.category"
              id="category-error"
              class="field-error"
            >
              {{ errors.category }}
            </p>
            <p
              v-if="isEditMode"
              class="locked-hint"
            >
              当前接口仅支持修改任务内容与偏好；分类、校区和地点如需变化，请取消后重发。
            </p>

            <div class="form-row form-row--intro">
              <label
                class="publish-field"
                for="task-title"
              >
                <span>任务标题</span>
                <input
                  id="task-title"
                  v-model.trim="taskForm.title"
                  maxlength="50"
                  placeholder="例如：代取菜鸟驿站的快递"
                  required
                  :aria-invalid="Boolean(errors.title)"
                  :aria-describedby="describedBy('title', 'title-hint')"
                  @input="clearError('title')"
                >
                <small id="title-hint">{{ taskForm.title.length }}/50</small>
                <small
                  v-if="errors.title"
                  id="title-error"
                  class="field-error"
                >
                  {{ errors.title }}
                </small>
              </label>

              <label
                class="publish-field"
                for="task-description"
              >
                <span>任务说明</span>
                <textarea
                  id="task-description"
                  v-model.trim="taskForm.description"
                  maxlength="2000"
                  placeholder="补充细节和注意事项，帮助同学更好地理解任务。"
                  required
                  :aria-invalid="Boolean(errors.description)"
                  :aria-describedby="describedBy('description', 'description-hint')"
                  @input="clearError('description')"
                />
                <small id="description-hint">{{ taskForm.description.length }}/2000</small>
                <small
                  v-if="errors.description"
                  id="description-error"
                  class="field-error"
                >
                  {{ errors.description }}
                </small>
              </label>
            </div>
          </section>

          <section class="form-section">
            <div class="section-heading">
              <span>2</span>
              <div>
                <h2>时间地点</h2>
              </div>
            </div>

            <div class="form-row">
              <label
                class="publish-field"
                for="task-campus"
              >
                <span>校区</span>
                <input
                  id="task-campus"
                  v-model.trim="taskForm.campus"
                  maxlength="50"
                  placeholder="例如：仙林校区"
                  required
                  :disabled="!canChangeFixedFields"
                  :aria-invalid="Boolean(errors.campus)"
                  :aria-describedby="describedBy('campus')"
                  @input="clearError('campus')"
                >
                <small
                  v-if="errors.campus"
                  id="campus-error"
                  class="field-error"
                >
                  {{ errors.campus }}
                </small>
              </label>

              <label
                class="publish-field"
                for="task-location"
              >
                <span>{{ taskForm.category === 'ERRAND' ? '取件地点' : '具体地点' }}</span>
                <input
                  id="task-location"
                  v-model.trim="taskForm.location"
                  maxlength="120"
                  :placeholder="taskForm.category === 'ERRAND' ? '例如：快递站 3 号架' : '例如：教学楼 A208'"
                  required
                  :disabled="!canChangeFixedFields"
                  :aria-invalid="Boolean(errors.location)"
                  :aria-describedby="describedBy('location')"
                  @input="clearError('location')"
                >
                <small
                  v-if="errors.location"
                  id="location-error"
                  class="field-error"
                >
                  {{ errors.location }}
                </small>
              </label>

              <label
                v-if="taskForm.category === 'ERRAND'"
                class="publish-field"
                for="task-delivery-location"
              >
                <span>送达地点</span>
                <input
                  id="task-delivery-location"
                  v-model.trim="taskForm.deliveryLocation"
                  maxlength="120"
                  placeholder="例如：宿舍楼 5 号楼 302"
                  :disabled="!canChangeFixedFields"
                  :aria-invalid="Boolean(errors.deliveryLocation)"
                  :aria-describedby="describedBy('deliveryLocation')"
                  @input="clearError('deliveryLocation')"
                >
                <small
                  v-if="errors.deliveryLocation"
                  id="deliveryLocation-error"
                  class="field-error"
                >
                  {{ errors.deliveryLocation }}
                </small>
              </label>
            </div>

            <div class="form-row">
              <label
                class="publish-field"
                for="task-deadline"
              >
                <span>截止时间</span>
                <input
                  id="task-deadline"
                  v-model="taskForm.deadline"
                  type="datetime-local"
                  required
                  :min="minDeadline"
                  :aria-invalid="Boolean(errors.deadline)"
                  :aria-describedby="describedBy('deadline', 'deadline-hint')"
                  @input="clearError('deadline')"
                >
                <small id="deadline-hint">必须晚于当前时间，避免任务刚发布就过期。</small>
                <small
                  v-if="errors.deadline"
                  id="deadline-error"
                  class="field-error"
                >
                  {{ errors.deadline }}
                </small>
              </label>

              <label class="toggle-control">
                <input
                  v-model="taskForm.isInstantTask"
                  type="checkbox"
                  :disabled="isEditMode"
                  @change="clearError('routeHint')"
                >
                <span>
                  <strong>顺路/即时协助</strong>
                  <small>适合代取、带物、临时顺手帮忙。</small>
                </span>
              </label>
            </div>

            <label
              v-if="taskForm.isInstantTask && !isEditMode"
              class="publish-field"
              for="task-route"
            >
              <span>顺路信息</span>
              <input
                id="task-route"
                v-model.trim="taskForm.routeHint"
                placeholder="例如：从北门快递站到 7 舍，今晚 7:30 前都可以"
                :aria-invalid="Boolean(errors.routeHint)"
                :aria-describedby="describedBy('routeHint')"
                @input="clearError('routeHint')"
              >
              <small
                v-if="errors.routeHint"
                id="routeHint-error"
                class="field-error"
              >
                {{ errors.routeHint }}
              </small>
            </label>
          </section>

          <section class="form-section">
            <div class="section-heading">
              <span>3</span>
              <div>
                <h2>奖励与要求</h2>
              </div>
            </div>

            <div
              class="reward-selector"
              role="radiogroup"
              aria-label="奖励方式"
            >
              <button
                v-for="reward in REWARD_TYPE_OPTIONS"
                :key="reward.value"
                class="choice-control reward-choice"
                :class="{ selected: taskForm.rewardType === reward.value }"
                type="button"
                role="radio"
                :aria-checked="taskForm.rewardType === reward.value"
                @click="selectRewardType(reward.value)"
              >
                <component
                  :is="rewardIcon(reward.value)"
                  :size="22"
                  aria-hidden="true"
                />
                <strong>{{ reward.label }}</strong>
                <span>{{ reward.description }}</span>
              </button>
            </div>
            <p
              v-if="errors.rewardType"
              id="rewardType-error"
              class="field-error"
            >
              {{ errors.rewardType }}
            </p>

            <div
              v-if="taskForm.rewardType === 'POINTS' || taskForm.rewardType === 'CASH'"
              class="form-row form-row--reward-detail"
            >
              <label
                class="publish-field"
                for="task-reward-amount"
              >
                <span>{{ taskForm.rewardType === 'POINTS' ? '积分数量' : '现金金额' }}</span>
                <input
                  id="task-reward-amount"
                  v-model.trim="taskForm.rewardAmount"
                  type="number"
                  min="1"
                  :max="taskForm.rewardType === 'POINTS' ? 10 : undefined"
                  :placeholder="taskForm.rewardType === 'POINTS' ? '不超过10' : '例如：10'"
                  :aria-invalid="Boolean(errors.rewardAmount)"
                  @input="clearError('rewardAmount')"
                >
                <small
                  v-if="errors.rewardAmount"
                  id="rewardAmount-error"
                  class="field-error"
                >
                  {{ errors.rewardAmount }}
                </small>
              </label>
            </div>

            <div class="form-row form-row--reward-detail">
              <label
                class="publish-field"
                for="task-reward-description"
              >
                <span>奖励说明（可选）</span>
                <textarea
                  id="task-reward-description"
                  v-model.trim="taskForm.rewardDescription"
                  maxlength="200"
                  :placeholder="selectedRewardDescription"
                  :aria-invalid="Boolean(errors.rewardDescription)"
                  :aria-describedby="describedBy('rewardDescription', 'reward-hint')"
                  @input="clearError('rewardDescription')"
                />
                <small id="reward-hint">{{ taskForm.rewardDescription.length }}/200</small>
                <small
                  v-if="errors.rewardDescription"
                  id="rewardDescription-error"
                  class="field-error"
                >
                  {{ errors.rewardDescription }}
                </small>
              </label>

              <label
                class="publish-field"
                for="task-completion-standard"
              >
                <span>完成标准</span>
                <textarea
                  id="task-completion-standard"
                  v-model.trim="taskForm.completionStandard"
                  maxlength="500"
                  placeholder="例如：快递送到宿舍楼下并发送取件照片；材料提交成功后回传截图。"
                  required
                  :aria-invalid="Boolean(errors.completionStandard)"
                  :aria-describedby="describedBy('completionStandard', 'standard-hint')"
                  @input="clearError('completionStandard')"
                />
                <small id="standard-hint">{{ taskForm.completionStandard.length }}/500</small>
                <small
                  v-if="errors.completionStandard"
                  id="completionStandard-error"
                  class="field-error"
                >
                  {{ errors.completionStandard }}
                </small>
              </label>
            </div>
          </section>

          <section class="form-section">
            <div class="section-heading">
              <span>4</span>
              <div>
                <h2>确认与安全</h2>
              </div>
            </div>

            <div class="preference-grid">
              <label class="toggle-control">
                <input
                  v-model="taskForm.requiresProof"
                  type="checkbox"
                >
                <ShieldCheck
                  :size="22"
                  aria-hidden="true"
                />
                <span>
                  <strong>要求完成凭证</strong>
                  <small>帮助者完成后需要提交图片或文字说明。</small>
                </span>
              </label>

              <label class="toggle-control">
                <input
                  v-model="taskForm.onlyInAppContact"
                  type="checkbox"
                >
                <MessageCircle
                  :size="22"
                  aria-hidden="true"
                />
                <span>
                  <strong>仅站内沟通</strong>
                  <small>不要求一开始交换私人联系方式。</small>
                </span>
              </label>

              <label class="toggle-control">
                <input
                  v-model="taskForm.allowOfflineContact"
                  type="checkbox"
                >
                <MapPin
                  :size="22"
                  aria-hidden="true"
                />
                <span>
                  <strong>允许线下接触</strong>
                  <small>适合当面交接，不适合时请关闭。</small>
                </span>
              </label>
            </div>
          </section>

          <div class="form-actions">
            <RouterLink
              v-if="sourceTaskId"
              class="ghost-action"
              :to="{ name: 'task-detail', params: { taskId: sourceTaskId } }"
            >
              <ArrowLeft
                :size="17"
                aria-hidden="true"
              />
              返回详情
            </RouterLink>
            <button
              class="ghost-action"
              type="button"
              :disabled="loadingSource"
              @click="handleSaveDraft"
            >
              <Save
                :size="17"
                aria-hidden="true"
              />
              保存草稿
            </button>
            <button
              class="ghost-action"
              type="button"
              @click="handlePreviewTask"
            >
              <Eye
                :size="17"
                aria-hidden="true"
              />
              预览任务
            </button>
            <button
              v-if="publishGate"
              class="ghost-action"
              type="button"
              :disabled="refreshing"
              @click="handleRefreshVerification"
            >
              <RefreshCw
                :class="{ spinning: refreshing }"
                :size="17"
                aria-hidden="true"
              />
              刷新认证
            </button>
            <button
              class="primary-action"
              type="submit"
              :disabled="submitting || loadingSource"
            >
              <LoaderCircle
                v-if="submitting"
                class="spinning"
                :size="18"
                aria-hidden="true"
              />
              <Pencil
                v-else-if="isEditMode"
                :size="18"
                aria-hidden="true"
              />
              <Repeat2
                v-else-if="isRepostMode"
                :size="18"
                aria-hidden="true"
              />
              <Send
                v-else
                :size="18"
                aria-hidden="true"
              />
              {{ primaryActionLabel }}
            </button>
          </div>
        </form>

        <aside class="publish-side">
          <article
            ref="previewCardRef"
            class="panel-card publish-preview-card"
          >
            <div class="panel-heading">
              <span class="side-heading-title">
                <Eye
                  :size="19"
                  aria-hidden="true"
                />
                任务预览
              </span>
            </div>

            <section class="task-preview-box">
              <h3>{{ previewTitle }}</h3>
              <dl class="preview-list">
                <div>
                  <dt>
                    <ClipboardCheck
                      :size="16"
                      aria-hidden="true"
                    />
                    分类
                  </dt>
                  <dd>{{ optionLabel(TASK_CATEGORY_OPTIONS, taskForm.category) }}</dd>
                </div>
                <div>
                  <dt>
                    <MapPin
                      :size="16"
                      aria-hidden="true"
                    />
                    地点
                  </dt>
                  <dd>{{ previewLocation }}</dd>
                </div>
                <div>
                  <dt>
                    <Clock3
                      :size="16"
                      aria-hidden="true"
                    />
                    截止时间
                  </dt>
                  <dd>{{ previewDeadline }}</dd>
                </div>
                <div>
                  <dt>
                    <Gift
                      :size="16"
                      aria-hidden="true"
                    />
                    奖励
                  </dt>
                  <dd>{{ previewReward }}</dd>
                </div>
              </dl>

              <p class="preview-copy">
                {{ taskForm.description || '发布后，更多同学可以看到并按单完成任务。' }}
              </p>
            </section>
          </article>

          <article class="panel-card publish-progress-card">
            <div class="panel-heading">
              <span class="side-heading-title">
                <ListChecks
                  :size="20"
                  aria-hidden="true"
                />
                发布进度
              </span>
              <strong class="progress-count">
                {{ completedPublishStepCount }}/{{ publishProgressItems.length }} 项已完成
              </strong>
            </div>
            <div class="check-list">
              <div
                v-for="item in publishProgressItems"
                :key="item.label"
                class="check-item"
                :class="{ done: item.done }"
              >
                <CheckCircle2
                  v-if="item.done"
                  :size="16"
                  aria-hidden="true"
                />
                <Circle
                  v-else
                  :size="16"
                  aria-hidden="true"
                />
                <span>{{ item.label }}</span>
              </div>
            </div>
          </article>

          <article class="panel-card publish-tips-card">
            <div class="panel-heading">
              <span class="side-heading-title">
                <Lightbulb
                  :size="20"
                  aria-hidden="true"
                />
                发布小贴士
              </span>
            </div>
            <div class="tip-list">
              <div class="tip-item">
                <span>
                  <Target
                    :size="18"
                    aria-hidden="true"
                  />
                </span>
                <div>
                  <strong>标题越具体，越容易被理解</strong>
                </div>
              </div>
              <div class="tip-item">
                <span>
                  <MapPin
                    :size="18"
                    aria-hidden="true"
                  />
                </span>
                <div>
                  <strong>时间地点写清楚，可以减少沟通成本</strong>
                </div>
              </div>
              <div class="tip-item">
                <span>
                  <Shield
                    :size="18"
                    aria-hidden="true"
                  />
                </span>
                <div>
                  <strong>不要发布涉及隐私、代考、违规内容</strong>
                </div>
              </div>
            </div>
          </article>
        </aside>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.publish-page {
  display: grid;
  gap: 20px;
}

.panel-card,
.page-feedback,
.draft-alert,
.gate-alert {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.section-kicker,
.section-heading p {
  margin: 0;
  color: var(--color-brand-text);
  font-size: 0.92rem;
  font-weight: 900;
}

.panel-heading h2,
.section-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
  line-height: 1.15;
}

.gate-alert,
.page-feedback,
.draft-alert {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 13px 14px;
  line-height: 1.55;
}

.gate-alert div {
  display: grid;
  flex: 1;
  gap: 3px;
}

.draft-alert {
  align-items: center;
  border-color: var(--color-info-text);
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.draft-alert > div:first-of-type {
  display: grid;
  flex: 1;
  gap: 3px;
}

.draft-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.gate-alert--warning {
  border-color: var(--color-warning-soft);
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.gate-alert--danger,
.page-feedback--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.page-feedback--success {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.page-feedback--info {
  border-color: var(--color-info-text);
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.gate-action {
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid currentColor;
  border-radius: 8px;
  background: var(--color-surface);
  color: inherit;
  font-weight: 900;
}

.publish-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.42fr) minmax(320px, 0.72fr);
  gap: 22px;
  align-items: start;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 20px;
  padding: 24px;
}

.publish-form {
  gap: 26px;
}

.form-section {
  display: grid;
  gap: 16px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.section-heading > span {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
  font-weight: 900;
}

.section-heading div {
  display: grid;
  gap: 4px;
}

.section-heading h2,
.panel-heading h2 {
  font-size: 1.24rem;
}

.category-selector,
.reward-selector,
.preference-grid {
  display: grid;
  gap: 12px;
}

.category-selector {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reward-selector,
.preference-grid,
.form-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.choice-control,
.toggle-control {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  text-align: left;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease,
    color 180ms ease;
}

.choice-control {
  display: grid;
  gap: 7px;
  min-height: 120px;
  padding: 14px;
}

.choice-control svg {
  color: var(--color-brand-text);
}

.reward-choice svg {
  padding: 9px;
  border-radius: 50%;
  background: var(--color-brand-soft);
  box-sizing: content-box;
}

.choice-control strong,
.toggle-control strong {
  color: var(--color-text);
  font-weight: 900;
}

.choice-control span,
.toggle-control small {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.choice-control:hover,
.choice-control.selected,
.toggle-control:hover {
  border-color: var(--color-border);
  background: var(--color-surface-muted);
  box-shadow: 0 12px 24px var(--shadow-card);
}

.choice-control.selected {
  color: var(--color-brand-text);
}

.choice-control:disabled,
.toggle-control input:disabled + span {
  cursor: not-allowed;
  opacity: 0.68;
}

.locked-hint {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
  line-height: 1.55;
}

.form-row {
  display: grid;
  gap: 14px;
}

.publish-field {
  display: grid;
  gap: 8px;
}

.publish-field span {
  color: var(--color-text);
  font-weight: 900;
}

.publish-field input,
.publish-field textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 12px 13px;
  outline: 0;
  line-height: 1.45;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.publish-field textarea {
  min-height: 116px;
  resize: vertical;
}

.publish-field input:focus,
.publish-field textarea:focus,
.choice-control:focus-visible,
.toggle-control:focus-within,
.gate-action:focus-visible,
.primary-action:focus-visible,
.ghost-action:focus-visible {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
  outline: 0;
}

.publish-field input[aria-invalid="true"],
.publish-field textarea[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.publish-field small {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.field-error {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.9rem;
  font-weight: 800;
  line-height: 1.45;
}

.toggle-control,
.confirm-row {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 14px;
}

.toggle-control input,
.confirm-row input {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  margin-top: 2px;
  accent-color: var(--color-brand-text);
}

.toggle-control span {
  display: grid;
  gap: 4px;
}

.sensitive-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-warning-bg);
}

.sensitive-heading {
  display: flex;
  gap: 10px;
  color: var(--color-warning-text);
}

.sensitive-heading h3,
.sensitive-heading p {
  margin: 0;
}

.sensitive-heading h3 {
  color: var(--color-warning-text);
  font-size: 1rem;
}

.sensitive-heading p {
  margin-top: 4px;
  line-height: 1.55;
}

.confirm-row {
  padding: 0;
  color: var(--color-warning-text);
  line-height: 1.55;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 4px;
}

.primary-action,
.ghost-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 46px;
  border-radius: 8px;
  font-weight: 900;
}

.primary-action {
  min-width: 148px;
  border: 1px solid var(--color-brand-text);
  background: var(--color-brand-text);
  color: var(--color-surface);
  box-shadow: var(--shadow-button);
}

.primary-action:hover:not(:disabled) {
  background: var(--color-brand-text);
}

.ghost-action {
  padding: 0 12px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand-text);
}

.ghost-action:hover:not(:disabled) {
  border-color: var(--color-border);
  background: var(--color-brand-soft);
}

.primary-action:disabled,
.ghost-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.publish-side {
  position: sticky;
  top: 118px;
  display: grid;
  gap: 18px;
}

.publish-preview-card,
.publish-progress-card,
.publish-tips-card {
  gap: 16px;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.side-heading-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
  font-size: 1.05rem;
  font-weight: 900;
}

.side-heading-title svg {
  padding: 7px;
  border-radius: 50%;
  background: var(--color-brand-soft);
  box-sizing: content-box;
  color: var(--color-brand-text);
}

.progress-count {
  color: var(--color-text);
  font-size: 0.95rem;
  white-space: nowrap;
}

.task-preview-box {
  display: grid;
  gap: 18px;
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.task-preview-box h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.2rem;
  line-height: 1.35;
}

.preview-list {
  display: grid;
  gap: 16px;
  margin: 0;
}

.preview-list div {
  display: grid;
  grid-template-columns: 94px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.preview-list dt {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 0.9rem;
  font-weight: 900;
}

.preview-list dt svg {
  color: var(--color-brand-text);
}

.preview-list dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
}

.preview-copy,
.tip-list,
.check-item {
  color: var(--color-text-muted);
  line-height: 1.6;
}

.preview-copy {
  margin: 0;
  padding-top: 4px;
  color: var(--color-text-muted);
}

.check-list {
  display: grid;
  gap: 9px;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
  font-weight: 900;
}

.check-item.done {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.check-item svg {
  color: var(--color-text-muted);
}

.check-item.done svg {
  color: var(--color-success);
}

.tip-list {
  display: grid;
  gap: 15px;
  margin: 0;
}

.tip-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.tip-item > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
}

.tip-item strong {
  display: block;
  margin: 0;
  color: var(--color-text);
  line-height: 1.35;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1180px) {
  .publish-layout {
    grid-template-columns: 1fr;
  }

  .publish-side {
    position: static;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .category-selector,
  .reward-selector,
  .preference-grid,
  .form-row,
  .preview-list,
  .preview-list div,
  .publish-side {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel-card {
    padding: 18px;
  }

  .gate-alert,
  .draft-alert {
    display: grid;
  }

  .form-actions {
    display: grid;
  }
}

.publish-page {
  --publish-primary: var(--color-brand);
  --publish-primary-dark: var(--color-brand-hover);
  --publish-soft: var(--color-brand-soft);
  --publish-border: var(--color-border);
  --publish-text: var(--color-text);
  --publish-muted: var(--color-text-muted);
  max-width: 1388px;
  margin: 0;
  gap: 10px;
  color: var(--publish-text);
}

.panel-card,
.page-feedback,
.draft-alert,
.gate-alert {
  border: 1px solid var(--publish-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: none;
}

.publish-layout {
  grid-template-columns: minmax(0, 2.24fr) minmax(360px, 0.98fr);
  gap: 20px;
}

.panel-card.publish-form {
  gap: 20px;
  padding: 0;
  border: 0;
  background: transparent;
}

.form-section {
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--publish-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.section-heading {
  align-items: center;
  gap: 16px;
}

.section-heading > span {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: var(--color-brand-soft);
  color: var(--publish-primary);
  font-size: 1.03rem;
}

.section-heading div {
  display: flex;
  align-items: baseline;
  gap: 24px;
}

.section-heading h2 {
  color: var(--color-text);
  font-size: 1.08rem;
}

.section-heading p {
  color: var(--publish-muted);
  font-size: 0.9rem;
  font-weight: 700;
}

.category-selector,
.reward-selector,
.preference-grid {
  gap: 12px;
}

.choice-control,
.toggle-control {
  position: relative;
  border-color: var(--publish-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.choice-control {
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: auto auto;
  min-height: 74px;
  padding: 12px 16px;
  gap: 4px 12px;
}

.choice-control svg {
  grid-row: span 2;
  align-self: center;
  color: var(--publish-primary);
}

.choice-control strong,
.toggle-control strong {
  color: var(--color-text);
  font-size: 0.95rem;
}

.choice-control span,
.toggle-control small {
  color: var(--publish-muted);
  font-size: 0.8rem;
  line-height: 1.35;
}

.choice-control:hover,
.choice-control.selected,
.toggle-control:hover {
  border-color: var(--color-brand-hover);
  background: var(--color-surface);
  box-shadow: none;
}

.choice-control.selected::after {
  content: "";
  position: absolute;
  right: 9px;
  bottom: 9px;
  width: 14px;
  height: 14px;
  border: 4px solid var(--color-surface);
  border-radius: 50%;
  background: var(--publish-primary);
  box-shadow: 0 0 0 1px var(--publish-primary);
}

.reward-selector {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reward-choice {
  min-height: 64px;
}

.reward-choice svg {
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.reward-choice:nth-child(2) svg,
.reward-choice:nth-child(3) svg {
  color: var(--color-warning);
}

.form-row {
  gap: 16px;
}

.form-row--intro {
  grid-template-columns: 1fr;
}

.form-row--reward-detail {
  grid-template-columns: 1fr;
}

.publish-field {
  gap: 6px;
}

.publish-field span {
  color: var(--color-text);
  font-size: 0.9rem;
}

.publish-field input,
.publish-field textarea {
  border-color: var(--color-border);
  border-radius: 7px;
  color: var(--color-text);
  padding: 9px 12px;
  font-size: 0.9rem;
  line-height: 1.35;
}

.publish-field input {
  min-height: 40px;
}

.publish-field textarea {
  min-height: 76px;
  resize: vertical;
}

.publish-field input::placeholder,
.publish-field textarea::placeholder {
  color: var(--color-text-muted);
}

.publish-field input:focus,
.publish-field textarea:focus,
.choice-control:focus-visible,
.toggle-control:focus-within,
.gate-action:focus-visible,
.primary-action:focus-visible,
.ghost-action:focus-visible {
  border-color: var(--color-brand-hover);
  box-shadow: var(--shadow-focus);
}

.publish-field small {
  color: var(--publish-muted);
  font-size: 0.76rem;
  line-height: 1.32;
}


.field-error {
  font-size: 0.8rem;
}

.toggle-control,
.confirm-row {
  gap: 10px;
  padding: 12px;
}

.toggle-control {
  min-height: 64px;
}

.toggle-control > svg {
  flex: 0 0 auto;
  color: var(--publish-primary);
}

.toggle-control input,
.confirm-row input {
  width: 16px;
  height: 16px;
  accent-color: var(--publish-primary);
}

.preference-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.locked-hint,
.sensitive-panel {
  padding: 10px 12px;
  font-size: 0.86rem;
}

.form-actions {
  justify-content: center;
  gap: 14px;
  padding-top: 6px;
}

.primary-action,
.ghost-action {
  min-width: 166px;
  min-height: 52px;
  border-radius: 8px;
  font-size: 1rem;
}

.primary-action {
  border-color: var(--publish-primary-dark);
  background: var(--publish-primary);
  color: var(--color-surface);
  box-shadow: var(--shadow-button-hover);
}

.primary-action:hover:not(:disabled) {
  background: var(--publish-primary-dark);
}

.ghost-action {
  border-color: var(--color-brand-border);
  background: var(--color-surface);
  color: var(--publish-primary);
}

.ghost-action:hover:not(:disabled) {
  border-color: var(--publish-primary);
  background: var(--color-brand-soft);
}

.publish-side {
  top: 84px;
  gap: 10px;
}

.publish-side .panel-card {
  gap: 12px;
  padding: 16px;
}

.panel-heading {
  gap: 12px;
}

.side-heading-title {
  color: var(--color-text);
  font-size: 1.04rem;
}

.side-heading-title svg {
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: var(--publish-primary);
}

.task-preview-box {
  gap: 14px;
  padding: 16px;
  border-color: var(--publish-border);
}

.task-preview-box h3 {
  color: var(--color-text);
  font-size: 1.06rem;
}

.preview-list {
  gap: 13px;
}

.preview-list div {
  grid-template-columns: 118px minmax(0, 1fr);
  gap: 14px;
}

.preview-list dt {
  color: var(--publish-muted);
  font-size: 0.88rem;
}

.preview-list dd {
  color: var(--color-text);
  font-size: 0.9rem;
}

.preview-copy {
  color: var(--publish-muted);
  font-size: 0.84rem;
}

.check-list {
  gap: 5px;
}

.check-item {
  min-height: 34px;
  padding: 7px 12px;
  border-color: var(--publish-border);
  background: var(--color-surface);
  color: var(--publish-muted);
  font-size: 0.86rem;
  line-height: 1.35;
}

.check-item.done {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.progress-count {
  font-size: 0.88rem;
}

.tip-list {
  gap: 12px;
}

.tip-item {
  gap: 10px;
}

.tip-item > span {
  width: 32px;
  height: 32px;
  background: var(--publish-soft);
  color: var(--publish-primary);
}

.tip-item strong {
  color: var(--color-text);
  font-size: 0.9rem;
}

.gate-alert,
.page-feedback,
.draft-alert {
  padding: 10px 12px;
}

@media (max-width: 1180px) {
  .publish-layout {
    grid-template-columns: 1fr;
  }

  .publish-side {
    position: static;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .form-row--intro,
  .form-row--reward-detail,
  .reward-selector,
  .preference-grid,
  .publish-side {
    grid-template-columns: 1fr;
  }

  .section-heading div {
    display: grid;
    gap: 3px;
  }
}

@media (max-width: 640px) {
  .publish-page {
    gap: 12px;
  }

  .category-selector {
    grid-template-columns: 1fr;
  }

  .form-section {
    padding: 12px;
  }

  .form-actions {
    display: grid;
  }

  .primary-action,
  .ghost-action {
    width: 100%;
  }
}

@media (min-width: 1181px) {
  .publish-page {
    gap: 8px;
  }

  .form-section {
    gap: 8px;
    padding: 10px 14px;
  }

  .section-heading > span {
    width: 27px;
    height: 27px;
  }

  .category-selector,
  .reward-selector,
  .preference-grid {
    gap: 10px;
  }

  .choice-control {
    min-height: 62px;
    padding: 9px 14px;
  }

  .reward-choice {
    min-height: 58px;
  }

  .form-row {
    gap: 14px;
  }

  .publish-field input {
    min-height: 38px;
    padding-block: 8px;
  }

  .publish-field textarea {
    min-height: 58px;
    padding-block: 8px;
  }

  .publish-field small {
    font-size: 0.72rem;
  }

  .toggle-control {
    min-height: 54px;
    padding: 9px 10px;
  }

  .form-actions {
    padding-top: 4px;
  }

  .primary-action,
  .ghost-action {
    min-height: 48px;
  }

  .publish-side .panel-card {
    padding: 14px 16px;
  }

  .task-preview-box {
    padding: 14px;
  }

  .preview-list {
    gap: 10px;
  }

  .check-item {
    min-height: 31px;
    padding-block: 5px;
  }

  .tip-list {
    gap: 9px;
  }
}

@media (min-width: 1181px) {
  .publish-page {
    width: 100%;
    max-width: none;
    gap: 12px;
  }

  .publish-layout {
    grid-template-columns: minmax(0, 1fr) minmax(390px, 420px);
    gap: 18px;
  }

  .form-section {
    gap: 11px;
    padding: 14px 16px;
  }

  .section-heading h2 {
    font-size: 1.16rem;
  }

  .section-heading p {
    font-size: 0.96rem;
  }

  .choice-control {
    min-height: 70px;
    padding: 11px 16px;
  }

  .reward-choice {
    min-height: 66px;
  }

  .choice-control strong,
  .toggle-control strong {
    font-size: 1rem;
  }

  .choice-control span,
  .toggle-control small {
    font-size: 0.86rem;
  }

  .publish-field span {
    font-size: 0.96rem;
  }

  .publish-field input {
    min-height: 42px;
  }

  .publish-field textarea {
    min-height: 66px;
  }

  .publish-field input,
  .publish-field textarea {
    font-size: 0.96rem;
  }

  .toggle-control {
    min-height: 62px;
    padding: 11px 12px;
  }

  .primary-action,
  .ghost-action {
    min-height: 52px;
    font-size: 1.04rem;
  }

  .publish-side .panel-card {
    padding: 16px;
  }

  .task-preview-box {
    padding: 16px;
  }

  .task-preview-box h3,
  .side-heading-title {
    font-size: 1.1rem;
  }

  .preview-list dd,
  .tip-item strong {
    font-size: 0.96rem;
  }

  .preview-list dt,
  .check-item {
    font-size: 0.88rem;
  }
}
</style>
