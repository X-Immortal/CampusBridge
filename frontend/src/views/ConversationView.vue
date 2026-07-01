<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Image,
  LoaderCircle,
  MessageCircle,
  MoreVertical,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  UserRound
} from '@lucide/vue'
import { workspaceNavItems } from '../app/navigation'
import DashboardShell from '../components/DashboardShell.vue'
import SectionNav from '../components/layout/SectionNav.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { useNotificationCenter } from '../composables/useNotificationCenter'
import { useToast } from '../composables/useToast'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const { currentUserId } = useAuth()
const { refreshNotificationSummary } = useNotificationCenter()
const { showToast } = useToast()

const categoryMeta = {
  ERRAND: {
    icon: CalendarClock,
    tone: 'blue'
  },
  AGENCY: {
    icon: ClipboardCheck,
    tone: 'orange'
  }
}

const taskCategoryLabels = {
  ERRAND: '跑腿代取',
  AGENCY: '委托代办'
}

const taskStatusLabels = {
  OPEN: '待接单',
  ACCEPTED: '已选人',
  IN_PROGRESS: '进行中',
  PENDING_CONFIRMATION: '待确认'
}

const rewardTypeLabels = {
  FREE: '免费互助',
  POINTS: '积分奖励',
  CASH: '小额酬谢',
  NEGOTIABLE: '双方协商'
}

const conversations = ref([])
const messages = ref([])
const relatedTask = ref(null)
const listLoading = ref(false)
const messagesLoading = ref(false)
const sending = ref(false)
const conversationSearch = ref('')
const threadRef = ref(null)

const conversationPage = reactive({
  page: 1,
  size: 4,
  total: 0
})

const messagePage = reactive({
  page: 1,
  size: 50,
  total: 0
})

const composer = reactive({
  text: ''
})
const composerInputRef = ref(null)

let listRequestId = 0
let messagesRequestId = 0

const selectedConversationId = computed(() =>
  route.params.conversationId ? String(route.params.conversationId) : ''
)
const routeRelatedType = computed(() => normalizeRelatedType(firstQueryValue(route.query.relatedType)))
const routeRelatedId = computed(() => normalizePositiveId(firstQueryValue(route.query.relatedId)))
const routePeerUserId = computed(() => normalizePositiveId(firstQueryValue(route.query.peerUserId)))

const activeConversation = computed(
  () => {
    if (selectedConversationId.value) {
      return (
        conversations.value.find((conversation) => String(conversation.id) === selectedConversationId.value) ||
        null
      )
    }
    return conversations.value[0] || null
  }
)

const currentUser = computed(() => currentUserId.value)
const conversationReplyable = computed(() => activeConversation.value?.replyable ?? true)
const isSystemConversation = computed(() => activeConversation.value?.type === 'SYSTEM')
const onlyInAppActive = computed(() => Boolean(relatedTask.value?.onlyInAppContact))
const totalConversationPages = computed(() =>
  Math.max(1, Math.ceil(conversationPage.total / conversationPage.size))
)

const filteredConversations = computed(() => {
  const keyword = conversationSearch.value.trim().toLowerCase()
  if (!keyword) return conversations.value

  return conversations.value.filter((conversation) => {
    return [
      conversation.title,
      conversation.type,
      conversation.relatedType,
      conversation.relatedId,
      conversation.lastMessagePreview,
      conversation.peerUserId
    ]
      .filter((value) => value !== undefined && value !== null)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  })
})

const chatDateLabel = computed(() => {
  const firstMessage = messages.value[0]
  if (!firstMessage?.createdAt) return '今天'
  return formatDateDivider(firstMessage.createdAt)
})

const activeIcon = computed(() => getCategoryMeta(activeConversation.value?.category).icon)
const activeIconTone = computed(() => `task-icon--${getCategoryMeta(activeConversation.value?.category).tone}`)

function getCategoryMeta(category) {
  return categoryMeta[category] || categoryMeta.ERRAND
}

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function normalizeRelatedType(value) {
  const normalized = String(value || '').trim().toUpperCase()
  return ['POST', 'TASK'].includes(normalized) ? normalized : ''
}

function normalizePositiveId(value) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? String(number) : ''
}

function hasRouteConversationHint() {
  return Boolean(routeRelatedType.value && routeRelatedId.value)
}

function normalizePagedResult(result, fallbackSize) {
  if (Array.isArray(result)) {
    return {
      items: result,
      page: 1,
      size: fallbackSize,
      total: result.length
    }
  }

  if (!result || typeof result !== 'object') {
    return {
      items: [],
      page: 1,
      size: fallbackSize,
      total: 0
    }
  }

  const items = result.items || result.records || result.content || result.list || []
  return {
    items: Array.isArray(items) ? items : [],
    page: Number(result.page || result.current || 1),
    size: Number(result.size || fallbackSize),
    total: Number(result.total || result.totalElements || items.length || 0)
  }
}

function normalizeBoolean(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback
  return value === true || value === 1 || value === '1' || String(value).toLowerCase() === 'true'
}

function normalizeConversation(conversation = {}) {
  const id = conversation.id ?? conversation.conversationId
  const relatedId = conversation.relatedId
  const type = conversation.type || 'TASK'
  const relatedType = conversation.relatedType || (type === 'TASK' ? 'TASK' : type === 'SYSTEM' ? 'SYSTEM' : 'POST')
  const lastMessageAt = conversation.lastMessageAt || conversation.updatedAt || conversation.createdAt || ''
  const lastMessagePreview =
    conversation.lastMessagePreview ||
    conversation.lastMessageContent ||
    conversation.lastMessage ||
    conversation.preview ||
    ''

  return {
    id,
    type,
    title: conversation.title || formatConversationTitle(relatedType, relatedId, type),
    ownerId: conversation.ownerId,
    peerUserId: conversation.peerUserId,
    relatedType,
    relatedId,
    replyable: normalizeBoolean(conversation.replyable, true),
    lastMessageAt,
    createdAt: conversation.createdAt || conversation.lastMessageAt || '',
    lastMessagePreview,
    category: conversation.category || 'ERRAND',
    unreadCount: Number(conversation.unreadCount || 0),
    timeLabel: conversation.timeLabel || formatConversationTime(lastMessageAt)
  }
}

function formatConversationTitle(relatedType, relatedId, type) {
  if (type === 'SYSTEM' || relatedType === 'SYSTEM') return '系统通知'
  if (relatedType === 'POST') return relatedId ? `帖子 #${relatedId} 私聊` : '帖子私聊'
  return relatedId ? `任务 #${relatedId} 沟通` : '站内会话'
}

function normalizeMessage(message = {}) {
  return {
    id: message.id ?? `local-${message.createdAt || Date.now()}`,
    conversationId: message.conversationId ?? activeConversation.value?.id,
    senderId: message.senderId,
    type: message.type || 'TEXT',
    content: message.content || '',
    readAt: message.readAt || null,
    createdAt: message.createdAt || new Date().toISOString()
  }
}

function selectInitialConversation() {
  if (!conversations.value.length) return null
  if (selectedConversationId.value) {
    return conversations.value.find(
      (conversation) => String(conversation.id) === selectedConversationId.value
    )
  }
  if (hasRouteConversationHint()) {
    return findConversationByRouteHint()
  }
  return conversations.value[0]
}

async function loadConversations() {
  listLoading.value = true
  const requestId = ++listRequestId

  try {
    const result = await api.getConversations({
      page: conversationPage.page,
      size: conversationPage.size
    })
    if (requestId !== listRequestId) return

    const paged = normalizePagedResult(result, conversationPage.size)
    await applyConversationPage(paged)
  } catch (error) {
    if (requestId !== listRequestId) return
    showToast(error.message || '会话列表加载失败，请稍后重试。', { type: 'danger' })
    await applyConversationPage({ items: [], page: 1, size: conversationPage.size, total: 0 })
  } finally {
    if (requestId === listRequestId) listLoading.value = false
  }
}

async function applyConversationPage(paged) {
  conversations.value = paged.items.map(normalizeConversation).filter((conversation) => conversation.id)
  conversationPage.page = paged.page || 1
  conversationPage.size = paged.size || conversationPage.size
  conversationPage.total = paged.total || conversations.value.length

  let target = selectInitialConversation()
  if (!target && hasRouteConversationHint()) {
    target = await ensureConversationFromRoute()
  }
  if (!selectedConversationId.value && target?.id) {
    router.replace({
      name: 'conversation-detail',
      params: { conversationId: target.id },
      query: route.query
    })
  }
}

function findConversationByRouteHint() {
  if (!hasRouteConversationHint()) return null
  return conversations.value.find(
    (conversation) =>
      conversation.relatedType === routeRelatedType.value &&
      String(conversation.relatedId) === routeRelatedId.value &&
      (!routePeerUserId.value || String(conversation.peerUserId) === routePeerUserId.value)
  )
}

function resolvePeerUserIdFromTask(task) {
  if (routePeerUserId.value) return routePeerUserId.value
  if (!task || !currentUser.value) return ''

  const requesterId = task.requesterId ?? task.ownerId
  const providerId = task.providerId ?? task.helperId ?? task.acceptedApplicantId

  if (requesterId !== undefined && requesterId !== null && String(requesterId) === String(currentUser.value)) {
    return normalizePositiveId(providerId)
  }
  if (providerId !== undefined && providerId !== null && String(providerId) === String(currentUser.value)) {
    return normalizePositiveId(requesterId)
  }
  return ''
}

async function ensureConversationFromRoute() {
  if (!hasRouteConversationHint() || selectedConversationId.value) return null

  let peerUserId = routePeerUserId.value
  if (!peerUserId && routeRelatedType.value === 'TASK') {
    try {
      const result = await api.getTaskDetail(routeRelatedId.value)
      peerUserId = resolvePeerUserIdFromTask(result?.task || result)
    } catch {
      peerUserId = ''
    }
  }

  if (!peerUserId) {
    showToast('未能确定对话对象，请从任务详情页进入站内沟通。', { type: 'danger' })
    return null
  }

  try {
    const conversation = normalizeConversation(
      await api.createConversation({
        relatedType: routeRelatedType.value,
        relatedId: Number(routeRelatedId.value),
        peerUserId: Number(peerUserId),
        initialMessage: 'hello'
      })
    )
    if (!conversation.id) return null

    conversations.value = [conversation, ...conversations.value]
    conversationPage.total = Math.max(conversationPage.total, conversations.value.length)
    return conversation
  } catch (error) {
    showToast(error.message || '创建会话失败，请稍后重试。', { type: 'danger' })
    return null
  }
}

async function loadMessages() {
  messages.value = []
  if (!activeConversation.value?.id) return

  messagesLoading.value = true
  const requestId = ++messagesRequestId
  const conversationId = activeConversation.value.id

  try {
    const result = await api.getConversationMessages(conversationId, {
      page: messagePage.page,
      size: messagePage.size
    })
    if (requestId !== messagesRequestId) return

    const paged = normalizePagedResult(result, messagePage.size)
    applyMessagePage(paged)
    markConversationLocallyRead(conversationId)
    refreshNotificationSummary()
  } catch {
    if (requestId !== messagesRequestId) return
    applyMessagePage({ items: [], page: 1, size: messagePage.size, total: 0 })
  } finally {
    if (requestId === messagesRequestId) messagesLoading.value = false
  }
}

function applyMessagePage(paged) {
  messages.value = paged.items
    .map(normalizeMessage)
    .sort((current, next) => new Date(current.createdAt) - new Date(next.createdAt))
  messagePage.page = paged.page || 1
  messagePage.size = paged.size || messagePage.size
  messagePage.total = paged.total || messages.value.length
  scrollMessagesToBottom()
}

function markConversationLocallyRead(conversationId) {
  conversations.value = conversations.value.map((conversation) =>
    String(conversation.id) === String(conversationId)
      ? {
          ...conversation,
          unreadCount: 0
        }
      : conversation
  )
}

async function loadRelatedTask() {
  const relatedId = activeConversation.value?.relatedId
  if (!relatedId) {
    relatedTask.value = null
    return
  }

  try {
    const result = await api.getTaskDetail(relatedId)
    relatedTask.value = result?.task || result || null
  } catch {
    relatedTask.value = null
  }
}

async function reloadConversations() {
  await loadConversations()
  await loadMessages()
}

async function changeConversationPage(page) {
  if (page < 1 || page > totalConversationPages.value || page === conversationPage.page) return
  conversationPage.page = page
  await loadConversations()
}

async function selectConversation(conversation) {
  if (!conversation?.id) return
  await router.push({
    name: 'conversation-detail',
    params: { conversationId: conversation.id }
  })
}

function buildOptimisticMessage(payload) {
  return normalizeMessage({
    id: `local-${Date.now()}`,
    conversationId: activeConversation.value?.id,
    senderId: currentUser.value,
    type: payload.type,
    content: payload.content,
    createdAt: new Date().toISOString()
  })
}

function syncConversationAfterSend(message, payload) {
  const index = conversations.value.findIndex(
    (conversation) => String(conversation.id) === String(message.conversationId)
  )
  if (index < 0) return

  conversations.value[index] = {
    ...conversations.value[index],
    lastMessageAt: message.createdAt,
    lastMessagePreview: payload.content,
    timeLabel: formatConversationTime(message.createdAt),
    unreadCount: 0
  }
}

async function sendMessage() {
  const content = composer.text.trim()
  if (!activeConversation.value?.id || sending.value || !conversationReplyable.value || !content) return

  sending.value = true
  const payload = {
    type: 'TEXT',
    content
  }

  try {
    const result = await api.sendConversationMessage(activeConversation.value.id, payload)
    const message = result ? normalizeMessage(result) : buildOptimisticMessage(payload)
    messages.value = [...messages.value, message]
    syncConversationAfterSend(message, payload)
    composer.text = ''
    await scrollMessagesToBottom()
    composerInputRef.value?.focus()
  } catch (error) {
    showToast(error.message || '消息发送失败，请稍后重试。', { type: 'danger' })
  } finally {
    sending.value = false
  }
}

// --- Image upload ---
const composerImageInputRef = ref(null)

function triggerImageUpload() {
  if (isSystemConversation.value || !conversationReplyable.value) return
  composerImageInputRef.value?.click()
}

async function handleImageSelected(event) {
  const file = event.target.files?.[0]
  if (!file) return
  // reset input so same file can be selected again
  event.target.value = ''

  if (!activeConversation.value?.id) return

  // validate file
  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件。', { type: 'danger' })
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    showToast('图片大小不能超过 10MB。', { type: 'danger' })
    return
  }

  sending.value = true
  try {
    // upload attachment
    const attachment = await api.uploadAttachment({
      file,
      expectedOwnerType: 'MESSAGE',
      type: 'IMAGE'
    })
    const attachmentId = attachment?.id || attachment?.attachmentId
    // send image message
    const payload = {
      type: 'IMAGE',
      content: attachment?.url || attachment?.fileUrl || '',
      attachmentIds: attachmentId ? [attachmentId] : []
    }
    const result = await api.sendConversationMessage(activeConversation.value.id, payload)
    const message = result ? normalizeMessage(result) : buildOptimisticMessage(payload)
    messages.value = [...messages.value, message]
    syncConversationAfterSend(message, payload)
    await scrollMessagesToBottom()
  } catch (error) {
    showToast(error.message || '图片发送失败，请稍后重试。', { type: 'danger' })
  } finally {
    sending.value = false
  }
}

async function scrollMessagesToBottom() {
  await nextTick()
  if (threadRef.value) {
    threadRef.value.scrollTop = threadRef.value.scrollHeight
  }
}

function formatConversationTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatMessageTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatDateDivider(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '今天'
  return `今天 ${date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })}`
}

function isOwnMessage(message) {
  return String(message.senderId) === String(currentUser.value)
}

function isImageMessage(message) {
  return message.type === 'IMAGE'
}

function conversationSubtitle(conversation) {
  if (!conversation) return ''
  const taskNo = conversation.relatedId ? `任务 #${conversation.relatedId}` : '站内沟通'
  return `${taskNo} · 围绕任务的站内沟通留痕`
}

function categoryLabel(category) {
  return taskCategoryLabels[category] || '任务会话'
}

function taskStatusLabel(status) {
  return taskStatusLabels[status] || status || '同步中'
}

function rewardTypeLabel(type) {
  return rewardTypeLabels[type] || type || '未设置'
}

watch(
  () => route.params.conversationId,
  async () => {
    loadRelatedTask()
    await loadMessages()
  }
)

watch(activeConversation, loadRelatedTask)

onMounted(async () => {
  await loadConversations()
  loadRelatedTask()
  await loadMessages()
})
</script>

<template>
  <DashboardShell active-label="站内沟通">
    <main class="conversation-page">
      <section class="page-title-card">
        <div class="title-icon">
          <MessageCircle
            :size="34"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>站内沟通</p>
          <h1>把协作细节留在平台内</h1>
          <span>围绕已确认的任务会话，完成文字澄清、图片补充和可追溯沟通。</span>
        </div>
        <StatusBadge
          label="仅站内优先"
          tone="success"
        />
      </section>

      <SectionNav
        class="conversation-section-nav"
        :items="workspaceNavItems"
        :active-key="route.meta?.subSection"
        variant="compact"
        aria-label="工作台二级导航"
      />

      <section class="conversation-workspace">
        <aside class="conversation-list-panel">
          <div class="panel-heading">
            <div>
              <p>会话列表</p>
              <h2>正在协作</h2>
            </div>
            <button
              class="icon-button"
              type="button"
              aria-label="刷新会话"
              :disabled="listLoading"
              @click="reloadConversations"
            >
              <RefreshCw
                :class="{ spinning: listLoading }"
                :size="20"
                aria-hidden="true"
              />
            </button>
          </div>

          <label class="conversation-search">
            <Search
              :size="18"
              aria-hidden="true"
            />
            <input
              v-model="conversationSearch"
              type="search"
              placeholder="搜索任务标题、内容或关键词"
              aria-label="搜索会话、任务编号"
            >
          </label>

          <div class="pagination-bar">
            <button
              class="pager-button"
              type="button"
              :disabled="conversationPage.page <= 1"
              @click="changeConversationPage(conversationPage.page - 1)"
            >
              <ChevronLeft
                :size="16"
                aria-hidden="true"
              />
              上一页
            </button>
            <span>{{ conversationPage.page }} / {{ totalConversationPages }}</span>
            <button
              class="pager-button"
              type="button"
              :disabled="conversationPage.page >= totalConversationPages"
              @click="changeConversationPage(conversationPage.page + 1)"
            >
              下一页
              <ChevronRight
                :size="16"
                aria-hidden="true"
              />
            </button>
          </div>

          <div
            v-if="listLoading && !conversations.length"
            class="loading-state"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="24"
              aria-hidden="true"
            />
            <span>正在加载会话...</span>
          </div>

          <div
            v-else
            class="conversation-list"
          >
            <button
              v-for="conversation in filteredConversations"
              :key="conversation.id"
              class="conversation-card"
              :class="{ active: String(conversation.id) === String(activeConversation?.id) }"
              type="button"
              @click="selectConversation(conversation)"
            >
              <span
                class="task-icon"
                :class="`task-icon--${getCategoryMeta(conversation.category).tone}`"
              >
                <component
                  :is="getCategoryMeta(conversation.category).icon"
                  :size="24"
                  aria-hidden="true"
                />
              </span>
              <span class="conversation-card-body">
                <span class="conversation-card-top">
                  <strong>{{ conversation.title }}</strong>
                  <small>{{ conversation.timeLabel || formatConversationTime(conversation.lastMessageAt) }}</small>
                </span>
                <span class="conversation-preview">{{ conversation.lastMessagePreview }}</span>
                <span class="conversation-meta">任务 #{{ conversation.relatedId }}</span>
              </span>
              <span
                v-if="conversation.unreadCount"
                class="unread-dot"
                aria-label="有未读消息"
              />
            </button>
          </div>
        </aside>

        <section
          v-if="activeConversation"
          class="chat-panel"
        >
          <header class="chat-header">
            <div class="chat-title">
              <span
                class="task-icon"
                :class="activeIconTone"
              >
                <component
                  :is="activeIcon"
                  :size="25"
                  aria-hidden="true"
                />
              </span>
              <div>
                <h2>{{ activeConversation.title }}</h2>
                <p>{{ conversationSubtitle(activeConversation) }}</p>
              </div>
            </div>
            <button
              class="icon-button"
              type="button"
              aria-label="更多会话操作"
            >
              <MoreVertical
                :size="22"
                aria-hidden="true"
              />
            </button>
          </header>

          <div class="date-divider">
            <span>{{ chatDateLabel }}</span>
          </div>

          <div
            ref="threadRef"
            class="message-thread"
            :aria-busy="messagesLoading"
          >
            <div
              v-if="messagesLoading && !messages.length"
              class="loading-state message-loading"
              role="status"
            >
              <LoaderCircle
                class="spinning"
                :size="24"
                aria-hidden="true"
              />
              <span>正在加载消息...</span>
            </div>

            <article
              v-for="message in messages"
              v-else
              :key="message.id"
              class="message-row"
              :class="{ 'message-row--mine': isOwnMessage(message) }"
            >
              <span
                v-if="!isOwnMessage(message)"
                class="message-avatar"
              >
                <UserRound
                  :size="24"
                  aria-hidden="true"
                />
              </span>
              <time>{{ formatMessageTime(message.createdAt) }}</time>
              <div class="message-bubble">
                <a
                  v-if="isImageMessage(message)"
                  class="message-image"
                  :href="message.content"
                  target="_blank"
                  rel="noreferrer"
                >
                  <img
                    :src="message.content"
                    alt="消息图片"
                  >
                </a>
                <p v-else>
                  {{ message.content }}
                </p>
              </div>
              <span
                v-if="isOwnMessage(message)"
                class="message-avatar message-avatar--mine"
              >
                <UserRound
                  :size="24"
                  aria-hidden="true"
                />
              </span>
            </article>
          </div>

          <form
            class="message-composer"
            @submit.prevent="sendMessage"
          >
            <input
              ref="composerInputRef"
              v-model="composer.text"
              type="text"
              :placeholder="isSystemConversation ? '系统消息无需回复' : '输入消息...'"
              aria-label="输入消息"
              :disabled="sending || !conversationReplyable || isSystemConversation"
              :class="{ 'composer-input--system': isSystemConversation }"
            >
            <input
              ref="composerImageInputRef"
              type="file"
              accept="image/*"
              style="display: none;"
              @change="handleImageSelected"
            >
            <button
              class="image-button"
              type="button"
              aria-label="发送图片"
              :disabled="sending || isSystemConversation || !conversationReplyable"
              @click="triggerImageUpload"
            >
              <Image
                :size="24"
                aria-hidden="true"
              />
            </button>
            <button
              class="send-button"
              type="submit"
              aria-label="发送消息"
              :disabled="sending || !composer.text.trim() || !conversationReplyable || isSystemConversation"
            >
              <Send
                :size="25"
                aria-hidden="true"
              />
            </button>
          </form>
        </section>

        <aside
          v-if="relatedTask"
          class="task-side-panel"
        >
          <div class="panel-heading">
            <div>
              <p>关联任务</p>
              <h2>{{ relatedTask.title }}</h2>
            </div>
            <StatusBadge
              :label="taskStatusLabel(relatedTask.status)"
              tone="info"
            />
          </div>
          <dl class="task-detail-list">
            <div>
              <dt>任务类型</dt>
              <dd>{{ categoryLabel(relatedTask.category) }}</dd>
            </div>
            <div>
              <dt>校区地点</dt>
              <dd>{{ relatedTask.campus }} · {{ relatedTask.location }}</dd>
            </div>
            <div>
              <dt>奖励方式</dt>
              <dd>{{ rewardTypeLabel(relatedTask.rewardType) }}</dd>
            </div>
            <div>
              <dt>完成标准</dt>
              <dd>{{ relatedTask.completionStandard }}</dd>
            </div>
          </dl>
          <div class="mode-note">
            <ShieldCheck
              :size="18"
              aria-hidden="true"
            />
            <span>{{ onlyInAppActive ? '当前任务开启仅站内沟通' : '当前任务允许按偏好协商沟通方式' }}</span>
          </div>
        </aside>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.conversation-page {
  --chat-primary: var(--color-brand);
  --chat-primary-dark: var(--color-brand-hover);
  --chat-text: var(--color-text);
  --chat-muted: var(--color-text-muted);
  --chat-border: var(--color-border);
  --chat-soft: var(--color-brand-soft);
  display: grid;
  gap: 18px;
  color: var(--chat-text);
}

.page-title-card,
.conversation-list-panel,
.chat-panel,
.task-side-panel {
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.page-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  min-height: 132px;
  gap: 28px;
  padding: 26px 38px;
}

.title-icon,
.task-icon,
.message-avatar {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
}

.title-icon {
  width: 78px;
  height: 82px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand-text);
}

.title-copy {
  display: grid;
  gap: 8px;
}

.title-copy p,
.panel-heading p {
  margin: 0;
  color: var(--color-brand-text);
  font-size: 0.92rem;
  font-weight: 900;
}

.title-copy h1,
.panel-heading h2,
.chat-title h2 {
  margin: 0;
  color: var(--chat-text);
  font-weight: 900;
  line-height: 1.16;
}

.title-copy h1 {
  font-size: 1.9rem;
}

.title-copy span,
.chat-title p,
.conversation-preview,
.conversation-meta,
.task-detail-list dt {
  color: var(--chat-muted);
  line-height: 1.5;
}

.page-title-card :deep(.status-badge--success) {
  min-height: 32px;
  padding: 0 13px;
  border: 1px solid var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
  font-size: 0.86rem;
  font-weight: 900;
  box-shadow: var(--shadow-card);
}

.conversation-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 0.34fr) minmax(520px, 1fr);
  gap: 14px;
  align-items: stretch;
}

.conversation-list-panel,
.chat-panel,
.task-side-panel {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 18px;
}

.conversation-list-panel {
  min-height: 660px;
}

.panel-heading,
.chat-header,
.conversation-card-top,
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-heading h2 {
  margin-top: 3px;
  font-size: 1.2rem;
}

.icon-button,
.pager-button,
.image-button,
.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--chat-primary);
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.icon-button {
  width: 42px;
  height: 42px;
}

.icon-button:hover:not(:disabled),
.pager-button:hover:not(:disabled),
.image-button:hover:not(:disabled) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  box-shadow: 0 10px 20px var(--shadow-card);
}

.icon-button:disabled,
.pager-button:disabled,
.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.conversation-search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 12px;
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
}

.conversation-search:focus-within,
.message-composer input:focus,
.icon-button:focus-visible,
.pager-button:focus-visible,
.conversation-card:focus-visible,
.image-button:focus-visible,
.send-button:focus-visible {
  border-color: var(--color-brand-border);
  box-shadow: 0 0 0 4px var(--shadow-focus);
  outline: 0;
}

.conversation-search input,
.message-composer input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--chat-text);
}

.conversation-search input::placeholder,
.message-composer input::placeholder {
  color: var(--color-text-muted);
}

.composer-input--system::placeholder {
  color: var(--color-text);
}

.conversation-list {
  display: grid;
  gap: 11px;
}

.conversation-card {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  min-height: 104px;
  padding: 14px 13px;
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: inherit;
  text-align: left;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.conversation-card:hover,
.conversation-card.active {
  border-color: var(--color-brand-border);
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-surface));
  box-shadow: 0 14px 30px var(--shadow-card);
}

.conversation-card.active::before {
  content: "";
  position: absolute;
  inset: 14px auto 14px 0;
  width: 3px;
  border-radius: 999px;
  background: var(--chat-primary);
}

.task-icon {
  width: 52px;
  height: 52px;
  border-radius: 8px;
}

.task-icon--blue {
  background: var(--color-brand-soft);
  color: var(--chat-primary);
}

.task-icon--orange {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.task-icon--green {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.conversation-card-body {
  display: grid;
  gap: 7px;
}

.conversation-card-top strong {
  color: var(--chat-text);
  font-size: 1rem;
  line-height: 1.3;
}

.conversation-card-top small {
  flex: 0 0 auto;
  color: var(--chat-primary);
  font-size: 0.88rem;
}

.conversation-preview,
.conversation-meta {
  font-size: 0.9rem;
}

.conversation-meta {
  color: var(--color-text-secondary);
}

.unread-dot {
  position: absolute;
  right: 14px;
  bottom: 18px;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--chat-primary);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.pagination-bar {
  color: var(--chat-text);
  font-weight: 800;
}

.pager-button {
  gap: 6px;
  min-height: 40px;
  padding: 0 14px;
  font-weight: 900;
}

.chat-panel {
  min-height: 660px;
}

.chat-header {
  padding: 5px 0 12px;
  border-bottom: 1px dashed var(--chat-border);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chat-title h2 {
  font-size: 1.2rem;
}

.chat-title p {
  margin: 5px 0 0;
  font-size: 0.92rem;
}

.date-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.date-divider::before,
.date-divider::after {
  content: "";
  height: 1px;
  flex: 1;
  border-top: 1px dashed var(--chat-border);
}

.date-divider span {
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--color-surface-muted);
}

.message-thread {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 430px;
  max-height: 52vh;
  padding: 12px 4px;
  overflow-y: auto;
}

.message-row {
  display: grid;
  grid-template-columns: 52px auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.message-row--mine {
  grid-template-columns: minmax(0, 1fr) auto 52px;
}

.message-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand-text);
}

.message-avatar--mine {
  background: linear-gradient(135deg, var(--color-brand-border-hover), var(--color-brand-hover));
  color: var(--color-surface);
}

.message-row time {
  color: var(--color-text-muted);
  font-size: 0.88rem;
}

.message-row--mine .message-bubble {
  order: 1;
  justify-self: end;
}

.message-row--mine time {
  order: 2;
}

.message-row--mine .message-avatar {
  order: 3;
}

.message-bubble {
  width: fit-content;
  max-width: min(520px, 100%);
  padding: 12px 16px;
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 10px 22px var(--shadow-card);
}

.message-row--mine .message-bubble {
  border-color: var(--color-border);
  background: var(--color-brand-soft);
}

.message-bubble p {
  margin: 0;
  color: var(--chat-text);
  line-height: 1.65;
  white-space: pre-wrap;
}

.message-image {
  display: block;
  overflow: hidden;
  width: min(300px, 100%);
  border-radius: 8px;
  background: var(--color-bg);
}

.message-image img {
  display: block;
  width: 100%;
  height: 126px;
  object-fit: cover;
}

.message-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 58px 66px;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.message-composer input {
  min-height: 48px;
  padding: 0 2px;
  font-size: 0.96rem;
}

.image-button,
.send-button {
  height: 48px;
}

.send-button {
  border-color: var(--chat-primary);
  background: var(--chat-primary);
  color: var(--color-surface);
  box-shadow: 0 14px 24px var(--shadow-button-hover);
}

.send-button:hover:not(:disabled) {
  background: var(--chat-primary-dark);
}

.task-side-panel {
  display: none;
}

.task-detail-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.task-detail-list div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--chat-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.task-detail-list dd {
  margin: 0;
  color: var(--chat-text);
  font-weight: 900;
  line-height: 1.45;
}

.mode-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 12px;
  border: 1px solid var(--color-success-soft);
  border-radius: 8px;
  background: var(--color-success-bg);
  color: var(--color-success-text);
  font-weight: 900;
  line-height: 1.45;
}

.loading-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 160px;
  color: var(--chat-primary);
  font-weight: 900;
}

.message-loading {
  min-height: 360px;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (min-width: 1440px) {
  .conversation-workspace {
    grid-template-columns: minmax(310px, 370px) minmax(0, 1fr);
  }
}

@media (max-width: 1280px) {
  .conversation-workspace {
    grid-template-columns: minmax(280px, 0.4fr) minmax(0, 1fr);
  }
}

@media (max-width: 1020px) {
  .conversation-workspace {
    grid-template-columns: 1fr;
  }

  .conversation-list-panel,
  .chat-panel {
    min-height: auto;
  }

  .conversation-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-side-panel {
    display: grid;
  }
}

@media (max-width: 720px) {
  .page-title-card {
    grid-template-columns: 1fr;
    padding: 20px;
  }

  .conversation-list {
    grid-template-columns: 1fr;
  }

  .message-row,
  .message-row--mine {
    grid-template-columns: 44px minmax(0, 1fr);
    align-items: end;
  }

  .message-row time,
  .message-row--mine time {
    grid-column: 2;
    order: initial;
  }

  .message-row--mine .message-avatar {
    grid-column: 1;
    grid-row: 1 / span 2;
    order: initial;
  }

  .message-row--mine .message-bubble {
    grid-column: 2;
    justify-self: start;
    order: initial;
  }

  .message-composer {
    grid-template-columns: minmax(0, 1fr) 50px 54px;
  }

  .pager-button {
    padding-inline: 10px;
  }
}

.conversation-section-nav {
  margin-bottom: 4px;
}
</style>
