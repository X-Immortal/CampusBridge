export const VERIFICATION_METHODS = [
  { value: 'STUDENT_CARD_IMAGE', label: '学生证照片' },
  { value: 'CAMPUS_EMAIL', label: '校园邮箱' },
  { value: 'MANUAL_REVIEW', label: '人工审核' }
]

export const STATUS_OPTIONS = [
  { value: 'UNVERIFIED', label: '未认证', tone: 'muted' },
  { value: 'PENDING', label: '审核中', tone: 'warning' },
  { value: 'VERIFIED', label: '已认证', tone: 'success' },
  { value: 'REJECTED', label: '已驳回', tone: 'danger' },
  { value: 'ENABLED', label: '可用', tone: 'success' },
  { value: 'DISABLED', label: '禁用', tone: 'danger' },
  { value: 'LOGGED_IN', label: '已登录', tone: 'success' },
  { value: 'GUEST', label: '游客模式', tone: 'warning' }
]

export const TASK_CATEGORY_OPTIONS = [
  {
    value: 'ERRAND',
    label: '跑腿代取',
    description: '取快递、带物、临时跑腿等低门槛互助'
  },
  {
    value: 'AGENCY',
    label: '委托代办',
    description: '资料提交、手续办理、排队协助等明确任务'
  },
  {
    value: 'ATTENDANCE_ASSIST',
    label: '出勤/签到协助',
    description: '会场入场、签到、公干等普通协助'
  }
]

export const REWARD_TYPE_OPTIONS = [
  { value: 'FREE', label: '免费互助', description: '适合轻量、顺手帮忙的需求' },
  { value: 'POINTS', label: '积分奖励', description: '用于平台积分或活动确认' },
  { value: 'CASH', label: '小额酬谢', description: '适合时效或有跑腿成本的任务' },
  { value: 'NEGOTIABLE', label: '双方协商', description: '先说明范围，接单后再确认' }
]

export const TASK_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿', tone: 'muted' },
  { value: 'OPEN', label: '待接单', tone: 'accent' },
  { value: 'PENDING_REVIEW', label: '待审核', tone: 'warning' },
  { value: 'ACCEPTED', label: '已选人', tone: 'info' },
  { value: 'IN_PROGRESS', label: '进行中', tone: 'info' },
  { value: 'PENDING_CONFIRMATION', label: '待确认', tone: 'warning' },
  { value: 'COMPLETED', label: '已完成', tone: 'success' },
  { value: 'REVIEWED', label: '已评价', tone: 'success' },
  { value: 'CANCELLED', label: '已取消', tone: 'muted' },
  { value: 'DISPUTED', label: '争议中', tone: 'danger' },
  { value: 'REJECTED', label: '未通过', tone: 'danger' }
]

export const APPLICATION_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待选择', tone: 'warning' },
  { value: 'ACCEPTED', label: '已选中', tone: 'success' },
  { value: 'REJECTED', label: '已婉拒', tone: 'muted' },
  { value: 'WITHDRAWN', label: '已撤回', tone: 'muted' }
]

export const CONVERSATION_TYPE_OPTIONS = [
  { value: 'TASK', label: '任务会话', tone: 'info' },
  { value: 'PRIVATE', label: '私信会话', tone: 'accent' },
  { value: 'SYSTEM', label: '系统会话', tone: 'muted' }
]

export const MESSAGE_TYPE_OPTIONS = [
  { value: 'TEXT', label: '文字消息' },
  { value: 'IMAGE', label: '图片消息' },
  { value: 'SYSTEM_NOTIFICATION', label: '系统通知' }
]

export const TASK_SORT_OPTIONS = [
  { value: 'CREATED_AT', label: '最新发布' },
  { value: 'DEADLINE', label: '截止时间近' },
  { value: 'DISTANCE', label: '距离优先' },
  { value: 'CREDIT', label: '信用优先' }
]

export function optionLabel(options, value) {
  if (value === null || value === undefined || value === '') return '未设置'
  return options.find((option) => option.value === value)?.label ?? value
}

export function statusTone(status) {
  return [
    ...STATUS_OPTIONS,
    ...TASK_STATUS_OPTIONS,
    ...APPLICATION_STATUS_OPTIONS,
    ...CONVERSATION_TYPE_OPTIONS
  ].find((item) => item.value === status)?.tone ?? 'muted'
}
