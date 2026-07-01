<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpDown,
  Award,
  BadgeCheck,
  BookOpen,
  BriefcaseBusiness,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  ClipboardCheck,
  Clock3,
  FileCheck2,
  FileWarning,
  Filter,
  Gift,
  Inbox,
  Info,
  LoaderCircle,
  MapPin,
  MessageCircle,
  Package,
  Pencil,
  PlusCircle,
  RefreshCw,
  Search,
  ShieldCheck,
  ShieldAlert,
  Tag,
  Trash2,
  UserRound,
  Utensils,
  X
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import ModuleTopbar from '../components/layout/ModuleTopbar.vue'
import ModuleEntryCard from '../components/layout/ModuleEntryCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import UserProfileModal from '../components/UserProfileModal.vue'
import { taskNavItems } from '../app/navigation'
import {
  APPLICATION_STATUS_OPTIONS,
  MODERATION_STATUS_OPTIONS,
  optionLabel,
  REPORT_TARGET_TYPE_OPTIONS,
  REWARD_TYPE_OPTIONS,
  TASK_CATEGORY_OPTIONS,
  TASK_SORT_OPTIONS,
  TASK_STATUS_OPTIONS,
  statusTone
} from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'
import { useGuestAction } from '../composables/useGuestAction'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, currentUserId, verificationStatus } = useAuth()
const { showToast } = useToast()
const { requireLoginAction } = useGuestAction()
const MINE_TASK_FETCH_SIZE = 100

function isMineRoute(routeName = route.name) {
  return ['my-tasks', 'my-task-detail', 'workspace-tasks', 'workspace-task-detail', 'workspace-drafts'].includes(
    routeName
  )
}

const isMineView = computed(() => isMineRoute(route.name))
const isDraftView = computed(() => route.name === 'workspace-drafts')
const showMineSubtabs = computed(() => isMineView.value && !isDraftView.value)

function createDefaultFilters(routeName = route.name) {
  const defaultStatus = routeName === 'workspace-drafts' ? 'DRAFT' : isMineRoute(routeName) ? '' : 'OPEN'
  return {
    keyword: '',
    category: '',
    campus: '',
    status: defaultStatus,
    rewardType: '',
    requiresProof: '',
    sortBy: 'CREATED_AT_DESC',
    page: 1,
    size: 8
  }
}

function currentDefaultFilters() {
  return createDefaultFilters(route.name)
}

function readFiltersFromRoute(routeName = route.name) {
  const defaults = createDefaultFilters(routeName)
  return {
    keyword: readQuery('keyword', defaults.keyword),
    category: readQuery('category', defaults.category),
    campus: readQuery('campus', defaults.campus),
    status: readQuery('status', defaults.status),
    rewardType: readQuery('rewardType', defaults.rewardType),
    requiresProof: readQuery('requiresProof', defaults.requiresProof),
    sortBy: readQuery('sortBy', defaults.sortBy),
    page: readNumberQuery('page', defaults.page),
    size: readNumberQuery('size', defaults.size)
  }
}

const proofOptions = [
  { value: '', label: '不限凭证' },
  { value: 'true', label: '需要凭证' },
  { value: 'false', label: '无需凭证' }
]

const pageSizeOptions = [8, 12, 20]
const filterExpanded = ref(false)

const TASK_TYPE_META = {
  ERRAND: {
    label: '跑腿代取',
    icon: Package,
    tone: 'purple'
  },
  DINING: {
    label: '生活服务',
    icon: Utensils,
    tone: 'orange'
  },
  STUDY: {
    label: '学习互助',
    icon: BookOpen,
    tone: 'blue'
  },
  AGENCY: {
    label: '委托代办',
    icon: BriefcaseBusiness,
    tone: 'green'
  }
}

const filters = reactive(readFiltersFromRoute(route.name))

const mineSubTab = ref('published')
const minePublishedPage = ref(1)
const mineAcceptedPage = ref(1)

const tasks = ref([])
const taskDetail = ref(null)
const selectedTaskId = ref(route.params.taskId ? String(route.params.taskId) : '')
const listLoading = ref(false)
const detailLoading = ref(false)
const listError = ref('')
const detailError = ref('')
const operationLoading = ref('')
const profileUserId = ref(null)
const showProfileModal = ref(false)
const profileRelatedId = ref(null)

function openProfileModal(userId, taskId) {
  if (!userId) return
  profileUserId.value = userId
  profileRelatedId.value = taskId
  showProfileModal.value = true
}

const cancelPanelOpen = ref(false)
const cancelReason = ref('')
const cancelReasonError = ref('')
const applicationPanelOpen = ref(false)
const candidatePanelOpen = ref(false)
const reportPanelOpen = ref(false)
const candidateApplications = ref([])
const candidatesLoaded = ref(false)
const submittedApplication = ref(null)
const candidatesLoading = ref(false)
const candidatesError = ref('')
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
const reportForm = reactive({
  targetType: 'TASK',
  targetId: '',
  reason: '',
  description: ''
})
const reportErrors = reactive({})
const reportSubmitting = ref(false)
const myReports = ref([])
const reportsLoading = ref(false)
const reportsLoaded = ref(false)
const reportsError = ref('')
const reportsFilter = ref('')
const pagination = reactive({
  page: filters.page,
  size: filters.size,
  total: 0
})

let filterTimer = 0
let listRequestId = 0
let detailRequestId = 0
let candidatesRequestId = 0

const totalPages = computed(() => Math.max(1, Math.ceil(pagination.total / pagination.size)))
const selectedTask = computed(() => tasks.value.find((task) => String(task.id) === selectedTaskId.value) || null)
const detailTask = computed(() => taskDetail.value?.task || selectedTask.value || null)
const detailApplications = computed(() => taskDetail.value?.applications || [])
const candidateSource = computed(() =>
  candidatesLoaded.value ? candidateApplications.value : detailApplications.value
)
const candidates = computed(() => candidateSource.value.map(normalizeCandidate))
const sortedCandidates = computed(() =>
  [...candidates.value].sort((current, next) => candidateScore(next) - candidateScore(current))
)
const detailHistory = computed(() => taskDetail.value?.history || [])
const resultRange = computed(() => {
  if (!pagination.total) return '0'
  const start = (pagination.page - 1) * pagination.size + 1
  const end = Math.min(pagination.page * pagination.size, pagination.total)
  return `${start}-${end}`
})
const activeFilterCount = computed(() => {
  const defaults = currentDefaultFilters()
  return [
    filters.keyword.trim(),
    filters.category,
    filters.campus.trim(),
    filters.status !== defaults.status ? filters.status : '',
    filters.rewardType,
    filters.requiresProof,
    filters.sortBy !== defaults.sortBy ? filters.sortBy : ''
  ].filter(Boolean).length
})
const openTaskCount = computed(() => tasks.value.filter((task) => task.status === 'OPEN').length)
const proofTaskCount = computed(() => tasks.value.filter((task) => task.requiresProof).length)
const urgentTaskCount = computed(() => tasks.value.filter((task) => deadlineHours(task.deadline) <= 6).length)
const inProgressCount = computed(() =>
  tasks.value.filter((task) => ['ACCEPTED', 'IN_PROGRESS'].includes(task.status)).length
)
const pendingConfirmCount = computed(() =>
  tasks.value.filter((task) => task.status === 'PENDING_CONFIRMATION').length
)
const completedTaskCount = computed(() =>
  tasks.value.filter((task) => ['COMPLETED', 'REVIEWED'].includes(task.status)).length
)
const requesterTaskCards = computed(() => tasks.value.filter(isRequesterTask))
const acceptedTaskCards = computed(() => tasks.value.filter(isProviderTask))
const visibleTaskGroups = computed(() => {
  if (!isMineView.value) {
    return [
      {
        key: 'all',
        title: '',
        emptyCopy: '',
        items: tasks.value
      }
    ]
  }

  if (isDraftView.value) {
    return [
      {
        key: 'drafts',
        title: '未发布草稿',
        emptyCopy: '当前没有未发布的任务草稿。',
        items: tasks.value
      }
    ]
  }

  if (mineSubTab.value === 'accepted') {
    return [
      {
        key: 'accepted',
        title: '我接受的任务',
        emptyCopy: '当前筛选下没有你接下的任务。',
        items: acceptedTaskCards.value
      }
    ]
  }
  return [
    {
      key: 'requested',
      title: '我发出的任务',
      emptyCopy: '当前筛选下没有你发布的任务。',
      items: requesterTaskCards.value
    }
  ]
})
const mineMetricTiles = computed(() => [
  {
    key: 'open',
    label: '待接单',
    value: openTaskCount.value,
    icon: ClipboardCheck,
    tone: 'purple'
  },
  {
    key: 'in-progress',
    label: '进行中',
    value: inProgressCount.value,
    icon: Clock3,
    tone: 'blue'
  },
  {
    key: 'pending-confirm',
    label: '待确认',
    value: pendingConfirmCount.value,
    icon: ShieldCheck,
    tone: 'orange'
  },
  {
    key: 'completed',
    label: '已完成',
    value: completedTaskCount.value,
    icon: CheckCircle2,
    tone: 'green'
  }
])
const detailGuides = computed(() => buildDetailGuides(detailTask.value))
const candidateStats = computed(() => {
  const items = candidates.value
  const pendingCount = items.filter((candidate) => candidate.status === 'PENDING').length
  const verifiedCount = items.filter((candidate) => candidate.verified).length
  const averageCredit = items.length
    ? Math.round(items.reduce((sum, candidate) => sum + candidate.creditLevel, 0) / items.length)
    : 0

  return {
    total: items.length,
    pendingCount,
    verifiedCount,
    averageCredit
  }
})
const acceptedCandidate = computed(
  () => candidates.value.find((candidate) => candidate.status === 'ACCEPTED') || null
)
const submittedApplicationForTask = computed(() => {
  if (!submittedApplication.value?.taskId || !detailTask.value?.id) return null
  return String(submittedApplication.value.taskId) === String(detailTask.value.id)
    ? submittedApplication.value
    : null
})
const completionProof = computed(() => taskDetail.value?.completionProof || detailTask.value?.completionProof || null)
const proofAttachmentItems = computed(() =>
  normalizeProofAttachments(
    completionProof.value?.attachments ||
      completionProof.value?.attachmentUrls ||
      completionProof.value?.fileUrls ||
      completionProof.value?.attachmentIds ||
      []
  )
)
const listRouteName = computed(() => {
  if (isDraftView.value) return 'workspace-drafts'
  return isMineView.value ? 'workspace-tasks' : 'task-list'
})
const detailRouteName = computed(() => {
  if (isDraftView.value) return 'workspace-drafts'
  return isMineView.value ? 'workspace-task-detail' : 'task-detail'
})
const taskListTitle = computed(() => {
  if (isDraftView.value) return `${pagination.total} 条草稿 · 第 ${pagination.page} 页`
  return `${pagination.total} 条任务 · 第 ${pagination.page} 页`
})
const editableStatuses = new Set(['DRAFT', 'OPEN'])
const cancelableStatuses = new Set(['DRAFT', 'OPEN', 'ACCEPTED', 'IN_PROGRESS'])
const repostableStatuses = new Set(['CANCELLED', 'DRAFT'])
const isDetailOwner = computed(() => {
  const requesterId = detailTask.value?.requesterId
  return Boolean(
    isAuthenticated.value &&
      requesterId !== undefined &&
      requesterId !== null &&
      String(requesterId) === String(currentUserId.value)
  )
})
const isDetailProvider = computed(() => {
  if (!isAuthenticated.value || currentUserId.value === null || currentUserId.value === undefined) return false

  const task = detailTask.value
  const providerId = task?.providerId ?? task?.helperId ?? task?.acceptedApplicantId
  if (providerId !== undefined && providerId !== null) {
    return String(providerId) === String(currentUserId.value)
  }

  return candidates.value.some(
    (candidate) =>
      candidate.status === 'ACCEPTED' &&
      candidate.applicantId !== undefined &&
      candidate.applicantId !== null &&
      String(candidate.applicantId) === String(currentUserId.value)
  )
})
const canEditDetailTask = computed(
  () => isDetailOwner.value && editableStatuses.has(detailTask.value?.status)
)
const canCancelDetailTask = computed(
  () => isDetailOwner.value && cancelableStatuses.has(detailTask.value?.status)
)
const canRepostDetailTask = computed(() => {
  if (!isDetailOwner.value || !detailTask.value) return false
  return repostableStatuses.has(detailTask.value.status)
})
const hasDetailActions = computed(
  () => canEditDetailTask.value || canCancelDetailTask.value || canRepostDetailTask.value
)
const canStartDetailTask = computed(
  () => isDetailProvider.value && detailTask.value?.status === 'ACCEPTED'
)
const canSubmitCompletion = computed(
  () => isDetailProvider.value && detailTask.value?.status === 'IN_PROGRESS'
)
const canReviewCompletion = computed(
  () => isDetailOwner.value && detailTask.value?.status === 'PENDING_CONFIRMATION'
)
const canDisputeCompletion = computed(() => {
  const status = detailTask.value?.status
  return Boolean(
    (isDetailOwner.value || isDetailProvider.value) &&
      ['IN_PROGRESS', 'PENDING_CONFIRMATION'].includes(status)
  )
})
const shouldShowCompletionPanel = computed(() => {
  const status = detailTask.value?.status
  return Boolean(
    status &&
      (['ACCEPTED', 'IN_PROGRESS', 'PENDING_CONFIRMATION', 'COMPLETED', 'REVIEWED', 'DISPUTED'].includes(status) ||
        completionProof.value ||
        isDetailProvider.value)
  )
})
const canOpenConversation = computed(() => {
  const status = detailTask.value?.status
  return Boolean(
    detailTask.value?.id &&
      (isDetailOwner.value || isDetailProvider.value) &&
      ['ACCEPTED', 'IN_PROGRESS', 'PENDING_CONFIRMATION', 'COMPLETED', 'REVIEWED', 'DISPUTED'].includes(status)
  )
})
const detailTaskReports = computed(() =>
  myReports.value.filter((report) => report.targetType === 'TASK' && String(report.targetId) === String(detailTask.value?.id))
)
const selectedDetailReport = computed(() => detailTaskReports.value[0] || null)
const detailConversationPeerUserId = computed(() => {
  const task = detailTask.value
  if (!task || currentUserId.value === null || currentUserId.value === undefined) return ''

  const requesterId = task.requesterId ?? task.ownerId
  const providerId = task.providerId ?? task.helperId ?? task.acceptedApplicantId ?? acceptedCandidate.value?.applicantId

  if (requesterId !== undefined && requesterId !== null && String(currentUserId.value) === String(requesterId)) {
    return providerId || ''
  }

  if (providerId !== undefined && providerId !== null && String(currentUserId.value) === String(providerId)) {
    return requesterId || ''
  }

  return ''
})
const completionStageItems = computed(() => buildCompletionStageItems(detailTask.value?.status))
const completionPanelCopy = computed(() => {
  const status = detailTask.value?.status
  if (canStartDetailTask.value) return '你已被选为帮助者，可以开始执行并在完成后提交说明或图片凭证。'
  if (canSubmitCompletion.value) return '完成后请提交清楚的结果说明，必要时附上图片、截图或材料链接。'
  if (canReviewCompletion.value) return '帮助者已提交完成凭证，请对照完成标准核验后处理。'
  if (status === 'PENDING_CONFIRMATION') return '任务正在等待求助者确认，凭证和处理记录会保留在详情中。'
  if (status === 'COMPLETED' || status === 'REVIEWED') return '任务已完成，后续可以进入评价与信用记录环节。'
  if (status === 'DISPUTED') return '任务已进入争议流程，处理时应优先参考站内记录、时间戳和完成凭证。'
  return '任务进入协作后，完成说明、确认结果和异常原因都会留在这里。'
})
const resolutionMeta = computed(() => {
  const meta = {
    CONFIRM_COMPLETION: {
      title: '确认任务完成',
      copy: '确认后任务会进入已完成状态，后续可进入评价或结算流程。',
      tone: 'success',
      button: '确认完成'
    },
    REJECT_COMPLETION: {
      title: '驳回完成结果',
      copy: '请写清楚哪里不符合完成标准，方便帮助者补充说明或重新执行。',
      tone: 'warning',
      button: '提交驳回'
    },
    DISPUTE: {
      title: '发起争议处理',
      copy: '请描述分歧点，平台后续会结合站内沟通、时间戳和凭证进行仲裁。',
      tone: 'danger',
      button: '提交争议'
    }
  }

  return meta[resolutionAction.value] || null
})
const proofDescription = computed(
  () => completionProof.value?.description || completionProof.value?.proofDescription || ''
)
const proofSubmittedAt = computed(
  () => completionProof.value?.submittedAt || completionProof.value?.createdAt || completionProof.value?.updatedAt || ''
)
const hasCurrentUserApplied = computed(() =>
  Boolean(submittedApplicationForTask.value) ||
  candidates.value.some(
    (candidate) =>
      candidate.applicantId !== undefined &&
      candidate.applicantId !== null &&
      String(candidate.applicantId) === String(currentUserId.value)
  )
)
const canApplyDetailTask = computed(() => {
  if (!detailTask.value) return false
  return Boolean(
    isAuthenticated.value &&
      verificationStatus.value === 'VERIFIED' &&
      !isDetailOwner.value &&
      detailTask.value.status === 'OPEN' &&
      !hasCurrentUserApplied.value
  )
})
const applicationGate = computed(() => {
  if (!detailTask.value || isDetailOwner.value) return null
  if (!isAuthenticated.value) {
    return {
      tone: 'danger',
      title: '登录后才能申请接单',
      copy: '登录后可申请并接收后续通知。',
      action: '去登录',
      to: '/auth'
    }
  }
  if (verificationStatus.value !== 'VERIFIED') {
    return {
      tone: 'warning',
      title: '完成校内认证后再接单',
      copy: '认证后可申请接单。',
      action: '去认证中心',
      to: '/home'
    }
  }
  if (isDetailProvider.value) {
    return {
      tone: 'success',
      title: '你已被选为帮助者',
      copy: '进入执行后提交凭证，可用任务会话沟通。'
    }
  }
  if (hasCurrentUserApplied.value) {
    return {
      tone: 'success',
      title: '你已提交接单申请',
      copy: '申请已提交，留意任务状态。'
    }
  }
  if (detailTask.value.status !== 'OPEN') {
    return {
      tone: 'muted',
      title: '当前任务暂不开放申请',
      copy: '当前状态不能提交新申请。'
    }
  }
  return null
})

function firstValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function readQuery(key, fallback) {
  return firstValue(route.query[key]) ?? fallback
}

function readNumberQuery(key, fallback) {
  const value = Number(firstValue(route.query[key]))
  return Number.isFinite(value) && value > 0 ? value : fallback
}

function normalizeTags(value) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }
  if (typeof value === 'string') {
    return value
      .split(/[,，\s]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  }
  return []
}

function numericValue(...values) {
  const value = values.find((item) => item !== undefined && item !== null && item !== '')
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
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

function normalizeTask(task = {}) {
  const id = task.id ?? task.taskId
  return {
    ...task,
    id,
    requesterId: task.requesterId ?? task.ownerId,
    providerId: task.providerId ?? task.helperId ?? task.acceptedApplicantId,
    category: task.category || task.taskCategory || task.typeKey || 'AGENCY',
    rewardType: task.rewardType || 'FREE',
    status: task.status || 'OPEN',
    requiresProof: booleanFlag(task.requiresProof),
    onlyInAppContact: booleanFlag(task.onlyInAppContact, true),
    allowOfflineContact: booleanFlag(task.allowOfflineContact),
    isInstantTask: booleanFlag(task.isInstantTask),
    riskDisclosureConfirmed: booleanFlag(task.riskDisclosureConfirmed)
  }
}

function normalizePagedTasks(result) {
  if (Array.isArray(result)) return result
  return result?.tasks || result?.items || result?.records || result?.content || result?.list || []
}

function normalizeTaskDetail(result) {
  if (!result) return null

  if (result.task) {
    return {
      ...result,
      task: normalizeTask(result.task),
      applications: Array.isArray(result.applications) ? result.applications : [],
      history: Array.isArray(result.history) ? result.history : [],
      reviews: Array.isArray(result.reviews) ? result.reviews : []
    }
  }

  return {
    task: normalizeTask(result),
    applications: [],
    completionProof: result.completionProof || null,
    history: [],
    reviews: []
  }
}

function normalizeCandidate(application = {}) {
  const applicantId = application.applicantId ?? application.userId ?? application.applicant?.id
  const fallbackName = applicantId ? `同学 ${applicantId}` : '候选同学'
  const publicTags = normalizeTags(
    application.tags ?? application.publicTags ?? application.profileTags ?? application.skillTags
  )

  return {
    applicationId: application.applicationId ?? application.id,
    applicantId,
    name:
      application.nickname ||
      application.applicantName ||
      application.username ||
      application.applicant?.nickname ||
      fallbackName,
    username: application.username || application.applicant?.username || '',
    avatarUrl: application.avatarUrl || application.applicant?.avatarUrl || '',
    bio: application.bio || application.applicant?.bio || '',
    campus: application.campus || application.applicant?.campus || '',
    college: application.college || application.department || application.applicant?.college || '',
    tags: publicTags,
    verified: Boolean(application.verified ?? application.isVerified ?? application.applicant?.verified),
    creditLevel: Math.max(1, numericValue(application.creditLevel, application.level, application.credit?.level) || 1),
    points: numericValue(application.points, application.creditPoints, application.credit?.points),
    completedCount: numericValue(
      application.completedCount,
      application.completedTasks,
      application.completedTaskCount,
      application.historyCompletedCount
    ),
    message: application.message || '',
    status: String(application.status || 'PENDING').toUpperCase(),
    createdAt: application.createdAt || application.appliedAt || ''
  }
}

function candidateInitial(candidate = {}) {
  const source = candidate.name || candidate.username || candidate.applicantId || '候'
  return String(source).trim().slice(0, 1).toUpperCase()
}

function candidateAccountCopy(candidate = {}) {
  if (candidate.username) return `@${candidate.username}`
  if (candidate.applicantId) return `用户 ID ${candidate.applicantId}`
  return '账号信息待同步'
}

function candidateProfileCopy(candidate = {}) {
  const parts = [candidate.campus, candidate.college].filter(Boolean)
  return parts.length ? parts.join(' · ') : '档案信息待补充'
}

function candidateSubmittedCopy(candidate = {}) {
  return candidate.createdAt ? `申请于 ${formatDateTime(candidate.createdAt)}` : '申请时间待同步'
}

function candidateCardClass(candidate = {}) {
  return `candidate-card--${String(candidate.status || 'PENDING').toLowerCase()}`
}

function taskTypeKey(task = {}) {
  return task.typeKey || task.taskType || task.serviceType || task.category || 'AGENCY'
}

function taskTypeMeta(task = {}) {
  return TASK_TYPE_META[taskTypeKey(task)] || TASK_TYPE_META[task.category] || TASK_TYPE_META.AGENCY
}

function taskCategoryLabel(task = {}) {
  return task.typeLabel || taskTypeMeta(task).label || optionLabel(TASK_CATEGORY_OPTIONS, task.category)
}

function taskRewardValue(task = {}) {
  if (task.rewardType === 'FREE') return '无偿互助'
  if (task.rewardType === 'NEGOTIABLE') return '双方协商'

  if (task.rewardAmount !== undefined && task.rewardAmount !== null && task.rewardAmount !== '') {
    if (task.rewardType === 'POINTS') return `${task.rewardAmount} 积分`
    if (task.rewardType === 'CASH') return `¥ ${task.rewardAmount}`
    return String(task.rewardAmount)
  }

  if (task.rewardDescription) return task.rewardDescription
  return optionLabel(REWARD_TYPE_OPTIONS, task.rewardType)
}

function taskRewardUnit(task = {}) {
  if (task.rewardType === 'CASH') return '现金'
  if (task.rewardType === 'POINTS') return '积分'
  if (task.rewardType === 'FREE') return '互助'
  if (task.rewardType === 'NEGOTIABLE') return '协商'
  return optionLabel(REWARD_TYPE_OPTIONS, task.rewardType)
}

function taskRewardTone(task = {}) {
  return {
    CASH: 'cash',
    POINTS: 'points',
    FREE: 'free',
    NEGOTIABLE: 'negotiable'
  }[task.rewardType] || 'neutral'
}

function formatRelativeTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  const minutes = Math.max(0, Math.round((Date.now() - date.getTime()) / 60000))
  if (minutes < 1) return '刚刚发布'
  if (minutes < 60) return `${minutes} 分钟前发布`

  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} 小时前发布`

  return `${Math.round(hours / 24)} 天前发布`
}

function taskContactCopy(task = {}) {
  return task.onlyInAppContact ? '仅站内沟通' : '可协商站外沟通'
}

function taskSafetyCopy(task = {}) {
  return task.allowOfflineContact ? '公开地点交接' : '不建议线下接触'
}

function isRequesterTask(task = {}) {
  if (currentUserId.value === null || currentUserId.value === undefined) return false
  const requesterId = task.requesterId ?? task.ownerId
  return requesterId !== undefined && requesterId !== null && String(requesterId) === String(currentUserId.value)
}

function isProviderTask(task = {}) {
  if (currentUserId.value === null || currentUserId.value === undefined) return false
  const providerId = task.providerId ?? task.helperId ?? task.acceptedApplicantId
  return providerId !== undefined && providerId !== null && String(providerId) === String(currentUserId.value)
}

function applyTaskResult(result) {
  let items = normalizePagedTasks(result).map(normalizeTask)

  if (isMineView.value) {
    const mineItems = items.filter((task) => isRequesterTask(task) || isProviderTask(task))

    if (isDraftView.value) {
      const draftItems = mineItems.filter((task) => isRequesterTask(task) && task.status === 'DRAFT')
      const start = (filters.page - 1) * filters.size
      const pagedItems = draftItems.slice(start, start + filters.size)

      tasks.value = pagedItems
      pagination.page = filters.page
      pagination.size = filters.size
      pagination.total = draftItems.length
      return
    }

    const nonDraftMineItems = mineItems.filter((task) => task.status !== 'DRAFT')
    const publishedItems = nonDraftMineItems.filter((task) => isRequesterTask(task))
    const acceptedItems = nonDraftMineItems.filter((task) => isProviderTask(task))

    const activePage = mineSubTab.value === 'published' ? minePublishedPage : mineAcceptedPage
    const activeItems = mineSubTab.value === 'published' ? publishedItems : acceptedItems

    const start = (activePage.value - 1) * filters.size
    const pagedItems = activeItems.slice(start, start + filters.size)

    tasks.value = pagedItems
    pagination.page = activePage.value
    pagination.size = filters.size
    pagination.total = activeItems.length
    filters.page = activePage.value
    return
  }

  const page = Number(result?.page || filters.page || 1)
  const size = Number(result?.size || filters.size || currentDefaultFilters().size)
  const explicitTotal = Number(result?.total ?? result?.totalElements ?? result?.totalCount ?? result?.count)
  const inferredTotal = (page - 1) * size + items.length + (items.length >= size ? 1 : 0)

  tasks.value = items
  pagination.page = page
  pagination.size = size
  pagination.total = Number.isFinite(explicitTotal) ? explicitTotal : inferredTotal
  filters.page = pagination.page
  filters.size = pagination.size
}

function candidateScore(candidate) {
  const statusWeight = candidate.status === 'ACCEPTED' ? 1000 : candidate.status === 'PENDING' ? 500 : 0
  return statusWeight + candidate.creditLevel * 40 + candidate.completedCount * 8 + candidate.points / 20
}

function taskStatusLabel(status) {
  return optionLabel(TASK_STATUS_OPTIONS, status)
}

function taskStatusTone(status) {
  return statusTone(status)
}

function applicationStatusLabel(status) {
  return optionLabel(APPLICATION_STATUS_OPTIONS, status)
}

function applicationStatusTone(status) {
  return statusTone(status)
}

function reportTargetLabel(type) {
  return optionLabel(REPORT_TARGET_TYPE_OPTIONS, type)
}

function reportStatusLabel(status) {
  return optionLabel(MODERATION_STATUS_OPTIONS, status)
}

function reportStatusTone(status) {
  return statusTone(status)
}

function normalizeReport(report = {}) {
  return {
    ...report,
    id: report.id ?? report.reportId,
    status: report.status || 'PENDING'
  }
}

function normalizePagedReports(result) {
  if (Array.isArray(result)) return result
  return result?.items || result?.reports || result?.records || result?.content || result?.list || []
}

function formatDateTime(value) {
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

function formatCreatedAt(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit'
  })
}

function deadlineHours(value) {
  if (!value) return Number.POSITIVE_INFINITY
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return Number.POSITIVE_INFINITY
  return (date.getTime() - Date.now()) / 36e5
}

function deadlineCopy(value) {
  const hours = deadlineHours(value)
  if (!Number.isFinite(hours)) return '截止时间未设置'
  if (hours <= 0) return '已过截止时间'
  if (hours < 1) return '1 小时内截止'
  if (hours < 24) return `${Math.ceil(hours)} 小时内截止`
  return `${Math.ceil(hours / 24)} 天后截止`
}

function deadlineTone(value) {
  const hours = deadlineHours(value)
  if (hours <= 0) return 'danger'
  if (hours <= 6) return 'warning'
  if (hours <= 24) return 'info'
  return 'muted'
}

function shortDescription(text) {
  if (!text) return '求助者暂未补充说明。'
  return text.length > 112 ? `${text.slice(0, 112)}...` : text
}

function attachmentNameFromUri(uri, index) {
  if (!uri) return `凭证 ${index + 1}`
  if (uri.startsWith('data:')) return `本地图片 ${index + 1}`

  try {
    const url = new URL(uri)
    const filename = decodeURIComponent(url.pathname.split('/').filter(Boolean).at(-1) || '')
    return filename || url.hostname || `凭证 ${index + 1}`
  } catch {
    return uri.length > 36 ? `${uri.slice(0, 36)}...` : uri
  }
}

function normalizeAttachmentItem(item, index = 0) {
  if (!item) return null

  if (typeof item === 'string') {
    return {
      id: null,
      url: item,
      name: attachmentNameFromUri(item, index)
    }
  }

  const url = item.url || item.fileUrl || item.uri || item.previewUrl || ''
  const id = item.id ?? item.attachmentId ?? null

  return {
    id,
    url,
    previewUrl: item.previewUrl || url,
    name: item.name || item.fileName || (url ? attachmentNameFromUri(url, index) : `附件 ${id || index + 1}`),
    type: item.type || item.contentType || '',
    status: item.status || '',
    size: item.size || 0
  }
}

function normalizeProofAttachments(value = []) {
  if (!Array.isArray(value)) return []
  return value
    .filter(Boolean)
    .map((item, index) => {
      if (typeof item === 'number') {
        return normalizeAttachmentItem({ id: item, name: `附件 #${item}` }, index)
      }
      return normalizeAttachmentItem(item, index)
    })
    .filter(Boolean)
}

function inferAttachmentType(file = {}) {
  return String(file.type || '').toLowerCase().startsWith('image/') ? 'IMAGE' : 'FILE'
}

function completionAttachmentIds() {
  return completionAttachments.value
    .map((item) => Number(item.id ?? item.attachmentId))
    .filter((id) => Number.isInteger(id) && id > 0)
}

function buildCompletionStageItems(status) {
  const stageIndex = {
    ACCEPTED: 0,
    IN_PROGRESS: 1,
    PENDING_CONFIRMATION: 2,
    COMPLETED: 3,
    REVIEWED: 3,
    DISPUTED: 3
  }
  const current = stageIndex[status] ?? -1

  return [
    { label: '已选人', done: current >= 0, active: status === 'ACCEPTED' },
    { label: '进行中', done: current >= 1, active: status === 'IN_PROGRESS' },
    { label: '待确认', done: current >= 2, active: status === 'PENDING_CONFIRMATION' },
    {
      label: status === 'DISPUTED' ? '争议中' : '已完成',
      done: current >= 3,
      active: ['COMPLETED', 'REVIEWED', 'DISPUTED'].includes(status)
    }
  ]
}

function buildTaskQuery() {
  const query = {
    keyword: filters.keyword.trim(),
    category: filters.category,
    campus: filters.campus.trim(),
    status: filters.status,
    rewardType: filters.rewardType,
    requiresProof: filters.requiresProof === '' ? undefined : filters.requiresProof === 'true',
    excludeExpired: !isMineView.value,
    sortBy: filters.sortBy,
    page: filters.page,
    size: filters.size
  }

  if (isMineView.value) {
    query.page = 1
    query.size = MINE_TASK_FETCH_SIZE
  }

  if (isDraftView.value) {
    query.status = 'DRAFT'
  }

  return query
}

function buildRouteQuery() {
  const defaults = currentDefaultFilters()
  const query = {
    keyword: filters.keyword.trim() || undefined,
    category: filters.category || undefined,
    campus: filters.campus.trim() || undefined,
    status: filters.status !== defaults.status ? filters.status || undefined : undefined,
    rewardType: filters.rewardType || undefined,
    requiresProof: filters.requiresProof || undefined,
    sortBy: filters.sortBy !== defaults.sortBy ? filters.sortBy || undefined : undefined,
    page: filters.page !== defaults.page ? String(filters.page) : undefined,
    size: filters.size !== defaults.size ? String(filters.size) : undefined
  }

  Object.keys(query).forEach((key) => {
    if (query[key] === undefined) delete query[key]
  })

  return query
}

async function syncRoute() {
  const query = buildRouteQuery()
  if (selectedTaskId.value) {
    await router.replace({
      name: detailRouteName.value,
      params: { taskId: selectedTaskId.value },
      query
    })
    return
  }

  await router.replace({ name: listRouteName.value, query })
}

async function loadTasks() {
  listLoading.value = true
  listError.value = ''
  const requestId = ++listRequestId

  try {
    const result = await api.getTasks(buildTaskQuery())
    if (requestId !== listRequestId) return

    applyTaskResult(result)
  } catch (error) {
    if (requestId !== listRequestId) return
    tasks.value = []
    pagination.page = filters.page
    pagination.size = filters.size
    pagination.total = 0
    listError.value = error.message || '任务列表加载失败，请稍后重试。'
  } finally {
    if (requestId === listRequestId) listLoading.value = false
  }
}

async function loadTaskDetail(taskId) {
  if (!taskId) {
    taskDetail.value = null
    detailError.value = ''
    applicationPanelOpen.value = false
    candidatePanelOpen.value = false
    candidateApplications.value = []
    candidatesLoaded.value = false
    candidatesError.value = ''
    submittedApplication.value = null
    return
  }

  if (String(submittedApplication.value?.taskId || '') !== String(taskId)) {
    submittedApplication.value = null
  }

  detailLoading.value = true
  detailError.value = ''
  const requestId = ++detailRequestId

  try {
    const result = await api.getTaskDetail(taskId)
    if (requestId !== detailRequestId) return
    taskDetail.value = normalizeTaskDetail(result)
    await loadCandidates(taskId)
  } catch (error) {
    if (requestId !== detailRequestId) return
    taskDetail.value = null
    candidateApplications.value = []
    candidatesLoaded.value = false
    candidatesError.value = ''
    detailError.value = error.message || '任务详情加载失败，请稍后重试。'
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

async function loadMyReports() {
  if (!isAuthenticated.value) {
    myReports.value = []
    reportsLoaded.value = false
    reportsError.value = ''
    reportsLoading.value = false
    return
  }

  reportsLoading.value = true
  reportsError.value = ''
  try {
    const result = await api.getReports({
      status: reportsFilter.value,
      page: 1,
      size: 100
    })
    myReports.value = normalizePagedReports(result).map(normalizeReport)
    reportsLoaded.value = true
  } catch (error) {
    myReports.value = []
    reportsLoaded.value = true
    reportsError.value = error.message || '举报记录加载失败，请稍后重试。'
  } finally {
    reportsLoading.value = false
  }
}

async function loadCandidates(taskId) {
  if (!taskId || !isAuthenticated.value || !isDetailOwner.value) {
    candidateApplications.value = []
    candidatesLoaded.value = false
    candidatesError.value = ''
    candidatesLoading.value = false
    return
  }

  candidatesLoading.value = true
  candidatesError.value = ''
  const requestId = ++candidatesRequestId

  try {
    const result = await api.getTaskApplications(taskId)
    if (requestId !== candidatesRequestId) return
    candidateApplications.value = Array.isArray(result)
      ? result
      : result?.applications || result?.items || result?.records || result?.content || result?.list || []
    candidatesLoaded.value = true
  } catch (error) {
    if (requestId !== candidatesRequestId) return
    candidateApplications.value = []
    candidatesLoaded.value = true
    candidatesError.value = error.message || '候选申请加载失败，请稍后重试。'
  } finally {
    if (requestId === candidatesRequestId) candidatesLoading.value = false
  }
}

function switchMineTab(tab) {
  mineSubTab.value = tab
  applyFilters()
}

function handleMinePageChange(page) {
  if (mineSubTab.value === 'published') {
    minePublishedPage.value = page
  } else {
    mineAcceptedPage.value = page
  }
  filters.page = page
  refreshTasks()
}

async function applyFilters() {
  window.clearTimeout(filterTimer)
  filters.page = 1
  if (isMineView.value) {
    minePublishedPage.value = 1
    mineAcceptedPage.value = 1
  }
  await syncRoute()
  await loadTasks()
}

function queueFilterRefresh() {
  window.clearTimeout(filterTimer)
  filterTimer = window.setTimeout(() => {
    applyFilters()
  }, 280)
}

async function resetFilters() {
  window.clearTimeout(filterTimer)
  Object.assign(filters, currentDefaultFilters())
  selectedTaskId.value = ''
  taskDetail.value = null
  applicationPanelOpen.value = false
  candidatePanelOpen.value = false
  reportPanelOpen.value = false
  candidateApplications.value = []
  candidatesLoaded.value = false
  candidatesError.value = ''
  submittedApplication.value = null
  applicationMessage.value = ''
  applicationMessageError.value = ''
  clearReportErrors()
  await syncRoute()
  await loadTasks()
}

async function refreshTasks() {
  await syncRoute()
  await loadTasks()
  if (selectedTaskId.value) await loadTaskDetail(selectedTaskId.value)
}

async function changePage(nextPage) {
  const normalizedPage = Math.min(Math.max(nextPage, 1), totalPages.value)
  if (normalizedPage === filters.page) return
  filters.page = normalizedPage
  if (isMineView.value) {
    handleMinePageChange(normalizedPage)
  }
  await syncRoute()
  await loadTasks()
}

async function selectTask(task) {
  if (!task?.id) return
  const nextTaskId = String(task.id)
  selectedTaskId.value = nextTaskId

  await router.push({
    name: detailRouteName.value,
    params: { taskId: nextTaskId },
    query: buildRouteQuery()
  })
}

async function clearSelectedTask() {
  selectedTaskId.value = ''
  taskDetail.value = null
  detailError.value = ''
  applicationPanelOpen.value = false
  candidatePanelOpen.value = false
  reportPanelOpen.value = false
  candidateApplications.value = []
  candidatesLoaded.value = false
  candidatesError.value = ''
  submittedApplication.value = null
  applicationMessage.value = ''
  applicationMessageError.value = ''
  cancelPanelOpen.value = false
  resetCompletionState()
  clearReportErrors()
  await router.push({ name: listRouteName.value, query: buildRouteQuery() })
}

async function openEditTask() {
  if (!requireLoginAction('编辑任务')) return
  if (!detailTask.value?.id || !canEditDetailTask.value) return
  await router.push({ name: 'task-edit', params: { taskId: detailTask.value.id } })
}

async function openRepostTask() {
  if (!requireLoginAction('重新发布任务')) return
  if (!detailTask.value?.id || !canRepostDetailTask.value) return
  await router.push({ name: 'task-repost', params: { taskId: detailTask.value.id } })
}

async function handlePublishShortcut() {
  if (!requireLoginAction('发布任务')) return
  await router.push({ name: 'task-publish' })
}

function openCancelPanel() {
  if (!requireLoginAction('管理任务')) return
  if (!canCancelDetailTask.value) return
  cancelPanelOpen.value = true
  cancelReason.value = ''
  cancelReasonError.value = ''
}

function closeCancelPanel() {
  cancelPanelOpen.value = false
  cancelReason.value = ''
  cancelReasonError.value = ''
}

function openApplicationPanel() {
  if (!detailTask.value || isDetailOwner.value) return
  if (!requireLoginAction('申请接单')) return
  applicationPanelOpen.value = true
  applicationMessageError.value = ''
}

function closeApplicationPanel() {
  applicationPanelOpen.value = false
  applicationMessageError.value = ''
}

async function openCandidatePanel() {
  if (!detailTask.value?.id || !isDetailOwner.value) return
  candidatePanelOpen.value = true
  if (!candidatesLoaded.value && !candidatesLoading.value) {
    await loadCandidates(detailTask.value.id)
  }
}

function closeCandidatePanel() {
  candidatePanelOpen.value = false
}

function focusCompletionForm() {
  const descriptionInput = document.getElementById('completion-description')
  if (descriptionInput) {
    descriptionInput.focus()
    descriptionInput.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  showToast('任务已进入执行流程，请在完成确认区域处理后续状态。', { type: 'success' })
}

async function submitQuickConfirmCompletion() {
  if (!detailTask.value?.id || !canReviewCompletion.value) return

  operationLoading.value = 'quick-confirm'
  try {
    await api.confirmTaskCompletion(detailTask.value.id)
    showToast('任务已确认完成。', { type: 'success' })
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '确认完成失败，请稍后重试。', { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

async function openTaskConversation() {
  if (!detailTask.value?.id) return
  if (!requireLoginAction('任务私信')) return
  if (!canOpenConversation.value) return

  const peerUserId = detailConversationPeerUserId.value
  if (!peerUserId) {
    showToast('未能确定接单者或发布者，暂时无法打开任务会话。', { type: 'danger' })
    return
  }

  operationLoading.value = 'conversation'
  try {
    const conversation = await api.createConversation({
      relatedType: 'TASK',
      relatedId: Number(detailTask.value.id),
      peerUserId: Number(peerUserId),
      initialMessage: 'hello'
    })
    const conversationId = conversation?.id ?? conversation?.conversationId
    if (!conversationId) {
      throw new Error('会话创建成功但未返回会话 ID。')
    }

    await router.push({
      name: 'conversation-detail',
      params: { conversationId },
      query: {
        relatedType: 'TASK',
        relatedId: detailTask.value.id,
        peerUserId
      }
    })
  } catch (error) {
    showToast(error.message || '打开任务会话失败，请稍后重试。', { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

async function openReportPanel() {
  if (!detailTask.value?.id) return
  if (!requireLoginAction('提交举报')) return
  reportPanelOpen.value = true
  hydrateReportForm()
  if (!reportsLoaded.value && !reportsLoading.value) {
    await loadMyReports()
  }
}

function closeReportPanel() {
  reportPanelOpen.value = false
  clearReportErrors()
}

function hydrateReportForm() {
  reportForm.targetType = 'TASK'
  reportForm.targetId = detailTask.value?.id ? String(detailTask.value.id) : ''
  reportForm.reason = ''
  reportForm.description = ''
  clearReportErrors()
}

function clearReportErrors() {
  Object.keys(reportErrors).forEach((field) => {
    delete reportErrors[field]
  })
}

function setReportError(field, message) {
  reportErrors[field] = message
}

function validateReportForm() {
  clearReportErrors()

  if (!String(reportForm.reason).trim()) {
    setReportError('reason', '请填写举报原因。')
  }
  if (reportForm.reason.trim().length > 100) {
    setReportError('reason', '举报原因不能超过 100 个字符。')
  }
  if (reportForm.description.trim().length > 1000) {
    setReportError('description', '详细描述不能超过 1000 个字符。')
  }

  return Object.keys(reportErrors).length === 0
}

async function submitReport() {
  if (!validateReportForm()) return

  reportSubmitting.value = true
  try {
    const result = await api.createReport({
      targetType: reportForm.targetType,
      targetId: Number(reportForm.targetId),
      reason: reportForm.reason.trim(),
      description: reportForm.description.trim()
    })
    const normalized = normalizeReport(result)
    myReports.value = [normalized, ...myReports.value.filter((report) => String(report.id) !== String(normalized.id))]
    reportsLoaded.value = true
    showToast(`举报 #${normalized.id || reportForm.targetId} 已提交，管理员会根据站内记录处理。`, { type: 'success' })
    closeReportPanel()
  } catch (error) {
    showToast(error.message || '举报提交失败，请稍后重试。', { type: 'danger' })
  } finally {
    reportSubmitting.value = false
  }
}

async function quickDeleteDraft(task) {
  if (!task?.id || task.status !== 'DRAFT') return
  if (!confirm('确认删除这个草稿？')) return
  operationLoading.value = 'delete-draft'
  try {
    await api.deleteTaskDraft(task.id)
    showToast('草稿已删除。', { type: 'success' })
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '删除草稿失败，请稍后重试。', { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

async function openDraftEditor(task) {
  if (!requireLoginAction('编辑草稿')) return
  if (!task?.id || task.status !== 'DRAFT') return

  await router.push({
    name: 'task-publish',
    query: { draftId: String(task.id) }
  })
}

async function submitCancelTask() {
  if (!detailTask.value?.id || !canCancelDetailTask.value) return

  const isDraft = detailTask.value.status === 'DRAFT'
  const reason = cancelReason.value.trim()
  if (!isDraft && reason.length < 4) {
    cancelReasonError.value = '请用至少 4 个字说明取消原因，方便后续追溯。'
    return
  }

  operationLoading.value = 'cancel'
  cancelReasonError.value = ''
  try {
    if (isDraft) {
      await api.deleteTaskDraft(detailTask.value.id)
    } else {
      await api.cancelTask(detailTask.value.id, reason)
    }
    showToast(isDraft ? '草稿已删除。' : '任务已取消，状态记录会保留在任务历史中。', { type: 'success' })
    closeCancelPanel()
    await refreshTasks()
  } catch (error) {
    showToast(error.message || `${isDraft ? '删除草稿失败' : '取消任务失败'}，请稍后重试。`, { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

function clearCompletionError(field) {
  delete completionErrors[field]
}

function setCompletionError(field, message) {
  completionErrors[field] = message
}

function clearCompletionErrors() {
  Object.keys(completionErrors).forEach((field) => {
    delete completionErrors[field]
  })
}

function resetCompletionState() {
  completionDescription.value = ''
  completionAttachments.value = []
  completionUploading.value = false
  completionSubmitting.value = false
  resolutionAction.value = ''
  resolutionReason.value = ''
  resolutionReasonError.value = ''
  clearCompletionErrors()
}

function removeCompletionAttachment(index) {
  completionAttachments.value = completionAttachments.value.filter((_, itemIndex) => itemIndex !== index)
  clearCompletionError('attachments')
}

function readFileAsDataUri(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('图片读取失败，请重新选择。'))
    reader.readAsDataURL(file)
  })
}

async function handleCompletionFiles(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  clearCompletionError('attachments')
  const remaining = 9 - completionAttachments.value.length
  const selectedFiles = files.slice(0, Math.max(remaining, 0))

  if (files.length > remaining) {
    setCompletionError('attachments', '完成凭证最多提交 9 个附件，超出的图片没有加入。')
  }

  const nextAttachments = [...completionAttachments.value]
  completionUploading.value = true
  try {
    for (const file of selectedFiles) {
      if (file.size > 4 * 1024 * 1024) {
        setCompletionError('attachments', '单张图片请控制在 4MB 以内，避免提交失败。')
        continue
      }

      try {
        const previewUrl = file.type.startsWith('image/') ? await readFileAsDataUri(file) : ''
        const uploaded = await api.uploadAttachment({
          file,
          expectedOwnerType: 'COMPLETION_PROOF',
          type: inferAttachmentType(file)
        })
        const normalized = normalizeAttachmentItem(
          {
            ...uploaded,
            previewUrl,
            name: uploaded?.fileName || file.name
          },
          nextAttachments.length
        )
        if (normalized) nextAttachments.push(normalized)
      } catch (error) {
        setCompletionError('attachments', error.message || `${file.name || '附件'} 上传失败，请稍后重试。`)
      }
    }

    completionAttachments.value = nextAttachments
  } finally {
    completionUploading.value = false
    event.target.value = ''
  }
}

function validateCompletionForm() {
  clearCompletionErrors()
  const description = completionDescription.value.trim()

  if (!description) {
    setCompletionError('description', '请填写完成说明，方便求助者核验。')
  }

  if (description.length > 1000) {
    setCompletionError('description', '完成说明最多 1000 个字。')
  }

  if (completionAttachments.value.length > 9) {
    setCompletionError('attachments', '完成凭证最多提交 9 个附件。')
  }

  if (completionAttachments.value.length && completionAttachmentIds().length !== completionAttachments.value.length) {
    setCompletionError('attachments', '只有上传成功并生成附件 ID 的凭证才能提交。')
  }

  return !Object.keys(completionErrors).length
}

async function startDetailTask() {
  if (!detailTask.value?.id || !canStartDetailTask.value) return

  operationLoading.value = 'start'
  try {
    await api.transitionTaskStatus(detailTask.value.id, {
      action: 'START',
      reason: '帮助者开始执行任务'
    })
    showToast('任务已进入进行中，完成后可以提交说明和凭证。', { type: 'success' })
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '开始任务失败，请稍后重试。', { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

async function submitCompletionProof() {
  if (!detailTask.value?.id || !canSubmitCompletion.value) return
  if (!validateCompletionForm()) return

  completionSubmitting.value = true
  try {
    const attachmentIds = completionAttachmentIds()
    await api.submitTaskCompletion(detailTask.value.id, {
      description: completionDescription.value.trim(),
      attachmentIds
    })
    showToast('完成凭证已提交，任务进入待确认状态。', { type: 'success' })
    resetCompletionState()
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '提交完成凭证失败，请稍后重试。', { type: 'danger' })
  } finally {
    completionSubmitting.value = false
  }
}

function openCompletionResolution(action) {
  if (action === 'DISPUTE' ? !canDisputeCompletion.value : !canReviewCompletion.value) return
  resolutionAction.value = action
  resolutionReason.value = ''
  resolutionReasonError.value = ''
}

function closeCompletionResolution() {
  resolutionAction.value = ''
  resolutionReason.value = ''
  resolutionReasonError.value = ''
}

async function submitCompletionResolution() {
  const canSubmitResolution =
    resolutionAction.value === 'DISPUTE' ? canDisputeCompletion.value : canReviewCompletion.value
  if (!detailTask.value?.id || !canSubmitResolution || !resolutionAction.value) return

  const reason = resolutionReason.value.trim()
  if (resolutionAction.value !== 'CONFIRM_COMPLETION' && !reason) {
    resolutionReasonError.value = '驳回或发起争议时必须填写原因。'
    return
  }

  if (resolutionAction.value !== 'CONFIRM_COMPLETION' && reason.length < 4) {
    resolutionReasonError.value = '处理原因至少 4 个字。'
    return
  }

  if (reason.length > 300) {
    resolutionReasonError.value = '处理说明最多 300 个字。'
    return
  }

  resolutionReasonError.value = ''

  operationLoading.value = resolutionAction.value
  try {
    if (resolutionAction.value === 'CONFIRM_COMPLETION') {
      await api.confirmTaskCompletion(detailTask.value.id)
    } else if (resolutionAction.value === 'REJECT_COMPLETION') {
      await api.rejectTaskCompletion(detailTask.value.id, { reason })
    } else {
      await api.disputeTaskCompletion(detailTask.value.id, { reason })
    }
    showToast(resolutionAction.value === 'CONFIRM_COMPLETION'
        ? '已确认任务完成，完成记录会保留在任务历史中。'
        : resolutionAction.value === 'REJECT_COMPLETION'
          ? '已驳回完成结果，原因会写入任务记录。'
          : '已发起争议，平台可基于凭证和站内记录继续处理。', { type: 'success' })
    closeCompletionResolution()
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '完成确认操作失败，请稍后重试。', { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

function validateApplicationMessage() {
  const message = applicationMessage.value.trim()
  if (!message) {
    applicationMessageError.value = '请写一段接单说明，方便求助者判断你是否合适。'
    return false
  }
  if (message.length < 6) {
    applicationMessageError.value = '说明至少 6 个字，例如时间、路线或完成经验。'
    return false
  }
  if (message.length > 300) {
    applicationMessageError.value = '接单说明最多 300 个字。'
    return false
  }
  applicationMessageError.value = ''
  return true
}

async function submitTaskApplication() {
  if (!detailTask.value?.id || !canApplyDetailTask.value) return
  if (!validateApplicationMessage()) return

  applicationSubmitting.value = true
  try {
    const message = applicationMessage.value.trim()
    const result = await api.createTaskApplication(detailTask.value.id, { message })
    submittedApplication.value = {
      ...normalizeCandidate({
        applicantId: currentUserId.value,
        message,
        status: 'PENDING',
        createdAt: new Date().toISOString(),
        ...result
      }),
      taskId: detailTask.value.id
    }
    applicationMessage.value = ''
    showToast('接单申请已提交，求助者会在候选列表中看到你的说明。', { type: 'success' })
    closeApplicationPanel()
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '接单申请提交失败，请稍后重试。', { type: 'danger' })
  } finally {
    applicationSubmitting.value = false
  }
}

async function acceptCandidate(candidate) {
  if (!detailTask.value?.id || !candidate?.applicationId || candidate.status !== 'PENDING') return

  operationLoading.value = `accept-${candidate.applicationId}`
  try {
    await api.acceptTaskApplication(detailTask.value.id, candidate.applicationId)
    showToast(`已选择 ${candidate.name} 作为帮助者，其他待处理申请会自动婉拒。`, { type: 'success' })
    await refreshTasks()
  } catch (error) {
    showToast(error.message || '选择帮助者失败，请稍后重试。', { type: 'danger' })
  } finally {
    operationLoading.value = ''
  }
}

function buildDetailGuides(task) {
  if (!task) return []
  const guides = []

  if (task.onlyInAppContact) {
    guides.push({
      title: '先站内沟通',
      copy: '接单前把取件码、窗口要求等敏感信息留在站内确认。'
    })
  }

  if (task.requiresProof) {
    guides.push({
      title: '留好完成凭证',
      copy: '送达照片、提交截图或文字说明能减少后续确认争议。'
    })
  }

  if (deadlineHours(task.deadline) <= 6) {
    guides.push({
      title: '优先确认时间',
      copy: '这个任务接近截止，申请前先判断路线和排队成本。'
    })
  }

  if (task.allowOfflineContact) {
    guides.push({
      title: '线下交接有边界',
      copy: '当面交接时建议选择公开地点，并保留站内消息记录。'
    })
  }

  if (!guides.length) {
    guides.push({
      title: '先澄清再申请',
      copy: '不确定地点、奖励或完成标准时，先通过站内沟通补充细节。'
    })
  }

  return guides
}

const retainedTaskWorkflowBindings = [
  sortedCandidates,
  detailHistory,
  detailGuides,
  candidateStats,
  acceptedCandidate,
  requesterTaskCards,
  acceptedTaskCards,
  visibleTaskGroups,
  proofAttachmentItems,
  proofDescription,
  proofSubmittedAt,
  hasDetailActions,
  shouldShowCompletionPanel,
  canDisputeCompletion,
  canOpenConversation,
  detailConversationPeerUserId,
  completionStageItems,
  completionPanelCopy,
  resolutionMeta,
  applicationGate,
  applicationStatusLabel,
  applicationStatusTone,
  openApplicationPanel,
  closeApplicationPanel,
  openCandidatePanel,
  closeCandidatePanel,
  focusCompletionForm,
  submitQuickConfirmCompletion,
  openTaskConversation,
  openEditTask,
  openRepostTask,
  openCancelPanel,
  submitCancelTask,
  removeCompletionAttachment,
  handleCompletionFiles,
  startDetailTask,
  submitCompletionProof,
  openCompletionResolution,
  submitCompletionResolution,
  submitTaskApplication,
  acceptCandidate,
  openReportPanel,
  closeReportPanel,
  loadMyReports,
  submitReport
]
void retainedTaskWorkflowBindings

watch(
  () => [route.name, route.params.taskId],
  ([routeName, taskId], [previousRouteName] = []) => {
    const isTaskListRoute = ['task-list', 'my-tasks', 'workspace-tasks', 'workspace-drafts'].includes(routeName)
    if (isTaskListRoute && routeName !== previousRouteName) {
      Object.assign(filters, readFiltersFromRoute(routeName))
      loadTasks()
    }

    selectedTaskId.value = taskId ? String(taskId) : ''
    applicationMessage.value = ''
    applicationMessageError.value = ''
    candidateApplications.value = []
    candidatesLoaded.value = false
    candidatesError.value = ''
    cancelPanelOpen.value = false
    applicationPanelOpen.value = false
    candidatePanelOpen.value = false
    reportPanelOpen.value = false
    resetCompletionState()
    clearReportErrors()
    loadTaskDetail(selectedTaskId.value)
  }
)

onMounted(async () => {
  await Promise.allSettled([loadTasks(), loadMyReports()])
  if (selectedTaskId.value) await loadTaskDetail(selectedTaskId.value)
})
</script>

<template>
  <DashboardShell>
    <main
      class="tasks-page"
      :class="{ 'tasks-page--mine': isMineView }"
      :aria-busy="listLoading"
    >
      <ModuleTopbar
        eyebrow="任务"
        title="任务中心"
        description="浏览、管理与继续编辑任务。"
      >
        <template #extra>
          <button
            class="primary-action"
            type="button"
            @click="handlePublishShortcut"
          >
            <PlusCircle
              :size="18"
              aria-hidden="true"
            />
            发布任务
          </button>
        </template>
        <ModuleEntryCard
          v-for="item in taskNavItems"
          :key="item.key"
          :to="item.to"
          :icon="item.icon"
          :title="item.label"
          :description="item.description"
          :active="route.meta.subSection === item.key"
        />
      </ModuleTopbar>

      <section
        class="tasks-layout tasks-layout--full"
      >
        <section class="tasks-main-column">
          <form
            class="panel-card filter-panel"
            :class="{ 'filter-panel--mine': isMineView }"
            novalidate
            @submit.prevent="applyFilters"
          >
            <button
              type="button"
              class="filter-heading filter-toggle"
              :aria-expanded="filterExpanded"
              @click="filterExpanded = !filterExpanded"
            >
              <div>
                <p class="section-kicker">
                  筛选条件
                </p>
                <h2>把不合适的任务先筛掉</h2>
              </div>
              <span class="filter-toggle-right">
                <StatusBadge
                  :label="`${activeFilterCount} 个条件`"
                  :tone="activeFilterCount ? 'info' : 'muted'"
                />
                <ChevronDown
                  class="filter-chevron"
                  :class="{ 'filter-chevron--open': filterExpanded }"
                  :size="18"
                  aria-hidden="true"
                />
              </span>
            </button>

            <div v-show="filterExpanded">
              <label
                class="search-field"
                for="task-keyword"
              >
                <Search
                  :size="20"
                  aria-hidden="true"
                />
                <input
                  id="task-keyword"
                  v-model.trim="filters.keyword"
                  type="search"
                  placeholder="搜索任务标题、内容或关键词"
                  @input="queueFilterRefresh"
                >
                <button
                  v-if="!isMineView"
                  class="search-submit"
                  type="submit"
                >
                  搜索
                </button>
              </label>

              <div class="filter-grid">
                <label
                  class="filter-field"
                  for="task-campus"
                >
                  <span>校区</span>
                  <input
                    id="task-campus"
                    v-model.trim="filters.campus"
                    type="text"
                    placeholder="输入校区名称"
                    @input="queueFilterRefresh"
                  >
                </label>

                <label
                  class="filter-field"
                  for="task-category"
                >
                  <span>分类</span>
                  <span
                    class="select-shell"
                    :class="{ 'select-shell--plain': !isMineView }"
                  >
                    <Tag
                      v-if="isMineView"
                      :size="18"
                      aria-hidden="true"
                    />
                    <select
                      id="task-category"
                      v-model="filters.category"
                      @change="applyFilters"
                    >
                      <option value="">
                        全部分类
                      </option>
                      <option
                        v-for="category in TASK_CATEGORY_OPTIONS"
                        :key="category.value"
                        :value="category.value"
                      >
                        {{ category.label }}
                      </option>
                    </select>
                  </span>
                </label>

                <label
                  class="filter-field"
                  for="task-status"
                >
                  <span>状态</span>
                  <span
                    class="select-shell"
                    :class="{ 'select-shell--plain': !isMineView }"
                  >
                    <Clock3
                      v-if="isMineView"
                      :size="18"
                      aria-hidden="true"
                    />
                    <select
                      id="task-status"
                      v-model="filters.status"
                      @change="applyFilters"
                    >
                      <option value="">
                        全部状态
                      </option>
                      <option
                        v-for="status in TASK_STATUS_OPTIONS"
                        :key="status.value"
                        :value="status.value"
                      >
                        {{ status.label }}
                      </option>
                    </select>
                  </span>
                </label>

                <label
                  class="filter-field"
                  for="task-reward"
                >
                  <span>奖励方式</span>
                  <span
                    class="select-shell"
                    :class="{ 'select-shell--plain': !isMineView }"
                  >
                    <Gift
                      v-if="isMineView"
                      :size="18"
                      aria-hidden="true"
                    />
                    <select
                      id="task-reward"
                      v-model="filters.rewardType"
                      @change="applyFilters"
                    >
                      <option value="">
                        全部奖励
                      </option>
                      <option
                        v-for="reward in REWARD_TYPE_OPTIONS"
                        :key="reward.value"
                        :value="reward.value"
                      >
                        {{ reward.label }}
                      </option>
                    </select>
                  </span>
                </label>

                <label
                  class="filter-field"
                  for="task-proof"
                >
                  <span>完成凭证</span>
                  <span
                    class="select-shell"
                    :class="{ 'select-shell--plain': !isMineView }"
                  >
                    <ShieldCheck
                      v-if="isMineView"
                      :size="18"
                      aria-hidden="true"
                    />
                    <select
                      id="task-proof"
                      v-model="filters.requiresProof"
                      @change="applyFilters"
                    >
                      <option
                        v-for="proof in proofOptions"
                        :key="proof.value"
                        :value="proof.value"
                      >
                        {{ proof.label }}
                      </option>
                    </select>
                  </span>
                </label>

                <label
                  class="filter-field"
                  for="task-sort"
                >
                  <span>排序</span>
                  <span
                    class="select-shell"
                    :class="{ 'select-shell--plain': !isMineView }"
                  >
                    <ArrowUpDown
                      v-if="isMineView"
                      :size="18"
                      aria-hidden="true"
                    />
                    <select
                      id="task-sort"
                      v-model="filters.sortBy"
                      @change="applyFilters"
                    >
                      <option
                        v-for="sort in TASK_SORT_OPTIONS"
                        :key="sort.value"
                        :value="sort.value"
                      >
                        {{ sort.label }}
                      </option>
                    </select>
                  </span>
                </label>
              </div>

              <div class="filter-actions">
                <button
                  class="ghost-action"
                  type="button"
                  @click="resetFilters"
                >
                  <Trash2
                    v-if="isMineView"
                    :size="17"
                    aria-hidden="true"
                  />
                  <X
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  清空条件
                </button>
                <button
                  class="primary-action"
                  type="submit"
                  :disabled="listLoading"
                >
                  <LoaderCircle
                    v-if="listLoading"
                    class="spinning"
                    :size="18"
                    aria-hidden="true"
                  />
                  <Filter
                    v-else
                    :size="18"
                    aria-hidden="true"
                  />
                  {{ isMineView ? '更新列表' : '更新列表' }}
                </button>
              </div>
            </div>
          </form>

          <section
            class="metric-strip"
            :class="{ 'metric-strip--mine': isMineView }"
          >
            <article
              v-for="metric in isMineView && !isDraftView ? mineMetricTiles : []"
              :key="metric.key"
              class="metric-tile metric-tile--mine"
              :class="`metric-tile--${metric.tone}`"
            >
              <span class="metric-icon">
                <component
                  :is="metric.icon"
                  :size="26"
                  aria-hidden="true"
                />
              </span>
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </article>
            <article
              v-if="!isMineView"
              class="metric-tile"
            >
              <span>本页可接</span>
              <strong>{{ openTaskCount }}</strong>
            </article>
            <article
              v-if="!isMineView"
              class="metric-tile metric-tile--warning"
            >
              <span>6 小时内截止</span>
              <strong>{{ urgentTaskCount }}</strong>
            </article>
            <article
              v-if="!isMineView"
              class="metric-tile metric-tile--accent"
            >
              <span>需要凭证</span>
              <strong>{{ proofTaskCount }}</strong>
            </article>
          </section>

          <div
            v-if="showMineSubtabs"
            class="mine-subtabs"
          >
            <button
              :class="{ active: mineSubTab === 'published' }"
              @click="switchMineTab('published')"
            >
              我发布的
            </button>
            <button
              :class="{ active: mineSubTab === 'accepted' }"
              @click="switchMineTab('accepted')"
            >
              我接取的
            </button>
          </div>

          <section
            class="panel-card list-panel"
            :class="{ 'list-panel--mine': isMineView }"
          >
            <div class="list-toolbar">
              <div>
                <p
                  v-if="!isMineView"
                  class="section-kicker"
                >
                  查询结果
                </p>
                <h2>{{ taskListTitle }}</h2>
                <span v-if="pagination.total">当前显示 {{ resultRange }}</span>
              </div>
              <div class="toolbar-actions">
                <label
                  class="size-field"
                  for="task-page-size"
                >
                  {{ isMineView ? '每页显示' : '每页' }}
                  <select
                    id="task-page-size"
                    v-model.number="filters.size"
                    @change="applyFilters"
                  >
                    <option
                      v-for="size in pageSizeOptions"
                      :key="size"
                      :value="size"
                    >
                      {{ size }}
                    </option>
                  </select>
                </label>
                <button
                  v-if="!isMineView"
                  class="icon-action"
                  type="button"
                  aria-label="刷新任务列表"
                  :disabled="listLoading"
                  @click="refreshTasks"
                >
                  <RefreshCw
                    :class="{ spinning: listLoading }"
                    :size="18"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </div>

            <div
              v-if="listLoading"
              class="loading-state"
              role="status"
            >
              <LoaderCircle
                class="spinning"
                :size="24"
                aria-hidden="true"
              />
              <span>正在同步任务列表...</span>
            </div>

            <div
              v-else-if="!tasks.length"
              class="empty-state"
              :class="{ 'empty-state--mine': isMineView }"
            >
              <template v-if="isMineView">
                <div
                  class="empty-illustration"
                  aria-hidden="true"
                >
                  <span class="empty-illustration__clipboard">
                    <span />
                    <span />
                    <span />
                  </span>
                  <span class="empty-illustration__clip" />
                  <span class="empty-illustration__building empty-illustration__building--left" />
                  <span class="empty-illustration__building empty-illustration__building--right" />
                  <span class="empty-illustration__plane" />
                </div>
                <h3>{{ isDraftView ? '还没有任务草稿' : '还没有匹配的任务' }}</h3>
                <p>
                  {{
                    isDraftView
                      ? '保存草稿后会出现在这里，方便继续编辑。'
                      : '可以发布新任务，或调整筛选条件后重新查看。'
                  }}
                </p>
                <div class="empty-actions">
                  <RouterLink
                    class="primary-action"
                    to="/tasks/new"
                  >
                    <PlusCircle
                      :size="18"
                      aria-hidden="true"
                    />
                    {{ isDraftView ? '新建草稿' : '发布任务' }}
                  </RouterLink>
                  <button
                    class="ghost-action"
                    type="button"
                    @click="resetFilters"
                  >
                    清空筛选
                  </button>
                </div>
              </template>
              <template v-else>
                <Inbox
                  :size="36"
                  aria-hidden="true"
                />
                <h3>没有匹配的任务</h3>
                <p>可以放宽校区、状态或奖励条件，也可以改用更短的关键词重新搜索。</p>
                <button
                  class="ghost-action"
                  type="button"
                  @click="resetFilters"
                >
                  清空条件
                </button>
              </template>
            </div>

            <div
              v-else
              class="task-list-groups"
              :class="{ 'task-list-groups--single': !isMineView }"
            >
              <section
                v-for="group in visibleTaskGroups"
                :key="group.key"
                class="task-list-group"
              >
                <header
                  v-if="isMineView"
                  class="task-list-group__header"
                >
                  <div>
                    <p class="section-kicker">
                      {{ group.key === 'accepted' ? '已接任务' : (group.key === 'drafts' ? '任务草稿' : '发布任务') }}
                    </p>
                    <h3>{{ group.title }}</h3>
                  </div>
                  <StatusBadge
                    :label="`${group.items.length} 条`"
                    :tone="group.items.length ? 'info' : 'muted'"
                  />
                </header>

                <div
                  v-if="group.items.length"
                  class="task-list"
                >
                  <button
                    v-for="task in group.items"
                    :key="`${group.key}-${task.id}`"
                    class="task-card"
                    :class="{ selected: String(task.id) === selectedTaskId }"
                    type="button"
                    :aria-pressed="String(task.id) === selectedTaskId"
                    @click="selectTask(task)"
                  >
                    <span
                      class="deadline-chip"
                      :class="`deadline-chip--${deadlineTone(task.deadline)}`"
                    >
                      <Clock3
                        :size="14"
                        aria-hidden="true"
                      />
                      {{ deadlineCopy(task.deadline) }}
                    </span>

                    <span class="task-card-main">
                      <span class="task-card-heading">
                        <span class="task-card-titleline">
                          <span
                            class="task-type-icon"
                            :class="`task-type-icon--${taskTypeMeta(task).tone}`"
                          >
                            <component
                              :is="taskTypeMeta(task).icon"
                              :size="22"
                              aria-hidden="true"
                            />
                          </span>
                          <strong>{{ task.title || '未命名任务' }}</strong>
                        </span>
                        <StatusBadge
                          :label="taskStatusLabel(task.status)"
                          :tone="taskStatusTone(task.status)"
                        />
                      </span>

                      <span class="task-description">{{ shortDescription(task.description) }}</span>

                      <span class="task-meta-grid">
                        <span>
                          <MapPin
                            :size="15"
                            aria-hidden="true"
                          />
                          {{ task.campus || '未填校区' }} · {{ task.location || '未填地点' }}
                          <template v-if="task.category === 'ERRAND' && task.deliveryLocation">
                            → {{ task.deliveryLocation }}
                          </template>
                        </span>
                        <span>
                          <CalendarClock
                            :size="15"
                            aria-hidden="true"
                          />
                          {{ formatDateTime(task.deadline) }}
                        </span>
                        <span>
                          <BadgeCheck
                            :size="15"
                            aria-hidden="true"
                          />
                          {{ taskRewardValue(task) }}
                        </span>
                        <span>
                          <FileCheck2
                            :size="15"
                            aria-hidden="true"
                          />
                          {{ task.requiresProof ? '需要完成凭证' : '不要求凭证' }}
                        </span>
                      </span>
                    </span>

                    <span class="task-card-footer">
                      <span>{{ taskCategoryLabel(task) }}</span>
                      <span>发布于 {{ formatCreatedAt(task.createdAt) }}</span>
                      <button
                        v-if="isMineView && task.status === 'DRAFT'"
                        class="card-edit-btn"
                        type="button"
                        aria-label="编辑草稿"
                        @click.stop="openDraftEditor(task)"
                      >
                        <Pencil
                          :size="15"
                          aria-hidden="true"
                        />
                        编辑
                      </button>
                      <button
                        v-if="isMineView && task.status === 'DRAFT'"
                        class="card-delete-btn"
                        type="button"
                        aria-label="删除草稿"
                        @click.stop="quickDeleteDraft(task)"
                      >
                        <Trash2
                          :size="15"
                          aria-hidden="true"
                        />
                        删除
                      </button>
                    </span>
                  </button>
                </div>

                <div
                  v-else-if="isMineView"
                  class="task-list-group__empty"
                >
                  {{ group.emptyCopy }}
                </div>
              </section>
            </div>

            <nav
              class="pagination-bar"
              :class="{ 'pagination-bar--mine': isMineView }"
              aria-label="任务分页"
            >
              <button
                class="ghost-action page-button"
                type="button"
                :disabled="pagination.page <= 1 || listLoading"
                @click="changePage(pagination.page - 1)"
              >
                <ArrowLeft
                  :size="16"
                  aria-hidden="true"
                />
                上一页
              </button>
              <span class="page-current">{{ pagination.page }}</span>
              <button
                class="ghost-action page-button"
                type="button"
                :disabled="pagination.page >= totalPages || listLoading"
                @click="changePage(pagination.page + 1)"
              >
                下一页
                <ArrowRight
                  :size="16"
                  aria-hidden="true"
                />
              </button>
            </nav>
          </section>
        </section>
      </section>
    </main>

    <UserProfileModal
      :user-id="profileUserId"
      :visible="showProfileModal"
      related-type="TASK"
      :related-id="profileRelatedId"
      @close="showProfileModal = false"
    />
  </DashboardShell>

  <Teleport to="body">
    <div
      v-if="selectedTaskId"
      class="task-detail-modal-layer"
      @click.self="clearSelectedTask"
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
            @click="clearSelectedTask"
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
            v-if="canOpenConversation"
            class="detail-action-row"
          >
            <button
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
          </footer>
        </template>
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
  min-height: 44px;
  border: 1px solid var(--color-brand-text);
  background: var(--color-brand-text);
  color: var(--color-surface);
  box-shadow: var(--shadow-button);
}

.primary-action:hover:not(:disabled),
.search-submit:hover:not(:disabled) {
  background: var(--color-brand-text);
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
  border-color: var(--color-warning-soft);
  background: var(--color-warning-bg);
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
