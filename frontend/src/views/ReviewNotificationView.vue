<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Bell,
  Check,
  CheckCheck,
  CheckCircle2,
  ClipboardCheck,
  FileCheck2,
  FileText,
  Inbox,
  LoaderCircle,
  RefreshCw,
  Search,
  Sparkles,
  Star,
  UserRound
} from '@lucide/vue'
import { workspaceNavItems } from '../app/navigation'
import DashboardShell from '../components/DashboardShell.vue'
import ModuleTopbar from '../components/layout/ModuleTopbar.vue'
import ModuleEntryCard from '../components/layout/ModuleEntryCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { useNotificationCenter } from '../composables/useNotificationCenter'
import { useToast } from '../composables/useToast'
import { optionLabel, TASK_CATEGORY_OPTIONS, TASK_STATUS_OPTIONS, statusTone } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const { currentUserId } = useAuth()
const { showToast } = useToast()
const {
  markConversationRead: markConversationReadOnServer,
  markAllNotificationsRead: markAllNotificationsReadOnServer,
  replaceNotificationSummary,
  refreshNotificationSummary
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
  { value: 'POST', label: '帖子' },
  { value: 'REVIEW', label: '评价' },
  { value: 'CREDIT', label: '信用' }
]

const activeSection = computed(() =>
  ['notifications', 'workspace-notifications'].includes(route.name) ? 'notifications' : 'reviews'
)
const activeLabel = computed(() => (activeSection.value === 'notifications' ? '消息中心' : '评价中心'))
const currentUser = computed(() => currentUserId.value)

const tasks = ref([])
const notifications = ref([])
const selectedTaskId = ref('')
const selectedNotificationId = ref('')
const loadingReviews = ref(false)
const loadingNotifications = ref(false)
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

const decoratedNotifications = computed(() =>
  notifications.value
    .map((notification) => ({
      ...notification,
      read: Number(notification.unreadCount || 0) <= 0
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
      [notification.title, notification.content, notification.source, notification.taskId, notification.postId]
        .filter((value) => value !== undefined && value !== null)
        .join(' ')
        .toLowerCase()
        .includes(keyword)

    return matchesFilter && matchesKeyword
  })
})

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

function resetReviewForm() {
  reviewForm.rating = 5
  reviewForm.comment = ''
  reviewForm.tags = []
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
  try {
    const [completedResult, reviewedResult] = await Promise.allSettled([
      api.getTasks({ status: 'COMPLETED', page: 1, size: 12 }),
      api.getTasks({ status: 'REVIEWED', page: 1, size: 12 })
    ])
    const rawTasks = [
      ...(completedResult.status === 'fulfilled' ? normalizePagedItems(completedResult.value) : []),
      ...(reviewedResult.status === 'fulfilled' ? normalizePagedItems(reviewedResult.value) : [])
    ]
    const myId = currentUserId.value
    const uniqueTasks = [...new Map(rawTasks.map((task) => [String(task.id ?? task.taskId), task])).values()]
      .filter((task) => task.id || task.taskId)
      .map(normalizeTask)
      .filter((task) =>
        String(task.requesterId) === String(myId) || String(task.providerId) === String(myId))

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
    showToast('请至少写 8 个字，说明本次协作是否符合预期。', { type: 'danger' })
    return
  }
  if (comment.length > 500) {
    showToast('文字评价最多 500 个字。', { type: 'danger' })
    return
  }

  loadingReviews.value = true
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
    showToast('评价已提交，后续会参与信用记录计算。')
    resetReviewForm()
  } catch (error) {
    showToast(error.message || '评价提交失败，请稍后重试。', { type: 'danger' })
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
  if (conversation.type !== 'SYSTEM') return null
  const conversationId = conversation.id || conversation.conversationId
  if (!conversationId) return null
  const id = `conversation-${conversationId}`
  const unreadCount = Number(conversation.unreadCount || 0)
  const content =
    conversation.lastMessagePreview ||
    conversation.lastMessageContent ||
    conversation.lastMessage ||
    conversation.preview ||
    '有新的站内协作动态。'
  return {
    id,
    type: conversation.relatedType === 'POST' ? 'POST' : inferNotificationType(content),
    tone: unreadCount > 0 ? inferNotificationTone(content) : 'muted',
    title: inferNotificationTitle(content, conversation.title || '站内协作动态'),
    content,
    source: conversation.relatedType === 'POST' ? '帖子通知' : '任务通知',
    taskId: conversation.relatedType === 'TASK' ? conversation.relatedId : null,
    postId: conversation.relatedType === 'POST' ? conversation.relatedId : null,
    conversationId,
    createdAt: conversation.lastMessageAt || conversation.updatedAt || conversation.createdAt || new Date().toISOString(),
    unreadCount,
    readAt: unreadCount > 0 ? null : conversation.lastReadAt || conversation.readAt || conversation.lastMessageAt
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

function notificationTypeLabel(type) {
  if (type === 'REVIEW') return '评价提醒'
  if (type === 'CREDIT') return '信用动态'
  if (type === 'TASK') return '任务动态'
  return '系统通知'
}

function relatedContentLabel(notification) {
  if (notification?.taskId) return '关联任务'
  if (notification?.postId) return '关联帖子'
  return '系统消息'
}

function applyConversationRead(conversationId) {
  const readAt = new Date().toISOString()
  notifications.value = notifications.value.map((notification) =>
    String(notification.conversationId) === String(conversationId)
      ? {
          ...notification,
          unreadCount: 0,
          readAt,
          tone: notification.tone === 'info' ? 'muted' : notification.tone
        }
      : notification
  )
}

async function loadNotifications() {
  loadingNotifications.value = true
  const requestId = ++notificationRequestId

  try {
    const result = await api.getConversations({ page: 1, size: 100 })
    if (requestId !== notificationRequestId) return
    const conversations = normalizePagedItems(result)
    replaceNotificationSummary(conversations)
    notifications.value = conversations
      .filter((conversation) => conversation.type === 'SYSTEM')
      .map(normalizeNotificationFromConversation)
      .filter(Boolean)
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

async function markNotificationRead(notification) {
  if (!notification?.id || notification.read) return
  try {
    await markConversationReadOnServer(notification.conversationId)
    applyConversationRead(notification.conversationId)
    showToast('已标记为已读。')
  } catch (error) {
    showToast(error.message || '标记已读失败，请稍后重试。', { type: 'danger' })
  }
}

async function markAllNotificationsRead() {
  try {
    await markAllNotificationsReadOnServer()
    const readAt = new Date().toISOString()
    notifications.value = notifications.value.map((notification) => ({
      ...notification,
      unreadCount: 0,
      readAt,
      tone: notification.tone === 'info' ? 'muted' : notification.tone
    }))
    showToast('全部通知已标记为已读。')
  } catch (error) {
    showToast(error.message || '全部已读失败，请稍后重试。', { type: 'danger' })
  }
}

function notificationIcon(type) {
  if (type === 'POST') return FileText
  if (type === 'REVIEW') return Star
  if (type === 'CREDIT') return Sparkles
  return ClipboardCheck
}

function notificationToneClass(notification) {
  return `notification-icon--${notification.tone || 'info'}`
}

watch(currentUser, () => {
  refreshNotificationSummary()
})

onMounted(async () => {
  await Promise.all([loadReviews(), loadNotifications()])
})
</script>

<template>
  <DashboardShell :active-label="activeLabel">
    <main
      class="fe08-page"
      :class="{ 'fe08-page--notifications': activeSection === 'notifications' }"
    >
      <ModuleTopbar
        eyebrow="工作台"
        :title="activeSection === 'notifications' ? '通知中心' : '评价记录'"
        :description="activeSection === 'notifications'
          ? '系统通知与未读消息。'
          : '查看评价与信用。'"
      >
        <ModuleEntryCard
          v-for="item in workspaceNavItems"
          :key="item.key"
          :to="item.to"
          :icon="item.icon"
          :title="item.label"
          :description="item.description"
          :active="route.meta?.subSection === item.key"
        />
      </ModuleTopbar>

      <section
        v-if="activeSection === 'reviews'"
        class="metric-strip"
        aria-label="评价概览"
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
          <span>已评价任务</span>
          <strong>{{ reviewedTasks.length }}</strong>
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
            <Inbox
              :size="30"
              aria-hidden="true"
            />
            <h3>暂无已完成任务</h3>
            <p>你发布或承接的任务完成后会出现在这里，双方可互相评价。</p>
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

          <div
            v-if="!selectedTask"
            class="empty-state"
          >
            <Star
              :size="30"
              aria-hidden="true"
            />
            <h3>选择任务后填写评价</h3>
            <p>从左侧互评任务列表中选择一次已完成协作。</p>
          </div>

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
              :disabled="!unreadNotifications.length || loadingNotifications"
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
                placeholder="搜索任务标题、内容或关键词"
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
                <span class="notification-meta">
                  {{ notification.source }} · {{ relatedContentLabel(notification) }}
                </span>
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
                <dd>{{ notificationTypeLabel(selectedNotification.type) }}</dd>
              </div>
              <div>
                <dt>关联内容</dt>
                <dd>{{ relatedContentLabel(selectedNotification) }}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd>{{ selectedNotification.read ? '已读' : '未读' }}</dd>
              </div>
            </dl>

            <div class="detail-actions">
              <button
                class="primary-action"
                type="button"
                :disabled="selectedNotification.read || loadingNotifications"
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
                :to="{ name: 'workspace-task-detail', params: { taskId: selectedNotification.taskId } }"
              >
                <FileCheck2
                  :size="17"
                  aria-hidden="true"
                />
                查看任务
              </RouterLink>
              <RouterLink
                v-if="selectedNotification.postId"
                class="ghost-action"
                :to="{ name: 'post-list', query: { postId: selectedNotification.postId } }"
              >
                <FileText
                  :size="17"
                  aria-hidden="true"
                />
                查看帖子
              </RouterLink>
            </div>
          </template>
        </aside>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.fe08-page {
  display: grid;
  gap: 20px;
  align-items: start;
}

.panel-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--shadow-card) 72%, transparent);
}

.filter-tabs,
.tag-list,
.star-row,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.filter-tabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  font-weight: 800;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.filter-tabs button.active {
  border-color: var(--color-info-soft);
  background: var(--color-brand-soft);
  color: var(--color-info);
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  font-weight: 800;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.tag-chip:hover {
  border-color: var(--color-border);
  background: var(--color-brand-soft);
}

.tag-chip.active {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
  box-shadow: 0 4px 12px var(--shadow-card);
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}

.metric-tile {
  display: grid;
  gap: 6px;
  min-height: 64px;
  padding: 14px 16px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
}

.metric-tile span {
  color: var(--color-text-muted);
  font-size: 0.86rem;
  font-weight: 800;
}

.metric-tile strong {
  color: var(--color-text);
  font-size: 1.45rem;
  line-height: 1;
}

.metric-tile--accent {
  border-color: var(--color-accent-bg);
}

.metric-tile--success {
  border-color: var(--color-success-bg);
}

.metric-tile--info {
  border-color: var(--color-brand-border);
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.74fr) minmax(430px, 1.26fr);
  gap: 18px;
  align-items: start;
}

.review-history-panel {
  grid-column: 1 / -1;
}

.notification-layout {
  display: grid;
  grid-template-columns: minmax(380px, 0.92fr) minmax(360px, 1.08fr);
  grid-template-areas:
    "list detail";
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
  border: 1px solid var(--color-divider);
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.icon-action:hover,
.ghost-action:hover {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-info);
}

.ghost-action {
  min-width: 112px;
  padding: 0 13px;
  border: 1px solid var(--color-divider);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  text-decoration: none;
}

.ghost-action--compact {
  min-width: auto;
}

.primary-action {
  min-width: 148px;
  padding: 0 16px;
  border: 1px solid var(--color-info);
  background: var(--color-info);
  color: var(--color-surface);
  box-shadow: var(--shadow-button);
}

.primary-action:hover:not(:disabled) {
  background: var(--color-info-text);
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
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state--compact {
  min-height: 150px;
}

.empty-state h3,
.review-complete-state strong {
  margin: 0;
  color: var(--color-text);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
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
  border-color: var(--color-brand-border-hover);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
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
  color: var(--color-text-muted);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.review-context span,
.notification-meta-list dt {
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 800;
}

.review-context strong,
.notification-meta-list dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 900;
  line-height: 1.4;
}

.review-context small {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.review-form {
  display: grid;
  gap: 16px;
}

.rating-field,
.tag-field {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.rating-field legend,
.tag-field legend,
.field span {
  color: var(--color-text);
  font-weight: 900;
}

.star-button {
  width: 42px;
  min-height: 42px;
  border: 1px solid var(--color-divider);
  background: var(--color-surface);
  color: var(--color-text-muted);
}

.star-button.active {
  border-color: var(--color-warning);
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.field {
  display: grid;
  gap: 8px;
}

.field textarea {
  width: 100%;
  min-height: 126px;
  resize: vertical;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  padding: 12px;
  color: var(--color-text);
  line-height: 1.55;
  outline: 0;
}

.field textarea:focus,
.notification-search:focus-within {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.history-review {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.history-review-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.history-review-head > div {
  display: grid;
  gap: 2px;
}

.review-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--color-info-bg);
  color: var(--color-info);
}

.mini-stars {
  display: flex;
  color: var(--color-warning-text);
}

.history-review p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.notification-list-panel {
  grid-area: list;
}

.notification-detail-panel {
  grid-area: detail;
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
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
}

.notification-search input {
  width: 100%;
  border: 0;
  outline: 0;
  color: var(--color-text);
}

.notification-card {
  position: relative;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 13px;
}

.notification-card.unread {
  border-color: var(--color-info-soft);
  background: var(--color-surface);
}

.notification-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
}

.notification-icon--info {
  background: var(--color-info-bg);
  color: var(--color-info);
}

.notification-icon--warning {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.notification-icon--success {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.notification-icon--danger {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.notification-icon--accent {
  background: var(--color-accent-bg);
  color: var(--color-accent-text);
}

.notification-icon--muted {
  background: var(--color-border);
  color: var(--color-text-muted);
}

.notification-body {
  display: grid;
  gap: 5px;
}

.notification-body > span:not(.notification-card-head):not(.notification-meta) {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.unread-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-info);
}

.notification-detail-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.notification-detail-hero div {
  display: grid;
  gap: 4px;
}

.notification-detail-hero strong {
  color: var(--color-text);
}

.notification-detail-hero span {
  color: var(--color-text-muted);
}

.notification-detail-copy {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.notification-meta-list {
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
  .notification-detail-panel {
    grid-area: auto;
  }
}

@media (max-width: 760px) {
  .metric-strip,
  .review-context,
  .notification-tools {
    grid-template-columns: 1fr;
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
