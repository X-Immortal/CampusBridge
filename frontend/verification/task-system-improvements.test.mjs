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

test('共享导航保留首页推荐入口并使用四个一级模块', async () => {
  const navigationSource = await source('src/app/navigation.js')

  assert.match(navigationSource, /key:\s*'discover'[\s\S]*?to:\s*'\/'/)
  assert.match(navigationSource, /export const primaryNavItems/)
  assert.match(navigationSource, /key:\s*'tasks'/)
  assert.match(navigationSource, /key:\s*'posts'/)
  assert.match(navigationSource, /key:\s*'workspace'/)
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

test('社区模块支持加载我的标记并跳转到帖子详情', async () => {
  const [apiSource, postViewSource, navigationSource, routerSource] = await Promise.all([
    source('src/services/api.js'),
    source('src/views/PostView.vue'),
    source('src/app/navigation.js'),
    source('src/router/index.js')
  ])

  assert.match(apiSource, /getMarkedPosts:\s*\(\)\s*=>\s*request\('\/posts\/marked'\)/)
  assert.match(postViewSource, /await api\.getMarkedPosts\(\)/)
  assert.match(navigationSource, /key:\s*'marked'[\s\S]*?label:\s*'我的标记'/)
  assert.match(routerSource, /path:\s*'\/workspace\/marked-posts'[\s\S]*?subSection:\s*'marked'/)
  assert.match(postViewSource, /readQuery\('postId'/)
})
