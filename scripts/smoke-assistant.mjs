/**
 * 本地冒烟：/api/assistant 四步链路（开始 → 回答 → 确认 → 建单）。
 * 仅用于本机 demo profile（8081，H2）；规则基线或真实模型都适用。
 */
const base = 'http://127.0.0.1:8081';

async function post(path, body) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'content-type': 'application/json; charset=utf-8' },
    body: JSON.stringify(body)
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text }; }
  return { status: res.status, body: json };
}

function futureDeadline() {
  const date = new Date(Date.now() + 6 * 60 * 60 * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

const values = {
  deadline: futureDeadline(),
  location: '北门菜鸟驿站',
  deliveryLocation: '北门菜鸟驿站门口',
  rewardType: 'CASH',
  rewardAmount: 8,
  itemCount: 3,
  timeWindow: '今晚 20:00-22:00',
  shareDormRoom: false,
  riskDisclosureConfirmed: true,
  requiresProof: true,
  onlyInAppContact: true,
  allowOfflineContact: false,
  completionStandard: '从北门菜鸟驿站取出三个快递并送到北门门口当面交付',
  contactPreference: '站内消息',
  title: '帮取三个快递',
  category: 'ERRAND',
  intent: 'ERRAND_PICKUP'
};

function answerFor(field) {
  return Object.prototype.hasOwnProperty.call(values, field) ? values[field] : '都可以';
}

const start = await post('/api/assistant', {
  goal: '今晚九点去北门菜鸟驿站帮我取三个快递，预算8元',
  context: { requesterId: 1 }
});
console.log('START', start.status, 'state=' + start.body.state, 'intent=' + start.body.intent,
  'card=' + (start.body.card && start.body.card.kind), 'mode=' + start.body.modelMode,
  'degraded=' + start.body.degraded);

const sessionId = start.body.sessionId;
let last = start;
for (let round = 0; round < 4; round++) {
  const unknowns = last.body.unknowns || [];
  if (unknowns.length === 0) break;
  const answers = unknowns.map((u) => ({ field: u.field, value: answerFor(u.field) }));
  console.log('ANSWER round ' + (round + 1) + ': ' + unknowns.map((u) => u.field).join(','));
  last = await post('/api/assistant/sessions/' + sessionId + '/answers', { answers });
  console.log('  ->', last.status, 'state=' + last.body.state, 'card=' + (last.body.card && last.body.card.kind));
}

let card = last.body.card;
for (let attempt = 0; attempt < 3 && card && card.kind === 'comparison'; attempt++) {
  let optionId = '';
  for (const block of card.blocks || []) {
    if (block.type === 'Choice' && block.options && block.options.length > 0) {
      optionId = block.options[0].id;
      break;
    }
  }
  if (!optionId) break;
  console.log('SELECT option=' + optionId);
  last = await post('/api/assistant/sessions/' + sessionId + '/select', { optionId });
  console.log('  ->', last.status, 'state=' + last.body.state, 'card=' + (last.body.card && last.body.card.kind));
  card = last.body.card;
}

if (!card || card.kind !== 'confirmation') {
  console.log('NO CONFIRMATION CARD, last state=' + last.body.state);
  process.exitCode = 2;
} else {
  console.log('CONFIRM CARD', 'taskVersion=' + card.taskVersion, 'blocks=' + card.blocks.length,
    'token=' + (card.token ? 'yes' : 'no'));
  const key = 'smoke-' + Date.now();
  const confirm = await post('/api/assistant/sessions/' + sessionId + '/confirm', {
    token: card.token.value,
    idempotencyKey: key
  });
  console.log('CONFIRM', confirm.status, 'outcome=' + confirm.body.outcome,
    'taskId=' + confirm.body.taskId, 'card=' + (confirm.body.card && confirm.body.card.kind));

  const replay = await post('/api/assistant/sessions/' + sessionId + '/confirm', {
    token: card.token.value,
    idempotencyKey: key
  });
  console.log('REPLAY', replay.status, 'outcome=' + replay.body.outcome,
    'taskId=' + replay.body.taskId, 'notes=' + JSON.stringify(replay.body.notes || []));

  const streamRes = await fetch(base + '/api/stream?sessionId=' + encodeURIComponent(sessionId) + '&after=0');
  const wire = await streamRes.text();
  const frames = wire.split('\n\n').filter((frame) => frame.trim().length > 0);
  const kinds = [];
  for (const frame of frames) {
    const dataLine = frame.split('\n').find((line) => line.startsWith('data:'));
    if (!dataLine) continue;
    try {
      const event = JSON.parse(dataLine.slice(5).trim());
      kinds.push(event.sequence + ':' + event.kind);
    } catch { /* 忽略注释帧 */ }
  }
  console.log('STREAM', streamRes.status, 'events=' + kinds.length, kinds.join(','));
}
