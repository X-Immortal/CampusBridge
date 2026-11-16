export const NOTIFICATION_PRESENTATION = {
  POST_REPLY: { category: 'POST', tone: 'info' },
  TASK_APPLICATION_CREATED: { category: 'TASK', tone: 'info' },
  TASK_APPLICATION_ACCEPTED: { category: 'TASK', tone: 'success' },
  TASK_APPLICATION_REJECTED: { category: 'TASK', tone: 'muted' },
  TASK_APPLICATION_WITHDRAWN: { category: 'TASK', tone: 'muted' },
  TASK_PUBLISHED: { category: 'TASK', tone: 'info' },
  TASK_PENDING_REVIEW: { category: 'TASK', tone: 'warning' },
  TASK_ACCEPTED: { category: 'TASK', tone: 'success' },
  TASK_STARTED: { category: 'TASK', tone: 'info' },
  TASK_PENDING_CONFIRMATION: { category: 'TASK', tone: 'warning' },
  TASK_COMPLETED: { category: 'TASK', tone: 'success' },
  TASK_REVIEWED: { category: 'REVIEW', tone: 'info' },
  TASK_CANCELLED: { category: 'TASK', tone: 'muted' },
  TASK_DISPUTED: { category: 'TASK', tone: 'danger' },
  TASK_REJECTED: { category: 'TASK', tone: 'danger' },
  TASK_OPEN_TIMEOUT: { category: 'TASK', tone: 'warning' },
  TASK_CONFIRMATION_REMINDER: { category: 'TASK', tone: 'warning' }
}

export function presentNotification(notification) {
  const presentation = NOTIFICATION_PRESENTATION[notification.type]
  return {
    ...notification,
    ...presentation,
    read: notification.readAt !== null,
    source: notification.relatedType === 'POST' ? '帖子通知' : '任务通知',
    taskId: notification.relatedType === 'TASK' ? notification.relatedId : null,
    postId: notification.relatedType === 'POST' ? notification.relatedId : null
  }
}
