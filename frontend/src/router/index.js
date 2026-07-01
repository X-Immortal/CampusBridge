import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import HomePageView from '../views/HomePageView.vue'
import AuthView from '../views/AuthView.vue'
import TaskPublishView from '../views/TaskPublishView.vue'
import TaskListView from '../views/TaskListView.vue'
import PostView from '../views/PostView.vue'
import ConversationView from '../views/ConversationView.vue'
import ReviewNotificationView from '../views/ReviewNotificationView.vue'
import AdminView from '../views/AdminView.vue'
import { useAuth } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'homepage',
      component: HomePageView,
      meta: {
        publicAccess: true,
        section: 'discover',
        subSection: 'home',
        title: '发现'
      }
    },
    {
      path: '/discover',
      name: 'discover',
      component: HomePageView,
      meta: {
        publicAccess: true,
        section: 'discover',
        subSection: 'home',
        title: '发现'
      }
    },
    {
      path: '/home',
      name: 'home',
      redirect: { name: 'workspace' }
    },
    {
      path: '/auth',
      name: 'auth',
      component: AuthView,
      meta: {
        publicAccess: true,
        section: 'auth',
        title: '登录 / 注册'
      }
    },
    {
      path: '/tasks',
      name: 'task-list',
      component: TaskListView,
      meta: {
        publicAccess: true,
        guestReadable: true,
        section: 'tasks',
        subSection: 'plaza',
        title: '任务广场'
      }
    },
    {
      path: '/tasks/new',
      alias: '/tasks/create',
      name: 'task-publish',
      component: TaskPublishView,
      meta: {
        requiresAuth: true,
        requiresVerified: true,
        section: 'tasks',
        subSection: 'publish',
        title: '发布任务'
      }
    },
    {
      path: '/tasks/mine',
      name: 'my-tasks',
      redirect: { name: 'workspace-tasks' }
    },
    {
      path: '/tasks/mine/:taskId',
      name: 'my-task-detail',
      redirect: (to) => ({ name: 'workspace-task-detail', params: { taskId: to.params.taskId }, query: to.query })
    },
    {
      path: '/tasks/:taskId/edit',
      name: 'task-edit',
      component: TaskPublishView,
      meta: {
        requiresAuth: true,
        section: 'tasks',
        subSection: 'edit',
        title: '编辑任务'
      }
    },
    {
      path: '/tasks/:taskId/repost',
      name: 'task-repost',
      component: TaskPublishView,
      meta: {
        requiresAuth: true,
        section: 'tasks',
        subSection: 'repost',
        title: '重新发布任务'
      }
    },
    {
      path: '/tasks/:taskId',
      name: 'task-detail',
      component: TaskListView,
      meta: {
        publicAccess: true,
        guestReadable: true,
        section: 'tasks',
        subSection: 'plaza',
        title: '任务详情'
      }
    },
    {
      path: '/posts',
      name: 'post-list',
      component: PostView,
      meta: {
        publicAccess: true,
        guestReadable: true,
        section: 'posts',
        subSection: 'plaza',
        title: '帖子广场'
      }
    },
    {
      path: '/conversations',
      name: 'conversation-list',
      redirect: { name: 'workspace-messages' }
    },
    {
      path: '/conversations/:conversationId',
      name: 'conversation-detail',
      redirect: (to) => ({
        name: 'workspace-message-detail',
        params: { conversationId: to.params.conversationId },
        query: to.query
      })
    },
    {
      path: '/reviews',
      name: 'reviews',
      redirect: { name: 'workspace-reviews' }
    },
    {
      path: '/notifications',
      name: 'notifications',
      redirect: { name: 'workspace-notifications' }
    },
    {
      path: '/workspace',
      name: 'workspace',
      component: HomeView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'overview',
        title: '工作台'
      }
    },
    {
      path: '/workspace/profile',
      name: 'workspace-profile',
      component: HomeView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'profile',
        title: '个人资料'
      }
    },
    {
      path: '/workspace/verification',
      name: 'workspace-verification',
      component: HomeView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'verification',
        title: '校园认证'
      }
    },
    {
      path: '/workspace/messages',
      name: 'workspace-messages',
      component: ConversationView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'messages',
        title: '消息私信'
      }
    },
    {
      path: '/workspace/messages/:conversationId',
      name: 'workspace-message-detail',
      component: ConversationView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'messages',
        title: '消息私信'
      }
    },
    {
      path: '/workspace/reviews',
      name: 'workspace-reviews',
      component: ReviewNotificationView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'reviews',
        title: '评价记录'
      }
    },
    {
      path: '/workspace/notifications',
      name: 'workspace-notifications',
      component: ReviewNotificationView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'notifications',
        title: '通知中心'
      }
    },
    {
      path: '/workspace/settings',
      name: 'workspace-settings',
      component: HomeView,
      meta: {
        requiresAuth: true,
        section: 'workspace',
        subSection: 'settings',
        title: '账号与主题'
      }
    },
    {
      path: '/workspace/tasks',
      name: 'workspace-tasks',
      component: TaskListView,
      meta: {
        requiresAuth: true,
        section: 'tasks',
        subSection: 'mine',
        title: '我的任务'
      }
    },
    {
      path: '/workspace/tasks/:taskId',
      name: 'workspace-task-detail',
      component: TaskListView,
      meta: {
        requiresAuth: true,
        section: 'tasks',
        subSection: 'mine',
        title: '我的任务'
      }
    },
    {
      path: '/workspace/drafts',
      name: 'workspace-drafts',
      component: TaskListView,
      meta: {
        requiresAuth: true,
        section: 'tasks',
        subSection: 'drafts',
        title: '任务草稿'
      }
    },
    {
      path: '/workspace/posts',
      name: 'workspace-posts',
      component: PostView,
      meta: {
        requiresAuth: true,
        section: 'posts',
        subSection: 'mine',
        title: '我的帖子'
      }
    },
    {
      path: '/workspace/replies',
      name: 'workspace-replies',
      component: PostView,
      meta: {
        requiresAuth: true,
        section: 'posts',
        subSection: 'replies',
        title: '我的回复'
      }
    },
    {
      path: '/workspace/marked-posts',
      name: 'workspace-marked-posts',
      component: PostView,
      meta: {
        requiresAuth: true,
        section: 'posts',
        subSection: 'marked',
        title: '我的标记'
      }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      meta: {
        requiresAuth: true,
        requiresRole: 'ADMIN',
        section: 'admin',
        subSection: 'dashboard',
        title: '管理后台'
      }
    },
    {
      path: '/admin/verifications',
      name: 'admin-verifications',
      component: AdminView,
      meta: {
        requiresAuth: true,
        requiresRole: 'ADMIN',
        section: 'admin',
        subSection: 'verifications',
        title: '认证审核'
      }
    },
    {
      path: '/admin/reports',
      name: 'admin-reports',
      component: AdminView,
      meta: {
        requiresAuth: true,
        requiresRole: 'ADMIN',
        section: 'admin',
        subSection: 'reports',
        title: '举报裁决'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      redirect: { name: 'homepage' }
    }
  ]
})

router.beforeEach((to) => {
  const { isAuthenticated, state } = useAuth()
  const role = String(state.user?.role || '').toUpperCase()
  const isVerified = state.verification?.status === 'VERIFIED'

  if (to.name === 'auth' && isAuthenticated.value) {
    return { name: 'workspace' }
  }

  if (to.meta?.publicAccess) {
    return true
  }

  if (to.meta?.requiresAuth && !isAuthenticated.value) {
    return {
      name: 'auth',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (!to.meta?.publicAccess && !isAuthenticated.value) {
    return {
      name: 'auth',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (to.meta?.requiresRole && role !== String(to.meta.requiresRole).toUpperCase()) {
    return { name: 'homepage' }
  }

  if (to.meta?.requiresVerified && !isVerified) {
    return { name: 'workspace-verification', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
