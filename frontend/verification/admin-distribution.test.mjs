import assert from 'node:assert/strict'
import test, { after, before } from 'node:test'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { MODERATION_STATUS_OPTIONS, TASK_STATUS_OPTIONS } from '../src/constants.js'

let server
let Chart

before(async () => {
  server = await createServer({ server: { middlewareMode: true } })
  Chart = (await server.ssrLoadModule('/src/components/admin/AdminDistributionChart.vue')).default
})
after(async () => { await server?.close() })

test('same status code uses the selected business domain labels', async () => {
  const data = [{ code: 'REJECTED', count: 2 }]
  const report = await renderToString(createSSRApp(Chart, { data, options: MODERATION_STATUS_OPTIONS }))
  const task = await renderToString(createSSRApp(Chart, { data, options: TASK_STATUS_OPTIONS }))
  assert.match(report, /举报驳回/)
  assert.doesNotMatch(report, /未通过/)
  assert.match(task, /未通过/)
  assert.doesNotMatch(task, /举报驳回/)
  assert.match(report, /待处理/)
  assert.match(report, /0%/)
})

test('empty distribution keeps the empty state', async () => {
  const html = await renderToString(createSSRApp(Chart, { data: [], options: TASK_STATUS_OPTIONS }))
  assert.match(html, /暂无数据/)
  assert.doesNotMatch(html, /最高：/)
})

test('admin page compiles with all three chart option bindings', async () => {
  const result = await server.transformRequest('/src/views/AdminView.vue')
  assert.ok(result?.code)
})
