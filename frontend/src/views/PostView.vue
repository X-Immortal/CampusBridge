<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookOpen,
  Bookmark,
  CheckCircle2,
  Clock3,
  FileText,
  Filter,
  LoaderCircle,
  MessageCircle,
  PlusCircle,
  RefreshCw,
  Search,
  Send,
  Star,
  Tag,
  ThumbsUp,
  UserRound,
  X
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { optionLabel, POST_CATEGORY_OPTIONS, POST_STATUS_OPTIONS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, currentUserId } = useAuth()

const DEFAULT_PAGE_SIZE = 8

const categoryIconMap = {
  INQUIRY_HELP: MessageCircle,
  SECOND_HAND: Bookmark,
  TEAM_FRIEND: Star,
  CAMPUS_SOCIAL: BookOpen
}

const filters = reactive(readFiltersFromRoute())
const pagination = reactive({
  page: filters.page,
  size: filters.size,
  total: 0
})
const posts = ref([])
const selectedPostId = ref('')
const postDetail = ref(null)
const listLoading = ref(false)
const detailLoading = ref(false)
const listError = ref('')
const detailError = ref('')
const feedback = ref('')
const feedbackType = ref('success')
const publishDialogOpen = ref(false)
const publishing = ref(false)
const publishErrors = reactive({})
const postForm = reactive(createDefaultPostForm())

const replyContent = ref('')
const replySubmitting = ref(false)
const replyError = ref('')

let listRequestId = 0
let detailRequestId = 0

const categoryCards = computed(() =>
  POST_CATEGORY_OPTIONS.map((option) => ({
    ...option,
    icon: categoryIconMap[option.value] || Tag
  }))
)
const activeFilterCount = computed(() =>
  [filters.keyword.trim(), filters.category, filters.status !== 'PUBLISHED' ? filters.status : ''].filter(Boolean)
    .length
)
const totalPages = computed(() => Math.max(1, Math.ceil(pagination.total / pagination.size)))
const selectedListPost = computed(
  () => posts.value.find((post) => String(post.id) === selectedPostId.value) || null
)
const detailPost = computed(() => postDetail.value?.post || selectedListPost.value || null)
const detailReplies = computed(() => postDetail.value?.replies || [])
const visibleReplyCount = computed(() =>
  posts.value.reduce((sum, post) => sum + numericValue(post.replyCount), 0)
)
const visibleLikeCount = computed(() =>
  posts.value.reduce((sum, post) => sum + numericValue(post.likeCount), 0)
)
const myVisiblePostCount = computed(() =>
  posts.value.filter(
    (post) =>
      post.authorId !== undefined &&
      post.authorId !== null &&
      currentUserId.value !== undefined &&
      currentUserId.value !== null &&
      String(post.authorId) === String(currentUserId.value)
  ).length
)
const resultRange = computed(() => {
  if (!pagination.total) return '0'
  const start = (pagination.page - 1) * pagination.size + 1
  const end = Math.min(pagination.page * pagination.size, pagination.total)
  return `${start}-${end}`
})
const canPublish = computed(() => isAuthenticated.value)
const previewTitle = computed(() => postForm.title.trim() || '写下一个清晰的帖子标题')
const previewContent = computed(() =>
  postForm.content.trim() || '正文可以说明背景、你想获得的帮助、可提供的信息或希望大家讨论的方向。'
)

function firstValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function readQuery(key, fallback) {
  const value = firstValue(route.query[key])
  return value === undefined || value === null ? fallback : String(value)
}

function readNumberQuery(key, fallback) {
  const value = Number(firstValue(route.query[key]))
  return Number.isFinite(value) && value > 0 ? value : fallback
}

function readFiltersFromRoute() {
  return {
    keyword: readQuery('keyword', ''),
    category: readQuery('category', ''),
    status: readQuery('status', 'PUBLISHED'),
    page: readNumberQuery('page', 1),
    size: readNumberQuery('size', DEFAULT_PAGE_SIZE)
  }
}

function createDefaultPostForm() {
  return {
    category: 'INQUIRY_HELP',
    title: '',
    content: ''
  }
}

function numericValue(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function normalizePost(post = {}) {
  const id = post.id ?? post.postId
  return {
    ...post,
    id,
    authorId: post.authorId ?? post.userId,
    category: post.category || 'INQUIRY_HELP',
    status: post.status || 'PUBLISHED',
    replyCount: numericValue(post.replyCount),
    likeCount: numericValue(post.likeCount),
    markCount: numericValue(post.markCount)
  }
}

function normalizeReply(reply = {}) {
  return {
    ...reply,
    id: reply.id ?? reply.replyId,
    authorId: reply.authorId ?? reply.userId,
    likeCount: numericValue(reply.likeCount)
  }
}

function normalizePagedPosts(result) {
  if (Array.isArray(result)) return result
  return result?.items || result?.posts || result?.records || result?.content || result?.list || []
}

function normalizePostDetail(result) {
  if (!result) return null

  if (result.post) {
    return {
      ...result,
      post: normalizePost(result.post),
      replies: Array.isArray(result.replies) ? result.replies.map(normalizeReply) : []
    }
  }

  return {
    post: normalizePost(result),
    replies: [],
    likedByCurrentUser: false,
    markedByCurrentUser: false,
    acceptedReplyId: result.acceptedReplyId || null
  }
}

function applyPostResult(result) {
  const items = normalizePagedPosts(result).map(normalizePost)
  posts.value = items
  pagination.page = Number(result?.page) || filters.page
  pagination.size = Number(result?.size) || filters.size
  pagination.total = Number(result?.total) || items.length
}

function buildPostQuery() {
  return {
    keyword: filters.keyword.trim(),
    category: filters.category,
    status: filters.status,
    page: filters.page,
    size: filters.size
  }
}

function buildRouteQuery() {
  const query = {}
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.category) query.category = filters.category
  if (filters.status && filters.status !== 'PUBLISHED') query.status = filters.status
  if (filters.page > 1) query.page = String(filters.page)
  if (filters.size !== DEFAULT_PAGE_SIZE) query.size = String(filters.size)
  return query
}

async function syncRoute() {
  await router.replace({
    name: 'post-list',
    query: buildRouteQuery()
  })
}

async function refreshPosts() {
  const requestId = ++listRequestId
  listLoading.value = true
  listError.value = ''

  try {
    const result = await api.getPosts(buildPostQuery())
    if (requestId !== listRequestId) return

    applyPostResult(result || {})

    const nextSelected =
      posts.value.find((post) => String(post.id) === selectedPostId.value) || posts.value[0] || null
    selectedPostId.value = nextSelected?.id ? String(nextSelected.id) : ''

    if (nextSelected?.id) {
      await loadPostDetail(nextSelected.id)
    } else {
      postDetail.value = null
      detailError.value = ''
    }
  } catch (error) {
    if (requestId !== listRequestId) return
    posts.value = []
    selectedPostId.value = ''
    postDetail.value = null
    listError.value = error.message || '帖子列表加载失败'
  } finally {
    if (requestId === listRequestId) listLoading.value = false
  }
}

async function loadPostDetail(postId) {
  if (!postId) return

  const requestId = ++detailRequestId
  detailLoading.value = true
  detailError.value = ''

  try {
    const result = await api.getPostDetail(postId)
    if (requestId !== detailRequestId) return
    postDetail.value = normalizePostDetail(result)
  } catch (error) {
    if (requestId !== detailRequestId) return
    postDetail.value = {
      post: selectedListPost.value,
      replies: [],
      likedByCurrentUser: false,
      markedByCurrentUser: false,
      acceptedReplyId: selectedListPost.value?.acceptedReplyId || null
    }
    detailError.value = error.message || '帖子详情加载失败'
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

function selectPost(post) {
  if (!post?.id || String(post.id) === selectedPostId.value) return
  selectedPostId.value = String(post.id)
  loadPostDetail(post.id)
}

async function applyFilters() {
  filters.page = 1
  await syncRoute()
  await refreshPosts()
}

async function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    category: '',
    status: 'PUBLISHED',
    page: 1,
    size: DEFAULT_PAGE_SIZE
  })
  await syncRoute()
  await refreshPosts()
}

async function changePage(nextPage) {
  const normalizedPage = Math.min(Math.max(nextPage, 1), totalPages.value)
  if (normalizedPage === filters.page) return
  filters.page = normalizedPage
  await syncRoute()
  await refreshPosts()
}

function setFeedback(message, type = 'success') {
  feedback.value = message
  feedbackType.value = type
}

function resetPostForm() {
  Object.assign(postForm, createDefaultPostForm(), {
    category: filters.category || 'INQUIRY_HELP'
  })
  clearPublishErrors()
}

function openPublishDialog() {
  resetPostForm()
  feedback.value = ''
  publishDialogOpen.value = true
  nextTick(() => {
    const titleInput = document.getElementById('post-title-input')
    if (titleInput instanceof HTMLElement) titleInput.focus()
  })
}

function closePublishDialog() {
  if (publishing.value) return
  publishDialogOpen.value = false
}

function clearPublishErrors() {
  Object.keys(publishErrors).forEach((field) => {
    delete publishErrors[field]
  })
}

function setPublishError(field, message) {
  publishErrors[field] = message
}

function clearPublishError(field) {
  delete publishErrors[field]
}

function fieldDescribedBy(field, hintId) {
  const ids = []
  if (hintId) ids.push(hintId)
  if (publishErrors[field]) ids.push(`${field}-error`)
  return ids.join(' ') || undefined
}

function selectPostCategory(category) {
  postForm.category = category
  clearPublishError('category')
}

function validatePostForm() {
  clearPublishErrors()

  if (!postForm.category) setPublishError('category', '请选择帖子分类。')
  if (!postForm.title.trim()) setPublishError('title', '请填写帖子标题。')
  if (!postForm.content.trim()) setPublishError('content', '请填写帖子内容。')
  if (postForm.title.trim().length > 100) setPublishError('title', '标题最多 100 个字。')
  if (postForm.content.trim().length > 5000) setPublishError('content', '正文最多 5000 个字。')

  return Object.keys(publishErrors).length === 0
}

async function focusFirstInvalidField() {
  await nextTick()
  const target = document.querySelector('[aria-invalid="true"]')
  if (target instanceof HTMLElement) target.focus()
}

function buildCreatePostPayload() {
  return {
    category: postForm.category,
    title: postForm.title.trim(),
    content: postForm.content.trim(),
    attachmentIds: []
  }
}

async function handlePublishPost() {
  feedback.value = ''

  if (!validatePostForm()) {
    await focusFirstInvalidField()
    return
  }

  publishing.value = true
  try {
    const createdPost = normalizePost(await api.createPost(buildCreatePostPayload()))
    setFeedback(`帖子 #${createdPost.id || ''} 已发布。`)
    publishDialogOpen.value = false

    Object.assign(filters, {
      keyword: '',
      category: '',
      status: 'PUBLISHED',
      page: 1
    })
    await syncRoute()
    await refreshPosts()

    if (createdPost.id) {
      selectedPostId.value = String(createdPost.id)
      await loadPostDetail(createdPost.id)
    }
  } catch (error) {
    setFeedback(error.message || '帖子发布失败', 'danger')
  } finally {
    publishing.value = false
  }
}

function categoryLabel(category) {
  return optionLabel(POST_CATEGORY_OPTIONS, category)
}

function categoryDescription(category) {
  return POST_CATEGORY_OPTIONS.find((option) => option.value === category)?.description || ''
}

function categoryIcon(category) {
  return categoryIconMap[category] || Tag
}

function postStatusMeta(status) {
  return POST_STATUS_OPTIONS.find((option) => option.value === status) || {
    value: status,
    label: status || '未知状态',
    tone: 'muted'
  }
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

function compactContent(value, maxLength = 118) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '暂无正文'
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function authorCopy(authorId) {
  if (authorId === undefined || authorId === null) return '发布者未记录'
  if (currentUserId.value !== undefined && currentUserId.value !== null && String(authorId) === String(currentUserId.value)) {
    return '我发布'
  }
  return `同学 ${authorId}`
}

async function submitReply() {
  const content = replyContent.value.trim()
  if (!content) {
    replyError.value = '请输入回复内容'
    return
  }
  if (content.length > 2000) {
    replyError.value = '回复内容不能超过2000字'
    return
  }
  if (!detailPost.value?.id) return

  replyError.value = ''
  replySubmitting.value = true
  try {
    await api.createPostReply(detailPost.value.id, { content })
    replyContent.value = ''
    await loadPostDetail(detailPost.value.id)
  } catch (error) {
    replyError.value = error.message || '回复发送失败，请稍后重试。'
  } finally {
    replySubmitting.value = false
  }
}

watch(
  () => route.query,
  () => {
    if (route.name !== 'post-list') return
    Object.assign(filters, readFiltersFromRoute())
    refreshPosts()
  }
)

onMounted(() => {
  refreshPosts()
})
</script>

<template>
  <DashboardShell active-label="帖子广场">
    <main
      class="posts-page"
      :aria-busy="listLoading"
    >
      <section class="page-title-card posts-title-card">
        <div class="title-icon">
          <BookOpen
            :size="32"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>社区帖子</p>
          <h1>浏览校园帖子</h1>
          <span>按分类和关键词查看问询、闲置、组队与校园交流内容。</span>
        </div>
        <button
          class="primary-action title-action"
          type="button"
          @click="openPublishDialog"
        >
          <PlusCircle
            :size="18"
            aria-hidden="true"
          />
          发布帖子
        </button>
      </section>

      <div
        v-if="feedback"
        class="page-feedback"
        :class="`page-feedback--${feedbackType}`"
      >
        <CheckCircle2
          v-if="feedbackType === 'success'"
          :size="18"
          aria-hidden="true"
        />
        <Filter
          v-else
          :size="18"
          aria-hidden="true"
        />
        <span>{{ feedback }}</span>
      </div>

      <section class="metric-strip">
        <div class="metric-tile metric-tile--posts">
          <FileText
            :size="20"
            aria-hidden="true"
          />
          <span>
            <strong>{{ posts.length }}</strong>
            当前页帖子
          </span>
        </div>
        <div class="metric-tile metric-tile--replies">
          <MessageCircle
            :size="20"
            aria-hidden="true"
          />
          <span>
            <strong>{{ visibleReplyCount }}</strong>
            当前页回复
          </span>
        </div>
        <div class="metric-tile metric-tile--likes">
          <ThumbsUp
            :size="20"
            aria-hidden="true"
          />
          <span>
            <strong>{{ visibleLikeCount }}</strong>
            当前页点赞
          </span>
        </div>
        <div class="metric-tile metric-tile--mine">
          <UserRound
            :size="20"
            aria-hidden="true"
          />
          <span>
            <strong>{{ myVisiblePostCount }}</strong>
            当前页我的发布
          </span>
        </div>
      </section>

      <form
        class="panel-card filter-panel"
        novalidate
        @submit.prevent="applyFilters"
      >
        <div class="filter-heading">
          <div>
            <span class="section-kicker">筛选</span>
            <h2>找到相关帖子</h2>
          </div>
          <span
            v-if="activeFilterCount"
            class="filter-count"
          >
            {{ activeFilterCount }} 个条件
          </span>
        </div>

        <div class="filter-grid">
          <label class="filter-field filter-field--wide">
            <span>关键词</span>
            <span class="search-field">
              <Search
                :size="18"
                aria-hidden="true"
              />
              <input
                v-model.trim="filters.keyword"
                type="search"
                placeholder="搜索标题或正文"
              >
            </span>
          </label>

          <label class="filter-field">
            <span>分类</span>
            <select v-model="filters.category">
              <option value="">全部分类</option>
              <option
                v-for="category in POST_CATEGORY_OPTIONS"
                :key="category.value"
                :value="category.value"
              >
                {{ category.label }}
              </option>
            </select>
          </label>

          <label class="filter-field">
            <span>状态</span>
            <select v-model="filters.status">
              <option value="">全部状态</option>
              <option
                v-for="status in POST_STATUS_OPTIONS"
                :key="status.value"
                :value="status.value"
              >
                {{ status.label }}
              </option>
            </select>
          </label>

          <label class="filter-field">
            <span>每页</span>
            <select v-model.number="filters.size">
              <option :value="8">8 条</option>
              <option :value="12">12 条</option>
              <option :value="20">20 条</option>
            </select>
          </label>
        </div>

        <div class="filter-actions">
          <button
            class="ghost-action"
            type="button"
            :disabled="listLoading"
            @click="resetFilters"
          >
            重置
          </button>
          <button
            class="ghost-action"
            type="button"
            :disabled="listLoading"
            @click="refreshPosts"
          >
            <RefreshCw
              :class="{ spinning: listLoading }"
              :size="17"
              aria-hidden="true"
            />
            刷新
          </button>
          <button
            class="primary-action"
            type="submit"
            :disabled="listLoading"
          >
            <LoaderCircle
              v-if="listLoading"
              class="spinning"
              :size="17"
              aria-hidden="true"
            />
            <Search
              v-else
              :size="17"
              aria-hidden="true"
            />
            查询
          </button>
        </div>
      </form>

      <div class="posts-layout">
        <section class="panel-card list-panel">
          <div class="list-toolbar">
            <div>
              <span class="section-kicker">帖子列表</span>
              <h2>共 {{ pagination.total }} 条，当前 {{ resultRange }}</h2>
            </div>
            <button
              class="ghost-action compact-action"
              type="button"
              @click="openPublishDialog"
            >
              <PlusCircle
                :size="17"
                aria-hidden="true"
              />
              发帖
            </button>
          </div>

          <div
            v-if="listLoading"
            class="loading-state"
          >
            <LoaderCircle
              class="spinning"
              :size="28"
              aria-hidden="true"
            />
            <span>正在加载帖子</span>
          </div>

          <div
            v-else-if="listError"
            class="empty-state empty-state--danger"
          >
            <h3>列表加载失败</h3>
            <p>{{ listError }}</p>
            <button
              class="primary-action"
              type="button"
              @click="refreshPosts"
            >
              重新加载
            </button>
          </div>

          <div
            v-else-if="!posts.length"
            class="empty-state"
          >
            <FileText
              :size="40"
              aria-hidden="true"
            />
            <h3>还没有匹配的帖子</h3>
            <p>可以清空筛选条件，或发布一条新的校园帖子。</p>
            <button
              class="primary-action"
              type="button"
              @click="openPublishDialog"
            >
              <PlusCircle
                :size="18"
                aria-hidden="true"
              />
              发布帖子
            </button>
          </div>

          <div
            v-else
            class="post-list"
          >
            <button
              v-for="post in posts"
              :key="post.id"
              class="post-card"
              :class="{ selected: String(post.id) === selectedPostId }"
              type="button"
              :aria-pressed="String(post.id) === selectedPostId"
              @click="selectPost(post)"
            >
              <span
                class="post-type-icon"
                :class="`post-type-icon--${post.category}`"
              >
                <component
                  :is="categoryIcon(post.category)"
                  :size="20"
                  aria-hidden="true"
                />
              </span>

              <span class="post-card-main">
                <span class="post-card-heading">
                  <span>
                    <strong>{{ post.title || '未命名帖子' }}</strong>
                    <small>{{ categoryLabel(post.category) }} · {{ authorCopy(post.authorId) }}</small>
                  </span>
                  <StatusBadge
                    :status="post.status"
                    :label="postStatusMeta(post.status).label"
                    :tone="postStatusMeta(post.status).tone"
                  />
                </span>
                <span class="post-card-copy">{{ compactContent(post.content) }}</span>
                <span class="post-card-footer">
                  <span>
                    <MessageCircle
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ post.replyCount }} 回复
                  </span>
                  <span>
                    <ThumbsUp
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ post.likeCount }} 赞
                  </span>
                  <span>
                    <Bookmark
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ post.markCount }} 标记
                  </span>
                  <span>
                    <Clock3
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ formatDateTime(post.createdAt) }}
                  </span>
                </span>
              </span>
            </button>
          </div>

          <div
            v-if="posts.length"
            class="pagination-bar"
          >
            <button
              class="page-button"
              type="button"
              :disabled="pagination.page <= 1 || listLoading"
              @click="changePage(pagination.page - 1)"
            >
              上一页
            </button>
            <span>第 {{ pagination.page }} / {{ totalPages }} 页</span>
            <button
              class="page-button"
              type="button"
              :disabled="pagination.page >= totalPages || listLoading"
              @click="changePage(pagination.page + 1)"
            >
              下一页
            </button>
          </div>
        </section>

        <aside class="panel-card detail-panel">
          <div
            v-if="detailLoading"
            class="loading-state detail-loading"
          >
            <LoaderCircle
              class="spinning"
              :size="28"
              aria-hidden="true"
            />
            <span>正在加载详情</span>
          </div>

          <template v-else-if="detailPost">
            <div class="detail-card-header">
              <div>
                <span class="section-kicker">{{ categoryLabel(detailPost.category) }}</span>
                <h2>{{ detailPost.title || '未命名帖子' }}</h2>
              </div>
              <StatusBadge
                :status="detailPost.status"
                :label="postStatusMeta(detailPost.status).label"
                :tone="postStatusMeta(detailPost.status).tone"
              />
            </div>

            <div
              v-if="detailError"
              class="page-feedback page-feedback--danger compact-feedback"
            >
              {{ detailError }}
            </div>

            <dl class="detail-facts">
              <div>
                <dt>发布者</dt>
                <dd>{{ authorCopy(detailPost.authorId) }}</dd>
              </div>
              <div>
                <dt>发布时间</dt>
                <dd>{{ formatDateTime(detailPost.createdAt) }}</dd>
              </div>
              <div>
                <dt>回复</dt>
                <dd>{{ detailPost.replyCount || detailReplies.length }}</dd>
              </div>
              <div>
                <dt>互动</dt>
                <dd>{{ detailPost.likeCount }} 赞 · {{ detailPost.markCount }} 标记</dd>
              </div>
            </dl>

            <section class="detail-section">
              <h3>正文</h3>
              <p>{{ detailPost.content || '暂无正文' }}</p>
            </section>

            <section class="detail-section">
              <div class="reply-heading">
                <h3>回复</h3>
                <span>{{ detailReplies.length }} 条</span>
              </div>

              <form
                v-if="isAuthenticated"
                class="reply-form"
                @submit.prevent="submitReply"
              >
                <textarea
                  v-model="replyContent"
                  :disabled="replySubmitting"
                  maxlength="2000"
                  placeholder="写下你的回复，帮助楼主或参与讨论..."
                  :aria-invalid="Boolean(replyError)"
                />
                <div class="reply-form-footer">
                  <small>{{ replyContent.length }}/2000</small>
                  <p
                    v-if="replyError"
                    class="reply-form-error"
                  >
                    {{ replyError }}
                  </p>
                  <button
                    class="primary-action"
                    type="submit"
                    :disabled="replySubmitting || !replyContent.trim()"
                  >
                    <LoaderCircle
                      v-if="replySubmitting"
                      class="spinning"
                      :size="16"
                      aria-hidden="true"
                    />
                    <Send
                      v-else
                      :size="16"
                      aria-hidden="true"
                    />
                    发送回复
                  </button>
                </div>
              </form>
              <p
                v-else
                class="reply-login-hint"
              >
                请先登录后再参与回复。
              </p>

              <div
                v-if="!detailReplies.length"
                class="reply-empty"
              >
                暂无回复
              </div>
              <div
                v-else
                class="reply-list"
              >
                <article
                  v-for="reply in detailReplies"
                  :key="reply.id"
                  class="reply-card"
                  :class="{ accepted: String(reply.id) === String(postDetail?.acceptedReplyId) }"
                >
                  <div class="reply-card-top">
                    <span>
                      <UserRound
                        :size="16"
                        aria-hidden="true"
                      />
                      {{ authorCopy(reply.authorId) }}
                    </span>
                    <small>{{ formatDateTime(reply.createdAt) }}</small>
                  </div>
                  <p>{{ reply.content }}</p>
                  <span
                    v-if="String(reply.id) === String(postDetail?.acceptedReplyId)"
                    class="accepted-tag"
                  >
                    已采纳
                  </span>
                </article>
              </div>
            </section>
          </template>

          <div
            v-else
            class="empty-state detail-empty"
          >
            <BookOpen
              :size="40"
              aria-hidden="true"
            />
            <h3>选择一个帖子查看详情</h3>
            <p>帖子正文、回复和互动计数会显示在这里。</p>
          </div>
        </aside>
      </div>

      <div
        v-if="publishDialogOpen"
        class="workflow-modal-layer"
        @click.self="closePublishDialog"
      >
        <section
          class="panel-card publish-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="post-publish-title"
        >
          <header class="dialog-header">
            <div>
              <span class="section-kicker">发布帖子</span>
              <h2 id="post-publish-title">
                编辑帖子内容
              </h2>
            </div>
            <button
              class="icon-action"
              type="button"
              aria-label="关闭发布弹窗"
              :disabled="publishing"
              @click="closePublishDialog"
            >
              <X
                :size="18"
                aria-hidden="true"
              />
            </button>
          </header>

          <form
            class="publish-form"
            novalidate
            @submit.prevent="handlePublishPost"
          >
            <fieldset class="category-selector">
              <legend>帖子分类</legend>
              <button
                v-for="category in categoryCards"
                :key="category.value"
                class="choice-control"
                :class="{ selected: postForm.category === category.value }"
                type="button"
                :aria-pressed="postForm.category === category.value"
                @click="selectPostCategory(category.value)"
              >
                <component
                  :is="category.icon"
                  :size="20"
                  aria-hidden="true"
                />
                <span>
                  <strong>{{ category.label }}</strong>
                  <small>{{ category.description }}</small>
                </span>
              </button>
            </fieldset>
            <p
              v-if="publishErrors.category"
              id="category-error"
              class="field-error"
            >
              {{ publishErrors.category }}
            </p>

            <label class="publish-field">
              <span>标题</span>
              <input
                id="post-title-input"
                v-model.trim="postForm.title"
                type="text"
                maxlength="100"
                placeholder="例如：求推荐仙林附近适合小组讨论的地方"
                :aria-invalid="Boolean(publishErrors.title)"
                :aria-describedby="fieldDescribedBy('title', 'post-title-hint')"
                @input="clearPublishError('title')"
              >
              <small id="post-title-hint">{{ postForm.title.trim().length }}/100</small>
              <p
                v-if="publishErrors.title"
                id="title-error"
                class="field-error"
              >
                {{ publishErrors.title }}
              </p>
            </label>

            <label class="publish-field">
              <span>正文</span>
              <textarea
                v-model="postForm.content"
                maxlength="5000"
                placeholder="把背景、问题、限制条件或希望大家讨论的点写清楚。"
                :aria-invalid="Boolean(publishErrors.content)"
                :aria-describedby="fieldDescribedBy('content', 'post-content-hint')"
                @input="clearPublishError('content')"
              />
              <small id="post-content-hint">{{ postForm.content.trim().length }}/5000</small>
              <p
                v-if="publishErrors.content"
                id="content-error"
                class="field-error"
              >
                {{ publishErrors.content }}
              </p>
            </label>

            <section class="post-preview">
              <div class="post-preview-heading">
                <span>
                  <component
                    :is="categoryIcon(postForm.category)"
                    :size="18"
                    aria-hidden="true"
                  />
                  {{ categoryLabel(postForm.category) }}
                </span>
                <small>{{ categoryDescription(postForm.category) }}</small>
              </div>
              <h3>{{ previewTitle }}</h3>
              <p>{{ previewContent }}</p>
            </section>

            <div
              v-if="!canPublish"
              class="page-feedback page-feedback--danger compact-feedback"
            >
              登录后才能发布帖子。
            </div>

            <footer class="dialog-actions">
              <button
                class="ghost-action"
                type="button"
                :disabled="publishing"
                @click="closePublishDialog"
              >
                取消
              </button>
              <button
                class="primary-action"
                type="submit"
                :disabled="publishing || !canPublish"
              >
                <LoaderCircle
                  v-if="publishing"
                  class="spinning"
                  :size="17"
                  aria-hidden="true"
                />
                <Send
                  v-else
                  :size="17"
                  aria-hidden="true"
                />
                发布
              </button>
            </footer>
          </form>
        </section>
      </div>
    </main>
  </DashboardShell>
</template>

<style scoped>
.posts-page {
  display: grid;
  gap: 18px;
  width: 100%;
  max-width: none;
  color: #202033;
}

.page-title-card,
.panel-card,
.page-feedback {
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 36px rgba(42, 38, 61, 0.045);
}

.page-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 22px;
  min-height: 108px;
  padding: 20px 28px;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eef2ff, #f5f3ff);
  color: #4f46e5;
}

.title-copy {
  display: grid;
  gap: 5px;
}

.title-copy p,
.title-copy h1,
.title-copy span,
.filter-heading h2,
.list-toolbar h2,
.detail-card-header h2,
.detail-section h3,
.dialog-header h2,
.post-preview h3 {
  margin: 0;
}

.title-copy p,
.section-kicker {
  color: #4f46e5;
  font-size: 0.82rem;
  font-weight: 900;
}

.title-copy h1 {
  color: #111827;
  font-size: 1.5rem;
  line-height: 1.2;
}

.title-copy span {
  color: #667085;
  font-size: 0.92rem;
  line-height: 1.5;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 22px;
}

.primary-action,
.ghost-action,
.page-button,
.icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  border-radius: 8px;
  font-weight: 900;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.primary-action {
  min-width: 132px;
  padding: 0 16px;
  border: 1px solid #4f46e5;
  background: #4f46e5;
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(79, 70, 229, 0.16);
}

.primary-action:hover:not(:disabled) {
  background: #4338ca;
}

.ghost-action,
.page-button,
.icon-action {
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #4f46e5;
}

.ghost-action {
  padding: 0 12px;
}

.ghost-action:hover:not(:disabled),
.page-button:hover:not(:disabled),
.icon-action:hover:not(:disabled) {
  border-color: #b9b2e8;
  background: #f7f4ff;
}

.primary-action:disabled,
.ghost-action:disabled,
.page-button:disabled,
.icon-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.primary-action:focus-visible,
.ghost-action:focus-visible,
.page-button:focus-visible,
.icon-action:focus-visible,
.post-card:focus-visible,
.choice-control:focus-visible,
.filter-field input:focus,
.filter-field select:focus,
.publish-field input:focus,
.publish-field textarea:focus {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
  outline: 0;
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  color: #166534;
  line-height: 1.55;
}

.page-feedback--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.page-feedback--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.compact-feedback {
  box-shadow: none;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-tile {
  --metric-bg: #ffffff;
  --metric-border: #e4e7ef;
  --metric-icon: #4f46e5;
  --metric-label: #667085;
  --metric-value: #111827;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-height: 72px;
  padding: 14px 16px;
  border: 1px solid var(--metric-border);
  border-radius: 8px;
  background: var(--metric-bg);
}

.metric-tile--posts {
  --metric-bg: #eef2ff;
  --metric-border: #c7d2fe;
  --metric-icon: #4f46e5;
  --metric-label: #4338ca;
  --metric-value: #312e81;
}

.metric-tile--replies {
  --metric-bg: #eff6ff;
  --metric-border: #bfdbfe;
  --metric-icon: #2563eb;
  --metric-label: #1d4ed8;
  --metric-value: #1e3a8a;
}

.metric-tile--likes {
  --metric-bg: #ecfdf5;
  --metric-border: #bbf7d0;
  --metric-icon: #059669;
  --metric-label: #047857;
  --metric-value: #064e3b;
}

.metric-tile--mine {
  --metric-bg: #fff7ed;
  --metric-border: #fed7aa;
  --metric-icon: #ea580c;
  --metric-label: #c2410c;
  --metric-value: #7c2d12;
}

.metric-strip svg {
  color: var(--metric-icon);
}

.metric-strip span {
  display: grid;
  gap: 2px;
  color: var(--metric-label);
  font-size: 0.88rem;
  font-weight: 800;
}

.metric-strip strong {
  color: var(--metric-value);
  font-size: 1.36rem;
  line-height: 1;
}

.filter-panel {
  gap: 14px;
}

.filter-heading,
.list-toolbar,
.detail-card-header,
.dialog-header,
.post-preview-heading,
.reply-heading,
.dialog-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.filter-heading h2,
.list-toolbar h2,
.detail-card-header h2,
.dialog-header h2 {
  color: #111827;
  font-size: 1.08rem;
}

.filter-count {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.82rem;
  font-weight: 900;
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) repeat(3, minmax(160px, 0.35fr));
  gap: 12px;
}

.filter-field {
  display: grid;
  gap: 7px;
}

.filter-field span:first-child,
.publish-field span,
.category-selector legend {
  color: #3c3543;
  font-weight: 900;
}

.search-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 9px;
}

.filter-field input,
.filter-field select,
.search-field,
.publish-field input,
.publish-field textarea {
  width: 100%;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  outline: 0;
}

.filter-field input,
.filter-field select {
  min-height: 42px;
  padding: 0 12px;
}

.search-field {
  padding-left: 12px;
  color: #667085;
}

.search-field input {
  border: 0;
  border-radius: 0;
}

.filter-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.posts-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 18px;
  align-items: start;
}

.detail-panel {
  position: sticky;
  top: 98px;
  max-height: calc(100vh - 122px);
  overflow: auto;
}

.post-list {
  display: grid;
  gap: 12px;
}

.post-card {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  width: 100%;
  padding: 16px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
  color: inherit;
  text-align: left;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.post-card:hover,
.post-card.selected {
  border-color: #b9b2e8;
  background: #fbf9ff;
  box-shadow: 0 14px 28px rgba(116, 109, 195, 0.08);
}

.post-card.selected::before {
  content: "";
  position: absolute;
  inset: 14px auto 14px 0;
  width: 3px;
  border-radius: 999px;
  background: #4f46e5;
}

.post-type-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: #eef2ff;
  color: #4f46e5;
}

.post-type-icon--SECOND_HAND {
  background: #ecfdf5;
  color: #047857;
}

.post-type-icon--TEAM_FRIEND {
  background: #fff7ed;
  color: #c2410c;
}

.post-type-icon--CAMPUS_SOCIAL {
  background: #f0f9ff;
  color: #0369a1;
}

.post-card-main {
  display: grid;
  gap: 10px;
}

.post-card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.post-card-heading > span:first-child {
  display: grid;
  gap: 4px;
}

.post-card-heading strong {
  color: #111827;
  font-size: 1.05rem;
  line-height: 1.35;
}

.post-card-heading small,
.post-card-copy,
.post-card-footer,
.detail-section p,
.reply-card p {
  color: #667085;
  line-height: 1.6;
}

.post-card-copy {
  margin: 0;
}

.post-card-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  font-size: 0.86rem;
  font-weight: 800;
}

.post-card-footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.post-card-footer svg {
  color: #4f46e5;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
}

.page-button {
  min-width: 96px;
  padding: 0 12px;
}

.loading-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 220px;
  color: #667085;
  font-weight: 900;
}

.detail-loading {
  min-height: 360px;
}

.empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 228px;
  padding: 24px;
  border: 1px dashed #d7d1e5;
  border-radius: 8px;
  background: #fbf9fc;
  color: #667085;
  text-align: center;
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: #111827;
}

.empty-state p {
  max-width: 440px;
  line-height: 1.6;
}

.empty-state--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.detail-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.detail-facts div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #fcfbfd;
}

.detail-facts dt {
  color: #8a8792;
  font-size: 0.84rem;
}

.detail-facts dd {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.45;
}

.detail-section {
  display: grid;
  gap: 10px;
}

.detail-section h3 {
  color: #111827;
  font-size: 1rem;
}

.detail-section p {
  margin: 0;
  white-space: pre-wrap;
}

.reply-heading {
  align-items: center;
}

.reply-heading span {
  color: #8a8792;
  font-weight: 900;
}

.reply-form {
  display: grid;
  gap: 8px;
  padding: 13px;
  border: 1px solid #dbd7e8;
  border-radius: 8px;
  background: #fbf9fc;
}

.reply-form textarea {
  width: 100%;
  min-height: 88px;
  padding: 10px 12px;
  border: 1px solid #d7d1e5;
  border-radius: 8px;
  background: #ffffff;
  color: #3c3543;
  line-height: 1.55;
  outline: 0;
  resize: vertical;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.reply-form textarea:focus {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
}

.reply-form textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.reply-form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.reply-form-footer small {
  color: #8a8792;
}

.reply-form-error {
  color: #ef4444;
  margin: 0;
  font-size: 0.88rem;
}

.reply-login-hint {
  margin: 0;
  padding: 13px;
  border: 1px dashed #d7d1e5;
  border-radius: 8px;
  background: #fbf9fc;
  color: #667085;
  text-align: center;
  font-size: 0.92rem;
}

.reply-empty {
  padding: 14px;
  border: 1px dashed #d7d1e5;
  border-radius: 8px;
  background: #fbf9fc;
  color: #667085;
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
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  background: #ffffff;
}

.reply-card.accepted {
  border-color: #bbf7d0;
  background: #f0fdf4;
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
  color: #3c3543;
  font-weight: 900;
}

.reply-card-top small {
  color: #8a8792;
}

.accepted-tag {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 999px;
  background: #dcfce7;
  color: #166534;
  font-size: 0.8rem;
  font-weight: 900;
}

.workflow-modal-layer {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: 22px;
  background: rgba(17, 24, 39, 0.42);
  backdrop-filter: blur(10px);
}

.publish-dialog {
  width: min(760px, calc(100vw - 44px));
  max-height: calc(100vh - 44px);
  overflow: auto;
  box-shadow: 0 24px 54px rgba(17, 24, 39, 0.2);
}

.icon-action {
  width: 38px;
  min-height: 38px;
  padding: 0;
}

.publish-form {
  display: grid;
  gap: 16px;
}

.category-selector {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.category-selector legend {
  grid-column: 1 / -1;
  padding: 0 0 2px;
}

.choice-control {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 76px;
  padding: 12px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: #ffffff;
  color: #4a4650;
  text-align: left;
}

.choice-control svg {
  color: #4f46e5;
}

.choice-control span {
  display: grid;
  gap: 3px;
}

.choice-control strong {
  color: #111827;
}

.choice-control small {
  color: #667085;
  line-height: 1.4;
}

.choice-control:hover,
.choice-control.selected {
  border-color: #b9b2e8;
  background: #fbf9ff;
}

.publish-field {
  display: grid;
  gap: 7px;
}

.publish-field input,
.publish-field textarea {
  padding: 11px 12px;
  line-height: 1.45;
}

.publish-field textarea {
  min-height: 150px;
  resize: vertical;
}

.publish-field input[aria-invalid="true"],
.publish-field textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.publish-field small {
  color: #8a8792;
}

.field-error {
  margin: 0;
  color: #b91c1c;
  font-size: 0.88rem;
  font-weight: 800;
  line-height: 1.45;
}

.post-preview {
  display: grid;
  gap: 9px;
  padding: 14px;
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  background: #fbfbfd;
}

.post-preview-heading span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #4f46e5;
  font-weight: 900;
}

.post-preview-heading small {
  color: #8a8792;
}

.post-preview h3 {
  color: #111827;
  font-size: 1rem;
}

.post-preview p {
  margin: 0;
  color: #667085;
  line-height: 1.6;
  white-space: pre-wrap;
}

.dialog-actions {
  justify-content: flex-end;
  padding-top: 2px;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1120px) {
  .posts-layout {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    position: static;
    max-height: none;
  }
}

@media (max-width: 900px) {
  .page-title-card,
  .metric-strip,
  .filter-grid,
  .detail-facts,
  .category-selector {
    grid-template-columns: 1fr;
  }

  .page-title-card {
    gap: 18px;
    padding: 22px;
  }

  .filter-heading,
  .list-toolbar,
  .detail-card-header,
  .post-card-heading,
  .dialog-header,
  .post-preview-heading,
  .reply-card-top {
    display: grid;
  }

  .filter-actions,
  .dialog-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .panel-card {
    padding: 18px;
  }

  .post-card {
    grid-template-columns: 1fr;
  }

  .post-type-icon {
    width: 40px;
    height: 40px;
  }

  .title-action,
  .primary-action,
  .ghost-action,
  .page-button,
  .compact-action {
    width: 100%;
  }

  .pagination-bar {
    display: grid;
  }

  .workflow-modal-layer {
    padding: 12px;
  }

  .publish-dialog {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }
}
</style>
