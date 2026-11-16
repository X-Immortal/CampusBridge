#!/usr/bin/env node
/**
 * CampusBridge 2.0 · mock SSE 服务（组长自用，也是张琼文写测试的夹具）
 *
 * 跑：npm run mock              → http://127.0.0.1:4390
 * 换端口：$env:PORT=4391; npm run mock
 *
 * 故障注入（开新会话时带 ?fault=）：
 *   stale       确认卡绑到旧版本 → 确认返回 409（演「改过内容后旧卡失效」）
 *   tool_fail   工具失败，只有 failed 进度，没有 task.snapshot
 *   drop        流发出 2 条就断开（演断线重连 + after 游标）
 *   model_down  模型不可用，卡片 generatedBy=rule（演 L1 降级）
 *
 * 它只实现契约，不实现业务：没有任何数据库，重启即清空。
 */

import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';

import { formatHeartbeat, formatSseFrame } from '../../src/transport/sse.ts';
import { makeEvent } from '../../src/transport/events.ts';

const PORT = Number(process.env.PORT || 4390);

function fixture(name) {
  return JSON.parse(readFileSync(new URL('../../fixtures/' + name + '.json', import.meta.url), 'utf8'));
}

const CARDS = {
  clarification: fixture('card-clarification'),
  confirmation: fixture('card-confirmation')
};

const sessions = new Map();
let sessionCounter = 0;
let taskCounter = 1024;

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function log(...args) {
  console.log('[mock]', ...args);
}

function emit(session, kind, payload) {
  session.sequence += 1;
  const event = makeEvent(session.id, session.sequence, kind, payload);
  session.events.push(event);
  log('seq', event.sequence, kind, payload.text ? payload.text.slice(0, 24) : '');
  return event;
}

function violation(ruleId, message) {
  return { ruleId, severity: 'BLOCK', title: '确认失败', message, codeRef: 'policy/K13' };
}

function createSession(goal, fault) {
  sessionCounter += 1;
  const id = 's-' + String(sessionCounter).padStart(3, '0');
  const session = {
    id,
    goal,
    fault: fault || 'none',
    events: [],
    sequence: 0,
    taskVersion: 1,
    taskId: null,
    token: null,
    digest: null,
    usedNonces: [],
    confirmed: false,
    idempotency: new Map()
  };
  sessions.set(id, session);
  return session;
}

function clarificationCard(session) {
  const card = clone(CARDS.clarification);
  card.sessionId = session.id;
  card.cardId = 'card-clarify-' + session.id;
  card.taskVersion = session.taskVersion;
  if (session.fault === 'model_down') {
    card.generatedBy = 'rule';
    card.warnings = ['模型不可用，已改用规则整理'];
  }
  return card;
}

function confirmationCard(session) {
  const card = clone(CARDS.confirmation);
  card.sessionId = session.id;
  card.cardId = 'card-confirm-' + session.id;
  card.taskVersion = session.taskVersion;
  const boundVersion = session.fault === 'stale' ? session.taskVersion - 1 : session.taskVersion;
  const digest = 'd-' + session.id;
  card.paramDigest = digest;
  card.token = {
    value: 'hmac-' + session.id + '-v' + boundVersion,
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    nonce: 'n-' + session.id + '-' + session.taskVersion,
    paramDigest: digest
  };
  session.token = { value: card.token.value, nonce: card.token.nonce, expiresAt: card.token.expiresAt, taskVersion: boundVersion };
  session.digest = digest;
  return card;
}

function baseResponse(session, state, card) {
  return {
    sessionId: session.id,
    traceId: 'trace-' + session.id,
    state,
    intent: 'publish_task',
    unknowns: [],
    assumptions: ['取件点在校园内'],
    modelMode: session.fault === 'model_down' ? 'rule' : 'model',
    degraded: session.fault === 'model_down',
    taskId: session.taskId === null ? undefined : session.taskId,
    card,
    violations: [],
    outcome: state,
    notes: []
  };
}

function handleStart(body, fault) {
  const session = createSession(body.goal || '（没给目标）', fault);
  const card = clarificationCard(session);

  if (session.fault === 'model_down') {
    emit(session, 'text.delta', { text: '模型暂时不可用，我先把能确定的部分整理成规则卡片。' });
  } else {
    emit(session, 'text.delta', { text: '我先确认两件事，确认完就能发。' });
  }
  emit(session, 'schema.card', { card });

  return baseResponse(session, 'CLARIFYING', card);
}

function handleAnswers(session, body) {
  const answers = body.answers || [];
  for (const answer of answers) {
    log('answer', answer.field, '=', String(answer.value));
  }
  session.taskVersion += 1;
  const card = confirmationCard(session);
  emit(session, 'text.delta', { text: '好，按这个发布。点「确认发布」才会真的建单。' });
  emit(session, 'schema.card', { card });
  return baseResponse(session, 'AWAITING_CONFIRMATION', card);
}

function handleConfirm(session, body) {
  const token = body.token || '';
  const idempotencyKey = body.idempotencyKey || '';

  if (session.confirmed && session.idempotency.get(idempotencyKey) === true) {
    return { status: 200, payload: { ok: true, replay: true, taskId: session.taskId, message: '幂等命中，没有产生第二条任务' } };
  }

  const problems = [];
  if (session.token === null) {
    problems.push(violation('K13', '没有可用的确认令牌'));
  } else {
    if (token !== session.token.value) {
      problems.push(violation('K13', '令牌不匹配（可能被篡改）'));
    }
    if (session.token.taskVersion !== session.taskVersion) {
      problems.push(violation('K13', '卡片版本落后，改过内容后旧卡失效'));
    }
    if (session.usedNonces.indexOf(session.token.nonce) >= 0) {
      problems.push(violation('K13', '令牌已被使用'));
    }
    if (new Date(session.token.expiresAt).getTime() < Date.now()) {
      problems.push(violation('K13', '令牌已过期'));
    }
  }
  if (problems.length > 0) {
    return { status: 409, payload: { ok: false, replay: false, message: problems[0].message, violations: problems } };
  }

  session.usedNonces.push(session.token.nonce);

  if (session.fault === 'tool_fail') {
    emit(session, 'tool.progress', { toolName: 'publishTask', status: 'failed', message: '建单失败：数据库拒绝' });
    emit(session, 'stream.end', { reason: 'error', message: '工具失败，任务没有建立' });
    return { status: 200, payload: { ok: false, replay: false, message: '建单失败：数据库拒绝' } };
  }

  session.confirmed = true;
  session.taskId = taskCounter;
  taskCounter += 1;
  session.idempotency.set(idempotencyKey, true);

  emit(session, 'tool.progress', { toolName: 'publishTask', status: 'started', progress: 0, message: '正在建单' });
  emit(session, 'task.snapshot', { taskId: session.taskId, taskStatus: 'OPEN', taskVersion: session.taskVersion });
  emit(session, 'tool.progress', { toolName: 'publishTask', status: 'done', progress: 100, message: '已发布' });
  emit(session, 'stream.end', { reason: 'done', message: '本轮结束' });

  return { status: 200, payload: { ok: true, replay: false, taskId: session.taskId, message: '已发布' } };
}

function openStream(request, response, url) {
  const sessionId = url.searchParams.get('sessionId') || '';
  const after = Number(url.searchParams.get('after') || '0');
  const session = sessions.get(sessionId);
  if (session === undefined) {
    response.writeHead(404, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ ok: false, message: 'unknown session: ' + sessionId }));
    return;
  }

  response.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'Access-Control-Allow-Origin': '*'
  });

  let index = session.events.findIndex((event) => event.sequence > after);
  if (index < 0) {
    index = session.events.length;
  }
  let sent = 0;
  let timer = null;

  const stop = () => {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  };

  const pump = () => {
    while (index < session.events.length) {
      const event = session.events[index];
      index += 1;
      response.write(formatSseFrame(event));
      sent += 1;
      if (session.fault === 'drop' && sent >= 2) {
        stop();
        log('fault=drop：主动断开（不发送 stream.end），客户端应带 after 重连');
        response.end();
        return true;
      }
      if (event.kind === 'stream.end') {
        stop();
        response.end();
        return true;
      }
    }
    return false;
  };

  timer = setInterval(() => {
    if (!pump()) {
      response.write(formatHeartbeat());
    }
  }, 1200);

  pump();

  request.on('close', () => {
    stop();
  });
}

function readBody(request) {
  return new Promise((resolve) => {
    let raw = '';
    request.on('data', (chunk) => {
      raw += chunk;
    });
    request.on('end', () => {
      if (raw.length === 0) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch (error) {
        resolve({});
      }
    });
  });
}

function sendJson(response, status, body) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'content-type',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS'
  });
  response.end(JSON.stringify(body));
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url || '/', 'http://127.0.0.1');
  const path = url.pathname;

  if (request.method === 'OPTIONS') {
    sendJson(response, 204, {});
    return;
  }

  if (request.method === 'GET' && path === '/health') {
    sendJson(response, 200, { ok: true, protocol: 'campushub.stream.v1', sessions: sessions.size });
    return;
  }

  if (request.method === 'GET' && path === '/') {
    response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end(
      [
        'CampusBridge mock',
        '',
        'POST /api/assistant?fault=none|stale|tool_fail|drop|model_down   { goal }',
        'POST /api/assistant/sessions/{id}/answers   { answers: [{ field, value }] }',
        'POST /api/assistant/sessions/{id}/confirm   { token, idempotencyKey }',
        'GET  /api/assistant/sessions/{id}',
        'GET  /api/stream?sessionId=&after=',
        'GET  /health'
      ].join('\n')
    );
    return;
  }

  if (request.method === 'GET' && path === '/api/stream') {
    openStream(request, response, url);
    return;
  }

  if (request.method === 'POST' && path === '/api/assistant') {
    const body = await readBody(request);
    const fault = url.searchParams.get('fault') || process.env.MOCK_FAULT || 'none';
    const payload = handleStart(body, fault);
    log('start', payload.sessionId, 'fault=' + fault);
    sendJson(response, 200, payload);
    return;
  }

  const answersMatch = path.match(/^\/api\/assistant\/sessions\/([^/]+)\/answers$/);
  if (request.method === 'POST' && answersMatch !== null) {
    const session = sessions.get(answersMatch[1]);
    if (session === undefined) {
      sendJson(response, 404, { ok: false, message: 'unknown session' });
      return;
    }
    const body = await readBody(request);
    sendJson(response, 200, handleAnswers(session, body));
    return;
  }

  const confirmMatch = path.match(/^\/api\/assistant\/sessions\/([^/]+)\/confirm$/);
  if (request.method === 'POST' && confirmMatch !== null) {
    const session = sessions.get(confirmMatch[1]);
    if (session === undefined) {
      sendJson(response, 404, { ok: false, message: 'unknown session' });
      return;
    }
    const body = await readBody(request);
    const result = handleConfirm(session, body);
    log('confirm', session.id, '→', result.status, result.payload.message);
    sendJson(response, result.status, result.payload);
    return;
  }

  const viewMatch = path.match(/^\/api\/assistant\/sessions\/([^/]+)$/);
  if (request.method === 'GET' && viewMatch !== null) {
    const session = sessions.get(viewMatch[1]);
    if (session === undefined) {
      sendJson(response, 404, { ok: false, message: 'unknown session' });
      return;
    }
    sendJson(response, 200, baseResponse(session, session.confirmed ? 'CREATED' : 'CLARIFYING', undefined));
    return;
  }

  sendJson(response, 404, { ok: false, message: 'no route: ' + request.method + ' ' + path });
});

server.listen(PORT, '127.0.0.1', () => {
  log('listening on http://127.0.0.1:' + PORT);
  log('health: http://127.0.0.1:' + PORT + '/health');
  log('示例：POST /api/assistant?fault=none {"goal":"今晚九点下课，找个人从北门取快递送到 5 号宿舍楼"}');
  console.log('（Ctrl+C 退出；本服务不落库，重启即清空）');
});
