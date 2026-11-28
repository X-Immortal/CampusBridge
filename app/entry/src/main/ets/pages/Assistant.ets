import { http } from '@kit.NetworkKit';
import { router } from '@kit.ArkUI';
import type {
  AnswerInput,
  AssistantResponse,
  CardAction,
  CardBlock,
  ChoiceOption,
  DraftFieldKey,
  SummaryRow,
  UiCard
} from '../protocol/types';
import { emptyCard } from '../renderer/CardRenderer';
import { cardProblems } from '../ui/cardGuard';
import { DRAFT_FIELD_DEFS, draftFieldType, isSideEffectAction } from '../protocol/whitelist';
import { SseFrameParser, StreamAssembler } from '../transport/sseParser';
import { StreamEvent } from '../transport/events';

const DEFAULT_BASE = 'http://127.0.0.1:8081';
const DEMO_GOAL = '我脚受伤了，今晚需要取三个快递，但不希望把宿舍号直接告诉陌生人';

interface StartContext {
  requesterId: number;
}

interface StartBody {
  goal: string;
  context: StartContext;
}

interface AnswersBody {
  answers: AnswerInput[];
}

interface SelectBody {
  optionId: string;
}

interface ConfirmBody {
  token: string;
  idempotencyKey: string;
}

interface CancelBody {
  reason: string;
}

/** 一次 HTTP 调用的结果：状态码 + 原始 body，解析放在调用方，错误码才有机会被读出来 */
interface HttpResult {
  status: number;
  body: string;
}

/** 先用它判断这是 AssistantResponse 还是 AssistantException 的 {code,message} */
interface ResultProbe {
  sessionId?: string;
  code?: string;
  message?: string;
  status?: number;
}

function pad2(value: number): string {
  return value < 10 ? '0' + String(value) : String(value);
}

/** 建议截止时间：现在 + 6 小时，按 LocalDateTime 的 ISO 形式交给后端 */
function suggestedDeadline(): string {
  const target = new Date(Date.now() + 6 * 60 * 60 * 1000);
  return String(target.getFullYear()) + '-' + pad2(target.getMonth() + 1) + '-'
    + pad2(target.getDate()) + 'T' + pad2(target.getHours()) + ':'
    + pad2(target.getMinutes()) + ':00';
}

function isDigits(text: string): boolean {
  if (text.length === 0) {
    return false;
  }
  for (let i = 0; i < text.length; i++) {
    const ch = text.charAt(i);
    if (ch < '0' || ch > '9') {
      return false;
    }
  }
  return true;
}

/** 只做形状检查：21:00 / 2026-11-27T21:00 / 2026-11-27 21:00 都放行，真正的解析交给后端 */
function looksLikeDateTime(text: string): boolean {
  const trimmed = text.trim();
  if (trimmed.length < 4 || trimmed.indexOf(':') <= 0) {
    return false;
  }
  let digits = 0;
  for (let i = 0; i < trimmed.length; i++) {
    const ch = trimmed.charAt(i);
    if (ch >= '0' && ch <= '9') {
      digits += 1;
      continue;
    }
    if (ch === ':' || ch === '-' || ch === 'T' || ch === ' ') {
      continue;
    }
    return false;
  }
  return digits >= 3;
}

function fieldLabelOf(key: string): string {
  for (let i = 0; i < DRAFT_FIELD_DEFS.length; i++) {
    if (DRAFT_FIELD_DEFS[i].key === key) {
      return DRAFT_FIELD_DEFS[i].label;
    }
  }
  return key;
}

function choiceValuesOf(key: string): string[] {
  if (key === 'rewardType') {
    return ['CASH', 'POINTS', 'NEGOTIABLE', 'FREE'];
  }
  if (key === 'riskLevel') {
    return ['LOW', 'MEDIUM', 'HIGH'];
  }
  if (key === 'currency') {
    return ['CNY'];
  }
  return [];
}

function choiceLabelsOf(key: string): string[] {
  if (key === 'rewardType') {
    return ['现金', '积分', '面议', '免费'];
  }
  if (key === 'riskLevel') {
    return ['低', '中', '高'];
  }
  if (key === 'currency') {
    return ['人民币'];
  }
  return [];
}

/**
 * 助手真实链路页（交互版）。
 *
 * 与旧版最大的区别：不再点一次按钮就把「追问 → 比较 → 确认 → 建单」自动跑完。
 * 这里把每一步拆成用户动作：
 *   1. 一句话开始  → 只出第一张卡（澄清或比较）；
 *   2. 填完澄清卡点「补全并继续」 → 才请求下一张卡；
 *   3. 比较卡选好方案点「就选这个」 → 才签发确认卡和令牌；
 *   4. 确认卡点「确认发布」 → 才真正建单。
 * 任何时候界面只渲染「当前这一张卡」，历史步骤只留一行状态，不堆卡片。
 */
@Entry
@Component
struct Assistant {
  @State baseUrl: string = DEFAULT_BASE;
  @State goal: string = DEMO_GOAL;
  /** idle / clarify / compare / confirm / edit / done */
  @State stage: string = 'idle';
  @State busy: boolean = false;
  @State statusText: string = '';
  @State errorText: string = '';
  @State sessionId: string = '';
  @State sessionState: string = '';
  @State stepIndex: number = -1;
  @State hasCard: boolean = false;
  @State card: UiCard = emptyCard();
  @State warnings: string[] = [];
  @State violations: string[] = [];
  @State notesText: string = '';
  @State assumptionsText: string = '';
  @State showAssumptions: boolean = false;
  @State taskId: number = -1;
  @State streamText: string = '';
  @State selectedOptionId: string = '';
  @State idempotencyKey: string = '';

  // 澄清 / 编辑表单。ArkTS 里改「数组项」才会稳定触发刷新，所以按索引存值。
  @State formFields: string[] = [];
  @State formLabels: string[] = [];
  @State formHints: string[] = [];
  /** text / number / datetime / enum / bool */
  @State formKinds: string[] = [];
  @State formRequired: boolean[] = [];
  @State formValues: string[] = [];
  @State formChoices: string[][] = [];
  @State formChoiceLabels: string[][] = [];

  // 非状态的小账本：记下用户答过哪些字段，确认卡上点「还要改一处」时回填。
  private knownKeys: string[] = [];
  private knownValues: string[] = [];
  private responseCount: number = 0;
  private scroller: Scroller = new Scroller();
  private stepLabels: string[] = ['补全', '选方案', '确认', '建单'];
  private editFieldKeys: string[] = [
    'deadline',
    'location',
    'deliveryLocation',
    'rewardType',
    'rewardAmount',
    'itemCount',
    'completionStandard',
    'contactPreference',
    'title'
  ];

  // ---- 网络 ---------------------------------------------------------------

  /** 去掉末尾斜杠，允许用户手填地址（模拟器走 hdc rport，真机填电脑 IP） */
  private base(): string {
    const trimmed = this.baseUrl.trim();
    if (trimmed.length > 0 && trimmed.charAt(trimmed.length - 1) === '/') {
      return trimmed.substring(0, trimmed.length - 1);
    }
    return trimmed;
  }

  private async postJson(path: string, payload: string): Promise<HttpResult> {
    const request = http.createHttp();
    try {
      const response = await request.request(this.base() + path, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: payload,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 10000,
        readTimeout: 60000
      });
      return { status: Number(response.responseCode), body: String(response.result) };
    } finally {
      request.destroy();
    }
  }

  private async loadStream(): Promise<void> {
    if (this.sessionId.length === 0) {
      return;
    }
    const request = http.createHttp();
    try {
      const url = this.base() + '/api/stream?sessionId='
        + encodeURIComponent(this.sessionId) + '&after=0';
      const response = await request.request(url, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 5000,
        readTimeout: 20000
      });
      const frames = new SseFrameParser().feed(String(response.result));
      const assembler = new StreamAssembler(this.sessionId);
      const kinds: string[] = [];
      for (let i = 0; i < frames.length; i++) {
        const parsed = assembler.acceptFrame(frames[i]);
        if (parsed.status === 'accepted' && parsed.event !== undefined) {
          const event: StreamEvent = parsed.event;
          kinds.push(event.kind);
        }
      }
      this.streamText = '证据流 ' + String(kinds.length) + ' 条：' + kinds.join(' / ');
    } catch (error) {
      this.streamText = '证据流读取失败：' + String(error);
    } finally {
      request.destroy();
    }
  }

  // ---- 四个用户动作 ---------------------------------------------------------

  private async handleStart(): Promise<void> {
    if (this.busy) {
      return;
    }
    const goalText = this.goal.trim();
    if (goalText.length === 0) {
      this.errorText = '先写一句你要办的事';
      return;
    }
    this.busy = true;
    this.clearSession();
    this.statusText = '正在理解这句话…（真模型 reasoning 一般 10–30 秒）';
    const body: StartBody = { goal: goalText, context: { requesterId: 1 } };
    try {
      const result = await this.postJson('/api/assistant', JSON.stringify(body));
      this.applyResult(result);
    } catch (error) {
      this.errorText = '连不上后端：' + String(error);
      this.statusText = '';
    } finally {
      this.busy = false;
    }
  }

  private async submitForm(): Promise<void> {
    if (this.busy) {
      return;
    }
    this.errorText = '';
    const answers: AnswerInput[] = [];
    for (let i = 0; i < this.formFields.length; i++) {
      const raw = this.formValues[i].trim();
      if (raw.length === 0) {
        if (this.formRequired[i]) {
          this.errorText = '「' + this.formLabels[i] + '」还没填';
          this.scrollTop();
          return;
        }
        continue;
      }
      const kind = this.formKinds[i];
      if (kind === 'number' && !isDigits(raw)) {
        this.errorText = '「' + this.formLabels[i] + '」要填数字';
        this.scrollTop();
        return;
      }
      if (kind === 'datetime' && !looksLikeDateTime(raw)) {
        this.errorText = '「' + this.formLabels[i] + '」请按 2026-11-27T22:00 或 22:00 填写';
        this.scrollTop();
        return;
      }
      let value: Object = raw;
      if (kind === 'number') {
        value = Number(raw);
      } else if (kind === 'bool') {
        value = raw === 'true';
      } else if (kind === 'datetime') {
        value = raw.replace(' ', 'T');
      }
      answers.push({ field: this.formFields[i] as DraftFieldKey, value: value });
      this.remember(this.formFields[i], String(value));
    }
    if (answers.length === 0) {
      this.errorText = '至少填一项才能继续';
      this.scrollTop();
      return;
    }
    this.busy = true;
    this.statusText = '提交中，等后端生成下一张卡…';
    const body: AnswersBody = { answers: answers };
    try {
      const result = await this.postJson(
        '/api/assistant/sessions/' + this.sessionId + '/answers', JSON.stringify(body));
      this.applyResult(result);
    } catch (error) {
      this.errorText = '提交失败：' + String(error);
    } finally {
      this.busy = false;
    }
  }

  private async chooseOption(): Promise<void> {
    if (this.busy) {
      return;
    }
    if (this.selectedOptionId.length === 0) {
      this.errorText = '先点一个方案，再按「就选这个」';
      this.scrollTop();
      return;
    }
    this.busy = true;
    this.errorText = '';
    this.statusText = '正在锁定方案并申请确认令牌…';
    const body: SelectBody = { optionId: this.selectedOptionId };
    try {
      const result = await this.postJson(
        '/api/assistant/sessions/' + this.sessionId + '/select', JSON.stringify(body));
      this.applyResult(result);
    } catch (error) {
      this.errorText = '选择失败：' + String(error);
    } finally {
      this.busy = false;
    }
  }

  private async confirmCard(): Promise<void> {
    if (this.busy) {
      return;
    }
    const token = this.card.token;
    if (token === undefined) {
      this.errorText = '这张确认卡没有令牌，不能建单';
      return;
    }
    if (this.idempotencyKey.length === 0) {
      this.idempotencyKey = 'arkui-' + String(Date.now())
        + '-' + String(Math.floor(Math.random() * 100000));
    }
    this.busy = true;
    this.errorText = '';
    this.statusText = '正在校验令牌并写入业务库…';
    const body: ConfirmBody = { token: token.value, idempotencyKey: this.idempotencyKey };
    try {
      const result = await this.postJson(
        '/api/assistant/sessions/' + this.sessionId + '/confirm', JSON.stringify(body));
      this.applyResult(result);
      if (result.status === 409) {
        if (this.violations.length > 0) {
          this.errorText = '被规则拦住：' + this.violations.join('；') + '（点「还要改一处」改完再确认）';
        } else if (this.warnings.length > 0) {
          this.errorText = '建单被拒绝：' + this.warnings.join('；') + '（点「还要改一处」改完再确认）';
        } else {
          this.errorText = '确认被拒绝，原因见卡片';
        }
      }
    } catch (error) {
      this.errorText = '确认失败：' + String(error);
    } finally {
      this.busy = false;
    }
  }

  private async cancelSession(): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.errorText = '';
    this.statusText = '正在撤销这次会话…';
    const body: CancelBody = { reason: '用户取消' };
    try {
      const result = await this.postJson(
        '/api/assistant/sessions/' + this.sessionId + '/cancel', JSON.stringify(body));
      this.applyResult(result);
    } catch (error) {
      this.errorText = '撤销失败：' + String(error);
    } finally {
      this.busy = false;
    }
  }

  private openTask(): void {
    if (this.taskId < 0) {
      this.statusText = '这次没有建单，没有可打开的任务';
      return;
    }
    router.pushUrl({ url: 'pages/TaskDetail', params: { taskId: this.taskId } });
  }

  private onAction(actionId: string): void {
    if (this.busy) {
      return;
    }
    if (actionId === 'answer') {
      this.submitForm();
    } else if (actionId === 'select') {
      this.chooseOption();
    } else if (actionId === 'confirm') {
      this.confirmCard();
    } else if (actionId === 'edit') {
      this.beginEdit();
    } else if (actionId === 'cancel') {
      this.cancelSession();
    } else if (actionId === 'open') {
      this.openTask();
    } else {
      this.errorText = '未知动作：' + actionId;
    }
  }

  // ---- 响应落地 ------------------------------------------------------------

  private applyResult(result: HttpResult): void {
    const text = result.body;
    if (text.length === 0) {
      this.errorText = '后端返回了空 body（HTTP ' + String(result.status) + '）';
      return;
    }
    let probe: ResultProbe = { message: text };
    try {
      probe = JSON.parse(text) as ResultProbe;
    } catch (error) {
      this.errorText = '后端返回不是 JSON（HTTP ' + String(result.status) + '）：' + text;
      return;
    }
    if (probe.sessionId === undefined) {
      this.errorText = probe.message !== undefined ? probe.message : text;
      this.statusText = 'HTTP ' + String(result.status);
      return;
    }
    const response = JSON.parse(text) as AssistantResponse;
    this.applyResponse(response);
  }

  private applyResponse(response: AssistantResponse): void {
    this.responseCount += 1;
    this.sessionId = response.sessionId;
    this.sessionState = response.state;
    this.taskId = response.taskId !== undefined && response.taskId !== null ? response.taskId : -1;
    this.errorText = '';

    const violationLines: string[] = [];
    if (response.violations !== undefined) {
      for (let i = 0; i < response.violations.length; i++) {
        const violation = response.violations[i];
        violationLines.push(violation.ruleId + '：' + violation.message);
      }
    }
    this.violations = violationLines;

    const noteLines: string[] = [];
    if (response.notes !== undefined) {
      for (let i = 0; i < response.notes.length; i++) {
        noteLines.push(response.notes[i]);
      }
    }
    this.notesText = noteLines.join(' · ');

    const assumptionLines: string[] = [];
    if (response.assumptions !== undefined) {
      for (let i = 0; i < response.assumptions.length && i < 2; i++) {
        assumptionLines.push(response.assumptions[i]);
      }
      if (response.assumptions.length > 2) {
        assumptionLines.push('…');
      }
    }
    this.assumptionsText = assumptionLines.join('；');
    // 假设是「第一次理解」的产物，回答之后就可能被覆盖；只在第一张卡上展示，避免和后来的值打架。
    this.showAssumptions = this.responseCount === 1 && this.assumptionsText.length > 0;

    if (response.card === undefined || response.card === null) {
      this.statusText = '后端这次没有生成卡片';
      return;
    }
    const problems = cardProblems(response.card);
    if (problems.length > 0) {
      // 坏卡整张拒绝，界面上保留上一张，绝不做「只删掉一个危险按钮」的假安全。
      this.errorText = '坏卡已拒绝：' + problems.join('；');
      return;
    }

    this.card = response.card;
    this.hasCard = true;
    this.warnings = response.card.warnings !== undefined ? response.card.warnings : [];
    this.stepIndex = this.stepOfState(response.state);
    this.idempotencyKey = '';
    this.selectedOptionId = '';
    this.formFields = [];
    this.formLabels = [];
    this.formHints = [];
    this.formKinds = [];
    this.formRequired = [];
    this.formValues = [];
    this.formChoices = [];
    this.formChoiceLabels = [];

    const kind = response.card.kind;
    if (kind === 'clarification') {
      this.stage = 'clarify';
      this.buildClarifyForm(response.card);
      this.statusText = '还差 ' + String(this.formFields.length) + ' 项。填完点「补全并继续」才会出下一张卡';
    } else if (kind === 'comparison') {
      this.stage = 'compare';
      this.selectedOptionId = this.firstOptionId(response.card);
      this.statusText = '方案定了就点「就选这个」，确认卡才会出现';
    } else if (kind === 'confirmation') {
      this.stage = 'confirm';
      this.statusText = '逐项核对；点「确认发布」才会真的建单';
    } else {
      this.stage = 'done';
      if (this.taskId >= 0) {
        this.statusText = '任务 #' + String(this.taskId) + ' 已创建，可回任务页看到它';
        this.loadStream();
      } else {
        this.statusText = '会话结束，没有产生业务事实';
      }
    }
    this.scrollTop();
  }

  private stepOfState(state: string): number {
    if (state === 'CLARIFYING') {
      return 0;
    }
    if (state === 'PROPOSING') {
      return 1;
    }
    if (state === 'AWAITING_CONFIRMATION') {
      return 2;
    }
    if (state === 'CREATED') {
      return 3;
    }
    return -1;
  }

  // ---- 表单构建 ------------------------------------------------------------

  private buildClarifyForm(card: UiCard): void {
    const fields: string[] = [];
    const labels: string[] = [];
    const hints: string[] = [];
    const kinds: string[] = [];
    const required: boolean[] = [];
    const values: string[] = [];
    const choices: string[][] = [];
    const choiceLabels: string[][] = [];
    for (let i = 0; i < card.blocks.length; i++) {
      const block = card.blocks[i];
      if (block.type !== 'Field' || block.field === undefined) {
        continue;
      }
      const key = String(block.field);
      const type = draftFieldType(key);
      let kind = 'text';
      let options: string[] = [];
      let optionLabels: string[] = [];
      if (type === 'BOOLEAN') {
        kind = 'bool';
        options = ['true', 'false'];
        optionLabels = ['是', '否'];
      } else if (type === 'ENUM') {
        const enumValues = choiceValuesOf(key);
        if (enumValues.length > 0) {
          kind = 'enum';
          options = enumValues;
          optionLabels = choiceLabelsOf(key);
        }
      } else if (type === 'DATETIME') {
        kind = 'datetime';
      } else if (type === 'NUMBER') {
        kind = 'number';
      }
      const initial = this.recall(key);
      fields.push(key);
      labels.push(block.label !== undefined && block.label.length > 0
        ? block.label : fieldLabelOf(key));
      hints.push(block.hint !== undefined ? block.hint : '');
      kinds.push(kind);
      required.push(block.required === true);
      values.push(initial.length > 0 ? initial : (kind === 'datetime' ? suggestedDeadline() : ''));
      choices.push(options);
      choiceLabels.push(optionLabels);
    }
    this.formFields = fields;
    this.formLabels = labels;
    this.formHints = hints;
    this.formKinds = kinds;
    this.formRequired = required;
    this.formValues = values;
    this.formChoices = choices;
    this.formChoiceLabels = choiceLabels;
  }

  /** 确认卡上的「还要改一处」：把答过的字段（外加几个关键字段）重新变成输入框 */
  private buildEditForm(): void {
    const fields: string[] = [];
    const labels: string[] = [];
    const hints: string[] = [];
    const kinds: string[] = [];
    const required: boolean[] = [];
    const values: string[] = [];
    const choices: string[][] = [];
    const choiceLabels: string[][] = [];
    // 业务层对 title 有硬要求，模型不一定会推断出来；所以「还要改一处」里始终给出标题。
    const essentials: string[] = ['title', 'deadline', 'location', 'rewardAmount', 'completionStandard'];
    for (let i = 0; i < this.editFieldKeys.length; i++) {
      const key = this.editFieldKeys[i];
      const remembered = this.recall(key);
      let essential = false;
      for (let k = 0; k < essentials.length; k++) {
        if (essentials[k] === key) {
          essential = true;
        }
      }
      if (remembered.length === 0 && !essential) {
        continue;
      }
      const type = draftFieldType(key);
      let kind = 'text';
      let options: string[] = [];
      let optionLabels: string[] = [];
      if (type === 'BOOLEAN') {
        kind = 'bool';
        options = ['true', 'false'];
        optionLabels = ['是', '否'];
      } else if (type === 'ENUM') {
        const enumValues = choiceValuesOf(key);
        if (enumValues.length > 0) {
          kind = 'enum';
          options = enumValues;
          optionLabels = choiceLabelsOf(key);
        }
      } else if (type === 'DATETIME') {
        kind = 'datetime';
      } else if (type === 'NUMBER') {
        kind = 'number';
      }
      fields.push(key);
      labels.push(fieldLabelOf(key));
      hints.push('');
      kinds.push(kind);
      required.push(false);
      values.push(remembered.length > 0 ? remembered
        : (kind === 'datetime' ? suggestedDeadline() : ''));
      choices.push(options);
      choiceLabels.push(optionLabels);
    }
    this.formFields = fields;
    this.formLabels = labels;
    this.formHints = hints;
    this.formKinds = kinds;
    this.formRequired = required;
    this.formValues = values;
    this.formChoices = choices;
    this.formChoiceLabels = choiceLabels;
  }

  private beginEdit(): void {
    this.buildEditForm();
    this.stage = 'edit';
    this.errorText = '';
    this.statusText = '改完点「保存修改」，会重新出比较卡，再确认一次才建单';
  }

  private cancelEdit(): void {
    this.formFields = [];
    this.formLabels = [];
    this.formHints = [];
    this.formKinds = [];
    this.formRequired = [];
    this.formValues = [];
    this.formChoices = [];
    this.formChoiceLabels = [];
    this.stage = 'confirm';
    this.statusText = '已返回确认卡，什么都没改';
  }

  // ---- 表单小工具 ----------------------------------------------------------

  private formIndex(key: string): number {
    for (let i = 0; i < this.formFields.length; i++) {
      if (this.formFields[i] === key) {
        return i;
      }
    }
    return -1;
  }

  private formValueOf(key: string): string {
    const index = this.formIndex(key);
    return index >= 0 ? this.formValues[index] : '';
  }

  private formKindOf(key: string): string {
    const index = this.formIndex(key);
    return index >= 0 ? this.formKinds[index] : 'text';
  }

  private formLabelOf(key: string): string {
    const index = this.formIndex(key);
    return index >= 0 ? this.formLabels[index] : fieldLabelOf(key);
  }

  private formHintOf(key: string): string {
    const index = this.formIndex(key);
    return index >= 0 ? this.formHints[index] : '';
  }

  private formRequiredOf(key: string): boolean {
    const index = this.formIndex(key);
    return index >= 0 ? this.formRequired[index] : false;
  }

  private formChoicesOf(key: string): string[] {
    const index = this.formIndex(key);
    return index >= 0 ? this.formChoices[index] : [];
  }

  private formChoiceLabelAt(key: string, optionIndex: number): string {
    const index = this.formIndex(key);
    if (index < 0) {
      return '';
    }
    const labels = this.formChoiceLabels[index];
    return optionIndex >= 0 && optionIndex < labels.length ? labels[optionIndex] : '';
  }

  private formPlaceholderOf(key: string): string {
    if (key === 'deadline') {
      return '例如 2026-11-27T22:00 或 22:00';
    }
    if (key === 'location') {
      return '例如：北门菜鸟驿站';
    }
    if (key === 'deliveryLocation') {
      return '例如：5 号楼一层货架';
    }
    if (key === 'rewardAmount') {
      return '例如：8';
    }
    if (key === 'itemCount') {
      return '例如：3';
    }
    if (key === 'completionStandard') {
      return '怎样才算完成？写具体一点';
    }
    if (key === 'title') {
      return '用一句话概括这个任务';
    }
    return '在这里填写';
  }

  private setFormValue(key: string, value: string): void {
    const index = this.formIndex(key);
    if (index >= 0) {
      this.formValues[index] = value;
    }
  }

  private remember(key: string, value: string): void {
    for (let i = 0; i < this.knownKeys.length; i++) {
      if (this.knownKeys[i] === key) {
        this.knownValues[i] = value;
        return;
      }
    }
    this.knownKeys.push(key);
    this.knownValues.push(value);
  }

  private recall(key: string): string {
    for (let i = 0; i < this.knownKeys.length; i++) {
      if (this.knownKeys[i] === key) {
        return this.knownValues[i];
      }
    }
    return '';
  }

  private firstOptionId(card: UiCard): string {
    for (let i = 0; i < card.blocks.length; i++) {
      const block = card.blocks[i];
      if (block.type === 'Choice' && block.options !== undefined && block.options.length > 0) {
        if (block.selectedId !== undefined) {
          return block.selectedId;
        }
        return block.options[0].id;
      }
    }
    return '';
  }

  private kindLabel(): string {
    const kind = this.card.kind;
    if (kind === 'clarification') {
      return '澄清';
    }
    if (kind === 'comparison') {
      return '比较';
    }
    if (kind === 'confirmation') {
      return '确认';
    }
    return '进度';
  }

  private sourceLabel(source: string): string {
    if (source === 'INFERRED') {
      return '系统推测';
    }
    if (source === 'DEFAULT') {
      return '默认';
    }
    return '用户说的';
  }

  private progressColor(state: string): string {
    if (state === 'done') {
      return '#2F9E44';
    }
    if (state === 'active') {
      return '#2F6FDE';
    }
    if (state === 'failed') {
      return '#D9480F';
    }
    return '#C7CED8';
  }

  private clearSession(): void {
    this.responseCount = 0;
    this.hasCard = false;
    this.showAssumptions = false;
    this.card = emptyCard();
    this.warnings = [];
    this.violations = [];
    this.notesText = '';
    this.assumptionsText = '';
    this.sessionId = '';
    this.sessionState = '';
    this.stepIndex = -1;
    this.taskId = -1;
    this.streamText = '';
    this.selectedOptionId = '';
    this.idempotencyKey = '';
    this.errorText = '';
    this.formFields = [];
    this.formLabels = [];
    this.formHints = [];
    this.formKinds = [];
    this.formRequired = [];
    this.formValues = [];
    this.formChoices = [];
    this.formChoiceLabels = [];
  }

  private resetToIdle(): void {
    this.clearSession();
    this.knownKeys = [];
    this.knownValues = [];
    this.stage = 'idle';
    this.statusText = '';
  }

  /** 换了一张卡就回到顶部：否则用户停在上一次的滚动位置，会以为「点了没反应」 */
  private scrollTop(): void {
    this.scroller.scrollEdge(Edge.Top);
  }

  // ---- 界面 ---------------------------------------------------------------

  @Builder
  header() {
    Row() {
      Button('‹ 返回')
        .fontSize(13)
        .height(32)
        .backgroundColor('#FFFFFF')
        .fontColor('#34495E')
        .borderRadius(16)
        .onClick(() => {
          router.back();
        })
      Column() {
        Text('助手 · 真实链路')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
        Text('一次只出一张卡，你点了才往下走')
          .fontSize(11)
          .fontColor('#7A8BA6')
          .margin({ top: 2 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: 10 })
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 12, bottom: 8 })
  }

  @Builder
  stepper() {
    Row() {
      ForEach(this.stepLabels, (label: string, index: number) => {
        Row() {
          Text(this.stepIndex > index ? '✓' : String(index + 1))
            .fontSize(11)
            .fontColor(this.stepIndex >= index ? '#FFFFFF' : '#7A8BA6')
            .backgroundColor(this.stepIndex >= index ? '#2F6FDE' : '#E3E8F0')
            .width(20)
            .height(20)
            .textAlign(TextAlign.Center)
            .borderRadius(10)
          Text(label)
            .fontSize(11)
            .fontColor(this.stepIndex === index ? '#2F6FDE' : '#8A9BB3')
            .margin({ left: 4 })
        }
        .margin({ right: 8 })
      }, (label: string, index: number) => label + String(index))
    }
    .width('100%')
    .margin({ top: 2, bottom: 10 })
  }

  @Builder
  goalEditor() {
    Column() {
      Row() {
        Text('一句话需求')
          .fontSize(12)
          .fontColor('#7A8BA6')
          .layoutWeight(1)
      }
      .width('100%')

      TextArea({ text: this.goal })
        .fontSize(14)
        .height(84)
        .margin({ top: 6 })
        .onChange((value: string) => {
          this.goal = value;
        })

      Row() {
        Text('后端')
          .fontSize(11)
          .fontColor('#7A8BA6')
          .width(32)
        TextInput({ text: this.baseUrl })
          .fontSize(12)
          .height(36)
          .layoutWeight(1)
          .margin({ left: 4 })
          .onChange((value: string) => {
            this.baseUrl = value;
          })
      }
      .width('100%')
      .margin({ top: 8 })

      Button(this.busy ? '正在理解…' : '一句话开始')
        .width('100%')
        .height(44)
        .margin({ top: 12 })
        .backgroundColor(this.busy ? '#9AB2DE' : '#2F6FDE')
        .fontColor('#FFFFFF')
        .borderRadius(22)
        .enabled(!this.busy)
        .onClick(() => {
          this.handleStart();
        })

      Text('点一次只走到第一张卡：信息不全先追问，信息够才给方案。')
        .fontSize(11)
        .fontColor('#A6B4C8')
        .width('100%')
        .margin({ top: 8 })
    }
    .padding(14)
    .backgroundColor('#FFFFFF')
    .borderRadius(14)
    .width('100%')
  }

  @Builder
  goalSummary() {
    Column() {
      Row() {
        Text('你的需求')
          .fontSize(11)
          .fontColor('#7A8BA6')
          .layoutWeight(1)
        Text(this.sessionId.length > 0 ? '会话 ' + this.sessionId : '新建会话')
          .fontSize(10)
          .fontColor('#A6B4C8')
      }
      .width('100%')

      Text(this.goal)
        .fontSize(13)
        .fontColor('#34495E')
        .width('100%')
        .maxLines(3)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .margin({ top: 4 })

      Row() {
        Text(this.sessionState.length > 0 ? '状态 ' + this.sessionState : '')
          .fontSize(10)
          .fontColor('#A6B4C8')
          .layoutWeight(1)
        Button('重新开始')
          .fontSize(11)
          .height(28)
          .backgroundColor('#EEF1F6')
          .fontColor('#34495E')
          .borderRadius(14)
          .enabled(!this.busy)
          .onClick(() => {
            this.resetToIdle();
          })
      }
      .width('100%')
      .margin({ top: 8 })
    }
    .padding(12)
    .backgroundColor('#FFFFFF')
    .borderRadius(12)
    .width('100%')
  }

  @Builder
  statusArea() {
    Column() {
      if (this.busy) {
        Row() {
          LoadingProgress()
            .width(16)
            .height(16)
            .color('#2F6FDE')
          Text('处理中…按钮先禁用，避免重复提交')
            .fontSize(11)
            .fontColor('#2F6FDE')
            .margin({ left: 6 })
        }
        .width('100%')
        .margin({ top: 8 })
      }
      if (this.statusText.length > 0) {
        Text(this.statusText)
          .fontSize(12)
          .fontColor('#34495E')
          .width('100%')
          .margin({ top: 8 })
      }
      if (this.errorText.length > 0) {
        Text('⚠ ' + this.errorText)
          .fontSize(12)
          .fontColor('#D9480F')
          .width('100%')
          .margin({ top: 6 })
          .padding(10)
          .backgroundColor('#FFF0EB')
          .borderRadius(8)
      }
    }
    .width('100%')
  }

  @Builder
  formFieldView(key: string) {
    Column() {
      Row() {
        Text(this.formLabelOf(key))
          .fontSize(13)
          .fontColor('#1F2D3D')
          .layoutWeight(1)
        if (this.formRequiredOf(key)) {
          Text('必填')
            .fontSize(10)
            .fontColor('#D9480F')
        }
      }
      .width('100%')

      if (this.formHintOf(key).length > 0) {
        Text(this.formHintOf(key))
          .fontSize(10)
          .fontColor('#8A9BB3')
          .width('100%')
          .margin({ top: 2 })
      }

      if (this.formKindOf(key) === 'enum' || this.formKindOf(key) === 'bool') {
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(this.formChoicesOf(key), (option: string, optionIndex: number) => {
            Text(this.formChoiceLabelAt(key, optionIndex))
              .fontSize(12)
              .fontColor(this.formValueOf(key) === option ? '#FFFFFF' : '#34495E')
              .backgroundColor(this.formValueOf(key) === option ? '#2F6FDE' : '#EEF1F6')
              .borderRadius(14)
              .padding({ left: 12, right: 12, top: 6, bottom: 6 })
              .margin({ right: 8, top: 8 })
              .onClick(() => {
                if (!this.busy) {
                  this.setFormValue(key, option);
                  this.errorText = '';
                }
              })
          }, (option: string, optionIndex: number) => key + '_' + option + String(optionIndex))
        }
        .width('100%')
      } else {
        TextInput({ text: this.formValueOf(key), placeholder: this.formPlaceholderOf(key) })
          .fontSize(13)
          .height(40)
          .margin({ top: 8 })
          .type(this.formKindOf(key) === 'number' ? InputType.Number : InputType.Normal)
          .onChange((value: string) => {
            this.setFormValue(key, value);
          })
      }
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .margin({ top: 10 })
    .padding(10)
    .backgroundColor('#F7F9FC')
    .borderRadius(10)
  }

  @Builder
  choiceView(block: CardBlock) {
    Column() {
      ForEach(block.options !== undefined ? block.options : [], (option: ChoiceOption) => {
        Row() {
          Text(this.selectedOptionId === option.id ? '●' : '○')
            .fontSize(15)
            .fontColor(this.selectedOptionId === option.id ? '#2F6FDE' : '#C7CED8')
            .width(22)
          Column() {
            Text(option.label)
              .fontSize(14)
              .fontColor('#1F2D3D')
            if (option.detail !== undefined) {
              Text(option.detail)
                .fontSize(11)
                .fontColor('#7A8BA6')
                .margin({ top: 2 })
            }
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
        }
        .width('100%')
        .padding(10)
        .margin({ top: 8 })
        .borderRadius(10)
        .backgroundColor(this.selectedOptionId === option.id ? '#EAF1FE' : '#F7F9FC')
        .borderWidth(this.selectedOptionId === option.id ? 1 : 0)
        .borderColor('#2F6FDE')
        .onClick(() => {
          if (!this.busy) {
            this.selectedOptionId = option.id;
            this.errorText = '';
          }
        })
      }, (option: ChoiceOption) => this.card.cardId + '_' + option.id)
    }
    .width('100%')
    .margin({ top: 4 })
  }

  @Builder
  summaryView(block: CardBlock) {
    Column() {
      ForEach(block.rows !== undefined ? block.rows : [], (row: SummaryRow, rowIndex: number) => {
        Row() {
          Text(row.label)
            .fontSize(12)
            .fontColor('#7A8BA6')
            .width(84)
          Text(row.value)
            .fontSize(13)
            .fontColor('#1F2D3D')
            .layoutWeight(1)
          Text(this.sourceLabel(row.source))
            .fontSize(10)
            .fontColor(row.source === 'USER' ? '#2F9E44' : '#B08900')
        }
        .width('100%')
        .margin({ top: 6 })
      }, (row: SummaryRow, rowIndex: number) => row.label + '|' + row.value + String(rowIndex))
    }
    .width('100%')
    .margin({ top: 6 })
  }

  @Builder
  blockView(block: CardBlock) {
    if (block.type === 'Text') {
      Text(block.text !== undefined ? block.text : '')
        .fontSize(13)
        .fontColor('#2C3E50')
        .width('100%')
        .margin({ top: 10 })
    } else if (block.type === 'Field') {
      if (this.stage === 'clarify' && block.field !== undefined) {
        this.formFieldView(String(block.field))
      } else {
        Row() {
          Text(block.label !== undefined ? block.label : '')
            .fontSize(13)
            .fontColor('#7A8BA6')
            .width(96)
          Text(block.value !== undefined && block.value.length > 0 ? block.value : '—')
            .fontSize(14)
            .fontColor('#1F2D3D')
            .layoutWeight(1)
        }
        .width('100%')
        .margin({ top: 8 })
      }
    } else if (block.type === 'Choice') {
      this.choiceView(block)
    } else if (block.type === 'Summary') {
      this.summaryView(block)
    } else {
      Row() {
        Circle({ width: 8, height: 8 })
          .fill(this.progressColor(block.state !== undefined ? block.state : 'pending'))
        Text(block.step !== undefined ? block.step : '')
          .fontSize(13)
          .fontColor('#1F2D3D')
          .margin({ left: 8 })
        Text(block.detail !== undefined ? block.detail : '')
          .fontSize(12)
          .fontColor('#7A8BA6')
          .margin({ left: 8 })
          .layoutWeight(1)
      }
      .width('100%')
      .margin({ top: 8 })
    }
  }

  @Builder
  actionRow() {
    if (this.card.actions.length > 0) {
      Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.End }) {
        ForEach(this.card.actions, (action: CardAction) => {
          Button(action.label)
            .fontSize(13)
            .height(38)
            .backgroundColor(action.kind === 'primary'
              ? (isSideEffectAction(action.actionId) ? '#D9480F' : '#2F6FDE')
              : '#EEF1F6')
            .fontColor(action.kind === 'primary' ? '#FFFFFF' : '#34495E')
            .borderRadius(19)
            .margin({ left: 8, top: 8 })
            .enabled(!this.busy)
            .onClick(() => {
              this.onAction(action.actionId);
            })
        }, (action: CardAction) => this.card.cardId + '_' + action.actionId)
      }
      .width('100%')
      .margin({ top: 12 })
    }
  }

  @Builder
  editPanel() {
    Column() {
      Text('要改哪一处？改完会重新走「比较 → 确认」，旧确认令牌同时失效。')
        .fontSize(12)
        .fontColor('#34495E')
        .width('100%')
        .margin({ top: 12 })

      ForEach(this.formFields, (key: string, index: number) => {
        this.formFieldView(key)
      }, (key: string, index: number) => 'edit_' + key + String(index))

      Row() {
        Button('取消修改')
          .fontSize(13)
          .height(38)
          .backgroundColor('#EEF1F6')
          .fontColor('#34495E')
          .borderRadius(19)
          .enabled(!this.busy)
          .onClick(() => {
            this.cancelEdit();
          })
        Button(this.busy ? '提交中…' : '保存修改')
          .fontSize(13)
          .height(38)
          .backgroundColor('#2F6FDE')
          .fontColor('#FFFFFF')
          .borderRadius(19)
          .margin({ left: 8 })
          .enabled(!this.busy)
          .onClick(() => {
            this.submitForm();
          })
      }
      .width('100%')
      .justifyContent(FlexAlign.End)
      .margin({ top: 12 })
    }
    .width('100%')
  }

  @Builder
  activeCard() {
    Column() {
      Row() {
        Text(this.card.title)
          .fontSize(17)
          .fontWeight(FontWeight.Medium)
          .fontColor('#101A2B')
          .layoutWeight(1)
        Text(this.kindLabel())
          .fontSize(11)
          .fontColor('#3B6FD4')
          .backgroundColor('#EAF1FE')
          .borderRadius(4)
          .padding({ left: 6, right: 6, top: 2, bottom: 2 })
      }
      .width('100%')

      ForEach(this.card.blocks, (block: CardBlock, blockIndex: number) => {
        this.blockView(block)
      }, (block: CardBlock, blockIndex: number) => this.card.cardId + '_' + this.stage
        + '_' + String(blockIndex))

      if (this.warnings.length > 0) {
        Column() {
          ForEach(this.warnings, (line: string, index: number) => {
            Text('· ' + line)
              .fontSize(11)
              .fontColor('#8A6D00')
              .width('100%')
          }, (line: string, index: number) => 'warn_' + String(index))
        }
        .width('100%')
        .padding(10)
        .margin({ top: 10 })
        .backgroundColor('#FFF9E6')
        .borderRadius(8)
      }

      if (this.violations.length > 0) {
        Column() {
          ForEach(this.violations, (line: string, index: number) => {
            Text('· ' + line)
              .fontSize(11)
              .fontColor('#D9480F')
              .width('100%')
          }, (line: string, index: number) => 'violation_' + String(index))
        }
        .width('100%')
        .padding(10)
        .margin({ top: 10 })
        .backgroundColor('#FFF0EB')
        .borderRadius(8)
      }

      if (this.stage === 'edit') {
        this.editPanel()
      } else {
        this.actionRow()
      }
    }
    .width('100%')
    .padding(16)
    .margin({ top: 12 })
    .backgroundColor('#FFFFFF')
    .borderRadius(14)
    .shadow({ radius: 10, color: '#14000000', offsetX: 0, offsetY: 2 })
  }

  @Builder
  detailPanel() {
    Column() {
      if (this.showAssumptions) {
        Text('系统初次理解的假设（可能已被你的回答覆盖）：' + this.assumptionsText)
          .fontSize(10)
          .fontColor('#8A9BB3')
          .width('100%')
      }
      if (this.notesText.length > 0) {
        Text('系统注记：' + this.notesText)
          .fontSize(10)
          .fontColor('#2F9E44')
          .width('100%')
          .margin({ top: 4 })
      }
    }
    .width('100%')
    .padding(10)
    .margin({ top: 12 })
    .backgroundColor('#FFFFFF')
    .borderRadius(10)
  }

  build() {
    Column() {
      this.header()

      Scroll(this.scroller) {
        Column() {
          this.stepper()

          if (this.stage === 'idle') {
            this.goalEditor()
          } else {
            this.goalSummary()
          }

          this.statusArea()

          if (this.hasCard) {
            this.activeCard()
          }

          if (this.showAssumptions || this.notesText.length > 0) {
            this.detailPanel()
          }

          if (this.streamText.length > 0) {
            Text(this.streamText)
              .fontSize(10)
              .fontColor('#A6B4C8')
              .width('100%')
              .margin({ top: 12 })
          }
        }
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 32 })
      }
      .layoutWeight(1)
      .width('100%')
      .align(Alignment.Top)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F2F5FA')
  }
}
