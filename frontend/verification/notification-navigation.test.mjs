import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

const root = new URL('../', import.meta.url)

async function source(path) {
  return readFile(new URL(path, root), 'utf8')
}

test('顶部消息提醒统计私信和系统通知并按系统通知优先跳转', async () => {
  const [centerSource, shellSource] = await Promise.all([
    source('src/composables/useNotificationCenter.js'),
    source('src/components/DashboardShell.vue')
  ])

  assert.match(centerSource, /systemUnreadCount/)
  assert.match(centerSource, /privateUnreadCount/)
  assert.match(centerSource, /systemUnreadCount\.value \+ privateUnreadCount\.value/)
  assert.match(shellSource, /if \(systemUnreadCount\.value > 0\) return '\/workspace\/notifications'/)
  assert.match(shellSource, /if \(privateUnreadCount\.value > 0\) return '\/workspace\/messages'/)
  assert.match(shellSource, /:to="notificationTarget"/)
})

test('工作台消息私信和通知中心按各自未读数显示红点', async () => {
  const sectionNavSource = await source('src/components/layout/SectionNav.vue')

  assert.match(sectionNavSource, /item\.key === 'messages'.*privateUnreadCount\.value/)
  assert.match(sectionNavSource, /item\.key === 'notifications'.*systemUnreadCount\.value/)
  assert.match(sectionNavSource, /class="section-nav__unread-dot"/)
  assert.match(sectionNavSource, /background:\s*var\(--color-danger\)/)
})

test('系统任务通知进入工作台任务详情以保留接单者操作', async () => {
  const notificationViewSource = await source('src/views/ReviewNotificationView.vue')

  assert.match(
    notificationViewSource,
    /name: 'workspace-task-detail', params: \{ taskId: selectedNotification\.taskId \}/
  )
  assert.doesNotMatch(
    notificationViewSource,
    /name: 'task-detail', params: \{ taskId: selectedNotification\.taskId \}/
  )
})
