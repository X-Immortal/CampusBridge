/**
 * domain 草稿模型测试（M2 · 来源标记 / 版本 / 确认守卫 / 旧卡失效）。
 * 被测代码：app/entry/src/main/ets/domain/draftModel.ts（纯 TS）。
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { DraftStore } from '../../app/entry/src/main/ets/domain/draftModel.ts';

const FUTURE = '2026-11-16T10:05:00.000Z';
const NOW = '2026-11-16T10:00:00.000Z';

test('字段来源分得清：用户说的、系统猜的、默认值', () => {
  const store = new DraftStore('s-domain-1');
  store.setUserField('goal', '帮我取三个快递');
  store.setInferredField('location', '宿舍楼东门');
  store.setDefaultField('currency', 'CNY');

  assert.equal(store.getField('goal')?.source, 'USER');
  assert.equal(store.getField('location')?.source, 'INFERRED');
  assert.equal(store.getField('currency')?.source, 'DEFAULT');
  assert.equal(store.hasField('location'), true);
  assert.equal(store.hasField('deadline'), false);
  // 初始版本 1，三次写入后是 4
  assert.equal(store.draft.version, 4);
});

test('同一个字段写入是替换，不是追加', () => {
  const store = new DraftStore('s-domain-2');
  store.setUserField('rewardAmount', '8');
  store.setUserField('rewardAmount', '9');

  let count = 0;
  for (const field of store.draft.fields) {
    if (field.field === 'rewardAmount') {
      count += 1;
      assert.equal(field.value, '9');
    }
  }
  assert.equal(count, 1);
  assert.equal(store.draft.version, 3);
});

test('确认卡绑定的版本落后就出不来', () => {
  const store = new DraftStore('s-domain-3');
  store.setUserField('goal', '取快递');
  assert.throws(() => store.presentConfirmation(1, FUTURE), /确认卡版本落后/);
});

test('出卡后改字段：旧卡立即失效，确认被拒', () => {
  const store = new DraftStore('s-domain-4');
  store.setUserField('goal', '取快递');
  store.presentConfirmation(2, FUTURE);

  assert.equal(store.confirm('token-abc', NOW).ok, true);
  store.setUserField('rewardAmount', '9');

  assert.equal(store.draft.phase, 'CLARIFYING');
  assert.equal(store.isCardStale(2), true);

  const rejected = store.confirm('token-abc', NOW);
  assert.equal(rejected.ok, false);
  assert.equal(rejected.code, 'K13_WRONG_PHASE');
});

test('空令牌、过期令牌都拒', () => {
  const store = new DraftStore('s-domain-5');
  store.setUserField('goal', '取快递');
  store.presentConfirmation(2, '2026-11-16T09:00:00.000Z');

  const empty = store.confirm('', NOW);
  assert.equal(empty.ok, false);
  assert.equal(empty.code, 'K13_MISSING_TOKEN');

  const expired = store.confirm('token-abc', NOW);
  assert.equal(expired.ok, false);
  assert.equal(expired.code, 'K13_TOKEN_EXPIRED');
});

test('正常确认后接服务端快照：任务落库、阶段变 CREATED', () => {
  const store = new DraftStore('s-domain-6');
  store.setUserField('goal', '取三个快递');

  assert.equal(store.confirm('token-abc', NOW).code, 'K13_WRONG_PHASE');

  store.presentConfirmation(2, FUTURE);
  assert.equal(store.confirm('token-abc', NOW).ok, true);

  assert.equal(store.applySnapshot(501, 'OPEN', 2), true);
  assert.equal(store.draft.taskId, 501);
  assert.equal(store.draft.taskStatus, 'OPEN');
  assert.equal(store.draft.phase, 'CREATED');
});

test('比本地旧的 task.snapshot 不覆盖新状态', () => {
  const store = new DraftStore('s-domain-7');
  store.setUserField('goal', '取快递');
  store.presentConfirmation(2, FUTURE);
  store.confirm('token-abc', NOW);

  assert.equal(store.applySnapshot(501, 'OPEN', 2), true);
  store.setUserField('rewardAmount', '12');

  // 版本 2 的快照比本地（3）旧：忽略
  assert.equal(store.applySnapshot(501, 'COMPLETED', 2), false);
  assert.equal(store.draft.taskStatus, 'OPEN');

  // 同版本或更新的快照才生效
  assert.equal(store.applySnapshot(501, 'IN_PROGRESS', 3), true);
  assert.equal(store.draft.taskStatus, 'IN_PROGRESS');
});

test('取消后确认不再可能', () => {
  const store = new DraftStore('s-domain-8');
  store.setUserField('goal', '取快递');
  store.presentConfirmation(2, FUTURE);
  store.cancel();

  assert.equal(store.draft.phase, 'CANCELLED');
  assert.equal(store.confirm('token-abc', NOW).code, 'K13_WRONG_PHASE');
});
