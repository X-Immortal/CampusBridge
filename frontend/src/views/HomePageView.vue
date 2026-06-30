<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BookOpen,
  Briefcase,
  Flame,
  LoaderCircle,
  MessageCircle,
  Sparkles,
  ThumbsUp,
  Clock3,
  ArrowRight,
  MapPin
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const router = useRouter()
const { currentUserId } = useAuth()

const loading = ref(true)
const hotPosts = ref([])
const latestTasks = ref([])
const error = ref('')

const taskCategoryLabels = {
  ERRAND: '跑腿代取',
  AGENCY: '代办委托'
}

const taskCategoryTones = {
  ERRAND: 'info',
  AGENCY: 'warn'
}

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function authorCopy(authorId, nickname) {
  if (currentUserId.value != null && String(authorId) === String(currentUserId.value)) return '我'
  if (nickname) return nickname
  return `同学 ${authorId}`
}

function compactContent(value, maxLength = 80) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '暂无内容'
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function goPost(postId) {
  router.push({ name: 'post-list', query: { postId: String(postId) } })
}

function goTask(taskId) {
  router.push({ name: 'task-detail', params: { taskId: String(taskId) } })
}

function goTasks() {
  router.push({ name: 'task-list' })
}

function goPosts() {
  router.push({ name: 'post-list' })
}

onMounted(async () => {
  try {
    const result = await api.getHomeRecommendations()
    hotPosts.value = Array.isArray(result?.hotPosts) ? result.hotPosts : []
    latestTasks.value = Array.isArray(result?.latestTasks) ? result.latestTasks : []
  } catch (err) {
    error.value = err.message || '推荐内容加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <DashboardShell active-label="首页推荐">
    <main
      class="home-page"
      :aria-busy="loading"
    >
      <!-- Hero banner -->
      <section class="hero-banner">
        <div class="hero-content">
          <div class="hero-icon">
            <Sparkles
              :size="36"
              aria-hidden="true"
            />
          </div>
          <div>
            <p class="hero-kicker">
              校园生活一站通
            </p>
            <h1>欢迎来到 CampusBridge</h1>
            <span class="hero-subtitle">浏览热门帖子、发现最新任务，让校园协作更高效。</span>
          </div>
        </div>
        <div class="hero-actions">
          <button
            class="primary-action"
            type="button"
            @click="goPosts"
          >
            <BookOpen
              :size="18"
              aria-hidden="true"
            />
            浏览帖子
          </button>
          <button
            class="ghost-action"
            type="button"
            @click="goTasks"
          >
            <Briefcase
              :size="18"
              aria-hidden="true"
            />
            查看任务
          </button>
        </div>
      </section>

      <!-- Loading state -->
      <div
        v-if="loading"
        class="loading-state"
      >
        <LoaderCircle
          class="spinning"
          :size="28"
          aria-hidden="true"
        />
        <span>正在加载推荐内容</span>
      </div>

      <!-- Error state -->
      <div
        v-else-if="error"
        class="page-feedback page-feedback--danger"
      >
        {{ error }}
      </div>

      <template v-else>
        <!-- Quick nav cards -->
        <section class="quick-nav">
          <button
            class="quick-nav-card"
            type="button"
            @click="goPosts"
          >
            <BookOpen
              :size="24"
              aria-hidden="true"
            />
            <div>
              <strong>帖子广场</strong>
              <span>问询、闲置、组队、社交</span>
            </div>
            <ArrowRight
              :size="18"
              aria-hidden="true"
            />
          </button>
          <button
            class="quick-nav-card"
            type="button"
            @click="goTasks"
          >
            <Briefcase
              :size="24"
              aria-hidden="true"
            />
            <div>
              <strong>任务大厅</strong>
              <span>跑腿代取、代办委托</span>
            </div>
            <ArrowRight
              :size="18"
              aria-hidden="true"
            />
          </button>
        </section>

        <!-- Hot posts -->
        <section class="home-section">
          <div class="section-header">
            <div class="section-title">
              <Flame
                :size="22"
                aria-hidden="true"
              />
              <div>
                <h2>热门帖子</h2>
                <p>按互动热度与时间衰减推荐</p>
              </div>
            </div>
            <button
              class="ghost-action"
              type="button"
              @click="goPosts"
            >
              查看全部
            </button>
          </div>

          <div
            v-if="!hotPosts.length"
            class="empty-state"
          >
            <BookOpen
              :size="32"
              aria-hidden="true"
            />
            <p>暂无热门帖子</p>
          </div>

          <div
            v-else
            class="home-card-grid"
          >
            <button
              v-for="post in hotPosts"
              :key="post.id"
              class="home-card"
              type="button"
              @click="goPost(post.id)"
            >
              <div class="home-card-header">
                <span class="home-card-category">{{ post.category }}</span>
                <span class="home-card-stats">
                  <span>
                    <MessageCircle
                      :size="13"
                      aria-hidden="true"
                    />
                    {{ post.replyCount || 0 }}
                  </span>
                  <span>
                    <ThumbsUp
                      :size="13"
                      aria-hidden="true"
                    />
                    {{ post.likeCount || 0 }}
                  </span>
                </span>
              </div>
              <h3 class="home-card-title">
                {{ post.title || '未命名帖子' }}
              </h3>
              <p class="home-card-content">
                {{ compactContent(post.content) }}
              </p>
              <div class="home-card-footer">
                <span>{{ authorCopy(post.authorId, post.authorName) }}</span>
                <span>
                  <Clock3
                    :size="13"
                    aria-hidden="true"
                  />
                  {{ formatDateTime(post.createdAt) }}
                </span>
              </div>
            </button>
          </div>
        </section>

        <!-- Latest tasks -->
        <section class="home-section">
          <div class="section-header">
            <div class="section-title">
              <Briefcase
                :size="22"
                aria-hidden="true"
              />
              <div>
                <h2>最新任务</h2>
                <p>尚未截止、等待接单的公开任务</p>
              </div>
            </div>
            <button
              class="ghost-action"
              type="button"
              @click="goTasks"
            >
              查看全部
            </button>
          </div>

          <div
            v-if="!latestTasks.length"
            class="empty-state"
          >
            <Briefcase
              :size="32"
              aria-hidden="true"
            />
            <p>暂无可接任务</p>
          </div>

          <div
            v-else
            class="home-card-grid"
          >
            <button
              v-for="task in latestTasks"
              :key="task.id"
              class="home-card home-card--task"
              type="button"
              @click="goTask(task.id)"
            >
              <div class="home-card-header">
                <StatusBadge
                  :label="taskCategoryLabels[task.category] || task.category"
                  :tone="taskCategoryTones[task.category] || 'info'"
                />
                <span
                  v-if="task.location"
                  class="home-card-location"
                >
                  <MapPin
                    :size="13"
                    aria-hidden="true"
                  />
                  {{ task.campus }} · {{ task.location }}
                </span>
              </div>
              <h3 class="home-card-title">
                {{ task.title || '未命名任务' }}
              </h3>
              <p class="home-card-content">
                {{ compactContent(task.description) }}
              </p>
              <div class="home-card-footer">
                <span>{{ authorCopy(task.requesterId, null) }}</span>
                <span>
                  <Clock3
                    :size="13"
                    aria-hidden="true"
                  />
                  截止 {{ formatDateTime(task.deadline) }}
                </span>
              </div>
            </button>
          </div>
        </section>
      </template>
    </main>
  </DashboardShell>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 22px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

.hero-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--color-brand) 0%, var(--color-brand-hover) 100%);
  color: #fff;
  flex-wrap: wrap;
}

.hero-content {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.hero-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.18);
  flex-shrink: 0;
}

.hero-kicker {
  margin: 0 0 4px;
  font-size: 0.85rem;
  font-weight: 700;
  opacity: 0.85;
}

.hero-banner h1 {
  margin: 0 0 6px;
  font-size: 1.5rem;
  font-weight: 900;
}

.hero-subtitle {
  font-size: 0.92rem;
  opacity: 0.85;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.hero-actions .primary-action {
  background: var(--color-surface);
  color: var(--color-brand);
  border: none;
}

.hero-actions .ghost-action {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.3);
}

.hero-actions .ghost-action:hover {
  background: rgba(255, 255, 255, 0.25);
}

.quick-nav {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.quick-nav-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  text-align: left;
  width: 100%;
}

.quick-nav-card:hover {
  border-color: var(--color-brand-border);
  box-shadow: 0 2px 8px var(--shadow-card);
}

.quick-nav-card div {
  display: grid;
  gap: 2px;
  flex: 1;
}

.quick-nav-card strong {
  font-size: 0.95rem;
  color: var(--color-text);
}

.quick-nav-card span {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.quick-nav-card svg:last-child {
  color: var(--color-text-muted);
}

.home-section {
  display: grid;
  gap: 14px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title h2 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 900;
  color: var(--color-text);
}

.section-title p {
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.home-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.home-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  text-align: left;
  width: 100%;
}

.home-card:hover {
  border-color: var(--color-brand-border);
  box-shadow: 0 4px 12px var(--shadow-card);
}

.home-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.home-card-category {
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.home-card-stats {
  display: flex;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.home-card-stats span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.home-card-location {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.home-card-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 800;
  color: var(--color-text);
  line-height: 1.4;
}

.home-card-content {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.home-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.home-card-footer span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.home-card--task {
  border-left: 3px solid var(--color-brand);
}

@media (max-width: 768px) {
  .hero-banner {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions button {
    flex: 1;
  }

  .home-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
