import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL, URLSearchParams } from 'node:url'
import {
  optionLabel,
  REWARD_TYPE_OPTIONS,
  TASK_CATEGORY_OPTIONS,
  TASK_STATUS_OPTIONS
} from '../src/constants.js'

const MINE_TASK_FETCH_SIZE = 100

const fixtureTasks = [
  {
    id: 20,
    requesterId: 9,
    providerId: null,
    category: 'ERRAND',
    title: '我的快递代取',
    description: '帮我从南门取快递。',
    campus: '主校区',
    location: '南门菜鸟驿站',
    deadline: '2026-06-05T17:34:42',
    rewardType: 'POINTS',
    rewardDescription: '12积分',
    completionStandard: '送达图书馆一楼服务台',
    requiresProof: 0,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    riskDisclosureConfirmed: 0,
    status: 'OPEN',
    createdAt: '2026-05-30T10:00:00'
  },
  {
    id: 21,
    requesterId: 9,
    providerId: 12,
    category: 'AGENCY',
    title: '我的材料代交',
    description: '帮我交学院纸质材料。',
    campus: '主校区',
    location: '管理学院 302',
    deadline: '2026-06-03T15:00:00',
    rewardType: 'CASH',
    rewardDescription: '20元',
    completionStandard: '回传提交成功截图',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 1,
    isInstantTask: 0,
    riskDisclosureConfirmed: 0,
    status: 'ACCEPTED',
    createdAt: '2026-05-30T11:00:00'
  },
  {
    id: 22,
    requesterId: 9,
    providerId: null,
    category: 'ATTENDANCE_ASSIST',
    title: '我的签到协助草稿',
    description: '草稿内容。',
    campus: '鼓楼校区',
    location: '礼堂入口',
    deadline: '2026-06-04T09:00:00',
    rewardType: 'NEGOTIABLE',
    rewardDescription: '',
    completionStandard: '现场记录完成',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 1,
    riskDisclosureConfirmed: 1,
    status: 'DRAFT',
    createdAt: '2026-05-30T12:00:00'
  },
  {
    id: 23,
    requesterId: 9,
    providerId: 10,
    category: 'ERRAND',
    title: '我的已完成任务',
    description: '已经完成的代取。',
    campus: '主校区',
    location: '图书馆',
    deadline: '2026-06-01T10:00:00',
    rewardType: 'FREE',
    rewardDescription: '',
    completionStandard: '站内确认',
    requiresProof: 0,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    riskDisclosureConfirmed: 0,
    status: 'COMPLETED',
    createdAt: '2026-05-29T09:00:00'
  },
  {
    id: 24,
    requesterId: 3,
    providerId: 9,
    category: 'ERRAND',
    title: '我接到的他人任务',
    description: '这不是我发布的任务。',
    campus: '主校区',
    location: '北门',
    deadline: '2026-06-02T10:00:00',
    rewardType: 'POINTS',
    rewardDescription: '8积分',
    completionStandard: '送达',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    riskDisclosureConfirmed: 0,
    status: 'IN_PROGRESS',
    createdAt: '2026-05-28T09:00:00'
  },
  {
    id: 25,
    requesterId: 4,
    providerId: null,
    category: 'AGENCY',
    title: '其他人发布的任务',
    description: '不应出现在我的任务。',
    campus: '主校区',
    location: '行政楼',
    deadline: '2026-06-08T10:00:00',
    rewardType: 'CASH',
    rewardDescription: '15元',
    completionStandard: '完成',
    requiresProof: 0,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    riskDisclosureConfirmed: 0,
    status: 'OPEN',
    createdAt: '2026-05-27T09:00:00'
  }
]

function createDefaultFilters(routeName) {
  return {
    keyword: '',
    category: '',
    campus: '',
    status: routeName === 'my-tasks' ? '' : 'OPEN',
    rewardType: '',
    requiresProof: '',
    sortBy: 'CREATED_AT_DESC',
    page: 1,
    size: 8
  }
}

function booleanFlag(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value === 1
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes'].includes(normalized)) return true
    if (['false', '0', 'no'].includes(normalized)) return false
  }
  return Boolean(value)
}

function normalizeTask(task = {}) {
  return {
    ...task,
    id: task.id ?? task.taskId,
    requesterId: task.requesterId ?? task.ownerId,
    providerId: task.providerId ?? task.helperId ?? task.acceptedApplicantId,
    category: task.category || task.taskCategory || task.typeKey || 'AGENCY',
    rewardType: task.rewardType || 'FREE',
    status: task.status || 'OPEN',
    requiresProof: booleanFlag(task.requiresProof),
    onlyInAppContact: booleanFlag(task.onlyInAppContact, true),
    allowOfflineContact: booleanFlag(task.allowOfflineContact),
    isInstantTask: booleanFlag(task.isInstantTask),
    riskDisclosureConfirmed: booleanFlag(task.riskDisclosureConfirmed)
  }
}

function normalizePagedTasks(result) {
  if (Array.isArray(result)) return result
  return result?.tasks || result?.items || result?.records || result?.content || result?.list || []
}

function isRequesterTask(task, currentUserId) {
  const requesterId = task.requesterId ?? task.ownerId
  return requesterId !== undefined && requesterId !== null && String(requesterId) === String(currentUserId)
}

function paginateTasks(items, page, size) {
  const normalizedSize = Number(size) > 0 ? Number(size) : createDefaultFilters('my-tasks').size
  const totalPagesForItems = Math.max(1, Math.ceil(items.length / normalizedSize))
  const normalizedPage = Math.min(Math.max(Number(page) || 1, 1), totalPagesForItems)
  const start = (normalizedPage - 1) * normalizedSize

  return {
    page: normalizedPage,
    size: normalizedSize,
    total: items.length,
    items: items.slice(start, start + normalizedSize)
  }
}

function buildTaskQuery(filters, { isMineView = false } = {}) {
  const query = {
    keyword: filters.keyword.trim(),
    category: filters.category,
    campus: filters.campus.trim(),
    status: filters.status,
    rewardType: filters.rewardType,
    requiresProof: filters.requiresProof === '' ? undefined : filters.requiresProof === 'true',
    sortBy: filters.sortBy,
    page: filters.page,
    size: filters.size
  }

  if (isMineView) {
    query.page = 1
    query.size = MINE_TASK_FETCH_SIZE
  }

  return query
}

function mockListTasks(params = {}) {
  let tasks = [...fixtureTasks]
  if (params.keyword) {
    tasks = tasks.filter((task) => `${task.title} ${task.description}`.includes(params.keyword))
  }
  if (params.category) tasks = tasks.filter((task) => task.category === params.category)
  if (params.campus) tasks = tasks.filter((task) => task.campus === params.campus)
  if (params.status) tasks = tasks.filter((task) => task.status === params.status)
  if (params.rewardType) tasks = tasks.filter((task) => task.rewardType === params.rewardType)
  if (params.requiresProof === true) tasks = tasks.filter((task) => task.requiresProof === 1)
  if (params.requiresProof === false) tasks = tasks.filter((task) => task.requiresProof === 0)

  return {
    code: 'SUCCESS',
    message: 'OK',
    data: {
      tasks,
      page: params.page || 1,
      size: params.size || 20
    }
  }
}

function applyMyTaskResult(result, { currentUserId, page, size }) {
  const items = normalizePagedTasks(result).map(normalizeTask)
  const mineItems = items.filter((task) => isRequesterTask(task, currentUserId))
  return paginateTasks(mineItems, page, size)
}

function taskRewardValue(task = {}) {
  if (task.rewardType === 'FREE') return '无偿互助'
  if (task.rewardType === 'NEGOTIABLE') return '双方协商'
  if (task.rewardDescription) return task.rewardDescription
  return optionLabel(REWARD_TYPE_OPTIONS, task.rewardType)
}

function taskCardSnapshot(task = {}) {
  return {
    title: task.title || '未命名任务',
    status: optionLabel(TASK_STATUS_OPTIONS, task.status),
    category: optionLabel(TASK_CATEGORY_OPTIONS, task.category),
    location: `${task.campus || '未填校区'} · ${task.location || '未填地点'}`,
    reward: taskRewardValue(task),
    proof: task.requiresProof ? '需要完成凭证' : '不要求凭证'
  }
}

function buildQueryString(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    query.set(key, String(value))
  })
  return query.toString()
}

test('我的任务页默认不带 OPEN 状态限制，任务广场仍默认 OPEN', () => {
  assert.equal(createDefaultFilters('my-tasks').status, '')
  assert.equal(createDefaultFilters('task-list').status, 'OPEN')
})

test('我的任务页仍按 API 文档调用 GET /tasks 参数，并一次拉取最多 100 条用于本地归属过滤', () => {
  const filters = {
    ...createDefaultFilters('my-tasks'),
    page: 3,
    size: 8,
    category: 'ERRAND'
  }
  const query = buildTaskQuery(filters, { isMineView: true })
  const queryString = buildQueryString(query)

  assert.equal(query.page, 1)
  assert.equal(query.size, 100)
  assert.equal(query.category, 'ERRAND')
  assert.equal(query.status, '')
  assert.equal(queryString.includes('requesterId='), false)
  assert.equal(queryString.includes('ownerId='), false)
})

test('源码中的我的任务查询限制值和测试保持一致', async () => {
  const source = await readFile(new URL('../src/views/TaskListView.vue', import.meta.url), 'utf8')
  assert.match(source, /const MINE_TASK_FETCH_SIZE = 100/)
  assert.match(source, /query\.page = 1/)
  assert.match(source, /query\.size = MINE_TASK_FETCH_SIZE/)
})

test('我的任务只展示当前用户发布的任务，不展示仅作为接单者参与的任务', () => {
  const filters = createDefaultFilters('my-tasks')
  const response = mockListTasks(buildTaskQuery(filters, { isMineView: true }))
  const state = applyMyTaskResult(response.data, {
    currentUserId: 9,
    page: filters.page,
    size: filters.size
  })

  assert.deepEqual(
    state.items.map((task) => task.id),
    [20, 21, 22, 23]
  )
  assert.equal(state.items.some((task) => task.id === 24), false)
  assert.equal(state.items.some((task) => task.id === 25), false)
  assert.equal(state.total, 4)
})

test('我的任务本地分页使用 UI 每页数量，而不是接口聚合请求的 100 条', () => {
  const filters = {
    ...createDefaultFilters('my-tasks'),
    page: 2,
    size: 2
  }
  const response = mockListTasks(buildTaskQuery(filters, { isMineView: true }))
  const state = applyMyTaskResult(response.data, {
    currentUserId: 9,
    page: filters.page,
    size: filters.size
  })

  assert.equal(state.page, 2)
  assert.equal(state.size, 2)
  assert.equal(state.total, 4)
  assert.deepEqual(
    state.items.map((task) => task.id),
    [22, 23]
  )
})

test('我的任务页状态筛选能显示草稿等非 OPEN 状态', () => {
  const filters = {
    ...createDefaultFilters('my-tasks'),
    status: 'DRAFT',
    category: 'ATTENDANCE_ASSIST'
  }
  const response = mockListTasks(buildTaskQuery(filters, { isMineView: true }))
  const state = applyMyTaskResult(response.data, {
    currentUserId: 9,
    page: filters.page,
    size: filters.size
  })
  const [task] = state.items

  assert.equal(state.total, 1)
  assert.equal(task.id, 22)
  assert.equal(task.isInstantTask, true)
  assert.equal(task.riskDisclosureConfirmed, true)
  assert.deepEqual(taskCardSnapshot(task), {
    title: '我的签到协助草稿',
    status: '草稿',
    category: '出勤/签到协助',
    location: '鼓楼校区 · 礼堂入口',
    reward: '双方协商',
    proof: '需要完成凭证'
  })
})

test('我的任务页报酬和凭证筛选后仍会做当前用户发布者过滤', () => {
  const filters = {
    ...createDefaultFilters('my-tasks'),
    rewardType: 'CASH',
    requiresProof: 'true'
  }
  const response = mockListTasks(buildTaskQuery(filters, { isMineView: true }))
  const state = applyMyTaskResult(response.data, {
    currentUserId: 9,
    page: filters.page,
    size: filters.size
  })
  const [task] = state.items

  assert.equal(state.total, 1)
  assert.equal(task.id, 21)
  assert.equal(task.requiresProof, true)
  assert.equal(task.allowOfflineContact, true)
  assert.deepEqual(taskCardSnapshot(task), {
    title: '我的材料代交',
    status: '已选人',
    category: '委托代办',
    location: '主校区 · 管理学院 302',
    reward: '20元',
    proof: '需要完成凭证'
  })
})

test('我的任务页关键字筛选后正常显示任务卡片核心内容', () => {
  const filters = {
    ...createDefaultFilters('my-tasks'),
    keyword: '快递'
  }
  const response = mockListTasks(buildTaskQuery(filters, { isMineView: true }))
  const state = applyMyTaskResult(response.data, {
    currentUserId: 9,
    page: filters.page,
    size: filters.size
  })
  const [task] = state.items

  assert.equal(state.total, 1)
  assert.deepEqual(taskCardSnapshot(task), {
    title: '我的快递代取',
    status: '待接单',
    category: '跑腿代取',
    location: '主校区 · 南门菜鸟驿站',
    reward: '12积分',
    proof: '不要求凭证'
  })
})
