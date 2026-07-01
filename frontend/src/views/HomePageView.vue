<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bookmark,
  BookOpen,
  Briefcase,
  Clock3,
  Flame,
  Grid2X2,
  LayoutDashboard,
  LoaderCircle,
  MapPin,
  MessageCircle,
  ThumbsUp,
  UserRound,
  X
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import ModuleTopbar from '../components/layout/ModuleTopbar.vue'
import ModuleEntryCard from '../components/layout/ModuleEntryCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TaskDetailModal from '../components/TaskDetailModal.vue'
import { POST_STATUS_OPTIONS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'
import { useGuestAction } from '../composables/useGuestAction'

const router = useRouter()
const { isAuthenticated, currentUserId } = useAuth()
const { showToast } = useToast()
const { requireLoginAction } = useGuestAction()

const loading = ref(true)
const hotPosts = ref([])
const latestTasks = ref([])
const error = ref('')

/* ---- task detail modal ---- */
const selectedTaskId = ref(null)

/* ---- post detail modal ---- */
const postModalOpen = ref(false)
const postModalLoading = ref(false)
const postModalData = ref(null)
const postModalError = ref('')

const categoryLabelMap = {
  INQUIRY_HELP: '问询求助',
  SECOND_HAND: '二手闲置',
  TEAM_FRIEND: '组队搭子',
  CAMPUS_SOCIAL: '校园交流'
}

function postCategoryLabel(category) {
  return categoryLabelMap[category] || category || '未分类'
}

function postStatusMeta(status) {
  return POST_STATUS_OPTIONS.find((o) => o.value === status) || { value: status, label: status || '未知', tone: 'muted' }
}

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
  openPostDetail(postId)
}

function goTask(taskId) {
  selectedTaskId.value = taskId
}

/* ---- post detail modal ---- */
async function openPostDetail(postId) {
  if (!postId) return
  postModalOpen.value = true
  postModalLoading.value = true
  postModalError.value = ''
  postModalData.value = null
  try {
    const result = await api.getPostDetail(postId)
    postModalData.value = normalizePostDetail(result)
  } catch (err) {
    postModalError.value = err.message || '帖子详情加载失败'
  } finally {
    postModalLoading.value = false
  }
}

function closePostModal() {
  postModalOpen.value = false
  postModalData.value = null
  postModalError.value = ''
}

function normalizePostDetail(result) {
  if (!result) return null
  if (result.post) {
    return {
      post: result.post,
      replies: Array.isArray(result.replies) ? result.replies : [],
      likedByCurrentUser: Boolean(result.likedByCurrentUser),
      markedByCurrentUser: Boolean(result.markedByCurrentUser),
      attachments: Array.isArray(result.attachments) ? result.attachments : [],
      acceptedReplyId: result.acceptedReplyId || null
    }
  }
  return {
    post: result,
    replies: [],
    likedByCurrentUser: false,
    markedByCurrentUser: false,
    attachments: [],
    acceptedReplyId: null
  }
}

async function togglePostLike() {
  if (!requireLoginAction('点赞')) return
  const detail = postModalData.value
  if (!detail?.post?.id) return
  const post = detail.post
  const liked = detail.likedByCurrentUser
  detail.likedByCurrentUser = !liked
  post.likeCount = Math.max(0, (Number(post.likeCount) || 0) + (liked ? -1 : 1))
  try {
    if (liked) {
      await api.cancelPostInteraction(post.id, 'LIKE')
    } else {
      await api.createPostInteraction(post.id, { type: 'LIKE' })
    }
  } catch {
    detail.likedByCurrentUser = liked
    post.likeCount = Math.max(0, (Number(post.likeCount) || 0) + (liked ? 1 : -1))
  }
}

async function togglePostMark() {
  if (!requireLoginAction('收藏')) return
  const detail = postModalData.value
  if (!detail?.post?.id) return
  const post = detail.post
  const marked = detail.markedByCurrentUser
  detail.markedByCurrentUser = !marked
  post.markCount = Math.max(0, (Number(post.markCount) || 0) + (marked ? -1 : 1))
  try {
    if (marked) {
      await api.cancelPostInteraction(post.id, 'MARK')
    } else {
      await api.createPostInteraction(post.id, { type: 'MARK' })
    }
  } catch {
    detail.markedByCurrentUser = marked
    post.markCount = Math.max(0, (Number(post.markCount) || 0) + (marked ? 1 : -1))
  }
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
      <!-- Module topbar -->
      <ModuleTopbar
        eyebrow="校园生活一站通"
        title="发现校园协作"
        description="浏览热门帖子、发现最新任务，让校园协作更高效。"
      >
        <ModuleEntryCard
          :to="{ name: 'post-list' }"
          :icon="BookOpen"
          title="浏览校园帖子"
          description="查看校园讨论与经验分享"
        />
        <ModuleEntryCard
          :to="{ name: 'task-list' }"
          :icon="Grid2X2"
          title="任务广场"
          description="浏览公开任务"
        />
        <ModuleEntryCard
          :to="{ name: 'workspace' }"
          :icon="LayoutDashboard"
          title="工作台概览"
          description="账户与认证状态"
        />
      </ModuleTopbar>

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
              class="secondary-action"
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
              class="secondary-action"
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

    <!-- Post detail modal -->
    <div
      v-if="postModalOpen"
      class="workflow-modal-layer"
      @click.self="closePostModal"
    >
      <section
        class="panel-card detail-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="home-post-detail-title"
      >
        <div
          v-if="postModalLoading"
          class="loading-state"
        >
          <LoaderCircle class="spinning" :size="28" aria-hidden="true" />
          <span>正在加载帖子详情</span>
        </div>

        <template v-else-if="postModalData?.post">
          <div class="detail-card-header">
            <div>
              <span class="section-kicker">{{ postCategoryLabel(postModalData.post.category) }}</span>
              <h2 id="home-post-detail-title">{{ postModalData.post.title || '未命名帖子' }}</h2>
            </div>
            <div class="detail-header-actions">
              <StatusBadge
                :status="postModalData.post.status"
                :label="postStatusMeta(postModalData.post.status).label"
                :tone="postStatusMeta(postModalData.post.status).tone"
              />
              <button class="icon-action" type="button" aria-label="关闭" @click="closePostModal">
                <X :size="18" aria-hidden="true" />
              </button>
            </div>
          </div>

          <div v-if="postModalError" class="page-feedback page-feedback--danger compact-feedback">
            {{ postModalError }}
          </div>

          <dl class="detail-facts">
            <div>
              <dt>发布者</dt>
              <dd>{{ authorCopy(postModalData.post.authorId, postModalData.post.authorName) }}</dd>
            </div>
            <div>
              <dt>发布时间</dt>
              <dd>{{ formatDateTime(postModalData.post.createdAt) }}</dd>
            </div>
            <div>
              <dt>回复</dt>
              <dd>{{ postModalData.post.replyCount || postModalData.replies.length }}</dd>
            </div>
            <div>
              <dt>互动</dt>
              <dd>{{ (postModalData.post.likeCount || 0) + (postModalData.post.markCount || 0) }}</dd>
            </div>
          </dl>

          <div class="modal-interaction-bar">
            <button
              class="like-button"
              :class="{ 'like-button--active': postModalData.likedByCurrentUser }"
              type="button"
              @click="togglePostLike"
            >
              <ThumbsUp :size="17" :fill="postModalData.likedByCurrentUser ? 'currentColor' : 'none'" aria-hidden="true" />
              <span class="like-count">{{ postModalData.post.likeCount || 0 }}</span>
            </button>
            <button
              class="like-button"
              :class="{ 'like-button--active': postModalData.markedByCurrentUser }"
              type="button"
              @click="togglePostMark"
            >
              <Bookmark :size="17" :fill="postModalData.markedByCurrentUser ? 'currentColor' : 'none'" aria-hidden="true" />
              <span class="like-count">{{ postModalData.post.markCount || 0 }}</span>
            </button>
          </div>

          <section v-if="postModalData.attachments.length" class="detail-section">
            <h3>图片</h3>
            <div class="detail-image-grid">
              <a
                v-for="(att, idx) in postModalData.attachments"
                :key="att.id || idx"
                :href="att.fileUrl || att.url"
                target="_blank" rel="noreferrer"
                class="detail-image-grid__link"
              >
                <img :src="att.fileUrl || att.url" :alt="att.fileName || `图片 ${idx + 1}`" class="detail-image-grid__img" loading="lazy">
              </a>
            </div>
          </section>

          <section class="detail-section">
            <h3>正文</h3>
            <p class="detail-content-text">{{ postModalData.post.content || '暂无正文' }}</p>
          </section>

          <section class="detail-section">
            <div class="reply-heading">
              <h3>回复</h3>
              <span>{{ postModalData.replies.length }} 条</span>
            </div>
            <div v-if="!postModalData.replies.length" class="reply-empty">暂无回复</div>
            <div v-else class="reply-list">
              <article
                v-for="reply in postModalData.replies"
                :key="reply.id"
                class="reply-card"
                :class="{ accepted: String(reply.id) === String(postModalData.acceptedReplyId) }"
              >
                <div class="reply-card-top">
                  <span><UserRound :size="16" aria-hidden="true" /> {{ authorCopy(reply.authorId, reply.authorName) }}</span>
                  <small>{{ formatDateTime(reply.createdAt) }}</small>
                </div>
                <p>{{ reply.content }}</p>
                <div class="reply-actions">
                  <span v-if="String(reply.id) === String(postModalData.acceptedReplyId)" class="accepted-tag">已采纳</span>
                </div>
              </article>
            </div>
          </section>
        </template>

        <div v-else class="empty-state detail-empty">
          <BookOpen :size="40" aria-hidden="true" />
          <h3>帖子详情暂不可用</h3>
          <p>请关闭弹窗后重新打开试试。</p>
        </div>
      </section>
    </div>

    <TaskDetailModal :task-id="selectedTaskId" @close="selectedTaskId = null" />
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

.secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-brand);
  border-radius: 8px;
  background: transparent;
  color: var(--color-brand);
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
  text-decoration: none;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.secondary-action:hover:not(:disabled) {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-hover);
}

/* ---- modal shared ---- */
.workflow-modal-layer {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: 22px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(10px);
}

.panel-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.detail-modal {
  width: min(720px, 92vw);
  max-height: 88vh;
  overflow-y: auto;
  display: grid;
  gap: 16px;
  padding: 22px;
}

.detail-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.detail-card-header h2 {
  margin: 0;
  font-size: 1.08rem;
  color: var(--color-text);
}

.detail-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.section-kicker {
  color: var(--color-brand);
  font-size: 0.82rem;
  font-weight: 900;
}

.icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  min-height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-brand);
  cursor: pointer;
  transition: background-color 180ms ease;
}

.icon-action:hover {
  background: var(--color-brand-soft);
}

/* ---- detail facts ---- */
.detail-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.detail-facts div {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.detail-facts dt {
  color: var(--color-text-muted);
  font-size: 0.78rem;
  font-weight: 600;
}

.detail-facts dt::after {
  content: '\FF1A';
}

.detail-facts dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 700;
  font-size: 0.88rem;
  line-height: 1.45;
}

/* ---- interaction bar ---- */
.modal-interaction-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.like-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms ease, border-color 150ms ease, color 150ms ease;
}

.like-button:hover {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
  color: var(--color-brand);
}

.like-button--active {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
  color: var(--color-brand);
}

.like-count {
  font-weight: 900;
}

/* ---- detail section ---- */
.detail-section {
  display: grid;
  gap: 10px;
}

.detail-section h3 {
  color: var(--color-text);
  font-size: 1rem;
  margin: 0;
}

.detail-content-text {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 1.02rem;
  color: var(--color-text);
  margin: 0;
}

/* ---- image grid ---- */
.detail-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.detail-image-grid__link {
  display: block;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  transition: border-color 180ms ease;
}

.detail-image-grid__link:hover {
  border-color: var(--color-brand-border);
}

.detail-image-grid__img {
  display: block;
  width: 100%;
  height: 140px;
  object-fit: cover;
}

/* ---- replies ---- */
.reply-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reply-heading span {
  color: var(--color-text-muted);
  font-weight: 900;
}

.reply-empty {
  padding: 14px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  text-align: center;
}

.reply-list {
  display: grid;
  gap: 10px;
}

.reply-card {
  display: grid;
  gap: 8px;
  padding: 13px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.reply-card.accepted {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
}

.reply-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.reply-card-top span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text);
  font-weight: 900;
}

.reply-card-top small {
  color: var(--color-text-muted);
}

.reply-card p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.reply-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.accepted-tag {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--color-success-bg);
  color: var(--color-success-text);
  font-size: 0.8rem;
  font-weight: 900;
}

/* ---- states ---- */
.loading-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 220px;
  color: var(--color-text-muted);
  font-weight: 900;
}

.empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  padding: 24px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: var(--color-text);
}

.empty-state p {
  margin: 0;
  max-width: 360px;
  line-height: 1.6;
}

.page-feedback--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  line-height: 1.55;
}

.compact-feedback {
  box-shadow: none;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- responsive ---- */
@media (max-width: 768px) {
  .home-card-grid {
    grid-template-columns: 1fr;
  }

  .detail-facts {
    grid-template-columns: 1fr;
  }

  .detail-image-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .detail-image-grid__img {
    height: 110px;
  }

  .workflow-modal-layer {
    padding: 12px;
  }

  .detail-modal {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }
}
</style>
