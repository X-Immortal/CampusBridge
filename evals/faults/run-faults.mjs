// 故障注入脚本
//
// 用法：
//   node run-faults.mjs --base http://127.0.0.1:8081
//
// 三个故障，各自断言：
//   ① 模型超时/不可用   —— 请求带 context.model=unavailable；断言 degraded=true 且不返回 500
//   ② 工具重复调用       —— 同一 idempotencyKey 确认两次；断言只产生一个 taskId
//   ③ 流式中断后重连     —— 收到 N 条事件后 abort，再按 after 重连；断言事件不重复、序号连续
//
// 输出：控制台逐条 PASS/FAIL；全部通过退出码 0，否则非 0。
// 前提：后端 demo profile（H2）起着。① 不依赖谢-2 的 ai.fault.mode 开关，
//       用 context.model=unavailable 触发降级；谢交付后可再加 ai.fault.mode=model_timeout 复测。

const args = process.argv.slice(2);
function argOf(name, dflt) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] !== undefined ? args[i + 1] : dflt;
}
const BASE = argOf('--base', 'http://127.0.0.1:8081');

const GOAL = '明天需要取三个快递，不希望把宿舍号告诉陌生人';
const VALUES = {
  deadline: '2026-11-25T21:00:00',
  location: '北门菜鸟驿站',
  deliveryLocation: '三号宿舍楼东门',
  rewardType: 'CASH', rewardAmount: 8, itemCount: 3,
  timeWindow: '明晚 20:00-22:00', shareDormRoom: false,
  riskDisclosureConfirmed: true, requiresProof: true,
  onlyInAppContact: true, allowOfflineContact: false,
  completionStandard: '送到三号宿舍楼东门并当面交付拍照',
  contactPreference: '站内消息', title: '帮取三个快递',
  category: 'ERRAND', intent: 'ERRAND_PICKUP',
};
const valueFor = (f) => (Object.prototype.hasOwnProperty.call(VALUES, f) ? VALUES[f] : '都可以');

async function post(path, body) {
  const res = await fetch(new URL(path, BASE), {
    method: 'POST',
    headers: { 'content-type': 'application/json; charset=utf-8' },
    body: JSON.stringify(body ?? {}),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 保留 null */ }
  return { status: res.status, body: json };
}

// 跑一条完整链路到 confirmation 卡，返回 { sessionId, token, ok }
async function flowToConfirmation(extraContext = {}) {
  const start = await post('/api/assistant', { goal: GOAL, context: { requesterId: 1, ...extraContext } });
  if (!start.body || !start.body.sessionId) return { ok: false, reason: 'start 失败 ' + start.status };
  const sessionId = start.body.sessionId;
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
    return { ok: false, sessionId, reason: '没走到确认卡（state=' + (cur.body && cur.body.state) + '）' };
  }
  return { ok: true, sessionId, token: card.token.value };
}

// 读 SSE：读到 stream.end、或读满 maxEvents 条后主动 abort（模拟断流）
async function readStream(sessionId, after, maxEvents) {
  const events = [];
  let ended = false;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);
  try {
    const url = new URL('/api/stream', BASE);
    url.searchParams.set('sessionId', sessionId);
    url.searchParams.set('after', String(after));
    const res = await fetch(url, { signal: controller.signal });
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) { ended = true; break; }
      buf += decoder.decode(value, { stream: true });
      const frames = buf.split('\n\n');
      buf = frames.pop();
      for (const f of frames) {
        const dl = f.split('\n').find((l) => l.startsWith('data:'));
        if (!dl) continue;
        try {
          const ev = JSON.parse(dl.slice(5).trim());
          events.push({ seq: ev.sequence, kind: ev.kind });
          if (ev.kind === 'stream.end') ended = true;
        } catch { /* 忽略心跳/注释帧 */ }
      }
      if (ended) break;
      if (maxEvents && events.length >= maxEvents) break; // 模拟“收到 N 条后中断”
    }
    controller.abort(); // 主动断开（正常结束也 abort，无副作用）
    clearTimeout(timeout);
  } catch (e) {
    if (e.name !== 'AbortError') return { events, ended, error: e.message };
    clearTimeout(timeout);
  }
  return { events, ended };
}

// ---------- ① 模型超时/不可用 → degraded=true 且不 500 ----------
async function fault1() {
  const r = await post('/api/assistant', { goal: GOAL, context: { requesterId: 1, model: 'unavailable' } });
  const degraded = r.body && r.body.degraded === true;
  const hasCard = r.body && r.body.card != null;
  const not500 = r.status !== 500;
  const pass = r.status === 200 && degraded && hasCard && not500;
  return {
    pass,
    detail: `status=${r.status} degraded=${degraded} 有卡片=${hasCard}`,
    reason: pass ? '模型不可用时降级到规则并仍出卡，未 500'
      : `未满足降级预期（status=${r.status}, degraded=${degraded}, 有卡片=${hasCard}）`,
  };
}

// ---------- ② 同一幂等键重复确认 → 只产生一个 taskId ----------
async function fault2() {
  const flow = await flowToConfirmation();
  if (!flow.ok) return { pass: false, detail: flow.reason, reason: flow.reason };
  const key = `fault-idem-${Date.now()}`;
  const c1 = await post(`/api/assistant/sessions/${flow.sessionId}/confirm`, { token: flow.token, idempotencyKey: key });
  const c2 = await post(`/api/assistant/sessions/${flow.sessionId}/confirm`, { token: flow.token, idempotencyKey: key });
  const ids = [c1.body && c1.body.taskId, c2.body && c2.body.taskId].filter((t) => t != null);
  const distinct = [...new Set(ids)];
  const pass = distinct.length === 1 && c1.status === 200 && c2.status === 200;
  return {
    pass,
    detail: `两次 taskId=[${ids.join(',')}] 去重后=${distinct.length} 个`,
    reason: pass ? '同一幂等键两次确认只落一个业务事实' : `产生了 ${distinct.length} 个不同 taskId（应为 1）`,
  };
}

// ---------- ③ 断流后按 after 重连 → 事件不重复、序号连续 ----------
async function fault3() {
  // 先跑到建单，保证事件日志里有 stream.end（完整流可回放）
  const flow = await flowToConfirmation();
  if (!flow.ok) return { pass: false, detail: flow.reason, reason: flow.reason };
  await post(`/api/assistant/sessions/${flow.sessionId}/confirm`, {
    token: flow.token, idempotencyKey: `fault3-${Date.now()}`,
  });

  // 段1：只读 3 条就中断，模拟“收到 N 条后 abort”
  const seg1 = await readStream(flow.sessionId, 0, 3);
  if (seg1.events.length === 0) return { pass: false, detail: '首段没收到事件', reason: '首段无事件' };
  const lastSeq1 = seg1.events[seg1.events.length - 1].seq;

  // 段2：用 after=lastSeq1 重连，读到结束
  const seg2 = await readStream(flow.sessionId, lastSeq1, 0);

  const all = [...seg1.events, ...seg2.events];
  const seqs = all.map((e) => e.seq);
  const distinctSeqs = [...new Set(seqs)];
  const noDup = distinctSeqs.length === seqs.length; // 合并后不能有重复序号
  const maxSeq = Math.max(...seqs);
  const contiguous = distinctSeqs.slice().sort((a, b) => a - b)
    .every((s, i) => s === i + 1); // 序号必须从 1 连续到 max，无空洞
  const resumedRight = seg2.events.length ? seg2.events[0].seq === lastSeq1 + 1 : seg1.ended;
  const gotEnd = all.some((e) => e.kind === 'stream.end');

  const pass = noDup && contiguous && resumedRight && gotEnd;
  return {
    pass,
    detail: `段1=${seg1.events.length}条(末序号${lastSeq1}) 段2起于${seg2.events[0] ? seg2.events[0].seq : 'N/A'} 合并序号=${distinctSeqs.sort((a,b)=>a-b).join(',')}`,
    reason: pass
      ? '断流重连后事件不重复、序号 1..N 连续、补到 stream.end'
      : `断言未过（不重复=${noDup} 连续=${contiguous} 重连点正确=${resumedRight} 收到结束=${gotEnd}）`,
  };
}

// ---------- 主流程 ----------
const faults = [
  ['① 模型超时/不可用（degraded 且不 500）', fault1],
  ['② 工具重复调用（同幂等键只一个 taskId）', fault2],
  ['③ 流式中断重连（事件不重复、序号连续）', fault3],
];

let allPass = true;
console.log(`故障注入 @ ${BASE}`);
console.log('─'.repeat(60));
for (const [name, fn] of faults) {
  let r;
  try { r = await fn(); } catch (e) { r = { pass: false, detail: '抛异常', reason: e.message }; }
  if (!r.pass) allPass = false;
  console.log(`${r.pass ? 'PASS' : 'FAIL'}  ${name}`);
  console.log(`      ${r.detail}`);
  console.log(`      → ${r.reason}`);
}
console.log('─'.repeat(60));
console.log(allPass ? '全部通过 ✅' : '存在失败 ❌');
process.exit(allPass ? 0 : 1);
