import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { useToast } from './useToast'

export function useGuestAction() {
  const { isAuthenticated, isGuest } = useAuth()
  const { showToast } = useToast()
  const route = useRoute()
  const router = useRouter()

  function redirectToAuth(fallbackQuery = {}) {
    const query = { redirect: route.fullPath, ...fallbackQuery }
    if (query.redirect === undefined) delete query.redirect
    if (Object.keys(query).length === 0) {
      router.push({ name: 'auth' })
      return
    }
    router.push({ name: 'auth', query })
  }

  function requireLoginAction(actionName, options = {}) {
    if (isAuthenticated.value) {
      return true
    }

    const message =
      options.message || (actionName ? `${actionName}需要先登录。` : '该操作需要先登录。')
    showToast(message, { type: 'warning' })
    redirectToAuth(options.query || {})
    return false
  }

  function requireAuthenticatedRoute() {
    if (isAuthenticated.value) return true
    showToast('请先登录后再访问该页面。', { type: 'warning' })
    redirectToAuth()
    return false
  }

  return {
    isGuest,
    isAuthenticated,
    requireLoginAction,
    requireAuthenticatedRoute,
    redirectToAuth
  }
}
