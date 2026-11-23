// 评测/压测/故障脚本共用的小型 HTTP + SSE 工具。
//
// 刻意不引入第三方依赖：Node 22 自带 fetch / AbortController / TextDecoder，
// 评审现场 `node evals/load/run-load.mjs` 就能跑，不需要 npm install。

const JSON_HEADERS = { 'content-type': 'application/json', accept: 'application/json' };

export async function requestJson(base, path, options = {}) {
  const {
    method = 'GET',
    body,
    headers = {},
    timeoutMs = 15000
  } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(new URL(path, base), {
      method,
      headers: { ...JSON_HEADERS, ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal
    });
    const text = await response.text();
    let parsed;
    try {
      parsed = text ? JSON.parse(text) : null;
    } catch {
      parsed = { raw: text };
    }
    return { status: response.status, ok: response.ok, body: parsed, text };
  } finally {
    clearTimeout(timer);
  }
}

export function requireOk(response, label) {
  if (!response.ok) {
    throw new Error(`${label} 返回 HTTP ${response.status}: ${response.text.slice(0, 200)}`);
  }
  return response.body;
}

export async function startAssistant(base, goal, context = {}) {
  return requestJson(base, '/api/assistant', {
    method: 'POST',
    body: { goal, context }
  });
}

export async function answerAssistant(base, sessionId, answers) {
  return requestJson(base, `/api/assistant/sessions/${encodeURIComponent(sessionId)}/answers`, {
    method: 'POST',
    body: { answers }
  });
}

export async function selectAssistant(base, sessionId, optionId) {
  return requestJson(base, `/api/assistant/sessions/${encodeURIComponent(sessionId)}/select`, {
    method: 'POST',
    body: { optionId }
  });
}

export async function confirmAssistant(base, sessionId, token, idempotencyKey) {
  return requestJson(base, `/api/assistant/sessions/${encodeURIComponent(sessionId)}/confirm`, {
    method: 'POST',
    body: { token, idempotencyKey }
  });
}

export function firstChoiceOption(card) {
  const blocks = card?.blocks || [];
  for (const block of blocks) {
    if (block.type === 'Choice' && Array.isArray(block.options) && block.options.length > 0) {
      return block.options[0].id;
    }
  }
  return null;
}

export function demoValue(field) {
  const deadline = new Date(Date.now() + 6 * 60 * 60 * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  const iso = `${deadline.getFullYear()}-${pad(deadline.getMonth() + 1)}-${pad(deadline.getDate())}`
    + `T${pad(deadline.getHours())}:${pad(deadline.getMinutes())}:00`;
  switch (field) {
    case 'deadline': return iso;
    case 'location': return '三号宿舍楼';
    case 'deliveryLocation': return '三号宿舍楼东门';
    case 'rewardType': return 'CASH';
    case 'rewardAmount': return 8;
    case 'itemCount': return 3;
    case 'timeWindow': return '今晚 20:00-22:00';
    case 'riskDisclosureConfirmed': return true;
    case 'requiresProof': return true;
    case 'onlyInAppContact': return true;
    case 'shareDormRoom': return false;
    case 'allowOfflineContact': return false;
    case 'completionStandard': return '送到三号宿舍楼东门并当面交付';
    case 'contactPreference': return '站内消息';
    case 'title': return '帮取三个快递';
    default: return '都可以';
  }
}

/**
 * 走到确认卡就停：开始 → 补全追问（最多 4 轮）→ 选第一个方案。
 * 压测和故障脚本都要先拿到一张可确认的卡。
 */
export async function prepareConfirmation(base, goal, context = {}) {
  let response = await startAssistant(base, goal, context);
  requireOk(response, 'POST /api/assistant');
  let body = response.body;
  const sessionId = body.sessionId;

  for (let round = 0; round < 4 && body.card?.kind === 'clarification'; round += 1) {
    const unknowns = body.unknowns || [];
    if (unknowns.length === 0) break;
    const answers = unknowns.map((question) => ({
      field: question.field,
      value: demoValue(question.field)
    }));
    response = await answerAssistant(base, sessionId, answers);
    requireOk(response, 'POST /answers');
    body = response.body;
  }

  if (body.card?.kind === 'comparison') {
    const optionId = firstChoiceOption(body.card);
    if (optionId) {
      response = await selectAssistant(base, sessionId, optionId);
      requireOk(response, 'POST /select');
      body = response.body;
    }
  }

  return { sessionId, body, token: body.card?.token?.value ?? null };
}

/**
 * 完整链路：准备确认卡 → 确认建单。返回 sessionId / taskId / 确认响应。
 */
export async function runFullFlow(base, goal, options = {}) {
  const prepared = await prepareConfirmation(base, goal, options.context || {});
  if (!prepared.token) {
    throw new Error('没有拿到确认令牌，链路停在 ' + (prepared.body?.state || 'unknown'));
  }
  const idempotencyKey = options.idempotencyKey || `flow-${crypto.randomUUID()}`;
  const confirmation = await confirmAssistant(
    base, prepared.sessionId, prepared.token, idempotencyKey);
  requireOk(confirmation, 'POST /confirm');
  return {
    sessionId: prepared.sessionId,
    token: prepared.token,
    idempotencyKey,
    confirmation: confirmation.body,
    taskId: confirmation.body?.taskId ?? null
  };
}

export function parseSseFrame(raw) {
  const lines = raw.replace(/\r\n/g, '\n').split('\n');
  let id = null;
  let event = 'message';
  const data = [];
  for (const line of lines) {
    if (line.startsWith('id:')) id = line.slice(3).trim();
    else if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).trim());
  }
  if (data.length === 0) return null;
  const text = data.join('\n');
  let payload = null;
  try {
    payload = JSON.parse(text);
  } catch {
    payload = text;
  }
  return { id, event, data: payload, raw };
}

/**
 * 读 SSE。abortAfterFrames > 0 时读到第 N 帧就主动断开，用来模拟流式中断。
 */
export async function readSse(base, sessionId, options = {}) {
  const {
    after = 0,
    abortAfterFrames = Number.POSITIVE_INFINITY,
    timeoutMs = 30000
  } = options;
  const url = new URL(
    `/api/stream?sessionId=${encodeURIComponent(sessionId)}&after=${after}`, base);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const frames = [];
  const startedAt = performance.now();
  let firstFrameMs = null;
  let response;
  try {
    response = await fetch(url, {
      headers: { accept: 'text/event-stream' },
      signal: controller.signal
    });
    if (!response.ok) {
      throw new Error(`GET /api/stream 返回 HTTP ${response.status}`);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (value) buffer += decoder.decode(value, { stream: true });
        buffer = buffer.replace(/\r\n/g, '\n');
        let index;
        while ((index = buffer.indexOf('\n\n')) >= 0) {
          const raw = buffer.slice(0, index);
          buffer = buffer.slice(index + 2);
          const frame = parseSseFrame(raw);
          if (frame) {
            frames.push(frame);
            if (firstFrameMs === null) {
              firstFrameMs = performance.now() - startedAt;
            }
          }
          if (frames.length >= abortAfterFrames) {
            controller.abort();
            return { frames, aborted: true, status: response.status, firstFrameMs };
          }
        }
        if (done) break;
      }
    } finally {
      reader.cancel().catch(() => {});
    }
    return { frames, aborted: false, status: response.status, firstFrameMs };
  } catch (error) {
    if (error.name === 'AbortError') {
      return { frames, aborted: true, status: response?.status ?? 0, firstFrameMs };
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export async function getSessionEvents(base, sessionId, limit = 200) {
  const response = await requestJson(
    base, `/api/events?sessionId=${encodeURIComponent(sessionId)}&limit=${limit}`);
  return requireOk(response, 'GET /api/events');
}

export function sequenceOf(frame) {
  const value = frame?.data?.sequence;
  return typeof value === 'number' ? value : Number(frame?.id);
}
