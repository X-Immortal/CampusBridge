/**
 * 客户端草稿模型（纯 TS，不 import @ohos.*，node --test 可跑）。
 *
 * 这层管三件事（对应 M2 的 Preview → Confirm → Execute → Record）：
 * 1. 每个字段记来源：USER / INFERRED / DEFAULT —— 界面必须能区分「你说的」和「我猜的」。
 * 2. 任何字段变化都让草稿版本 +1；确认卡绑定的旧版本立即失效（K13）。
 * 3. 确认令牌只在 AWAITING_CONFIRMATION 阶段、且版本一致、未过期时可换到 CREATED。
 *
 * 约束：ArkTS 兼容——不用 enum / any / unknown / Record / 判别联合。
 */

import type { DraftFieldKey, TaskStatus, ValueSource } from '../protocol/types.ts';

/** 与 SessionState 对齐的窄状态机：不能跳步 */
export type DraftPhase =
  | 'CLARIFYING'
  | 'PROPOSING'
  | 'AWAITING_CONFIRMATION'
  | 'CREATED'
  | 'CANCELLED';

export interface DraftFieldValue {
  field: DraftFieldKey;
  value: string;
  source: ValueSource;
}

export interface TaskDraft {
  sessionId: string;
  taskId?: number;
  taskStatus?: TaskStatus;
  /** 从 1 开始；每次字段变化 +1 */
  version: number;
  phase: DraftPhase;
  fields: DraftFieldValue[];
  /** 当前确认卡绑定的版本；旧卡用它判断是否失效 */
  cardVersion?: number;
  /** 确认卡令牌过期时间（ISO），由服务端给 */
  tokenExpiresAt?: string;
}

/** 拒绝原因码；客户端据此显示「这张卡过期了，已按最新内容重出」 */
export type ConfirmCode =
  | 'OK'
  | 'K13_STALE_CARD'
  | 'K13_MISSING_TOKEN'
  | 'K13_TOKEN_EXPIRED'
  | 'K13_VERSION_MISMATCH'
  | 'K13_WRONG_PHASE';

export interface ConfirmResult {
  ok: boolean;
  code: ConfirmCode;
  message: string;
}

export class DraftStore {
  private current: TaskDraft;

  constructor(sessionId: string) {
    this.current = {
      sessionId: sessionId,
      version: 1,
      phase: 'CLARIFYING',
      fields: []
    };
  }

  get draft(): TaskDraft {
    return this.current;
  }

  /** 草稿有没有这个字段（用于「还缺什么」） */
  hasField(field: DraftFieldKey): boolean {
    for (let i = 0; i < this.current.fields.length; i++) {
      if (this.current.fields[i].field === field) {
        return true;
      }
    }
    return false;
  }

  getField(field: DraftFieldKey): DraftFieldValue | undefined {
    for (let i = 0; i < this.current.fields.length; i++) {
      if (this.current.fields[i].field === field) {
        return this.current.fields[i];
      }
    }
    return undefined;
  }

  /** 用户回答或编辑：来源 USER，版本 +1，旧确认立即失效 */
  setUserField(field: DraftFieldKey, value: string): TaskDraft {
    return this.setField(field, value, 'USER');
  }

  /** 模型推断：来源 INFERRED，界面要标「系统推测」 */
  setInferredField(field: DraftFieldKey, value: string): TaskDraft {
    return this.setField(field, value, 'INFERRED');
  }

  setDefaultField(field: DraftFieldKey, value: string): TaskDraft {
    return this.setField(field, value, 'DEFAULT');
  }

  setField(field: DraftFieldKey, value: string, source: ValueSource): TaskDraft {
    let replaced = false;
    for (let i = 0; i < this.current.fields.length; i++) {
      if (this.current.fields[i].field === field) {
        this.current.fields[i] = { field: field, value: value, source: source };
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      this.current.fields.push({ field: field, value: value, source: source });
    }

    this.current.version += 1;
    // 改了内容，原来那张确认卡就作废，必须重新出卡
    if (this.current.phase === 'AWAITING_CONFIRMATION' || this.current.phase === 'PROPOSING') {
      this.current.phase = 'CLARIFYING';
    }
    this.current.cardVersion = undefined;
    this.current.tokenExpiresAt = undefined;
    return this.current;
  }

  /** 服务端认为信息齐了，出了一张确认卡 */
  presentConfirmation(cardVersion: number, tokenExpiresAt: string): TaskDraft {
    if (this.current.phase !== 'CLARIFYING' && this.current.phase !== 'PROPOSING') {
      throw new Error('presentConfirmation 只能在澄清/提议阶段调用，当前 ' + this.current.phase);
    }
    if (cardVersion !== this.current.version) {
      throw new Error('确认卡版本落后：卡 ' + cardVersion + '，草稿 ' + this.current.version);
    }
    this.current.phase = 'AWAITING_CONFIRMATION';
    this.current.cardVersion = cardVersion;
    this.current.tokenExpiresAt = tokenExpiresAt;
    return this.current;
  }

  /** 点确认前先在本地挡一遍；真正生效仍以服务端返回为准 */
  confirm(token: string, nowIso: string): ConfirmResult {
    if (this.current.phase !== 'AWAITING_CONFIRMATION') {
      return this.reject('K13_WRONG_PHASE', '当前没有待确认的卡');
    }
    if (token.length === 0) {
      return this.reject('K13_MISSING_TOKEN', '确认令牌为空');
    }
    if (this.current.tokenExpiresAt === undefined || this.isExpired(nowIso, this.current.tokenExpiresAt)) {
      return this.reject('K13_TOKEN_EXPIRED', '确认令牌已过期，请刷新后再确认');
    }
    if (this.current.cardVersion !== this.current.version) {
      return this.reject('K13_STALE_CARD', '这张卡对应的是旧版本草稿');
    }
    return { ok: true, code: 'OK', message: '可以提交确认' };
  }

  /** 服务端 task.snapshot 回来：以服务端为准 */
  applySnapshot(taskId: number, taskStatus: TaskStatus, taskVersion: number): boolean {
    if (taskVersion < this.current.version) {
      // 快照比本地旧，忽略；等更新的那条
      return false;
    }
    this.current.taskId = taskId;
    this.current.taskStatus = taskStatus;
    this.current.version = taskVersion;
    this.current.phase = 'CREATED';
    return true;
  }

  /** 服务端返回 409（例如 nonce 复用 / 版本落后）：把草稿退回可编辑 */
  markConfirmRejected(): TaskDraft {
    if (this.current.phase !== 'CREATED') {
      this.current.phase = 'CLARIFYING';
      this.current.cardVersion = undefined;
      this.current.tokenExpiresAt = undefined;
    }
    return this.current;
  }

  cancel(): TaskDraft {
    this.current.phase = 'CANCELLED';
    return this.current;
  }

  /** 旧卡是否已失效：界面用它决定置灰 */
  isCardStale(cardVersion: number): boolean {
    return cardVersion !== this.current.version || this.current.phase !== 'AWAITING_CONFIRMATION';
  }

  private isExpired(nowIso: string, expiresAtIso: string): boolean {
    const now = Date.parse(nowIso);
    const expires = Date.parse(expiresAtIso);
    if (isNaN(now) || isNaN(expires)) {
      // 解析不了就当过期，宁可不放行
      return true;
    }
    return now >= expires;
  }

  private reject(code: ConfirmCode, message: string): ConfirmResult {
    return { ok: false, code: code, message: message };
  }
}
