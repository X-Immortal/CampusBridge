import { CardAction, CardBlock, CardKind, ChoiceOption, ProgressState, SummaryRow, UiCard } from '../protocol/types';
import { isSideEffectAction } from '../protocol/whitelist';

function kindLabel(kind: CardKind): string {
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

function sourceLabel(source: string): string {
  if (source === 'INFERRED') {
    return '系统推测';
  }
  if (source === 'DEFAULT') {
    return '默认';
  }
  return '用户说的';
}

function progressColor(state: ProgressState | undefined): string {
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

export function emptyCard(): UiCard {
  return {
    schemaVersion: '1.0',
    cardId: '',
    sessionId: '',
    taskVersion: 0,
    kind: 'progress',
    title: '',
    blocks: [],
    actions: []
  };
}

/**
 * 声明式卡片渲染器：只认白名单组件，不执行任何模型产出的代码。
 * 完整校验在 protocol/validator.ts（张琼文）；这里默认传进来的卡已经过校验。
 */
@Component
export struct CardRenderer {
  card: UiCard = emptyCard();
  onAction: (actionId: string) => void = (actionId: string) => {};

  @Builder
  blockView(block: CardBlock) {
    if (block.type === 'Text') {
      Text(block.text !== undefined ? block.text : '')
        .fontSize(14)
        .fontColor('#2C3E50')
        .width('100%')
        .margin({ top: 10 })
    } else if (block.type === 'Field') {
      Row() {
        Text(block.label !== undefined ? block.label : '')
          .fontSize(13)
          .fontColor('#7A8BA6')
          .width(92)
        Text(block.value !== undefined ? block.value : '—')
          .fontSize(14)
          .fontColor('#1F2D3D')
          .layoutWeight(1)
        if (block.required === true) {
          Text('必填').fontSize(10).fontColor('#D9480F').margin({ left: 6 })
        }
      }
      .width('100%')
      .margin({ top: 8 })
    } else if (block.type === 'Choice') {
      Column() {
        ForEach(block.options !== undefined ? block.options : [], (option: ChoiceOption) => {
          Row() {
            Radio({ value: option.id, group: block.name !== undefined ? block.name : 'choice' })
              .checked(block.selectedId === option.id)
            Column() {
              Text(option.label).fontSize(14).fontColor('#1F2D3D')
              if (option.detail !== undefined) {
                Text(option.detail).fontSize(12).fontColor('#7A8BA6')
              }
            }
            .alignItems(HorizontalAlign.Start)
            .margin({ left: 4 })
          }
          .width('100%')
          .margin({ top: 6 })
        }, (option: ChoiceOption) => option.id)
      }
      .width('100%')
      .margin({ top: 4 })
    } else if (block.type === 'Summary') {
      Column() {
        ForEach(block.rows !== undefined ? block.rows : [], (row: SummaryRow) => {
          Row() {
            Text(row.label).fontSize(13).fontColor('#7A8BA6').width(76)
            Text(row.value).fontSize(14).fontColor('#1F2D3D').layoutWeight(1)
            Text(sourceLabel(row.source))
              .fontSize(10)
              .fontColor(row.source === 'USER' ? '#2F9E44' : '#B08900')
          }
          .width('100%')
          .margin({ top: 6 })
        }, (row: SummaryRow) => row.label + '|' + row.value)
      }
      .width('100%')
    } else {
      Row() {
        Circle({ width: 8, height: 8 }).fill(progressColor(block.state))
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

  build() {
    Column() {
      Row() {
        Text(this.card.title)
          .fontSize(17)
          .fontWeight(FontWeight.Medium)
          .fontColor('#101A2B')
          .layoutWeight(1)
        Text(kindLabel(this.card.kind))
          .fontSize(11)
          .fontColor('#3B6FD4')
          .backgroundColor('#EAF1FE')
          .borderRadius(4)
          .padding({ left: 6, right: 6, top: 2, bottom: 2 })
      }
      .width('100%')

      ForEach(this.card.blocks, (block: CardBlock, index: number) => {
        this.blockView(block)
      }, (block: CardBlock, index: number) => block.type + '_' + String(index))

      if (this.card.actions.length > 0) {
        Row() {
          ForEach(this.card.actions, (action: CardAction) => {
            Button(action.label)
              .fontSize(14)
              .height(36)
              .backgroundColor(action.kind === 'primary'
                ? (isSideEffectAction(action.actionId) ? '#D9480F' : '#2F6FDE')
                : '#EEF1F6')
              .fontColor(action.kind === 'primary' ? '#FFFFFF' : '#34495E')
              .margin({ left: 8 })
              .onClick(() => {
                this.onAction(action.actionId);
              })
          }, (action: CardAction) => action.actionId)
        }
        .width('100%')
        .justifyContent(FlexAlign.End)
        .margin({ top: 14 })
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#FFFFFF')
    .borderRadius(14)
    .shadow({ radius: 10, color: '#14000000', offsetX: 0, offsetY: 2 })
  }
}
