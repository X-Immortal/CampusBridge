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
  Building2,
  CalendarClock,
  CheckCircle2,
  ClipboardCheck,
  Clock3,
  FileCheck2,
  Filter,
  Gift,
  Inbox,
  Info,
  LoaderCircle,
  MapPin,
  MessageCircle,
  Package,
  PlusCircle,
  RefreshCw,
  Search,
  ShieldCheck,
  ShieldAlert,
  Tag,
  Trash2,
  Utensils,
  X
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import {
  APPLICATION_STATUS_OPTIONS,
  optionLabel,
  REWARD_TYPE_OPTIONS,
  TASK_CATEGORY_OPTIONS,
  TASK_SORT_OPTIONS,
  TASK_STATUS_OPTIONS,
  statusTone
} from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, currentUserId, verificationStatus } = useAuth()
const isMineView = computed(() => route.name === 'my-tasks')

const defaultFilters = {
  keyword: '',
  category: '',
  campus: '',
  status: route.name === 'my-tasks' ? '' : 'OPEN',
  rewardType: '',
  requiresProof: '',
  sortBy: 'CREATED_AT',
  page: 1,
  size: 8
}

const proofOptions = [
  { value: '', label: '不限凭证' },
  { value: 'true', label: '需要凭证' },
  { value: 'false', label: '无需凭证' }
]

const pageSizeOptions = [8, 12, 20]

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
  },
  ATTENDANCE_ASSIST: {
    label: '出勤协助',
    icon: ClipboardCheck,
    tone: 'amber'
  }
}

const DEMO_TASKS = [
  {
    id: 2024051701,
    requesterId: 10023,
    providerId: null,
    category: 'ERRAND',
    typeKey: 'ERRAND',
    typeLabel: '生活服务',
    title: '代取快递（菜鸟驿站）',
    description: '帮忙在菜鸟驿站代取一个快递，取件码已私聊发你，取完拍照回传，谢谢！',
    campus: '仙林校区',
    location: '菜鸟驿站（文苑路 8 号）',
    deadline: demoDate(2),
    rewardType: 'CASH',
    rewardAmount: 8,
    rewardDescription: '8 元现金',
    completionStandard: '成功取件并拍摄快递单面照片清晰可见。',
    requiresProof: true,
    onlyInAppContact: true,
    allowOfflineContact: false,
    isInstantTask: true,
    routeHint: '靠近西门，顺路可接。',
    riskDisclosureConfirmed: true,
    sensitiveReason: '',
    status: 'OPEN',
    createdAt: demoDate(-0.17),
    updatedAt: demoDate(-0.17)
  },
  {
    id: 2024051702,
    requesterId: 10056,
    providerId: null,
    category: 'AGENCY',
    typeKey: 'DINING',
    typeLabel: '生活服务',
    title: '食堂带饭（桃园食堂）',
    description: '帮忙在桃园食堂买一份酸辣土豆丝和米饭，送到南苑 12 栋，谢谢！',
    campus: '仙林校区',
    location: '桃园食堂',
    deadline: demoDate(3.5),
    rewardType: 'CASH',
    rewardAmount: 15,
    rewardDescription: '15 元现金',
    completionStandard: '按备注买到指定餐品并送达宿舍楼下，餐品包装完整。',
    requiresProof: false,
    onlyInAppContact: true,
    allowOfflineContact: true,
    isInstantTask: true,
    routeHint: '南苑方向优先。',
    riskDisclosureConfirmed: true,
    sensitiveReason: '',
    status: 'OPEN',
    createdAt: demoDate(-0.42),
    updatedAt: demoDate(-0.42)
  },
  {
    id: 2024051703,
    requesterId: 10078,
    providerId: null,
    category: 'AGENCY',
    typeKey: 'STUDY',
    typeLabel: '学习互助',
    title: '图书馆占座（南京大学图书馆）',
    description: '帮忙占一个南京大学图书馆自习座位，靠窗优先，占好后拍照确认。',
    campus: '鼓楼校区',
    location: '南京大学图书馆',
    deadline: demoDate(18),
    rewardType: 'CASH',
    rewardAmount: 20,
    rewardDescription: '20 元现金',
    completionStandard: '座位确认可用，拍摄座位号或附近标识后回传。',
    requiresProof: true,
    onlyInAppContact: true,
    allowOfflineContact: false,
    isInstantTask: false,
    routeHint: '',
    riskDisclosureConfirmed: true,
    sensitiveReason: '',
    status: 'OPEN',
    createdAt: demoDate(-1),
    updatedAt: demoDate(-1)
  },
  {
    id: 2024051704,
    requesterId: 10102,
    providerId: null,
    category: 'AGENCY',
    typeKey: 'AGENCY',
    typeLabel: '委托代办',
    title: '帮忙提交学院材料',
    description: '把打印好的申请材料交到学院办公室，窗口老师收下后帮忙拍一张回执照片即可。',
    campus: '仙林校区',
    location: '计算机学院办公室',
    deadline: demoDate(26),
    rewardType: 'FREE',
    rewardAmount: '',
    rewardDescription: '无偿互助',
    completionStandard: '材料成功交到办公室，并拍摄窗口或回执作为确认。',
    requiresProof: true,
    onlyInAppContact: true,
    allowOfflineContact: false,
    isInstantTask: false,
    routeHint: '教学楼附近顺路优先。',
    riskDisclosureConfirmed: true,
    sensitiveReason: '',
    status: 'OPEN',
    createdAt: demoDate(-2.5),
    updatedAt: demoDate(-2.5)
  },
  {
    id: 2024051705,
    requesterId: 10118,
    providerId: null,
    category: 'AGENCY',
    typeKey: 'STUDY',
    typeLabel: '学习互助',
    title: '高数错题讲解 30 分钟',
    description: '想找同学讲一下极限和导数相关错题，线上或图书馆都可以，重点是把思路讲清楚。',
    campus: '鼓楼校区',
    location: '逸夫馆自习区',
    deadline: demoDate(30),
    rewardType: 'POINTS',
    rewardAmount: 60,
    rewardDescription: '60 积分',
    completionStandard: '讲完 4 道错题并整理关键步骤，双方确认理解。',
    requiresProof: false,
    onlyInAppContact: true,
    allowOfflineContact: true,
    isInstantTask: false,
    routeHint: '',
    riskDisclosureConfirmed: true,
    sensitiveReason: '',
    status: 'OPEN',
    createdAt: demoDate(-4),
    updatedAt: demoDate(-4)
  },
  {
    id: 2024051706,
    requesterId: 10136,
    providerId: null,
    category: 'ERRAND',
    typeKey: 'ERRAND',
    typeLabel: '跑腿代取',
    title: '活动展板搬运协助',
    description: '需要把两块轻量展板从活动中心搬到教学楼一楼，距离不远，时间可商量。',
    campus: '仙林校区',
    location: '大学生活动中心',
    deadline: demoDate(8),
    rewardType: 'NEGOTIABLE',
    rewardAmount: '',
    rewardDescription: '双方协商',
    completionStandard: '展板安全搬到指定教室门口，避免弯折和磕碰。',
    requiresProof: false,
    onlyInAppContact: true,
    allowOfflineContact: true,
    isInstantTask: true,
    routeHint: '两人一起更快，可沟通具体时间。',
    riskDisclosureConfirmed: true,
    sensitiveReason: '',
    status: 'OPEN',
    createdAt: demoDate(-0.75),
    updatedAt: demoDate(-0.75)
  }
]

const filters = reactive({
  keyword: readQuery('keyword', defaultFilters.keyword),
  category: readQuery('category', defaultFilters.category),
  campus: readQuery('campus', defaultFilters.campus),
  status: readQuery('status', defaultFilters.status),
  rewardType: readQuery('rewardType', defaultFilters.rewardType),
  requiresProof: readQuery('requiresProof', defaultFilters.requiresProof),
  sortBy: readQuery('sortBy', defaultFilters.sortBy),
  page: readNumberQuery('page', defaultFilters.page),
  size: readNumberQuery('size', defaultFilters.size)
})

const tasks = ref([])
const taskDetail = ref(null)
const selectedTaskId = ref(route.params.taskId ? String(route.params.taskId) : '')
const listLoading = ref(false)
const detailLoading = ref(false)
const listError = ref('')
const detailError = ref('')
const operationFeedback = ref('')
const operationFeedbackType = ref('success')
const operationLoading = ref('')
const cancelPanelOpen = ref(false)
const cancelReason = ref('')
const cancelReasonError = ref('')
const candidateApplications = ref([])
const candidatesLoading = ref(false)
const candidatesError = ref('')
const applicationMessage = ref('')
const applicationMessageError = ref('')
const applicationSubmitting = ref(false)
const completionDescription = ref('')
const completionAttachmentInput = ref('')
const completionAttachments = ref([])
const completionErrors = reactive({})
const completionSubmitting = ref(false)
const resolutionAction = ref('')
const resolutionReason = ref('')
const resolutionReasonError = ref('')
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
  candidateApplications.value.length ? candidateApplications.value : detailApplications.value
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
  return [
    filters.keyword.trim(),
    filters.category,
    filters.campus.trim(),
    filters.status !== defaultFilters.status ? filters.status : '',
    filters.rewardType,
    filters.requiresProof,
    filters.sortBy !== defaultFilters.sortBy ? filters.sortBy : ''
  ].filter(Boolean).length
})
const openTaskCount = computed(() => tasks.value.filter((task) => task.status === 'OPEN').length)
const proofTaskCount = computed(() => tasks.value.filter((task) => task.requiresProof).length)
const urgentTaskCount = computed(() => tasks.value.filter((task) => deadlineHours(task.deadline) <= 6).length)
const verifiedOnlyTaskCount = computed(() =>
  tasks.value.filter((task) =>
    Boolean(task.requiresVerification || task.requiresRealName || task.requiresVerifiedApplicant)
  ).length
)
const completedTaskCount = computed(() =>
  tasks.value.filter((task) => ['COMPLETED', 'REVIEWED'].includes(task.status)).length
)
const mineMetricTiles = computed(() => [
  {
    key: 'open',
    label: '本页可接',
    value: openTaskCount.value,
    icon: ClipboardCheck,
    tone: 'purple'
  },
  {
    key: 'urgent',
    label: '6 小时内截止',
    value: urgentTaskCount.value,
    icon: Clock3,
    tone: 'orange'
  },
  {
    key: 'verified',
    label: '需实名认证',
    value: verifiedOnlyTaskCount.value,
    icon: ShieldCheck,
    tone: 'teal'
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
const completionProof = computed(() => taskDetail.value?.completionProof || detailTask.value?.completionProof || null)
const proofAttachmentItems = computed(() => normalizeProofAttachments(completionProof.value?.attachmentUrls))
const pageTitle = computed(() =>
  isMineView.value ? '管理自己发布的任务' : '找到值得接、能准时完成的校园互助'
)
const pageSubtitle = computed(() =>
  isMineView.value
    ? '集中查看任务状态、报名情况、截止时间与完成进度。'
    : '按校区、奖励、凭证和任务状态快速筛选，再查看完整细节后申请。'
)
const listRouteName = computed(() => (isMineView.value ? 'my-tasks' : 'task-list'))
const editableStatuses = new Set(['DRAFT', 'OPEN'])
const cancelableStatuses = new Set(['OPEN', 'ACCEPTED', 'IN_PROGRESS'])
const repostableStatuses = new Set(['CANCELLED', 'REJECTED'])
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
  return repostableStatuses.has(detailTask.value.status) || deadlineHours(detailTask.value.deadline) <= 0
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
  () => isDetailOwner.value && detailTask.value?.status === 'PENDING_CONFIRMATION' && Boolean(completionProof.value)
)
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
const detailConversationRoute = computed(() => ({
  name: 'conversation-list',
  query: {
    relatedType: 'TASK',
    relatedId: detailTask.value?.id
  }
}))
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
const hasCurrentUserApplied = computed(() =>
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
      copy: '接单申请会绑定当前账号，用于后续站内沟通、完成凭证和信用记录追溯。',
      action: '去登录',
      to: '/auth'
    }
  }
  if (verificationStatus.value !== 'VERIFIED') {
    return {
      tone: 'warning',
      title: '完成校内认证后再接单',
      copy: '认证状态会帮助求助者判断可信度，也能减少线下交接和失联风险。',
      action: '去认证中心',
      to: '/home'
    }
  }
  if (detailTask.value.status !== 'OPEN') {
    return {
      tone: 'muted',
      title: '当前任务暂不开放申请',
      copy: '任务进入审核、进行中、已完成或已取消后，不能再提交新的接单申请。'
    }
  }
  if (hasCurrentUserApplied.value) {
    return {
      tone: 'success',
      title: '你已提交接单申请',
      copy: '求助者会在候选列表中看到你的说明，后续请留意站内消息和任务状态变化。'
    }
  }
  return null
})

function demoDate(hourOffset) {
  const date = new Date()
  date.setMinutes(0, 0, 0)
  date.setTime(date.getTime() + hourOffset * 60 * 60 * 1000)
  return date.toISOString()
}

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
    status: application.status || 'PENDING',
    createdAt: application.createdAt || application.appliedAt || ''
  }
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

function isDemoTaskId(taskId) {
  return DEMO_TASKS.some((task) => String(task.id) === String(taskId))
}

function demoTaskDetail(taskId) {
  const task = DEMO_TASKS.find((item) => String(item.id) === String(taskId))
  return task ? { task, applications: [], history: [], reviews: [] } : null
}

function buildDemoTaskResult() {
  const keyword = filters.keyword.trim().toLowerCase()
  const proofFilter = filters.requiresProof === '' ? null : filters.requiresProof === 'true'
  let items = DEMO_TASKS.filter((task) => {
    const searchableText = [
      task.title,
      task.description,
      task.campus,
      task.location,
      taskCategoryLabel(task)
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()

    return (
      (!keyword || searchableText.includes(keyword)) &&
      (!filters.category || task.category === filters.category) &&
      (!filters.campus.trim() || task.campus?.includes(filters.campus.trim())) &&
      (!filters.status || task.status === filters.status) &&
      (!filters.rewardType || task.rewardType === filters.rewardType) &&
      (proofFilter === null || Boolean(task.requiresProof) === proofFilter)
    )
  })

  items = [...items].sort((current, next) => {
    if (filters.sortBy === 'DEADLINE') {
      return new Date(current.deadline).getTime() - new Date(next.deadline).getTime()
    }
    if (filters.sortBy === 'CREDIT') {
      return Number(next.requesterId || 0) - Number(current.requesterId || 0)
    }
    return new Date(next.createdAt).getTime() - new Date(current.createdAt).getTime()
  })

  const total = items.length
  const page = Math.max(1, filters.page)
  const size = Math.max(1, filters.size || defaultFilters.size)
  const start = (page - 1) * size

  return {
    items: items.slice(start, start + size),
    page,
    size,
    total
  }
}

function applyTaskResult(result) {
  const items = Array.isArray(result) ? result : result?.items || []
  tasks.value = items
  pagination.page = Number(result?.page || filters.page || 1)
  pagination.size = Number(result?.size || filters.size || defaultFilters.size)
  pagination.total = Number(result?.total ?? items.length)
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

function normalizeProofAttachments(value = []) {
  if (!Array.isArray(value)) return []
  return value
    .filter(Boolean)
    .map((url, index) => ({
      url: String(url),
      name: attachmentNameFromUri(String(url), index)
    }))
}

function isSupportedAttachmentUri(value) {
  try {
    const url = new URL(value)
    return ['http:', 'https:', 'data:'].includes(url.protocol)
  } catch {
    return false
  }
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
  return {
    keyword: filters.keyword.trim(),
    category: filters.category,
    campus: filters.campus.trim(),
    status: filters.status,
    rewardType: filters.rewardType,
    requiresProof: filters.requiresProof === '' ? undefined : filters.requiresProof === 'true',
    sortBy: filters.sortBy,
    page: filters.page,
    size: filters.size
  }
}

function buildRouteQuery() {
  const query = {
    keyword: filters.keyword.trim() || undefined,
    category: filters.category || undefined,
    campus: filters.campus.trim() || undefined,
    status: filters.status !== defaultFilters.status ? filters.status || undefined : undefined,
    rewardType: filters.rewardType || undefined,
    requiresProof: filters.requiresProof || undefined,
    sortBy: filters.sortBy !== defaultFilters.sortBy ? filters.sortBy || undefined : undefined,
    page: filters.page !== defaultFilters.page ? String(filters.page) : undefined,
    size: filters.size !== defaultFilters.size ? String(filters.size) : undefined
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
      name: 'task-detail',
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

    const items = Array.isArray(result) ? result : result?.items || []
    applyTaskResult(!items.length && !isMineView.value ? buildDemoTaskResult() : result)
  } catch (error) {
    if (requestId !== listRequestId) return
    if (!isMineView.value) {
      applyTaskResult(buildDemoTaskResult())
      return
    }
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
    candidateApplications.value = []
    candidatesError.value = ''
    return
  }

  detailLoading.value = true
  detailError.value = ''
  const requestId = ++detailRequestId

  if (!isMineView.value && isDemoTaskId(taskId)) {
    taskDetail.value = demoTaskDetail(taskId)
    candidateApplications.value = []
    candidatesError.value = ''
    candidatesLoading.value = false
    detailLoading.value = false
    return
  }

  try {
    const result = await api.getTaskDetail(taskId)
    if (requestId !== detailRequestId) return
    taskDetail.value = result
    await loadCandidates(taskId)
  } catch (error) {
    if (requestId !== detailRequestId) return
    taskDetail.value = null
    candidateApplications.value = []
    detailError.value = error.message || '任务详情加载失败，请稍后重试。'
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

async function loadCandidates(taskId) {
  if (!taskId || !isAuthenticated.value || !isDetailOwner.value) {
    candidateApplications.value = []
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
    candidateApplications.value = Array.isArray(result) ? result : result?.items || result?.applications || []
  } catch (error) {
    if (requestId !== candidatesRequestId) return
    candidateApplications.value = []
    candidatesError.value = error.message || '候选申请加载失败，请稍后重试。'
  } finally {
    if (requestId === candidatesRequestId) candidatesLoading.value = false
  }
}

async function applyFilters() {
  window.clearTimeout(filterTimer)
  filters.page = 1
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
  Object.assign(filters, { ...defaultFilters })
  selectedTaskId.value = ''
  taskDetail.value = null
  candidateApplications.value = []
  candidatesError.value = ''
  applicationMessage.value = ''
  applicationMessageError.value = ''
  await syncRoute()
  await loadTasks()
}

async function refreshTasks() {
  await syncRoute()
  await loadTasks()
  if (selectedTaskId.value) await loadTaskDetail(selectedTaskId.value)
}

function setOperationFeedback(message, type = 'success') {
  operationFeedback.value = message
  operationFeedbackType.value = type
}

async function changePage(nextPage) {
  const normalizedPage = Math.min(Math.max(nextPage, 1), totalPages.value)
  if (normalizedPage === filters.page) return
  filters.page = normalizedPage
  await syncRoute()
  await loadTasks()
}

async function selectTask(task) {
  if (!task?.id) return
  const nextTaskId = String(task.id)
  selectedTaskId.value = nextTaskId

  await router.push({
    name: 'task-detail',
    params: { taskId: nextTaskId },
    query: buildRouteQuery()
  })
}

async function clearSelectedTask() {
  selectedTaskId.value = ''
  taskDetail.value = null
  detailError.value = ''
  candidateApplications.value = []
  candidatesError.value = ''
  operationFeedback.value = ''
  applicationMessage.value = ''
  applicationMessageError.value = ''
  cancelPanelOpen.value = false
  resetCompletionState()
  await router.push({ name: listRouteName.value, query: buildRouteQuery() })
}

async function openEditTask() {
  if (!detailTask.value?.id || !canEditDetailTask.value) return
  await router.push({ name: 'task-edit', params: { taskId: detailTask.value.id } })
}

async function openRepostTask() {
  if (!detailTask.value?.id || !canRepostDetailTask.value) return
  await router.push({ name: 'task-repost', params: { taskId: detailTask.value.id } })
}

function openCancelPanel() {
  if (!canCancelDetailTask.value) return
  cancelPanelOpen.value = true
  cancelReason.value = ''
  cancelReasonError.value = ''
  operationFeedback.value = ''
}

function closeCancelPanel() {
  cancelPanelOpen.value = false
  cancelReason.value = ''
  cancelReasonError.value = ''
}

async function submitCancelTask() {
  if (!detailTask.value?.id || !canCancelDetailTask.value) return

  const reason = cancelReason.value.trim()
  if (reason.length < 4) {
    cancelReasonError.value = '请用至少 4 个字说明取消原因，方便后续追溯。'
    return
  }

  operationLoading.value = 'cancel'
  cancelReasonError.value = ''
  try {
    await api.transitionTaskStatus(detailTask.value.id, {
      action: 'CANCEL',
      reason
    })
    setOperationFeedback('任务已取消，状态记录会保留在任务历史中。')
    closeCancelPanel()
    await refreshTasks()
  } catch (error) {
    setOperationFeedback(error.message || '取消任务失败，请稍后重试。', 'danger')
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
  completionAttachmentInput.value = ''
  completionAttachments.value = []
  completionSubmitting.value = false
  resolutionAction.value = ''
  resolutionReason.value = ''
  resolutionReasonError.value = ''
  clearCompletionErrors()
}

function addCompletionAttachmentUrl() {
  const values = completionAttachmentInput.value
    .split(/[\s,，]+/)
    .map((item) => item.trim())
    .filter(Boolean)

  if (!values.length) {
    setCompletionError('attachmentUrl', '请粘贴图片或材料链接后再添加。')
    return
  }

  const nextAttachments = [...completionAttachments.value]
  for (const value of values) {
    if (nextAttachments.length >= 9) {
      setCompletionError('attachmentUrl', '完成凭证最多提交 9 个附件。')
      break
    }

    if (!isSupportedAttachmentUri(value)) {
      setCompletionError('attachmentUrl', '附件链接需为 http、https 或本地图片数据链接。')
      return
    }

    if (nextAttachments.some((item) => item.url === value)) continue
    nextAttachments.push({
      url: value,
      name: attachmentNameFromUri(value, nextAttachments.length)
    })
  }

  completionAttachments.value = nextAttachments
  completionAttachmentInput.value = ''
  clearCompletionError('attachmentUrl')
  clearCompletionError('attachments')
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
  for (const file of selectedFiles) {
    if (!file.type.startsWith('image/')) {
      setCompletionError('attachments', '目前只支持选择图片作为本地凭证。')
      continue
    }

    if (file.size > 4 * 1024 * 1024) {
      setCompletionError('attachments', '单张图片请控制在 4MB 以内，避免提交失败。')
      continue
    }

    try {
      const url = await readFileAsDataUri(file)
      nextAttachments.push({
        url,
        name: file.name || attachmentNameFromUri(url, nextAttachments.length)
      })
    } catch (error) {
      setCompletionError('attachments', error.message)
    }
  }

  completionAttachments.value = nextAttachments
  event.target.value = ''
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

  return !Object.keys(completionErrors).length
}

async function startDetailTask() {
  if (!detailTask.value?.id || !canStartDetailTask.value) return

  operationLoading.value = 'start'
  operationFeedback.value = ''
  try {
    await api.transitionTaskStatus(detailTask.value.id, {
      action: 'START',
      reason: '帮助者开始执行任务'
    })
    setOperationFeedback('任务已进入进行中，完成后可以提交说明和凭证。')
    await refreshTasks()
  } catch (error) {
    setOperationFeedback(error.message || '开始任务失败，请稍后重试。', 'danger')
  } finally {
    operationLoading.value = ''
  }
}

async function submitCompletionProof() {
  if (!detailTask.value?.id || !canSubmitCompletion.value) return
  if (!validateCompletionForm()) return

  completionSubmitting.value = true
  operationFeedback.value = ''
  try {
    await api.submitTaskCompletion(detailTask.value.id, {
      description: completionDescription.value.trim(),
      attachmentUrls: completionAttachments.value.map((item) => item.url)
    })
    setOperationFeedback('完成凭证已提交，任务进入待确认状态。')
    resetCompletionState()
    await refreshTasks()
  } catch (error) {
    setOperationFeedback(error.message || '提交完成凭证失败，请稍后重试。', 'danger')
  } finally {
    completionSubmitting.value = false
  }
}

function openCompletionResolution(action) {
  if (!canReviewCompletion.value) return
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
  if (!detailTask.value?.id || !canReviewCompletion.value || !resolutionAction.value) return

  const reason = resolutionReason.value.trim()
  if (resolutionAction.value !== 'CONFIRM_COMPLETION' && !reason) {
    resolutionReasonError.value = '驳回或发起争议时必须填写原因。'
    return
  }

  if (reason.length > 300) {
    resolutionReasonError.value = '处理说明最多 300 个字。'
    return
  }

  operationLoading.value = resolutionAction.value
  operationFeedback.value = ''
  try {
    const payload = { action: resolutionAction.value }
    if (reason) payload.reason = reason
    await api.transitionTaskStatus(detailTask.value.id, payload)
    setOperationFeedback(
      resolutionAction.value === 'CONFIRM_COMPLETION'
        ? '已确认任务完成，完成记录会保留在任务历史中。'
        : resolutionAction.value === 'REJECT_COMPLETION'
          ? '已驳回完成结果，原因会写入任务记录。'
          : '已发起争议，平台可基于凭证和站内记录继续处理。'
    )
    closeCompletionResolution()
    await refreshTasks()
  } catch (error) {
    setOperationFeedback(error.message || '完成确认操作失败，请稍后重试。', 'danger')
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
  operationFeedback.value = ''
  try {
    await api.createTaskApplication(detailTask.value.id, {
      message: applicationMessage.value.trim()
    })
    applicationMessage.value = ''
    setOperationFeedback('接单申请已提交，求助者会在候选列表中看到你的说明。')
    await refreshTasks()
  } catch (error) {
    setOperationFeedback(error.message || '接单申请提交失败，请稍后重试。', 'danger')
  } finally {
    applicationSubmitting.value = false
  }
}

async function acceptCandidate(candidate) {
  if (!detailTask.value?.id || !candidate?.applicationId || candidate.status !== 'PENDING') return

  operationLoading.value = `accept-${candidate.applicationId}`
  operationFeedback.value = ''
  try {
    await api.acceptTaskApplication(detailTask.value.id, candidate.applicationId)
    setOperationFeedback(`已选择 ${candidate.name} 作为帮助者，其他待处理申请会自动婉拒。`)
    await refreshTasks()
  } catch (error) {
    setOperationFeedback(error.message || '选择帮助者失败，请稍后重试。', 'danger')
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
  proofAttachmentItems,
  hasDetailActions,
  shouldShowCompletionPanel,
  canOpenConversation,
  completionStageItems,
  completionPanelCopy,
  resolutionMeta,
  applicationGate,
  applicationStatusLabel,
  applicationStatusTone,
  openEditTask,
  openRepostTask,
  openCancelPanel,
  submitCancelTask,
  addCompletionAttachmentUrl,
  removeCompletionAttachment,
  handleCompletionFiles,
  startDetailTask,
  submitCompletionProof,
  openCompletionResolution,
  submitCompletionResolution,
  submitTaskApplication,
  acceptCandidate
]
void retainedTaskWorkflowBindings

watch(
  () => route.params.taskId,
  (taskId) => {
    selectedTaskId.value = taskId ? String(taskId) : ''
    operationFeedback.value = ''
    applicationMessage.value = ''
    applicationMessageError.value = ''
    candidateApplications.value = []
    candidatesError.value = ''
    cancelPanelOpen.value = false
    resetCompletionState()
    loadTaskDetail(selectedTaskId.value)
  }
)

onMounted(async () => {
  await loadTasks()
  if (selectedTaskId.value) await loadTaskDetail(selectedTaskId.value)
})
</script>

<template>
  <DashboardShell :active-label="isMineView ? '我的任务' : '任务广场'">
    <main
      class="tasks-page"
      :class="{ 'tasks-page--mine': isMineView }"
      :aria-busy="listLoading"
    >
      <section class="page-title-card tasks-title-card">
        <div class="title-icon">
          <component
            :is="isMineView ? ClipboardCheck : Filter"
            :size="32"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>{{ isMineView ? '我的任务' : '任务广场' }}</p>
          <h1>{{ pageTitle }}</h1>
          <span>{{ pageSubtitle }}</span>
        </div>
        <RouterLink
          class="publish-shortcut"
          to="/tasks/new"
        >
          <PlusCircle
            :size="18"
            aria-hidden="true"
          />
          发布任务
        </RouterLink>
      </section>

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
            <div
              v-if="!isMineView"
              class="filter-heading"
            >
              <div>
                <p class="section-kicker">
                  筛选条件
                </p>
                <h2>把不合适的任务先筛掉</h2>
              </div>
              <StatusBadge
                :label="`${activeFilterCount} 个条件`"
                :tone="activeFilterCount ? 'info' : 'muted'"
              />
            </div>

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
                placeholder="搜索任务标题、地点或说明"
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
                <span
                  v-if="isMineView"
                  class="select-shell"
                >
                  <Building2
                    :size="18"
                    aria-hidden="true"
                  />
                  <input
                    id="task-campus"
                    v-model.trim="filters.campus"
                    placeholder="全部校区"
                    @input="queueFilterRefresh"
                  >
                </span>
                <input
                  v-else
                  id="task-campus"
                  v-model.trim="filters.campus"
                  placeholder="例如：仙林校区"
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
          </form>

          <section
            class="metric-strip"
            :class="{ 'metric-strip--mine': isMineView }"
          >
            <article
              v-for="metric in isMineView ? mineMetricTiles : []"
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

          <section
            v-if="listError"
            class="page-feedback page-feedback--danger"
            role="status"
          >
            <Info
              :size="18"
              aria-hidden="true"
            />
            <span>{{ listError }}</span>
          </section>

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
                <h2>{{ pagination.total }} 条任务 · 第 {{ pagination.page }} 页</h2>
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
                <h3>还没有匹配的任务</h3>
                <p>可以发布新任务，或调整筛选条件后重新查看。</p>
                <div class="empty-actions">
                  <RouterLink
                    class="primary-action"
                    to="/tasks/new"
                  >
                    <PlusCircle
                      :size="18"
                      aria-hidden="true"
                    />
                    发布任务
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
              class="task-list"
            >
              <button
                v-for="task in tasks"
                :key="task.id"
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
                </span>
              </button>
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
                  <MapPin
                    :size="16"
                    aria-hidden="true"
                  />
                  任务地点
                </dt>
                <dd>{{ detailTask.campus || '未填校区' }} · {{ detailTask.location || '未填地点' }}</dd>
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
              v-if="operationFeedback"
              class="operation-feedback"
              :class="operationFeedbackType === 'danger' ? 'operation-feedback--danger' : 'operation-feedback--success'"
              role="status"
            >
              <Info
                :size="18"
                aria-hidden="true"
              />
              <span>{{ operationFeedback }}</span>
            </section>
          </article>

          <footer class="detail-action-row">
            <button
              class="primary-action detail-primary-action"
              type="button"
              @click="setOperationFeedback('演示模式：后端完成后这里会提交接单申请。')"
            >
              <Award
                :size="17"
                aria-hidden="true"
              />
              立即接取
            </button>
            <RouterLink
              class="ghost-action detail-secondary-action"
              :to="detailConversationRoute"
            >
              <MessageCircle
                :size="17"
                aria-hidden="true"
              />
              联系发布者
            </RouterLink>
          </footer>
        </template>
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
  color: #111827;
}

.page-title-card,
.panel-card,
.page-feedback {
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
.section-kicker {
  margin: 0;
  color: #746dc3;
  font-size: 0.92rem;
  font-weight: 900;
}

.title-copy h1,
.filter-heading h2,
.list-toolbar h2,
.detail-topbar h2 {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.15;
}

.title-copy h1 {
  font-size: 1.9rem;
}

.title-copy span,
.list-toolbar span {
  color: #8a8792;
  line-height: 1.55;
}

.publish-shortcut,
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

.publish-shortcut,
.primary-action,
.search-submit {
  min-height: 44px;
  border: 1px solid #746dc3;
  background: #746dc3;
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(116, 109, 195, 0.16);
}

.publish-shortcut {
  padding: 0 16px;
  white-space: nowrap;
}

.publish-shortcut:hover,
.primary-action:hover:not(:disabled),
.search-submit:hover:not(:disabled) {
  background: #625ab4;
}

.ghost-action {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #746dc3;
}

.ghost-action:hover:not(:disabled) {
  border-color: #b9b2e8;
  background: #f7f4ff;
}

.danger-action {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.danger-action:hover:not(:disabled) {
  border-color: #fca5a5;
  background: #fee2e2;
  color: #991b1b;
}

.icon-action {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #746dc3;
}

.icon-action:hover:not(:disabled) {
  border-color: #b9b2e8;
  background: #f7f4ff;
}

.primary-action:disabled,
.ghost-action:disabled,
.danger-action:disabled,
.icon-action:disabled,
.search-submit:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.publish-shortcut:focus-visible,
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
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
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
  border-color: #e6e9f2;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(42, 38, 61, 0.055);
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

.search-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 0 12px 0 16px;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #9a96a3;
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
  color: #393545;
}

.search-field input::placeholder,
.filter-field input::placeholder {
  color: #b9b5c2;
}

.filter-panel--mine .search-field {
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 46px;
  padding: 0 16px;
  border-color: #dfe4ef;
  color: #667085;
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
  border-top: 1px solid #edf0f6;
}

.filter-field {
  display: grid;
  gap: 7px;
}

.filter-field span {
  color: #3c3543;
  font-size: 0.92rem;
  font-weight: 900;
}

.filter-panel--mine .filter-field > span:first-child {
  color: #111827;
  font-size: 0.94rem;
}

.filter-field input,
.filter-field select,
.size-field select {
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
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
  border: 1px solid #dfe4ef;
  border-radius: 8px;
  background: #ffffff;
  color: #667085;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.select-shell:focus-within {
  border-color: #818cf8;
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.12);
}

.filter-field .select-shell input,
.filter-field .select-shell select {
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
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
  border-color: #4f46e5;
  background: linear-gradient(180deg, #5b4deb, #4937d0);
  box-shadow: 0 16px 28px rgba(79, 70, 229, 0.22);
}

.filter-panel--mine .ghost-action {
  border-color: #dfe4ef;
  color: #4f46e5;
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
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1e3a8a;
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
  box-shadow: 0 14px 34px rgba(42, 38, 61, 0.045);
}

.metric-tile--mine::after {
  content: "";
  position: absolute;
  inset: -42px -20px auto auto;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.48);
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
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.metric-tile--accent {
  border-color: #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
}

.metric-tile--purple {
  border-color: #e8e4ff;
  background: linear-gradient(105deg, #faf8ff, #f2efff);
  color: #4f46e5;
}

.metric-tile--purple .metric-icon {
  background: linear-gradient(135deg, #dcd6ff, #b7adff);
}

.metric-tile--orange {
  border-color: #fce8c7;
  background: linear-gradient(105deg, #fffdf8, #fff5e6);
  color: #c65f00;
}

.metric-tile--orange .metric-icon {
  background: #fff0d7;
}

.metric-tile--teal {
  border-color: #d7f2f1;
  background: linear-gradient(105deg, #fbfeff, #ecfbfb);
  color: #0790a0;
}

.metric-tile--teal .metric-icon {
  background: #d9f6f5;
}

.metric-tile--green {
  border-color: #dcefd4;
  background: linear-gradient(105deg, #fdfffb, #f1faed);
  color: #4d9b1f;
}

.metric-tile--green .metric-icon {
  background: #e6f5df;
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

.page-feedback,
.detail-error {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 14px;
  line-height: 1.55;
}

.page-feedback--danger,
.detail-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.list-panel {
  gap: 18px;
}

.list-panel--mine {
  gap: 18px;
  padding: 18px 24px 16px;
  border-color: #e6e9f2;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(42, 38, 61, 0.055);
}

.list-panel--mine .list-toolbar {
  align-items: center;
}

.list-panel--mine .list-toolbar h2 {
  color: #111827;
  font-size: 1.18rem;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.size-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #746f7a;
  font-weight: 900;
}

.list-panel--mine .size-field {
  color: #111827;
}

.size-field select {
  min-width: 76px;
}

.list-panel--mine .size-field select {
  min-width: 72px;
  border-color: #dfe4ef;
}

.loading-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  color: #746dc3;
  font-weight: 900;
}

.empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 240px;
  padding: 24px;
  border: 1px dashed #d7d1e5;
  border-radius: 8px;
  background: #fbf9fc;
  color: #8a8792;
  text-align: center;
}

.empty-state--mine {
  gap: 8px;
  min-height: 236px;
  padding: 20px 24px 18px;
  border-color: #dfe4ef;
  background:
    radial-gradient(circle at 45% 0%, rgba(124, 107, 239, 0.06), transparent 24%),
    #ffffff;
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
  border: 6px solid #8f83ea;
  border-radius: 9px;
  background: #ffffff;
  box-shadow: 0 14px 26px rgba(93, 79, 200, 0.16);
}

.empty-illustration__clipboard span {
  position: absolute;
  left: 17px;
  width: 32px;
  height: 6px;
  border-radius: 999px;
  background: #dfdbff;
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
  background: #cfc8ff;
  box-shadow: inset 0 8px 0 rgba(255, 255, 255, 0.45);
}

.empty-illustration__building {
  position: absolute;
  bottom: 0;
  width: 32px;
  height: 42px;
  border-radius: 6px 6px 0 0;
  background: #eeeaff;
}

.empty-illustration__building::before {
  content: "";
  position: absolute;
  inset: 10px 9px auto;
  height: 8px;
  border-radius: 3px;
  background: #d5cffb;
  box-shadow: 0 14px 0 #d5cffb;
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
  border-left: 28px solid #a79cff;
  transform: rotate(-12deg);
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: #3c3543;
}

.empty-state--mine h3 {
  color: #111827;
  font-size: 1.16rem;
}

.empty-state--mine p {
  color: #667085;
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
  border-color: #4f46e5;
  background: linear-gradient(180deg, #5b4deb, #4937d0);
}

.task-list {
  display: grid;
  gap: 12px;
}

.task-card {
  position: relative;
  display: grid;
  gap: 14px;
  width: 100%;
  padding: 18px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
  color: inherit;
  text-align: left;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.task-card:hover,
.task-card.selected {
  border-color: #b9b2e8;
  background: #fbf9ff;
  box-shadow: 0 14px 28px rgba(116, 109, 195, 0.08);
}

.task-card.selected::before {
  content: "";
  position: absolute;
  inset: 14px auto 14px 0;
  width: 3px;
  border-radius: 999px;
  background: #746dc3;
}

.deadline-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #334155;
  font-size: 0.82rem;
  font-weight: 900;
}

.deadline-chip--info {
  background: #e0f2fe;
  color: #075985;
}

.deadline-chip--warning {
  background: #fef3c7;
  color: #92400e;
}

.deadline-chip--danger {
  background: #fee2e2;
  color: #991b1b;
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
  background: #f1ecff;
  color: #746dc3;
}

.task-type-icon--orange,
.detail-type-icon--orange,
.detail-type-chip--orange {
  background: #fff3e8;
  color: #c45a1a;
}

.task-type-icon--blue,
.detail-type-icon--blue,
.detail-type-chip--blue {
  background: #edf5ff;
  color: #3763c7;
}

.task-type-icon--green,
.detail-type-icon--green,
.detail-type-chip--green {
  background: #eafaf3;
  color: #0f766e;
}

.task-type-icon--amber,
.detail-type-icon--amber,
.detail-type-chip--amber {
  background: #fff7df;
  color: #92400e;
}

.task-card-heading strong {
  color: #3c3543;
  font-size: 1.14rem;
  line-height: 1.35;
}

.task-description {
  color: #746f7a;
  line-height: 1.58;
}

.task-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  color: #746f7a;
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
  color: #746dc3;
}

.task-card-footer {
  flex-wrap: wrap;
  justify-content: space-between;
  color: #8a8792;
  font-size: 0.88rem;
}

.task-card-footer span:first-child {
  color: #746dc3;
  font-weight: 900;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
  color: #746f7a;
  font-weight: 900;
}

.pagination-bar--mine {
  justify-content: center;
  gap: 14px;
  padding-top: 0;
}

.pagination-bar--mine .page-button {
  min-width: 110px;
  border-color: #e6e9f2;
  color: #9ca3af;
}

.pagination-bar--mine .page-button:not(:disabled) {
  color: #4f46e5;
}

.page-current {
  display: inline-grid;
  place-items: center;
  min-width: 38px;
  height: 42px;
  padding: 0 12px;
  border: 1px solid #8b7dff;
  border-radius: 8px;
  background: #ffffff;
  color: #4f46e5;
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.12);
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

.task-detail-modal {
  grid-template-rows: auto minmax(0, 1fr) auto;
  align-content: stretch;
  width: min(72vw, 960px);
  height: min(82vh, 780px);
  max-width: calc(100vw - 48px);
  max-height: calc(100vh - 48px);
  overflow: hidden;
  padding: clamp(24px, 3vw, 34px);
  font-family: KaiTi, "楷体", STKaiti, "Microsoft YaHei", serif;
  box-shadow: 0 24px 64px rgba(42, 38, 61, 0.18);
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
  color: #746f7a;
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
  color: #2f2940;
  font-size: 1.35rem;
  line-height: 1.25;
  font-weight: 900;
}

.detail-title-block p {
  margin-top: 8px;
  color: #746f7a;
  line-height: 1.6;
}

.detail-reward-strip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 12px;
  padding: 0 0 16px;
  border-bottom: 1px solid #eeeaf5;
}

.detail-reward-strip span,
.detail-reward-strip small {
  color: #746f7a;
  font-weight: 900;
}

.detail-reward-strip strong {
  color: #e11d48;
  font-size: 1.5rem;
  line-height: 1;
  font-weight: 900;
}

.detail-reward-strip--points strong {
  color: #4f46e5;
}

.detail-reward-strip--free strong {
  color: #0f766e;
}

.detail-reward-strip--negotiable strong {
  color: #a1490c;
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
  color: #5c5470;
  font-size: 0.9rem;
  font-weight: 900;
}

.detail-info-list dt svg {
  color: #746dc3;
}

.detail-info-list dd {
  margin: 0;
  color: #4a4455;
  line-height: 1.65;
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
  background: #fff4e6;
  color: #a1490c;
}

.detail-warning-pill {
  background: #fff7ed;
  color: #b45309;
}

.detail-action-row {
  padding-top: 18px;
  border-top: 1px solid #eeeaf5;
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
  color: #746dc3;
  font-weight: 900;
}

.owner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.operation-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  line-height: 1.55;
}

.conversation-entry {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
}

.conversation-entry > div {
  display: grid;
  gap: 3px;
}

.conversation-entry strong {
  color: #166534;
}

.conversation-entry span {
  color: #166534;
  line-height: 1.5;
}

.operation-feedback--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.application-panel,
.candidates-section {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.application-panel--locked {
  border-color: #e6e2eb;
  background: #fbf9fc;
}

.completion-panel {
  gap: 14px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.completion-heading,
.proof-card-heading,
.completion-action-box,
.review-actions,
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
  color: #3c3543;
  font-size: 1rem;
}

.completion-heading p,
.proof-card-heading span,
.completion-action-box span,
.resolution-panel p,
.proof-card p,
.proof-empty {
  margin: 0;
  color: #746f7a;
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
  border: 1px solid #e6e2eb;
  border-radius: 8px;
  background: #ffffff;
  color: #8a8792;
  font-size: 0.88rem;
  font-weight: 900;
}

.completion-steps span.done {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #075985;
}

.completion-steps span.active {
  border-color: #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
}

.proof-card,
.completion-action-box,
.resolution-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
}

.proof-card-heading svg {
  color: #746dc3;
}

.proof-attachment-list,
.attachment-preview-list,
.review-actions {
  display: flex;
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
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #075985;
  font-size: 0.88rem;
  font-weight: 900;
}

.proof-attachment-list a:hover {
  border-color: #93c5fd;
  background: #dbeafe;
}

.proof-empty {
  display: inline-flex;
  width: fit-content;
  padding: 8px 10px;
  border: 1px dashed #d7d1e5;
  border-radius: 8px;
  background: #fbf9fc;
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
  color: #3c3543;
  font-weight: 900;
}

.completion-field textarea,
.completion-field input {
  width: 100%;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #3c3543;
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
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
}

.completion-field textarea[aria-invalid="true"],
.completion-field input[aria-invalid="true"] {
  border-color: #ef4444;
}

.completion-field small {
  color: #8a8792;
  line-height: 1.45;
}

.completion-upload-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(210px, 0.42fr);
  gap: 12px;
  align-items: end;
}

.attachment-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.file-pick-control {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 72px;
  padding: 14px;
  border: 1px dashed #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #075985;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.file-pick-control:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.file-pick-control span {
  display: grid;
  gap: 3px;
}

.file-pick-control strong {
  color: #075985;
}

.file-pick-control small {
  color: #64748b;
}

.file-pick-control input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.attachment-preview-list span {
  background: #f0fdfa;
  border-color: #99f6e4;
  color: #0f766e;
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
  background: rgba(15, 118, 110, 0.1);
}

.completion-submit {
  width: fit-content;
  justify-self: end;
}

.resolution-panel--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.resolution-panel--warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.resolution-panel--danger {
  border-color: #fecaca;
  background: #fef2f2;
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
  color: #3c3543;
  font-size: 1rem;
}

.application-copy,
.candidate-heading p {
  margin: 0;
  color: #746f7a;
  line-height: 1.6;
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
  color: #3c3543;
  font-weight: 900;
}

.application-field textarea {
  width: 100%;
  min-height: 112px;
  padding: 11px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #3c3543;
  line-height: 1.5;
  outline: 0;
  resize: vertical;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.application-field textarea:focus {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
}

.application-field textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.application-field small {
  color: #8a8792;
}

.application-gate-action {
  width: fit-content;
}

.cancel-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
  color: #7c2d12;
}

.cancel-panel h3,
.cancel-panel p {
  margin: 0;
}

.cancel-panel h3 {
  color: #7c2d12;
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
  border: 1px solid #fdba74;
  border-radius: 8px;
  background: #ffffff;
  color: #3c3543;
  outline: 0;
  resize: vertical;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.cancel-reason-field textarea:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.12);
}

.cancel-reason-field textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.cancel-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.field-error {
  margin: 0;
  color: #b91c1c;
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
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #fcfbfd;
}

.detail-facts dt {
  color: #8a8792;
  font-size: 0.84rem;
}

.detail-facts dd {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.45;
}

.detail-section {
  display: grid;
  gap: 9px;
}

.detail-section h3 {
  margin: 0;
  color: #3c3543;
  font-size: 1rem;
}

.detail-section p {
  margin: 0;
  color: #746f7a;
  line-height: 1.65;
  white-space: pre-wrap;
}

.candidates-section {
  border-color: #e0f2fe;
  background: #f8fcff;
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
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1e3a8a;
  font-weight: 900;
}

.accepted-candidate {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 12px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  font-weight: 900;
  line-height: 1.5;
}

.candidate-empty {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 150px;
  padding: 20px;
  border: 1px dashed #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
  text-align: center;
}

.candidate-empty strong {
  color: #3c3543;
}

.candidate-list {
  display: grid;
  gap: 12px;
}

.candidate-card {
  display: grid;
  gap: 13px;
  padding: 14px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
}

.candidate-card--accepted {
  border-color: #86efac;
  background: #f0fdf4;
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
  border: 1px solid #dbeafe;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
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
  color: #3c3543;
  font-size: 1rem;
}

.candidate-identity span,
.candidate-actions span {
  color: #8a8792;
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
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #fcfbfd;
}

.candidate-metrics dt {
  color: #8a8792;
  font-size: 0.82rem;
}

.candidate-metrics dd {
  margin: 0;
  color: #3c3543;
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
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.82rem;
  font-weight: 900;
}

.candidate-tag--verified {
  background: #dcfce7;
  color: #166534;
}

.candidate-tag--muted {
  background: #e2e8f0;
  color: #475569;
}

.candidate-message {
  margin: 0;
  color: #4a4650;
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
  border: 1px solid #e6e2eb;
  border-radius: 8px;
  background: #fbf9fc;
  color: #746f7a;
  font-weight: 800;
}

.boundary-list span.active {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.guide-list {
  display: grid;
  gap: 9px;
}

.guide-item {
  align-items: flex-start;
  padding: 10px;
  border: 1px solid #e0f2fe;
  border-radius: 8px;
  background: #f0f9ff;
  color: #075985;
}

.guide-item span {
  display: grid;
  gap: 2px;
}

.guide-item strong {
  color: #075985;
}

.guide-item small {
  line-height: 1.45;
}

.history-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 20px;
  color: #746f7a;
}

.history-list li {
  padding-left: 4px;
}

.history-list span {
  display: block;
  color: #3c3543;
  font-weight: 900;
}

.history-list small {
  color: #8a8792;
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
  .page-title-card,
  .filter-grid,
  .metric-strip,
  .metric-strip--mine,
  .task-meta-grid,
  .detail-info-list div,
  .detail-facts,
  .candidate-summary,
  .candidate-metrics,
  .completion-steps,
  .completion-upload-grid {
    grid-template-columns: 1fr;
  }

  .page-title-card {
    gap: 18px;
    padding: 22px;
  }

  .filter-heading,
  .list-toolbar,
  .detail-topbar,
  .task-card-heading,
  .detail-card-header,
  .detail-action-row,
  .application-heading,
  .completion-heading,
  .completion-action-box,
  .conversation-entry,
  .candidate-heading,
  .candidate-top,
  .candidate-actions,
  .proof-card-heading,
  .resolution-actions {
    display: grid;
  }

  .toolbar-actions,
  .filter-actions {
    justify-content: flex-start;
  }

  .filter-panel--mine .filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .task-detail-modal {
    width: calc(100vw - 24px);
    height: calc(100vh - 24px);
    max-width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }

  .search-field {
    grid-template-columns: auto minmax(0, 1fr);
    padding: 12px;
  }

  .search-submit {
    grid-column: 1 / -1;
    width: 100%;
  }

  .publish-shortcut,
  .primary-action,
  .ghost-action,
  .danger-action,
  .page-button,
  .completion-submit {
    width: 100%;
  }

  .attachment-input-row {
    grid-template-columns: 1fr;
  }

  .pagination-bar {
    display: grid;
  }

  .pagination-bar--mine {
    grid-template-columns: 1fr auto 1fr;
  }

  .title-icon {
    width: 64px;
    height: 64px;
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
