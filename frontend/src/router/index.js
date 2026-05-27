import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import AuthView from '../views/AuthView.vue'
import TaskPublishView from '../views/TaskPublishView.vue'
import TaskListView from '../views/TaskListView.vue'
import ConversationView from '../views/ConversationView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
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
      path: '/conversations',
      name: 'conversation-list',
      component: ConversationView
    },
    {
      path: '/conversations/:conversationId',
      name: 'conversation-detail',
      component: ConversationView
    }
  ]
})

export default router
