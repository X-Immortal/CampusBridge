import { computed, ref } from 'vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const notificationSummaries = ref([])
const summaryLoading = ref(false)
let refreshPromise = null

const systemUnreadCount = computed(() => sumUnreadByType((summary) => summary.type === 'SYSTEM'))
const privateUnreadCount = computed(() => sumUnreadByType((summary) => summary.type !== 'SYSTEM'))
const unreadCount = computed(() => systemUnreadCount.value + privateUnreadCount.value)

function normalizePagedItems(result) {
  if (Array.isArray(result)) return result
  return result?.tasks || result?.items || result?.records || result?.content || result?.list || []
}

function normalizeNotificationFromConversation(conversation = {}) {
  const conversationId = conversation.id || conversation.conversationId
  if (!conversationId) return null

  const unreadCount = Number(conversation.unreadCount || 0)
  return {
    id: `conversation-${conversationId}`,
    conversationId,
    type: conversation.type || 'PRIVATE',
    unreadCount,
    read: unreadCount <= 0,
    readAt: unreadCount > 0 ? null : conversation.lastReadAt || conversation.readAt || null
  }
}

function replaceNotificationSummary(conversations = []) {
  notificationSummaries.value = conversations
    .map(normalizeNotificationFromConversation)
    .filter(Boolean)
}

function sumUnreadByType(matchesType) {
  return notificationSummaries.value.reduce(
    (sum, summary) => sum + (matchesType(summary) ? Number(summary.unreadCount || 0) : 0),
    0
  )
}

function updateConversationSummary(conversation) {
  const normalized = normalizeNotificationFromConversation(conversation)
  if (!normalized) return

  notificationSummaries.value = notificationSummaries.value.map((summary) =>
    String(summary.conversationId) === String(normalized.conversationId) ? normalized : summary
  )
}

async function refreshNotificationSummary() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated.value) {
    replaceNotificationSummary([])
    return []
  }

  if (refreshPromise) return refreshPromise

  summaryLoading.value = true
  refreshPromise = api
    .getConversations({ page: 1, size: 100 })
    .then((result) => {
      const conversations = normalizePagedItems(result)
      replaceNotificationSummary(conversations)
      return conversations
    })
    .catch(() => {
      replaceNotificationSummary([])
      return []
    })
    .finally(() => {
      summaryLoading.value = false
      refreshPromise = null
    })

  return refreshPromise
}

async function markConversationRead(conversationId) {
  if (!conversationId) return null
  const conversation = await api.markConversationRead(conversationId)
  updateConversationSummary(conversation)
  return conversation
}

async function markAllNotificationsRead() {
  const ids = notificationSummaries.value
    .filter((notification) => notification.type === 'SYSTEM')
    .map((notification) => notification.conversationId)
    .filter(Boolean)
  await Promise.all(ids.map((conversationId) => api.markConversationRead(conversationId)))
  notificationSummaries.value = notificationSummaries.value.map((notification) => ({
    ...notification,
    unreadCount: notification.type === 'SYSTEM' ? 0 : notification.unreadCount,
    read: notification.type === 'SYSTEM' ? true : notification.read,
    readAt:
      notification.type === 'SYSTEM'
        ? notification.readAt || new Date().toISOString()
        : notification.readAt
  }))
  return { updatedCount: ids.length, unreadCount: 0 }
}

export function useNotificationCenter() {
  return {
    unreadCount,
    systemUnreadCount,
    privateUnreadCount,
    summaryLoading,
    markConversationRead,
    markAllNotificationsRead,
    replaceNotificationSummary,
    refreshNotificationSummary
  }
}
