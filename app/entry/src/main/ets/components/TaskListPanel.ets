import { DEFAULT_BASE, fetchTasks, rewardText, statusText, TaskItem, textOf } from '../ui/taskApi';

/**
 * 任务广场列表（L2 固定页面）。
 *
 * 只读 1.0 公开接口；列表加载失败时保留已有数据并显示降级提示，不把空白页当成正常。
 */
@Component
export struct TaskListPanel {
  @Prop @Watch('onRefreshTick') refreshTick: number = 0;
  @Prop selectedId: number = 0;
  onOpenTask: (taskId: number) => void = (taskId: number) => {};

  @State tasks: TaskItem[] = [];
  @State total: number = 0;
  @State loading: boolean = true;
  @State error: string = '';
  private base: string = DEFAULT_BASE;

  aboutToAppear(): void {
    this.load();
  }

  onRefreshTick(): void {
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.error = '';
    try {
      const page = await fetchTasks(this.base);
      this.tasks = page.tasks;
      this.total = page.total;
    } catch (error) {
      // 保留上一次的成功列表，只把错误显示出来：杀后端时仍能看已加载的任务。
      this.error = '任务列表加载失败：' + String(error);
    } finally {
      this.loading = false;
    }
  }

  @Builder
  taskRow(task: TaskItem) {
    Row() {
      Column() {
        Text(task.title)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor('#1F2D3D')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Row() {
          Text(statusText(task.status))
            .fontSize(11)
            .fontColor('#2F6FDE')
          Text(rewardText(task))
            .fontSize(11)
            .fontColor('#B08900')
            .margin({ left: 10 })
          Text(textOf(task.location))
            .fontSize(11)
            .fontColor('#7A8BA6')
            .margin({ left: 10 })
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .layoutWeight(1)
        }
        .width('100%')
        .margin({ top: 5 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Text(textOf(task.deadline))
        .fontSize(11)
        .fontColor('#7A8BA6')
        .margin({ left: 8 })
    }
    .width('100%')
    .padding(12)
    .backgroundColor(this.selectedId === task.id ? '#E8F0FE' : '#FFFFFF')
    .borderRadius(10)
    .borderWidth(1)
    .borderColor(this.selectedId === task.id ? '#2F6FDE' : '#E3E8F0')
  }

  build() {
    Column() {
      Row() {
        Text('任务广场')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
          .layoutWeight(1)
        Text('共 ' + String(this.total) + ' 条')
          .fontSize(11)
          .fontColor('#7A8BA6')
          .margin({ right: 8 })
        Button('刷新')
          .fontSize(12)
          .height(30)
          .backgroundColor('#FFFFFF')
          .fontColor('#2F6FDE')
          .borderRadius(15)
          .onClick(() => {
            this.load();
          })
      }
      .width('100%')
      .padding({ left: 4, right: 4, top: 4, bottom: 8 })

      if (this.error.length > 0) {
        Text(this.error)
          .fontSize(11)
          .fontColor('#D9480F')
          .width('100%')
          .padding(8)
          .backgroundColor('#FFF0E8')
          .borderRadius(8)
          .margin({ bottom: 8 })
      }

      if (this.loading && this.tasks.length === 0) {
        Column() {
          LoadingProgress()
            .width(32)
            .height(32)
          Text('正在加载公开任务…')
            .fontSize(12)
            .fontColor('#7A8BA6')
            .margin({ top: 8 })
        }
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.tasks.length === 0) {
        Column() {
          Text('暂时没有可展示的任务')
            .fontSize(13)
            .fontColor('#7A8BA6')
          Text('助手建单后点「刷新」会出现在这里')
            .fontSize(11)
            .fontColor('#A6B4C8')
            .margin({ top: 6 })
        }
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        List({ space: 8 }) {
          ForEach(this.tasks, (task: TaskItem) => {
            ListItem() {
              this.taskRow(task)
            }
            .onClick(() => {
              this.onOpenTask(task.id);
            })
          }, (task: TaskItem) => String(task.id))
        }
        .layoutWeight(1)
        .width('100%')
      }
    }
    .width('100%')
    .height('100%')
  }
}
