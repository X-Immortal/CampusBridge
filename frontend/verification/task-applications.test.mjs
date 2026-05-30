import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

function numericValue(...values) {
  const value = values.find((item) => item !== undefined && item !== null && item !== '')
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function normalizeCandidate(application = {}) {
  const applicantId = application.applicantId ?? application.userId ?? application.applicant?.id
  const fallbackName = applicantId ? `同学 ${applicantId}` : '候选同学'

  return {
    applicationId: application.applicationId ?? application.id,
    applicantId,
    name:
      application.nickname ||
      application.applicantName ||
      application.username ||
      application.applicant?.nickname ||
      fallbackName,
    username: application.username || application.applicant?.username || '',
    avatarUrl: application.avatarUrl || application.applicant?.avatarUrl || '',
    bio: application.bio || application.applicant?.bio || '',
    campus: application.campus || application.applicant?.campus || '',
    college: application.college || application.department || application.applicant?.college || '',
    verified: Boolean(application.verified ?? application.isVerified ?? application.applicant?.verified),
    creditLevel: Math.max(1, numericValue(application.creditLevel, application.level, application.credit?.level) || 1),
    points: numericValue(application.points, application.creditPoints, application.credit?.points),
    completedCount: numericValue(
      application.completedCount,
      application.completedTasks,
      application.completedTaskCount,
      application.historyCompletedCount
    ),
    message: application.message || '',
    status: String(application.status || 'PENDING').toUpperCase(),
    createdAt: application.createdAt || application.appliedAt || ''
  }
}

function candidateScore(candidate) {
  const statusWeight = candidate.status === 'ACCEPTED' ? 1000 : candidate.status === 'PENDING' ? 500 : 0
  return statusWeight + candidate.creditLevel * 40 + candidate.completedCount * 8 + candidate.points / 20
}

test('接单申请接口路径与任务系统 API 文档一致', async () => {
  const source = await readFile(new URL('../src/services/api.js', import.meta.url), 'utf8')

  assert.match(source, /createTaskApplication:\s*\(taskId,\s*body\)\s*=>\s*request\(`\/tasks\/\$\{taskId\}\/applications`,\s*\{\s*method:\s*'POST'/)
  assert.match(source, /getTaskApplications:\s*\(taskId\)\s*=>\s*request\(`\/tasks\/\$\{taskId\}\/applications`\)/)
  assert.match(source, /acceptTaskApplication:\s*\(taskId,\s*applicationId\)\s*=>\s*request\(`\/tasks\/\$\{taskId\}\/applications\/\$\{applicationId\}\/accept`,\s*\{\s*method:\s*'POST'/)
})

test('候选响应保留完整档案、信用信息和申请留言', () => {
  const candidate = normalizeCandidate({
    applicationId: 3,
    applicantId: 10,
    username: 'chen_yiheng',
    nickname: '陈亦恒',
    avatarUrl: 'https://example.com/avatar/chen-yiheng.png',
    bio: '信息管理专业，熟悉校园打印、资料整理和问卷收集。',
    campus: '主校区',
    college: '管理学院',
    verified: true,
    creditLevel: 2,
    points: 74,
    completedCount: 6,
    message: '我下午会经过南门，可以顺路帮你取到图书馆。',
    status: 'PENDING',
    createdAt: '2026-05-30T11:00:00'
  })

  assert.deepEqual(candidate, {
    applicationId: 3,
    applicantId: 10,
    name: '陈亦恒',
    username: 'chen_yiheng',
    avatarUrl: 'https://example.com/avatar/chen-yiheng.png',
    bio: '信息管理专业，熟悉校园打印、资料整理和问卷收集。',
    campus: '主校区',
    college: '管理学院',
    verified: true,
    creditLevel: 2,
    points: 74,
    completedCount: 6,
    message: '我下午会经过南门，可以顺路帮你取到图书馆。',
    status: 'PENDING',
    createdAt: '2026-05-30T11:00:00'
  })
})

test('候选排序优先展示已选中和待处理申请，再参考信用与完成数', () => {
  const candidates = [
    normalizeCandidate({ applicationId: 1, applicantId: 8, status: 'REJECTED', creditLevel: 9, points: 300 }),
    normalizeCandidate({ applicationId: 2, applicantId: 9, status: 'PENDING', creditLevel: 2, points: 80 }),
    normalizeCandidate({ applicationId: 3, applicantId: 10, status: 'ACCEPTED', creditLevel: 1, points: 10 })
  ].sort((current, next) => candidateScore(next) - candidateScore(current))

  assert.deepEqual(
    candidates.map((candidate) => candidate.applicationId),
    [3, 2, 1]
  )
})

test('详情弹窗包含申请表、候选列表和选择候选交互', async () => {
  const source = await readFile(new URL('../src/views/TaskListView.vue', import.meta.url), 'utf8')

  assert.match(source, /class="workflow-entry workflow-entry--application"/)
  assert.match(source, /class="panel-card workflow-modal application-panel"/)
  assert.match(source, /applicationPanelOpen/)
  assert.match(source, /@submit\.prevent="submitTaskApplication"/)
  assert.match(source, /maxlength="300"/)
  assert.match(source, /class="workflow-entry workflow-entry--candidates"/)
  assert.match(source, /class="panel-card workflow-modal workflow-modal--wide candidates-section"/)
  assert.match(source, /candidatePanelOpen/)
  assert.match(source, /api\.getTaskApplications\(taskId\)/)
  assert.match(source, /@click="acceptCandidate\(candidate\)"/)
  assert.match(source, /api\.acceptTaskApplication\(detailTask\.value\.id,\s*candidate\.applicationId\)/)
})
