import { computed, ref } from 'vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

export const NOTIFICATION_READ_STORAGE_KEY = 'campusbridge.notifications.read'

const SUMMARY_PAGE_SIZE = 20

const notificationSummaries = ref([])
const readIds = ref([])
const summaryLoading = ref(false)
let refreshPromise = null

function normalizePagedItems(result) {
  if (Array.isArray(result)) return result
  return result?.tasks || result?.items || result?.records || result?.content || result?.list || []
}

function currentUserKey() {
  const { currentUserId } = useAuth()
  return String(currentUserId.value || 'anonymous')
}

function readStoredNotificationIds(userKey = currentUserKey()) {
  if (typeof localStorage === 'undefined') return []

  try {
    const payload = JSON.parse(localStorage.getItem(NOTIFICATION_READ_STORAGE_KEY) || '{}')
    return Array.isArray(payload[userKey]) ? payload[userKey].map(String) : []
  } catch {
    return []
  }
}

function writeStoredNotificationIds(ids, userKey = currentUserKey()) {
  if (typeof localStorage === 'undefined') return

  let payload = {}
  try {
    payload = JSON.parse(localStorage.getItem(NOTIFICATION_READ_STORAGE_KEY) || '{}')
  } catch {
    payload = {}
  }

  payload[userKey] = [...new Set(ids.map(String))]
  localStorage.setItem(NOTIFICATION_READ_STORAGE_KEY, JSON.stringify(payload))
}

function normalizeNotificationFromConversation(conversation = {}) {
  const conversationId = conversation.id || conversation.conversationId
  if (!conversationId) return null

  return {
    id: `conversation-${conversationId}`,
    readAt: conversation.readAt || conversation.lastReadAt || null
  }
}

function syncReadIdsFromStorage() {
  readIds.value = readStoredNotificationIds()
}

function replaceNotificationSummary(conversations = []) {
  syncReadIdsFromStorage()
  notificationSummaries.value = conversations
    .map(normalizeNotificationFromConversation)
    .filter(Boolean)
}

function markNotificationIdsRead(ids = []) {
  const nextIds = ids
    .filter((id) => id !== undefined && id !== null && id !== '')
    .map(String)
  if (!nextIds.length) return

  readIds.value = [...new Set([...readIds.value.map(String), ...nextIds])]
  writeStoredNotificationIds(readIds.value)
}

const decoratedNotifications = computed(() =>
  notificationSummaries.value.map((notification) => ({
    ...notification,
    read: Boolean(notification.readAt || readIds.value.includes(String(notification.id)))
  }))
)

const unreadCount = computed(
  () => decoratedNotifications.value.filter((notification) => !notification.read).length
)

async function refreshNotificationSummary() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated.value) {
    replaceNotificationSummary([])
    return []
  }

  if (refreshPromise) return refreshPromise

  summaryLoading.value = true
  refreshPromise = api
    .getConversations({ page: 1, size: SUMMARY_PAGE_SIZE })
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

export function useNotificationCenter() {
  return {
    readIds,
    unreadCount,
    summaryLoading,
    syncReadIdsFromStorage,
    markNotificationIdsRead,
    replaceNotificationSummary,
    refreshNotificationSummary
  }
}
