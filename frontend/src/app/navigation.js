import {
  Bell,
  BookOpen,
  ClipboardList,
  Grid2X2,
  Home,
  LayoutDashboard,
  MessageCircle,
  PlusCircle,
  Scale,
  Settings,
  Shield,
  ShieldCheck,
  Star,
  UserRound
} from '@lucide/vue'

/**
 * 一级主导航（桌面端侧栏 / 移动端底部 Tab）
 * 只解决「用户从哪里进入」的问题。
 */
export const primaryNavItems = [
  {
    key: 'discover',
    label: '发现',
    shortLabel: '发现',
    icon: Home,
    to: '/',
    publicAccess: true,
    requiresAuth: false,
    requiresRole: null,
    section: 'discover'
  },
  {
    key: 'tasks',
    label: '任务',
    shortLabel: '任务',
    icon: Grid2X2,
    to: '/tasks',
    publicAccess: true,
    requiresAuth: false,
    requiresRole: null,
    section: 'tasks'
  },
  {
    key: 'posts',
    label: '社区',
    shortLabel: '社区',
    icon: BookOpen,
    to: '/posts',
    publicAccess: true,
    requiresAuth: false,
    requiresRole: null,
    section: 'posts'
  },
  {
    key: 'workspace',
    label: '工作台',
    shortLabel: '我的',
    icon: UserRound,
    to: '/workspace',
    publicAccess: false,
    requiresAuth: true,
    requiresRole: null,
    section: 'workspace'
  },
  {
    key: 'admin',
    label: '管理后台',
    shortLabel: '管理',
    icon: Shield,
    to: '/admin',
    publicAccess: false,
    requiresAuth: true,
    requiresRole: 'ADMIN',
    section: 'admin'
  }
]

/**
 * 任务模块二级导航
 */
export const taskNavItems = [
  {
    key: 'plaza',
    label: '任务广场',
    to: '/tasks',
    publicAccess: true,
    requiresAuth: false,
    description: '浏览公开任务'
  },
  {
    key: 'mine',
    label: '我的任务',
    to: '/workspace/tasks',
    publicAccess: false,
    requiresAuth: true,
    description: '我发布的和我接取的任务'
  },
  {
    key: 'drafts',
    label: '任务草稿',
    to: '/workspace/drafts',
    publicAccess: false,
    requiresAuth: true,
    description: '未发布的草稿任务'
  }
]

/**
 * 社区模块二级导航
 */
export const postNavItems = [
  {
    key: 'plaza',
    label: '帖子广场',
    to: '/posts',
    publicAccess: true,
    requiresAuth: false,
    description: '浏览校园帖子'
  },
  {
    key: 'mine',
    label: '我的帖子',
    to: '/workspace/posts',
    publicAccess: false,
    requiresAuth: true,
    description: '我发布的帖子'
  },
  {
    key: 'replies',
    label: '我的回复',
    to: '/workspace/replies',
    publicAccess: false,
    requiresAuth: true,
    description: '我参与过的回复'
  },
  {
    key: 'marked',
    label: '我的标价',
    to: '/workspace/marked-posts',
    publicAccess: false,
    requiresAuth: true,
    description: '我标记收藏的帖子'
  }
]

/**
 * 工作台模块二级导航（rail 形态）
 */
export const workspaceNavItems = [
  {
    key: 'overview',
    label: '工作台概览',
    to: '/workspace',
    icon: LayoutDashboard,
    description: '账户状态与快捷入口'
  },
  {
    key: 'profile',
    label: '个人资料',
    to: '/workspace/profile',
    icon: UserRound,
    description: '昵称、头像、标签与公开档案'
  },
  {
    key: 'verification',
    label: '校园认证',
    to: '/workspace/verification',
    icon: ShieldCheck,
    description: '提交与查看校内认证资料'
  },
  {
    key: 'messages',
    label: '消息私信',
    to: '/workspace/messages',
    icon: MessageCircle,
    description: '站内私信与会话'
  },
  {
    key: 'reviews',
    label: '评价记录',
    to: '/workspace/reviews',
    icon: Star,
    description: '收到的评价与信用记录'
  },
  {
    key: 'notifications',
    label: '通知中心',
    to: '/workspace/notifications',
    icon: Bell,
    description: '系统通知与未读消息'
  },
  {
    key: 'settings',
    label: '账号与主题',
    to: '/workspace/settings',
    icon: Settings,
    description: '账号安全、外观主题与显示偏好'
  }
]

/**
 * 管理后台二级导航（rail 形态）
 */
export const adminNavItems = [
  {
    key: 'dashboard',
    label: '数据仪表盘',
    to: '/admin',
    icon: LayoutDashboard,
    description: '平台数据总览',
    requiresRole: 'ADMIN'
  },
  {
    key: 'verifications',
    label: '认证审核',
    to: '/admin/verifications',
    icon: ShieldCheck,
    description: '处理校园认证申请',
    requiresRole: 'ADMIN'
  },
  {
    key: 'reports',
    label: '举报裁决',
    to: '/admin/reports',
    icon: Scale,
    description: '处理用户举报与争议',
    requiresRole: 'ADMIN'
  }
]

/**
 * 顶栏全局动作入口（发布任务）
 */
export const publishTaskAction = {
  key: 'publish-task',
  label: '发布任务',
  shortLabel: '发布',
  icon: PlusCircle,
  to: '/tasks/new',
  requiresAuth: true,
  requiresVerified: true
}

/**
 * 用户菜单条目
 */
export const userMenuItems = (auth) => [
  {
    key: 'workspace',
    label: '我的工作台',
    icon: LayoutDashboard,
    to: '/workspace',
    requiresAuth: true
  },
  {
    key: 'profile',
    label: '个人资料',
    icon: UserRound,
    to: '/workspace/profile',
    requiresAuth: true
  },
  {
    key: 'verification',
    label: '校园认证',
    icon: ShieldCheck,
    to: '/workspace/verification',
    requiresAuth: true
  },
  {
    key: 'my-tasks',
    label: '我的任务',
    icon: ClipboardList,
    to: '/workspace/tasks',
    requiresAuth: true
  },
  {
    key: 'messages',
    label: '消息私信',
    icon: MessageCircle,
    to: '/workspace/messages',
    requiresAuth: true
  },
  {
    key: 'reviews',
    label: '评价记录',
    icon: Star,
    to: '/workspace/reviews',
    requiresAuth: true
  },
  {
    key: 'notifications',
    label: '通知中心',
    icon: Bell,
    to: '/workspace/notifications',
    requiresAuth: true
  },
  {
    key: 'settings',
    label: '账号与主题',
    icon: Settings,
    to: '/workspace/settings',
    requiresAuth: true
  }
].filter((item) => canShowNavItem(item, auth))

/**
 * 根据当前用户身份过滤导航条目
 * @param {object} item
 * @param {{ isAuthenticated: import('vue').ComputedRef<boolean>, state: object }} auth
 * @returns {boolean}
 */
export function canShowNavItem(item, auth) {
  const role = String(auth?.state?.user?.role || '').toUpperCase()

  if (item.requiresRole) {
    return role === String(item.requiresRole).toUpperCase()
  }

  if (item.requiresAuth) {
    return Boolean(auth?.isAuthenticated?.value)
  }

  return true
}

/**
 * 判断当前导航项是否处于激活状态
 * @param {object} item
 * @param {import('vue-router').RouteLocationNormalized} route
 * @returns {boolean}
 */
export function isNavItemActive(item, route) {
  if (!route || !item) return false
  if (item.section && route.meta?.section === item.section) return true
  if (route.path === item.to) return true
  if (typeof item.to === 'string' && item.to !== '/' && route.path.startsWith(item.to)) return true
  return false
}
