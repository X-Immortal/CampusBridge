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
    if (payload.code !== 'SUCCESS') {
      throw new ApiError(payload.message || '业务校验失败', response.status, payload.code, payload.data)
    }
    return payload.data
  }

  return payload
}

export default {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),
  getCurrentUser: () => request('/users/me'),
  submitVerification: (body) => request('/verifications', { method: 'POST', body }),
  getVerification: () => request('/verifications')
}

export { API_BASE_URL, AUTH_STORAGE_KEY }
