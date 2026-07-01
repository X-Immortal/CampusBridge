import { ref } from 'vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const notificationSummaries = ref([])
const unreadCount = ref(0)
const summaryLoading = ref(false)
let refreshPromise = null

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
    unreadCount,
    read: unreadCount <= 0,
    readAt: unreadCount > 0 ? null : conversation.lastReadAt || conversation.readAt || null
  }
}

function replaceNotificationSummary(conversations = []) {
  notificationSummaries.value = conversations
    .filter((conversation) => conversation?.type === 'SYSTEM')
    .map(normalizeNotificationFromConversation)
    .filter(Boolean)
  updateUnreadCountFromSummaries()
}

function updateUnreadCountFromSummaries() {
  unreadCount.value = notificationSummaries.value.reduce(
    (sum, notification) => sum + Number(notification.unreadCount || 0),
    0
  )
}

function updateConversationSummary(conversation) {
  const normalized = normalizeNotificationFromConversation(conversation)
  if (!normalized) return

  notificationSummaries.value = notificationSummaries.value.map((summary) =>
    String(summary.conversationId) === String(normalized.conversationId) ? normalized : summary
  )
  updateUnreadCountFromSummaries()
}

async function refreshNotificationSummary() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated.value) {
    replaceNotificationSummary([])
    unreadCount.value = 0
    return []
  }

  if (refreshPromise) return refreshPromise

  summaryLoading.value = true
  refreshPromise = api
    .getConversations({ page: 1, size: 100 })
    .then((result) => {
      const conversations = normalizePagedItems(result).filter((conversation) => conversation?.type === 'SYSTEM')
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
  updateUnreadCountFromSummaries()
  return conversation
}

async function markAllNotificationsRead() {
  const ids = notificationSummaries.value.map((notification) => notification.conversationId).filter(Boolean)
  await Promise.all(ids.map((conversationId) => api.markConversationRead(conversationId)))
  unreadCount.value = 0
  notificationSummaries.value = notificationSummaries.value.map((notification) => ({
    ...notification,
    unreadCount: 0,
    read: true,
    readAt: notification.readAt || new Date().toISOString()
  }))
  return { updatedCount: ids.length, unreadCount: 0 }
}

export function useNotificationCenter() {
  return {
    unreadCount,
    summaryLoading,
    markConversationRead,
    markAllNotificationsRead,
    replaceNotificationSummary,
    refreshNotificationSummary
  }
}
