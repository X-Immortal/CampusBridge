/**
 * 白名单：客户端只能渲染这里的组件，动作只能是这七种。
 * 与后端 SchemaValidator 的常量保持一致；不一致时以本文件为准并当天修后端。
 */

import type { ActionId, BlockType, CardKind, DraftFieldKey, ProgressState } from './types.ts';

/** 白名单组件（后端 SchemaValidator.ALLOWED_BLOCKS 同源） */
export const ALLOWED_BLOCKS: BlockType[] = ['Text', 'Field', 'Choice', 'Summary', 'Progress'];

/** 白名单动作（后端 SchemaValidator.ALLOWED_ACTIONS 同源） */
export const ALLOWED_ACTIONS: ActionId[] = [
  'answer',
  'select',
  'confirm',
  'edit',
  'cancel',
  'retry',
  'open'
];

/** 有副作用的动作：出现时必须带确认，且只能在确认卡上出现 */
export const SIDE_EFFECT_ACTIONS: ActionId[] = ['confirm'];

export const CARD_KINDS: CardKind[] = ['clarification', 'comparison', 'confirmation', 'progress'];

export const PROGRESS_STATES: ProgressState[] = ['done', 'active', 'pending', 'failed'];

/** 草稿字段定义：与后端 DraftField 一一对应 */
export class DraftFieldDef {
  key: DraftFieldKey;
  type: string;
  label: string;

  constructor(key: DraftFieldKey, type: string, label: string) {
    this.key = key;
    this.type = type;
    this.label = label;
  }
}

export const DRAFT_FIELD_DEFS: DraftFieldDef[] = [
  new DraftFieldDef('goal', 'TEXT', '目标原话'),
  new DraftFieldDef('intent', 'TEXT', '意图'),
  new DraftFieldDef('persona', 'TEXT', '人物类型'),
  new DraftFieldDef('title', 'TEXT', '标题'),
  new DraftFieldDef('category', 'TEXT', '任务类型'),
  new DraftFieldDef('campus', 'TEXT', '校区'),
  new DraftFieldDef('location', 'TEXT', '取件地点'),
  new DraftFieldDef('deliveryLocation', 'TEXT', '送达地点'),
  new DraftFieldDef('deadline', 'DATETIME', '截止时间'),
  new DraftFieldDef('timeWindow', 'TEXT', '时间窗描述'),
  new DraftFieldDef('rewardType', 'ENUM', '报酬形式'),
  new DraftFieldDef('rewardAmount', 'NUMBER', '报酬数量'),
  new DraftFieldDef('currency', 'TEXT', '币种'),
  new DraftFieldDef('rewardDescription', 'TEXT', '报酬说明'),
  new DraftFieldDef('completionStandard', 'TEXT', '完成标准'),
  new DraftFieldDef('requiresProof', 'BOOLEAN', '是否需要凭证'),
  new DraftFieldDef('onlyInAppContact', 'BOOLEAN', '仅站内联络'),
  new DraftFieldDef('allowOfflineContact', 'BOOLEAN', '允许线下联络'),
  new DraftFieldDef('riskLevel', 'ENUM', '风险等级'),
  new DraftFieldDef('riskDisclosureConfirmed', 'BOOLEAN', '风险已披露'),
  new DraftFieldDef('shareDormRoom', 'BOOLEAN', '共享宿舍号'),
  new DraftFieldDef('itemCount', 'NUMBER', '取件件数'),
  new DraftFieldDef('contactPreference', 'TEXT', '联络偏好')
];

/** 只看键的列表，校验器用这个 */
export const DRAFT_FIELDS: DraftFieldKey[] = DRAFT_FIELD_DEFS.map((def) => def.key);

export function isAllowedBlock(value: string): boolean {
  for (let i = 0; i < ALLOWED_BLOCKS.length; i++) {
    if (ALLOWED_BLOCKS[i] === value) {
      return true;
    }
  }
  return false;
}

export function isAllowedAction(value: string): boolean {
  for (let i = 0; i < ALLOWED_ACTIONS.length; i++) {
    if (ALLOWED_ACTIONS[i] === value) {
      return true;
    }
  }
  return false;
}

export function isSideEffectAction(value: string): boolean {
  for (let i = 0; i < SIDE_EFFECT_ACTIONS.length; i++) {
    if (SIDE_EFFECT_ACTIONS[i] === value) {
      return true;
    }
  }
  return false;
}

export function isKnownDraftField(value: string): boolean {
  for (let i = 0; i < DRAFT_FIELDS.length; i++) {
    if (DRAFT_FIELDS[i] === value) {
      return true;
    }
  }
  return false;
}

export function isCardKind(value: string): boolean {
  for (let i = 0; i < CARD_KINDS.length; i++) {
    if (CARD_KINDS[i] === value) {
      return true;
    }
  }
  return false;
}

export function isProgressState(value: string): boolean {
  for (let i = 0; i < PROGRESS_STATES.length; i++) {
    if (PROGRESS_STATES[i] === value) {
      return true;
    }
  }
  return false;
}

/** 字段类型（TEXT / DATETIME / NUMBER / BOOLEAN / ENUM），渲染器选控件用 */
export function draftFieldType(key: string): string {
  for (let i = 0; i < DRAFT_FIELD_DEFS.length; i++) {
    if (DRAFT_FIELD_DEFS[i].key === key) {
      return DRAFT_FIELD_DEFS[i].type;
    }
  }
  return 'TEXT';
}
