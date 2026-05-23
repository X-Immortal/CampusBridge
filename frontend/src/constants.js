export const TASK_CATEGORIES = [
  { value: 'ERRAND', label: '跑腿代办' },
  { value: 'AGENCY', label: '委托协助' },
  { value: 'ATTENDANCE_ASSIST', label: '出勤协助' }
]

export const POST_CATEGORIES = [
  { value: 'INQUIRY_HELP', label: '打听求助' },
  { value: 'SECOND_HAND', label: '二手交易' },
  { value: 'TEAM_FRIEND', label: '组队搭子' },
  { value: 'CAMPUS_SOCIAL', label: '校园社交' }
]

export const REWARD_TYPES = [
  { value: 'FREE', label: '无偿互助' },
  { value: 'POINTS', label: '积分奖励' },
  { value: 'CASH', label: '现金报酬' },
  { value: 'NEGOTIABLE', label: '协商报酬' }
]

export const TASK_STATUSES = [
  { value: 'DRAFT', label: '草稿', tone: 'muted' },
  { value: 'OPEN', label: '待接单', tone: 'info' },
  { value: 'PENDING_REVIEW', label: '待审核', tone: 'warning' },
  { value: 'ACCEPTED', label: '已接单', tone: 'accent' },
  { value: 'IN_PROGRESS', label: '进行中', tone: 'accent' },
  { value: 'PENDING_CONFIRMATION', label: '待确认', tone: 'warning' },
  { value: 'COMPLETED', label: '已完成', tone: 'success' },
  { value: 'REVIEWED', label: '已评价', tone: 'success' },
  { value: 'CANCELLED', label: '已取消', tone: 'danger' },
  { value: 'DISPUTED', label: '争议中', tone: 'danger' },
  { value: 'REJECTED', label: '已驳回', tone: 'danger' }
]

export const APPLICATION_STATUSES = [
  { value: 'PENDING', label: '待选择', tone: 'warning' },
  { value: 'ACCEPTED', label: '已选中', tone: 'success' },
  { value: 'REJECTED', label: '未选中', tone: 'muted' },
  { value: 'WITHDRAWN', label: '已撤回', tone: 'muted' }
]

export const TRANSITION_ACTIONS = [
  { value: 'START', label: '开始任务' },
  { value: 'CONFIRM_COMPLETION', label: '确认完成' },
  { value: 'REJECT_COMPLETION', label: '驳回凭证' },
  { value: 'DISPUTE', label: '发起争议' },
  { value: 'CANCEL', label: '取消任务' }
]

export const VERIFICATION_METHODS = [
  { value: 'STUDENT_CARD_IMAGE', label: '学生证照片' },
  { value: 'CAMPUS_EMAIL', label: '校园邮箱' },
  { value: 'MANUAL_REVIEW', label: '人工审核' }
]

export const REPORT_TARGET_TYPES = [
  { value: 'POST', label: '帖子' },
  { value: 'REPLY', label: '回复' },
  { value: 'TASK', label: '任务' },
  { value: 'USER', label: '用户' },
  { value: 'MESSAGE', label: '消息' }
]

const fallbackLabels = new Map([
  ['TASK', '任务'],
  ['POST', '帖子'],
  ['TEXT', '文本'],
  ['IMAGE', '图片'],
  ['SYSTEM_NOTIFICATION', '系统通知'],
  ['VERIFIED', '已认证'],
  ['PENDING', '审核中'],
  ['UNVERIFIED', '未认证'],
  ['REJECTED', '已驳回']
])

export function optionLabel(options, value) {
  if (value === null || value === undefined || value === '') return '未设置'
  return options.find((option) => option.value === value)?.label ?? fallbackLabels.get(value) ?? value
}

export function statusTone(status) {
  return TASK_STATUSES.find((item) => item.value === status)?.tone ?? 'muted'
}

export function toLocalDateTimeInput(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const offset = date.getTimezoneOffset()
  const local = new Date(date.getTime() - offset * 60000)
  return local.toISOString().slice(0, 16)
}

export function toApiDateTime(value) {
  if (!value) return null
  return `${value}:00`
}
