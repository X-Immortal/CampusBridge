import { UiCard } from '../protocol/types';
import { isAllowedBlock, isKnownDraftField, isSideEffectAction } from '../protocol/whitelist';

/**
 * 客户端渲染前的最后一道白名单校验（M2 要求 ⑥/⑦ 的客户端半边）。
 *
 * 服务端已经校验过一次；这里再查一遍，原因是「最后一跳」的卡也可能被中间层改写。
 * 只做拒绝，不做裁剪：坏卡整张不渲染，保留上一张，避免出现“只删掉一个危险按钮”的假安全。
 */
export function cardProblems(card: UiCard): string[] {
  const problems: string[] = [];
  if (card.schemaVersion !== '1.0') {
    problems.push('schemaVersion 不认识：' + card.schemaVersion);
  }
  for (let i = 0; i < card.blocks.length; i++) {
    const block = card.blocks[i];
    if (!isAllowedBlock(block.type)) {
      problems.push('非白名单组件：' + block.type);
    }
    if (block.type === 'Field' && block.field !== undefined && !isKnownDraftField(block.field)) {
      problems.push('绑定不存在的字段：' + block.field);
    }
  }
  for (let i = 0; i < card.actions.length; i++) {
    const action = card.actions[i];
    if (isSideEffectAction(action.actionId) && action.requiresConfirmation !== true) {
      problems.push('副作用动作缺少确认：' + action.actionId);
    }
  }
  return problems;
}
