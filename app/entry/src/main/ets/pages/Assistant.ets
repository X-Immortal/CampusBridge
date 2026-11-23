import { http } from '@kit.NetworkKit';
import { router } from '@kit.ArkUI';
import type {
  AnswerInput,
  AssistantResponse,
  ClarificationQuestion,
  UiCard
} from '../protocol/types';
import { CardRenderer } from '../renderer/CardRenderer';
import { SseFrameParser, StreamAssembler } from '../transport/sseParser';
import { StreamEvent } from '../transport/events';
import { cardProblems } from '../ui/cardGuard';

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

function demoValue(field: string): Object {
  if (field === 'deadline') {
    return '2026-11-16T22:00:00';
  }
  if (field === 'location') {
    return '三号宿舍楼';
  }
  if (field === 'deliveryLocation') {
    return '三号宿舍楼东门';
  }
  if (field === 'rewardType') {
    return 'CASH';
  }
  if (field === 'rewardAmount') {
    return 8;
  }
  if (field === 'itemCount') {
    return 3;
  }
  if (field === 'timeWindow') {
    return '今晚 20:00-22:00';
  }
  if (field === 'riskDisclosureConfirmed' || field === 'requiresProof'
    || field === 'onlyInAppContact') {
    return true;
  }
  if (field === 'shareDormRoom' || field === 'allowOfflineContact') {
    return false;
  }
  if (field === 'completionStandard') {
    return '送到三号宿舍楼东门并当面交付';
  }
  if (field === 'contactPreference') {
    return '站内消息';
  }
  if (field === 'title') {
    return '帮取三个快递';
  }
  return '都可以';
}

/**
 * 真实链路演示页：POST /api/assistant 走完四步，再用 GET /api/stream 回放五种事件。
 * 只依赖两件事：本机后端 8081（模拟器用 scripts/emulator-net.ps1 做 rport），
 * 以及 transport/sseParser.ts 的解析器（与 node --test 是同一份代码）。
 */
@Entry
@Component
struct Assistant {
  @State baseUrl: string = DEFAULT_BASE;
  @State goal: string = DEMO_GOAL;
  @State status: string = '点「一句话开始」走完整链路';
  @State cards: UiCard[] = [];
  @State bubbles: string[] = [];
  @State streamSummary: string = '';
  @State busy: boolean = false;

  build() {
    Column() {
      Row() {
        Button('‹ 四卡演示')
          .fontSize(13)
          .height(32)
          .backgroundColor('#FFFFFF')
          .fontColor('#34495E')
          .borderRadius(16)
          .onClick(() => {
            router.back();
          })
        Text('真实链路')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
          .margin({ left: 12 })
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 8 })

      Scroll() {
        Column() {
          Text('后端地址')
            .fontSize(12)
            .fontColor('#7A8BA6')
          TextInput({ text: this.baseUrl })
            .fontSize(13)
            .height(40)
            .onChange((value: string) => {
              this.baseUrl = value;
            })
            .margin({ top: 6 })

          Text('一句话需求')
            .fontSize(12)
            .fontColor('#7A8BA6')
            .margin({ top: 12 })
          TextArea({ text: this.goal })
            .fontSize(14)
            .height(84)
            .onChange((value: string) => {
              this.goal = value;
            })
            .margin({ top: 6 })

          Button(this.busy ? '执行中…' : '一句话开始（追问 → 比较 → 确认 → 建单）')
            .height(42)
            .width('100%')
            .margin({ top: 14 })
            .backgroundColor('#2F6FDE')
            .fontColor('#FFFFFF')
            .enabled(!this.busy)
            .onClick(() => {
              this.runFlow();
            })

          Text(this.status)
            .fontSize(12)
            .fontColor('#34495E')
            .width('100%')
            .margin({ top: 12 })

          if (this.streamSummary.length > 0) {
            Text(this.streamSummary)
              .fontSize(12)
              .fontColor('#2F9E44')
              .width('100%')
              .margin({ top: 8 })
          }

          ForEach(this.bubbles, (line: string, index: number) => {
            Text(line)
              .fontSize(13)
              .fontColor('#34495E')
              .width('100%')
              .padding(10)
              .backgroundColor('#FFFFFF')
              .borderRadius(8)
              .margin({ top: 8 })
          }, (line: string, index: number) => String(index) + '_' + line)

          ForEach(this.cards, (card: UiCard, index: number) => {
            CardRenderer({
              card: card,
              onAction: (actionId: string) => {
                this.status = '点了动作：' + actionId + '（演示页只自动走主链路）';
              }
            })
              .margin({ top: 12 })
          }, (card: UiCard, index: number) => card.cardId + '_' + String(index))
        }
        .padding({ left: 16, right: 16, bottom: 24 })
        .width('100%')
      }
      .layoutWeight(1)
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F2F5FA')
  }

  private async postJson(path: string, body: Object): Promise<AssistantResponse> {
    const request = http.createHttp();
    try {
      const response = await request.request(this.baseUrl + path, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: JSON.stringify(body),
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 5000,
        readTimeout: 60000
      });
      return JSON.parse(String(response.result)) as AssistantResponse;
    } finally {
      request.destroy();
    }
  }

  private pushCard(response: AssistantResponse): void {
    if (response.card !== undefined && response.card !== null) {
      const problems = cardProblems(response.card);
      if (problems.length > 0) {
        // 坏卡整张拒绝，保留上一张；绝不做“只删一个按钮”的假安全。
        this.status = '坏卡已拒绝：' + problems.join('；');
        return;
      }
      this.cards = this.cards.concat([response.card]);
    }
    if (response.notes !== undefined && response.notes !== null) {
      for (let i = 0; i < response.notes.length; i++) {
        this.bubbles = this.bubbles.concat(['系统注记：' + response.notes[i]]);
      }
    }
  }

  private async runFlow(): Promise<void> {
    this.busy = true;
    this.cards = [];
    this.bubbles = [];
    this.streamSummary = '';
    this.status = '正在理解这句话…';
    try {
      const startBody: StartBody = {
        goal: this.goal,
        context: { requesterId: 1 }
      };
      let current = await this.postJson('/api/assistant', startBody);
      this.pushCard(current);
      this.status = '会话 ' + current.sessionId + ' · ' + current.state
        + ' · 卡片 ' + (current.card !== undefined ? current.card.kind : '无');

      let round = 0;
      while (current.card !== undefined && current.card.kind === 'clarification' && round < 4) {
        const unknowns: ClarificationQuestion[] = current.unknowns !== undefined ? current.unknowns : [];
        if (unknowns.length === 0) {
          break;
        }
        const answers: AnswerInput[] = [];
        for (let i = 0; i < unknowns.length; i++) {
          const answer: AnswerInput = {
            field: unknowns[i].field,
            value: demoValue(unknowns[i].field)
          };
          answers.push(answer);
        }
        const answersBody: AnswersBody = { answers: answers };
        current = await this.postJson(
          '/api/assistant/sessions/' + current.sessionId + '/answers', answersBody);
        this.pushCard(current);
        this.status = '已补全 ' + answers.length + ' 项，当前 ' + current.state;
        round += 1;
      }

      let guard = 0;
      while (current.card !== undefined && current.card.kind === 'comparison' && guard < 2) {
        let optionId = '';
        const blocks = current.card.blocks;
        for (let i = 0; i < blocks.length; i++) {
          const options = blocks[i].options;
          if (blocks[i].type === 'Choice' && options !== undefined && options.length > 0) {
            optionId = options[0].id;
            break;
          }
        }
        if (optionId.length === 0) {
          break;
        }
        const selectBody: SelectBody = { optionId: optionId };
        current = await this.postJson(
          '/api/assistant/sessions/' + current.sessionId + '/select', selectBody);
        this.pushCard(current);
        this.status = '已选方案 ' + optionId + '，等待确认';
        guard += 1;
      }

      if (current.card !== undefined && current.card.kind === 'confirmation'
        && current.card.token !== undefined) {
        const idempotencyKey = 'arkui-' + String(Date.now());
        const confirmBody: ConfirmBody = {
          token: current.card.token.value,
          idempotencyKey: idempotencyKey
        };
        current = await this.postJson(
          '/api/assistant/sessions/' + current.sessionId + '/confirm', confirmBody);
        this.pushCard(current);
        this.status = '确认结果：' + String(current.outcome) + ' · taskId=' + String(current.taskId);
      }

      await this.loadStream(current.sessionId);
    } catch (error) {
      this.status = '失败：' + String(error);
    } finally {
      this.busy = false;
    }
  }

  private async loadStream(sessionId: string): Promise<void> {
    const request = http.createHttp();
    try {
      const url = this.baseUrl + '/api/stream?sessionId=' + encodeURIComponent(sessionId) + '&after=0';
      const response = await request.request(url, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 5000,
        readTimeout: 20000
      });
      const wire = String(response.result);
      const frames = new SseFrameParser().feed(wire);
      const assembler = new StreamAssembler(sessionId);
      const kinds: string[] = [];
      for (let i = 0; i < frames.length; i++) {
        const result = assembler.acceptFrame(frames[i]);
        if (result.status === 'accepted' && result.event !== undefined) {
          const event: StreamEvent = result.event;
          kinds.push(event.kind);
          if (event.kind === 'text.delta' && event.payload.text !== undefined) {
            this.bubbles = this.bubbles.concat([event.payload.text]);
          }
        }
      }
      this.streamSummary = '流事件 ' + String(kinds.length) + ' 条：' + kinds.join(' / ');
    } finally {
      request.destroy();
    }
  }
}
