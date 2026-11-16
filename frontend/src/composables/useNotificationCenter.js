import { computed, ref } from 'vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const systemUnreadCount = ref(0)
const privateUnreadCount = ref(0)
const unreadCount = computed(() => systemUnreadCount.value + privateUnreadCount.value)
const summaryLoading = ref(false)
let refreshPromise = null
let refreshUserId = null
let refreshVersion = 0
let pollTimer = null
let visibilityListener = null

async function refreshNotificationSummary(force = false) {
  const { isAuthenticated, currentUserId } = useAuth()
  if (!isAuthenticated.value) {
    refreshVersion += 1
    systemUnreadCount.value = 0
    privateUnreadCount.value = 0
    refreshPromise = null
    summaryLoading.value = false
    return
  }
  const userId = currentUserId.value
  if (!force && refreshPromise && refreshUserId === userId) return refreshPromise
  const version = ++refreshVersion
  refreshUserId = userId
  summaryLoading.value = true
  refreshPromise = Promise.all([api.getNotificationUnreadCount(), api.getConversationUnreadCount()])
    .then(([notifications, conversations]) => {
      if (!isAuthenticated.value || currentUserId.value !== userId || version !== refreshVersion) return
      systemUnreadCount.value = notifications.unreadCount
      privateUnreadCount.value = conversations.unreadCount
    })
    .catch(() => {
      // Retain the last successful counts when a background refresh fails.
    })
    .finally(() => {
      if (version === refreshVersion) {
        summaryLoading.value = false
        refreshPromise = null
      }
    })
  return refreshPromise
}

function startPolling(intervalMs = 30000) {
  stopPolling()
  const refreshWhenVisible = () => {
    if (document.visibilityState !== 'hidden') refreshNotificationSummary()
  }
  refreshWhenVisible()
  pollTimer = setInterval(refreshWhenVisible, intervalMs)
  visibilityListener = refreshWhenVisible
  document.addEventListener('visibilitychange', visibilityListener)
}

function stopPolling() {
  clearInterval(pollTimer)
  pollTimer = null
  if (visibilityListener) document.removeEventListener('visibilitychange', visibilityListener)
  visibilityListener = null
}

async function markNotificationRead(id) {
  const notification = await api.markNotificationRead(id)
  await refreshNotificationSummary(true)
  return notification
}

async function markAllNotificationsRead() {
  const result = await api.markAllNotificationsRead()
  await refreshNotificationSummary(true)
  return result
}

export function useNotificationCenter() {
  return {
    unreadCount, systemUnreadCount, privateUnreadCount, summaryLoading,
    markNotificationRead, markAllNotificationsRead, refreshNotificationSummary,
    startPolling, stopPolling
  }
}
