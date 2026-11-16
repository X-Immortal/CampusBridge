import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (await readFile(new URL('../src/services/api.js', import.meta.url), 'utf8'))
  .replace('import.meta.env.VITE_API_BASE_URL', 'undefined')
const { default: api, ApiError } = await import(
  'data:text/javascript;base64,' + Buffer.from(source).toString('base64')
)

for (const [status, code] of [
  [400, 'VERIFY_001_INVALID_INPUT'],
  [404, 'VERIFY_004_NOT_FOUND'],
  [409, 'VERIFY_002_ALREADY_PENDING_OR_VERIFIED'],
  [502, 'VERIFY_005_EMAIL_SEND_FAILED']
]) {
  test('verification error ' + status + ' preserves server code and message', async (context) => {
    context.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
      code, message: '认证请求失败', details: null,
      meta: { requestId: 'test-request', timestamp: '2026-11-16T12:00:00' }
    }), { status, headers: { 'Content-Type': 'application/json' } }))
    const originalWindow = globalThis.window
    const originalStorage = globalThis.localStorage
    globalThis.window = { setTimeout, clearTimeout }
    globalThis.localStorage = { getItem: () => null }
    try {
      await assert.rejects(api.submitVerification({}), (error) => {
        assert.ok(error instanceof ApiError)
        assert.equal(error.status, status)
        assert.equal(error.code, code)
        assert.equal(error.message, '认证请求失败')
        return true
      })
    } finally {
      globalThis.window = originalWindow
      globalThis.localStorage = originalStorage
    }
  })
}


test('post update uses PUT and transmits the complete edit body', async (context) => {
  const body = { title: 'New title', content: 'New content', status: 'PUBLISHED' }
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.ok(url.endsWith('/posts/7'))
    assert.equal(options.method, 'PUT')
    assert.deepEqual(JSON.parse(options.body), body)
    return new Response(JSON.stringify({ code: 'SUCCESS', data: { id: 7, ...body } }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    })
  })
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  globalThis.window = { setTimeout, clearTimeout }
  globalThis.localStorage = { getItem: () => null }
  try {
    const result = await api.updatePost(7, body)
    assert.equal(result.title, body.title)
  } finally {
    globalThis.window = originalWindow
    globalThis.localStorage = originalStorage
  }
})

test('login omits stale token while protected requests still carry it', async (context) => {
  const requests = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    requests.push({ url, headers: options.headers })
    return new Response(JSON.stringify({ code: 'SUCCESS', data: {} }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    })
  })
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  globalThis.window = { setTimeout, clearTimeout }
  globalThis.localStorage = { getItem: () => JSON.stringify({ token: 'expired-token' }) }
  try {
    await api.login({ username: 'alice', password: 'pass123' })
    await api.getNotificationUnreadCount()
    assert.ok(requests[0].url.endsWith('/auth/login'))
    assert.equal(requests[0].headers.Authorization, undefined)
    assert.equal(requests[1].headers.Authorization, 'Bearer expired-token')
  } finally {
    globalThis.window = originalWindow
    globalThis.localStorage = originalStorage
  }
})
