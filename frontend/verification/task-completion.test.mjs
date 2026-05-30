import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

function completionAttachmentIds(attachments = []) {
  return attachments
    .map((item) => Number(item.id ?? item.attachmentId))
    .filter((id) => Number.isInteger(id) && id > 0)
}

function buildCompletionPayload({ description, attachments }) {
  return {
    description: description.trim(),
    attachmentIds: completionAttachmentIds(attachments)
  }
}

function canDisputeCompletion({ status, isOwner = false, isProvider = false }) {
  return Boolean((isOwner || isProvider) && ['IN_PROGRESS', 'PENDING_CONFIRMATION'].includes(status))
}

test('附件上传接口使用 /attachments multipart 表单并允许传完成凭证归属', async () => {
  const source = await readFile(new URL('../src/services/api.js', import.meta.url), 'utf8')

  assert.match(source, /body instanceof FormData/)
  assert.match(source, /if \(!isFormData\) \{[\s\S]*?requestHeaders\['Content-Type'\] = 'application\/json'/)
  assert.match(source, /uploadAttachment:\s*\(\{ file,\s*expectedOwnerType,\s*type \} = \{\}\) => \{/)
  assert.match(source, /formData\.append\('file', file\)/)
  assert.match(source, /formData\.append\('expectedOwnerType', expectedOwnerType\)/)
  assert.match(source, /request\('\/attachments',\s*\{\s*method:\s*'POST',\s*body:\s*formData,\s*timeoutMs:\s*15000\s*\}\)/)
})

test('完成凭证请求体只提交说明和有效附件 ID', () => {
  const payload = buildCompletionPayload({
    description: ' 已将包裹送达图书馆一楼服务台。 ',
    attachments: [
      { id: 5, name: '送达照片.jpg' },
      { attachmentId: 6, name: '签收截图.png' },
      { url: 'https://example.com/local-preview-only.png' },
      { id: 0 },
      { id: 'bad' }
    ]
  })

  assert.deepEqual(payload, {
    description: '已将包裹送达图书馆一楼服务台。',
    attachmentIds: [5, 6]
  })
})

test('争议入口允许参与者在进行中或待确认时使用', () => {
  assert.equal(canDisputeCompletion({ status: 'IN_PROGRESS', isProvider: true }), true)
  assert.equal(canDisputeCompletion({ status: 'PENDING_CONFIRMATION', isOwner: true }), true)
  assert.equal(canDisputeCompletion({ status: 'ACCEPTED', isProvider: true }), false)
  assert.equal(canDisputeCompletion({ status: 'COMPLETED', isOwner: true }), false)
  assert.equal(canDisputeCompletion({ status: 'IN_PROGRESS' }), false)
})

test('详情弹窗包含凭证上传、提交、确认、驳回和争议交互', async () => {
  const source = await readFile(new URL('../src/views/TaskListView.vue', import.meta.url), 'utf8')

  assert.match(source, /class="completion-panel"/)
  assert.match(source, /@click="startDetailTask"/)
  assert.match(source, /@change="handleCompletionFiles"/)
  assert.match(source, /expectedOwnerType:\s*'COMPLETION_PROOF'/)
  assert.match(source, /@submit\.prevent="submitCompletionProof"/)
  assert.match(source, /api\.submitTaskCompletion\(detailTask\.value\.id,\s*\{[\s\S]*?description:[\s\S]*?attachmentIds/)
  assert.match(source, /openCompletionResolution\('CONFIRM_COMPLETION'\)/)
  assert.match(source, /openCompletionResolution\('REJECT_COMPLETION'\)/)
  assert.match(source, /openCompletionResolution\('DISPUTE'\)/)
  assert.match(source, /api\.confirmTaskCompletion\(detailTask\.value\.id\)/)
  assert.match(source, /api\.rejectTaskCompletion\(detailTask\.value\.id,\s*\{ reason \}\)/)
  assert.match(source, /api\.disputeTaskCompletion\(detailTask\.value\.id,\s*\{ reason \}\)/)
})
