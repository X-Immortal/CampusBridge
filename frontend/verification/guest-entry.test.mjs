import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

test('登录页只保留 feature 游客模式入口', async () => {
  const source = await readFile(new URL('../src/views/AuthView.vue', import.meta.url), 'utf8')

  assert.match(source, /@click="continueAsGuest"/)
  assert.match(source, /继续以游客身份浏览/)
  assert.doesNotMatch(source, /enterAsGuest/)
  assert.doesNotMatch(source, /先以游客身份浏览/)
  assert.equal((source.match(/@click="continueAsGuest"/g) || []).length, 1)
})
