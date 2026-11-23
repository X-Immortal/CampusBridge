// M3 故障注入回归：三种故障必须现场能复现，且不能把系统打挂。
//
// 用法：
//   node evals/faults/run-faults.mjs --base http://127.0.0.1:8081
//   node evals/faults/run-faults.mjs --base http://127.0.0.1:8081 --fault-mode model_timeout
//
// ① 模型超时：默认用 context.model=unavailable 模拟；若后端以 ai.fault.mode=model_timeout
//    启动，加 --fault-mode model_timeout 直接打故障开关。
// ② 工具重复调用：同一 token + 同一 idempotencyKey 确认两次，只能建一单。
// ③ 流式中断：收 N 帧后 abort，再按 after 重连，事件不重复、序号连续、能收到 stream.end。
//
// 退出码 0 才算全过。

import assert from 'node:assert/strict';
import {
  confirmAssistant,
  getSessionEvents,
  readSse,
  runFullFlow,
  sequenceOf,
  startAssistant
} from '../lib/api.mjs';

const GOAL = '我脚受伤了，今晚需要取三个快递，但不希望把宿舍号直接告诉陌生人';

function argValue(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const results = [];

function pass(name, detail) {
  results.push({ name, ok: true, detail });
  console.log(`PASS ${name} · ${detail}`);
}

function fail(name, error) {
  results.push({ name, ok: false, detail: error.message });
  console.log(`FAIL ${name} · ${error.message}`);
}

async function checkModelTimeout(base, faultMode) {
  const context = faultMode === 'model_timeout'
    ? { requesterId: 1 }
    : { requesterId: 1, model: 'unavailable' };
  const response = await startAssistant(base, '帮我取个快递', context);
  assert.equal(response.status, 200, `期望 HTTP 200，实际 ${response.status}`);
  const body = response.body;
  assert.equal(body.degraded, true, '期望 degraded=true');
  assert.ok(body.card, '降级后仍应返回卡片');
  assert.equal(body.taskId ?? null, null, '降级不应建单');
  pass(faultMode === 'model_timeout' ? '① 模型超时开关' : '① 模型不可用降级',
    `degraded=true，card=${body.card.kind}，未建单`);
}

async function checkDuplicateTool(base) {
  const flow = await runFullFlow(base, GOAL);
  assert.ok(flow.taskId, '首次确认应建单');
  const replay = await confirmAssistant(
    base, flow.sessionId, flow.token, flow.idempotencyKey);
  assert.equal(replay.status, 200, `重试确认期望 HTTP 200，实际 ${replay.status}`);
  assert.equal(replay.body.outcome, 'OK', '重试确认应返回 OK');
  assert.equal(String(replay.body.taskId), String(flow.taskId), '重试应返回同一 taskId');

  const events = await getSessionEvents(base, flow.sessionId, 200);
  const created = events.filter((event) => event.type === 'task.created'
    && String(event.payload?.taskId) === String(flow.taskId));
  assert.equal(created.length, 1, `task.created 应只有 1 条，实际 ${created.length}`);
  pass('② 工具重复调用', `同一 taskId=${flow.taskId}，task.created=1 条`);
}

async function checkStreamReconnect(base) {
  const flow = await runFullFlow(base, GOAL);
  const first = await readSse(base, flow.sessionId, {
    after: 0,
    abortAfterFrames: 3,
    timeoutMs: 20000
  });
  assert.ok(first.frames.length >= 3, `期望至少 3 帧，实际 ${first.frames.length}`);
  const lastSeq = sequenceOf(first.frames[first.frames.length - 1]);
  assert.ok(Number.isFinite(lastSeq), '首段流缺少 sequence');

  const second = await readSse(base, flow.sessionId, {
    after: lastSeq,
    timeoutMs: 20000
  });
  assert.ok(second.frames.length > 0, '重连后没有收到事件');
  const sequences = second.frames.map(sequenceOf);
  for (let i = 0; i < sequences.length; i += 1) {
    assert.equal(sequences[i], lastSeq + i + 1, `重连序号不连续：${sequences.join(',')}`);
  }
  const last = second.frames[second.frames.length - 1];
  assert.equal(last.data?.kind, 'stream.end', '重连最后一帧应为 stream.end');
  pass('③ 流式中断重连',
    `首段 ${first.frames.length} 帧，重连 after=${lastSeq} 收 ${second.frames.length} 帧，序号连续`);
}

async function main() {
  const base = argValue('base', 'http://127.0.0.1:8081');
  const faultMode = argValue('fault-mode', '');
  const checks = [
    ['model-timeout', () => checkModelTimeout(base, faultMode)],
    ['duplicate-tool', () => checkDuplicateTool(base)],
    ['stream-reconnect', () => checkStreamReconnect(base)]
  ];
  for (const [name, run] of checks) {
    try {
      await run();
    } catch (error) {
      fail(name, error);
    }
  }
  const failed = results.filter((result) => !result.ok).length;
  console.log(`${results.length - failed}/${results.length} 通过`);
  if (failed > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error('故障脚本异常：' + error.message);
  process.exitCode = 1;
});
