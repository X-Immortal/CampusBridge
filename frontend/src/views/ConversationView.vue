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
  RefreshCw,
  Search,
  Send,
  ShieldCheck
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
const { currentUserId, state } = useAuth()
const { refreshNotificationSummary } = useNotificationCenter()
const { showToast } = useToast()

const categoryMeta = {
  ERRAND: {
    icon: CalendarClock,
    tone: 'blue',
    label: '跑腿代取'
  },
  AGENCY: {
    icon: ClipboardCheck,
    tone: 'orange',
    label: '委托代办'
  },
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
  CASH: '现金酬谢',
  NEGOTIABLE: '双方协商'
}

const conversations = ref([])
const messages = ref([])
const relatedTask = ref(null)
const listLoading = ref(false)
const messagesLoading = ref(false)
const sending = ref(false)
const conversationSearch = ref('')
const conversationProfiles = ref({})
const failedAvatarKeys = ref({})
const threadRef = ref(null)
const composerInputRef = ref(null)
const composerImageInputRef = ref(null)

const conversationPage = reactive({
  page: 1,
  size: 8,
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

let listRequestId = 0
let messagesRequestId = 0

const selectedConversationId = computed(() => (route.params.conversationId ? String(route.params.conversationId) : ''))
const routeRelatedType = computed(() => normalizeRelatedType(firstQueryValue(route.query.relatedType)))
const routeRelatedId = computed(() => normalizePositiveId(firstQueryValue(route.query.relatedId)))
const routePeerUserId = computed(() => normalizePositiveId(firstQueryValue(route.query.peerUserId)))
const currentUser = computed(() => currentUserId.value)

const activeConversation = computed(() => {
  if (selectedConversationId.value) {
    return conversations.value.find((conversation) => String(conversation.id) === selectedConversationId.value) || null
  }
  return conversations.value[0] || null
})

const activeConversationMeta = computed(() => getConversationMeta(activeConversation.value))
const conversationReplyable = computed(() => activeConversation.value?.replyable ?? true)
const isSystemConversation = computed(() => activeConversation.value?.type === 'SYSTEM')
const onlyInAppActive = computed(() => Boolean(relatedTask.value?.onlyInAppContact))
const totalConversationPages = computed(() => Math.max(1, Math.ceil(conversationPage.total / conversationPage.size)))
const currentUserProfile = computed(() => ({
  nickname: state.profile?.nickname || state.user?.username || '我',
  avatarUrl: state.profile?.avatarUrl || ''
}))
const unreadConversationCount = computed(() =>
  conversations.value.reduce((sum, conversation) => sum + Number(conversation.unreadCount || 0), 0)
)

const filteredConversations = computed(() => {
  const keyword = conversationSearch.value.trim().toLowerCase()
  if (!keyword) return sortConversationsForDisplay(conversations.value)

  const matchedConversations = conversations.value.filter((conversation) =>
    [
      conversation.title,
      conversation.relatedType,
      conversation.relatedId,
      conversation.lastMessagePreview,
      conversation.peerUserId
    ]
      .filter((value) => value !== undefined && value !== null)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  )
  return sortConversationsForDisplay(matchedConversations)
})

const renderedMessages = computed(() => buildRenderedMessages(messages.value))

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

function normalizeBoolean(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback
  return value === true || value === 1 || value === '1' || String(value).toLowerCase() === 'true'
}

function normalizePagedResult(result, fallbackSize) {
  if (Array.isArray(result)) {
    return { items: result, page: 1, size: fallbackSize, total: result.length }
  }

  if (!result || typeof result !== 'object') {
    return { items: [], page: 1, size: fallbackSize, total: 0 }
  }

  const items = result.items || result.records || result.content || result.list || result.tasks || []
  return {
    items: Array.isArray(items) ? items : [],
    page: Number(result.page || result.current || 1),
    size: Number(result.size || fallbackSize),
    total: Number(result.total || result.totalElements || items.length || 0)
  }
}

function getConversationMeta(conversation) {
  return categoryMeta[conversation?.category] || categoryMeta.ERRAND
}

function normalizeProfile(result = {}) {
  const source = result?.userProfile || result?.profile || result || {}
  return {
    nickname: source.nickname || source.username || '',
    avatarUrl: source.avatarUrl || ''
  }
}

function profileInitial(name) {
  const normalized = String(name || '').trim()
  return normalized ? normalized.slice(0, 1) : '友'
}

function buildAvatarKey(scope, value, avatarUrl = '') {
  return `${scope}:${String(value || '')}:${String(avatarUrl || '')}`
}

function hasBrokenAvatar(scope, value, avatarUrl = '') {
  return Boolean(failedAvatarKeys.value[buildAvatarKey(scope, value, avatarUrl)])
}

function markAvatarBroken(scope, value, avatarUrl = '') {
  failedAvatarKeys.value = {
    ...failedAvatarKeys.value,
    [buildAvatarKey(scope, value, avatarUrl)]: true
  }
}

function peerProfile(conversation) {
  if (!conversation?.peerUserId) return null
  return conversationProfiles.value[String(conversation.peerUserId)] || null
}

function conversationDisplayName(conversation) {
  if (!conversation) return ''
  if (conversation.type === 'SYSTEM') return '系统通知'
  return peerProfile(conversation)?.nickname || `同学 ${conversation.peerUserId || ''}`.trim()
}

function conversationDisplayTitle(conversation) {
  if (!conversation) return ''
  if (conversation.type === 'SYSTEM') return conversation.title || '系统通知'
  return `和${conversationDisplayName(conversation)}的对话`
}

function conversationAvatarUrl(conversation) {
  if (!conversation || conversation.type === 'SYSTEM') return ''
  const avatarUrl = peerProfile(conversation)?.avatarUrl || ''
  if (!avatarUrl || hasBrokenAvatar('conversation', conversation.peerUserId || conversation.id, avatarUrl)) return ''
  return avatarUrl
}

function messageSenderProfile(message) {
  if (isOwnMessage(message)) return currentUserProfile.value
  return peerProfile(activeConversation.value) || { nickname: conversationDisplayName(activeConversation.value), avatarUrl: '' }
}

function messageAvatarUrl(message) {
  const profile = messageSenderProfile(message)
  const scope = isOwnMessage(message) ? 'message-self' : 'message-peer'
  const identity = isOwnMessage(message) ? currentUser.value : activeConversation.value?.peerUserId || activeConversation.value?.id
  if (!profile.avatarUrl || hasBrokenAvatar(scope, identity, profile.avatarUrl)) return ''
  return profile.avatarUrl
}

async function ensureConversationProfiles(items = []) {
  const peerIds = [...new Set(items.map((conversation) => conversation?.peerUserId).filter(Boolean).map(String))]
  const missingIds = peerIds.filter((peerUserId) => !conversationProfiles.value[peerUserId])
  if (!missingIds.length) return

  const settled = await Promise.allSettled(
    missingIds.map(async (peerUserId) => [peerUserId, normalizeProfile(await api.getUserProfile(peerUserId))])
  )

  const nextProfiles = { ...conversationProfiles.value }
  settled.forEach((result) => {
    if (result.status !== 'fulfilled') return
    const [peerUserId, profile] = result.value
    nextProfiles[String(peerUserId)] = profile
  })
  conversationProfiles.value = nextProfiles
}

function sortConversationsForDisplay(items = []) {
  return [...items].sort(
    (current, next) => new Date(next.lastMessageAt || 0) - new Date(current.lastMessageAt || 0)
  )
}

function normalizeConversation(conversation = {}) {
  const id = conversation.id ?? conversation.conversationId
  const relatedId = conversation.relatedId
  const type = conversation.type || 'TASK'
  const relatedType = conversation.relatedType || (type === 'SYSTEM' ? 'SYSTEM' : 'TASK')
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
    timeLabel: formatConversationTime(lastMessageAt)
  }
}

function normalizeMessage(message = {}) {
  return {
    id: message.id ?? `local-${message.createdAt || Date.now()}`,
    conversationId: message.conversationId ?? activeConversation.value?.id,
    senderId: message.senderId,
    type: message.type || 'TEXT',
    content: message.content || '',
    createdAt: message.createdAt || new Date().toISOString()
  }
}

function formatConversationTitle(relatedType, relatedId, type) {
  if (type === 'SYSTEM' || relatedType === 'SYSTEM') return '系统通知'
  if (relatedType === 'POST') return relatedId ? `帖子 #${relatedId} 私聊` : '帖子私聊'
  return relatedId ? `任务 #${relatedId}` : '站内会话'
}

function resolvePeerUserIdFromTask(task) {
  if (routePeerUserId.value) return routePeerUserId.value
  if (!task || !currentUser.value) return ''

  const requesterId = task.requesterId ?? task.ownerId
  const providerId = task.providerId ?? task.helperId ?? task.acceptedApplicantId

  if (requesterId !== undefined && String(requesterId) === String(currentUser.value)) {
    return normalizePositiveId(providerId)
  }
  if (providerId !== undefined && String(providerId) === String(currentUser.value)) {
    return normalizePositiveId(requesterId)
  }
  return ''
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

function selectInitialConversation() {
  if (!conversations.value.length) return null
  if (selectedConversationId.value) {
    return conversations.value.find((conversation) => String(conversation.id) === selectedConversationId.value) || null
  }
  if (hasRouteConversationHint()) return findConversationByRouteHint()
  return conversations.value[0]
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
    const createdConversation = normalizeConversation(
      await api.createConversation({
        relatedType: routeRelatedType.value,
        relatedId: Number(routeRelatedId.value),
        peerUserId: Number(peerUserId),
        initialMessage: 'hello'
      })
    )
    if (!createdConversation.id) return null

    conversations.value = [createdConversation, ...conversations.value]
    conversationPage.total = Math.max(conversationPage.total, conversations.value.length)
    return createdConversation
  } catch (error) {
    showToast(error.message || '创建会话失败，请稍后重试。', { type: 'danger' })
    return null
  }
}

async function applyConversationPage(paged) {
  conversations.value = paged.items
    .map(normalizeConversation)
    .filter((conversation) => conversation.id && conversation.type !== 'SYSTEM')
  await ensureConversationProfiles(conversations.value)
  conversationPage.page = paged.page || 1
  conversationPage.size = paged.size || conversationPage.size
  conversationPage.total = conversations.value.length

  let target = selectInitialConversation()
  if (!target && hasRouteConversationHint()) {
    target = await ensureConversationFromRoute()
  }

  if (!selectedConversationId.value && target?.id) {
    await router.replace({
      name: 'conversation-detail',
      params: { conversationId: target.id },
      query: route.query
    })
  }
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
    await applyConversationPage(normalizePagedResult(result, conversationPage.size))
  } catch (error) {
    if (requestId !== listRequestId) return
    showToast(error.message || '会话列表加载失败，请稍后重试。', { type: 'danger' })
    await applyConversationPage({ items: [], page: 1, size: conversationPage.size, total: 0 })
  } finally {
    if (requestId === listRequestId) listLoading.value = false
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
    applyMessagePage(normalizePagedResult(result, messagePage.size))
    markConversationLocallyRead(conversationId)
    await refreshNotificationSummary()
  } catch {
    if (requestId !== messagesRequestId) return
    applyMessagePage({ items: [], page: 1, size: messagePage.size, total: 0 })
  } finally {
    if (requestId === messagesRequestId) messagesLoading.value = false
  }
}

async function loadRelatedTask() {
  const relatedId = activeConversation.value?.relatedId
  if (!relatedId || activeConversation.value?.relatedType !== 'TASK') {
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
    lastMessagePreview: payload.type === 'IMAGE' ? '[图片]' : payload.content,
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

function triggerImageUpload() {
  if (isSystemConversation.value || !conversationReplyable.value) return
  composerImageInputRef.value?.click()
}

async function handleImageSelected(event) {
  const file = event.target.files?.[0]
  if (!file) return
  event.target.value = ''

  if (!activeConversation.value?.id) return
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
    const attachment = await api.uploadAttachment({
      file,
      expectedOwnerType: 'MESSAGE',
      type: 'IMAGE'
    })
    const attachmentId = attachment?.id || attachment?.attachmentId
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
  if (Number.isNaN(date.getTime())) return ''

  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const targetStart = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const diffDays = Math.round((todayStart - targetStart) / 86400000)
  const timeText = formatMessageTime(value)

  if (diffDays === 0) return `今天 ${timeText}`
  if (diffDays === 1) return `昨天 ${timeText}`
  return `${date.getMonth() + 1}月${date.getDate()}日 ${timeText}`
}

function shouldInsertDateDivider(previousMessage, currentMessage) {
  if (!currentMessage?.createdAt) return false
  if (!previousMessage?.createdAt) return true

  const previousDate = new Date(previousMessage.createdAt)
  const currentDate = new Date(currentMessage.createdAt)
  if (Number.isNaN(previousDate.getTime()) || Number.isNaN(currentDate.getTime())) return false

  const previousDay = new Date(previousDate.getFullYear(), previousDate.getMonth(), previousDate.getDate()).getTime()
  const currentDay = new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate()).getTime()
  if (previousDay !== currentDay) return true

  return currentDate.getTime() - previousDate.getTime() >= 5 * 60 * 1000
}

function buildRenderedMessages(items = []) {
  const rendered = []

  items.forEach((message, index) => {
    const previousMessage = index > 0 ? items[index - 1] : null
    if (shouldInsertDateDivider(previousMessage, message)) {
      rendered.push({
        id: `divider-${message.id}`,
        type: 'divider',
        label: formatDateDivider(message.createdAt)
      })
    }

    rendered.push({
      id: `message-${message.id}`,
      type: 'message',
      payload: message
    })
  })

  return rendered
}

function conversationSubtitle(conversation) {
  if (!conversation) return ''
  if (conversation.type === 'SYSTEM') return conversation.pinned ? '置顶通知' : '系统通知'
  return conversation.relatedId ? `任务 #${conversation.relatedId}` : '站内会话'
}

function categoryLabel(category) {
  return categoryMeta[category]?.label || categoryMeta.ERRAND.label
}

function taskStatusLabel(status) {
  return taskStatusLabels[status] || status || '同步中'
}

function rewardTypeLabel(type) {
  return rewardTypeLabels[type] || type || '未设置'
}

function shortText(text, length = 54) {
  if (!text) return '暂无内容'
  return text.length > length ? `${text.slice(0, length)}...` : text
}

function isOwnMessage(message) {
  return String(message.senderId) === String(currentUser.value)
}

function isImageMessage(message) {
  return message.type === 'IMAGE'
}

watch(
  () => route.params.conversationId,
  async () => {
    await loadRelatedTask()
    await loadMessages()
  }
)

watch(activeConversation, async () => {
  await loadRelatedTask()
})

watch(currentUser, () => {
  refreshNotificationSummary()
})

onMounted(async () => {
  await loadConversations()
  await loadRelatedTask()
  await loadMessages()
})
</script>

<template>
  <DashboardShell active-label="消息通知">
    <main class="message-page">
      <SectionNav
        class="conversation-section-nav"
        :items="workspaceNavItems"
        :active-key="route.meta?.subSection"
        variant="rail"
        aria-label="工作台二级导航"
      />

      <section class="message-shell">
        <aside class="conversation-list-panel panel-card">
          <div class="panel-heading panel-heading--tight">
            <h1>消息通知</h1>
            <button
              class="icon-action"
              type="button"
              aria-label="刷新会话"
              :disabled="listLoading"
              @click="reloadConversations"
            >
              <RefreshCw
                :class="{ spinning: listLoading }"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>

          <label class="search-box">
            <Search
              :size="18"
              aria-hidden="true"
            />
            <input
              v-model="conversationSearch"
              type="search"
              placeholder="搜索会话"
              aria-label="搜索会话"
            >
          </label>

          <div class="list-meta">
            <span>{{ conversations.length }} 个会话</span>
            <span v-if="unreadConversationCount">{{ unreadConversationCount }} 条未读</span>
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
          </div>

          <div
            v-else-if="!filteredConversations.length"
            class="empty-state empty-state--compact"
          >
            <MessageCircle
              :size="28"
              aria-hidden="true"
            />
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
                class="task-icon conversation-avatar"
                :class="`task-icon--${getConversationMeta(conversation).tone}`"
              >
                <img
                  v-if="conversationAvatarUrl(conversation)"
                  :src="conversationAvatarUrl(conversation)"
                  :alt="`${conversationDisplayName(conversation)}头像`"
                  @error="markAvatarBroken('conversation', conversation.peerUserId || conversation.id, conversationAvatarUrl(conversation))"
                >
                <component
                  :is="getConversationMeta(conversation).icon"
                  v-else-if="conversation.type === 'SYSTEM'"
                  :size="20"
                  aria-hidden="true"
                />
                <span v-else>{{ profileInitial(conversationDisplayName(conversation)) }}</span>
              </span>
              <span class="conversation-card-body">
                <span class="conversation-card-top">
                  <strong>
                    {{ conversationDisplayTitle(conversation) }}
                    <em v-if="conversation.type === 'SYSTEM'">置顶</em>
                  </strong>
                  <small>{{ conversation.timeLabel || formatConversationTime(conversation.lastMessageAt) }}</small>
                </span>
                <span class="conversation-preview">{{ shortText(conversation.lastMessagePreview) }}</span>
                <span class="conversation-meta">{{ conversationSubtitle(conversation) }}</span>
              </span>
              <span
                v-if="conversation.unreadCount"
                class="unread-dot"
                aria-label="有未读消息"
              />
            </button>
          </div>

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
            </button>
            <span>{{ conversationPage.page }} / {{ totalConversationPages }}</span>
            <button
              class="pager-button"
              type="button"
              :disabled="conversationPage.page >= totalConversationPages"
              @click="changeConversationPage(conversationPage.page + 1)"
            >
              <ChevronRight
                :size="16"
                aria-hidden="true"
              />
            </button>
          </div>
        </aside>

        <section
          v-if="activeConversation"
          class="chat-panel panel-card"
        >
          <header class="chat-header">
            <div class="chat-title">
              <span
                class="task-icon conversation-avatar"
                :class="`task-icon--${activeConversationMeta.tone}`"
              >
                <img
                  v-if="conversationAvatarUrl(activeConversation)"
                  :src="conversationAvatarUrl(activeConversation)"
                  :alt="`${conversationDisplayName(activeConversation)}头像`"
                  @error="markAvatarBroken('conversation', activeConversation.peerUserId || activeConversation.id, conversationAvatarUrl(activeConversation))"
                >
                <component
                  :is="activeConversationMeta.icon"
                  v-else-if="activeConversation.type === 'SYSTEM'"
                  :size="22"
                  aria-hidden="true"
                />
                <span v-else>
                  {{ profileInitial(conversationDisplayName(activeConversation)) }}
                </span>
              </span>
              <div>
                <h2>{{ conversationDisplayTitle(activeConversation) }}</h2>
                <p>{{ conversationSubtitle(activeConversation) }}</p>
              </div>
            </div>
            <StatusBadge
              :label="isSystemConversation ? '系统' : '会话'"
              :tone="isSystemConversation ? 'warning' : 'success'"
            />
          </header>

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
            </div>

            <template v-else-if="messages.length">
              <template
                v-for="entry in renderedMessages"
                :key="entry.id"
              >
                <div
                  v-if="entry.type === 'divider'"
                  class="date-divider"
                >
                  <span>{{ entry.label }}</span>
                </div>

                <article
                  v-else
                  class="message-row"
                  :class="{ 'message-row--mine': isOwnMessage(entry.payload) }"
                >
                  <span
                    v-if="!isOwnMessage(entry.payload)"
                    class="message-avatar"
                  >
                    <img
                      v-if="messageAvatarUrl(entry.payload)"
                      :src="messageAvatarUrl(entry.payload)"
                      :alt="`${messageSenderProfile(entry.payload).nickname}头像`"
                      @error="markAvatarBroken('message-peer', activeConversation?.peerUserId || activeConversation?.id, messageAvatarUrl(entry.payload))"
                    >
                    <span v-else>{{ profileInitial(messageSenderProfile(entry.payload).nickname) }}</span>
                  </span>
                  <div class="message-bubble">
                    <a
                      v-if="isImageMessage(entry.payload)"
                      class="message-image"
                      :href="entry.payload.content"
                      target="_blank"
                      rel="noreferrer"
                    >
                      <img
                        :src="entry.payload.content"
                        alt="消息图片"
                      >
                    </a>
                    <p v-else>
                      {{ entry.payload.content }}
                    </p>
                  </div>
                  <span
                    v-if="isOwnMessage(entry.payload)"
                    class="message-avatar message-avatar--mine"
                  >
                    <img
                      v-if="messageAvatarUrl(entry.payload)"
                      :src="messageAvatarUrl(entry.payload)"
                      :alt="`${messageSenderProfile(entry.payload).nickname}头像`"
                      @error="markAvatarBroken('message-self', currentUser, messageAvatarUrl(entry.payload))"
                    >
                    <span v-else>{{ profileInitial(messageSenderProfile(entry.payload).nickname) }}</span>
                  </span>
                </article>
              </template>
            </template>

            <div
              v-else
              class="empty-state empty-state--compact"
            >
              <MessageCircle
                :size="28"
                aria-hidden="true"
              />
            </div>
          </div>

          <form
            v-if="!isSystemConversation"
            class="message-composer"
            @submit.prevent="sendMessage"
          >
            <input
              ref="composerInputRef"
              v-model="composer.text"
              type="text"
              placeholder="输入消息"
              aria-label="输入消息"
              :disabled="sending || !conversationReplyable"
            >
            <input
              ref="composerImageInputRef"
              type="file"
              accept="image/*"
              class="hidden-input"
              @change="handleImageSelected"
            >
            <button
              class="image-button"
              type="button"
              aria-label="发送图片"
              :disabled="sending || !conversationReplyable"
              @click="triggerImageUpload"
            >
              <Image
                :size="20"
                aria-hidden="true"
              />
            </button>
            <button
              class="send-button"
              type="submit"
              aria-label="发送消息"
              :disabled="sending || !composer.text.trim() || !conversationReplyable"
            >
              <Send
                :size="20"
                aria-hidden="true"
              />
            </button>
          </form>
        </section>

        <section
          v-else
          class="chat-panel panel-card"
        >
          <div class="empty-state">
            <MessageCircle
              :size="30"
              aria-hidden="true"
            />
          </div>
        </section>

        <aside
          v-if="relatedTask && !isSystemConversation"
          class="task-side-panel panel-card"
        >
          <div class="panel-heading panel-heading--tight">
            <h2>{{ relatedTask.title }}</h2>
            <StatusBadge
              :label="taskStatusLabel(relatedTask.status)"
              tone="info"
            />
          </div>

          <dl class="task-detail-list">
            <div>
              <dt>类型</dt>
              <dd>{{ categoryLabel(relatedTask.category) }}</dd>
            </div>
            <div>
              <dt>地点</dt>
              <dd>{{ relatedTask.campus || '未设置' }} · {{ relatedTask.location || '未设置' }}</dd>
            </div>
            <div>
              <dt>奖励</dt>
              <dd>{{ rewardTypeLabel(relatedTask.rewardType) }}</dd>
            </div>
            <div>
              <dt>完成标准</dt>
              <dd>{{ relatedTask.completionStandard || '未填写' }}</dd>
            </div>
          </dl>

          <div class="mode-note">
            <ShieldCheck
              :size="18"
              aria-hidden="true"
            />
            <span>{{ onlyInAppActive ? '仅站内沟通' : '可协商其他沟通方式' }}</span>
          </div>
        </aside>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.message-page {
  display: grid;
  grid-template-columns: minmax(220px, 270px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.conversation-section-nav {
  position: sticky;
  top: 18px;
}

.message-shell {
  display: grid;
  grid-template-columns: minmax(300px, 0.92fr) minmax(420px, 1.35fr) minmax(260px, 0.78fr);
  gap: 18px;
  align-items: start;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.conversation-list-panel {
  min-height: 720px;
}

.chat-panel {
  min-height: 720px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-heading--tight {
  align-items: center;
}

.panel-heading h1,
.panel-heading h2,
.chat-title h2 {
  margin: 0;
  color: var(--color-text);
}

.icon-action,
.pager-button,
.image-button,
.send-button,
.conversation-card {
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease,
    color 180ms ease;
}

.icon-action,
.pager-button,
.image-button,
.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.icon-action {
  width: 38px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.search-box input {
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--color-text);
}

.list-meta {
  display: flex;
  justify-content: space-between;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.conversation-list {
  display: grid;
  gap: 10px;
  align-content: start;
}

.conversation-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  width: 100%;
  padding: 13px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
}

.conversation-card:hover,
.conversation-card.active {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-card);
}

.conversation-card-body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.conversation-card-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.conversation-card-top strong {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.conversation-card-top em {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 700;
}

.conversation-card-top small,
.conversation-preview,
.conversation-meta,
.chat-title p,
.task-detail-list dt {
  color: var(--color-text-muted);
}

.conversation-preview,
.conversation-meta,
.message-bubble p,
.task-detail-list dd {
  line-height: 1.55;
  word-break: break-word;
}

.task-icon,
.message-avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  overflow: hidden;
}

.conversation-avatar img,
.message-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-avatar span {
  font-size: 1rem;
  font-weight: 700;
}

.task-icon--blue {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.task-icon--orange {
  background: rgba(249, 115, 22, 0.12);
  color: #ea580c;
}

.task-icon--slate {
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
}

.unread-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  background: var(--color-danger);
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
}

.pager-button,
.image-button,
.send-button {
  width: 38px;
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chat-title {
  display: flex;
  gap: 12px;
}

.chat-title p {
  margin: 4px 0 0;
}

.date-divider {
  display: flex;
  justify-content: center;
}

.date-divider span {
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  font-size: 0.84rem;
  font-weight: 700;
}

.message-thread {
  display: grid;
  align-content: start;
  grid-auto-rows: max-content;
  gap: 8px;
  height: 560px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.message-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: end;
  gap: 8px;
}

.message-row--mine {
  grid-template-columns: minmax(0, 1fr) auto;
}

.message-avatar {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.message-avatar--mine {
  background: var(--color-accent-bg);
  color: var(--color-accent);
}

.message-bubble {
  justify-self: start;
  width: fit-content;
  max-width: min(100%, 520px);
  padding: 7px 12px;
  border-radius: 16px;
  background: var(--color-bg);
  color: var(--color-text);
}

.message-row--mine .message-bubble {
  justify-self: end;
  background: var(--color-brand-soft);
}

.message-bubble p {
  margin: 0;
}

.message-image,
.message-image img {
  display: block;
}

.message-image img {
  max-width: 220px;
  border-radius: 12px;
}

.message-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
}

.message-composer input[type='text'] {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text);
}

.send-button {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: #fff;
}

.task-detail-list {
  display: grid;
  gap: 12px;
}

.task-detail-list div {
  display: grid;
  gap: 4px;
}

.task-detail-list dd {
  margin: 0;
  color: var(--color-text);
}

.mode-note {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-text-secondary);
}

.loading-state,
.empty-state {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 180px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
}

.empty-state--compact {
  min-height: 120px;
}

.hidden-input {
  display: none;
}

.icon-action:hover,
.pager-button:hover:not(:disabled),
.image-button:hover:not(:disabled),
.send-button:hover:not(:disabled) {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-button);
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1200px) {
  .message-page {
    grid-template-columns: 1fr;
  }

  .conversation-section-nav {
    position: static;
  }

  .message-shell {
    grid-template-columns: 1fr;
  }

  .conversation-list-panel,
  .chat-panel {
    min-height: auto;
  }
}
</style>
