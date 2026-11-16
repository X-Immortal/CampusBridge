/**
 * SSE 客户端解析器（纯 TS，不 import @ohos.*，可直接 node --test）。
 *
 * 职责链（对应 contract/PROTOCOL.md §4）：
 *   字节块 → Utf8ChunkDecoder（保留半个 UTF-8 字符）
 *          → SseFrameParser（按空行切帧，拼 data 行）
 *          → StreamAssembler（校验信封、去重、缺口、游标）
 *
 * 约束：ArkTS 兼容——不用 any / unknown / enum / Record / 判别联合。
 */

import { PROTOCOL_VERSION } from '../protocol/types.ts';
import { isEventKind } from './events.ts';
import type { StreamEvent } from './events.ts';

/** 一个 SSE 帧。id/event 只做链路提示，协议判定只看 data 里的 JSON。 */
export interface SseFrame {
  id: string;
  event: string;
  data: string;
}

/** 接纳结果。status 是调用方唯一该判断的东西。 */
export type AcceptStatus =
  | 'accepted'
  | 'duplicate'
  | 'stale'
  | 'gap'
  | 'protocol_mismatch'
  | 'unknown_kind'
  | 'session_mismatch'
  | 'invalid';

export interface AcceptResult {
  status: AcceptStatus;
  /** accepted 时有值；其余情况是能解析出来的那条事件（可能为空） */
  event?: StreamEvent;
  /** gap 时告诉调用方期望的下一个序号 */
  expectedSequence: number;
  /** invalid 时的可读原因 */
  message?: string;
}

/** UTF-8 增量解码：网络块可能在多字节字符中间断开。 */
export class Utf8ChunkDecoder {
  private tail: number[] = [];

  push(chunk: Uint8Array): string {
    const bytes: number[] = this.tail.slice();
    for (let i = 0; i < chunk.length; i++) {
      bytes.push(chunk[i]);
    }
    this.tail = [];

    let out = '';
    let i = 0;
    while (i < bytes.length) {
      const b0 = bytes[i];

      if (b0 < 0x80) {
        out += String.fromCharCode(b0);
        i += 1;
        continue;
      }

      let need = 0;
      let codePoint = 0;
      if ((b0 & 0xe0) === 0xc0) {
        need = 1;
        codePoint = b0 & 0x1f;
      } else if ((b0 & 0xf0) === 0xe0) {
        need = 2;
        codePoint = b0 & 0x0f;
      } else if ((b0 & 0xf8) === 0xf0) {
        need = 3;
        codePoint = b0 & 0x07;
      } else {
        // 非法起始字节
        out += '\uFFFD';
        i += 1;
        continue;
      }

      if (i + need >= bytes.length) {
        // 这个字符还没收全，留到下一块
        this.tail = bytes.slice(i);
        return out;
      }

      let valid = true;
      for (let k = 1; k <= need; k++) {
        const b = bytes[i + k];
        if ((b & 0xc0) !== 0x80) {
          valid = false;
          break;
        }
        codePoint = (codePoint << 6) | (b & 0x3f);
      }

      if (!valid) {
        out += '\uFFFD';
        i += 1;
        continue;
      }

      out += codePointToString(codePoint);
      i += need + 1;
    }

    return out;
  }

  /** 流结束时调用：残留的半个字符按替换符处理。 */
  flush(): string {
    if (this.tail.length === 0) {
      return '';
    }
    this.tail = [];
    return '\uFFFD';
  }
}

function codePointToString(codePoint: number): string {
  if (codePoint <= 0xffff) {
    return String.fromCharCode(codePoint);
  }
  const v = codePoint - 0x10000;
  return String.fromCharCode(0xd800 + (v >> 10), 0xdc00 + (v & 0x3ff));
}

/** SSE 分帧：空行结束一帧；`:` 开头是心跳注释；data 可以多行。 */
export class SseFrameParser {
  private buffer = '';

  /** 还没成帧的残余（调试/测试用） */
  pending(): string {
    return this.buffer;
  }

  feed(text: string): SseFrame[] {
    if (this.buffer.length === 0 && text.charCodeAt(0) === 0xfeff) {
      text = text.slice(1);
    }
    this.buffer += text;
    // 只处理完整的 CRLF；块尾单独的 \r 等下一块拼上再换
    this.buffer = this.buffer.replace(/\r\n/g, '\n');

    const frames: SseFrame[] = [];
    let blank = this.buffer.indexOf('\n\n');
    while (blank >= 0) {
      const frameText = this.buffer.slice(0, blank);
      this.buffer = this.buffer.slice(blank + 2);
      const frame = parseFrame(frameText);
      if (frame !== undefined) {
        frames.push(frame);
      }
      blank = this.buffer.indexOf('\n\n');
    }
    return frames;
  }

  reset(): void {
    this.buffer = '';
  }
}

function parseFrame(frameText: string): SseFrame | undefined {
  const lines = frameText.split('\n');
  const dataLines: string[] = [];
  let id = '';
  let event = '';

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.length === 0) {
      continue;
    }
    if (line.charAt(0) === ':') {
      continue;
    }

    const colon = line.indexOf(':');
    let field = line;
    let value = '';
    if (colon >= 0) {
      field = line.slice(0, colon);
      value = line.slice(colon + 1);
      if (value.charAt(0) === ' ') {
        value = value.slice(1);
      }
    }

    if (field === 'data') {
      dataLines.push(value);
    } else if (field === 'id') {
      id = value;
    } else if (field === 'event') {
      event = value;
    }
  }

  if (dataLines.length === 0) {
    return undefined;
  }

  return { id: id, event: event, data: dataLines.join('\n') };
}

/**
 * 把 SSE 帧接成 StreamEvent：校验版本/类型/会话，去重，找缺口，尊重 after 游标。
 * 调用方拿到 gap 时应断开并用 after=lastSequence 重连（PROTOCOL.md §5）。
 */
export class StreamAssembler {
  private sessionId: string;
  private lastSeq: number;
  private seenIds: Set<string> = new Set<string>();
  private frameParser: SseFrameParser = new SseFrameParser();
  private decoder: Utf8ChunkDecoder = new Utf8ChunkDecoder();

  constructor(sessionId: string, after?: number) {
    this.sessionId = sessionId;
    if (after !== undefined && after > 0) {
      this.lastSeq = after;
    } else {
      this.lastSeq = 0;
    }
  }

  get lastSequence(): number {
    return this.lastSeq;
  }

  /** 字节入口：先过 UTF-8 解码，再走文本入口。 */
  acceptBytes(chunk: Uint8Array): AcceptResult[] {
    return this.acceptText(this.decoder.push(chunk));
  }

  /** 文本入口：分帧后逐帧接纳。 */
  acceptText(chunk: string): AcceptResult[] {
    const frames = this.frameParser.feed(chunk);
    const results: AcceptResult[] = [];
    for (let i = 0; i < frames.length; i++) {
      results.push(this.acceptFrame(frames[i]));
    }
    return results;
  }

  acceptFrame(frame: SseFrame): AcceptResult {
    let event: StreamEvent;
    try {
      event = JSON.parse(frame.data) as StreamEvent;
    } catch (error) {
      return this.invalid('data 不是合法 JSON');
    }
    if (event === null || event === undefined) {
      return this.invalid('data 是空值');
    }
    return this.acceptEvent(event);
  }

  acceptEvent(event: StreamEvent): AcceptResult {
    if (event.protocol !== PROTOCOL_VERSION) {
      return this.reject('protocol_mismatch', event);
    }
    if (!isEventKind(event.kind)) {
      return this.reject('unknown_kind', event);
    }
    if (event.sessionId !== this.sessionId) {
      return this.reject('session_mismatch', event);
    }
    if (typeof event.eventId !== 'string' || event.eventId.length === 0) {
      return this.invalid('eventId 为空');
    }
    if (typeof event.sequence !== 'number' || event.sequence <= 0) {
      return this.invalid('sequence 非法');
    }
    if (this.seenIds.has(event.eventId)) {
      return this.reject('duplicate', event);
    }
    if (event.sequence <= this.lastSeq) {
      // 重连补放时服务端本不该发，发了也不重新渲染
      return this.reject('stale', event);
    }
    if (event.sequence > this.lastSeq + 1) {
      return {
        status: 'gap',
        event: event,
        expectedSequence: this.lastSeq + 1
      };
    }

    this.seenIds.add(event.eventId);
    this.lastSeq = event.sequence;
    return { status: 'accepted', event: event, expectedSequence: this.lastSeq + 1 };
  }

  /** cursor_expired 后由调用方决定是否丢弃本地游标。 */
  resetCursor(sequence: number): void {
    this.lastSeq = sequence;
    this.seenIds.clear();
    this.frameParser.reset();
    this.decoder = new Utf8ChunkDecoder();
  }

  private reject(status: AcceptStatus, event: StreamEvent): AcceptResult {
    return { status: status, event: event, expectedSequence: this.lastSeq + 1 };
  }

  private invalid(message: string): AcceptResult {
    return { status: 'invalid', expectedSequence: this.lastSeq + 1, message: message };
  }
}
