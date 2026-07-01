import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'

const conversationViewUrl = new URL('../src/views/ConversationView.vue', import.meta.url)

test('新会话展示前先加载对方昵称或用户名，且不暴露内部用户编号', async () => {
  const source = await readFile(conversationViewUrl, 'utf8')

  assert.match(source, /nickname:\s*source\.nickname \|\| source\.username \|\| '校园用户'/)
  assert.match(source, /await ensureConversationProfiles\(\[createdConversation\]\)/)
  assert.doesNotMatch(source, /`同学 \$\{conversation\.peerUserId/)
})
