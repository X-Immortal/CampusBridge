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

function buildQuery(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, value)
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
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

async function request(path, { method = 'GET', body, params, headers = {}, formData } = {}) {
  const auth = readAuthSnapshot()
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 3500)
  const requestHeaders = {
    Accept: 'application/json',
    ...headers
  }

  if (!formData) {
    requestHeaders['Content-Type'] = 'application/json'
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
    response = await fetch(`${API_BASE_URL}${path}${buildQuery(params)}`, {
      method,
      headers: requestHeaders,
      body: formData || (body ? JSON.stringify(body) : undefined),
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
    if (payload.code !== 'SUCCESS') {
      throw new ApiError(payload.message || '业务校验失败', response.status, payload.code, payload.data)
    }
    return payload.data
  }

  return payload
}

export function normalizePagedResult(payload, fallbackItems = []) {
  if (!payload) {
    return {
      items: fallbackItems,
      page: 1,
      size: fallbackItems.length,
      total: fallbackItems.length,
      fromFallback: true
    }
  }
  if (Array.isArray(payload)) {
    return {
      items: payload,
      page: 1,
      size: payload.length,
      total: payload.length,
      fromFallback: false
    }
  }
  return {
    items: payload.items || payload.records || [],
    page: payload.page || 1,
    size: payload.size || payload.items?.length || 0,
    total: payload.total || payload.items?.length || 0,
    fromFallback: false
  }
}

export default {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),
  getCurrentUser: () => request('/users/me'),
  getMyProfile: () => request('/users/me/profile'),
  updateMyProfile: (body) => request('/users/me/profile', { method: 'PUT', body }),
  submitVerification: (body) => request('/verifications', { method: 'POST', body }),
  getVerification: () => request('/verifications'),
  listContent: (params) => request('/content', { params }),
  createTask: (body) => request('/tasks', { method: 'POST', body }),
  listTasks: (params) => request('/tasks', { params }),
  getTask: (taskId) => request(`/tasks/${taskId}`),
  updateTask: (taskId, body) => request(`/tasks/${taskId}`, { method: 'PATCH', body }),
  cancelTask: (taskId) => request(`/tasks/${taskId}`, { method: 'DELETE' }),
  applyTask: (taskId, body) => request(`/tasks/${taskId}/applications`, { method: 'POST', body }),
  listApplications: (taskId) => request(`/tasks/${taskId}/applications`),
  acceptApplication: (taskId, applicationId) =>
    request(`/tasks/${taskId}/applications/${applicationId}/accept`, { method: 'POST' }),
  transitionTask: (taskId, body) => request(`/tasks/${taskId}/status-transitions`, { method: 'POST', body }),
  submitCompletion: (taskId, body) => request(`/tasks/${taskId}/completion`, { method: 'POST', body }),
  createReview: (taskId, body) => request(`/tasks/${taskId}/reviews`, { method: 'POST', body }),
  listReviews: (taskId) => request(`/tasks/${taskId}/reviews`),
  listConversations: (params) => request('/conversations', { params }),
  createConversation: (body) => request('/conversations', { method: 'POST', body }),
  listMessages: (conversationId, params) => request(`/conversations/${conversationId}/messages`, { params }),
  sendMessage: (conversationId, body) =>
    request(`/conversations/${conversationId}/messages`, { method: 'POST', body }),
  uploadAttachment: (file, expectedOwnerType, type = 'IMAGE') => {
    const formData = new FormData()
    formData.set('file', file)
    if (expectedOwnerType) formData.set('expectedOwnerType', expectedOwnerType)
    if (type) formData.set('type', type)
    return request('/attachments', { method: 'POST', formData })
  },
  createReport: (body) => request('/reports', { method: 'POST', body }),
  listReports: (params) => request('/reports', { params })
}

export { API_BASE_URL, AUTH_STORAGE_KEY }
