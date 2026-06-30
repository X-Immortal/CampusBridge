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
      component: HomePageView
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView
    },
    {
      path: '/auth',
      name: 'auth',
      component: AuthView
    },
    {
      path: '/tasks',
      name: 'task-list',
      component: TaskListView
    },
    {
      path: '/tasks/new',
      alias: '/tasks/create',
      name: 'task-publish',
      component: TaskPublishView
    },
    {
      path: '/tasks/mine',
      name: 'my-tasks',
      component: TaskListView
    },
    {
      path: '/tasks/mine/:taskId',
      name: 'my-task-detail',
      component: TaskListView
    },
    {
      path: '/tasks/:taskId/edit',
      name: 'task-edit',
      component: TaskPublishView
    },
    {
      path: '/tasks/:taskId/repost',
      name: 'task-repost',
      component: TaskPublishView
    },
    {
      path: '/tasks/:taskId',
      name: 'task-detail',
      component: TaskListView
    },
    {
      path: '/posts',
      name: 'post-list',
      component: PostView
    },
    {
      path: '/conversations',
      name: 'conversation-list',
      component: ConversationView
    },
    {
      path: '/conversations/:conversationId',
      name: 'conversation-detail',
      component: ConversationView
    },
    {
      path: '/reviews',
      name: 'reviews',
      component: ReviewNotificationView
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: ConversationView
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView
    }
  ]
})

router.beforeEach((to) => {
  const { isAuthenticated } = useAuth()
  const publicRouteNames = new Set(['homepage', 'auth', 'task-list', 'task-detail', 'post-list'])

  if (to.name === 'auth' && isAuthenticated.value) {
    return { name: 'home' }
  }

  if (!publicRouteNames.has(to.name) && !isAuthenticated.value) {
    return {
      name: 'auth',
      query: {
        redirect: to.fullPath
      }
    }
  }

  return true
})

export default router
