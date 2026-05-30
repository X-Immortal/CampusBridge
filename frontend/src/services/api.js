const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const AUTH_STORAGE_KEY = 'campusbridge.auth'

export class ApiError extends Error {
  constructor(message, status, code, data) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
  }
}

function readAuthSnapshot() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

async function parseResponse(response) {
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const auth = readAuthSnapshot()
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 3500)
  const requestHeaders = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...headers
  }

  if (auth.token) {
    requestHeaders.Authorization = `Bearer ${auth.token}`
  }

  const userId = auth.user?.id || auth.user?.userId || auth.userId
  if (userId) {
    requestHeaders['X-User-Id'] = userId
  }

  let response
  let payload
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: requestHeaders,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal
    })
    payload = await parseResponse(response)
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new ApiError('请求超时，请确认后端服务已启动', 0, 'NETWORK_TIMEOUT')
    }
    throw new ApiError(error.message || '网络请求失败', 0, 'NETWORK_ERROR')
  } finally {
    window.clearTimeout(timeout)
  }

  if (!response.ok) {
    throw new ApiError(
      payload?.message || response.statusText || '请求失败',
      response.status,
      payload?.code,
      payload?.data
    )
  }

  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (!['SUCCESS', 200, '200'].includes(payload.code)) {
      throw new ApiError(payload.message || '业务校验失败', response.status, payload.code, payload.data)
    }
    return payload.data
  }

  return payload
}

function buildQuery(params = {}) {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    query.set(key, String(value))
  })

  const queryString = query.toString()
  return queryString ? `?${queryString}` : ''
}

export default {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),
  getCurrentUser: () => request('/users/me'),
  getMyProfile: () => request('/users/me/profile'),
  updateMyProfile: (body) => request('/users/me/profile', { method: 'PUT', body }),
  getUserProfile: (userId) => request(`/users/${userId}/profile`),
  submitVerification: (body) => request('/verifications', { method: 'POST', body }),
  getVerification: () => request('/verifications'),
  createTask: (body) => request('/tasks', { method: 'POST', body }),
  saveTaskDraft: (body) => request('/tasks/drafts', { method: 'POST', body }),
  updateTask: (taskId, body) => request(`/tasks/${taskId}`, { method: 'PUT', body }),
  cancelTask: (taskId, reason) =>
    request(`/tasks/${taskId}${buildQuery({ reason })}`, { method: 'DELETE' }),
  resendTask: (taskId) => request(`/tasks/${taskId}/resend`, { method: 'POST' }),
  transitionTaskStatus: (taskId, body) =>
    request(`/tasks/${taskId}/status-transitions`, { method: 'POST', body }),
  submitTaskCompletion: (taskId, body) =>
    request(`/tasks/${taskId}/completion`, { method: 'POST', body }),
  confirmTaskCompletion: (taskId) =>
    request(`/tasks/${taskId}/completion/confirm`, { method: 'POST' }),
  rejectTaskCompletion: (taskId, body) =>
    request(`/tasks/${taskId}/completion/reject`, { method: 'POST', body }),
  disputeTaskCompletion: (taskId, body) =>
    request(`/tasks/${taskId}/completion/dispute`, { method: 'POST', body }),
  getTaskReviews: (taskId) => request(`/tasks/${taskId}/reviews`),
  createTaskReview: (taskId, body) =>
    request(`/tasks/${taskId}/reviews`, { method: 'POST', body }),
  getTasks: (params) => request(`/tasks${buildQuery(params)}`),
  getTaskDetail: (taskId) => request(`/tasks/${taskId}`),
  createTaskApplication: (taskId, body) =>
    request(`/tasks/${taskId}/applications`, { method: 'POST', body }),
  getTaskApplications: (taskId) => request(`/tasks/${taskId}/applications`),
  acceptTaskApplication: (taskId, applicationId) =>
    request(`/tasks/${taskId}/applications/${applicationId}/accept`, { method: 'POST' }),
  getConversations: (params) => request(`/conversations${buildQuery(params)}`),
  getConversationMessages: (conversationId, params) =>
    request(`/conversations/${conversationId}/messages${buildQuery(params)}`),
  sendConversationMessage: (conversationId, body) =>
    request(`/conversations/${conversationId}/messages`, { method: 'POST', body })
}

export { API_BASE_URL, AUTH_STORAGE_KEY }
