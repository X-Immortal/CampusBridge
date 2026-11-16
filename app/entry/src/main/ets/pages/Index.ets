import { UiCard } from '../protocol/types';
import { isAllowedBlock, isKnownDraftField, isSideEffectAction } from '../protocol/whitelist';
import { EVENT_KINDS } from '../transport/events';
import { CardRenderer } from '../renderer/CardRenderer';
import {
  BAD_CARD_JSON,
  CARD_CLARIFICATION_JSON,
  CARD_COMPARISON_JSON,
  CARD_CONFIRMATION_JSON,
  CARD_PROGRESS_JSON
} from '../fixtures/Fixtures';

function parseCard(json: string): UiCard {
  return JSON.parse(json) as UiCard;
}

/** 最小守卫：演示"非白名单 / 未知字段 / 缺确认"三种拒绝。完整校验器在 protocol/validator.ts（张琼文）。 */
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

@Entry
@Component
struct Index {
  @State tab: number = 0;
  @State lastAction: string = '（还没点）';

  private cards: UiCard[] = [
    parseCard(CARD_CLARIFICATION_JSON),
    parseCard(CARD_COMPARISON_JSON),
    parseCard(CARD_CONFIRMATION_JSON),
    parseCard(CARD_PROGRESS_JSON)
  ];
  private badCard: UiCard = parseCard(BAD_CARD_JSON);
  private badProblems: string[] = cardProblems(parseCard(BAD_CARD_JSON));
  private tabs: string[] = ['澄清', '比较', '确认', '进度', '坏卡'];

  @Builder
  tabButton(label: string, index: number) {
    Button(label)
      .fontSize(13)
      .height(32)
      .backgroundColor(this.tab === index ? '#2F6FDE' : '#FFFFFF')
      .fontColor(this.tab === index ? '#FFFFFF' : '#34495E')
      .borderRadius(16)
      .margin({ right: 8 })
      .onClick(() => {
        this.tab = index;
      })
  }

  build() {
    Column() {
      Column() {
        Text('CampusBridge 2.0')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
        Text('契约渲染演示 · 四类卡片 + 白名单拦截 · ' + EVENT_KINDS.length + ' 种流事件')
          .fontSize(12)
          .fontColor('#7A8BA6')
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)
      .width('100%')
      .padding({ left: 16, right: 16, top: 16, bottom: 12 })

      Scroll() {
        Row() {
          ForEach(this.tabs, (label: string, index: number) => {
            this.tabButton(label, index)
          }, (label: string, index: number) => label)
        }
        .padding({ left: 16, right: 16 })
      }
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
      .width('100%')

      Scroll() {
        Column() {
          if (this.tab < this.cards.length) {
            CardRenderer({
              card: this.cards[this.tab],
              onAction: (actionId: string) => {
                this.lastAction = actionId;
              }
            })
            Text('校验：通过（Schema 1.0，全部组件在白名单内）')
              .fontSize(12)
              .fontColor('#2F9E44')
              .margin({ top: 10 })
          } else {
            CardRenderer({
              card: this.badCard,
              onAction: (actionId: string) => {
                this.lastAction = actionId;
              }
            })
            Column() {
              Text('校验：拒绝（这张卡不该被渲染）')
                .fontSize(12)
                .fontColor('#D9480F')
              ForEach(this.badProblems, (problem: string) => {
                Text('· ' + problem)
                  .fontSize(12)
                  .fontColor('#D9480F')
                  .margin({ top: 4 })
              }, (problem: string) => problem)
            }
            .alignItems(HorizontalAlign.Start)
            .width('100%')
            .margin({ top: 10 })
          }

          Text('最后点击的动作：' + this.lastAction)
            .fontSize(12)
            .fontColor('#7A8BA6')
            .margin({ top: 16 })
        }
        .padding(16)
        .width('100%')
      }
      .layoutWeight(1)
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F2F5FA')
  }
}
