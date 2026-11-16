/**
 * SSE 线的字节层面：只负责「把一条事件变成一帧」和心跳。
 * 解析（分帧、UTF-8 缓冲、去重、缺口）是 transport 的事，不在这里。
 */

import type { StreamEvent } from './events.ts';

/**
 * 编码一帧。格式固定为：
 *
 *   id: 12
 *   event: message
 *   data: {"protocol":...}
 *
 *   （空行）
 */
export function formatSseFrame(event: StreamEvent): string {
  const json = JSON.stringify(event);
  return 'id: ' + event.sequence + '\n' + 'event: message\n' + 'data: ' + json + '\n\n';
}

/** SSE 注释行，做心跳用；客户端必须忽略以 `:` 开头的行 */
export function formatHeartbeat(): string {
  return ': ping\n\n';
}
