import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatLocalDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function futureDeadline(minutes = 120) {
  return formatLocalDateTime(new Date(Date.now() + minutes * 60 * 1000))
}

function pastDeadline() {
  return formatLocalDateTime(new Date(Date.now() - 60 * 60 * 1000))
}

function normalizeDeadline(value) {
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
    return `${value}:00`
  }
  return value
}

function optionalText(value = '') {
  const text = value.trim()
  return text || undefined
}

function optionalNumber(value) {
  if (value === '' || value === null || value === undefined) return undefined
  return Number(value)
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

function baseForm(overrides = {}) {
  return {
    category: 'ERRAND',
    title: ' 南门快递柜取件 ',
    description: ' 下午六点前帮忙取一只小包裹，取到后放到图书馆一楼服务台。 ',
    campus: ' 主校区 ',
    location: ' 南门菜鸟驿站 ',
    deadline: futureDeadline(),
    rewardType: 'POINTS',
    rewardDescription: ' 8积分 ',
    rewardAmount: '8',
    completionStandard: ' 包裹送达图书馆服务台并站内确认 ',
    requiresProof: false,
    onlyInAppContact: true,
    allowOfflineContact: false,
    isInstantTask: false,
    routeHint: '',
    riskDisclosureConfirmed: false,
    sensitiveReason: '',
    ...overrides
  }
}

function validateForm(form, { isEditMode = false } = {}) {
  const errors = {}

  function setError(field, message) {
    errors[field] = message
  }

  function validateTextLength(field, value, maxLength, message) {
    if (String(value || '').trim().length > maxLength) {
      setError(field, message)
    }
  }

  if (!form.category) setError('category', '请选择任务分类。')
  if (!form.title.trim()) setError('title', '请填写任务标题。')
  if (!form.description.trim()) setError('description', '请说明任务背景和需要帮助的内容。')
  if (!isEditMode && !form.campus.trim()) setError('campus', '请填写校区。')
  if (!isEditMode && !form.location.trim()) setError('location', '请填写具体地点。')
  if (!form.deadline) setError('deadline', '请选择截止时间。')
  if (!form.rewardType) setError('rewardType', '请选择奖励方式。')
  if (!form.completionStandard.trim()) setError('completionStandard', '请填写完成标准。')

  validateTextLength('title', form.title, 100, '标题最多 100 个字。')
  validateTextLength('description', form.description, 2000, '任务说明最多 2000 个字。')
  validateTextLength('campus', form.campus, 50, '校区最多 50 个字。')
  validateTextLength('location', form.location, 120, '地点最多 120 个字。')
  validateTextLength('rewardDescription', form.rewardDescription, 200, '奖励说明最多 200 个字。')
  validateTextLength('completionStandard', form.completionStandard, 500, '完成标准最多 500 个字。')
  validateTextLength('routeHint', form.routeHint, 300, '顺路信息最多 300 个字。')

  if (form.rewardType === 'POINTS' || form.rewardType === 'CASH') {
    const val = form.rewardAmount
    if (val !== undefined && val !== null && val !== '') {
      const num = Number(val)
      if (!Number.isFinite(num) || num <= 0 || !Number.isInteger(num)) {
        setError('rewardAmount', '请输入有效的正整数。')
      } else if (form.rewardType === 'POINTS' && num > 10) {
        setError('rewardAmount', '积分数量不能超过 10。')
      }
    }
  }

  if (form.deadline) {
    const deadline = new Date(form.deadline)
    if (Number.isNaN(deadline.getTime()) || deadline <= new Date()) {
      setError('deadline', '截止时间必须晚于当前时间。')
    }
  }

  if (!isEditMode && form.isInstantTask && !form.routeHint.trim()) {
    setError('routeHint', '开启顺路/即时协助时，请填写顺路信息。')
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors
  }
}

function hasDraftContent(form) {
  return [
    form.title,
    form.description,
    form.campus,
    form.location,
    form.rewardDescription,
    form.completionStandard,
    form.routeHint,
    form.sensitiveReason
  ].some((value) => String(value || '').trim())
}

function buildPayload(form) {
  return {
    category: form.category,
    title: form.title.trim(),
    description: form.description.trim(),
    campus: form.campus.trim(),
    location: form.location.trim(),
    deadline: normalizeDeadline(form.deadline),
    rewardType: form.rewardType,
    rewardDescription: optionalText(form.rewardDescription),
    rewardAmount: optionalNumber(form.rewardAmount),
    completionStandard: form.completionStandard.trim(),
    requiresProof: form.requiresProof,
    onlyInAppContact: form.onlyInAppContact,
    allowOfflineContact: form.allowOfflineContact,
    isInstantTask: form.isInstantTask,
    routeHint: form.isInstantTask ? optionalText(form.routeHint) : undefined,
    riskDisclosureConfirmed: form.riskDisclosureConfirmed,
    sensitiveReason: optionalText(form.sensitiveReason),
    attachmentIds: []
  }
}

function buildDraftPayload(form) {
  return {
    category: form.category || undefined,
    title: optionalText(form.title),
    description: optionalText(form.description),
    campus: optionalText(form.campus),
    location: optionalText(form.location),
    deadline: form.deadline ? normalizeDeadline(form.deadline) : undefined,
    rewardType: form.rewardType || undefined,
    rewardDescription: optionalText(form.rewardDescription),
    rewardAmount: optionalNumber(form.rewardAmount),
    completionStandard: optionalText(form.completionStandard),
    requiresProof: form.requiresProof,
    onlyInAppContact: form.onlyInAppContact,
    allowOfflineContact: form.allowOfflineContact,
    isInstantTask: form.isInstantTask,
    routeHint: optionalText(form.routeHint),
    riskDisclosureConfirmed: form.riskDisclosureConfirmed,
    sensitiveReason: optionalText(form.sensitiveReason),
    attachmentIds: []
  }
}

function buildUpdatePayload(form) {
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    deadline: normalizeDeadline(form.deadline),
    rewardType: form.rewardType,
    rewardDescription: optionalText(form.rewardDescription),
    rewardAmount: optionalNumber(form.rewardAmount),
    completionStandard: form.completionStandard.trim(),
    requiresProof: form.requiresProof,
    onlyInAppContact: form.onlyInAppContact,
    allowOfflineContact: form.allowOfflineContact
  }
}

function normalizeTaskResult(result) {
  const task = result?.task || result || {}
  return {
    ...task,
    requiresProof: booleanFlag(task.requiresProof, true),
    onlyInAppContact: booleanFlag(task.onlyInAppContact, true),
    allowOfflineContact: booleanFlag(task.allowOfflineContact),
    isInstantTask: booleanFlag(task.isInstantTask),
    riskDisclosureConfirmed: booleanFlag(task.riskDisclosureConfirmed)
  }
}

function publishGate({ isAuthenticated, verificationStatus }) {
  if (!isAuthenticated) return '登录后才能发布任务'
  if (verificationStatus !== 'VERIFIED') return '请先完成校内认证'
  return null
}

function createApiRecorder() {
  const calls = []

  return {
    calls,
    createTask(body) {
      calls.push({ method: 'createTask', body })
      return Promise.resolve({
        id: 101,
        status: 'OPEN',
        ...body,
        requiresProof: body.requiresProof ? 1 : 0,
        onlyInAppContact: body.onlyInAppContact ? 1 : 0,
        allowOfflineContact: body.allowOfflineContact ? 1 : 0,
        isInstantTask: body.isInstantTask ? 1 : 0,
        riskDisclosureConfirmed: body.riskDisclosureConfirmed ? 1 : 0
      })
    },
    saveTaskDraft(body) {
      calls.push({ method: 'saveTaskDraft', body })
      return Promise.resolve({ id: 102, status: 'DRAFT', ...body })
    },
    updateTask(taskId, body) {
      calls.push({ method: 'updateTask', taskId, body })
      return Promise.resolve({ id: Number(taskId), status: 'OPEN', ...body })
    },
    resendTask(taskId) {
      calls.push({ method: 'resendTask', taskId })
      return Promise.resolve({ id: Number(taskId), status: 'OPEN' })
    }
  }
}

async function simulateSubmitTask({
  mode = 'create',
  form = baseForm(),
  api = createApiRecorder(),
  sourceTaskId = '88',
  sourceTask = mode === 'create' ? null : { id: Number(sourceTaskId) },
  isAuthenticated = true,
  verificationStatus = 'VERIFIED',
  loadingSource = false
} = {}) {
  if (loadingSource) {
    return { feedback: '原任务仍在加载，请稍后再保存。', type: 'danger', api }
  }

  if ((mode === 'edit' || mode === 'repost') && !sourceTask) {
    return { feedback: '没有可用的原任务内容，请返回任务详情后重试。', type: 'danger', api }
  }

  const gate = publishGate({ isAuthenticated, verificationStatus })
  if (gate) return { feedback: gate, type: 'danger', api }

  const validation = validateForm(form, { isEditMode: mode === 'edit' })
  if (!validation.valid) {
    return { feedback: '请先修正表单中的提示项。', type: 'danger', errors: validation.errors, api }
  }

  if (mode === 'edit') {
    const updatedTask = await api.updateTask(sourceTaskId, buildUpdatePayload(form))
    return {
      feedback: '任务修改已保存，返回详情后可以继续处理候选和状态。',
      lastTask: normalizeTaskResult(updatedTask),
      api
    }
  }

  if (mode === 'repost') {
    const resentTask = await api.resendTask(sourceTaskId)
    return {
      feedback: '任务已重新发布，新的接单流程已经开始。',
      lastTask: normalizeTaskResult(resentTask),
      api
    }
  }

  const createdTask = await api.createTask(buildPayload(form))
  return {
    feedback: '任务已发布，等待合适的同学申请接单。',
    lastTask: normalizeTaskResult(createdTask),
    api
  }
}

async function simulateSaveDraft({
  form = baseForm(),
  api = createApiRecorder(),
  isAuthenticated = true,
  verificationStatus = 'VERIFIED'
} = {}) {
  if (!hasDraftContent(form)) {
    return { feedback: '至少填写标题、说明、地点或完成标准中的一项后再保存草稿。', type: 'danger', api }
  }

  const gate = publishGate({ isAuthenticated, verificationStatus })
  if (gate) {
    return { feedback: '草稿已保存在本机，可在完成登录和认证后继续发布。', localOnly: true, api }
  }

  const serverDraft = await api.saveTaskDraft(buildDraftPayload(form))
  return {
    feedback: '草稿已保存到任务系统，本机也保留了一份便于继续编辑。',
    lastTask: normalizeTaskResult(serverDraft),
    api
  }
}

test('API 封装使用任务发布文档里的端点和 HTTP 方法', async () => {
  const source = await readFile(new URL('../src/services/api.js', import.meta.url), 'utf8')

  assert.match(source, /createTask:\s*\(body\)\s*=>\s*request\('\/tasks',\s*\{\s*method:\s*'POST'/)
  assert.match(source, /saveTaskDraft:\s*\(body\)\s*=>\s*request\('\/tasks\/drafts',\s*\{\s*method:\s*'POST'/)
  assert.match(source, /updateTask:\s*\(taskId,\s*body\)\s*=>\s*request\(`\/tasks\/\$\{taskId\}`,\s*\{\s*method:\s*'PUT'/)
  assert.match(source, /resendTask:\s*\(taskId\)\s*=>\s*request\(`\/tasks\/\$\{taskId\}\/resend`,\s*\{\s*method:\s*'POST'/)
})

test('创建模式会校验必填项、未来截止时间和即时任务路线', () => {
  const emptyResult = validateForm(
    baseForm({
      category: '',
      title: '',
      description: '',
      campus: '',
      location: '',
      deadline: '',
      rewardType: '',
      completionStandard: ''
    })
  )

  assert.equal(emptyResult.valid, false)
  assert.deepEqual(Object.keys(emptyResult.errors).sort(), [
    'campus',
    'category',
    'completionStandard',
    'deadline',
    'description',
    'location',
    'rewardType',
    'title'
  ])

  const instantResult = validateForm(baseForm({ isInstantTask: true, routeHint: '' }))
  assert.equal(instantResult.valid, false)
  assert.equal(instantResult.errors.routeHint, '开启顺路/即时协助时，请填写顺路信息。')

  const pastResult = validateForm(baseForm({ deadline: pastDeadline() }))
  assert.equal(pastResult.valid, false)
  assert.equal(pastResult.errors.deadline, '截止时间必须晚于当前时间。')

  const pointsResult = validateForm(baseForm({ rewardAmount: '11' }))
  assert.equal(pointsResult.valid, false)
  assert.equal(pointsResult.errors.rewardAmount, '积分数量不能超过 10。')
})

test('积分数量输入框提示不超过 10', async () => {
  const source = await readFile(new URL('../src/views/TaskPublishView.vue', import.meta.url), 'utf8')

  assert.match(source, /不超过10/)
  assert.match(source, /积分数量不能超过 10。/)
})

test('编辑模式不要求重新提交分类、校区和地点，但仍校验可编辑字段', () => {
  const result = validateForm(
    baseForm({
      campus: '',
      location: '',
      title: '更新后的标题',
      description: '更新后的描述',
      completionStandard: '更新后的完成标准'
    }),
    { isEditMode: true }
  )

  assert.equal(result.valid, true)
  assert.equal(result.errors.campus, undefined)
  assert.equal(result.errors.location, undefined)
})

test('发布普通任务会生成文档要求的请求体并清理表单空白', () => {
  const payload = buildPayload(baseForm())

  assert.deepEqual(payload, {
    category: 'ERRAND',
    title: '南门快递柜取件',
    description: '下午六点前帮忙取一只小包裹，取到后放到图书馆一楼服务台。',
    campus: '主校区',
    location: '南门菜鸟驿站',
    deadline: normalizeDeadline(payload.deadline.slice(0, 16)),
    rewardType: 'POINTS',
    rewardDescription: '8积分',
    rewardAmount: 8,
    completionStandard: '包裹送达图书馆服务台并站内确认',
    requiresProof: false,
    onlyInAppContact: true,
    allowOfflineContact: false,
    isInstantTask: false,
    routeHint: undefined,
    riskDisclosureConfirmed: false,
    sensitiveReason: undefined,
    attachmentIds: []
  })
})

test('即时任务会携带 routeHint，空奖励说明会省略为 undefined', () => {
  const payload = buildPayload(
    baseForm({
      isInstantTask: true,
      routeHint: ' 从南门到图书馆，18:00 前顺路 ',
      rewardType: 'FREE',
      rewardDescription: '   '
    })
  )

  assert.equal(payload.routeHint, '从南门到图书馆，18:00 前顺路')
  assert.equal(payload.rewardDescription, undefined)
  assert.equal(payload.rewardType, 'FREE')
})

test('草稿 payload 允许部分字段为空，并使用草稿接口保存', async () => {
  const api = createApiRecorder()
  const form = baseForm({
    title: '只写标题的草稿',
    description: '',
    campus: '',
    location: '',
    rewardDescription: '',
    completionStandard: '',
    routeHint: ''
  })
  const result = await simulateSaveDraft({ form, api })

  assert.equal(result.feedback, '草稿已保存到任务系统，本机也保留了一份便于继续编辑。')
  assert.equal(api.calls.length, 1)
  assert.equal(api.calls[0].method, 'saveTaskDraft')
  assert.equal(api.calls[0].body.title, '只写标题的草稿')
  assert.equal(api.calls[0].body.description, undefined)
  assert.equal(result.lastTask.status, 'DRAFT')
})

test('未登录或未认证时草稿只保存在本机，不调用后端草稿接口', async () => {
  const api = createApiRecorder()
  const result = await simulateSaveDraft({
    form: baseForm({ title: '本地草稿' }),
    api,
    isAuthenticated: true,
    verificationStatus: 'PENDING'
  })

  assert.equal(result.localOnly, true)
  assert.equal(result.feedback, '草稿已保存在本机，可在完成登录和认证后继续发布。')
  assert.equal(api.calls.length, 0)
})

test('编辑模式只提交 API 允许更新的字段', () => {
  const payload = buildUpdatePayload(
    baseForm({
      category: 'AGENCY',
      campus: '不应提交',
      location: '不应提交',
      isInstantTask: true,
      routeHint: '不应提交',
      sensitiveReason: '不应提交',
      title: '更新后的标题'
    })
  )

  assert.deepEqual(Object.keys(payload).sort(), [
    'allowOfflineContact',
    'completionStandard',
    'deadline',
    'description',
    'onlyInAppContact',
    'requiresProof',
    'rewardAmount',
    'rewardDescription',
    'rewardType',
    'title'
  ])
  assert.equal(payload.title, '更新后的标题')
  assert.equal('category' in payload, false)
  assert.equal('campus' in payload, false)
  assert.equal('location' in payload, false)
  assert.equal('routeHint' in payload, false)
})

test('提交创建、编辑、重发时分别调用 createTask、updateTask、resendTask', async () => {
  const createApi = createApiRecorder()
  const createResult = await simulateSubmitTask({ mode: 'create', api: createApi })

  assert.equal(createResult.feedback, '任务已发布，等待合适的同学申请接单。')
  assert.equal(createApi.calls[0].method, 'createTask')
  assert.equal(createResult.lastTask.status, 'OPEN')
  assert.equal(createResult.lastTask.onlyInAppContact, true)

  const editApi = createApiRecorder()
  const editResult = await simulateSubmitTask({
    mode: 'edit',
    sourceTaskId: '88',
    sourceTask: { id: 88 },
    form: baseForm({ title: '更新后的标题' }),
    api: editApi
  })

  assert.equal(editResult.feedback, '任务修改已保存，返回详情后可以继续处理候选和状态。')
  assert.equal(editApi.calls[0].method, 'updateTask')
  assert.equal(editApi.calls[0].taskId, '88')
  assert.equal(editApi.calls[0].body.title, '更新后的标题')

  const repostApi = createApiRecorder()
  const repostResult = await simulateSubmitTask({
    mode: 'repost',
    sourceTaskId: '77',
    sourceTask: { id: 77 },
    api: repostApi
  })

  assert.equal(repostResult.feedback, '任务已重新发布，新的接单流程已经开始。')
  assert.equal(repostApi.calls.length, 1)
  assert.equal(repostApi.calls[0].method, 'resendTask')
  assert.equal(repostApi.calls[0].taskId, '77')
})

test('未通过发布门禁或源任务缺失时不会调用发布接口', async () => {
  const authApi = createApiRecorder()
  const authResult = await simulateSubmitTask({
    api: authApi,
    isAuthenticated: false,
    verificationStatus: 'UNVERIFIED'
  })

  assert.equal(authResult.feedback, '登录后才能发布任务')
  assert.equal(authApi.calls.length, 0)

  const sourceApi = createApiRecorder()
  const sourceResult = await simulateSubmitTask({
    mode: 'edit',
    api: sourceApi,
    sourceTask: null
  })

  assert.equal(sourceResult.feedback, '没有可用的原任务内容，请返回任务详情后重试。')
  assert.equal(sourceApi.calls.length, 0)
})

test('后端返回的 0/1 任务字段会转换为前端 checkbox 使用的布尔值', () => {
  const task = normalizeTaskResult({
    id: 10,
    requiresProof: 1,
    onlyInAppContact: 0,
    allowOfflineContact: 1,
    isInstantTask: 0,
    riskDisclosureConfirmed: 1
  })

  assert.equal(task.requiresProof, true)
  assert.equal(task.onlyInAppContact, false)
  assert.equal(task.allowOfflineContact, true)
  assert.equal(task.isInstantTask, false)
  assert.equal(task.riskDisclosureConfirmed, true)
})
