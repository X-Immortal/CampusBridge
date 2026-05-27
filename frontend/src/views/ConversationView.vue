<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Clock3,
  ClipboardCheck,
  ImagePlus,
  Inbox,
  Info,
  Link2,
  LoaderCircle,
  LockKeyhole,
  MessageCircle,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  UploadCloud,
  UserRound,
  X
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import {
  CONVERSATION_TYPE_OPTIONS,
  optionLabel,
  REWARD_TYPE_OPTIONS,
  TASK_CATEGORY_OPTIONS,
  TASK_STATUS_OPTIONS,
  statusTone
} from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, currentUserId } = useAuth()

const conversations = ref([])
const messages = ref([])
const relatedTask = ref(null)
const listLoading = ref(false)
const messagesLoading = ref(false)
const taskLoading = ref(false)
const sending = ref(false)
const listError = ref('')
const messagesError = ref('')
const taskError = ref('')
const pageFeedback = ref('')
const pageFeedbackType = ref('success')
const conversationSearch = ref('')
const threadRef = ref(null)

const conversationPage = reactive({
  page: 1,
  size: 12,
  total: 0
})

const messagePage = reactive({
  page: 1,
  size: 50,
  total: 0
})

const composer = reactive({
  mode: 'TEXT',
  text: '',
  imageUrl: '',
  imageDataUrl: '',
  imageName: ''
})

const composerErrors = reactive({})

let listRequestId = 0
let messagesRequestId = 0
let taskRequestId = 0

const selectedConversationId = computed(() =>
  route.params.conversationId ? String(route.params.conversationId) : ''
)

const relatedQuery = computed(() => ({
  type: String(firstQueryValue(route.query.relatedType) || '').toUpperCase(),
  id: String(firstQueryValue(route.query.relatedId) || '')
}))

const activeConversation = computed(
  () =>
    conversations.value.find((conversation) => String(conversation.id) === selectedConversationId.value) ||
    null
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

const totalConversationPages = computed(() =>
  Math.max(1, Math.ceil(conversationPage.total / conversationPage.size))
)

const conversationReplyable = computed(() => activeConversation.value?.replyable ?? false)
const isTaskConversation = computed(() => {
  const conversation = activeConversation.value
  return Boolean(
    conversation &&
      String(conversation.relatedType || conversation.type || '').toUpperCase().includes('TASK') &&
      conversation.relatedId
  )
})

const onlyInAppActive = computed(() => Boolean(relatedTask.value?.onlyInAppContact))
const taskStatusTone = computed(() => statusTone(relatedTask.value?.status))
const relatedQueryMissing = computed(() => {
  if (selectedConversationId.value || !relatedQuery.value.id) return false
  return !conversations.value.some((conversation) => {
    return (
      String(conversation.relatedType).toUpperCase() === relatedQuery.value.type &&
      String(conversation.relatedId) === relatedQuery.value.id
    )
  })
})

const composerModeLabel = computed(() => (composer.mode === 'IMAGE' ? '图片消息' : '文字消息'))
const imageDraftSource = computed(() => composer.imageDataUrl || composer.imageUrl.trim())

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
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
  const type = conversation.type || 'TASK'
  const title = conversation.title || (conversation.relatedId ? `任务会话 #${conversation.relatedId}` : '站内会话')

  return {
    id,
    type,
    title,
    ownerId: conversation.ownerId,
    peerUserId: conversation.peerUserId,
    relatedType: conversation.relatedType || (type === 'TASK' ? 'TASK' : type),
    relatedId: conversation.relatedId,
    replyable: normalizeBoolean(conversation.replyable, true),
    lastMessageAt: conversation.lastMessageAt || conversation.updatedAt || conversation.createdAt || '',
    createdAt: conversation.createdAt || conversation.lastMessageAt || '',
    lastMessagePreview:
      conversation.lastMessagePreview ||
      conversation.lastMessageContent ||
      conversation.lastMessage ||
      conversation.preview ||
      ''
  }
}

function normalizeMessage(message = {}) {
  return {
    id: message.id ?? `local-${message.createdAt || Date.now()}`,
    conversationId: message.conversationId ?? selectedConversationId.value,
    senderId: message.senderId,
    type: message.type || 'TEXT',
    content: message.content || '',
    readAt: message.readAt || '',
    createdAt: message.createdAt || new Date().toISOString()
  }
}

function buildOptimisticMessage(payload) {
  return normalizeMessage({
    id: `local-${Date.now()}`,
    conversationId: selectedConversationId.value,
    senderId: currentUserId.value,
    type: payload.type,
    content: payload.content,
    createdAt: new Date().toISOString()
  })
}

function setFeedback(message, type = 'success') {
  pageFeedback.value = message
  pageFeedbackType.value = type
}

function clearComposerError(field) {
  delete composerErrors[field]
}

function clearComposerErrors() {
  Object.keys(composerErrors).forEach((field) => {
    delete composerErrors[field]
  })
}

function setComposerError(field, message) {
  composerErrors[field] = message
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

function formatMessageTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function conversationTitle(conversation) {
  if (!conversation) return '站内会话'
  return conversation.title || `会话 #${conversation.id}`
}

function conversationSubtitle(conversation) {
  if (!conversation) return '选择会话后查看消息'
  if (String(conversation.relatedType).toUpperCase() === 'TASK') {
    return conversation.relatedId ? `关联任务 #${conversation.relatedId}` : '任务协作会话'
  }
  return conversation.peerUserId ? `对方用户 #${conversation.peerUserId}` : '站内沟通记录'
}

function conversationTypeLabel(type) {
  return optionLabel(CONVERSATION_TYPE_OPTIONS, type)
}

function avatarInitial(conversation) {
  const title = conversationTitle(conversation).trim()
  return title ? title.slice(0, 1) : '会'
}

function isOwnMessage(message) {
  if (message.senderId === undefined || message.senderId === null || currentUserId.value === null) return false
  return String(message.senderId) === String(currentUserId.value)
}

function isSystemMessage(message) {
  return message.type === 'SYSTEM_NOTIFICATION'
}

function isImageUri(value) {
  if (!value) return false
  if (value.startsWith('data:image/')) return true

  try {
    const url = new URL(value)
    return /\.(png|jpe?g|webp|gif|bmp|svg)$/i.test(url.pathname)
  } catch {
    return false
  }
}

function isImageMessage(message) {
  return message.type === 'IMAGE' || isImageUri(message.content)
}

function isSupportedImageUri(value) {
  if (!value) return false
  if (value.startsWith('data:image/')) return true

  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol)
  } catch {
    return false
  }
}

function hasExternalContact(text) {
  return /(微信|v信|wechat|vx|电话|手机号|手机|qq|QQ|[1][3-9]\d{9})/i.test(text)
}

function messagePreviewFromPayload(payload) {
  if (payload.type === 'IMAGE') return '已发送图片'
  return payload.content.length > 28 ? `${payload.content.slice(0, 28)}...` : payload.content
}

function syncConversationAfterSend(message, payload) {
  const index = conversations.value.findIndex(
    (conversation) => String(conversation.id) === String(message.conversationId)
  )
  if (index < 0) return

  conversations.value[index] = {
    ...conversations.value[index],
    lastMessageAt: message.createdAt,
    lastMessagePreview: messagePreviewFromPayload(payload)
  }
}

async function scrollMessagesToBottom() {
  await nextTick()
  if (threadRef.value) {
    threadRef.value.scrollTop = threadRef.value.scrollHeight
  }
}

function selectInitialConversation() {
  if (!conversations.value.length) return null

  if (selectedConversationId.value) {
    return conversations.value.find(
      (conversation) => String(conversation.id) === selectedConversationId.value
    )
  }

  if (relatedQuery.value.id) {
    const related = conversations.value.find((conversation) => {
      return (
        String(conversation.relatedType).toUpperCase() === relatedQuery.value.type &&
        String(conversation.relatedId) === relatedQuery.value.id
      )
    })
    if (related) return related
  }

  return conversations.value[0]
}

async function loadConversations() {
  listLoading.value = true
  listError.value = ''
  const requestId = ++listRequestId

  try {
    const result = await api.getConversations({
      page: conversationPage.page,
      size: conversationPage.size
    })
    if (requestId !== listRequestId) return

    const paged = normalizePagedResult(result, conversationPage.size)
    conversations.value = paged.items.map(normalizeConversation).filter((conversation) => conversation.id)
    conversationPage.page = paged.page || conversationPage.page
    conversationPage.size = paged.size || conversationPage.size
    conversationPage.total = paged.total || conversations.value.length

    const target = selectInitialConversation()
    if (!selectedConversationId.value && target?.id) {
      await router.replace({
        name: 'conversation-detail',
        params: { conversationId: target.id },
        query: route.query
      })
    }
  } catch (error) {
    listError.value = error.message || '会话列表加载失败，请稍后重试。'
  } finally {
    if (requestId === listRequestId) listLoading.value = false
  }
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

async function loadMessages() {
  messages.value = []
  messagesError.value = ''
  if (!selectedConversationId.value) return

  messagesLoading.value = true
  const requestId = ++messagesRequestId

  try {
    const result = await api.getConversationMessages(selectedConversationId.value, {
      page: messagePage.page,
      size: messagePage.size
    })
    if (requestId !== messagesRequestId) return

    const paged = normalizePagedResult(result, messagePage.size)
    messages.value = paged.items
      .map(normalizeMessage)
      .sort((current, next) => new Date(current.createdAt) - new Date(next.createdAt))
    messagePage.page = paged.page || messagePage.page
    messagePage.size = paged.size || messagePage.size
    messagePage.total = paged.total || messages.value.length
    await scrollMessagesToBottom()
  } catch (error) {
    messagesError.value = error.message || '消息加载失败，请稍后重试。'
  } finally {
    if (requestId === messagesRequestId) messagesLoading.value = false
  }
}

async function loadRelatedTask() {
  const conversation = activeConversation.value
  relatedTask.value = null
  taskError.value = ''

  if (
    !conversation ||
    !conversation.relatedId ||
    !String(conversation.relatedType || conversation.type || '').toUpperCase().includes('TASK')
  ) {
    return
  }

  taskLoading.value = true
  const requestId = ++taskRequestId

  try {
    const result = await api.getTaskDetail(conversation.relatedId)
    if (requestId !== taskRequestId) return

    relatedTask.value = result?.task || result || null
  } catch (error) {
    taskError.value = error.message || '关联任务加载失败。'
  } finally {
    if (requestId === taskRequestId) taskLoading.value = false
  }
}

function resetComposer() {
  composer.text = ''
  composer.imageUrl = ''
  composer.imageDataUrl = ''
  composer.imageName = ''
  clearComposerErrors()
}

function switchComposerMode(mode) {
  composer.mode = mode
  clearComposerErrors()
}

function readFileAsDataUri(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('图片读取失败，请重新选择。'))
    reader.readAsDataURL(file)
  })
}

async function handleImageFile(event) {
  const file = event.target.files?.[0]
  if (!file) return

  clearComposerError('image')
  if (!file.type.startsWith('image/')) {
    setComposerError('image', '目前只支持发送图片文件。')
    event.target.value = ''
    return
  }

  if (file.size > 4 * 1024 * 1024) {
    setComposerError('image', '单张图片请控制在 4MB 以内。')
    event.target.value = ''
    return
  }

  try {
    composer.imageDataUrl = await readFileAsDataUri(file)
    composer.imageName = file.name || '本地图片'
    composer.imageUrl = ''
    composer.mode = 'IMAGE'
  } catch (error) {
    setComposerError('image', error.message)
  } finally {
    event.target.value = ''
  }
}

function removeImageDraft() {
  composer.imageDataUrl = ''
  composer.imageName = ''
  clearComposerError('image')
}

function validateComposer() {
  clearComposerErrors()

  if (!isAuthenticated.value) {
    setComposerError('auth', '请先登录后再发送站内消息。')
  }

  if (!conversationReplyable.value) {
    setComposerError('replyable', '当前会话已关闭回复。')
  }

  if (composer.mode === 'TEXT') {
    const content = composer.text.trim()
    if (!content) setComposerError('text', '请先输入要发送的内容。')
    if (content.length > 2000) setComposerError('text', '文字消息最多 2000 个字。')
    if (onlyInAppActive.value && hasExternalContact(content)) {
      setComposerError('text', '当前任务开启仅站内沟通，请不要在消息中填写微信、电话或 QQ。')
    }
  }

  if (composer.mode === 'IMAGE') {
    const imageSource = imageDraftSource.value
    if (!imageSource) setComposerError('image', '请粘贴图片链接或选择本地图片。')
    if (imageSource && !isSupportedImageUri(imageSource)) {
      setComposerError('image', '图片链接需为 http、https，或选择本地图片。')
    }
  }

  return !Object.keys(composerErrors).length
}

function buildMessagePayload() {
  if (composer.mode === 'IMAGE') {
    const imageSource = imageDraftSource.value
    return {
      type: 'IMAGE',
      content: imageSource,
      attachmentUrls: [imageSource]
    }
  }

  return {
    type: 'TEXT',
    content: composer.text.trim()
  }
}

async function sendMessage() {
  if (!activeConversation.value?.id || sending.value || !validateComposer()) return

  sending.value = true
  pageFeedback.value = ''
  const payload = buildMessagePayload()

  try {
    const result = await api.sendConversationMessage(activeConversation.value.id, payload)
    const message = result ? normalizeMessage(result) : buildOptimisticMessage(payload)
    messages.value = [...messages.value, message]
    syncConversationAfterSend(message, payload)
    resetComposer()
    setFeedback(`${composerModeLabel.value}已发送。`)
    await scrollMessagesToBottom()
  } catch (error) {
    setFeedback(error.message || '消息发送失败，请稍后重试。', 'danger')
  } finally {
    sending.value = false
  }
}

watch(
  () => route.params.conversationId,
  async () => {
    resetComposer()
    await loadMessages()
  }
)

watch(
  activeConversation,
  async () => {
    await loadRelatedTask()
  },
  { immediate: true }
)

onMounted(async () => {
  await loadConversations()
  await loadMessages()
})
</script>

<template>
  <DashboardShell active-label="站内沟通">
    <main class="conversation-page">
      <section class="page-title-card conversation-title-card">
        <div class="title-icon">
          <MessageCircle
            :size="32"
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

      <section
        v-if="pageFeedback"
        class="page-feedback"
        :class="pageFeedbackType === 'danger' ? 'page-feedback--danger' : 'page-feedback--success'"
        role="status"
      >
        <Info
          :size="18"
          aria-hidden="true"
        />
        <span>{{ pageFeedback }}</span>
      </section>

      <section
        v-if="relatedQueryMissing"
        class="page-feedback page-feedback--info"
        role="status"
      >
        <Info
          :size="18"
          aria-hidden="true"
        />
        <span>对应任务会话尚未同步。双方确认合作后，会话会自动出现在这里。</span>
      </section>

      <section class="conversation-layout">
        <aside class="panel-card conversation-list-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                会话列表
              </p>
              <h2>正在协作</h2>
            </div>
            <button
              class="icon-action"
              type="button"
              aria-label="刷新会话列表"
              :disabled="listLoading"
              @click="loadConversations"
            >
              <RefreshCw
                :class="{ spinning: listLoading }"
                :size="17"
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
              v-model.trim="conversationSearch"
              type="search"
              placeholder="搜索会话、任务编号"
              aria-label="搜索会话、任务编号"
            >
          </label>

          <div
            v-if="listLoading"
            class="loading-state compact-loading"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="22"
              aria-hidden="true"
            />
            <span>正在同步会话...</span>
          </div>

          <div
            v-else-if="listError"
            class="inline-error"
            role="status"
          >
            <Info
              :size="18"
              aria-hidden="true"
            />
            <span>{{ listError }}</span>
          </div>

          <div
            v-else-if="!filteredConversations.length"
            class="empty-state list-empty"
          >
            <Inbox
              :size="32"
              aria-hidden="true"
            />
            <h3>暂无站内会话</h3>
            <p>确认帮助者后，任务会话会自动建立，用于后续澄清、凭证和争议追溯。</p>
          </div>

          <div
            v-else
            class="conversation-list"
          >
            <button
              v-for="conversation in filteredConversations"
              :key="conversation.id"
              class="conversation-item"
              :class="{ selected: String(conversation.id) === selectedConversationId }"
              type="button"
              @click="selectConversation(conversation)"
            >
              <span class="conversation-avatar">{{ avatarInitial(conversation) }}</span>
              <span class="conversation-main">
                <span class="conversation-row">
                  <strong>{{ conversationTitle(conversation) }}</strong>
                  <small>{{ formatDateTime(conversation.lastMessageAt) }}</small>
                </span>
                <span>{{ conversationSubtitle(conversation) }}</span>
                <span class="conversation-preview">
                  {{ conversation.lastMessagePreview || '等待新的站内消息' }}
                </span>
              </span>
            </button>
          </div>

          <nav
            class="pagination-bar"
            aria-label="会话分页"
          >
            <button
              class="ghost-action"
              type="button"
              :disabled="conversationPage.page <= 1 || listLoading"
              @click="changeConversationPage(conversationPage.page - 1)"
            >
              <ArrowLeft
                :size="15"
                aria-hidden="true"
              />
              上一页
            </button>
            <span>{{ conversationPage.page }} / {{ totalConversationPages }}</span>
            <button
              class="ghost-action"
              type="button"
              :disabled="conversationPage.page >= totalConversationPages || listLoading"
              @click="changeConversationPage(conversationPage.page + 1)"
            >
              下一页
              <ArrowRight
                :size="15"
                aria-hidden="true"
              />
            </button>
          </nav>
        </aside>

        <section class="panel-card chat-panel">
          <template v-if="activeConversation">
            <header class="chat-heading">
              <div class="chat-person">
                <span class="chat-avatar">
                  <UserRound
                    :size="24"
                    aria-hidden="true"
                  />
                </span>
                <div>
                  <p class="section-kicker">
                    {{ conversationTypeLabel(activeConversation.type) }}
                  </p>
                  <h2>{{ conversationTitle(activeConversation) }}</h2>
                  <span>{{ conversationSubtitle(activeConversation) }}</span>
                </div>
              </div>
              <div class="chat-heading-actions">
                <StatusBadge
                  v-if="onlyInAppActive"
                  label="仅站内沟通"
                  tone="success"
                />
                <StatusBadge
                  v-else-if="isTaskConversation"
                  label="站内优先"
                  tone="info"
                />
                <button
                  class="icon-action"
                  type="button"
                  aria-label="刷新消息"
                  :disabled="messagesLoading"
                  @click="loadMessages"
                >
                  <RefreshCw
                    :class="{ spinning: messagesLoading }"
                    :size="17"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </header>

            <div
              v-if="onlyInAppActive"
              class="mode-banner"
              role="status"
            >
              <LockKeyhole
                :size="18"
                aria-hidden="true"
              />
              <span>该任务开启仅站内沟通，页面不会要求填写或交换站外联系方式。</span>
            </div>

            <div
              ref="threadRef"
              class="message-thread"
              aria-live="polite"
            >
              <div
                v-if="messagesLoading"
                class="loading-state"
                role="status"
              >
                <LoaderCircle
                  class="spinning"
                  :size="24"
                  aria-hidden="true"
                />
                <span>正在加载消息...</span>
              </div>

              <div
                v-else-if="messagesError"
                class="inline-error"
                role="status"
              >
                <Info
                  :size="18"
                  aria-hidden="true"
                />
                <span>{{ messagesError }}</span>
              </div>

              <div
                v-else-if="!messages.length"
                class="empty-state message-empty"
              >
                <MessageCircle
                  :size="34"
                  aria-hidden="true"
                />
                <h3>还没有消息</h3>
                <p>合作双方可以先确认地点、时间、完成标准和凭证要求。</p>
              </div>

              <template v-else>
                <article
                  v-for="message in messages"
                  :key="message.id"
                  class="message-row"
                  :class="{
                    'message-row--mine': isOwnMessage(message),
                    'message-row--system': isSystemMessage(message)
                  }"
                >
                  <span
                    v-if="!isSystemMessage(message)"
                    class="message-author"
                  >
                    {{ isOwnMessage(message) ? '我' : '对方' }}
                  </span>
                  <div class="message-bubble">
                    <template v-if="isImageMessage(message)">
                      <a
                        class="message-image-link"
                        :href="message.content"
                        target="_blank"
                        rel="noreferrer"
                      >
                        <img
                          :src="message.content"
                          :alt="`${isOwnMessage(message) ? '我' : '对方'}发送的图片`"
                        >
                      </a>
                    </template>
                    <p v-else>
                      {{ message.content || '空消息' }}
                    </p>
                    <time :datetime="message.createdAt">
                      {{ formatMessageTime(message.createdAt) }}
                    </time>
                  </div>
                </article>
              </template>
            </div>

            <form
              class="message-composer"
              novalidate
              @submit.prevent="sendMessage"
            >
              <div
                class="composer-tabs"
                role="tablist"
                aria-label="消息类型"
              >
                <button
                  type="button"
                  :class="{ active: composer.mode === 'TEXT' }"
                  @click="switchComposerMode('TEXT')"
                >
                  <MessageCircle
                    :size="16"
                    aria-hidden="true"
                  />
                  文字
                </button>
                <button
                  type="button"
                  :class="{ active: composer.mode === 'IMAGE' }"
                  @click="switchComposerMode('IMAGE')"
                >
                  <ImagePlus
                    :size="16"
                    aria-hidden="true"
                  />
                  图片
                </button>
              </div>

              <p
                v-if="composerErrors.auth || composerErrors.replyable"
                class="field-error"
              >
                {{ composerErrors.auth || composerErrors.replyable }}
              </p>

              <label
                v-if="composer.mode === 'TEXT'"
                class="composer-field"
                for="message-text"
              >
                <span>消息内容</span>
                <textarea
                  id="message-text"
                  v-model="composer.text"
                  maxlength="2000"
                  placeholder="说明取件码发送方式、到达时间、交接位置或完成标准。"
                  :aria-invalid="Boolean(composerErrors.text)"
                  :aria-describedby="composerErrors.text ? 'message-text-error' : 'message-text-hint'"
                  @input="clearComposerError('text')"
                />
                <small id="message-text-hint">{{ composer.text.length }}/2000</small>
                <small
                  v-if="composerErrors.text"
                  id="message-text-error"
                  class="field-error"
                >
                  {{ composerErrors.text }}
                </small>
              </label>

              <section
                v-else
                class="image-composer"
                aria-labelledby="image-composer-title"
              >
                <div class="image-composer-grid">
                  <label
                    class="composer-field"
                    for="message-image-url"
                  >
                    <span id="image-composer-title">图片链接</span>
                    <span class="image-url-row">
                      <input
                        id="message-image-url"
                        v-model.trim="composer.imageUrl"
                        placeholder="https://example.com/proof.jpg"
                        :disabled="Boolean(composer.imageDataUrl)"
                        :aria-invalid="Boolean(composerErrors.image)"
                        @input="clearComposerError('image')"
                      >
                      <Link2
                        :size="17"
                        aria-hidden="true"
                      />
                    </span>
                  </label>

                  <label
                    class="file-pick-control"
                    for="message-image-file"
                  >
                    <UploadCloud
                      :size="20"
                      aria-hidden="true"
                    />
                    <span>
                      <strong>选择本地图片</strong>
                      <small>单张不超过 4MB。</small>
                    </span>
                    <input
                      id="message-image-file"
                      type="file"
                      accept="image/*"
                      @change="handleImageFile"
                    >
                  </label>
                </div>

                <div
                  v-if="imageDraftSource"
                  class="image-preview"
                >
                  <img
                    :src="imageDraftSource"
                    alt="待发送图片预览"
                  >
                  <button
                    v-if="composer.imageDataUrl"
                    type="button"
                    aria-label="移除待发送图片"
                    @click="removeImageDraft"
                  >
                    <X
                      :size="15"
                      aria-hidden="true"
                    />
                    移除
                  </button>
                  <span>{{ composer.imageName || '图片链接预览' }}</span>
                </div>

                <p
                  v-if="composerErrors.image"
                  class="field-error"
                >
                  {{ composerErrors.image }}
                </p>
              </section>

              <div class="composer-actions">
                <span>{{ conversationReplyable ? '消息会保留在站内记录中' : '当前不可回复' }}</span>
                <button
                  class="primary-action"
                  type="submit"
                  :disabled="sending || !conversationReplyable"
                >
                  <LoaderCircle
                    v-if="sending"
                    class="spinning"
                    :size="18"
                    aria-hidden="true"
                  />
                  <Send
                    v-else
                    :size="18"
                    aria-hidden="true"
                  />
                  发送
                </button>
              </div>
            </form>
          </template>

          <div
            v-else
            class="empty-state chat-empty"
          >
            <MessageCircle
              :size="42"
              aria-hidden="true"
            />
            <h2>选择一个会话</h2>
            <p>站内沟通会围绕已确认的任务自动生成，文本、图片和时间戳会一起保留。</p>
          </div>
        </section>

        <aside class="context-column">
          <article class="panel-card context-card">
            <div class="panel-heading">
              <div>
                <p class="section-kicker">
                  沟通边界
                </p>
                <h2>{{ onlyInAppActive ? '仅站内模式已开启' : '站内沟通优先' }}</h2>
              </div>
              <LockKeyhole
                :size="24"
                aria-hidden="true"
              />
            </div>
            <div class="boundary-list">
              <span :class="{ active: onlyInAppActive }">
                <LockKeyhole
                  :size="16"
                  aria-hidden="true"
                />
                {{ onlyInAppActive ? '不交换站外联系方式' : '可按任务偏好协商' }}
              </span>
              <span class="active">
                <MessageCircle
                  :size="16"
                  aria-hidden="true"
                />
                文字和图片留痕
              </span>
              <span class="active">
                <ShieldCheck
                  :size="16"
                  aria-hidden="true"
                />
                争议可回溯
              </span>
            </div>
          </article>

          <article class="panel-card context-card">
            <div class="panel-heading">
              <div>
                <p class="section-kicker">
                  关联任务
                </p>
                <h2>{{ relatedTask?.title || '任务上下文' }}</h2>
              </div>
              <ClipboardCheck
                :size="24"
                aria-hidden="true"
              />
            </div>

            <div
              v-if="taskLoading"
              class="loading-state compact-loading"
              role="status"
            >
              <LoaderCircle
                class="spinning"
                :size="22"
                aria-hidden="true"
              />
              <span>正在同步任务...</span>
            </div>

            <div
              v-else-if="taskError"
              class="inline-error"
              role="status"
            >
              <Info
                :size="18"
                aria-hidden="true"
              />
              <span>{{ taskError }}</span>
            </div>

            <template v-else-if="relatedTask">
              <div class="task-status-row">
                <StatusBadge
                  :label="optionLabel(TASK_STATUS_OPTIONS, relatedTask.status)"
                  :tone="taskStatusTone"
                />
                <span>{{ optionLabel(TASK_CATEGORY_OPTIONS, relatedTask.category) }}</span>
              </div>
              <dl class="task-context-list">
                <div>
                  <dt>校区地点</dt>
                  <dd>{{ relatedTask.campus || '未填校区' }} · {{ relatedTask.location || '未填地点' }}</dd>
                </div>
                <div>
                  <dt>奖励方式</dt>
                  <dd>{{ optionLabel(REWARD_TYPE_OPTIONS, relatedTask.rewardType) }}</dd>
                </div>
                <div>
                  <dt>完成标准</dt>
                  <dd>{{ relatedTask.completionStandard || '暂未填写完成标准' }}</dd>
                </div>
              </dl>
              <RouterLink
                class="ghost-action context-link"
                :to="{ name: 'task-detail', params: { taskId: relatedTask.id || activeConversation.relatedId } }"
              >
                查看任务详情
              </RouterLink>
            </template>

            <p
              v-else
              class="context-copy"
            >
              当前会话没有可同步的任务上下文。
            </p>
          </article>

          <article class="panel-card context-card">
            <div class="panel-heading">
              <div>
                <p class="section-kicker">
                  协作检查
                </p>
                <h2>发送前确认</h2>
              </div>
              <BadgeCheck
                :size="24"
                aria-hidden="true"
              />
            </div>
            <div class="check-list">
              <span>
                <Clock3
                  :size="16"
                  aria-hidden="true"
                />
                明确到达或处理时间
              </span>
              <span>
                <ImagePlus
                  :size="16"
                  aria-hidden="true"
                />
                图片用于补充凭证
              </span>
              <span>
                <ShieldCheck
                  :size="16"
                  aria-hidden="true"
                />
                重要口径留在站内
              </span>
            </div>
          </article>
        </aside>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.conversation-page {
  display: grid;
  gap: 20px;
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
.panel-heading h2,
.chat-heading h2 {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.15;
}

.title-copy h1 {
  font-size: 1.9rem;
}

.title-copy span,
.chat-person span,
.context-copy {
  color: #8a8792;
  line-height: 1.55;
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 14px;
  line-height: 1.55;
}

.page-feedback--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.page-feedback--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.page-feedback--info {
  border-color: #bae6fd;
  background: #f0f9ff;
  color: #075985;
}

.conversation-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.34fr) minmax(0, 1fr) minmax(280px, 0.34fr);
  gap: 22px;
  align-items: start;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 22px;
}

.panel-heading,
.chat-heading,
.chat-heading-actions,
.conversation-row,
.composer-actions,
.task-status-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.panel-heading h2,
.chat-heading h2 {
  margin-top: 4px;
  font-size: 1.18rem;
}

.icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid #e0ddec;
  border-radius: 8px;
  background: #ffffff;
  color: #746dc3;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.icon-action:hover:not(:disabled) {
  border-color: #b9b2e8;
  background: #f7f4ff;
  box-shadow: 0 8px 18px rgba(116, 109, 195, 0.1);
}

.icon-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.conversation-list-panel {
  position: sticky;
  top: 118px;
}

.conversation-search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #9a96a3;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.conversation-search:focus-within,
.composer-field textarea:focus,
.composer-field input:focus,
.file-pick-control:focus-within,
.primary-action:focus-visible,
.ghost-action:focus-visible,
.composer-tabs button:focus-visible,
.conversation-item:focus-visible,
.icon-action:focus-visible {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
  outline: 0;
}

.conversation-search input,
.composer-field input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #393545;
}

.conversation-list {
  display: grid;
  gap: 10px;
}

.conversation-item {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  padding: 13px;
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

.conversation-item:hover,
.conversation-item.selected {
  border-color: #b9b2e8;
  background: #fbf9ff;
  box-shadow: 0 14px 28px rgba(116, 109, 195, 0.08);
}

.conversation-item.selected::before {
  content: "";
  position: absolute;
  inset: 12px auto 12px 0;
  width: 3px;
  border-radius: 999px;
  background: #746dc3;
}

.conversation-avatar,
.chat-avatar {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #eef2ff;
  color: #4338ca;
  font-weight: 900;
}

.conversation-main {
  display: grid;
  gap: 5px;
}

.conversation-row strong {
  color: #3c3543;
  font-size: 0.98rem;
  line-height: 1.35;
}

.conversation-row small,
.conversation-main > span,
.conversation-preview,
.pagination-bar {
  color: #8a8792;
  font-size: 0.86rem;
  line-height: 1.45;
}

.conversation-preview {
  color: #746f7a;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.chat-panel {
  min-height: 720px;
  gap: 16px;
}

.chat-heading {
  align-items: center;
  padding-bottom: 4px;
}

.chat-person {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chat-heading-actions {
  align-items: center;
  flex-wrap: wrap;
}

.mode-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  line-height: 1.5;
}

.message-thread {
  display: flex;
  flex-direction: column;
  gap: 13px;
  min-height: 390px;
  max-height: 52vh;
  padding: 18px;
  overflow-y: auto;
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background:
    linear-gradient(180deg, #fcfbff 0%, #ffffff 100%);
}

.message-row {
  display: grid;
  justify-items: start;
  gap: 5px;
  max-width: min(76%, 560px);
}

.message-row--mine {
  justify-self: end;
  justify-items: end;
}

.message-row--system {
  justify-self: center;
  justify-items: center;
  max-width: min(88%, 620px);
}

.message-author {
  color: #8a8792;
  font-size: 0.82rem;
  font-weight: 900;
}

.message-bubble {
  display: grid;
  gap: 7px;
  padding: 11px 13px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
  color: #3c3543;
  box-shadow: 0 10px 24px rgba(42, 38, 61, 0.04);
}

.message-row--mine .message-bubble {
  border-color: #c7d2fe;
  background: #eef2ff;
  color: #312e81;
}

.message-row--system .message-bubble {
  border-color: #dbeafe;
  background: #eff6ff;
  color: #075985;
}

.message-bubble p {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
}

.message-bubble time {
  justify-self: end;
  color: #8a8792;
  font-size: 0.78rem;
}

.message-image-link {
  display: block;
  width: min(280px, 100%);
  overflow: hidden;
  border-radius: 8px;
  background: #f8fafc;
}

.message-image-link img,
.image-preview img {
  display: block;
  width: 100%;
  height: auto;
  max-height: 260px;
  object-fit: contain;
}

.message-composer {
  display: grid;
  gap: 13px;
  padding-top: 2px;
}

.composer-tabs {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: fit-content;
  padding: 4px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #fbf9fc;
}

.composer-tabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #746f7a;
  font-weight: 900;
}

.composer-tabs button.active {
  border-color: #d6d1e8;
  background: #ffffff;
  color: #746dc3;
  box-shadow: 0 8px 16px rgba(116, 109, 195, 0.08);
}

.composer-field {
  display: grid;
  gap: 8px;
}

.composer-field > span:first-child {
  color: #3c3543;
  font-weight: 900;
}

.composer-field textarea,
.composer-field input {
  width: 100%;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #393545;
  outline: 0;
  padding: 11px 12px;
  line-height: 1.5;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.composer-field textarea {
  min-height: 104px;
  resize: vertical;
}

.composer-field textarea[aria-invalid="true"],
.composer-field input[aria-invalid="true"] {
  border-color: #ef4444;
}

.composer-field small {
  color: #8a8792;
}

.image-composer {
  display: grid;
  gap: 12px;
}

.image-composer-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(210px, 0.42fr);
  gap: 12px;
  align-items: end;
}

.image-url-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 11px;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #746dc3;
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

.image-preview {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fcff;
  color: #075985;
  font-weight: 900;
}

.image-preview img {
  max-height: 76px;
  border-radius: 8px;
  background: #ffffff;
}

.image-preview button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 32px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #075985;
  font-weight: 900;
}

.composer-actions {
  align-items: center;
}

.composer-actions > span {
  color: #8a8792;
  line-height: 1.5;
}

.primary-action,
.ghost-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  border-radius: 8px;
  font-weight: 900;
}

.primary-action {
  min-width: 112px;
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

.context-column {
  position: sticky;
  top: 118px;
  display: grid;
  gap: 18px;
}

.boundary-list,
.check-list {
  display: grid;
  gap: 9px;
}

.boundary-list span,
.check-list span {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 38px;
  padding: 9px 10px;
  border: 1px solid #e6e2eb;
  border-radius: 8px;
  background: #fbf9fc;
  color: #746f7a;
  font-weight: 900;
  line-height: 1.45;
}

.boundary-list span.active,
.check-list span {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.task-context-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.task-context-list div {
  display: grid;
  gap: 5px;
  padding: 11px;
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #fcfbfd;
}

.task-context-list dt {
  color: #8a8792;
  font-size: 0.84rem;
}

.task-context-list dd {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.45;
}

.context-link {
  width: fit-content;
}

.loading-state,
.empty-state,
.inline-error {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
}

.loading-state {
  color: #746dc3;
  font-weight: 900;
}

.compact-loading {
  min-height: 116px;
}

.empty-state {
  border: 1px dashed #d7d1e5;
  background: #fbf9fc;
  color: #8a8792;
}

.empty-state h2,
.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h2,
.empty-state h3 {
  color: #3c3543;
}

.message-empty {
  min-height: 330px;
}

.chat-empty {
  min-height: 620px;
}

.inline-error,
.field-error {
  color: #991b1b;
}

.inline-error {
  border: 1px solid #fecaca;
  background: #fef2f2;
  line-height: 1.55;
}

.field-error {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 800;
  line-height: 1.45;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1280px) {
  .conversation-layout {
    grid-template-columns: minmax(260px, 0.36fr) minmax(0, 1fr);
  }

  .context-column {
    position: static;
    grid-column: 1 / -1;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .conversation-list-panel {
    position: static;
  }
}

@media (max-width: 980px) {
  .conversation-layout,
  .context-column,
  .page-title-card,
  .image-composer-grid {
    grid-template-columns: 1fr;
  }

  .chat-panel {
    min-height: 620px;
  }

  .message-row {
    max-width: 88%;
  }
}

@media (max-width: 640px) {
  .page-title-card {
    gap: 18px;
    padding: 22px;
  }

  .title-icon {
    width: 64px;
    height: 64px;
  }

  .panel-card {
    padding: 18px;
  }

  .chat-heading,
  .composer-actions,
  .pagination-bar,
  .image-preview {
    display: grid;
  }

  .composer-tabs,
  .primary-action,
  .ghost-action {
    width: 100%;
  }

  .message-thread {
    max-height: none;
  }

  .message-row,
  .message-row--system {
    max-width: 100%;
  }
}
</style>
