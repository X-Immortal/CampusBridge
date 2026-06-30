<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, CheckCircle2, Inbox, LoaderCircle, RefreshCw, Star } from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { useToast } from '../composables/useToast'
import { optionLabel, TASK_CATEGORY_OPTIONS, TASK_STATUS_OPTIONS, statusTone } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const { currentUserId } = useAuth()
const { showToast } = useToast()

const reviewLabels = {
  1: '体验较差',
  2: '低于预期',
  3: '基本完成',
  4: '值得信任',
  5: '非常可靠'
}

const reviewTags = ['准时响应', '说明清楚', '凭证完整', '沟通友好', '按标准完成', '愿意再次合作']

const tasks = ref([])
const selectedTaskId = ref('')
const loadingReviews = ref(false)

const reviewForm = reactive({
  rating: 5,
  comment: '',
  tags: []
})

const currentUser = computed(() => currentUserId.value)
const selectedTask = computed(() => tasks.value.find((task) => String(task.id) === String(selectedTaskId.value)) || tasks.value[0] || null)
const reviewee = computed(() => resolveReviewee(selectedTask.value))
const selectedTaskReviews = computed(() => selectedTask.value?.reviews || [])

const existingReview = computed(() => {
  const task = selectedTask.value
  if (!task) return null
  return task.reviews?.find(
    (review) =>
      String(review.reviewerId) === String(currentUser.value) &&
      String(review.revieweeId) === String(reviewee.value?.id)
  )
})

const canSubmitReview = computed(() => Boolean(selectedTask.value?.id && reviewee.value?.id && !existingReview.value))

const pendingReviewTasks = computed(() =>
  tasks.value.filter((task) => {
    const target = resolveReviewee(task)
    if (!target?.id) return false
    return !task.reviews?.some(
      (review) =>
        String(review.reviewerId) === String(currentUser.value) &&
        String(review.revieweeId) === String(target.id)
    )
  })
)

const reviewedTasks = computed(() => tasks.value.filter((task) => !pendingReviewTasks.value.includes(task)))
const averageGivenRating = computed(() => {
  const ownReviews = tasks.value
    .flatMap((task) => task.reviews || [])
    .filter((review) => String(review.reviewerId) === String(currentUser.value))
  if (!ownReviews.length) return '0.0'
  const average = ownReviews.reduce((sum, review) => sum + Number(review.rating || 0), 0) / ownReviews.length
  return average.toFixed(1)
})

function normalizePagedItems(result) {
  if (Array.isArray(result)) return result
  return result?.tasks || result?.items || result?.records || result?.content || result?.list || []
}

function normalizeTask(task = {}) {
  const id = task.id ?? task.taskId
  return {
    ...task,
    id,
    requesterId: task.requesterId ?? task.ownerId,
    providerId: task.providerId ?? task.helperId ?? task.acceptedApplicantId,
    requesterName: task.requesterName || task.ownerName || '求助同学',
    providerName: task.providerName || task.helperName || '帮助同学',
    status: task.status || 'COMPLETED',
    completedAt: task.completedAt || task.updatedAt || task.deadline || task.createdAt || '',
    reviews: Array.isArray(task.reviews) ? task.reviews : []
  }
}

function normalizeReview(review = {}, taskId) {
  return {
    id: review.id ?? `local-review-${Date.now()}`,
    taskId: review.taskId ?? taskId,
    reviewerId: review.reviewerId ?? currentUser.value,
    revieweeId: review.revieweeId,
    rating: Number(review.rating || 5),
    comment: review.comment || '',
    createdAt: review.createdAt || new Date().toISOString()
  }
}

function resolveReviewee(task) {
  if (!task) return null
  const userId = String(currentUser.value)
  if (task.requesterId !== undefined && String(task.requesterId) === userId) {
    return {
      id: task.providerId,
      name: task.providerName || `同学 ${task.providerId || ''}`.trim() || '帮助同学',
      role: '帮助方'
    }
  }
  if (task.providerId !== undefined && String(task.providerId) === userId) {
    return {
      id: task.requesterId,
      name: task.requesterName || `同学 ${task.requesterId || ''}`.trim() || '求助同学',
      role: '求助方'
    }
  }
  return {
    id: task.providerId || task.requesterId,
    name: task.providerName || task.requesterName || '协作同学',
    role: task.providerId ? '帮助方' : '求助方'
  }
}

function taskCategoryLabel(task = {}) {
  return optionLabel(TASK_CATEGORY_OPTIONS, task.category)
}

function taskStatusLabel(status) {
  return optionLabel(TASK_STATUS_OPTIONS, status)
}

function taskStatusTone(status) {
  return statusTone(status)
}

function formatDateTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function shortText(text, length = 72) {
  if (!text) return '暂无说明'
  return text.length > length ? `${text.slice(0, length)}...` : text
}

function resetReviewForm() {
  reviewForm.rating = 5
  reviewForm.comment = ''
  reviewForm.tags = []
}

function selectTask(task) {
  if (!task?.id) return
  selectedTaskId.value = String(task.id)
  resetReviewForm()
}

function toggleReviewTag(tag) {
  if (reviewForm.tags.includes(tag)) {
    reviewForm.tags = reviewForm.tags.filter((item) => item !== tag)
    return
  }
  reviewForm.tags = [...reviewForm.tags, tag]
}

async function loadReviews() {
  loadingReviews.value = true
  try {
    const [completedResult, reviewedResult] = await Promise.allSettled([
      api.getTasks({ status: 'COMPLETED', page: 1, size: 12 }),
      api.getTasks({ status: 'REVIEWED', page: 1, size: 12 })
    ])

    const rawTasks = [
      ...(completedResult.status === 'fulfilled' ? normalizePagedItems(completedResult.value) : []),
      ...(reviewedResult.status === 'fulfilled' ? normalizePagedItems(reviewedResult.value) : [])
    ]

    const myId = currentUserId.value
    const uniqueTasks = [...new Map(rawTasks.map((task) => [String(task.id ?? task.taskId), task])).values()]
      .filter((task) => task.id || task.taskId)
      .map(normalizeTask)
      .filter((task) => String(task.requesterId) === String(myId) || String(task.providerId) === String(myId))

    tasks.value = uniqueTasks
    if (uniqueTasks.length) await hydrateTaskReviews()

    if (!selectedTaskId.value || !tasks.value.some((task) => String(task.id) === String(selectedTaskId.value))) {
      selectedTaskId.value = String(pendingReviewTasks.value[0]?.id || tasks.value[0]?.id || '')
    }
  } catch {
    tasks.value = []
    selectedTaskId.value = ''
  } finally {
    loadingReviews.value = false
  }
}

async function hydrateTaskReviews() {
  const settled = await Promise.allSettled(
    tasks.value.map(async (task) => {
      const [reviewsResult, detailResult] = await Promise.allSettled([api.getTaskReviews(task.id), api.getTaskDetail(task.id)])
      const reviewItems =
        reviewsResult.status === 'fulfilled' && Array.isArray(reviewsResult.value)
          ? reviewsResult.value
          : detailResult.status === 'fulfilled' && Array.isArray(detailResult.value?.reviews)
            ? detailResult.value.reviews
            : task.reviews

      const detailTask =
        detailResult.status === 'fulfilled' && detailResult.value?.task
          ? normalizeTask(detailResult.value.task)
          : detailResult.status === 'fulfilled' && detailResult.value?.id
            ? normalizeTask(detailResult.value)
            : {}

      return {
        ...task,
        ...detailTask,
        id: task.id,
        reviews: (reviewItems || []).map((review) => normalizeReview(review, task.id))
      }
    })
  )

  tasks.value = settled.map((result, index) => (result.status === 'fulfilled' ? result.value : tasks.value[index]))
}

function appendReviewToTask(taskId, review) {
  tasks.value = tasks.value.map((task) => {
    if (String(task.id) !== String(taskId)) return task
    return {
      ...task,
      status: 'REVIEWED',
      reviews: [...(task.reviews || []), review]
    }
  })
}

async function submitReview() {
  if (!selectedTask.value?.id || !reviewee.value?.id || !canSubmitReview.value) return
  const comment = reviewForm.comment.trim()

  if (comment.length < 8) {
    showToast('请至少写 8 个字，说明本次协作是否符合预期。', { type: 'danger' })
    return
  }
  if (comment.length > 500) {
    showToast('文字评价最多 500 个字。', { type: 'danger' })
    return
  }

  loadingReviews.value = true
  const tagLine = reviewForm.tags.length ? `\n\n亮点标签：${reviewForm.tags.join('、')}` : ''
  const payload = {
    revieweeId: reviewee.value.id,
    rating: reviewForm.rating,
    comment: `${comment}${tagLine}`.slice(0, 500)
  }

  try {
    const result = await api.createTaskReview(selectedTask.value.id, payload)
    const review = normalizeReview(result || payload, selectedTask.value.id)
    appendReviewToTask(selectedTask.value.id, review)
    resetReviewForm()
    showToast('评价已提交。')
  } catch (error) {
    showToast(error.message || '评价提交失败，请稍后重试。', { type: 'danger' })
  } finally {
    loadingReviews.value = false
  }
}

onMounted(loadReviews)
</script>

<template>
  <DashboardShell active-label="评价中心">
    <main class="review-page">
      <section class="page-title-card">
        <div class="title-icon">
          <Star
            :size="34"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>评价中心</p>
          <h1>把任务完成后的互评单独管理</h1>
          <span>这里仅处理任务评分、评价记录和待评价提醒，不再混入通知模块。</span>
        </div>
      </section>

      <section
        class="metric-strip"
        aria-label="评价概览"
      >
        <article class="metric-tile metric-tile--accent">
          <span>待评价任务</span>
          <strong>{{ pendingReviewTasks.length }}</strong>
        </article>
        <article class="metric-tile metric-tile--success">
          <span>已完成评价</span>
          <strong>{{ reviewedTasks.length }}</strong>
        </article>
        <article class="metric-tile metric-tile--info">
          <span>我给出的均分</span>
          <strong>{{ averageGivenRating }}</strong>
        </article>
      </section>

      <section class="review-layout">
        <aside class="panel-card task-review-list">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                互评任务
              </p>
              <h2>选择一次已完成协作</h2>
            </div>
            <button
              class="icon-action"
              type="button"
              aria-label="刷新评价任务"
              :disabled="loadingReviews"
              @click="loadReviews"
            >
              <RefreshCw
                :class="{ spinning: loadingReviews }"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>

          <div
            v-if="loadingReviews && !tasks.length"
            class="loading-state"
            role="status"
          >
            <LoaderCircle
              class="spinning"
              :size="24"
              aria-hidden="true"
            />
            <span>正在同步评价任务...</span>
          </div>

          <div
            v-else-if="!tasks.length"
            class="empty-state"
          >
            <Inbox
              :size="30"
              aria-hidden="true"
            />
            <h3>暂无已完成任务</h3>
            <p>你发布或承接的任务完成后，会出现在这里供双方互评。</p>
          </div>

          <div
            v-else
            class="review-task-list"
          >
            <button
              v-for="task in tasks"
              :key="task.id"
              class="review-task-card"
              :class="{ active: String(task.id) === String(selectedTask?.id) }"
              type="button"
              @click="selectTask(task)"
            >
              <span class="review-task-topline">
                <strong>{{ task.title }}</strong>
                <StatusBadge
                  :label="taskStatusLabel(task.status)"
                  :tone="taskStatusTone(task.status)"
                />
              </span>
              <span class="review-task-meta">
                <span>{{ taskCategoryLabel(task) }}</span>
                <span>{{ task.campus || '未设置校区' }} · {{ task.location || '未设置地点' }}</span>
              </span>
              <span class="review-task-footer">
                <span>完成于 {{ formatDateTime(task.completedAt) }}</span>
                <span v-if="resolveReviewee(task)">评价 {{ resolveReviewee(task).role }}</span>
              </span>
            </button>
          </div>
        </aside>

        <section class="panel-card review-form-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                评分评价
              </p>
              <h2>{{ selectedTask?.title || '选择任务后填写评价' }}</h2>
            </div>
            <StatusBadge
              :label="existingReview ? '已评价' : '待评价'"
              :tone="existingReview ? 'success' : 'warning'"
            />
          </div>

          <div
            v-if="!selectedTask"
            class="empty-state"
          >
            <Star
              :size="30"
              aria-hidden="true"
            />
            <h3>选择任务后填写评价</h3>
            <p>从左侧互评任务列表中选择一项协作即可开始。</p>
          </div>

          <template v-else>
            <div class="review-context">
              <div>
                <span>评价对象</span>
                <strong>{{ reviewee?.name || '协作同学' }}</strong>
                <small>{{ reviewee?.role || '协作方' }}</small>
              </div>
              <div>
                <span>完成标准</span>
                <strong>{{ shortText(selectedTask.completionStandard, 48) }}</strong>
                <small>{{ selectedTask.rewardDescription || '奖励方式未设置' }}</small>
              </div>
            </div>

            <form
              v-if="canSubmitReview"
              class="review-form"
              @submit.prevent="submitReview"
            >
              <fieldset class="rating-field">
                <legend>本次协作评分</legend>
                <div
                  class="star-row"
                  role="radiogroup"
                  aria-label="本次协作评分"
                >
                  <button
                    v-for="score in 5"
                    :key="score"
                    type="button"
                    class="star-button"
                    :class="{ active: score <= reviewForm.rating }"
                    :aria-pressed="score === reviewForm.rating"
                    @click="reviewForm.rating = score"
                  >
                    <Star
                      :size="24"
                      aria-hidden="true"
                    />
                  </button>
                </div>
                <span>{{ reviewLabels[reviewForm.rating] }}</span>
              </fieldset>

              <fieldset class="tag-field">
                <legend>协作印象</legend>
                <div class="tag-list">
                  <button
                    v-for="tag in reviewTags"
                    :key="tag"
                    type="button"
                    class="tag-chip"
                    :class="{ active: reviewForm.tags.includes(tag) }"
                    :aria-pressed="reviewForm.tags.includes(tag)"
                    @click="toggleReviewTag(tag)"
                  >
                    <Check
                      v-if="reviewForm.tags.includes(tag)"
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ tag }}
                  </button>
                </div>
              </fieldset>

              <label
                class="field"
                for="review-comment"
              >
                <span>文字评价</span>
                <textarea
                  id="review-comment"
                  v-model.trim="reviewForm.comment"
                  maxlength="500"
                  placeholder="例如：是否准时、沟通是否顺畅、是否符合完成标准。"
                />
                <small>{{ reviewForm.comment.length }} / 500</small>
              </label>

              <button
                class="primary-action"
                type="submit"
                :disabled="loadingReviews"
              >
                <LoaderCircle
                  v-if="loadingReviews"
                  class="spinning"
                  :size="18"
                  aria-hidden="true"
                />
                <CheckCircle2
                  v-else
                  :size="18"
                  aria-hidden="true"
                />
                提交评价
              </button>
            </form>

            <section
              v-else-if="existingReview"
              class="review-complete-state"
            >
              <CheckCircle2
                :size="42"
                aria-hidden="true"
              />
              <strong>你已完成评价</strong>
              <p>{{ existingReview.comment }}</p>
            </section>
          </template>
        </section>

        <section class="panel-card review-history-panel">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                评价记录
              </p>
              <h2>当前任务的互评历史</h2>
            </div>
          </div>

          <div
            v-if="!selectedTask"
            class="empty-state empty-state--compact"
          >
            <Star
              :size="28"
              aria-hidden="true"
            />
            <h3>先选择一个任务</h3>
            <p>选择任务后，这里会显示双方评价记录。</p>
          </div>

          <div
            v-else-if="!selectedTaskReviews.length"
            class="empty-state empty-state--compact"
          >
            <Inbox
              :size="28"
              aria-hidden="true"
            />
            <h3>还没有评价记录</h3>
            <p>提交第一条评价后，这里会立即更新。</p>
          </div>

          <div
            v-else
            class="history-list"
          >
            <article
              v-for="review in selectedTaskReviews"
              :key="review.id"
              class="history-review"
            >
              <div class="history-review-head">
                <div>
                  <strong>{{ String(review.reviewerId) === String(currentUser) ? '我给出的评价' : '对方给出的评价' }}</strong>
                  <small>{{ formatDateTime(review.createdAt) }}</small>
                </div>
                <span class="review-score">{{ review.rating }} / 5</span>
              </div>
              <p>{{ review.comment || '未填写文字评价。' }}</p>
            </article>
          </div>
        </section>
      </section>
    </main>
  </DashboardShell>
</template>

<style scoped>
.review-page {
  display: grid;
  gap: 18px;
}

.page-title-card,
.panel-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 18px 36px var(--shadow-card);
}

.page-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 24px;
  padding: 26px 32px;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 24px;
  background: var(--color-accent-bg);
  color: var(--color-accent);
}

.title-copy {
  display: grid;
  gap: 6px;
}

.title-copy p,
.section-kicker {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.86rem;
  font-weight: 800;
}

.title-copy h1,
.panel-heading h2,
.empty-state h3,
.review-complete-state strong {
  margin: 0;
  color: var(--color-text);
}

.title-copy span,
.empty-state p,
.review-complete-state p {
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.metric-tile {
  display: grid;
  gap: 8px;
  min-height: 82px;
  padding: 16px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
}

.metric-tile span {
  color: var(--color-text-muted);
  font-size: 0.86rem;
  font-weight: 800;
}

.metric-tile strong {
  color: var(--color-text);
  font-size: 1.65rem;
  line-height: 1;
}

.metric-tile--accent {
  background: var(--color-accent-bg);
}

.metric-tile--success {
  background: var(--color-success-bg);
}

.metric-tile--info {
  background: var(--color-brand-soft);
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.78fr) minmax(360px, 1.08fr) minmax(280px, 0.82fr);
  gap: 18px;
  align-items: start;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 20px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.icon-action,
.primary-action,
.star-button,
.tag-chip,
.review-task-card {
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease,
    color 180ms ease;
}

.icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  min-height: 40px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.icon-action:hover,
.primary-action:hover:not(:disabled) {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-button);
}

.primary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-info);
  border-radius: 8px;
  background: var(--color-info);
  color: var(--color-surface);
  font-weight: 800;
}

.primary-action:hover:not(:disabled) {
  background: var(--color-info-text);
}

.loading-state,
.empty-state,
.review-complete-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 210px;
  padding: 22px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state--compact {
  min-height: 150px;
}

.review-task-list,
.history-list {
  display: grid;
  gap: 10px;
}

.review-task-card {
  display: grid;
  gap: 11px;
  width: 100%;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
}

.review-task-card:hover,
.review-task-card.active {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-card);
}

.review-task-topline,
.review-task-meta,
.review-task-footer,
.history-review-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.review-task-meta,
.review-task-footer,
.review-context span,
.review-context small,
.history-review small,
.field small,
.review-score {
  color: var(--color-text-muted);
}

.review-context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.review-context div {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 8px;
  background: var(--color-bg);
}

.review-context strong {
  color: var(--color-text);
}

.review-form {
  display: grid;
  gap: 18px;
}

.rating-field,
.tag-field,
.field {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  border: 0;
}

.rating-field legend,
.tag-field legend,
.field span {
  color: var(--color-text);
  font-weight: 800;
}

.star-row,
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.star-button,
.tag-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
}

.star-button.active,
.tag-chip.active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.field textarea {
  min-height: 140px;
  padding: 14px;
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  outline: none;
  resize: vertical;
  background: var(--color-surface);
  color: var(--color-text);
  line-height: 1.6;
}

.history-review {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.history-review p {
  margin: 0;
  color: var(--color-text);
  line-height: 1.6;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1200px) {
  .review-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .page-title-card,
  .metric-strip,
  .review-context {
    grid-template-columns: 1fr;
  }
}
</style>
