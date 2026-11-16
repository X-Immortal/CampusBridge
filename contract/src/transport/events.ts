/**
 * 流式事件信封（campushub.stream.v1）。
 * 文字版：../../PROTOCOL.md。改这里必须同步改那边。
 */

import { PROTOCOL_VERSION } from '../protocol/types.ts';
import type { ProtocolVersion, StreamEndReason, TaskStatus, ToolStatus, UiCard } from '../protocol/types.ts';

/** 五种事件，一个不多一个不少 */
export type EventKind =
  | 'text.delta'
  | 'schema.card'
  | 'tool.progress'
  | 'task.snapshot'
  | 'stream.end';

export const EVENT_KINDS: EventKind[] = [
  'text.delta',
  'schema.card',
  'tool.progress',
  'task.snapshot',
  'stream.end'
];

/**
 * 负载用一个大接口装所有可选字段，而不是判别联合。
 * 理由：ArkTS 对联合类型收窄支持不稳，客户端 switch(kind) 之后直接读字段更省事。
 */
export interface EventPayload {
  text?: string;
  card?: UiCard;
  toolName?: string;
  status?: ToolStatus;
  progress?: number;
  message?: string;
  taskId?: number;
  taskStatus?: TaskStatus;
  taskVersion?: number;
  reason?: StreamEndReason;
}

/** 一条流式事件 */
export interface StreamEvent {
  protocol: ProtocolVersion;
  eventId: string;
  sequence: number;
  sessionId: string;
  ts: string;
  kind: EventKind;
  payload: EventPayload;
}

export function isEventKind(value: string): boolean {
  for (let i = 0; i < EVENT_KINDS.length; i++) {
    if (EVENT_KINDS[i] === value) {
      return true;
    }
  }
  return false;
}

const STREAM_END_REASONS: StreamEndReason[] = [
  'done',
  'cancelled',
  'error',
  'cursor_expired',
  'model_unavailable'
];

export function isStreamEndReason(value: string): boolean {
  for (let i = 0; i < STREAM_END_REASONS.length; i++) {
    if (STREAM_END_REASONS[i] === value) {
      return true;
    }
  }
  return false;
}

/** 服务端造事件用（mock 和测试也用它，避免手写 eventId/ts） */
export function makeEvent(
  sessionId: string,
  sequence: number,
  kind: EventKind,
  payload: EventPayload
): StreamEvent {
  return {
    protocol: PROTOCOL_VERSION,
    eventId: 'evt-' + sessionId + '-' + sequence,
    sequence: sequence,
    sessionId: sessionId,
    ts: new Date().toISOString(),
    kind: kind,
    payload: payload
  };
}
