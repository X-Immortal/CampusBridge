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

export function optionLabel(options, value) {
  if (value === null || value === undefined || value === '') return '未设置'
  return options.find((option) => option.value === value)?.label ?? value
}

export function statusTone(status) {
  return STATUS_OPTIONS.find((item) => item.value === status)?.tone ?? 'muted'
}
