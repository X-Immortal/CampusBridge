import { UIAbility } from '@kit.AbilityKit'
import { window } from '@kit.ArkUI'
export default class EntryAbility extends UIAbility {
  onForeground(): void { this.context.eventHub.emit('campus.foreground') }
  onBackground(): void { this.context.eventHub.emit('campus.background') }
  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index')
  }
}
