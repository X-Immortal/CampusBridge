import { router } from '@kit.ArkUI';
import { checkBackend, DEFAULT_BASE } from '../ui/taskApi';
import { TaskDetailPanel } from '../components/TaskDetailPanel';
import { TaskListPanel } from '../components/TaskListPanel';

/**
 * App Shell：底部导航（助手 / 任务）+ 后端健康探针 + 平板 master-detail。
 *
 * - 助手：进入 Assistant 真实链路页；后端不可用时按钮禁用（L2）。
 * - 任务：手机列表→详情页；≥840vp 左列表右详情，同一套 TaskListPanel/TaskDetailPanel。
 * - L2：探针失败显示横条并暂停助手，但任务页仍可重试/看已加载数据。
 */
@Entry
@Component
struct Shell {
  @State activeTab: number = 0;
  @State viewportWidth: number = 360;
  @State online: boolean = true;
  @State checking: boolean = true;
  @State selectedTaskId: number = 0;
  @State taskRefresh: number = 0;
  private base: string = DEFAULT_BASE;

  aboutToAppear(): void {
    this.probe();
  }

  onPageShow(): void {
    this.probe();
    this.taskRefresh += 1;
  }

  private async probe(): Promise<void> {
    this.checking = true;
    this.online = await checkBackend(this.base);
    this.checking = false;
  }

  @Builder
  offlineBanner() {
    Row() {
      Text('L2 降级：后端不可用，助手暂停；任务页可重试')
        .fontSize(11)
        .fontColor('#8A4B08')
        .layoutWeight(1)
      Button('重试')
        .fontSize(11)
        .height(26)
        .backgroundColor('#FFFFFF')
        .fontColor('#8A4B08')
        .borderRadius(13)
        .onClick(() => {
          this.probe();
        })
    }
    .width('100%')
    .padding({ left: 12, right: 12, top: 6, bottom: 6 })
    .backgroundColor('#FFF4E5')
  }

  @Builder
  assistantTab() {
    Scroll() {
      Column() {
        Text('CampusBridge 助手')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#101A2B')
        Text('说一句话 → 追问缺失 → 当场出卡 → 确认建单 → 全程留证据')
          .fontSize(12)
          .fontColor('#7A8BA6')
          .margin({ top: 6 })

        Row() {
          Text(this.checking ? '正在检查后端…' : (this.online ? '后端在线' : '后端离线'))
            .fontSize(12)
            .fontColor(this.online ? '#2F9E44' : '#D9480F')
          Text(' · ' + this.base)
            .fontSize(11)
            .fontColor('#A6B4C8')
        }
        .margin({ top: 14 })

        Button('进入助手对话')
          .width('100%')
          .height(44)
          .margin({ top: 18 })
          .backgroundColor(this.online ? '#2F6FDE' : '#C7CED8')
          .fontColor('#FFFFFF')
          .borderRadius(22)
          .enabled(this.online)
          .onClick(() => {
            router.pushUrl({ url: 'pages/Assistant' });
          })

        Button('四类卡片渲染演示')
          .width('100%')
          .height(40)
          .margin({ top: 10 })
          .backgroundColor('#FFFFFF')
          .fontColor('#2F6FDE')
          .borderRadius(20)
          .onClick(() => {
            router.pushUrl({ url: 'pages/Index' });
          })

        Column() {
          Text('当前能力')
            .fontSize(13)
            .fontWeight(FontWeight.Medium)
            .fontColor('#1F2D3D')
            .width('100%')
          Text('· 助手真实链路：/api/assistant + /api/stream')
            .fontSize(11)
            .fontColor('#7A8BA6')
            .margin({ top: 8 })
          Text('· 客户端白名单：坏卡整张拒绝，保留上一张')
            .fontSize(11)
            .fontColor('#7A8BA6')
            .margin({ top: 4 })
          Text('· 任务页走 1.0 公开接口，模型/后端挂了仍可看')
            .fontSize(11)
            .fontColor('#7A8BA6')
            .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .padding(14)
        .margin({ top: 20 })
        .backgroundColor('#FFFFFF')
        .borderRadius(12)
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding(16)
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  taskTab() {
    if (this.viewportWidth >= 840) {
      Row() {
        Column() {
          TaskListPanel({
            refreshTick: this.taskRefresh,
            selectedId: this.selectedTaskId,
            onOpenTask: (taskId: number) => {
              this.selectedTaskId = taskId;
            }
          })
        }
        .width('40%')
        .height('100%')
        .padding({ left: 12, right: 6, top: 8, bottom: 12 })

        Column() {
          if (this.selectedTaskId > 0) {
            TaskDetailPanel({ taskId: this.selectedTaskId })
          } else {
            Column() {
              Text('从左侧选一条任务')
                .fontSize(13)
                .fontColor('#7A8BA6')
            }
            .width('100%')
            .height('100%')
            .justifyContent(FlexAlign.Center)
          }
        }
        .layoutWeight(1)
        .height('100%')
        .padding({ left: 6, right: 12, top: 8, bottom: 12 })
      }
      .width('100%')
      .height('100%')
    } else {
      TaskListPanel({
        refreshTick: this.taskRefresh,
        onOpenTask: (taskId: number) => {
          router.pushUrl({ url: 'pages/TaskDetail', params: { taskId: taskId } });
        }
      })
        .width('100%')
        .height('100%')
        .padding({ left: 12, right: 12, top: 8, bottom: 12 })
    }
  }

  build() {
    Column() {
      if (!this.online) {
        this.offlineBanner()
      }
      Tabs({ barPosition: BarPosition.End, index: this.activeTab }) {
        TabContent() {
          this.assistantTab()
        }
        .tabBar('助手')

        TabContent() {
          this.taskTab()
        }
        .tabBar('任务')
      }
      .layoutWeight(1)
      .width('100%')
      .onChange((index: number) => {
        this.activeTab = index;
        if (index === 1) {
          this.taskRefresh += 1;
        }
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F2F5FA')
    .onAreaChange((oldArea: Area, newArea: Area) => {
      this.viewportWidth = Number(newArea.width);
    })
  }
}
