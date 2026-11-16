/**
 * transport 解析器测试（M2 · 分帧 / UTF-8 / 去重 / 缺口 / 游标）。
 *
 * 被测代码在鸿蒙工程里（app/entry/src/main/ets/transport/sseParser.ts），
 * 但它不 import @ohos.*，所以能直接跑 node --test。
 * 跑法：cd contract && npm run check
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import type { StreamEvent } from '../src/transport/events.ts';
import { makeEvent } from '../src/transport/events.ts';
import { PROTOCOL_VERSION } from '../src/protocol/types.ts';
import { formatHeartbeat, formatSseFrame } from '../src/transport/sse.ts';
import {
  SseFrameParser,
  StreamAssembler,
  Utf8ChunkDecoder
} from '../../app/entry/src/main/ets/transport/sseParser.ts';

function readJson(relativePath: string): StreamEvent[] {
  return JSON.parse(readFileSync(new URL(relativePath, import.meta.url), 'utf8')) as StreamEvent[];
}

function feedText(assembler: StreamAssembler, wire: string): StreamEvent[] {
  const accepted: StreamEvent[] = [];
  for (let i = 0; i < wire.length; i++) {
    const results = assembler.acceptText(wire.charAt(i));
    for (let k = 0; k < results.length; k++) {
      if (results[k].status === 'accepted' && results[k].event !== undefined) {
        accepted.push(results[k].event as StreamEvent);
      }
    }
  }
  return accepted;
}

test('逐字符喂入示例流：分帧正确、序号连续、内容一致', () => {
  const events = readJson('../fixtures/stream-happy.json');
  const sessionId = events[0].sessionId;
  const wire = events.map((event) => formatSseFrame(event)).join('');

  const assembler = new StreamAssembler(sessionId);
  const accepted = feedText(assembler, wire);

  assert.equal(accepted.length, events.length);
  assert.deepEqual(
    accepted.map((event) => event.sequence),
    events.map((event) => event.sequence)
  );
  assert.equal(assembler.lastSequence, events[events.length - 1].sequence);
});

test('多行 data 用换行拼回 JSON，心跳注释被忽略', () => {
  const event = makeEvent('s-multiline', 1, 'text.delta', { text: '先看宿舍楼' });
  const json = JSON.stringify(event);
  const markerIndex = json.indexOf(',"eventId"');
  assert.ok(markerIndex > 0);

  const frame =
    formatHeartbeat() +
    'id: 1\n' +
    'event: message\n' +
    'data: ' + json.slice(0, markerIndex + 1) + '\n' +
    'data: ' + json.slice(markerIndex + 1) + '\n' +
    '\n' +
    ': ping\n\n';

  const parser = new SseFrameParser();
  const frames = parser.feed(frame);
  assert.equal(frames.length, 1);
  assert.deepEqual(JSON.parse(frames[0].data), event);
  assert.equal(parser.pending(), '');

  const assembler = new StreamAssembler('s-multiline');
  const results = assembler.acceptText(frame);
  assert.equal(results.length, 1);
  assert.equal(results[0].status, 'accepted');
  assert.equal(results[0].event?.payload.text, '先看宿舍楼');
});

test('UTF-8 按单字节切开也能还原中文与 emoji', () => {
  const text = '宿舍楼东门取快递🙂，报酬 8 元';
  const wire = 'id: 1\nevent: message\ndata: ' + text + '\n\n';

  const decoder = new Utf8ChunkDecoder();
  let decoded = '';
  const bytes = new TextEncoder().encode(wire);
  for (let i = 0; i < bytes.length; i++) {
    decoded += decoder.push(bytes.subarray(i, i + 1));
  }
  decoded += decoder.flush();
  assert.equal(decoded, wire);
});

test('字节入口：整条流按 1 字节块喂入仍能接纳', () => {
  const events = readJson('../fixtures/stream-happy.json');
  const sessionId = events[0].sessionId;
  const wire = events.map((event) => formatSseFrame(event)).join('');
  const bytes = new TextEncoder().encode(wire);

  const assembler = new StreamAssembler(sessionId);
  const accepted: StreamEvent[] = [];
  for (let i = 0; i < bytes.length; i++) {
    const results = assembler.acceptBytes(bytes.subarray(i, i + 1));
    for (let k = 0; k < results.length; k++) {
      if (results[k].status === 'accepted' && results[k].event !== undefined) {
        accepted.push(results[k].event as StreamEvent);
      }
    }
  }

  assert.equal(accepted.length, events.length);
  assert.equal(accepted[0].payload.text, events[0].payload.text);
});

test('同一 eventId 重发只接纳一次', () => {
  const event = makeEvent('s-dup', 1, 'text.delta', { text: 'hi' });
  const frame = formatSseFrame(event);

  const assembler = new StreamAssembler('s-dup');
  const first = assembler.acceptText(frame);
  const second = assembler.acceptText(frame);

  assert.equal(first[0].status, 'accepted');
  assert.equal(second[0].status, 'duplicate');
  assert.equal(assembler.lastSequence, 1);
});

test('跳号判缺口，补上后继续接纳', () => {
  const first = makeEvent('s-gap', 1, 'text.delta', { text: 'a' });
  const third = makeEvent('s-gap', 3, 'text.delta', { text: 'c' });
  const second = makeEvent('s-gap', 2, 'text.delta', { text: 'b' });

  const assembler = new StreamAssembler('s-gap');
  assert.equal(assembler.acceptEvent(first).status, 'accepted');

  const gap = assembler.acceptEvent(third);
  assert.equal(gap.status, 'gap');
  assert.equal(gap.expectedSequence, 2);
  assert.equal(assembler.lastSequence, 1);

  assert.equal(assembler.acceptEvent(second).status, 'accepted');
  assert.equal(assembler.acceptEvent(third).status, 'accepted');
  assert.equal(assembler.lastSequence, 3);
});

test('after 游标之前的重放事件不重新渲染', () => {
  const events = readJson('../fixtures/stream-happy.json');
  const sessionId = events[0].sessionId;
  const assembler = new StreamAssembler(sessionId, 2);

  assert.equal(assembler.acceptEvent(events[0]).status, 'stale');
  assert.equal(assembler.acceptEvent(events[1]).status, 'stale');
  assert.equal(assembler.acceptEvent(events[2]).status, 'accepted');
  assert.equal(assembler.lastSequence, events[2].sequence);
});

test('版本、类型、会话任一不对都拒绝', () => {
  const assembler = new StreamAssembler('s-check');

  const wrongProtocol = {
    ...makeEvent('s-check', 1, 'text.delta', { text: 'x' }),
    protocol: 'campushub.stream.v2'
  } as unknown as StreamEvent;
  assert.equal(assembler.acceptEvent(wrongProtocol).status, 'protocol_mismatch');

  const wrongKind = {
    ...makeEvent('s-check', 1, 'text.delta', { text: 'x' }),
    kind: 'card.render'
  } as unknown as StreamEvent;
  assert.equal(assembler.acceptEvent(wrongKind).status, 'unknown_kind');

  const wrongSession = makeEvent('s-other', 1, 'text.delta', { text: 'x' });
  assert.equal(assembler.acceptEvent(wrongSession).status, 'session_mismatch');

  assert.equal(assembler.lastSequence, 0);
  assert.equal(PROTOCOL_VERSION, 'campushub.stream.v1');
});

test('坏 JSON 只算一条 invalid，不影响后续帧', () => {
  const good = makeEvent('s-bad', 1, 'text.delta', { text: 'ok' });
  const wire = 'data: {oops}\n\n' + formatSseFrame(good);

  const assembler = new StreamAssembler('s-bad');
  const results = assembler.acceptText(wire);

  assert.equal(results.length, 2);
  assert.equal(results[0].status, 'invalid');
  assert.equal(results[1].status, 'accepted');
  assert.equal(assembler.lastSequence, 1);
});
