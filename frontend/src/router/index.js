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
      meta: { publicAccess: true }
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView,
      meta: { publicAccess: true }
    },
    {
      path: '/auth',
      name: 'auth',
      component: AuthView,
      meta: { publicAccess: true }
    },
    {
      path: '/tasks',
      name: 'task-list',
      component: TaskListView,
      meta: { publicAccess: true }
    },
    {
      path: '/tasks/new',
      alias: '/tasks/create',
      name: 'task-publish',
      component: TaskPublishView,
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks/mine',
      name: 'my-tasks',
      component: TaskListView,
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks/mine/:taskId',
      name: 'my-task-detail',
      component: TaskListView,
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks/:taskId/edit',
      name: 'task-edit',
      component: TaskPublishView,
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks/:taskId/repost',
      name: 'task-repost',
      component: TaskPublishView,
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks/:taskId',
      name: 'task-detail',
      component: TaskListView,
      meta: { publicAccess: true }
    },
    {
      path: '/posts',
      name: 'post-list',
      component: PostView,
      meta: { publicAccess: true }
    },
    {
      path: '/conversations',
      name: 'conversation-list',
      component: ConversationView,
      meta: { requiresAuth: true }
    },
    {
      path: '/conversations/:conversationId',
      name: 'conversation-detail',
      component: ConversationView,
      meta: { requiresAuth: true }
    },
    {
      path: '/reviews',
      name: 'reviews',
      component: ReviewNotificationView,
      meta: { requiresAuth: true }
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: ReviewNotificationView,
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      meta: {
        requiresAuth: true,
        requiresRole: 'ADMIN'
      }
    }
  ]
})

router.beforeEach((to) => {
  const { isAuthenticated, state } = useAuth()
  const role = String(state.user?.role || '').toUpperCase()

  if (to.name === 'auth' && isAuthenticated.value) {
    return { name: 'home' }
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
    return { name: 'home' }
  }

  return true
})

export default router
