import { router } from '@kit.ArkUI';
import { TaskDetailPanel } from '../components/TaskDetailPanel';

interface TaskDetailParams {
  taskId: number;
}

/**
 * 任务详情独立页：窄屏从列表点进来，宽屏由 Shell 的 master-detail 直接展示。
 */
@Entry
@Component
struct TaskDetail {
  @State taskId: number = 0;

  aboutToAppear(): void {
    const params = router.getParams() as TaskDetailParams;
    if (params !== undefined && params !== null && params.taskId !== undefined) {
      this.taskId = params.taskId;
    }
  }

  build() {
    Column() {
      Row() {
        Button('‹ 任务列表')
          .fontSize(13)
          .height(32)
          .backgroundColor('#FFFFFF')
          .fontColor('#34495E')
          .borderRadius(16)
          .onClick(() => {
            router.back();
          })
        Text('任务详情')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
          .margin({ left: 12 })
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 4 })

      TaskDetailPanel({ taskId: this.taskId })
        .layoutWeight(1)
        .width('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F2F5FA')
  }
}
