/**
 * 契约测试：只证明一件事——四个人共用的这份词汇表没被悄悄改坏。
 * 跑法：npm run check
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import type { UiCard } from '../src/protocol/types.ts';
import type { StreamEvent } from '../src/transport/events.ts';
import { formatHeartbeat, formatSseFrame } from '../src/transport/sse.ts';
import { isEventKind } from '../src/transport/events.ts';
import {
  isAllowedAction,
  isAllowedBlock,
  isCardKind,
  isKnownDraftField,
  isSideEffectAction
} from '../src/protocol/whitelist.ts';

function readJson(relativePath: string): any {
  return JSON.parse(readFileSync(new URL(relativePath, import.meta.url), 'utf8'));
}

const CARD_FIXTURES: string[] = [
  '../fixtures/card-clarification.json',
  '../fixtures/card-comparison.json',
  '../fixtures/card-confirmation.json',
  '../fixtures/card-progress.json'
];

test('四类卡片齐了，且每张都过白名单', () => {
  const kinds: string[] = [];
  for (const fixturePath of CARD_FIXTURES) {
    const card = readJson(fixturePath) as UiCard;
    kinds.push(card.kind);

    assert.equal(card.schemaVersion, '1.0', fixturePath + ' 版本不对');
    assert.ok(isCardKind(card.kind), fixturePath + ' 卡片类型不在白名单');
    assert.ok(card.cardId.length > 0, fixturePath + ' 缺 cardId');
    assert.ok(card.blocks.length > 0, fixturePath + ' 没有任何 block');
    assert.ok(card.actions.length > 0, fixturePath + ' 没有任何 action');

    for (const block of card.blocks) {
      assert.ok(isAllowedBlock(block.type), fixturePath + ' 出现非白名单组件: ' + block.type);
      if (block.type === 'Field') {
        assert.ok(block.field !== undefined, fixturePath + ' 的 Field 缺 field');
        assert.ok(isKnownDraftField(block.field as string), fixturePath + ' 绑定了不存在的字段: ' + block.field);
      }
      if (block.type === 'Summary') {
        const rows = block.rows ?? [];
        assert.ok(rows.length > 0, fixturePath + ' 的 Summary 是空的');
        for (const row of rows) {
          assert.ok(
            row.source === 'USER' || row.source === 'INFERRED' || row.source === 'DEFAULT',
            fixturePath + ' 的 Summary 来源非法: ' + row.source
          );
        }
      }
      if (block.type === 'Progress') {
        assert.ok(block.step !== undefined && block.state !== undefined, fixturePath + ' 的 Progress 缺 step/state');
      }
    }

    for (const action of card.actions) {
      assert.ok(isAllowedAction(action.actionId), fixturePath + ' 出现非白名单动作: ' + action.actionId);
      if (isSideEffectAction(action.actionId)) {
        assert.equal(action.requiresConfirmation, true, fixturePath + ' 的副作用动作没有要求确认');
      }
    }
  }

  assert.deepEqual(kinds.sort(), ['clarification', 'comparison', 'confirmation', 'progress']);
});

test('确认卡必须带令牌，且令牌四个字段齐全', () => {
  const card = readJson('../fixtures/card-confirmation.json') as UiCard;
  assert.ok(card.token !== undefined, '确认卡缺 token');
  assert.ok(card.token!.value.length > 0);
  assert.ok(card.token!.nonce.length > 0);
  assert.ok(card.token!.paramDigest.length > 0);
  assert.ok(card.token!.expiresAt.length > 0);
  assert.ok(card.paramDigest !== undefined, '确认卡缺 paramDigest');

  let hasConfirmAction = false;
  for (const action of card.actions) {
    if (action.actionId === 'confirm') {
      hasConfirmAction = true;
      assert.equal(action.requiresConfirmation, true);
    }
  }
  assert.ok(hasConfirmAction, '确认卡没有 confirm 动作');
});

test('示例流：序号连续、事件号唯一、五种 kind 都合法', () => {
  const events = readJson('../fixtures/stream-happy.json') as StreamEvent[];
  assert.ok(events.length >= 5);

  const seenIds: string[] = [];
  let expectedSequence = 1;
  for (const event of events) {
    assert.equal(event.protocol, 'campushub.stream.v1');
    assert.equal(event.sequence, expectedSequence, '序号不连续，在 ' + event.eventId);
    expectedSequence += 1;
    assert.ok(seenIds.indexOf(event.eventId) < 0, '事件号重复: ' + event.eventId);
    seenIds.push(event.eventId);
    assert.ok(isEventKind(event.kind), '未知事件类型: ' + event.kind);
  }

  const first = events[0];
  const last = events[events.length - 1];
  assert.equal(first.kind, 'text.delta');
  assert.equal(last.kind, 'stream.end');
  assert.equal(last.payload.reason, 'done');

  for (const event of events) {
    if (event.kind === 'text.delta') {
      assert.ok(typeof event.payload.text === 'string' && event.payload.text.length > 0);
    }
    if (event.kind === 'schema.card') {
      assert.ok(event.payload.card !== undefined, 'schema.card 没有 card');
      assert.ok(isCardKind(event.payload.card!.kind));
    }
    if (event.kind === 'task.snapshot') {
      assert.ok(event.payload.taskId !== undefined);
      assert.ok(event.payload.taskStatus !== undefined);
      assert.ok(event.payload.taskVersion !== undefined);
    }
    if (event.kind === 'tool.progress') {
      assert.ok(event.payload.toolName !== undefined);
      assert.ok(event.payload.status !== undefined);
    }
  }
});

test('SSE 编码能被原样解回来', () => {
  const events = readJson('../fixtures/stream-happy.json') as StreamEvent[];
  for (const event of events) {
    const frame = formatSseFrame(event);
    assert.ok(frame.endsWith('\n\n'), '帧没有以空行结束');
    assert.ok(frame.startsWith('id: ' + event.sequence + '\n'), 'id 行不对');
    assert.ok(frame.indexOf('event: message\n') > 0, '缺 event 行');

    const dataLine = frame.split('\n').filter((line) => line.startsWith('data: '))[0];
    assert.ok(dataLine !== undefined, '缺 data 行');
    const decoded = JSON.parse(dataLine.slice('data: '.length)) as StreamEvent;
    assert.deepEqual(decoded, event);
  }

  assert.equal(formatHeartbeat(), ': ping\n\n');
});

test('恶意卡片会被白名单拦住', () => {
  const card = readJson('../fixtures/bad-card-script.json') as UiCard;

  let blockedBlocks = 0;
  for (const block of card.blocks) {
    if (!isAllowedBlock(block.type)) {
      blockedBlocks += 1;
    }
  }
  assert.equal(blockedBlocks, 1, 'Script 块应该被拦住');

  const confirmAction = card.actions.filter((action) => action.actionId === 'confirm')[0];
  assert.ok(confirmAction !== undefined);
  assert.equal(confirmAction.requiresConfirmation, false, '这张卡的副作用动作故意没要求确认');
  assert.ok(isSideEffectAction(confirmAction.actionId));
});

test('白名单会拒绝外来词', () => {
  assert.equal(isAllowedBlock('Text'), true);
  assert.equal(isAllowedBlock('Script'), false);
  assert.equal(isAllowedBlock('Iframe'), false);

  assert.equal(isAllowedAction('confirm'), true);
  assert.equal(isAllowedAction('delete'), false);

  assert.equal(isSideEffectAction('confirm'), true);
  assert.equal(isSideEffectAction('answer'), false);

  assert.equal(isKnownDraftField('shareDormRoom'), true);
  assert.equal(isKnownDraftField('dormitoryNumber'), false);

  assert.equal(isCardKind('confirmation'), true);
  assert.equal(isCardKind('settings'), false);
});
