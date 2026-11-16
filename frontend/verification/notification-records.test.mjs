import assert from 'node:assert/strict'
import test, { before, after } from 'node:test'
import { createServer } from 'vite'
import { presentNotification } from '../src/constants/notifications.js'

let server
let api
let center
before(async () => {
  globalThis.localStorage = {
    getItem: () => JSON.stringify({ token: 'test', user: { id: 1 } }),
    setItem: () => {}, removeItem: () => {}
  }
  server = await createServer({ server: { middlewareMode: true }, optimizeDeps: { noDiscovery: true, include: [] } })
  api = (await server.ssrLoadModule('/src/services/api.js')).default
  center = (await server.ssrLoadModule('/src/composables/useNotificationCenter.js')).useNotificationCenter()
})
after(async () => {
  await server?.close()
  delete globalThis.localStorage
})

test('notification category and tone depend on event type, not words in its body', () => {
  const notice = { id: 1, type: 'POST_REPLY', title: '新回复', content: '评价失败，积分超时',
    relatedType: 'POST', relatedId: 7, readAt: null }
  const view = presentNotification(notice)
  assert.equal(view.category, 'POST')
  assert.equal(view.tone, 'info')
  assert.equal(view.postId, 7)
  assert.equal(view.taskId, null)
  assert.equal(view.read, false)
  assert.equal(presentNotification({ ...notice, readAt: '2026-11-16T20:00:00' }).read, true)
})

test('unread counts cover all pages and notification read actions leave private count intact', async () => {
  let count = 125
  const readIds = []
  api.getNotificationUnreadCount = async () => ({ unreadCount: count })
  api.getConversationUnreadCount = async () => ({ unreadCount: 4 })
  api.markNotificationRead = async (id) => {
    readIds.push(id)
    count--
    return { id, readAt: '2026-11-16T20:00:00' }
  }
  api.markAllNotificationsRead = async () => {
    const updatedCount = count
    count = 0
    return { updatedCount }
  }
  await center.refreshNotificationSummary()
  assert.equal(center.unreadCount.value, 129)
  await center.markNotificationRead(17)
  assert.deepEqual(readIds, [17])
  assert.equal(center.systemUnreadCount.value, 124)
  assert.equal(center.privateUnreadCount.value, 4)
  const result = await center.markAllNotificationsRead()
  assert.equal(result.updatedCount, 124)
  assert.equal(center.systemUnreadCount.value, 0)
  assert.equal(center.unreadCount.value, 4)
})

test('a stale poll cannot restore unread counts after a completed read action', async () => {
  let finishPoll
  api.getNotificationUnreadCount = () => new Promise(resolve => { finishPoll = resolve })
  const oldPoll = center.refreshNotificationSummary()
  api.markNotificationRead = async id => ({ id, readAt: '2026-11-16T20:00:00' })
  api.getNotificationUnreadCount = async () => ({ unreadCount: 0 })
  await center.markNotificationRead(18)
  finishPoll({ unreadCount: 99 })
  await oldPoll
  assert.equal(center.systemUnreadCount.value, 0)
})

test('notification and private conversation components compile', async () => {
  for (const view of ['ReviewNotificationView', 'ConversationView']) {
    const result = await server.transformRequest('/src/views/' + view + '.vue')
    assert.ok(result?.code)
  }
})
