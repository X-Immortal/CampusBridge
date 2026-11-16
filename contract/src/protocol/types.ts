/**
 * CampusBridge 2.0 · 契约类型（唯一词汇表）
 *
 * 规则：
 * - 只有组长能改。改一次要同步 PROTOCOL.md、fixtures/ 和 test/。
 * - ArkTS 兼容：不用 as const / any / unknown / Record / 索引签名 / enum / 映射类型。
 * - 事件负载用一个 EventPayload 接口装所有可选字段，不做判别联合（ArkTS 收窄不可靠）。
 * - 字段与后端 Java record 一一对应，多一个少一个都算破坏契约。
 */

export type ProtocolVersion = 'campushub.stream.v1';
export type SchemaVersion = '1.0';

export const PROTOCOL_VERSION: ProtocolVersion = 'campushub.stream.v1';
export const SCHEMA_VERSION: SchemaVersion = '1.0';

/** 卡片类型：四种，对应澄清 / 比较 / 确认 / 进度 */
export type CardKind = 'clarification' | 'comparison' | 'confirmation' | 'progress';

/** 白名单组件：只有这五种 block */
export type BlockType = 'Text' | 'Field' | 'Choice' | 'Summary' | 'Progress';

/** 按钮动作：只有这七种 */
export type ActionId = 'answer' | 'select' | 'confirm' | 'edit' | 'cancel' | 'retry' | 'open';

export type ActionKind = 'primary' | 'secondary';

export type ProgressState = 'done' | 'active' | 'pending' | 'failed';

/** 字段来源：用户明说的 / 系统推断的 / 默认值。推断必须让用户看见 */
export type ValueSource = 'USER' | 'INFERRED' | 'DEFAULT';

/** 后端 AgentSession 的状态机（窄状态机：不能跳步） */
export type SessionState =
  | 'CLARIFYING'
  | 'PROPOSING'
  | 'AWAITING_CONFIRMATION'
  | 'CREATED'
  | 'CANCELLED';

/** 1.0 tasks.status 的完整枚举 */
export type TaskStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'PENDING_REVIEW'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'PENDING_CONFIRMATION'
  | 'COMPLETED'
  | 'REVIEWED'
  | 'CANCELLED'
  | 'DISPUTED'
  | 'REJECTED';

/** 草稿字段键：与后端 DraftField 一一对应（23 个） */
export type DraftFieldKey =
  | 'goal'
  | 'intent'
  | 'persona'
  | 'title'
  | 'category'
  | 'campus'
  | 'location'
  | 'deliveryLocation'
  | 'deadline'
  | 'timeWindow'
  | 'rewardType'
  | 'rewardAmount'
  | 'currency'
  | 'rewardDescription'
  | 'completionStandard'
  | 'requiresProof'
  | 'onlyInAppContact'
  | 'allowOfflineContact'
  | 'riskLevel'
  | 'riskDisclosureConfirmed'
  | 'shareDormRoom'
  | 'itemCount'
  | 'contactPreference';

/** 工具进度状态 */
export type ToolStatus = 'started' | 'progress' | 'done' | 'failed';

/** 流结束原因 */
export type StreamEndReason =
  | 'done'
  | 'cancelled'
  | 'error'
  | 'cursor_expired'
  | 'model_unavailable';

/** 比较卡里的一个选项，必须来自业务数据 */
export interface ChoiceOption {
  id: string;
  label: string;
  detail?: string;
}

/** 确认卡里的一行摘要；source 决定界面上要不要标「系统推测」 */
export interface SummaryRow {
  label: string;
  value: string;
  source: ValueSource;
}

/** 确认令牌的对内视图。客户端只能原样回传，不能解析、不能构造 */
export interface TokenView {
  value: string;
  expiresAt: string;
  nonce: string;
  paramDigest: string;
}

/** 卡片上的按钮 */
export interface CardAction {
  actionId: ActionId;
  label: string;
  kind: ActionKind;
  requiresConfirmation: boolean;
}

/** 卡片内容块。字段按 type 取用，其余留空 */
export interface CardBlock {
  type: BlockType;
  text?: string;
  field?: DraftFieldKey;
  label?: string;
  value?: string;
  hint?: string;
  required?: boolean;
  name?: string;
  options?: ChoiceOption[];
  selectedId?: string;
  rows?: SummaryRow[];
  step?: string;
  state?: ProgressState;
  detail?: string;
}

/** 声明式卡片：模型/规则只能产出这个结构，客户端只渲染白名单组件 */
export interface UiCard {
  schemaVersion: SchemaVersion;
  cardId: string;
  sessionId: string;
  taskId?: number;
  taskVersion: number;
  kind: CardKind;
  title: string;
  blocks: CardBlock[];
  actions: CardAction[];
  warnings?: string[];
  paramDigest?: string;
  token?: TokenView;
  generatedBy?: 'model' | 'rule';
}

/** 一条被违反的约束。答辩问「为什么拒绝」就靠它 */
export interface PolicyViolation {
  ruleId: string;
  severity: string;
  title: string;
  message: string;
  codeRef?: string;
}

/** 一个澄清问题：必须说明为什么问 */
export interface ClarificationQuestion {
  field: DraftFieldKey;
  question: string;
  reason: string;
  priority: number;
  blocksAction: boolean;
}

/** POST /api/assistant/... 的统一返回（同步通道） */
export interface AssistantResponse {
  sessionId: string;
  traceId: string;
  state: SessionState;
  intent?: string;
  understanding?: Object;
  unknowns?: ClarificationQuestion[];
  assumptions?: string[];
  modelMode?: string;
  degraded?: boolean;
  taskId?: number;
  card?: UiCard;
  violations?: PolicyViolation[];
  outcome?: string;
  notes?: string[];
}

/** 工具执行结果。replay=true 表示幂等命中，没有产生第二次事实 */
export interface ToolResult {
  ok: boolean;
  replay: boolean;
  taskId?: number;
  message: string;
  violations?: PolicyViolation[];
}

/** POST /api/assistant */
export interface StartRequest {
  goal: string;
  context?: Object;
}

export interface AnswerInput {
  field: DraftFieldKey;
  value: Object;
}

/** POST /api/assistant/sessions/{id}/answers */
export interface AnswerRequest {
  answers: AnswerInput[];
}

/** POST /api/assistant/sessions/{id}/select */
export interface SelectRequest {
  optionId: string;
}

/** POST /api/assistant/sessions/{id}/confirm。幂等键由客户端生成，同一张卡重复点必须用同一个键 */
export interface ConfirmRequest {
  token: string;
  idempotencyKey: string;
}
