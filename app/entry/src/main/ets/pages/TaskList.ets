import { router } from '@kit.ArkUI';
import { TaskListPanel } from '../components/TaskListPanel';

/**
 * 任务广场独立页（手机从 Shell 的「任务」页直接进来时复用）。
 */
@Entry
@Component
struct TaskList {
  @State refreshTick: number = 0;

  build() {
    Column() {
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
        Text('任务广场')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
          .margin({ left: 12 })
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 4 })

      TaskListPanel({
        refreshTick: this.refreshTick,
        onOpenTask: (taskId: number) => {
          router.pushUrl({ url: 'pages/TaskDetail', params: { taskId: taskId } });
        }
      })
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 12, right: 12, bottom: 12 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F2F5FA')
  }
}
