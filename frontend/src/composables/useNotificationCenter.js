import { computed, ref } from 'vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const notificationSummaries = ref([])
const summaryLoading = ref(false)
let refreshPromise = null

const UNREAD_POLL_INTERVAL_MS = 30000
const polledSummary = ref({ total: 0, system: 0, private: 0, dirty: false })
let pollTimer = null
let visibilityListener = null

const systemUnreadCount = computed(() => {
  if (polledSummary.value.dirty) return polledSummary.value.system
  return sumUnreadByType((summary) => summary.type === 'SYSTEM')
})
const privateUnreadCount = computed(() => {
  if (polledSummary.value.dirty) return polledSummary.value.private
  return sumUnreadByType((summary) => summary.type !== 'SYSTEM')
})
const unreadCount = computed(() => {
  if (polledSummary.value.dirty) return polledSummary.value.total
  return systemUnreadCount.value + privateUnreadCount.value
})

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
  syncPolledFromFullSummary()
}

function sumUnreadByType(matchesType) {
  return notificationSummaries.value.reduce(
    (sum, summary) => sum + (matchesType(summary) ? Number(summary.unreadCount || 0) : 0),
    0
  )
}

function syncPolledFromFullSummary() {
  polledSummary.value = {
    total: sumUnreadByType(() => true),
    system: sumUnreadByType((s) => s.type === 'SYSTEM'),
    private: sumUnreadByType((s) => s.type !== 'SYSTEM'),
    dirty: true
  }
}

function updateConversationSummary(conversation) {
  const normalized = normalizeNotificationFromConversation(conversation)
  if (!normalized) return

  notificationSummaries.value = notificationSummaries.value.map((summary) =>
    String(summary.conversationId) === String(normalized.conversationId) ? normalized : summary
  )
  syncPolledFromFullSummary()
}

async function refreshNotificationSummary() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated.value) {
    replaceNotificationSummary([])
    polledSummary.value = { total: 0, system: 0, private: 0, dirty: false }
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

async function pollUnreadSummary() {
  const { isAuthenticated } = useAuth()
  if (!isAuthenticated.value || document.visibilityState === 'hidden') return

  try {
    const result = await api.getConversationUnreadCount()
    polledSummary.value = {
      total: Number(result?.total || result?.unreadCount || 0),
      system: Number(result?.system || 0),
      private: Number(result?.private || 0),
      dirty: true
    }
  } catch {
    // Silently ignore polling errors — full summary serves as fallback
  }
}

function startPolling(intervalMs = UNREAD_POLL_INTERVAL_MS) {
  stopPolling()
  pollUnreadSummary()

  pollTimer = setInterval(pollUnreadSummary, intervalMs)

  visibilityListener = () => {
    if (document.visibilityState === 'visible') {
      pollUnreadSummary()
    }
  }
  document.addEventListener('visibilitychange', visibilityListener)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (visibilityListener) {
    document.removeEventListener('visibilitychange', visibilityListener)
    visibilityListener = null
  }
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
  syncPolledFromFullSummary()
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
    refreshNotificationSummary,
    startPolling,
    stopPolling
  }
}
