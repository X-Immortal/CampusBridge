import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import {
  MODERATION_DECISION_OPTIONS,
  MODERATION_STATUS_OPTIONS,
  optionLabel,
  REPORT_TARGET_TYPE_OPTIONS,
  statusTone
} from '../src/constants.js'

function buildResolvePayload(form) {
  const payload = {
    status: form.status,
    decision: form.decision.trim(),
    markTaskDisputed: Boolean(form.markTaskDisputed)
  }

  if (String(form.punishedUserId).trim()) {
    payload.punishedUserId = Number(form.punishedUserId)
  }
  if (String(form.creditChange).trim()) {
    payload.creditChange = Number(form.creditChange)
  }

  return payload
}

function validateReportForm(form) {
  const errors = {}

  if (!String(form.reportId).trim()) errors.reportId = '请填写举报 ID。'
  if (!String(form.decision).trim()) errors.decision = '请填写裁决说明。'
  if (form.decision.trim().length > 500) errors.decision = '裁决说明不能超过 500 个字符。'
  if (form.creditChange !== '' && Number(form.creditChange) === 0) errors.creditChange = '积分变化不能为 0。'
  if (form.creditChange !== '' && !Number.isFinite(Number(form.creditChange))) {
    errors.creditChange = '积分变化必须是数字。'
  }

  return errors
}

function validateCreateReportForm(form) {
  const errors = {}

  if (!form.targetType) errors.targetType = '请选择举报目标类型。'
  if (!String(form.targetId).trim()) errors.targetId = '请填写举报目标 ID。'
  if (!String(form.reason).trim()) errors.reason = '请填写举报原因。'
  if (form.reason.trim().length > 100) errors.reason = '举报原因不能超过 100 个字符。'
  if (form.description.trim().length > 1000) errors.description = '详细描述不能超过 1000 个字符。'

  return errors
}

test('管理员 API 封装路径与接口文档保持一致', async () => {
  const source = await readFile(new URL('../src/services/api.js', import.meta.url), 'utf8')

  assert.match(source, /getPendingVerifications:\s*\(params\)\s*=>\s*request\(`\/admin\/verifications\/pending/)
  assert.match(source, /approveVerification:\s*\(id\)\s*=>\s*request\(`\/admin\/verifications\/\$\{id\}\/approve`,\s*\{\s*method:\s*'POST'/)
  assert.match(source, /rejectVerification:\s*\(id,\s*body\)\s*=>\s*request\(`\/admin\/verifications\/\$\{id\}\/reject`,\s*\{\s*method:\s*'POST'/)
  assert.match(source, /resolveReport:\s*\(reportId,\s*body\)\s*=>\s*request\(`\/admin\/reports\/\$\{reportId\}\/resolve`,\s*\{\s*method:\s*'POST'/)
  assert.match(source, /getPendingModerationTasks:\s*\(\)\s*=>\s*request\('\/admin\/moderation\/tasks\/pending'\)/)
  assert.match(source, /approveModerationTask:\s*\(taskId,\s*body\s*=\s*\{\}\)\s*=>\s*request\(`\/admin\/moderation\/tasks\/\$\{taskId\}\/approve`,\s*\{\s*method:\s*'POST'/)
  assert.match(source, /rejectModerationTask:\s*\(taskId,\s*body\s*=\s*\{\}\)\s*=>\s*request\(`\/admin\/moderation\/tasks\/\$\{taskId\}\/reject`,\s*\{\s*method:\s*'POST'/)
  assert.match(source, /createReport:\s*\(body\)\s*=>\s*request\('\/reports',\s*\{\s*method:\s*'POST'/)
  assert.match(source, /getReports:\s*\(params\)\s*=>\s*request\(`\/reports/)
})

test('举报裁决只展示终局状态，并为状态提供正确标签与色调', () => {
  assert.deepEqual(
    MODERATION_DECISION_OPTIONS.map((option) => option.value),
    ['APPROVED', 'REJECTED', 'RESOLVED', 'PUNISHED']
  )
  assert.equal(optionLabel(MODERATION_STATUS_OPTIONS, 'PUNISHED'), '已处罚')
  assert.equal(statusTone('PUNISHED'), 'danger')
  assert.equal(optionLabel(REPORT_TARGET_TYPE_OPTIONS, 'TASK'), '任务')
})

test('举报裁决表单校验决策说明、举报 ID 和积分变化', () => {
  assert.deepEqual(
    validateReportForm({
      reportId: '',
      status: 'PUNISHED',
      decision: '',
      punishedUserId: '',
      creditChange: '0',
      markTaskDisputed: true
    }),
    {
      reportId: '请填写举报 ID。',
      decision: '请填写裁决说明。',
      creditChange: '积分变化不能为 0。'
    }
  )

  assert.deepEqual(
    validateReportForm({
      reportId: '3',
      status: 'PUNISHED',
      decision: '违规内容属实，扣除信用积分。',
      punishedUserId: '12',
      creditChange: '-20',
      markTaskDisputed: true
    }),
    {}
  )
})

test('举报裁决请求体按文档字段裁剪并转换数值', () => {
  assert.deepEqual(
    buildResolvePayload({
      status: 'PUNISHED',
      decision: ' 经核查举报成立 ',
      punishedUserId: '12',
      creditChange: '-20',
      markTaskDisputed: true
    }),
    {
      status: 'PUNISHED',
      decision: '经核查举报成立',
      punishedUserId: 12,
      creditChange: -20,
      markTaskDisputed: true
    }
  )

  assert.deepEqual(
    buildResolvePayload({
      status: 'RESOLVED',
      decision: '已处理违规内容',
      punishedUserId: '',
      creditChange: '',
      markTaskDisputed: false
    }),
    {
      status: 'RESOLVED',
      decision: '已处理违规内容',
      markTaskDisputed: false
    }
  )
})

test('用户端任务举报表单遵循 /reports 创建接口限制', () => {
  assert.deepEqual(
    validateCreateReportForm({
      targetType: 'TASK',
      targetId: '',
      reason: '',
      description: ''
    }),
    {
      targetId: '请填写举报目标 ID。',
      reason: '请填写举报原因。'
    }
  )

  assert.deepEqual(
    validateCreateReportForm({
      targetType: 'TASK',
      targetId: '10',
      reason: '任务包含违规代考内容',
      description: '任务描述要求替考，并诱导站外联系。'
    }),
    {}
  )
})

test('管理员工作台和任务详情包含核心交互入口', async () => {
  const adminSource = await readFile(new URL('../src/views/AdminView.vue', import.meta.url), 'utf8')
  const taskSource = await readFile(new URL('../src/views/TaskListView.vue', import.meta.url), 'utf8')
  const shellSource = await readFile(new URL('../src/components/DashboardShell.vue', import.meta.url), 'utf8')
  const routerSource = await readFile(new URL('../src/router/index.js', import.meta.url), 'utf8')

  assert.match(routerSource, /path:\s*'\/admin'/)
  assert.match(shellSource, /管理后台/)
  assert.match(adminSource, /activeSection === 'verifications'/)
  assert.match(adminSource, /submitReportResolution/)
  assert.match(adminSource, /moderateTask\(taskManualForm\.taskId,\s*'approve'/)
  assert.match(taskSource, /workflow-entry workflow-entry--report/)
  assert.match(taskSource, /@submit\.prevent="submitReport"/)
  assert.match(taskSource, /api\.createReport/)
  assert.match(taskSource, /api\.getReports/)
})
