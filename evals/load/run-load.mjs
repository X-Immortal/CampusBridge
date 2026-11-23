// 压测执行器（张-1）
//
// 用法：
//   node run-load.mjs --base http://127.0.0.1:8081 --scenario all --concurrency 50
//   node run-load.mjs --base http://127.0.0.1:8081 --scenario assistant        # 只跑场景①
//   node run-load.mjs --scenario stream --concurrency 20                        # 只跑场景②
//
// 三类场景（对应 team-tasks 张-1）：
//   ① assistant   —— N 路并发跑「完整链路」：开始 → 补全 → 选择 → 确认建单
//   ② stream      —— N 路并发开 SSE 长连接 GET /api/stream，统计事件数与连接时长
//   ③ idempotency —— 同一 idempotencyKey 并发重复确认，断言只产生一个 taskId
//
// 输出：evals/reports/load-<时间戳>.json（P50/P95/P99、错误率、吞吐、各场景配置）
// 前提：后端 demo profile（H2）起着，默认 8081。不要打生产库。

import { writeFile, mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPORTS_DIR = join(HERE, '..', 'reports');

// ---------- 命令行参数 ----------
const args = process.argv.slice(2);
function argOf(name, dflt) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] !== undefined ? args[i + 1] : dflt;
}
const BASE = argOf('--base', 'http://127.0.0.1:8081');
const SCENARIO = argOf('--scenario', 'all');
const CONCURRENCY = Number(argOf('--concurrency', '0')); // 0 = 各场景用自己的默认并发

// 各场景默认并发（团队任务书：① 50、② 20、③ 用 10 次重复）
const DEFAULTS = { assistant: 50, stream: 20, idempotency: 10 };

// ---------- 完整链路要填的示例值（deadline 用未来时间，避开 K04；不写精确宿舍号，避开 K07）----------
const VALUES = {
  deadline: '2026-11-25T21:00:00',
  location: '北门菜鸟驿站',
  deliveryLocation: '三号宿舍楼东门',
  rewardType: 'CASH',
  rewardAmount: 8,
  itemCount: 3,
  timeWindow: '明晚 20:00-22:00',
  shareDormRoom: false,
  riskDisclosureConfirmed: true,
  requiresProof: true,
  onlyInAppContact: true,
  allowOfflineContact: false,
  completionStandard: '送到三号宿舍楼东门并当面交付拍照',
  contactPreference: '站内消息',
  title: '帮取三个快递',
  category: 'ERRAND',
  intent: 'ERRAND_PICKUP',
};
const valueFor = (field) =>
  Object.prototype.hasOwnProperty.call(VALUES, field) ? VALUES[field] : '都可以';

// ---------- 基础 HTTP 帮助函数（带耗时）----------
async function post(path, body) {
  const started = Date.now();
  try {
    const res = await fetch(new URL(path, BASE), {
      method: 'POST',
      headers: { 'content-type': 'application/json; charset=utf-8' },
      body: JSON.stringify(body ?? {}),
    });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* 非 JSON 保留 null */ }
    return { status: res.status, body: json, raw: text, ms: Date.now() - started };
  } catch (e) {
    return { status: 0, error: e.message, ms: Date.now() - started };
  }
}

// ---------- 跑一条完整链路：开始 →（多轮）补全 → 选择 → 确认建单 ----------
// 返回 { ok, flowMs, taskId, terminalState, error }。ok 仅代表真的建出了单。
async function fullFlow(idemPrefix) {
  const started = Date.now();
  const goal = '我脚受伤了，明天需要取三个快递，不希望把宿舍号告诉陌生人';
  const start = await post('/api/assistant', { goal, context: { requesterId: 1 } });
  if (!start.body || !start.body.sessionId) {
    return { ok: false, flowMs: Date.now() - started, error: 'start 失败 ' + start.status };
  }
  const sessionId = start.body.sessionId;
  let cur = start;
  const flow = { sessionId, steps: [start] };

  // 补全：最多 4 轮，把 unknowns 里的字段都填上
  for (let round = 0; round < 4; round++) {
    const unknowns = (cur.body && cur.body.unknowns) || [];
    if (unknowns.length === 0) break;
    const answers = unknowns.map((u) => ({ field: u.field, value: valueFor(u.field) }));
    cur = await post(`/api/assistant/sessions/${sessionId}/answers`, { answers });
    flow.steps.push(cur);
  }

  // 选择：如果当前是 comparison 卡，取第一个选项（默认 now）
  let card = cur.body && cur.body.card;
  if (card && card.kind === 'comparison') {
    let optionId = 'now';
    for (const block of card.blocks || []) {
      if (block.type === 'Choice' && block.options && block.options.length > 0) {
        optionId = block.options[0].id; break;
      }
    }
    cur = await post(`/api/assistant/sessions/${sessionId}/select`, { optionId });
    flow.steps.push(cur);
    card = cur.body && cur.body.card;
  }

  // 确认：拿到 confirmation 卡就用它的 token 建单
  if (!card || card.kind !== 'confirmation' || !card.token) {
    return {
      ok: false, flowMs: Date.now() - started, sessionId,
      taskId: null, terminalState: cur.body && cur.body.state, error: '没走到确认卡',
    };
  }
  const key = `${idemPrefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const confirm = await post(`/api/assistant/sessions/${sessionId}/confirm`, {
    token: card.token.value, idempotencyKey: key,
  });
  flow.steps.push(confirm);
  const ok = confirm.status === 200 && confirm.body && confirm.body.taskId != null;
  return {
    ok, flowMs: Date.now() - started, sessionId,
    taskId: confirm.body ? confirm.body.taskId : null,
    terminalState: confirm.body && confirm.body.state,
    error: ok ? null : 'confirm 失败 ' + confirm.status,
  };
}

// ---------- 场景① N 路并发完整链路 ----------
async function scenarioAssistant(n) {
  const started = Date.now();
  const units = await Promise.all(
    Array.from({ length: n }, (_, i) => fullFlow(`load-a-${i}`))
  );
  const wall = Date.now() - started;
  return summarize(units.map((u) => ({ ms: u.flowMs, failed: !u.ok })), wall, {
    scenario: 'assistant', concurrency: n,
    endpoint: 'POST /api/assistant（完整链路）',
  });
}

// ---------- 打开一条 SSE 连接，读到 stream.end 或超时 ----------
async function oneStream(sessionId, timeoutMs) {
  const started = Date.now();
  let events = 0, ended = false, failed = false;
  try {
    const url = new URL('/api/stream', BASE);
    url.searchParams.set('sessionId', sessionId);
    url.searchParams.set('after', '0');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(url, { signal: controller.signal });
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) { ended = true; break; }
      buf += decoder.decode(value, { stream: true });
      // 按空行切帧，统计带 data: 且是 stream.end 的事件
      const frames = buf.split('\n\n');
      buf = frames.pop(); // 末尾可能是不完整帧，留到下轮
      for (const f of frames) {
        const dataLine = f.split('\n').find((l) => l.startsWith('data:'));
        if (!dataLine) continue;
        try {
          const ev = JSON.parse(dataLine.slice(5).trim());
          events += 1;
          if (ev.kind === 'stream.end') ended = true;
        } catch { /* 心跳/注释帧忽略 */ }
      }
      if (ended) break;
    }
    clearTimeout(timer);
    if (!ended && Date.now() - started >= timeoutMs) failed = true; // 挂死到超时
  } catch (e) {
    failed = true; // abort 也走这里：说明连接在超时窗口内没自然结束
  }
  return { ms: Date.now() - started, events, ended, failed: failed && events === 0 };
}

// ---------- 场景② N 路并发 SSE 长连接 ----------
async function scenarioStream(n) {
  // 先把 n 条会话「跑完整链路」，确保事件日志里有 stream.end ——
  // 否则 GET /api/stream 会因为没有结束帧而一直挂到超时，测出来的就不是真实流式时长。
  const flows = await Promise.all(
    Array.from({ length: n }, (_, i) => fullFlow(`load-s-${i}`))
  );
  const sessionIds = flows.map((f) => f.sessionId).filter(Boolean);
  // 现在并发打开 SSE 长连接，回放各自完整事件流，单独计时
  const started = Date.now();
  const results = await Promise.all(
    sessionIds.map((sid) => oneStream(sid, 5000))
  );
  const wall = Date.now() - started;
  const sum = summarize(
    results.map((r) => ({ ms: r.ms, failed: r.failed })), wall, {
      scenario: 'stream', concurrency: n, endpoint: 'GET /api/stream（SSE 长连接）',
    }
  );
  sum.totalEvents = results.reduce((a, r) => a + r.events, 0);
  sum.avgEvents = results.length ? +(sum.totalEvents / results.length).toFixed(2) : 0;
  sum.endedCount = results.filter((r) => r.ended).length;
  return sum;
}

// ---------- 场景③ 同一 idempotencyKey 并发重复确认 ----------
async function scenarioIdempotency(n) {
  // 先跑到确认卡，拿到 token，然后用「同一个 key」并发确认 n 次
  const goal = '明天需要取三个快递，不希望把宿舍号告诉陌生人';
  const start = await post('/api/assistant', { goal, context: { requesterId: 1 } });
  const sessionId = start.body && start.body.sessionId;
  if (!sessionId) return { scenario: 'idempotency', error: 'start 失败' };
  let cur = start;
  for (let round = 0; round < 4; round++) {
    const unknowns = (cur.body && cur.body.unknowns) || [];
    if (unknowns.length === 0) break;
    cur = await post(`/api/assistant/sessions/${sessionId}/answers`, {
      answers: unknowns.map((u) => ({ field: u.field, value: valueFor(u.field) })),
    });
  }
  let card = cur.body && cur.body.card;
  if (card && card.kind === 'comparison') {
    let optionId = 'now';
    for (const b of card.blocks || []) {
      if (b.type === 'Choice' && b.options && b.options.length) { optionId = b.options[0].id; break; }
    }
    cur = await post(`/api/assistant/sessions/${sessionId}/select`, { optionId });
    card = cur.body && cur.body.card;
  }
  if (!card || card.kind !== 'confirmation' || !card.token) {
    return { scenario: 'idempotency', concurrency: n, passed: false, reason: '没走到确认卡' };
  }

  const sameKey = `load-idem-${Date.now()}`; // 关键：n 次并发共用同一个幂等键
  const started = Date.now();
  const confirms = await Promise.all(
    Array.from({ length: n }, () => post(`/api/assistant/sessions/${sessionId}/confirm`, {
      token: card.token.value, idempotencyKey: sameKey,
    }))
  );
  const wall = Date.now() - started;
  const taskIds = confirms.map((c) => c.body && c.body.taskId).filter((t) => t != null);
  const distinct = [...new Set(taskIds)];
  const passed = distinct.length === 1; // 只应产生一个业务事实
  const sum = summarize(
    confirms.map((c) => ({ ms: c.ms, failed: c.status !== 200 })), wall, {
      scenario: 'idempotency', concurrency: n, endpoint: 'POST .../confirm（同幂等键重复）',
    }
  );
  sum.repeats = n;
  sum.taskIdCount = distinct.length;
  sum.taskIds = distinct;
  sum.passed = passed;
  sum.assertion = passed
    ? '✅ 同一幂等键只产生一个 taskId'
    : `❌ 产生了 ${distinct.length} 个不同 taskId（应为 1）`;
  return sum;
}

// ---------- 统计：P50/P95/P99、错误率、吞吐 ----------
function summarize(units, wallMs, meta) {
  const ok = units.filter((u) => !u.failed);
  const failures = units.length - ok.length;
  const sorted = units.map((u) => u.ms).sort((a, b) => a - b);
  const pct = (q) => (sorted.length ? sorted[Math.max(0, Math.ceil(q * sorted.length) - 1)] : 0);
  return {
    ...meta,
    units: units.length,
    success: ok.length,
    failures,
    errorRate: +(failures / (units.length || 1)).toFixed(4),
    throughputPerSec: +(units.length / (wallMs / 1000)).toFixed(1),
    latencyMs: {
      min: sorted[0] ?? 0,
      p50: pct(0.50),
      p95: pct(0.95),
      p99: pct(0.99),
      max: sorted[sorted.length - 1] ?? 0,
      avg: +(sorted.reduce((a, b) => a + b, 0) / (sorted.length || 1)).toFixed(1),
    },
    wallMs,
  };
}

// ---------- 主流程 ----------
const plan = SCENARIO === 'all'
  ? ['assistant', 'stream', 'idempotency']
  : [SCENARIO];

const report = { base: BASE, at: new Date().toISOString(), scenarios: {} };
for (const name of plan) {
  if (!['assistant', 'stream', 'idempotency'].includes(name)) {
    console.error(`未知场景：${name}（可选 all/assistant/stream/idempotency）`);
    process.exit(2);
  }
  const n = CONCURRENCY > 0 ? CONCURRENCY : DEFAULTS[name];
  process.stdout.write(`▶ 场景 ${name}（并发 ${n}）... `);
  let res;
  if (name === 'assistant') res = await scenarioAssistant(n);
  else if (name === 'stream') res = await scenarioStream(n);
  else res = await scenarioIdempotency(n);
  report.scenarios[name] = res;
  const L = res.latencyMs || {};
  console.log(
    `P50=${L.p50}ms P95=${L.p95}ms P99=${L.p99}ms 错误率=${res.errorRate} 吞吐=${res.throughputPerSec}/s` +
    (res.assertion ? ` | ${res.assertion}` : '')
  );
}

await mkdir(REPORTS_DIR, { recursive: true });
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const out = join(REPORTS_DIR, `load-${stamp}.json`);
await writeFile(out, JSON.stringify(report, null, 2), 'utf8');
console.log(`\n报告已写入 ${out}`);

// 任一场景断言失败（idempotency.passed=false）则退出码非 0
const idem = report.scenarios.idempotency;
process.exit(idem && idem.passed === false ? 1 : 0);

