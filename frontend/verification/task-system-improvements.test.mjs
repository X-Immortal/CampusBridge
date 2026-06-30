import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

const root = new URL('../', import.meta.url)

async function source(path) {
  return readFile(new URL(path, root), 'utf8')
}

test('首页推荐使用可直达的 postId 参数', async () => {
  const [homeSource, postSource] = await Promise.all([
    source('src/views/HomePageView.vue'),
    source('src/views/PostView.vue')
  ])

  assert.match(homeSource, /query:\s*\{\s*postId:/)
  assert.match(postSource, /readQuery\('postId'/)
})

test('个人主页侧边栏保留首页推荐入口', async () => {
  const homeViewSource = await source('src/views/HomeView.vue')

  assert.match(homeViewSource, /\{\s*label:\s*'首页推荐',\s*icon:\s*Home,\s*to:\s*'\/'/)
  assert.match(homeViewSource, /\{\s*label:\s*'个人主页',\s*icon:\s*Home,\s*active:\s*true,\s*to:\s*'\/home'/)
})

test('个人主页壳层尺寸与共享侧边栏保持一致，避免切页时轻微缩放', async () => {
  const homeViewSource = await source('src/views/HomeView.vue')

  assert.match(homeViewSource, /--sidebar-width:\s*293px/)
  assert.match(homeViewSource, /--topbar-height:\s*76px/)
  assert.match(homeViewSource, /background:\s*var\(--color-bg\)/)
  assert.match(homeViewSource, /@media\s*\(max-width:\s*1100px\)\s*\{\s*\.dashboard-shell\s*\{\s*--sidebar-width:\s*252px;/)
  assert.match(homeViewSource, /\.dashboard-shell\.is-collapsed\s*\{\s*--sidebar-width:\s*78px;/)
  assert.doesNotMatch(homeViewSource, /font-size:\s*15px/)
  assert.doesNotMatch(homeViewSource, /letter-spacing:\s*0\.02em/)
})

test('草稿删除使用专用接口，任务广场请求服务端排除过期任务', async () => {
  const [apiSource, taskListSource] = await Promise.all([
    source('src/services/api.js'),
    source('src/views/TaskListView.vue')
  ])

  assert.match(apiSource, /deleteTaskDraft:.*\/draft/)
  assert.match(taskListSource, /api\.deleteTaskDraft/)
  assert.match(taskListSource, /excludeExpired:\s*!isMineView\.value/)
  assert.doesNotMatch(taskListSource, /Filter out tasks with expired deadlines/)
})

test('送达地点会在任务卡片和详情中展示', async () => {
  const taskListSource = await source('src/views/TaskListView.vue')
  const matches = taskListSource.match(/task\.deliveryLocation|detailTask\.deliveryLocation/g) || []
  assert.ok(matches.length >= 4)
})

test('用户资料私信入口传递完整会话上下文', async () => {
  const profileSource = await source('src/components/UserProfileModal.vue')

  assert.match(profileSource, /peerUserId:\s*String\(props\.userId\)/)
  assert.match(profileSource, /relatedType:\s*props\.relatedType/)
  assert.match(profileSource, /relatedId:\s*String\(props\.relatedId\)/)
})

test('个人主页支持加载已标记帖子并跳转到帖子详情', async () => {
  const [apiSource, homeViewSource] = await Promise.all([
    source('src/services/api.js'),
    source('src/views/HomeView.vue')
  ])

  assert.match(apiSource, /getMarkedPosts:\s*\(\)\s*=>\s*request\('\/posts\/marked'\)/)
  assert.match(homeViewSource, /await api\.getMarkedPosts\(\)/)
  assert.match(homeViewSource, /router\.push\(\{\s*name:\s*'post-list',\s*query:\s*\{\s*postId:\s*String\(postId\)\s*\}\s*\}\)/)
  assert.match(homeViewSource, /被标记的帖子/)
})
