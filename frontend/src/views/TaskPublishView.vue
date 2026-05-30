<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  BadgeCheck,
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
import StatusBadge from '../components/StatusBadge.vue'
import { optionLabel, REWARD_TYPE_OPTIONS, TASK_CATEGORY_OPTIONS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const DRAFT_STORAGE_KEY = 'campusbridge.taskPublishDraft.v1'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, verificationStatus, refreshSession } = useAuth()

const loadingSource = ref(false)
const submitting = ref(false)
const refreshing = ref(false)
const feedback = ref('')
const feedbackType = ref('success')
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
  deadline: defaultDeadline(),
  rewardType: 'FREE',
  rewardDescription: '',
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
  },
  {
    ...TASK_CATEGORY_OPTIONS[2],
    icon: ShieldCheck,
    note: '会场入场、签到、公干等'
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
const sourceTaskId = computed(() => (route.params.taskId ? String(route.params.taskId) : ''))
const isEditMode = computed(() => publishMode.value === 'edit')
const isRepostMode = computed(() => publishMode.value === 'repost')
const canChangeFixedFields = computed(() => !isEditMode.value)
const shellActiveLabel = computed(() => (isEditMode.value || isRepostMode.value ? '我的任务' : '发布任务'))
const pageMeta = computed(() => {
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
    deadline: taskForm.deadline,
    rewardType: taskForm.rewardType,
    rewardDescription: taskForm.rewardDescription,
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
    taskForm.rewardDescription,
    taskForm.completionStandard,
    taskForm.routeHint,
    taskForm.sensitiveReason
  ].some((value) => value.trim())
}

function restoreSavedDraft() {
  if (!savedDraft.value?.form) return
  Object.assign(taskForm, savedDraft.value.form)
  clearErrors()
  setFeedback('已恢复本机草稿，发布前请重新确认时间和地点。')
}

function discardSavedDraft() {
  clearSavedDraft()
  setFeedback('本机草稿已清除。')
}

async function handleSaveDraft() {
  feedback.value = ''

  if (!hasDraftContent()) {
    setFeedback('至少填写标题、说明、地点或完成标准中的一项后再保存草稿。', 'danger')
    return
  }

  const draft = {
    savedAt: new Date().toISOString(),
    mode: publishMode.value,
    sourceTaskId: sourceTaskId.value || null,
    form: cloneForm()
  }

  localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft))
  savedDraft.value = draft

  if (!canPublish.value) {
    setFeedback('草稿已保存在本机，可在完成登录和认证后继续发布。')
    return
  }

  submitting.value = true
  try {
    const serverDraft = await api.saveTaskDraft(buildDraftPayload())
    lastCreatedTask.value = normalizeTaskResult(serverDraft)
    setFeedback('草稿已保存到任务系统，本机也保留了一份便于继续编辑。')
  } catch (error) {
    setFeedback(error.message || '本机草稿已保存，但同步到任务系统失败。', 'danger')
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

function setFeedback(message, type = 'success') {
  feedback.value = message
  feedbackType.value = type
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
    deadline: defaultDeadline(),
    rewardType: 'FREE',
    rewardDescription: '',
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
    deadline: isRepostMode.value && isPastDateTime(nextDeadline) ? defaultDeadline() : nextDeadline,
    rewardType: task.rewardType || 'FREE',
    rewardDescription: task.rewardDescription || '',
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
  feedback.value = ''
  try {
    const result = await api.getTaskDetail(sourceTaskId.value)
    const task = normalizeTaskResult(result)
    sourceTask.value = task
    applyTaskToForm(task)
    clearErrors()
  } catch (error) {
    sourceTask.value = null
    setFeedback(error.message || '原任务加载失败，请返回任务详情后重试。', 'danger')
  } finally {
    loadingSource.value = false
  }
}

function validateTextLength(field, value, maxLength, message) {
  if (value.trim().length > maxLength) {
    setError(field, message)
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

  validateTextLength('title', taskForm.title, 100, '标题最多 100 个字。')
  validateTextLength('description', taskForm.description, 2000, '任务说明最多 2000 个字。')
  validateTextLength('campus', taskForm.campus, 50, '校区最多 50 个字。')
  validateTextLength('location', taskForm.location, 120, '地点最多 120 个字。')
  validateTextLength('rewardDescription', taskForm.rewardDescription, 200, '奖励说明最多 200 个字。')
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
  const isSensitiveTask = taskForm.category === 'ATTENDANCE_ASSIST'

  return {
    category: taskForm.category,
    title: taskForm.title.trim(),
    description: taskForm.description.trim(),
    campus: taskForm.campus.trim(),
    location: taskForm.location.trim(),
    deadline: normalizeDeadline(taskForm.deadline),
    rewardType: taskForm.rewardType,
    rewardDescription: optionalText(taskForm.rewardDescription),
    completionStandard: taskForm.completionStandard.trim(),
    requiresProof: taskForm.requiresProof,
    onlyInAppContact: taskForm.onlyInAppContact,
    allowOfflineContact: taskForm.allowOfflineContact,
    isInstantTask: taskForm.isInstantTask,
    routeHint: taskForm.isInstantTask ? optionalText(taskForm.routeHint) : undefined,
    riskDisclosureConfirmed: isSensitiveTask,
    sensitiveReason: isSensitiveTask
      ? optionalText(taskForm.sensitiveReason) || '出勤协助任务，发布者已确认风险告知'
      : undefined,
    attachmentIds: []
  }
}

function buildDraftPayload() {
  const isSensitiveTask = taskForm.category === 'ATTENDANCE_ASSIST'

  return {
    category: taskForm.category || undefined,
    title: optionalText(taskForm.title),
    description: optionalText(taskForm.description),
    campus: optionalText(taskForm.campus),
    location: optionalText(taskForm.location),
    deadline: taskForm.deadline ? normalizeDeadline(taskForm.deadline) : undefined,
    rewardType: taskForm.rewardType || undefined,
    rewardDescription: optionalText(taskForm.rewardDescription),
    completionStandard: optionalText(taskForm.completionStandard),
    requiresProof: taskForm.requiresProof,
    onlyInAppContact: taskForm.onlyInAppContact,
    allowOfflineContact: taskForm.allowOfflineContact,
    isInstantTask: taskForm.isInstantTask,
    routeHint: optionalText(taskForm.routeHint),
    riskDisclosureConfirmed: isSensitiveTask ? true : taskForm.riskDisclosureConfirmed,
    sensitiveReason: isSensitiveTask
      ? optionalText(taskForm.sensitiveReason) || '出勤协助任务，发布者已确认风险告知'
      : undefined,
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
  feedback.value = ''
  try {
    await refreshSession()
    setFeedback('认证状态已刷新。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    refreshing.value = false
  }
}

async function handleSubmitTask() {
  feedback.value = ''
  lastCreatedTask.value = null

  if (loadingSource.value) {
    setFeedback('原任务仍在加载，请稍后再保存。', 'danger')
    return
  }

  if ((isEditMode.value || isRepostMode.value) && !sourceTask.value) {
    setFeedback('没有可用的原任务内容，请返回任务详情后重试。', 'danger')
    return
  }

  if (!canPublish.value) {
    setFeedback(publishGate.value?.title || '当前状态暂不可发布任务。', 'danger')
    return
  }

  if (!validateForm()) {
    setFeedback('请先修正表单中的提示项。', 'danger')
    await focusFirstInvalidField()
    return
  }

  submitting.value = true
  try {
    if (isEditMode.value) {
      const updatedTask = await api.updateTask(sourceTaskId.value, buildUpdatePayload())
      lastCreatedTask.value = normalizeTaskResult(updatedTask)
      clearSavedDraft()
      setFeedback('任务修改已保存，返回详情后可以继续处理候选和状态。')
      return
    }

    if (isRepostMode.value) {
      const resentTask = await api.resendTask(sourceTaskId.value)
      lastCreatedTask.value = normalizeTaskResult(resentTask)
      clearSavedDraft()
      setFeedback('任务已重新发布，新的接单流程已经开始。')
      return
    }

    const createdTask = await api.createTask(buildPayload())
    lastCreatedTask.value = normalizeTaskResult(createdTask)
    clearSavedDraft()
    setFeedback('任务已发布，等待合适的同学申请接单。')
  } catch (error) {
    setFeedback(error.message || '任务保存失败，请稍后再试。', 'danger')
  } finally {
    submitting.value = false
  }
}

watch(
  () => [route.name, route.params.taskId],
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
      <section class="page-title-card">
        <div class="title-icon">
          <Send
            :size="32"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>{{ pageMeta.kicker }}</p>
          <h1>{{ pageMeta.title }}</h1>
          <span>{{ pageMeta.copy }}</span>
        </div>
        <StatusBadge
          :label="pageMeta.status"
          :tone="canPublish ? 'success' : 'warning'"
        />
      </section>

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

      <section
        v-if="feedback"
        class="page-feedback"
        :class="feedbackType === 'danger' ? 'page-feedback--danger' : 'page-feedback--success'"
        role="status"
      >
        <BadgeCheck
          :size="18"
          aria-hidden="true"
        />
        <span>{{ feedback }}</span>
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
                <p>先让帮助者知道这是什么事</p>
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
                  maxlength="100"
                  placeholder="例如：代取菜鸟驿站的快递"
                  required
                  :aria-invalid="Boolean(errors.title)"
                  :aria-describedby="describedBy('title', 'title-hint')"
                  @input="clearError('title')"
                >
                <small id="title-hint">用一句话说明任务，帮助者浏览时会优先看到它。</small>
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
                <p>让接单判断成本和路线</p>
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
                  list="campus-options"
                  maxlength="50"
                  placeholder="例如：仙林校区"
                  required
                  :disabled="!canChangeFixedFields"
                  :aria-invalid="Boolean(errors.campus)"
                  :aria-describedby="describedBy('campus')"
                  @input="clearError('campus')"
                >
                <datalist id="campus-options">
                  <option value="仙林校区" />
                  <option value="鼓楼校区" />
                  <option value="浦口校区" />
                </datalist>
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
                <span>具体地点</span>
                <input
                  id="task-location"
                  v-model.trim="taskForm.location"
                  maxlength="120"
                  placeholder="例如：快递站 3 号架 / 教学楼 A208"
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
                <p>提前说清怎样算完成</p>
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
                <p>把接触边界写在任务里</p>
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
                  <p>清晰的标题能帮助同学快速判断是否能接。</p>
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
                  <p>避开模糊、超宽范围的地点描述。</p>
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
                  <p>遵守平台规范，共建安全友善的校园互助环境。</p>
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

.page-title-card,
.panel-card,
.page-feedback,
.draft-alert,
.gate-alert {
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 36px rgba(42, 38, 61, 0.045);
}

.page-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
  min-height: 132px;
  padding: 26px 38px;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 78px;
  height: 82px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eeeaff, #f9f7ff);
  color: #746dc3;
}

.title-copy {
  display: grid;
  gap: 8px;
}

.title-copy p,
.section-kicker,
.section-heading p {
  margin: 0;
  color: #746dc3;
  font-size: 0.92rem;
  font-weight: 900;
}

.title-copy h1,
.panel-heading h2,
.section-heading h2 {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.15;
}

.title-copy h1 {
  font-size: 1.9rem;
}

.title-copy span {
  color: #8a8792;
  font-size: 1rem;
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
  border-color: #bae6fd;
  background: #f0f9ff;
  color: #075985;
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
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.gate-alert--danger,
.page-feedback--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.page-feedback--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.page-feedback--info {
  border-color: #bae6fd;
  background: #f0f9ff;
  color: #075985;
}

.gate-action {
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid currentColor;
  border-radius: 8px;
  background: #ffffff;
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
  background: #f1ecff;
  color: #746dc3;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.reward-selector,
.preference-grid,
.form-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.choice-control,
.toggle-control {
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
  color: #4a4650;
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
  color: #746dc3;
}

.reward-choice svg {
  padding: 9px;
  border-radius: 50%;
  background: #f8f5ff;
  box-sizing: content-box;
}

.choice-control strong,
.toggle-control strong {
  color: #3c3543;
  font-weight: 900;
}

.choice-control span,
.toggle-control small {
  color: #746f7a;
  line-height: 1.45;
}

.choice-control:hover,
.choice-control.selected,
.toggle-control:hover {
  border-color: #b9b2e8;
  background: #fbf9ff;
  box-shadow: 0 12px 24px rgba(116, 109, 195, 0.08);
}

.choice-control.selected {
  color: #746dc3;
}

.choice-control:disabled,
.toggle-control input:disabled + span {
  cursor: not-allowed;
  opacity: 0.68;
}

.locked-hint {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
  color: #92400e;
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
  color: #3c3543;
  font-weight: 900;
}

.publish-field input,
.publish-field textarea {
  width: 100%;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #393545;
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
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
  outline: 0;
}

.publish-field input[aria-invalid="true"],
.publish-field textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.publish-field small {
  color: #8a8792;
  line-height: 1.45;
}

.field-error {
  margin: 0;
  color: #b91c1c;
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
  accent-color: #746dc3;
}

.toggle-control span {
  display: grid;
  gap: 4px;
}

.sensitive-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
}

.sensitive-heading {
  display: flex;
  gap: 10px;
  color: #9a3412;
}

.sensitive-heading h3,
.sensitive-heading p {
  margin: 0;
}

.sensitive-heading h3 {
  color: #7c2d12;
  font-size: 1rem;
}

.sensitive-heading p {
  margin-top: 4px;
  line-height: 1.55;
}

.confirm-row {
  padding: 0;
  color: #7c2d12;
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
  border: 1px solid #746dc3;
  background: #746dc3;
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(116, 109, 195, 0.16);
}

.primary-action:hover:not(:disabled) {
  background: #625ab4;
}

.ghost-action {
  padding: 0 12px;
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #746dc3;
}

.ghost-action:hover:not(:disabled) {
  border-color: #b9b2e8;
  background: #f7f4ff;
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
  color: #3c3543;
  font-size: 1.05rem;
  font-weight: 900;
}

.side-heading-title svg {
  padding: 7px;
  border-radius: 50%;
  background: #f1ecff;
  box-sizing: content-box;
  color: #746dc3;
}

.progress-count {
  color: #3c3543;
  font-size: 0.95rem;
  white-space: nowrap;
}

.task-preview-box {
  display: grid;
  gap: 18px;
  padding: 22px;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
}

.task-preview-box h3 {
  margin: 0;
  color: #2f2940;
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
  color: #8a8792;
  font-size: 0.9rem;
  font-weight: 900;
}

.preview-list dt svg {
  color: #746dc3;
}

.preview-list dd {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
}

.preview-copy,
.tip-list,
.check-item {
  color: #746f7a;
  line-height: 1.6;
}

.preview-copy {
  margin: 0;
  padding-top: 4px;
  color: #8a8792;
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
  border: 1px solid #e6e2eb;
  border-radius: 8px;
  background: #fbf9fc;
  font-weight: 900;
}

.check-item.done {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.check-item svg {
  color: #a5a1ad;
}

.check-item.done svg {
  color: #22c55e;
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
  align-items: start;
}

.tip-item > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #f1ecff;
  color: #746dc3;
}

.tip-item strong,
.tip-item p {
  margin: 0;
}

.tip-item strong {
  color: #3c3543;
  line-height: 1.35;
}

.tip-item p {
  margin-top: 4px;
  color: #8a8792;
  line-height: 1.55;
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
  .page-title-card,
  .category-selector,
  .reward-selector,
  .preference-grid,
  .form-row,
  .preview-list,
  .preview-list div,
  .publish-side {
    grid-template-columns: 1fr;
  }

  .page-title-card {
    gap: 18px;
    padding: 22px;
  }
}

@media (max-width: 640px) {
  .page-title-card {
    grid-template-columns: 1fr;
  }

  .title-icon {
    width: 64px;
    height: 64px;
  }

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
  --publish-primary: #4f46e5;
  --publish-primary-dark: #4338ca;
  --publish-soft: #eef2ff;
  --publish-border: #dfe3ef;
  --publish-text: #111827;
  --publish-muted: #667085;
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
  background: #ffffff;
  box-shadow: none;
}

.page-title-card {
  grid-template-columns: auto minmax(0, 1fr) auto;
  min-height: 132px;
  padding: 26px 38px;
  border: 1px solid #e4e1ea;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 36px rgba(42, 38, 61, 0.045);
}

.title-icon {
  width: 78px;
  height: 82px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eeeaff, #f9f7ff);
  color: #746dc3;
}

.title-copy {
  gap: 8px;
}

.title-copy p {
  color: #746dc3;
  font-size: 0.92rem;
}

.title-copy h1 {
  color: #3c3543;
  font-size: 1.9rem;
  line-height: 1.15;
}

.title-copy span {
  color: #8a8792;
  font-size: 1rem;
}

.publish-layout {
  grid-template-columns: minmax(0, 2.24fr) minmax(360px, 0.98fr);
  gap: 20px;
}

.panel-card.publish-form {
  gap: 8px;
  padding: 0;
  border: 0;
  background: transparent;
}

.form-section {
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--publish-border);
  border-radius: 8px;
  background: #ffffff;
}

.section-heading {
  align-items: center;
  gap: 16px;
}

.section-heading > span {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: #ede9fe;
  color: var(--publish-primary);
  font-size: 1.03rem;
}

.section-heading div {
  display: flex;
  align-items: baseline;
  gap: 24px;
}

.section-heading h2 {
  color: #111827;
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
  background: #ffffff;
  color: #374151;
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
  color: #111827;
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
  border-color: #7568ff;
  background: #fbfbff;
  box-shadow: none;
}

.choice-control.selected::after {
  content: "";
  position: absolute;
  right: 9px;
  bottom: 9px;
  width: 14px;
  height: 14px;
  border: 4px solid #ffffff;
  border-radius: 50%;
  background: var(--publish-primary);
  box-shadow: 0 0 0 1px var(--publish-primary);
}

.reward-selector {
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  color: #f59e0b;
}

.form-row {
  gap: 16px;
}

.form-row--intro {
  grid-template-columns: minmax(240px, 0.85fr) minmax(280px, 1.25fr);
}

.form-row--reward-detail {
  grid-template-columns: minmax(240px, 0.92fr) minmax(280px, 1.08fr);
}

.publish-field {
  gap: 6px;
}

.publish-field span {
  color: #111827;
  font-size: 0.9rem;
}

.publish-field input,
.publish-field textarea {
  border-color: #d9dee9;
  border-radius: 7px;
  color: #111827;
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
  color: #6b7280;
}

.publish-field input:focus,
.publish-field textarea:focus,
.choice-control:focus-visible,
.toggle-control:focus-within,
.gate-action:focus-visible,
.primary-action:focus-visible,
.ghost-action:focus-visible {
  border-color: #7568ff;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.13);
}

.publish-field small {
  color: var(--publish-muted);
  font-size: 0.76rem;
  line-height: 1.32;
}

#title-hint {
  display: none;
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
  color: #ffffff;
  box-shadow: 0 14px 24px rgba(79, 70, 229, 0.2);
}

.primary-action:hover:not(:disabled) {
  background: var(--publish-primary-dark);
}

.ghost-action {
  border-color: #8b7dff;
  background: #ffffff;
  color: var(--publish-primary);
}

.ghost-action:hover:not(:disabled) {
  border-color: var(--publish-primary);
  background: #f8f7ff;
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
  color: #111827;
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
  color: #111827;
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
  color: #111827;
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
  background: #fbfbfd;
  color: var(--publish-muted);
  font-size: 0.86rem;
  line-height: 1.35;
}

.check-item.done {
  border-color: #bbf7d0;
  background: #ecfdf3;
  color: #15803d;
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
  color: #111827;
  font-size: 0.9rem;
}

.tip-item p {
  color: var(--publish-muted);
  font-size: 0.8rem;
  line-height: 1.4;
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

  .page-title-card {
    grid-template-columns: 1fr;
  }

  .title-icon {
    width: 64px;
    height: 64px;
  }

  .title-copy h1 {
    font-size: 1.48rem;
  }

  .page-title-card :deep(.status-badge) {
    grid-column: 1 / -1;
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
  .check-item,
  .tip-item p {
    font-size: 0.88rem;
  }
}
</style>
