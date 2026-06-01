<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  BellDot,
  Check,
  CheckCheck,
  CheckCircle2,
  ClipboardCheck,
  FileCheck2,
  Info,
  LoaderCircle,
  MessageCircle,
  RefreshCw,
  Search,
  Sparkles,
  Star,
  StarHalf,
  UserRound
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { useNotificationCenter } from '../composables/useNotificationCenter'
import { optionLabel, TASK_CATEGORY_OPTIONS, TASK_STATUS_OPTIONS, statusTone } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const { currentUserId } = useAuth()
const {
  readIds,
  markNotificationIdsRead,
  replaceNotificationSummary,
  syncReadIdsFromStorage
} = useNotificationCenter()

const reviewLabels = {
  1: '体验很差',
  2: '低于预期',
  3: '基本完成',
  4: '值得信任',
  5: '非常可靠'
}

const reviewTags = [
  '准时响应',
  '说明清楚',
  '凭证完整',
  '沟通友好',
  '按标准完成',
  '愿意再次合作'
]

const notificationFilters = [
  { value: 'ALL', label: '全部' },
  { value: 'UNREAD', label: '未读' },
  { value: 'TASK', label: '任务' },
  { value: 'REVIEW', label: '评价' },
  { value: 'CREDIT', label: '信用' }
]

const activeSection = computed(() => (route.name === 'notifications' ? 'notifications' : 'reviews'))
const activeLabel = computed(() => (activeSection.value === 'notifications' ? '消息中心' : '评价中心'))
const currentUser = computed(() => currentUserId.value)

const tasks = ref([])
const notifications = ref([])
const selectedTaskId = ref('')
const selectedNotificationId = ref('')
const loadingReviews = ref(false)
const loadingNotifications = ref(false)
const reviewFeedback = ref('')
const reviewFeedbackType = ref('success')
const notificationFeedback = ref('')
const notificationSearch = ref('')
const notificationFilter = ref('ALL')

const reviewForm = reactive({
  rating: 5,
  comment: '',
  tags: []
})

let notificationRequestId = 0

const selectedTask = computed(() =>
  tasks.value.find((task) => String(task.id) === String(selectedTaskId.value)) || tasks.value[0] || null
)

const selectedNotification = computed(
  () =>
    decoratedNotifications.value.find(
      (notification) => String(notification.id) === String(selectedNotificationId.value)
    ) ||
    decoratedNotifications.value[0] ||
    null
)

const reviewee = computed(() => resolveReviewee(selectedTask.value))
const existingReview = computed(() => {
  const task = selectedTask.value
  if (!task) return null
  return task.reviews?.find(
    (review) =>
      String(review.reviewerId) === String(currentUser.value) &&
      String(review.revieweeId) === String(reviewee.value?.id)
  )
})
const canSubmitReview = computed(
  () => Boolean(selectedTask.value?.id && reviewee.value?.id && !existingReview.value)
)
const pendingReviewTasks = computed(() =>
  tasks.value.filter((task) => {
    const target = resolveReviewee(task)
    if (!target?.id) return false
    return !task.reviews?.some(
      (review) =>
        String(review.reviewerId) === String(currentUser.value) &&
        String(review.revieweeId) === String(target.id)
    )
  })
)
const reviewedTasks = computed(() => tasks.value.filter((task) => !pendingReviewTasks.value.includes(task)))
const averageGivenRating = computed(() => {
  const ownReviews = tasks.value
    .flatMap((task) => task.reviews || [])
    .filter((review) => String(review.reviewerId) === String(currentUser.value))
  if (!ownReviews.length) return '0.0'
  const average = ownReviews.reduce((sum, review) => sum + Number(review.rating || 0), 0) / ownReviews.length
  return average.toFixed(1)
})
const selectedTaskReviews = computed(() => selectedTask.value?.reviews || [])
const reviewStatusCopy = computed(() => {
  if (!selectedTask.value) return '暂无可评价任务'
  if (existingReview.value) return '你已经完成本次互评，评价记录会作为信用判断的一部分。'
  return '完成后请从准时、沟通、凭证和完成标准四个维度给出真实评价。'
})

const decoratedNotifications = computed(() =>
  notifications.value
    .map((notification) => ({
      ...notification,
      read: Boolean(notification.readAt || readIds.value.includes(String(notification.id)))
    }))
    .sort((current, next) => new Date(next.createdAt) - new Date(current.createdAt))
)
const unreadNotifications = computed(() => decoratedNotifications.value.filter((notification) => !notification.read))
const filteredNotifications = computed(() => {
  const keyword = notificationSearch.value.trim().toLowerCase()
  return decoratedNotifications.value.filter((notification) => {
    const matchesFilter =
      notificationFilter.value === 'ALL' ||
      (notificationFilter.value === 'UNREAD' ? !notification.read : notification.type === notificationFilter.value)
    const matchesKeyword =
      !keyword ||
      [notification.title, notification.content, notification.source, notification.taskId]
        .filter((value) => value !== undefined && value !== null)
        .join(' ')
        .toLowerCase()
        .includes(keyword)

    return matchesFilter && matchesKeyword
  })
})

const notificationMetrics = computed(() => [
  { label: '未读通知', value: unreadNotifications.value.length, tone: 'info' },
  { label: '待评价', value: pendingReviewTasks.value.length, tone: 'accent' },
  { label: '已完成评价', value: reviewedTasks.value.length, tone: 'success' }
])

function normalizePagedItems(result) {
  if (Array.isArray(result)) return result
  return result?.tasks || result?.items || result?.records || result?.content || result?.list || []
}

function normalizeTask(task = {}) {
  const id = task.id ?? task.taskId
  return {
    ...task,
    id,
    requesterId: task.requesterId ?? task.ownerId,
    providerId: task.providerId ?? task.helperId ?? task.acceptedApplicantId,
    requesterName: task.requesterName || task.ownerName || '求助同学',
    providerName: task.providerName || task.helperName || '帮助同学',
    status: task.status || 'COMPLETED',
    completedAt: task.completedAt || task.updatedAt || task.deadline || task.createdAt || '',
    reviews: Array.isArray(task.reviews) ? task.reviews : []
  }
}

function normalizeReview(review = {}, taskId) {
  return {
    id: review.id ?? `local-review-${Date.now()}`,
    taskId: review.taskId ?? taskId,
    reviewerId: review.reviewerId ?? currentUser.value,
    revieweeId: review.revieweeId,
    rating: Number(review.rating || 5),
    comment: review.comment || '',
    createdAt: review.createdAt || new Date().toISOString()
  }
}

function resolveReviewee(task) {
  if (!task) return null
  const userId = String(currentUser.value)
  if (task.requesterId !== undefined && String(task.requesterId) === userId) {
    return {
      id: task.providerId,
      name: task.providerName || `同学 ${task.providerId || ''}`.trim() || '帮助同学',
      role: '帮助者'
    }
  }
  if (task.providerId !== undefined && String(task.providerId) === userId) {
    return {
      id: task.requesterId,
      name: task.requesterName || `同学 ${task.requesterId || ''}`.trim() || '求助同学',
      role: '求助者'
    }
  }
  return {
    id: task.providerId || task.requesterId,
    name: task.providerName || task.requesterName || '协作同学',
    role: task.providerId ? '帮助者' : '求助者'
  }
}

function taskCategoryLabel(task = {}) {
  return optionLabel(TASK_CATEGORY_OPTIONS, task.category)
}

function taskStatusLabel(status) {
  return optionLabel(TASK_STATUS_OPTIONS, status)
}

function taskStatusTone(status) {
  return statusTone(status)
}

function formatDateTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatRelativeTime(value) {
  if (!value) return '暂无时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const minutes = Math.max(0, Math.round((Date.now() - date.getTime()) / 60000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.round(hours / 24)} 天前`
}

function shortText(text, length = 72) {
  if (!text) return '暂无说明'
  return text.length > length ? `${text.slice(0, length)}...` : text
}

function setReviewFeedback(message, type = 'success') {
  reviewFeedback.value = message
  reviewFeedbackType.value = type
}

function resetReviewForm() {
  reviewForm.rating = 5
  reviewForm.comment = ''
  reviewForm.tags = []
  reviewFeedback.value = ''
}

function selectTask(task) {
  if (!task?.id) return
  selectedTaskId.value = String(task.id)
  resetReviewForm()
}

function toggleReviewTag(tag) {
  if (reviewForm.tags.includes(tag)) {
    reviewForm.tags = reviewForm.tags.filter((item) => item !== tag)
    return
  }
  reviewForm.tags = [...reviewForm.tags, tag]
}

async function loadReviews() {
  loadingReviews.value = true
  reviewFeedback.value = ''
  try {
    const [completedResult, reviewedResult] = await Promise.allSettled([
      api.getTasks({ status: 'COMPLETED', page: 1, size: 12 }),
      api.getTasks({ status: 'REVIEWED', page: 1, size: 12 })
    ])
    const rawTasks = [
      ...(completedResult.status === 'fulfilled' ? normalizePagedItems(completedResult.value) : []),
      ...(reviewedResult.status === 'fulfilled' ? normalizePagedItems(reviewedResult.value) : [])
    ]
    const uniqueTasks = [...new Map(rawTasks.map((task) => [String(task.id ?? task.taskId), task])).values()]
      .filter((task) => task.id || task.taskId)
      .map(normalizeTask)

    tasks.value = uniqueTasks
    if (uniqueTasks.length) await hydrateTaskReviews()

    if (!selectedTaskId.value || !tasks.value.some((task) => String(task.id) === String(selectedTaskId.value))) {
      selectedTaskId.value = String(pendingReviewTasks.value[0]?.id || tasks.value[0]?.id || '')
    }
  } catch {
    tasks.value = []
    selectedTaskId.value = ''
  } finally {
    loadingReviews.value = false
  }
}

async function hydrateTaskReviews() {
  const settled = await Promise.allSettled(
    tasks.value.map(async (task) => {
      const [reviewsResult, detailResult] = await Promise.allSettled([
        api.getTaskReviews(task.id),
        api.getTaskDetail(task.id)
      ])
      const reviewItems =
        reviewsResult.status === 'fulfilled' && Array.isArray(reviewsResult.value)
          ? reviewsResult.value
          : detailResult.status === 'fulfilled' && Array.isArray(detailResult.value?.reviews)
            ? detailResult.value.reviews
            : task.reviews

      const detailTask =
        detailResult.status === 'fulfilled' && detailResult.value?.task
          ? normalizeTask(detailResult.value.task)
          : detailResult.status === 'fulfilled' && detailResult.value?.id
            ? normalizeTask(detailResult.value)
            : {}

      return {
        ...task,
        ...detailTask,
        id: task.id,
        reviews: (reviewItems || []).map((review) => normalizeReview(review, task.id))
      }
    })
  )

  tasks.value = settled.map((result, index) => (result.status === 'fulfilled' ? result.value : tasks.value[index]))
}

async function submitReview() {
  if (!selectedTask.value?.id || !reviewee.value?.id || !canSubmitReview.value) return
  const comment = reviewForm.comment.trim()
  if (comment.length < 8) {
    setReviewFeedback('请至少写 8 个字，说明本次协作是否符合预期。', 'danger')
    return
  }
  if (comment.length > 500) {
    setReviewFeedback('文字评价最多 500 个字。', 'danger')
    return
  }

  loadingReviews.value = true
  reviewFeedback.value = ''
  const tagLine = reviewForm.tags.length ? `\n\n亮点标签：${reviewForm.tags.join('、')}` : ''
  const payload = {
    revieweeId: reviewee.value.id,
    rating: reviewForm.rating,
    comment: `${comment}${tagLine}`.slice(0, 500)
  }

  try {
    const result = await api.createTaskReview(selectedTask.value.id, payload)
    const review = normalizeReview(result || payload, selectedTask.value.id)
    appendReviewToTask(selectedTask.value.id, review)
    setReviewFeedback('评价已提交，后续会参与信用记录计算。')
    resetReviewForm()
  } catch (error) {
    setReviewFeedback(error.message || '评价提交失败，请稍后重试。', 'danger')
  } finally {
    loadingReviews.value = false
  }
}

function appendReviewToTask(taskId, review) {
  tasks.value = tasks.value.map((task) => {
    if (String(task.id) !== String(taskId)) return task
    return {
      ...task,
      status: 'REVIEWED',
      reviews: [...(task.reviews || []), review]
    }
  })
}

function normalizeNotificationFromConversation(conversation = {}) {
  const id = `conversation-${conversation.id || conversation.conversationId}`
  const content =
    conversation.lastMessagePreview ||
    conversation.lastMessageContent ||
    conversation.lastMessage ||
    conversation.preview ||
    '有新的站内协作动态。'
  return {
    id,
    type: conversation.type === 'SYSTEM' ? 'CREDIT' : 'TASK',
    tone: Number(conversation.unreadCount || 0) > 0 ? 'info' : 'muted',
    title: conversation.title || '站内协作动态',
    content,
    source: conversation.type === 'SYSTEM' ? '系统通知' : '任务会话',
    taskId: conversation.relatedType === 'TASK' ? conversation.relatedId : null,
    conversationId: conversation.id || conversation.conversationId,
    createdAt: conversation.lastMessageAt || conversation.updatedAt || conversation.createdAt || new Date().toISOString(),
    readAt: Number(conversation.unreadCount || 0) > 0 ? null : conversation.readAt
  }
}

function normalizeNotificationFromMessage(message = {}, conversation = {}) {
  const content = message.content || '有一条新的系统通知。'
  return {
    id: `message-${message.id || message.createdAt || content}`,
    type: inferNotificationType(content),
    tone: inferNotificationTone(content),
    title: inferNotificationTitle(content, conversation.title),
    content,
    source: conversation.title || '系统通知',
    taskId: conversation.relatedType === 'TASK' ? conversation.relatedId : null,
    conversationId: conversation.id || message.conversationId,
    createdAt: message.createdAt || conversation.lastMessageAt || new Date().toISOString(),
    readAt: message.readAt || null
  }
}

function inferNotificationType(content) {
  if (/评价|互评/.test(content)) return 'REVIEW'
  if (/信用|积分/.test(content)) return 'CREDIT'
  return 'TASK'
}

function inferNotificationTone(content) {
  if (/争议|驳回|失败|超时/.test(content)) return 'danger'
  if (/确认|待处理|提醒/.test(content)) return 'warning'
  if (/完成|通过|更新/.test(content)) return 'success'
  return 'info'
}

function inferNotificationTitle(content, fallback) {
  if (/评价|互评/.test(content)) return '评价提醒'
  if (/信用|积分/.test(content)) return '信用记录更新'
  if (/完成|凭证/.test(content)) return '任务完成动态'
  return fallback || '系统通知'
}

async function loadNotifications() {
  loadingNotifications.value = true
  notificationFeedback.value = ''
  const requestId = ++notificationRequestId

  try {
    const result = await api.getConversations({ page: 1, size: 20 })
    if (requestId !== notificationRequestId) return
    const conversations = normalizePagedItems(result)
    replaceNotificationSummary(conversations)
    let items = conversations.map(normalizeNotificationFromConversation)

    const messageResults = await Promise.allSettled(
      conversations.slice(0, 8).map(async (conversation) => {
        const conversationId = conversation.id || conversation.conversationId
        const messages = await api.getConversationMessages(conversationId, { page: 1, size: 20 })
        return normalizePagedItems(messages)
          .filter((message) => message.type === 'SYSTEM_NOTIFICATION')
          .map((message) => normalizeNotificationFromMessage(message, conversation))
      })
    )
    items = [
      ...items,
      ...messageResults.flatMap((resultItem) => (resultItem.status === 'fulfilled' ? resultItem.value : []))
    ]

    notifications.value = items
    selectDefaultNotification()
  } catch {
    if (requestId !== notificationRequestId) return
    replaceNotificationSummary([])
    notifications.value = []
    selectDefaultNotification()
  } finally {
    if (requestId === notificationRequestId) loadingNotifications.value = false
  }
}

function selectDefaultNotification() {
  if (
    !selectedNotificationId.value ||
    !decoratedNotifications.value.some((notification) => String(notification.id) === String(selectedNotificationId.value))
  ) {
    selectedNotificationId.value = String(unreadNotifications.value[0]?.id || decoratedNotifications.value[0]?.id || '')
  }
}

function selectNotification(notification) {
  if (!notification?.id) return
  selectedNotificationId.value = String(notification.id)
}

function markNotificationRead(notification) {
  if (!notification?.id || notification.read) return
  markNotificationIdsRead([notification.id])
  notificationFeedback.value = '已标记为已读。'
}

function markAllNotificationsRead() {
  markNotificationIdsRead(decoratedNotifications.value.map((notification) => notification.id))
  notificationFeedback.value = '全部通知已标记为已读。'
}

async function switchSection(section) {
  await router.push({ name: section === 'notifications' ? 'notifications' : 'reviews' })
}

function notificationIcon(type) {
  if (type === 'REVIEW') return Star
  if (type === 'CREDIT') return Sparkles
  return ClipboardCheck
}

function notificationToneClass(notification) {
  return `notification-icon--${notification.tone || 'info'}`
}

watch(currentUser, () => {
  syncReadIdsFromStorage()
})

onMounted(async () => {
  await Promise.all([loadReviews(), loadNotifications()])
})
</script>

<template>
  <DashboardShell :active-label="activeLabel">
    <main class="fe08-page">
      <section class="page-title-card fe08-title-card">
        <div class="title-icon">
          <component
            :is="activeSection === 'notifications' ? BellDot : StarHalf"
            :size="34"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>FE08 · 评价与通知</p>
          <h1>{{ activeSection === 'notifications' ? '消息中心与已读管理' : '任务评分评价中心' }}</h1>
          <span>围绕任务完成后的互评、信用记录和关键通知，保留真实可追溯的协作状态。</span>
        </div>
        <div
          class="section-switch"
          role="tablist"
          aria-label="评价与通知切换"
        >
          <button
            type="button"
            role="tab"
            :aria-selected="activeSection === 'reviews'"
            :class="{ active: activeSection === 'reviews' }"
            @click="switchSection('reviews')"
          >
            <Star
              :size="17"
              aria-hidden="true"
            />
            评价
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="activeSection === 'notifications'"
            :class="{ active: activeSection === 'notifications' }"
            @click="switchSection('notifications')"
          >
            <Bell
              :size="17"
              aria-hidden="true"
            />
            通知
          </button>
        </div>
      </section>

      <section
        class="metric-strip"
        aria-label="评价与通知概览"
      >
        <article class="metric-tile metric-tile--accent">
          <span>待评价任务</span>
          <strong>{{ pendingReviewTasks.length }}</strong>
        </article>
        <article class="metric-tile metric-tile--success">
          <span>我给出的均分</span>
          <strong>{{ averageGivenRating }}</strong>
        </article>
        <article class="metric-tile metric-tile--info">
          <span>未读通知</span>
          <strong>{{ unreadNotifications.length }}</strong>
        </article>
      </section>

      <section
        v-if="activeSection === 'reviews'"
        class="review-layout"
      >
        <aside class="panel-card task-review-list">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                互评任务
              </p>
              <h2>选择一次已完成协作</h2>
            </div>
            <button
              class="icon-action"
              type="button"
              aria-label="刷新评价任务"
              :disabled="loadingReviews"
              @click="loadReviews"
            >
              <RefreshCw
                :class="{ spinning: loadingReviews }"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>

          <div
            v-if="loadingReviews && !tasks.length"
            class="loading-state"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="24"
              aria-hidden="true"
            />
            <span>正在同步评价任务...</span>
          </div>

          <div
            v-else-if="!tasks.length"
            class="empty-state"
          >
            <Star
              :size="34"
              aria-hidden="true"
            />
            <h3>暂无可评价任务</h3>
            <p>任务完成后，互评入口会出现在这里。</p>
          </div>

          <div
            v-else
            class="review-task-list"
          >
            <button
              v-for="task in tasks"
              :key="task.id"
              class="review-task-card"
              :class="{ active: String(task.id) === String(selectedTask?.id) }"
              type="button"
              :aria-pressed="String(task.id) === String(selectedTask?.id)"
              @click="selectTask(task)"
            >
              <span class="review-task-topline">
                <strong>{{ task.title }}</strong>
                <StatusBadge
                  :label="taskStatusLabel(task.status)"
                  :tone="taskStatusTone(task.status)"
                />
              </span>
              <span class="review-task-meta">
                <span>{{ taskCategoryLabel(task) }}</span>
                <span>{{ task.campus || '未设置校区' }} · {{ task.location || '未设置地点' }}</span>
              </span>
              <span class="review-task-footer">
                <span>完成于 {{ formatDateTime(task.completedAt) }}</span>
                <span v-if="resolveReviewee(task)">评价 {{ resolveReviewee(task).role }}</span>
              </span>
            </button>
          </div>
        </aside>

        <section class="panel-card review-form-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                评分评价
              </p>
              <h2>{{ selectedTask?.title || '选择任务后填写评价' }}</h2>
            </div>
            <StatusBadge
              :label="existingReview ? '已评价' : '待评价'"
              :tone="existingReview ? 'success' : 'warning'"
            />
          </div>

          <p class="panel-copy">
            {{ reviewStatusCopy }}
          </p>

          <div
            v-if="selectedTask"
            class="review-context"
          >
            <div>
              <span>评价对象</span>
              <strong>{{ reviewee?.name || '协作同学' }}</strong>
              <small>{{ reviewee?.role || '协作方' }}</small>
            </div>
            <div>
              <span>完成标准</span>
              <strong>{{ shortText(selectedTask.completionStandard, 48) }}</strong>
              <small>{{ selectedTask.rewardDescription || '奖励方式未设置' }}</small>
            </div>
          </div>

          <form
            v-if="selectedTask && canSubmitReview"
            class="review-form"
            @submit.prevent="submitReview"
          >
            <fieldset class="rating-field">
              <legend>本次协作评分</legend>
              <div
                class="star-row"
                role="radiogroup"
                aria-label="本次协作评分"
              >
                <button
                  v-for="score in 5"
                  :key="score"
                  type="button"
                  class="star-button"
                  :class="{ active: score <= reviewForm.rating }"
                  :aria-pressed="score === reviewForm.rating"
                  @click="reviewForm.rating = score"
                >
                  <Star
                    :size="25"
                    aria-hidden="true"
                  />
                  <span class="sr-only">{{ score }} 星</span>
                </button>
              </div>
              <span>{{ reviewLabels[reviewForm.rating] }}</span>
            </fieldset>

            <fieldset class="tag-field">
              <legend>协作印象</legend>
              <div class="tag-list">
                <button
                  v-for="tag in reviewTags"
                  :key="tag"
                  type="button"
                  class="tag-chip"
                  :class="{ active: reviewForm.tags.includes(tag) }"
                  :aria-pressed="reviewForm.tags.includes(tag)"
                  @click="toggleReviewTag(tag)"
                >
                  <Check
                    v-if="reviewForm.tags.includes(tag)"
                    :size="15"
                    aria-hidden="true"
                  />
                  {{ tag }}
                </button>
              </div>
            </fieldset>

            <label
              class="field"
              for="review-comment"
            >
              <span>文字评价</span>
              <textarea
                id="review-comment"
                v-model.trim="reviewForm.comment"
                maxlength="500"
                placeholder="例如：是否准时、沟通是否顺畅、凭证是否清楚、是否符合完成标准。"
              />
              <small>{{ reviewForm.comment.length }} / 500</small>
            </label>

            <section
              v-if="reviewFeedback"
              class="operation-feedback"
              :class="reviewFeedbackType === 'danger' ? 'operation-feedback--danger' : 'operation-feedback--success'"
              role="status"
            >
              <Info
                :size="18"
                aria-hidden="true"
              />
              <span>{{ reviewFeedback }}</span>
            </section>

            <button
              class="primary-action"
              type="submit"
              :disabled="loadingReviews"
            >
              <LoaderCircle
                v-if="loadingReviews"
                class="spinning"
                :size="18"
                aria-hidden="true"
              />
              <CheckCircle2
                v-else
                :size="18"
                aria-hidden="true"
              />
              提交评价
            </button>
          </form>

          <section
            v-else-if="existingReview"
            class="review-complete-state"
          >
            <CheckCircle2
              :size="42"
              aria-hidden="true"
            />
            <strong>你已完成评价</strong>
            <p>{{ existingReview.comment }}</p>
          </section>
        </section>

        <section class="panel-card review-history-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                评价记录
              </p>
              <h2>本任务互评</h2>
            </div>
            <StatusBadge
              :label="`${selectedTaskReviews.length} 条`"
              tone="info"
            />
          </div>

          <div
            v-if="!selectedTaskReviews.length"
            class="empty-state empty-state--compact"
          >
            <MessageCircle
              :size="30"
              aria-hidden="true"
            />
            <h3>还没有评价记录</h3>
            <p>双方评价后会在这里汇总展示。</p>
          </div>

          <article
            v-for="review in selectedTaskReviews"
            v-else
            :key="review.id"
            class="history-review"
          >
            <div class="history-review-head">
              <span class="review-avatar">
                <UserRound
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <div>
                <strong>{{ String(review.reviewerId) === String(currentUser) ? '我给出的评价' : '对方评价' }}</strong>
                <small>{{ formatDateTime(review.createdAt) }}</small>
              </div>
              <span class="mini-stars">
                <Star
                  v-for="score in review.rating"
                  :key="score"
                  :size="14"
                  aria-hidden="true"
                />
              </span>
            </div>
            <p>{{ review.comment || '未填写文字评价。' }}</p>
          </article>
        </section>
      </section>

      <section
        v-else
        class="notification-layout"
      >
        <section class="panel-card notification-list-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                通知列表
              </p>
              <h2>关键事件不会丢</h2>
            </div>
            <button
              class="ghost-action ghost-action--compact"
              type="button"
              :disabled="!unreadNotifications.length"
              @click="markAllNotificationsRead"
            >
              <CheckCheck
                :size="17"
                aria-hidden="true"
              />
              全部已读
            </button>
          </div>

          <div class="notification-tools">
            <label
              class="notification-search"
              for="notification-search"
            >
              <Search
                :size="18"
                aria-hidden="true"
              />
              <input
                id="notification-search"
                v-model.trim="notificationSearch"
                type="search"
                placeholder="搜索通知、任务编号或来源"
              >
            </label>
            <button
              class="icon-action"
              type="button"
              aria-label="刷新通知"
              :disabled="loadingNotifications"
              @click="loadNotifications"
            >
              <RefreshCw
                :class="{ spinning: loadingNotifications }"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>

          <div
            class="filter-tabs"
            role="tablist"
            aria-label="通知筛选"
          >
            <button
              v-for="filter in notificationFilters"
              :key="filter.value"
              type="button"
              :class="{ active: notificationFilter === filter.value }"
              :aria-pressed="notificationFilter === filter.value"
              @click="notificationFilter = filter.value"
            >
              {{ filter.label }}
            </button>
          </div>

          <div
            v-if="loadingNotifications && !notifications.length"
            class="loading-state"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="24"
              aria-hidden="true"
            />
            <span>正在同步通知...</span>
          </div>

          <div
            v-else-if="!filteredNotifications.length"
            class="empty-state"
          >
            <Bell
              :size="34"
              aria-hidden="true"
            />
            <h3>没有匹配通知</h3>
            <p>调整筛选条件后再查看。</p>
          </div>

          <div
            v-else
            class="notification-list"
          >
            <button
              v-for="notification in filteredNotifications"
              :key="notification.id"
              class="notification-card"
              :class="{ active: String(notification.id) === String(selectedNotification?.id), unread: !notification.read }"
              type="button"
              @click="selectNotification(notification)"
            >
              <span
                class="notification-icon"
                :class="notificationToneClass(notification)"
              >
                <component
                  :is="notificationIcon(notification.type)"
                  :size="22"
                  aria-hidden="true"
                />
              </span>
              <span class="notification-body">
                <span class="notification-card-head">
                  <strong>{{ notification.title }}</strong>
                  <small>{{ formatRelativeTime(notification.createdAt) }}</small>
                </span>
                <span>{{ shortText(notification.content, 78) }}</span>
                <span class="notification-meta">{{ notification.source }} · {{ notification.taskId ? `任务 #${notification.taskId}` : '系统消息' }}</span>
              </span>
              <span
                v-if="!notification.read"
                class="unread-dot"
                aria-label="未读通知"
              />
            </button>
          </div>
        </section>

        <aside class="panel-card notification-detail-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                通知详情
              </p>
              <h2>{{ selectedNotification?.title || '选择一条通知' }}</h2>
            </div>
            <StatusBadge
              :label="selectedNotification?.read ? '已读' : '未读'"
              :tone="selectedNotification?.read ? 'success' : 'warning'"
            />
          </div>

          <template v-if="selectedNotification">
            <div class="notification-detail-hero">
              <span
                class="notification-icon"
                :class="notificationToneClass(selectedNotification)"
              >
                <component
                  :is="notificationIcon(selectedNotification.type)"
                  :size="30"
                  aria-hidden="true"
                />
              </span>
              <div>
                <strong>{{ selectedNotification.source }}</strong>
                <span>{{ formatDateTime(selectedNotification.createdAt) }}</span>
              </div>
            </div>
            <p class="notification-detail-copy">
              {{ selectedNotification.content }}
            </p>

            <dl class="notification-meta-list">
              <div>
                <dt>分类</dt>
                <dd>{{ selectedNotification.type }}</dd>
              </div>
              <div>
                <dt>关联任务</dt>
                <dd>{{ selectedNotification.taskId ? `#${selectedNotification.taskId}` : '无' }}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd>{{ selectedNotification.read ? '已读' : '未读' }}</dd>
              </div>
            </dl>

            <section
              v-if="notificationFeedback"
              class="operation-feedback operation-feedback--success"
              role="status"
            >
              <Info
                :size="18"
                aria-hidden="true"
              />
              <span>{{ notificationFeedback }}</span>
            </section>

            <div class="detail-actions">
              <button
                class="primary-action"
                type="button"
                :disabled="selectedNotification.read"
                @click="markNotificationRead(selectedNotification)"
              >
                <CheckCheck
                  :size="17"
                  aria-hidden="true"
                />
                标为已读
              </button>
              <RouterLink
                v-if="selectedNotification.taskId"
                class="ghost-action"
                :to="{ name: 'task-detail', params: { taskId: selectedNotification.taskId } }"
              >
                <FileCheck2
                  :size="17"
                  aria-hidden="true"
                />
                查看任务
              </RouterLink>
              <RouterLink
                v-if="selectedNotification.conversationId"
                class="ghost-action"
                :to="{ name: 'conversation-detail', params: { conversationId: selectedNotification.conversationId } }"
              >
                <MessageCircle
                  :size="17"
                  aria-hidden="true"
                />
                打开会话
              </RouterLink>
            </div>
          </template>
        </aside>

        <section class="panel-card notification-metrics-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                处理概览
              </p>
              <h2>今天要留意的事</h2>
            </div>
            <BellDot
              :size="22"
              aria-hidden="true"
            />
          </div>
          <div class="notification-metrics">
            <article
              v-for="metric in notificationMetrics"
              :key="metric.label"
              :class="`notification-metric notification-metric--${metric.tone}`"
            >
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </article>
          </div>
          <p class="panel-copy">
            已读状态会保存在当前浏览器，后端补充专门通知接口后可直接替换为服务端已读状态。
          </p>
        </section>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.fe08-page {
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

.fe08-title-card {
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
  background: linear-gradient(135deg, #e0f2fe, #f0fdfa);
  color: #0369a1;
}

.title-copy {
  display: grid;
  gap: 6px;
}

.title-copy p,
.section-kicker {
  margin: 0;
  color: #0369a1;
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

.section-switch,
.filter-tabs,
.tag-list,
.star-row,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.section-switch {
  padding: 4px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #f8fafc;
}

.section-switch button,
.filter-tabs button,
.tag-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  font-size: 0.9rem;
  font-weight: 800;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.section-switch button.active,
.filter-tabs button.active,
.tag-chip.active {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #0369a1;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.metric-tile,
.notification-metric {
  display: grid;
  gap: 8px;
  min-height: 82px;
  padding: 16px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
}

.metric-tile span,
.notification-metric span {
  color: #64748b;
  font-size: 0.86rem;
  font-weight: 800;
}

.metric-tile strong,
.notification-metric strong {
  color: #0f172a;
  font-size: 1.65rem;
  line-height: 1;
}

.metric-tile--accent,
.notification-metric--accent {
  background: #f0fdfa;
}

.metric-tile--success,
.notification-metric--success {
  background: #f0fdf4;
}

.metric-tile--info,
.notification-metric--info {
  background: #eff6ff;
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

.operation-feedback {
  border-radius: 8px;
}

.operation-feedback--success {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.operation-feedback--danger {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.78fr) minmax(360px, 1.1fr) minmax(280px, 0.82fr);
  gap: 18px;
  align-items: start;
}

.notification-layout {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(320px, 0.72fr);
  grid-template-areas:
    "list detail"
    "list metrics";
  gap: 18px;
  align-items: start;
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

.icon-action,
.ghost-action,
.primary-action,
.star-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  border-radius: 8px;
  font-weight: 900;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.icon-action {
  width: 40px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #334155;
}

.icon-action:hover,
.ghost-action:hover {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #0369a1;
}

.ghost-action {
  min-width: 112px;
  padding: 0 13px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #334155;
  text-decoration: none;
}

.ghost-action--compact {
  min-width: auto;
}

.primary-action {
  min-width: 148px;
  padding: 0 16px;
  border: 1px solid #0369a1;
  background: #0369a1;
  color: #ffffff;
  box-shadow: 0 12px 24px rgba(3, 105, 161, 0.14);
}

.primary-action:hover:not(:disabled) {
  background: #075985;
}

.primary-action:disabled,
.ghost-action:disabled,
.icon-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.loading-state,
.empty-state,
.review-complete-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 210px;
  padding: 22px;
  border: 1px dashed #bfccdc;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  text-align: center;
}

.empty-state--compact {
  min-height: 150px;
}

.empty-state h3,
.review-complete-state strong {
  margin: 0;
  color: #0f172a;
}

.empty-state p,
.review-complete-state p {
  margin: 0;
  line-height: 1.55;
}

.review-task-list,
.notification-list {
  display: grid;
  gap: 10px;
}

.review-task-card,
.notification-card {
  display: grid;
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #0f172a;
  text-align: left;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease;
}

.review-task-card {
  gap: 11px;
  padding: 14px;
}

.review-task-card:hover,
.review-task-card.active,
.notification-card:hover,
.notification-card.active {
  border-color: #93c5fd;
  background: #f8fbff;
  box-shadow: 0 12px 24px rgba(3, 105, 161, 0.09);
}

.review-task-topline,
.review-task-meta,
.review-task-footer,
.notification-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.review-task-topline strong {
  font-size: 0.98rem;
  line-height: 1.35;
}

.review-task-meta,
.review-task-footer,
.notification-meta,
.history-review small,
.field small {
  color: #64748b;
  font-size: 0.82rem;
  line-height: 1.45;
}

.review-context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.review-context div,
.notification-meta-list div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.review-context span,
.notification-meta-list dt {
  color: #64748b;
  font-size: 0.8rem;
  font-weight: 800;
}

.review-context strong,
.notification-meta-list dd {
  margin: 0;
  color: #0f172a;
  font-weight: 900;
  line-height: 1.4;
}

.review-context small {
  color: #475569;
  line-height: 1.45;
}

.review-form {
  display: grid;
  gap: 16px;
}

.rating-field,
.tag-field {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  border: 0;
}

.rating-field legend,
.tag-field legend,
.field span {
  color: #0f172a;
  font-weight: 900;
}

.star-button {
  width: 42px;
  min-height: 42px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #94a3b8;
}

.star-button.active {
  border-color: #fbbf24;
  background: #fffbeb;
  color: #d97706;
}

.field {
  display: grid;
  gap: 8px;
}

.field textarea {
  width: 100%;
  min-height: 126px;
  resize: vertical;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  padding: 12px;
  color: #0f172a;
  line-height: 1.55;
  outline: 0;
}

.field textarea:focus,
.notification-search:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 4px rgba(3, 105, 161, 0.12);
}

.history-review {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.history-review-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.review-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #e0f2fe;
  color: #0369a1;
}

.mini-stars {
  display: flex;
  color: #d97706;
}

.history-review p {
  margin: 0;
  color: #334155;
  line-height: 1.6;
}

.notification-list-panel {
  grid-area: list;
}

.notification-detail-panel {
  grid-area: detail;
}

.notification-metrics-panel {
  grid-area: metrics;
}

.notification-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.notification-search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
}

.notification-search input {
  width: 100%;
  border: 0;
  outline: 0;
  color: #0f172a;
}

.notification-card {
  position: relative;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 13px;
}

.notification-card.unread {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.notification-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
}

.notification-icon--info {
  background: #e0f2fe;
  color: #0369a1;
}

.notification-icon--warning {
  background: #fef3c7;
  color: #b45309;
}

.notification-icon--success {
  background: #dcfce7;
  color: #047857;
}

.notification-icon--danger {
  background: #fee2e2;
  color: #b91c1c;
}

.notification-icon--accent {
  background: #ccfbf1;
  color: #0f766e;
}

.notification-icon--muted {
  background: #e2e8f0;
  color: #475569;
}

.notification-body {
  display: grid;
  gap: 5px;
}

.notification-body > span:not(.notification-card-head):not(.notification-meta) {
  color: #475569;
  line-height: 1.45;
}

.unread-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #0369a1;
}

.notification-detail-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.notification-detail-hero div {
  display: grid;
  gap: 4px;
}

.notification-detail-hero strong {
  color: #0f172a;
}

.notification-detail-hero span {
  color: #64748b;
}

.notification-detail-copy {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

.notification-meta-list,
.notification-metrics {
  display: grid;
  gap: 10px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
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
  .review-layout,
  .notification-layout {
    grid-template-columns: 1fr;
    grid-template-areas: none;
  }

  .notification-list-panel,
  .notification-detail-panel,
  .notification-metrics-panel {
    grid-area: auto;
  }
}

@media (max-width: 760px) {
  .fe08-title-card,
  .metric-strip,
  .review-context,
  .notification-tools {
    grid-template-columns: 1fr;
  }

  .fe08-title-card {
    padding: 18px;
  }

  .section-switch {
    width: 100%;
  }

  .section-switch button {
    flex: 1;
  }

  .review-task-topline,
  .review-task-footer,
  .notification-card-head {
    flex-direction: column;
  }

  .notification-card {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .unread-dot {
    position: absolute;
    top: 14px;
    right: 14px;
  }
}
</style>
