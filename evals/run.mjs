// 评测执行器
//
// 用法：
//   node run.mjs --dry-run                       只校验样本格式 + 打印覆盖矩阵（不连服务）
//   node run.mjs --dry-run --min 15              M1 阶段用：门槛降到 15 条照样算完整
//   node run.mjs --live http://127.0.0.1:8081    逐条打真实服务，检查 expect/check 并写 reports/
//
// 设计意图：把"我们测过了"变成可重复的一行命令。评审时老师让你现场跑，就这一句。
// 样本放置：cases/*.json，一条一个文件，方便 review 和 diff。

import { readdir, readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const CASES_DIR = join(HERE, 'cases');
const REPORTS_DIR = join(HERE, 'reports');

// 课件 M4 要求的八类必须都覆盖，否则现场一问就露馅。
const REQUIRED_CATEGORIES = [
  '正常',
  '模糊',
  '信息缺失',
  '特殊情境',
  '恶意输入与越权',
  '错误Schema',
  '工具失败',
  '模型超时',
];

const REQUIRED_FIELDS = ['id', 'category'];
const DEFAULT_TARGET = 40;

async function loadCases() {
  const files = (await readdir(CASES_DIR)).filter((f) => f.endsWith('.json')).sort();
  const cases = [];
  const errors = [];
  for (const file of files) {
    let raw;
    try {
      raw = JSON.parse(await readFile(join(CASES_DIR, file), 'utf8'));
    } catch (e) {
      errors.push(`${file}: JSON 解析失败 — ${e.message}`);
      continue;
    }
    const list = Array.isArray(raw) ? raw : [raw];
    for (const c of list) {
      const missing = REQUIRED_FIELDS.filter((k) => c[k] === undefined);
      if (missing.length) {
        errors.push(`${file}: 缺少字段 ${missing.join(', ')}`);
        continue;
      }
      if (!c.goal && !c.schema) {
        errors.push(`${file}: 既要 goal（对话样本）要么 schema（校验样本）`);
        continue;
      }
      if (!REQUIRED_CATEGORIES.includes(c.category)) {
        errors.push(`${file}: category「${c.category}」不在允许集合内`);
        continue;
      }
      cases.push({ ...c, __file: file });
    }
  }
  return { cases, errors };
}

function coverage(cases) {
  const map = new Map(REQUIRED_CATEGORIES.map((c) => [c, 0]));
  for (const c of cases) map.set(c.category, (map.get(c.category) ?? 0) + 1);
  return map;
}

function printCoverage(cases, target) {
  const map = coverage(cases);
  const pad = Math.max(...REQUIRED_CATEGORIES.map((c) => c.length));
  console.log('\n覆盖矩阵（课件要求八类都必须有）');
  console.log('─'.repeat(pad + 12));
  for (const cat of REQUIRED_CATEGORIES) {
    const n = map.get(cat);
    console.log(`  ${cat.padEnd(pad)}  ${String(n).padStart(3)}  ${n === 0 ? '❌ 缺' : '✅'}`);
  }
  console.log('─'.repeat(pad + 12));
  const missing = REQUIRED_CATEGORIES.filter((c) => map.get(c) === 0);
  console.log(`  合计 ${cases.length} / 目标 ${target}`);
  if (cases.length < target) console.log(`  ⚠️  还差 ${target - cases.length} 条才到目标数量`);
  if (missing.length) console.log(`  ⚠️  未覆盖：${missing.join('、')}`);
  return missing.length === 0 && cases.length >= target;
}

function isBlank(value) {
  return value === undefined || value === null || value === '';
}

function checkCase(testCase, trace) {
  const failures = [];
  const check = testCase.check || {};
  const start = trace.start || {};
  const startBody = start.body || {};
  const final = trace.final || start;
  const finalBody = final.body || {};

  if (check.validSchema !== undefined) {
    const valid = trace.schema?.body?.valid;
    if (valid !== check.validSchema) {
      failures.push(`expected valid=${check.validSchema}, got ${valid}`);
    }
  }
  if (check.errorsInclude) {
    const errors = trace.schema?.body?.errors || [];
    for (const needle of check.errorsInclude) {
      if (!errors.some((e) => String(e).includes(needle))) {
        failures.push(`错误信息里没有「${needle}」：${JSON.stringify(errors)}`);
      }
    }
  }
  if (check.cardKindIn) {
    const kind = startBody.card?.kind;
    if (!check.cardKindIn.includes(kind)) {
      failures.push(`卡片类型 ${kind} 不在 ${check.cardKindIn.join('/')}`);
    }
  }
  if (check.questionFieldsInclude) {
    const fields = (startBody.unknowns || []).map((q) => q.field);
    for (const field of check.questionFieldsInclude) {
      if (!fields.includes(field)) {
        failures.push(`没有追问 ${field}（实际问的是 ${fields.join(',') || '无'}）`);
      }
    }
  }
  if (check.degraded !== undefined) {
    if (Boolean(startBody.degraded) !== check.degraded) {
      failures.push(`degraded 期望 ${check.degraded}，实际 ${startBody.degraded}`);
    }
  }
  if (check.stateIn) {
    if (!check.stateIn.includes(finalBody.state)) {
      failures.push(`最终状态 ${finalBody.state} 不在 ${check.stateIn.join('/')}`);
    }
  }
  if (check.finalOutcome) {
    if (finalBody.outcome !== check.finalOutcome) {
      failures.push(`最终 outcome ${finalBody.outcome} != ${check.finalOutcome}`);
    }
  }
  if (check.taskIdNull && !isBlank(finalBody.taskId)) {
    failures.push(`不该建单，却拿到了 taskId=${finalBody.taskId}`);
  }
  if (check.taskIdNotNull && isBlank(finalBody.taskId)) {
    failures.push('应该建单，但没有 taskId');
  }
  if (check.violationsInclude) {
    const rules = (finalBody.violations || []).map((v) => v.ruleId);
    for (const rule of check.violationsInclude) {
      if (!rules.includes(rule)) {
        failures.push(`违规里没有 ${rule}（实际 ${rules.join(',') || '无'}）`);
      }
    }
  }
  if (check.streamKindsInclude) {
    const kinds = (trace.stream?.events || []).map((e) => e.kind);
    for (const kind of check.streamKindsInclude) {
      if (!kinds.includes(kind)) {
        failures.push(`流里没有 ${kind}（实际 ${kinds.join(',') || '无'}）`);
      }
    }
  }
  if (check.streamMinEvents !== undefined) {
    const total = (trace.stream?.events || []).length;
    if (total < check.streamMinEvents) {
      failures.push(`流事件只有 ${total} 条，少于 ${check.streamMinEvents}`);
    }
  }
  if (check.streamEndReason) {
    const ends = (trace.stream?.events || []).filter((e) => e.kind === 'stream.end');
    const end = ends.length > 0 ? ends[ends.length - 1] : null;
    if (end === null) {
      failures.push('流里没有 stream.end');
    } else if (end.payload?.reason !== check.streamEndReason) {
      failures.push(`stream.end 原因 ${end.payload?.reason} != ${check.streamEndReason}`);
    }
  }
  return failures;
}

async function postJson(baseUrl, path, body) {
  const startedAt = Date.now();
  try {
    const response = await fetch(new URL(path, baseUrl), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body ?? {}),
    });
    return {
      status: response.status,
      body: await response.json().catch(() => null),
      ms: Date.now() - startedAt,
    };
  } catch (e) {
    return { status: 0, error: e.message, ms: Date.now() - startedAt };
  }
}

async function collectStream(baseUrl, sessionId) {
  const startedAt = Date.now();
  try {
    const url = new URL('/api/stream', baseUrl);
    url.searchParams.set('sessionId', sessionId);
    url.searchParams.set('after', '0');
    const response = await fetch(url);
    const wire = await response.text();
    const events = [];
    for (const frame of wire.split('\n\n')) {
      if (!frame.trim()) continue;
      const dataLine = frame.split('\n').find((line) => line.startsWith('data:'));
      if (!dataLine) continue;
      try {
        events.push(JSON.parse(dataLine.slice(5).trim()));
      } catch {
        // 注释/心跳帧不算事件
      }
    }
    return { status: response.status, events, ms: Date.now() - startedAt };
  } catch (e) {
    return { status: 0, events: [], error: e.message, ms: Date.now() - startedAt };
  }
}

async function runCase(testCase, baseUrl) {
  const trace = { steps: [] };
  if (testCase.schema) {
    trace.schema = await postJson(baseUrl, '/api/schema/validate', testCase.schema);
    trace.steps.push({ name: 'schema.validate', status: trace.schema.status, ms: trace.schema.ms });
    return trace;
  }
  const start = await postJson(baseUrl, '/api/assistant', {
    goal: testCase.goal,
    context: testCase.context ?? {},
  });
  trace.start = start;
  trace.steps.push({ name: 'assistant.start', status: start.status, ms: start.ms });
  let current = start;

  const flow = testCase.flow;
  if (flow && start.body?.sessionId) {
    const sessionPath = `/api/assistant/sessions/${start.body.sessionId}`;
    if (flow.answers) {
      current = await postJson(baseUrl, `${sessionPath}/answers`, { answers: flow.answers });
      trace.steps.push({ name: 'answers', status: current.status, ms: current.ms });
    }
    if (flow.select) {
      current = await postJson(baseUrl, `${sessionPath}/select`, { optionId: flow.select });
      trace.steps.push({ name: 'select', status: current.status, ms: current.ms });
    }
    if (flow.confirm) {
      const token = current.body?.card?.token?.value;
      current = await postJson(baseUrl, `${sessionPath}/confirm`, {
        token,
        idempotencyKey: flow.idempotencyKey ?? `eval-${testCase.id}`,
      });
      trace.steps.push({ name: 'confirm', status: current.status, ms: current.ms });
    }
    if (flow.cancel) {
      current = await postJson(baseUrl, `${sessionPath}/cancel`, { reason: 'eval cancel' });
      trace.steps.push({ name: 'cancel', status: current.status, ms: current.ms });
    }
  }
  trace.final = current;
  if (testCase.stream && start.body?.sessionId) {
    trace.stream = await collectStream(baseUrl, start.body.sessionId);
    trace.steps.push({ name: 'stream', status: trace.stream.status, ms: trace.stream.ms });
  }
  return trace;
}

async function runLive(cases, baseUrl) {
  const results = [];
  for (const c of cases) {
    const trace = await runCase(c, baseUrl);
    const failures = checkCase(c, trace);
    results.push({
      id: c.id,
      category: c.category,
      file: c.__file,
      ok: failures.length === 0,
      failures,
      status: trace.final?.status ?? trace.schema?.status,
      state: trace.final?.body?.state ?? null,
      outcome: trace.final?.body?.outcome ?? null,
      taskId: trace.final?.body?.taskId ?? null,
      cardKind: trace.start?.body?.card?.kind ?? null,
      streamEvents: (trace.stream?.events || []).length,
      ms: trace.steps.reduce((sum, s) => sum + (s.ms ?? 0), 0),
      steps: trace.steps,
    });
  }
  await mkdir(REPORTS_DIR, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const passed = results.filter((r) => r.ok).length;
  const summary = { baseUrl, at: new Date().toISOString(), total: results.length, passed, results };
  const out = join(REPORTS_DIR, `run-${stamp}.json`);
  await writeFile(out, JSON.stringify(summary, null, 2), 'utf8');

  console.log('\n逐条结果');
  console.log('─'.repeat(72));
  for (const r of results) {
    const mark = r.ok ? '✅' : '❌';
    console.log(`  ${mark} ${r.id} ${r.category}  card=${r.cardKind ?? '-'} state=${r.state ?? '-'} task=${r.taskId ?? '-'}`);
    for (const failure of r.failures) console.log(`       ↳ ${failure}`);
  }
  console.log('─'.repeat(72));
  console.log(`  通过 ${passed}/${results.length}，报告已写入 ${out}`);
  return passed === results.length;
}

const args = process.argv.slice(2);
const liveIdx = args.indexOf('--live');
const liveUrl = liveIdx >= 0 ? args[liveIdx + 1] : null;
const minIdx = args.indexOf('--min');
const target = minIdx >= 0 ? Number(args[minIdx + 1]) : DEFAULT_TARGET;

const { cases, errors } = await loadCases();

if (errors.length) {
  console.error('样本有问题：');
  for (const e of errors) console.error('  - ' + e);
  process.exit(1);
}

console.log(`载入 ${cases.length} 条样本，来自 ${CASES_DIR}`);
const complete = printCoverage(cases, target);

if (liveUrl) {
  const allPassed = await runLive(cases, liveUrl);
  process.exit(allPassed ? 0 : 1);
} else {
  console.log('\n（--dry-run 模式：只校验样本，未连接服务）');
  process.exit(complete ? 0 : 1);
}
