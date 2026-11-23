import { DEFAULT_BASE, fetchTaskDetail, rewardText, statusText, TaskItem, textOf } from '../ui/taskApi';

const EMPTY_TASK: TaskItem = { id: 0, title: '' };

/**
 * 任务详情（L2 固定页面）。宽屏时与列表并排，窄屏时由 TaskDetail 页面承载。
 */
@Component
export struct TaskDetailPanel {
  @Prop @Watch('onTaskIdChanged') taskId: number = 0;

  @State task: TaskItem = EMPTY_TASK;
  @State loaded: boolean = false;
  @State loading: boolean = false;
  @State error: string = '';
  private base: string = DEFAULT_BASE;

  aboutToAppear(): void {
    this.load();
  }

  onTaskIdChanged(): void {
    this.load();
  }

  private async load(): Promise<void> {
    if (this.taskId <= 0) {
      return;
    }
    this.loading = true;
    this.error = '';
    try {
      this.task = await fetchTaskDetail(this.base, this.taskId);
      this.loaded = true;
    } catch (error) {
      this.error = '任务详情加载失败：' + String(error);
    } finally {
      this.loading = false;
    }
  }

  @Builder
  fieldRow(label: string, value: string | null | undefined) {
    Row() {
      Text(label)
        .fontSize(12)
        .fontColor('#7A8BA6')
        .width(88)
      Text(textOf(value).length === 0 ? '—' : textOf(value))
        .fontSize(13)
        .fontColor('#1F2D3D')
        .layoutWeight(1)
    }
    .width('100%')
    .alignItems(VerticalAlign.Top)
    .margin({ top: 10 })
  }

  build() {
    Column() {
      if (this.taskId <= 0) {
        Column() {
          Text('从左侧选一条任务')
            .fontSize(13)
            .fontColor('#7A8BA6')
          Text('平板宽屏会在这里显示详情；手机点任务进入详情页')
            .fontSize(11)
            .fontColor('#A6B4C8')
            .margin({ top: 6 })
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.loading && !this.loaded) {
        Column() {
          LoadingProgress().width(30).height(30)
          Text('正在加载任务详情…')
            .fontSize(12)
            .fontColor('#7A8BA6')
            .margin({ top: 8 })
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.error.length > 0 && !this.loaded) {
        Column() {
          Text(this.error)
            .fontSize(12)
            .fontColor('#D9480F')
          Button('重试')
            .fontSize(12)
            .height(30)
            .margin({ top: 10 })
            .onClick(() => {
              this.load();
            })
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else {
        Scroll() {
          Column() {
            Row() {
              Text(statusText(this.task.status))
                .fontSize(11)
                .fontColor('#FFFFFF')
                .padding({ left: 8, right: 8, top: 3, bottom: 3 })
                .backgroundColor('#2F6FDE')
                .borderRadius(10)
              Text(textOf(this.task.category))
                .fontSize(11)
                .fontColor('#7A8BA6')
                .margin({ left: 8 })
            }
            .width('100%')

            Text(this.task.title)
              .fontSize(20)
              .fontWeight(FontWeight.Bold)
              .fontColor('#101A2B')
              .width('100%')
              .margin({ top: 10 })

            Text(textOf(this.task.description).length === 0
              ? '（无描述）' : textOf(this.task.description))
              .fontSize(13)
              .fontColor('#34495E')
              .width('100%')
              .margin({ top: 8 })

            Divider().margin({ top: 14, bottom: 4 })

            this.fieldRow('报酬', rewardText(this.task))
            this.fieldRow('截止时间', this.task.deadline)
            this.fieldRow('校区', this.task.campus)
            this.fieldRow('地点', this.task.location)
            this.fieldRow('送达地点', this.task.deliveryLocation)
            this.fieldRow('完成标准', this.task.completionStandard)
            this.fieldRow('发起人', this.task.requesterName)
            this.fieldRow('服务方', this.task.providerName)

            if (this.error.length > 0) {
              Text(this.error)
                .fontSize(11)
                .fontColor('#D9480F')
                .width('100%')
                .margin({ top: 10 })
            }
          }
          .padding(16)
          .width('100%')
        }
        .layoutWeight(1)
        .width('100%')
      }
    }
    .width('100%')
    .height('100%')
  }
}
