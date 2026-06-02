import test from 'node:test'
import assert from 'node:assert/strict'
import { URL, URLSearchParams } from 'node:url'
import {
  optionLabel,
  REWARD_TYPE_OPTIONS,
  TASK_CATEGORY_OPTIONS,
  TASK_STATUS_OPTIONS
} from '../src/constants.js'

const defaultFilters = {
  keyword: '',
  category: '',
  campus: '',
  status: 'OPEN',
  rewardType: '',
  requiresProof: '',
  sortBy: 'CREATED_AT_DESC',
  page: 1,
  size: 8
}

const fixtureTasks = [
  {
    id: 10,
    requesterId: 9,
    providerId: null,
    category: 'ERRAND',
    title: '南门快递柜取件',
    description: '下午六点前帮忙从南门快递柜取一只小包裹，取到后放到图书馆一楼服务台。',
    campus: '主校区',
    location: '南门菜鸟驿站',
    deadline: '2026-06-05T17:34:42',
    rewardType: 'POINTS',
    rewardDescription: '12积分',
    completionStandard: '包裹送达图书馆服务台并站内确认',
    requiresProof: 0,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    routeHint: '从南门进校后沿梧桐路直走到图书馆',
    riskDisclosureConfirmed: 0,
    sensitiveReason: null,
    status: 'OPEN',
    createdAt: '2026-05-30T10:00:00',
    updatedAt: '2026-05-30T10:00:00'
  },
  {
    id: 11,
    requesterId: 8,
    providerId: null,
    category: 'AGENCY',
    title: '帮忙提交学院材料',
    description: '明天下午前把纸质材料交到管理学院办公室，需要排队盖章。',
    campus: '主校区',
    location: '管理学院 302',
    deadline: '2026-06-03T15:00:00',
    rewardType: 'CASH',
    rewardDescription: '20元',
    completionStandard: '材料提交成功后回传窗口收据截图',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 1,
    isInstantTask: 0,
    routeHint: null,
    riskDisclosureConfirmed: 0,
    sensitiveReason: null,
    status: 'OPEN',
    createdAt: '2026-05-30T11:20:00',
    updatedAt: '2026-05-30T11:20:00'
  },
  {
    id: 12,
    requesterId: 7,
    providerId: null,
    category: 'AGENCY',
    title: '材料递交协助',
    description: '协助将活动材料递交到指定办公室。',
    campus: '鼓楼校区',
    location: '礼堂入口',
    deadline: '2026-06-04T09:00:00',
    rewardType: 'NEGOTIABLE',
    rewardDescription: '可协商',
    completionStandard: '材料递交完成并站内说明',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 1,
    routeHint: '早上 8:30 前到礼堂入口',
    riskDisclosureConfirmed: 0,
    sensitiveReason: '测试待审核任务展示',
    status: 'PENDING_REVIEW',
    createdAt: '2026-05-30T12:00:00',
    updatedAt: '2026-05-30T12:00:00'
  }
]

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

function applyTaskResult(result, filters = defaultFilters) {
  const items = normalizePagedTasks(result).map(normalizeTask)
  const page = Number(result?.page || filters.page || 1)
  const size = Number(result?.size || filters.size || defaultFilters.size)
  const explicitTotal = Number(result?.total ?? result?.totalElements ?? result?.totalCount ?? result?.count)
  const inferredTotal = (page - 1) * size + items.length + (items.length >= size ? 1 : 0)

  return {
    tasks: items,
    pagination: {
      page,
      size,
      total: Number.isFinite(explicitTotal) ? explicitTotal : inferredTotal
    }
  }
}

function buildTaskQuery(filters = defaultFilters) {
  return {
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

test('任务广场默认筛选请求使用任务文档里的参数名和值', () => {
  const query = buildTaskQuery(defaultFilters)
  const url = new URL(`http://localhost/api/v1/tasks${buildQuery(query)}`)

  assert.equal(url.searchParams.get('status'), 'OPEN')
  assert.equal(url.searchParams.get('sortBy'), 'CREATED_AT_DESC')
  assert.equal(url.searchParams.get('page'), '1')
  assert.equal(url.searchParams.get('size'), '8')
  assert.equal(url.searchParams.has('requiresProof'), false)
})

test('能解析 API 文档的 data.tasks 列表并把 0/1 字段转为布尔值', () => {
  const query = buildTaskQuery(defaultFilters)
  const response = mockListTasks(query)
  const state = applyTaskResult(response.data, defaultFilters)

  assert.equal(state.tasks.length, 2)
  assert.equal(state.pagination.page, 1)
  assert.equal(state.pagination.size, 8)
  assert.equal(state.pagination.total, 2)
  assert.equal(state.tasks[0].requiresProof, false)
  assert.equal(state.tasks[0].onlyInAppContact, true)
  assert.equal(state.tasks[0].allowOfflineContact, false)
})

test('分类、报酬和凭证筛选后只显示匹配任务卡片', () => {
  const filters = {
    ...defaultFilters,
    category: 'AGENCY',
    rewardType: 'CASH',
    requiresProof: 'true'
  }
  const query = buildTaskQuery(filters)
  const response = mockListTasks(query)
  const state = applyTaskResult(response.data, filters)
  const [task] = state.tasks

  assert.equal(state.tasks.length, 1)
  assert.equal(task.id, 11)
  assert.deepEqual(taskCardSnapshot(task), {
    title: '帮忙提交学院材料',
    status: '待接单',
    category: '委托代办',
    location: '主校区 · 管理学院 302',
    reward: '20元',
    proof: '需要完成凭证'
  })
})

test('关键字筛选后任务广场显示快递任务的核心文案', () => {
  const filters = {
    ...defaultFilters,
    keyword: '快递'
  }
  const response = mockListTasks(buildTaskQuery(filters))
  const state = applyTaskResult(response.data, filters)
  const [task] = state.tasks

  assert.equal(state.tasks.length, 1)
  assert.equal(task.id, 10)
  assert.deepEqual(taskCardSnapshot(task), {
    title: '南门快递柜取件',
    status: '待接单',
    category: '跑腿代取',
    location: '主校区 · 南门菜鸟驿站',
    reward: '12积分',
    proof: '不要求凭证'
  })
})

test('待审核任务可以按状态筛出并显示为待审核', () => {
  const filters = {
    ...defaultFilters,
    status: 'PENDING_REVIEW',
    category: 'AGENCY'
  }
  const response = mockListTasks(buildTaskQuery(filters))
  const state = applyTaskResult(response.data, filters)
  const [task] = state.tasks

  assert.equal(state.tasks.length, 1)
  assert.equal(task.id, 12)
  assert.equal(task.isInstantTask, true)
  assert.equal(task.riskDisclosureConfirmed, false)
  assert.equal(taskCardSnapshot(task).status, '待审核')
  assert.equal(taskCardSnapshot(task).category, '委托代办')
})
