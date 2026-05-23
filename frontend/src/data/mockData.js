const now = new Date()
const hoursFromNow = (hours) => new Date(now.getTime() + hours * 60 * 60 * 1000).toISOString()
const hoursAgo = (hours) => new Date(now.getTime() - hours * 60 * 60 * 1000).toISOString()

export const sampleTasks = [
  {
    id: 1001,
    requesterId: 3,
    providerId: null,
    category: 'ERRAND',
    title: '帮取东门快递并送到信科楼',
    description: '快递体积较小，送达后请在站内发一张凭证图片。',
    campus: '主校区',
    location: '东门驿站到信科楼 B 座',
    deadline: hoursFromNow(5),
    rewardType: 'POINTS',
    rewardDescription: '30 积分',
    completionStandard: '送达本人并上传照片',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 1,
    routeHint: '东门驿站 3 号架',
    status: 'OPEN',
    createdAt: hoursAgo(2),
    updatedAt: hoursAgo(1)
  },
  {
    id: 1002,
    requesterId: 8,
    providerId: 12,
    category: 'AGENCY',
    title: '图书馆占座取消提醒协助',
    description: '需要在规定时间前到自习区帮忙确认座位状态。',
    campus: '南校区',
    location: '图书馆三楼',
    deadline: hoursFromNow(18),
    rewardType: 'NEGOTIABLE',
    rewardDescription: '奶茶或积分协商',
    completionStandard: '站内说明确认结果',
    requiresProof: 0,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    routeHint: '',
    status: 'IN_PROGRESS',
    createdAt: hoursAgo(12),
    updatedAt: hoursAgo(3)
  },
  {
    id: 1003,
    requesterId: 5,
    providerId: 10,
    category: 'ERRAND',
    title: '帮忙打印课程资料',
    description: 'A4 黑白双面，约 28 页，课前送到教学楼。',
    campus: '主校区',
    location: '教学楼 A203',
    deadline: hoursFromNow(28),
    rewardType: 'CASH',
    rewardDescription: '12 元',
    completionStandard: '资料完整送达',
    requiresProof: 1,
    onlyInAppContact: 1,
    allowOfflineContact: 0,
    isInstantTask: 0,
    routeHint: '打印店可选北门附近',
    status: 'PENDING_CONFIRMATION',
    createdAt: hoursAgo(24),
    updatedAt: hoursAgo(4)
  }
]

export const sampleContent = [
  {
    itemType: 'TASK',
    sourceId: 1001,
    title: sampleTasks[0].title,
    summary: sampleTasks[0].description,
    authorId: 3,
    authorName: '林同学',
    categoryName: '跑腿代办',
    statusLabel: '待接单',
    createdAt: sampleTasks[0].createdAt,
    sortTime: sampleTasks[0].updatedAt,
    rewardLabel: '30 积分'
  },
  {
    itemType: 'POST',
    sourceId: 2001,
    title: '求推荐计算机组成复习资料',
    summary: '希望找一份按章节整理的知识点清单，最好包含往年题型。',
    authorId: 11,
    authorName: 'Echo',
    categoryName: '打听求助',
    statusLabel: '已发布',
    createdAt: hoursAgo(4),
    sortTime: hoursAgo(1),
    rewardLabel: ''
  },
  {
    itemType: 'TASK',
    sourceId: 1003,
    title: sampleTasks[2].title,
    summary: sampleTasks[2].description,
    authorId: 5,
    authorName: '王同学',
    categoryName: '跑腿代办',
    statusLabel: '待确认',
    createdAt: sampleTasks[2].createdAt,
    sortTime: sampleTasks[2].updatedAt,
    rewardLabel: '12 元'
  }
]

export const sampleApplications = [
  {
    applicationId: 501,
    applicantId: 21,
    username: 'helper_21',
    nickname: '陈晨',
    avatarUrl: '',
    bio: '主校区跑腿经验多，通常 20 分钟内响应。',
    campus: '主校区',
    college: '软件学院',
    verified: true,
    creditLevel: 4,
    points: 860,
    message: '我现在就在东门附近，可以直接取件。',
    status: 'PENDING',
    createdAt: hoursAgo(1)
  },
  {
    applicationId: 502,
    applicantId: 22,
    username: 'yue_runner',
    nickname: '月月',
    avatarUrl: '',
    bio: '晚间可接单，偏好资料打印和轻量配送。',
    campus: '主校区',
    college: '外国语学院',
    verified: true,
    creditLevel: 3,
    points: 510,
    message: '可以帮送，预计 30 分钟内完成。',
    status: 'PENDING',
    createdAt: hoursAgo(0.5)
  }
]

export const sampleConversations = [
  {
    id: 701,
    type: 'TASK',
    title: '帮取东门快递并送到信科楼',
    ownerId: 3,
    peerUserId: 21,
    relatedType: 'TASK',
    relatedId: 1001,
    replyable: 1,
    lastMessageAt: hoursAgo(0.3),
    createdAt: hoursAgo(1)
  },
  {
    id: 702,
    type: 'SYSTEM',
    title: '任务状态提醒',
    ownerId: 3,
    peerUserId: null,
    relatedType: 'TASK',
    relatedId: 1003,
    replyable: 0,
    lastMessageAt: hoursAgo(2),
    createdAt: hoursAgo(2)
  }
]

export const sampleMessages = [
  {
    id: 9001,
    conversationId: 701,
    senderId: 21,
    type: 'TEXT',
    content: '我已到东门驿站，取件码可以在站内发我。',
    readAt: null,
    createdAt: hoursAgo(0.8)
  },
  {
    id: 9002,
    conversationId: 701,
    senderId: 3,
    type: 'TEXT',
    content: '谢谢，取件码我已经发在任务备注里了。',
    readAt: null,
    createdAt: hoursAgo(0.5)
  }
]
