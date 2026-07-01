import { computed, reactive } from 'vue'
import api, { AUTH_STORAGE_KEY } from '../services/api'

function readInitialAuth() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

const initialAuth = readInitialAuth()

const authState = reactive({
  token: initialAuth.token || '',
  refreshToken: initialAuth.refreshToken || '',
  user: initialAuth.user || null,
  profile: initialAuth.profile || null,
  verification: initialAuth.verification || null,
  visitorMode: Boolean(initialAuth.visitorMode),
  loading: false,
  error: ''
})

const isAuthenticated = computed(() => Boolean(authState.token))
const isGuest = computed(() => !authState.token && authState.visitorMode)
const currentUserId = computed(() => authState.user?.id || authState.user?.userId || initialAuth.userId || null)
const verificationStatus = computed(() => authState.verification?.status || 'UNVERIFIED')

function persistAuth() {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      token: authState.token,
      refreshToken: authState.refreshToken,
      user: authState.user,
      profile: authState.profile,
      verification: authState.verification,
      visitorMode: authState.visitorMode,
      userId: currentUserId.value
    })
  )
}

function setAuth(payload) {
  authState.token = payload?.token || ''
  authState.refreshToken = payload?.refreshToken || ''
  authState.user =
    payload?.user ||
    (payload
      ? {
          id: payload.userId,
          username: payload.username,
          role: payload.role
        }
      : null)
  authState.visitorMode = false
  authState.error = ''
  persistAuth()
}

function enterGuestMode() {
  authState.token = ''
  authState.refreshToken = ''
  authState.user = {
    id: null,
    username: 'guest',
    role: 'GUEST'
  }
  authState.profile = null
  authState.verification = null
  authState.visitorMode = true
  authState.error = ''
  persistAuth()
}

function exitGuestMode() {
  authState.token = ''
  authState.refreshToken = ''
  authState.user = null
  authState.profile = null
  authState.verification = null
  authState.visitorMode = false
  authState.error = ''
  localStorage.removeItem(AUTH_STORAGE_KEY)
}

async function login(credentials) {
  authState.loading = true
  authState.error = ''
  try {
    const result = await api.login(credentials)
    setAuth(result)
    await refreshSession()
    return result
  } catch (error) {
    authState.error = error.message
    throw error
  } finally {
    authState.loading = false
  }
}

async function register(payload) {
  authState.loading = true
  authState.error = ''
  try {
    return await api.register(payload)
  } catch (error) {
    authState.error = error.message
    throw error
  } finally {
    authState.loading = false
  }
}

async function refreshSession() {
  if (!authState.token) return
  try {
    const [user, profile, verification] = await Promise.allSettled([
      api.getCurrentUser(),
      api.getMyProfile(),
      api.getVerification()
    ])
    if (user.status === 'fulfilled' && user.value) authState.user = user.value
    if (profile.status === 'fulfilled' && profile.value) authState.profile = profile.value
    if (verification.status === 'fulfilled' && verification.value) authState.verification = verification.value
    if (verification.status === 'rejected' && verification.reason?.code === 'VERIFY_004_NOT_FOUND') {
      authState.verification = null
    }
    persistAuth()
  } catch {
    persistAuth()
  }
}

async function submitVerification(payload) {
  const result = await api.submitVerification(payload)
  authState.verification = result
  persistAuth()
  return result
}

async function resendEmailVerificationCode() {
  const result = await api.resendEmailVerificationCode()
  authState.verification = result
  persistAuth()
  return result
}

async function confirmEmailVerification(payload) {
  const result = await api.confirmEmailVerification(payload)
  authState.verification = result
  persistAuth()
  return result
}

async function cancelCurrentVerification() {
  const result = await api.cancelCurrentVerification()
  authState.verification = null
  persistAuth()
  return result
}

async function updateMyProfile(payload) {
  const result = await api.updateMyProfile(payload)
  authState.profile = result
  persistAuth()
  return result
}

function logout() {
  exitGuestMode()
}

export function useAuth() {
  return {
    state: authState,
    isAuthenticated,
    isGuest,
    currentUserId,
    verificationStatus,
    login,
    register,
    logout,
    refreshSession,
    submitVerification,
    resendEmailVerificationCode,
    confirmEmailVerification,
    cancelCurrentVerification,
    updateMyProfile,
    enterGuestMode,
    exitGuestMode
  }
}
