import { http } from '@kit.NetworkKit';

/**
 * 1.0 公开任务接口的只读封装（M2/M3 的 L2 固定页面）。
 *
 * 只走免登录接口：GET /api/v1/tasks、GET /api/v1/tasks/{id}。
 * 不在这里做业务写操作；建单仍由助手确认链路完成。
 */
export const DEFAULT_BASE = 'http://127.0.0.1:8081';

export interface TaskItem {
  id: number;
  title: string;
  description?: string;
  category?: string;
  campus?: string;
  location?: string;
  deliveryLocation?: string;
  deadline?: string;
  rewardType?: string;
  rewardAmount?: number;
  rewardDescription?: string;
  completionStandard?: string;
  status?: string;
  requesterName?: string;
  providerName?: string;
  createdAt?: string;
}

export interface TaskPageData {
  tasks: TaskItem[];
  total: number;
  page: number;
  size: number;
}

export interface TaskDetailData {
  task: TaskItem;
}

interface TaskPageEnvelope {
  code: string;
  message: string;
  data: TaskPageData;
}

interface TaskDetailEnvelope {
  code: string;
  message: string;
  data: TaskDetailData;
}

async function getText(base: string, path: string): Promise<string> {
  const request = http.createHttp();
  try {
    const response = await request.request(base + path, {
      method: http.RequestMethod.GET,
      expectDataType: http.HttpDataType.STRING,
      connectTimeout: 5000,
      readTimeout: 10000
    });
    const payload = String(response.result);
    console.info('CampusBridge HTTP ' + String(response.responseCode) + ' '
      + payload.substring(0, 120));
    if (response.responseCode !== 200) {
      throw new Error('HTTP ' + String(response.responseCode));
    }
    return payload;
  } finally {
    request.destroy();
  }
}

export async function checkBackend(base: string): Promise<boolean> {
  try {
    const text = await getText(base, '/api/v1/tasks?page=1&size=1');
    const envelope = JSON.parse(text) as TaskPageEnvelope;
    return envelope.code === 'SUCCESS';
  } catch (error) {
    console.error('CampusBridge probe failed: ' + String(error));
    return false;
  }
}

export async function fetchTasks(base: string): Promise<TaskPageData> {
  const text = await getText(base, '/api/v1/tasks?page=1&size=50&sortBy=CREATED_AT_DESC');
  const envelope = JSON.parse(text) as TaskPageEnvelope;
  if (envelope.code !== 'SUCCESS' || envelope.data === null || envelope.data === undefined) {
    throw new Error(envelope.message !== undefined ? envelope.message : '任务列表返回失败');
  }
  return envelope.data;
}

export async function fetchTaskDetail(base: string, taskId: number): Promise<TaskItem> {
  const text = await getText(base, '/api/v1/tasks/' + String(taskId));
  const envelope = JSON.parse(text) as TaskDetailEnvelope;
  if (envelope.code !== 'SUCCESS' || envelope.data === null || envelope.data === undefined
    || envelope.data.task === null || envelope.data.task === undefined) {
    throw new Error(envelope.message !== undefined ? envelope.message : '任务详情返回失败');
  }
  return envelope.data.task;
}

export function textOf(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return value;
}

export function rewardText(task: TaskItem): string {
  const type = textOf(task.rewardType);
  if (type === 'CASH') {
    return '¥' + (task.rewardAmount === undefined || task.rewardAmount === null
      ? '?' : String(task.rewardAmount));
  }
  if (type === 'FREE') {
    return '免费';
  }
  if (type === 'POINTS') {
    return (task.rewardAmount === undefined || task.rewardAmount === null
      ? '?' : String(task.rewardAmount)) + ' 积分';
  }
  if (type === 'NEGOTIABLE') {
    return '面议';
  }
  return '—';
}

export function statusText(status: string | null | undefined): string {
  const value = textOf(status);
  const labels: string[] = [
    'DRAFT|草稿', 'OPEN|待接单', 'PENDING_REVIEW|待审核', 'ACCEPTED|已接单',
    'IN_PROGRESS|进行中', 'PENDING_CONFIRMATION|待确认', 'COMPLETED|已完成',
    'REVIEWED|已评价', 'CANCELLED|已取消', 'DISPUTED|争议中', 'REJECTED|已驳回'
  ];
  for (let i = 0; i < labels.length; i++) {
    const parts = labels[i].split('|');
    if (parts[0] === value) {
      return parts[1];
    }
  }
  return value.length > 0 ? value : '未知';
}
