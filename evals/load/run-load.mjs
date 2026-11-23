// M3 压测：三类场景 + P50/P95/P99 + 错误率 + 吞吐，报告落 evals/reports/load-*.json
//
// 用法：
//   node evals/load/run-load.mjs --base http://127.0.0.1:8081
//   node evals/load/run-load.mjs --base http://127.0.0.1:8081 --scenario assistant
//   node evals/load/run-load.mjs --base http://127.0.0.1:8081 --concurrency 20
//
// 场景：
//   assistant    N 并发 POST /api/assistant，每个请求走完整链路（追问→比较→确认→建单）
//   stream       N 并发 GET /api/stream（SSE），等 stream.end
//   idempotency  同一 token + 同一 idempotencyKey 并发确认，必须只建一单
//
// 注意：压测会往 demo 库写入任务；请用 demo profile（H2），不要打生产库。

import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  confirmAssistant,
  prepareConfirmation,
  readSse,
  runFullFlow
} from '../lib/api.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPORTS_DIR = join(HERE, '..', 'reports');
const GOAL = '我脚受伤了，今晚需要取三个快递，但不希望把宿舍号直接告诉陌生人';

function argValue(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1);
  return sorted[Math.max(0, index)];
}

function summarize(name, durations, errors, wallMs, total, concurrency, extra = {}) {
  const sorted = [...durations].sort((a, b) => a - b);
  const success = durations.length;
  return {
    name,
    total,
    concurrency,
    success,
    errors: errors.length,
    errorRate: total === 0 ? 0 : Number(((errors.length / total) * 100).toFixed(2)),
    throughputPerSec: wallMs === 0 ? 0 : Number((success / (wallMs / 1000)).toFixed(2)),
    p50Ms: Number(percentile(sorted, 0.50).toFixed(1)),
    p95Ms: Number(percentile(sorted, 0.95).toFixed(1)),
    p99Ms: Number(percentile(sorted, 0.99).toFixed(1)),
    maxMs: Number((sorted[sorted.length - 1] || 0).toFixed(1)),
    wallMs: Number(wallMs.toFixed(1)),
    ...extra
  };
}

async function runWorkers(total, concurrency, worker) {
  const durations = [];
  const errors = [];
  let next = 0;
  const startedAt = performance.now();
  const workers = Array.from({ length: Math.min(concurrency, total) }, async () => {
    for (;;) {
      const index = next;
      next += 1;
      if (index >= total) return;
      const start = performance.now();
      try {
        await worker(index);
        durations.push(performance.now() - start);
      } catch (error) {
        errors.push({ index, message: error.message });
      }
    }
  });
  await Promise.all(workers);
  return { durations, errors, wallMs: performance.now() - startedAt };
}

async function scenarioAssistant(base, count, concurrency) {
  const { durations, errors, wallMs } = await runWorkers(count, concurrency, async () => {
    const flow = await runFullFlow(base, GOAL);
    if (!flow.taskId) {
      throw new Error('完整链路没有 taskId');
    }
  });
  return summarize('assistant', durations, errors, wallMs, count, concurrency);
}

async function scenarioStream(base, count, concurrency) {
  const flow = await runFullFlow(base, GOAL);
  const firstFrames = [];
  const { durations, errors, wallMs } = await runWorkers(count, concurrency, async () => {
    const { frames, firstFrameMs } = await readSse(base, flow.sessionId, {
      after: 0,
      timeoutMs: 20000
    });
    if (frames.length === 0) {
      throw new Error('SSE 没有收到任何帧');
    }
    if (typeof firstFrameMs === 'number') {
      firstFrames.push(firstFrameMs);
    }
    const last = frames[frames.length - 1];
    if (last.data?.kind !== 'stream.end') {
      throw new Error('SSE 最后一帧不是 stream.end：' + String(last.data?.kind));
    }
  });
  firstFrames.sort((a, b) => a - b);
  return summarize('stream', durations, errors, wallMs, count, concurrency, {
    sessionId: flow.sessionId,
    firstFrameP50Ms: Number(percentile(firstFrames, 0.50).toFixed(1)),
    firstFrameP95Ms: Number(percentile(firstFrames, 0.95).toFixed(1))
  });
}

async function scenarioIdempotency(base, count, concurrency) {
  const prepared = await prepareConfirmation(base, GOAL);
  if (!prepared.token) {
    throw new Error('没有拿到确认令牌');
  }
  const idempotencyKey = `load-idem-${crypto.randomUUID()}`;
  const responses = [];
  const { durations, errors, wallMs } = await runWorkers(count, concurrency, async () => {
    const response = await confirmAssistant(
      base, prepared.sessionId, prepared.token, idempotencyKey);
    if (!response.ok) {
      throw new Error(`确认返回 HTTP ${response.status}`);
    }
    if (response.body?.outcome !== 'OK' || response.body?.taskId == null) {
      throw new Error('确认没有返回 OK/taskId');
    }
    responses.push(response.body);
  });
  const taskIds = [...new Set(responses.map((body) => String(body.taskId)))];
  const duplicateTasks = Math.max(0, taskIds.length - 1);
  return summarize('idempotency', durations, errors, wallMs, count, concurrency, {
    uniqueTaskIds: taskIds.length,
    duplicateTasks,
    replayedResponses: Math.max(0, responses.length - taskIds.length)
  });
}

async function main() {
  const base = argValue('base', 'http://127.0.0.1:8081');
  const scenario = argValue('scenario', 'all');
  const concurrencyOverride = Number(argValue('concurrency', '0'));
  const selected = scenario === 'all'
    ? ['assistant', 'stream', 'idempotency']
    : [scenario];
  const allowed = new Set(['assistant', 'stream', 'idempotency']);
  for (const name of selected) {
    if (!allowed.has(name)) {
      throw new Error(`未知 --scenario ${name}（assistant / stream / idempotency / all）`);
    }
  }

  // 预热：先跑一条完整链路，避免第一波请求被 JVM/连接池冷启动污染。
  await runFullFlow(base, GOAL);

  const defaults = { assistant: 50, stream: 20, idempotency: 10 };
  const results = {};
  for (const name of selected) {
    const count = concurrencyOverride > 0 ? concurrencyOverride : defaults[name];
    const startedAt = performance.now();
    if (name === 'assistant') {
      results.assistant = await scenarioAssistant(base, count, count);
    } else if (name === 'stream') {
      results.stream = await scenarioStream(base, count, count);
    } else {
      results.idempotency = await scenarioIdempotency(base, count, count);
    }
    const item = results[name];
    console.log(
      `[${name}] ${item.success}/${item.total} 成功, ${item.errors} 错误, `
      + `${item.throughputPerSec}/s, P50 ${item.p50Ms}ms, P95 ${item.p95Ms}ms, `
      + `P99 ${item.p99Ms}ms, 墙钟 ${item.wallMs}ms`);
    if (name === 'idempotency') {
      console.log(`  唯一 taskId=${item.uniqueTaskIds}, 重复建单=${item.duplicateTasks}`);
    }
    item.wallMsTotal = Number((performance.now() - startedAt).toFixed(1));
  }

  const report = {
    generatedAt: new Date().toISOString(),
    base,
    scenario,
    concurrencyOverride: concurrencyOverride || null,
    defaults,
    results
  };
  await mkdir(REPORTS_DIR, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const file = join(REPORTS_DIR, `load-${stamp}.json`);
  await writeFile(file, JSON.stringify(report, null, 2), 'utf8');
  console.log(`报告：${file}`);
}

main().catch((error) => {
  console.error('压测失败：' + error.message);
  process.exitCode = 1;
});
