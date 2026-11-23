'use strict';

const state = {
  sessionId: null,
  traceId: null,
  taskId: null,
  card: null,
  idempotencyKey: null,
  busy: false,
};

const $ = (id) => document.getElementById(id);
const log = $('log');
let fieldSequence = 0;

const CARD_META = {
  clarification: { label: '澄清卡', status: '待补充', icon: 'message' },
  comparison: { label: '方案比较卡', status: '待选择', icon: 'route' },
  confirmation: { label: '确认卡', status: '待授权', icon: 'clipboard' },
  progress: { label: '执行进度卡', status: '已执行', icon: 'circle-check' },
};

const SLOT_LABELS = {
  title: '任务标题',
  category: '任务类型',
  deadline: '最晚时间',
  location: '取件地点',
  deliveryLocation: '送达地点',
  campus: '校区',
  rewardType: '答谢方式',
  rewardAmount: '答谢数量',
  rewardDescription: '答谢说明',
  itemCount: '物品数量',
  completionStandard: '完成标准',
  requiresProof: '需要完成凭证',
  shareDormRoom: '共享宿舍号',
  onlyInAppContact: '仅站内联系',
  riskDisclosureConfirmed: '风险披露',
  preferredHelper: '帮助者偏好',
  goal: '需求原文',
  intent: '识别意图',
  currency: '币种',
  riskLevel: '风险等级',
};

const VALUE_LABELS = {
  CASH: '现金',
  POINTS: '积分',
  NEGOTIABLE: '面议',
  FREE: '免费',
  ERRAND: '校园跑腿',
  AGENCY: '委托代办',
  ERRAND_PICKUP: '代取快递',
  CNY: '人民币',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
};

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function svgIcon(name, className = '') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  if (className) svg.setAttribute('class', className);
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `#icon-${name}`);
  svg.appendChild(use);
  return svg;
}

function formatTime(date = new Date()) {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function scrollLog() {
  requestAnimationFrame(() => {
    log.scrollTop = log.scrollHeight;
  });
}

function createMessage(who = 'assistant') {
  $('welcome').hidden = true;
  const wrapper = el('div', `msg ${who}`);
  const avatar = el('span', 'message-avatar');
  if (who === 'user') {
    avatar.textContent = '你';
  } else {
    avatar.appendChild(svgIcon('sparkles'));
  }

  const body = el('div', 'message-body');
  const meta = el('div', 'message-meta');
  meta.appendChild(el('strong', '', who === 'user' ? '你' : '校园互助助手'));
  meta.appendChild(el('span', '', formatTime()));
  const bubble = el('div', 'bubble');
  body.appendChild(meta);
  body.appendChild(bubble);
  wrapper.appendChild(avatar);
  wrapper.appendChild(body);
  log.appendChild(wrapper);
  scrollLog();
  return { wrapper, body, bubble };
}

function addMessage(text, who = 'assistant') {
  const { wrapper, bubble } = createMessage(who);
  bubble.textContent = text;
  scrollLog();
  return wrapper;
}

function setBusy(busy) {
  state.busy = busy;
  $('send').disabled = busy;
  $('send').classList.toggle('is-loading', busy);
  $('sendLabel').textContent = busy ? '思考中' : '发送';
  document.querySelector('.chat').setAttribute('aria-busy', String(busy));
}

async function post(path, body) {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body || {}),
  });
  const data = await response.json().catch(() => ({}));
  return { status: response.status, data };
}

async function loadOntology() {
  const tag = $('ontologyTag');
  try {
    const response = await fetch('/api/ontology');
    if (!response.ok) throw new Error('Ontology unavailable');
    const data = await response.json();
    tag.replaceChildren();
    tag.appendChild(el('span', 'status-dot'));
    tag.appendChild(document.createTextNode(
      `本体 v${data.version} · ${data.counts.concepts} 概念 · ${data.counts.relations} 关系 · ${data.counts.constraints} 约束`
    ));
    tag.classList.add('is-ready');
    tag.title = `本体指纹 ${data.hash || '—'}，更新于 ${data.updatedAt || '—'}`;
  } catch (error) {
    tag.replaceChildren();
    tag.appendChild(el('span', 'status-dot'));
    tag.appendChild(document.createTextNode('本体暂不可用'));
    tag.classList.add('is-error');
  }
}

function renderCard(card, response) {
  if (!card) return null;
  state.card = card;
  const meta = CARD_META[card.kind] || { label: '生成卡片', status: '待处理', icon: 'sparkles' };
  const box = el('article', `card card--${card.kind || 'unknown'}`);
  box.dataset.cardKind = card.kind || 'unknown';

  const head = el('div', 'card-head');
  const icon = el('span', 'card-icon');
  icon.appendChild(svgIcon(meta.icon));
  const heading = el('div', 'card-heading');
  heading.appendChild(el('span', 'kind', meta.label));
  heading.appendChild(el('strong', '', card.title || '互助卡片'));
  head.appendChild(icon);
  head.appendChild(heading);
  head.appendChild(el('span', 'card-state', meta.status));
  box.appendChild(head);

  (card.blocks || []).forEach((block) => box.appendChild(renderBlock(block)));

  if (response?.violations?.length) {
    const violations = el('div', 'violations');
    response.violations.forEach((violation) => {
      violations.appendChild(el('div', '', `${violation.ruleId}：${violation.message}`));
    });
    box.appendChild(violations);
  }
  if (card.warnings?.length) {
    const warnings = el('div', 'warnings');
    card.warnings.forEach((warning) => warnings.appendChild(el('div', '', warning)));
    box.appendChild(warnings);
  }

  if (card.kind === 'confirmation') {
    const note = el('div', 'confirmation-note');
    note.appendChild(svgIcon('shield'));
    note.appendChild(el('span', '', '请逐项核对。只有点击“确认发布”后，系统才会校验授权并创建任务。'));
    box.appendChild(note);
  }

  const actions = el('div', 'actions');
  (card.actions || []).forEach((action) => {
    const button = el('button', action.kind === 'primary' ? 'primary' : '');
    button.type = 'button';
    button.dataset.action = action.actionId;
    button.appendChild(document.createTextNode(action.label));
    const actionIcon = action.actionId === 'confirm' ? 'shield' : action.actionId === 'select' ? 'check' : 'arrow';
    button.appendChild(svgIcon(actionIcon));

    if (action.actionId === 'confirm') {
      button.disabled = !card.token;
      button.onclick = () => confirmCard(card);
    } else if (action.actionId === 'answer') {
      button.onclick = () => submitAnswers(card);
    } else if (action.actionId === 'select') {
      button.onclick = () => selectOption(card);
    } else {
      button.disabled = true;
    }
    actions.appendChild(button);
  });
  if (actions.children.length) box.appendChild(actions);
  return box;
}

function renderBlock(block) {
  const wrap = el('div', 'block');
  switch (block.type) {
    case 'Text':
      wrap.appendChild(el('p', '', block.text));
      break;
    case 'Field': {
      const field = el('div', 'field');
      const label = el('label', '', block.label + (block.required ? ' *' : ''));
      field.appendChild(label);
      if (block.hint) field.appendChild(el('div', 'hint', block.hint));
      let input;
      if (block.field === 'deadline') {
        input = el('input');
        input.type = 'datetime-local';
      } else if (block.field === 'rewardAmount' || block.field === 'itemCount') {
        input = el('input');
        input.type = 'number';
        input.min = '0';
      } else if (block.field === 'rewardType') {
        input = el('select');
        [['', '请选择答谢方式'], ['CASH', '现金'], ['POINTS', '积分'], ['NEGOTIABLE', '面议'], ['FREE', '免费']]
          .forEach(([value, text]) => {
            const option = el('option', '', text);
            option.value = value;
            input.appendChild(option);
          });
      } else if (block.field === 'riskDisclosureConfirmed') {
        input = el('select');
        [['', '请选择'], ['true', '是，我已阅读并理解'], ['false', '还没有']].forEach(([value, text]) => {
          const option = el('option', '', text);
          option.value = value;
          input.appendChild(option);
        });
      } else {
        input = el('input');
      }
      input.name = block.field;
      input.dataset.field = block.field;
      input.id = `field-${block.field}-${++fieldSequence}`;
      input.placeholder = ({
        location: '例如：北门菜鸟驿站',
        deliveryLocation: '例如：5 号楼一层货架',
        rewardAmount: '例如：8',
        itemCount: '例如：3',
        completionStandard: '怎样才算完成？请写得具体一些',
        title: '用一句话概括这个任务',
      })[block.field] || '';
      label.htmlFor = input.id;
      field.appendChild(input);
      wrap.appendChild(field);
      break;
    }
    case 'Choice': {
      const group = el('div', 'choice');
      (block.options || []).forEach((option, index) => {
        const label = el('label');
        const radio = el('input');
        radio.type = 'radio';
        radio.name = block.name || 'choice';
        radio.value = option.id;
        if (index === 0) radio.checked = true;
        const text = el('span');
        text.appendChild(el('b', '', option.label));
        if (option.detail) text.appendChild(el('small', '', option.detail));
        label.appendChild(radio);
        label.appendChild(text);
        group.appendChild(label);
      });
      wrap.appendChild(group);
      break;
    }
    case 'Summary': {
      const table = el('table', 'rows');
      (block.rows || []).forEach((row) => {
        const tr = el('tr');
        tr.appendChild(el('td', '', row.label));
        const value = el('td', '', row.value);
        const source = el('span', `src${row.source === '系统推测' ? ' inferred' : ''}`, row.source || '');
        value.appendChild(source);
        tr.appendChild(value);
        table.appendChild(tr);
      });
      wrap.appendChild(table);
      break;
    }
    case 'Progress': {
      const step = el('div', 'progress-step');
      step.appendChild(el('span', `dot ${block.state || 'pending'}`));
      step.appendChild(el('span', '', block.step + (block.detail ? ` · ${block.detail}` : '')));
      wrap.appendChild(step);
      break;
    }
    default:
      wrap.appendChild(el('p', 'unsupported', `[未识别的组件 ${block.type}，已拒绝渲染]`));
  }
  return wrap;
}

function updateJourney(response) {
  const currentState = response?.state || '';
  const stageByState = {
    CLARIFYING: 0,
    PROPOSING: 1,
    AWAITING_CONFIRMATION: 2,
    CREATED: 3,
  };
  const stage = stageByState[currentState] ?? 0;
  const created = currentState === 'CREATED' || Boolean(response?.taskId);
  document.querySelectorAll('.journey-step').forEach((item, index) => {
    const done = created || index < stage;
    const active = Boolean(response) && !created && index === stage;
    item.classList.toggle('done', done);
    item.classList.toggle('active', active || (!response && index === 0));
    const marker = item.querySelector('.journey-marker');
    marker.replaceChildren();
    if (done) {
      marker.appendChild(svgIcon('check'));
    } else {
      marker.textContent = String(index + 1);
    }
  });

  const badge = $('sessionBadge');
  badge.className = 'badge';
  if (!response) {
    badge.textContent = '等待开始';
  } else if (created) {
    badge.textContent = '任务已创建';
    badge.classList.add('ok');
  } else {
    const labels = {
      CLARIFYING: '待补充信息',
      PROPOSING: '待选择方案',
      AWAITING_CONFIRMATION: '待你确认',
    };
    badge.textContent = labels[currentState] || '正在理解';
    badge.classList.add(currentState === 'AWAITING_CONFIRMATION' ? 'warn' : 'ok');
  }
}

function formatSlotValue(value) {
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (value === null || value === undefined || value === '') return '—';
  if (Array.isArray(value)) return value.map(formatSlotValue).join('、');
  if (typeof value === 'object') return JSON.stringify(value);
  return VALUE_LABELS[String(value)] || String(value);
}

async function updateSide(response) {
  if (!response) return;
  state.sessionId = response.sessionId || state.sessionId;
  state.traceId = response.traceId || state.traceId;
  state.taskId = response.taskId ?? state.taskId;
  $('state').textContent = response.state || '—';
  $('mode').textContent = (response.modelMode || '—') + (response.degraded ? ' · 降级' : '');
  $('sessionId').textContent = state.sessionId || '—';
  $('traceId').textContent = state.traceId || '—';
  $('taskId').textContent = state.taskId == null ? '—' : `#${state.taskId}`;
  updateJourney(response);

  const assumptions = $('assumptions');
  assumptions.innerHTML = '';
  const list = response.assumptions?.length ? response.assumptions : ['这次没有替你做假设'];
  list.forEach((item) => assumptions.appendChild(el('li', '', item)));

  const slots = $('slots');
  slots.innerHTML = '';
  const values = response.understanding?.slots || {};
  const entries = Object.entries(values).slice(0, 12);
  if (!entries.length) {
    slots.appendChild(el('span', '', '等待补全'));
    slots.appendChild(el('span', '', '暂时没有识别到结构化信息'));
  }
  entries.forEach(([key, value]) => {
    slots.appendChild(el('span', '', SLOT_LABELS[key] || key));
    slots.appendChild(el('span', '', formatSlotValue(value)));
  });

  await refreshEvents();
  if (state.taskId != null) $('eventPanel').open = true;
}

async function refreshEvents() {
  if (!state.sessionId) return;
  try {
    const response = await fetch(`/api/events?sessionId=${state.sessionId}&limit=40`);
    if (!response.ok) throw new Error('Events unavailable');
    const events = await response.json();
    const box = $('events');
    box.innerHTML = '';
    $('eventCount').textContent = `${events.length} 条`;
    if (!events.length) {
      box.appendChild(el('div', 'events-empty', '事件已创建，等待写入执行记录。'));
      return;
    }
    events.forEach((event) => {
      const row = el('div', 'event-row');
      row.appendChild(el('span', 'event-dot'));
      const content = el('div');
      content.appendChild(el('b', '', event.type));
      content.appendChild(el('code', '', JSON.stringify(event.payload).slice(0, 160)));
      row.appendChild(content);
      box.appendChild(row);
    });
  } catch (error) {
    $('events').textContent = '事件读取失败，请稍后重试。';
    $('eventCount').textContent = '读取失败';
  }
}

async function start(goal) {
  if (state.busy || !goal) return;
  setBusy(true);
  addMessage(goal, 'user');
  try {
    const { status, data } = await post('/api/assistant', { goal, context: { requesterId: 1 } });
    if (status >= 400) {
      addMessage(data.message || '这次请求没有成功，请检查后再试一次。');
      return;
    }
    state.idempotencyKey = null;
    const { bubble } = createMessage('assistant');
    bubble.textContent = data.card?.kind === 'clarification'
      ? '这个目标还差几项，我先问清楚，不会替你编。'
      : '我看明白了。先比较一下互助方式，再由你决定是否发布。';
    const cardNode = renderCard(data.card, data);
    if (cardNode) bubble.appendChild(cardNode);
    scrollLog();
    await updateSide(data);
  } catch (error) {
    addMessage('暂时无法连接互助服务。请确认后端已启动，然后再试一次。');
  } finally {
    setBusy(false);
    $('goal').focus();
  }
}

function collectAnswers() {
  const answers = [];
  log.querySelectorAll('input[data-field], select[data-field]').forEach((input) => {
    if (!input.value) return;
    let value = input.value;
    if (input.dataset.field === 'rewardAmount' || input.dataset.field === 'itemCount') value = Number(value);
    if (input.dataset.field === 'riskDisclosureConfirmed') value = value === 'true';
    answers.push({ field: input.dataset.field, value });
  });
  return answers;
}

async function submitAnswers() {
  if (state.busy) return;
  const answers = collectAnswers();
  if (!answers.length) {
    addMessage('上面那几项至少填一个，我才能继续。');
    return;
  }
  setBusy(true);
  try {
    const { status, data } = await post(`/api/assistant/sessions/${state.sessionId}/answers`, { answers });
    if (status >= 400) {
      addMessage(data.message || '提交失败，请检查填写内容。');
      return;
    }
    addMessage(`已补全：${answers.map((answer) => SLOT_LABELS[answer.field] || answer.field).join('、')}`, 'user');
    await appendCardMessage(data);
  } catch (error) {
    addMessage('提交时连接中断，请稍后重试。');
  } finally {
    setBusy(false);
  }
}

async function appendCardMessage(data) {
  const { bubble } = createMessage('assistant');
  const cardNode = renderCard(data.card, data);
  if (cardNode) {
    bubble.appendChild(cardNode);
  } else {
    bubble.textContent = data.notes?.[0] || data.message || '没有生成新的卡片。';
  }
  scrollLog();
  state.idempotencyKey = null;
  await updateSide(data);
}

async function selectOption(card) {
  if (state.busy) return;
  const chosen = log.querySelector('input[type=radio]:checked');
  const optionId = chosen ? chosen.value : 'now';
  setBusy(true);
  try {
    const { status, data } = await post(`/api/assistant/sessions/${state.sessionId}/select`, { optionId });
    if (status >= 400) {
      addMessage(data.message || '选择失败，请重新选择。');
      return;
    }
    const optionText = chosen?.closest('label')?.querySelector('b')?.textContent || optionId;
    addMessage(`我选择：${optionText}`, 'user');
    await appendCardMessage(data);
  } catch (error) {
    addMessage('选择时连接中断，请稍后重试。');
  } finally {
    setBusy(false);
  }
}

async function confirmCard(card) {
  if (!card.token || state.busy) return;
  if (!state.idempotencyKey) {
    state.idempotencyKey = globalThis.crypto?.randomUUID?.() || `idem-${Date.now()}`;
  }
  setBusy(true);
  try {
    const { status, data } = await post(`/api/assistant/sessions/${state.sessionId}/confirm`, {
      token: card.token.value,
      idempotencyKey: state.idempotencyKey,
    });
    if (status >= 400) {
      addMessage(data.notes?.[0] || data.message || '确认被拒绝了，请查看卡片中的原因。');
      await appendCardMessage(data);
      return;
    }
    addMessage('信息核对无误，确认发布。', 'user');
    await appendCardMessage(data);
    if (data.taskId) {
      addMessage(`任务 #${data.taskId} 已创建。相同确认再次提交也不会重复建单，执行记录可随时回查。`);
    }
  } catch (error) {
    addMessage('确认时连接中断，请稍后使用同一确认卡重试。');
  } finally {
    setBusy(false);
  }
}

function resetConversation() {
  if (state.busy) return;
  state.sessionId = null;
  state.traceId = null;
  state.taskId = null;
  state.card = null;
  state.idempotencyKey = null;
  log.querySelectorAll('.msg').forEach((message) => message.remove());
  $('welcome').hidden = false;
  $('goal').value = '';
  $('state').textContent = '—';
  $('mode').textContent = '—';
  $('sessionId').textContent = '—';
  $('traceId').textContent = '—';
  $('taskId').textContent = '—';
  $('slots').innerHTML = '<span>等待输入</span><span>发送需求后，这里会逐步补全</span>';
  $('assumptions').innerHTML = '<li>暂无，未替你补充未知信息</li>';
  $('events').innerHTML = '<div class="events-empty">开始对话后，执行事件会显示在这里。</div>';
  $('eventCount').textContent = '0 条';
  $('eventPanel').open = false;
  document.querySelector('.tech-details').open = false;
  updateJourney(null);
  log.scrollTop = 0;
  $('goal').focus();
}

$('send').addEventListener('click', () => {
  const goal = $('goal').value.trim();
  if (!goal) {
    $('goal').focus();
    return;
  }
  $('goal').value = '';
  start(goal);
});

$('goal').addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.isComposing) {
    event.preventDefault();
    $('send').click();
  }
});

$('reset').addEventListener('click', resetConversation);

document.querySelectorAll('.chip').forEach((chip) => {
  chip.addEventListener('click', () => {
    if (state.busy) return;
    $('goal').value = chip.dataset.goal;
    $('send').click();
  });
});

loadOntology();
