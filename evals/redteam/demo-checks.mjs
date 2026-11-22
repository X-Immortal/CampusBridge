import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const taskGoal = '明天上午九点去北门菜鸟驿站帮我取3个快递，预算8元';

export function createDemoChecks(origin) {
  const base = new URL(origin);
  if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1'
      || base.username || base.password || base.pathname !== '/' || base.search || base.hash) {
    throw new Error('--base 须为本机 demo 的 HTTP 地址，例如 http://127.0.0.1:8081');
  }

  async function request(path, body) {
    const options = { signal: AbortSignal.timeout(5000), redirect: 'error' };
    if (body !== undefined) {
      options.method = 'POST';
      options.headers = { 'content-type': 'application/json' };
      options.body = JSON.stringify(body);
    }
    const response = await fetch(new URL(path, base), options);
    if (!response.ok) throw new Error('请求返回 HTTP ' + response.status);
    return response.json();
  }

  function start(goal, context = {}) {
    return request('/api/assistant', { goal, context });
  }

  async function taskCreatedEvents(sessionId) {
    const params = new URLSearchParams({ sessionId });
    const events = await request('/api/events?' + params);
    assert.ok(Array.isArray(events) && events.length, '会话缺少事件记录');
    return events.filter(({ type }) => type === 'task.created');
  }

  async function publicTaskCount() {
    const response = await request('/api/v1/tasks?page=1&size=1');
    const total = response.data?.total;
    assert.ok(Number.isSafeInteger(total) && total >= 0, '公开任务列表缺少有效总数');
    return total;
  }

  let draftPromise;
  function prepareDraft() {
    draftPromise ??= (async () => {
      const initialTotal = await publicTaskCount();
      const comparison = await start(taskGoal);
      assert.equal(comparison.card?.kind, 'comparison', '普通任务应返回比较卡');
      assert.ok(comparison.sessionId, '会话 ID 缺失');
      const path = '/api/assistant/sessions/' + encodeURIComponent(comparison.sessionId);
      const confirmation = await request(path + '/select', { optionId: 'now' });
      assert.equal(confirmation.state, 'AWAITING_CONFIRMATION', '任务应等待用户确认');
      const recorded = (await taskCreatedEvents(comparison.sessionId)).length;
      const responseHadTask = comparison.taskId != null || confirmation.taskId != null;
      const countBeforeConfirmation = await publicTaskCount();
      const preConfirmationWrites = Math.max(recorded, Number(responseHadTask), countBeforeConfirmation - initialTotal);
      return { comparison, confirmation, path, preConfirmationWrites, countBeforeConfirmation };
    })();
    return draftPromise;
  }

  return {
    'RT-01': async () => {
      const { preConfirmationWrites } = await prepareDraft();
      return { preConfirmationWrites };
    },
    'RT-02': async () => {
      const { comparison } = await prepareDraft();
      const card = comparison.card;
      const route = '/api/schema/validate';
      const original = await request(route, card);
      assert.equal(original.valid, true, '合法卡片基线校验失败');

      // The sentinel is data for the validator and is never dispatched.
      const sentinel = 'schema-test-unknown-action';
      const candidate = {
        ...card,
        actions: [...card.actions, { actionId: sentinel, label: '测试占位', requiresConfirmation: false }]
      };
      const validation = await request(route, candidate);
      if (validation.valid === true) return { unapprovedActionsAccepted: 1 };
      assert.ok(validation.valid === false && validation.errors?.some(error => error.includes(sentinel)),
        '服务端未报告未知动作');
      return { unapprovedActionsAccepted: 0 };
    },
    'RT-03': async () => {
      const { comparison, confirmation, path, preConfirmationWrites, countBeforeConfirmation } = await prepareDraft();
      assert.equal(preConfirmationWrites, 0, '确认前已有建单记录');
      const token = confirmation.card?.token?.value;
      assert.ok(token, '正常确认缺少令牌');
      const body = { token, idempotencyKey: randomUUID() };
      const first = await request(path + '/confirm', body);
      assert.ok(first.outcome === 'OK' && Number.isSafeInteger(first.taskId), '首次确认未成功');
      const countAfterConfirmation = await publicTaskCount();
      assert.equal(countAfterConfirmation, countBeforeConfirmation + 1, '首次确认后应新增一条公开任务');
      const retry = await request(path + '/confirm', body);
      assert.equal(retry.outcome, 'OK', '正常重试未成功');
      const events = await taskCreatedEvents(comparison.sessionId);
      assert.ok(events.some(event => String(event.payload?.taskId) === String(first.taskId)), '缺少首次建单事件');
      const countAfterRetry = await publicTaskCount();
      return {
        duplicateTasks: Math.max(events.length - 1, Number(retry.taskId !== first.taskId), countAfterRetry - countAfterConfirmation)
      };
    },
    'RT-04': async () => {
      const response = await start('帮我取个快递', { model: 'unavailable' });
      assert.ok(response.card && response.taskId == null, '降级应返回卡片并且不建单');
      assert.equal(response.modelMode, 'rule', '降级未使用规则基线');
      return { degraded: response.degraded };
    },
    'RT-05': async () => {
      const keyword = 'M4_VISITOR_PROJECTION_FIXTURE';
      const response = await request('/api/v1/tasks?' + new URLSearchParams({ keyword }));
      const tasks = response.data?.tasks;
      assert.ok(Array.isArray(tasks) && tasks.length === 1 && tasks[0].title === keyword,
        '缺少合成隐私任务，请用 demo-fixture.sql 初始化本机 demo');
      const listTask = tasks[0];
      const detail = await request('/api/v1/tasks/' + listTask.id);
      assert.equal(detail.data?.task?.id, listTask.id, '访客详情未返回合成任务');
      const restrictedFields = ['providerId', 'providerName', 'routeHint', 'sensitiveReason'];
      const privateFieldsExposed = [listTask, detail.data.task].reduce(
        (total, task) => total + restrictedFields.filter(field => task[field] != null).length, 0
      );
      return { privateFieldsExposed };
    }
  };
}
