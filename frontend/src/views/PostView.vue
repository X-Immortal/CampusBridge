<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookOpen,
  Bookmark,
  Clock3,
  FileText,
  ImagePlus,
  LoaderCircle,
  MessageCircle,
  PlusCircle,
  RefreshCw,
  Reply as ReplyIcon,
  Search,
  Send,
  Star,
  Tag,
  ThumbsUp,
  UserRound,
  X
} from '@lucide/vue'
import DashboardShell from '../components/DashboardShell.vue'
import ModuleTopbar from '../components/layout/ModuleTopbar.vue'
import ModuleEntryCard from '../components/layout/ModuleEntryCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import UserProfileModal from '../components/UserProfileModal.vue'
import { postNavItems } from '../app/navigation'
import { optionLabel, POST_CATEGORY_OPTIONS, POST_STATUS_OPTIONS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'
import { useGuestAction } from '../composables/useGuestAction'

const route = useRoute()
const router = useRouter()
const { isAuthenticated, currentUserId } = useAuth()
const { showToast } = useToast()
const { requireLoginAction } = useGuestAction()

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
const publishDialogOpen = ref(false)
const publishing = ref(false)
const publishErrors = reactive({})
const postForm = reactive(createDefaultPostForm())

const replyContent = ref('')
const replySubmitting = ref(false)
const replyError = ref('')
const replyComposerOpen = ref(false)
const replyingToId = ref(null)
const postDetailModalOpen = ref(false)

const postAttachments = ref([])
const postUploading = ref(false)
const postAttachmentFileInput = ref(null)
const MAX_POST_IMAGES = 9
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

const profileUserId = ref(null)
const showProfileModal = ref(false)
const profileRelatedId = ref(null)

function openProfileModal(userId, postId) {
  if (!userId) return
  profileUserId.value = userId
  profileRelatedId.value = postId
  showProfileModal.value = true
}

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
const threadedReplies = computed(() => {
  const replies = detailReplies.value.map((reply) => ({ ...reply, children: [] }))
  const byId = new Map(replies.map((reply) => [String(reply.id), reply]))
  const roots = []

  for (const reply of replies) {
    if (reply.parentReplyId == null || !byId.has(String(reply.parentReplyId))) {
      roots.push(reply)
      continue
    }

    let root = byId.get(String(reply.parentReplyId))
    const visited = new Set([String(reply.id)])
    while (root?.parentReplyId != null && byId.has(String(root.parentReplyId)) && !visited.has(String(root.id))) {
      visited.add(String(root.id))
      root = byId.get(String(root.parentReplyId))
    }
    root?.children.push(reply)
  }

  return roots
})
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
const detailAttachments = computed(() => postDetail.value?.attachments || [])
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

function cleanDisplayTitle(value, fallback = '未命名帖子') {
  const text = String(value || '').trim()
  if (!text) return fallback
  return text
    .replace(/^\s*#?\d+\s*[-_—:：.)、]\s*/u, '')
    .replace(/\s*[-_—:：(（]?\s*#\d+\s*[)）]?\s*$/u, '')
    .trim() || fallback
}

function normalizePost(post = {}) {
  const source = post.post || post
  const id = source.id ?? source.postId
  const myReplies = Array.isArray(post.replies) ? post.replies.map(normalizeReply) : []
  return {
    ...source,
    id,
    title: cleanDisplayTitle(source.title),
    authorId: source.authorId ?? source.userId,
    category: source.category || 'INQUIRY_HELP',
    status: source.status || 'PUBLISHED',
    replyCount: numericValue(source.replyCount),
    likeCount: numericValue(source.likeCount),
    markCount: numericValue(source.markCount),
    _myReplies: myReplies,
    _myReplyTotal: numericValue(post.replyTotal || myReplies.length),
    _lastReplyAt: post.lastReplyAt || myReplies[0]?.createdAt || null
  }
}

function normalizeReply(reply = {}) {
  return {
    ...reply,
    id: reply.id ?? reply.replyId,
    authorId: reply.authorId ?? reply.userId,
    authorName: reply.authorName || reply.authorNickname || null,
    parentReplyId: reply.parentReplyId ?? null,
    likeCount: numericValue(reply.likeCount)
  }
}

function normalizePagedPosts(result) {
  if (Array.isArray(result)) return result
  return result?.items || result?.posts || result?.records || result?.content || result?.list || []
}

function normalizePostDetail(result) {
  if (!result) return null

  console.debug('[PostDetail] raw result:', result)

  if (result.post) {
    const normalized = {
      post: normalizePost(result.post),
      replies: Array.isArray(result.replies) ? result.replies.map(normalizeReply) : [],
      likedByCurrentUser: Boolean(result.likedByCurrentUser),
      markedByCurrentUser: Boolean(result.markedByCurrentUser),
      attachments: Array.isArray(result.attachments) ? result.attachments : [],
      acceptedReplyId: result.acceptedReplyId || null
    }
    console.debug('[PostDetail] normalized (wrapped):', normalized)
    return normalized
  }

  const normalized = {
    post: normalizePost(result),
    replies: [],
    likedByCurrentUser: Boolean(result.likedByCurrentUser),
    markedByCurrentUser: Boolean(result.markedByCurrentUser),
    acceptedReplyId: result.acceptedReplyId || null,
    attachments: Array.isArray(result.attachments) ? result.attachments : []
  }
  console.debug('[PostDetail] normalized (flat):', normalized)
  return normalized
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
    name: route.name || 'post-list',
    query: buildRouteQuery()
  })
}

async function refreshPosts() {
  const requestId = ++listRequestId
  listLoading.value = true
  listError.value = ''

  try {
    const result = currentSubSection.value === 'marked'
      ? await api.getMarkedPosts()
      : currentSubSection.value === 'replies'
        ? await api.getMyRepliedPosts(buildPostQuery())
      : await api.getPosts(buildPostQuery())
    if (requestId !== listRequestId) return

    applyPostResult(result || {})

    const requestedPostId = readQuery('postId', '')
    if (requestedPostId) {
      selectedPostId.value = requestedPostId
      postDetailModalOpen.value = true
      await loadPostDetail(requestedPostId)
      return
    }

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
  if (!post?.id) return
  postDetailModalOpen.value = true
  if (String(post.id) === selectedPostId.value) return
  selectedPostId.value = String(post.id)
  loadPostDetail(post.id)
}

function closePostModal() {
  postDetailModalOpen.value = false
  cancelReply()
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

function resetPostForm() {
  Object.assign(postForm, createDefaultPostForm(), {
    category: filters.category || 'INQUIRY_HELP'
  })
  clearPublishErrors()
  postAttachments.value = []
}

function triggerPostImageUpload() {
  postAttachmentFileInput.value?.click()
}

function readFileAsDataUri(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('图片读取失败，请重新选择。'))
    reader.readAsDataURL(file)
  })
}

async function handlePostAttachmentFileChange(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  const remaining = MAX_POST_IMAGES - postAttachments.value.length
  if (remaining <= 0) {
    showToast(`最多上传 ${MAX_POST_IMAGES} 张图片`, { type: 'warning' })
    event.target.value = ''
    return
  }

  const selectedFiles = files.slice(0, remaining)
  if (files.length > remaining) {
    showToast(`最多上传 ${MAX_POST_IMAGES} 张图片，超出部分未添加。`, { type: 'warning' })
  }

  const imageFiles = selectedFiles.filter((f) => f.type.startsWith('image/'))
  const oversized = imageFiles.filter((f) => f.size > MAX_IMAGE_SIZE)
  if (oversized.length) {
    showToast(`图片大小不能超过 10MB：${oversized.map((f) => f.name).join('、')}`, { type: 'danger' })
    event.target.value = ''
    return
  }

  postUploading.value = true
  const nextAttachments = [...postAttachments.value]

  try {
    for (const file of imageFiles) {
      const previewUrl = await readFileAsDataUri(file)
      const uploaded = await api.uploadAttachment({
        file,
        expectedOwnerType: 'POST'
      })

      const id = uploaded?.id ?? uploaded?.attachmentId ?? null
      nextAttachments.push({
        id,
        file,
        previewUrl,
        name: uploaded?.fileName || file.name,
        url: uploaded?.fileUrl || uploaded?.url || previewUrl
      })
    }
    postAttachments.value = nextAttachments
  } catch (error) {
    showToast(error.message || '图片上传失败，请稍后重试。', { type: 'danger' })
  } finally {
    postUploading.value = false
    event.target.value = ''
  }
}

function removePostAttachment(index) {
  postAttachments.value = postAttachments.value.filter((_, i) => i !== index)
}

function openPublishDialog() {
  if (!requireLoginAction('发布帖子')) return
  resetPostForm()
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
  const attachmentIds = postAttachments.value
    .map((item) => Number(item.id ?? item.attachmentId))
    .filter((id) => Number.isInteger(id) && id > 0)

  return {
    category: postForm.category,
    title: postForm.title.trim(),
    content: postForm.content.trim(),
    attachmentIds
  }
}

async function handlePublishPost() {
  if (!validatePostForm()) {
    await focusFirstInvalidField()
    return
  }

  publishing.value = true
  try {
    const createdPost = normalizePost(await api.createPost(buildCreatePostPayload()))
    showToast('帖子已发布。')
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
      postDetailModalOpen.value = true
      await loadPostDetail(createdPost.id)
    }
  } catch (error) {
    showToast(error.message || '帖子发布失败', { type: 'danger' })
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

function authorCopy(source) {
  if (!source) return '发布者未记录'
  const authorId = typeof source === 'object' ? source.authorId : source
  const authorName = typeof source === 'object' ? source.authorName : undefined
  if (authorId === undefined || authorId === null) return '发布者未记录'
  if (currentUserId.value !== undefined && currentUserId.value !== null && String(authorId) === String(currentUserId.value)) {
    return '我发布'
  }
  if (authorName && authorName.trim()) return authorName.trim()
  return `同学 ${authorId}`
}

function myReplyPreview(post) {
  const replies = Array.isArray(post?._myReplies) ? post._myReplies : []
  return replies
    .map((reply) => compactContent(reply.content, 72))
    .filter(Boolean)
    .slice(0, 2)
}

async function submitReply() {
  if (!requireLoginAction('回复帖子')) return
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
    await api.createPostReply(detailPost.value.id, {
      content,
      ...(replyingToId.value == null ? {} : { parentReplyId: Number(replyingToId.value) })
    })
    replyContent.value = ''
    replyComposerOpen.value = false
    replyingToId.value = null
    await loadPostDetail(detailPost.value.id)
    showToast('回复已发布。')
  } catch (error) {
    replyError.value = error.message || '回复发送失败，请稍后重试。'
  } finally {
    replySubmitting.value = false
  }
}

function startReply(replyId) {
  replyComposerOpen.value = true
  replyingToId.value = replyId
  replyContent.value = ''
  replyError.value = ''
  nextTick(() => {
    const targetId = replyId == null ? 'top-reply-input' : 'nested-reply-input'
    document.getElementById(targetId)?.focus()
  })
}

function cancelReply() {
  replyComposerOpen.value = false
  replyingToId.value = null
  replyContent.value = ''
  replyError.value = ''
}

async function togglePostLike(post) {
  if (!post?.id) return
  if (!requireLoginAction('点赞')) return
  const postRef = post
  const currentlyLiked = postRef._likedByCurrentUser ?? false
  const prevCount = Number(postRef.likeCount) || 0
  const newCount = currentlyLiked ? Math.max(0, prevCount - 1) : prevCount + 1

  postRef._likedByCurrentUser = !currentlyLiked
  postRef.likeCount = newCount

  const isDetailPost = postDetail.value?.post?.id === post.id
  if (isDetailPost && postDetail.value) {
    postDetail.value.likedByCurrentUser = !currentlyLiked
    postDetail.value.post.likeCount = newCount
  }

  try {
    if (currentlyLiked) {
      await api.cancelPostInteraction(post.id, 'LIKE')
    } else {
      await api.createPostInteraction(post.id, { type: 'LIKE' })
    }
  } catch {
    postRef._likedByCurrentUser = currentlyLiked
    postRef.likeCount = prevCount
    if (isDetailPost && postDetail.value) {
      postDetail.value.likedByCurrentUser = currentlyLiked
      postDetail.value.post.likeCount = prevCount
    }
  }
}

async function toggleDetailLike() {
  if (!detailPost.value?.id || !postDetail.value) return
  const postRef = postDetail.value
  const postObj = postRef.post
  const currentlyLiked = postRef.likedByCurrentUser ?? false
  const prevCount = Number(postObj.likeCount) || 0
  const newCount = currentlyLiked ? Math.max(0, prevCount - 1) : prevCount + 1

  postRef.likedByCurrentUser = !currentlyLiked
  postObj.likeCount = newCount

  try {
    if (currentlyLiked) {
      await api.cancelPostInteraction(postObj.id, 'LIKE')
    } else {
      await api.createPostInteraction(postObj.id, { type: 'LIKE' })
    }
  } catch {
    postRef.likedByCurrentUser = currentlyLiked
    postObj.likeCount = prevCount
  }
}

async function togglePostMark(post) {
  if (!post?.id) return
  if (!requireLoginAction('收藏')) return
  const postRef = post
  const currentlyMarked = postRef._markedByCurrentUser ?? false
  const prevCount = Number(postRef.markCount) || 0
  const newCount = currentlyMarked ? Math.max(0, prevCount - 1) : prevCount + 1

  postRef._markedByCurrentUser = !currentlyMarked
  postRef.markCount = newCount

  const isDetailPost = postDetail.value?.post?.id === post.id
  if (isDetailPost && postDetail.value) {
    postDetail.value.markedByCurrentUser = !currentlyMarked
    postDetail.value.post.markCount = newCount
  }

  try {
    if (currentlyMarked) {
      await api.cancelPostInteraction(post.id, 'MARK')
    } else {
      await api.createPostInteraction(post.id, { type: 'MARK' })
    }
  } catch {
    postRef._markedByCurrentUser = currentlyMarked
    postRef.markCount = prevCount
    if (isDetailPost && postDetail.value) {
      postDetail.value.markedByCurrentUser = currentlyMarked
      postDetail.value.post.markCount = prevCount
    }
  }
}

async function toggleDetailMark() {
  if (!detailPost.value?.id || !postDetail.value) return
  const postRef = postDetail.value
  const postObj = postRef.post
  const currentlyMarked = postRef.markedByCurrentUser ?? false
  const prevCount = Number(postObj.markCount) || 0
  const newCount = currentlyMarked ? Math.max(0, prevCount - 1) : prevCount + 1

  postRef.markedByCurrentUser = !currentlyMarked
  postObj.markCount = newCount

  try {
    if (currentlyMarked) {
      await api.cancelPostInteraction(postObj.id, 'MARK')
    } else {
      await api.createPostInteraction(postObj.id, { type: 'MARK' })
    }
  } catch {
    postRef.markedByCurrentUser = currentlyMarked
    postObj.markCount = prevCount
  }
}

const markedFilter = ref(false)
const currentSubSection = computed(() => route.meta?.subSection || 'plaza')

const displayPosts = computed(() => {
  let list = posts.value
  if (currentSubSection.value === 'mine' && currentUserId.value != null) {
    list = list.filter((p) => String(p.authorId) === String(currentUserId.value))
  }
  if (markedFilter.value) {
    list = list.filter((p) => p._markedByCurrentUser || currentSubSection.value === 'marked')
  }
  return list
})

const isRepliesSection = computed(() => currentSubSection.value === 'replies')

watch(
  () => route.fullPath,
  () => {
    Object.assign(filters, readFiltersFromRoute())
    refreshPosts()
  }
)

onMounted(() => {
  refreshPosts()
})
</script>

<template>
  <DashboardShell>
    <main
      class="posts-page"
      :aria-busy="listLoading"
    >
      <ModuleTopbar
        eyebrow="社区"
        title="校园社区"
        description="浏览帖子、查看我的发布与互动。"
      >
        <template #extra>
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
        </template>
        <ModuleEntryCard
          v-for="item in postNavItems"
          :key="item.key"
          :to="item.to"
          :icon="item.icon"
          :title="item.label"
          :description="item.description"
          :active="currentSubSection === item.key"
        />
      </ModuleTopbar>

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
          <label class="mark-filter-toggle">
            <input
              v-model="markedFilter"
              type="checkbox"
              :disabled="listLoading"
            >
            <Bookmark
              :size="16"
              :fill="markedFilter ? 'currentColor' : 'none'"
              aria-hidden="true"
            />
            仅显示标记
          </label>
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
              <span class="section-kicker">{{ isRepliesSection ? '我的回复' : '帖子列表' }}</span>
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
            v-else-if="!displayPosts.length"
            class="empty-state"
          >
            <FileText
              :size="40"
              aria-hidden="true"
            />
            <h3>{{ isRepliesSection ? '还没有找到你的回复' : markedFilter ? '当前页面没有标记的帖子' : '还没有匹配的帖子' }}</h3>
            <p>{{ isRepliesSection ? '你回复过的帖子会展示在这里，可以清空筛选条件后再查看。' : markedFilter ? '试试取消仅显示标记，或标记感兴趣的帖子后再次查看。' : '可以清空筛选条件，或发布一条新的校园帖子。' }}</p>
            <button
              class="primary-action"
              type="button"
              @click="isRepliesSection ? resetFilters() : openPublishDialog()"
            >
              <PlusCircle
                :size="18"
                aria-hidden="true"
              />
              {{ isRepliesSection ? '清空筛选' : '发布帖子' }}
            </button>
          </div>

          <div
            v-else
            class="post-list"
          >
            <button
              v-for="post in displayPosts"
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
                    <small>{{ categoryLabel(post.category) }} · {{ authorCopy(post) }}</small>
                  </span>
                  <StatusBadge
                    :status="post.status"
                    :label="postStatusMeta(post.status).label"
                    :tone="postStatusMeta(post.status).tone"
                  />
                </span>
                <span class="post-card-copy">{{ compactContent(post.content) }}</span>
                <span
                  v-if="isRepliesSection && post._myReplies?.length"
                  class="my-reply-snippets"
                >
                  <span
                    v-for="reply in myReplyPreview(post)"
                    :key="`${post.id}-${reply}`"
                  >
                    我回复：{{ reply }}
                  </span>
                </span>
                <span class="post-card-footer">
                  <span>
                    <MessageCircle
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ post.replyCount }} 回复
                  </span>
                  <button
                    class="like-action"
                    :class="{ 'like-action--active': post._likedByCurrentUser }"
                    type="button"
                    :aria-label="post._likedByCurrentUser ? '取消点赞' : '点赞'"
                    @click.stop="togglePostLike(post)"
                  >
                    <ThumbsUp
                      :size="15"
                      :fill="post._likedByCurrentUser ? 'currentColor' : 'none'"
                      aria-hidden="true"
                    />
                    {{ post.likeCount }} 赞
                  </button>
                  <button
                    class="like-action"
                    :class="{ 'like-action--active': post._markedByCurrentUser }"
                    type="button"
                    :aria-label="post._markedByCurrentUser ? '取消标记' : '标记'"
                    @click.stop="togglePostMark(post)"
                  >
                    <Bookmark
                      :size="15"
                      :fill="post._markedByCurrentUser ? 'currentColor' : 'none'"
                      aria-hidden="true"
                    />
                    {{ post.markCount }} 标记
                  </button>
                  <span>
                    <Clock3
                      :size="15"
                      aria-hidden="true"
                    />
                    {{ formatDateTime(post.createdAt) }}
                  </span>
                  <button
                    class="inline-profile-btn"
                    type="button"
                    @click.stop="openProfileModal(post.authorId, post.id)"
                  >
                    <UserRound :size="13" /> 查看
                  </button>
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
      </div>

      <div
        v-if="postDetailModalOpen"
        class="workflow-modal-layer post-detail-layer"
        @click.self="closePostModal"
      >
        <section
          class="panel-card post-detail-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="post-detail-title"
        >
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
                <h2 id="post-detail-title">
                  {{ detailPost.title || '未命名帖子' }}
                </h2>
              </div>
              <div class="detail-header-actions">
                <StatusBadge
                  :status="detailPost.status"
                  :label="postStatusMeta(detailPost.status).label"
                  :tone="postStatusMeta(detailPost.status).tone"
                />
                <button
                  class="icon-action"
                  type="button"
                  aria-label="关闭帖子详情"
                  @click="closePostModal"
                >
                  <X
                    :size="18"
                    aria-hidden="true"
                  />
                </button>
              </div>
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
                <dd>
                  {{ authorCopy(detailPost) }}
                  <button
                    class="inline-profile-btn"
                    type="button"
                    @click="openProfileModal(detailPost.authorId, detailPost.id)"
                  >
                    <UserRound :size="14" /> 查看
                  </button>
                </dd>
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
                <dd>{{ (detailPost.likeCount || 0) + (detailPost.markCount || 0) }}</dd>
              </div>
            </dl>

            <div class="modal-interaction-bar">
              <button
                class="like-button"
                :class="{ 'like-button--active': postDetail?.likedByCurrentUser }"
                type="button"
                :aria-label="postDetail?.likedByCurrentUser ? '取消点赞' : '点赞'"
                @click="toggleDetailLike"
              >
                <ThumbsUp
                  :size="17"
                  :fill="postDetail?.likedByCurrentUser ? 'currentColor' : 'none'"
                  aria-hidden="true"
                />
                <span class="like-count">{{ detailPost.likeCount || 0 }}</span>
              </button>
              <button
                class="like-button"
                :class="{ 'like-button--active': postDetail?.markedByCurrentUser }"
                type="button"
                :aria-label="postDetail?.markedByCurrentUser ? '取消标记' : '标记'"
                @click="toggleDetailMark"
              >
                <Bookmark
                  :size="17"
                  :fill="postDetail?.markedByCurrentUser ? 'currentColor' : 'none'"
                  aria-hidden="true"
                />
                <span class="like-count">{{ detailPost.markCount || 0 }}</span>
              </button>
            </div>

            <section
              v-if="detailAttachments.length"
              class="detail-section"
            >
              <h3>图片</h3>
              <div class="detail-image-grid">
                <a
                  v-for="(attachment, index) in detailAttachments"
                  :key="attachment.id || index"
                  :href="attachment.fileUrl || attachment.url"
                  target="_blank"
                  rel="noreferrer"
                  class="detail-image-grid__link"
                >
                  <img
                    :src="attachment.fileUrl || attachment.url"
                    :alt="attachment.fileName || `帖子图片 ${index + 1}`"
                    class="detail-image-grid__img"
                    loading="lazy"
                  >
                </a>
              </div>
            </section>

            <section class="detail-section">
              <h3>正文</h3>
              <p class="detail-content-text">
                {{ detailPost.content || '暂无正文' }}
              </p>
            </section>

            <section class="detail-section">
              <div class="reply-heading">
                <h3>回复</h3>
                <span>{{ detailReplies.length }} 条</span>
              </div>

              <p
                v-if="!isAuthenticated"
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
                  v-for="reply in threadedReplies"
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
                      {{ authorCopy(reply) }}
                    </span>
                    <small>{{ formatDateTime(reply.createdAt) }}</small>
                  </div>
                  <p>{{ reply.content }}</p>
                  <div class="reply-actions">
                    <span
                      v-if="String(reply.id) === String(postDetail?.acceptedReplyId)"
                      class="accepted-tag"
                    >
                      已采纳
                    </span>
                    <button
                      v-if="isAuthenticated"
                      class="reply-btn"
                      type="button"
                      @click="startReply(reply.id)"
                    >
                      <ReplyIcon :size="14" />
                      回复
                    </button>
                  </div>

                  <form
                    v-if="isAuthenticated && replyComposerOpen && replyingToId === reply.id"
                    class="inline-reply-form"
                    @submit.prevent="submitReply"
                  >
                    <textarea
                      id="nested-reply-input"
                      v-model="replyContent"
                      class="inline-reply-input"
                      :disabled="replySubmitting"
                      maxlength="2000"
                      placeholder="回复这条评论..."
                      :aria-invalid="Boolean(replyError)"
                    />
                    <div class="inline-reply-actions">
                      <small>{{ replyContent.length }}/2000</small>
                      <p
                        v-if="replyError"
                        class="reply-form-error"
                      >
                        {{ replyError }}
                      </p>
                      <button
                        class="reply-cancel-btn"
                        type="button"
                        :disabled="replySubmitting"
                        @click="cancelReply"
                      >
                        取消
                      </button>
                      <button
                        class="reply-submit-btn"
                        type="submit"
                        :disabled="replySubmitting || !replyContent.trim()"
                      >
                        <LoaderCircle
                          v-if="replySubmitting"
                          class="spinning"
                          :size="15"
                          aria-hidden="true"
                        />
                        <Send
                          v-else
                          :size="15"
                          aria-hidden="true"
                        />
                        回复
                      </button>
                    </div>
                  </form>

                  <div
                    v-if="reply.children.length"
                    class="nested-replies"
                  >
                    <article
                      v-for="child in reply.children"
                      :key="child.id"
                      class="reply-card reply-card--nested"
                    >
                      <div class="reply-card-top">
                        <span>
                          <UserRound
                            :size="14"
                            aria-hidden="true"
                          />
                          {{ authorCopy(child) }}
                        </span>
                        <small>{{ formatDateTime(child.createdAt) }}</small>
                      </div>
                      <p>{{ child.content }}</p>
                      <button
                        v-if="isAuthenticated"
                        class="reply-btn"
                        type="button"
                        @click="startReply(child.id)"
                      >
                        <ReplyIcon :size="13" />
                        回复
                      </button>

                      <form
                        v-if="isAuthenticated && replyComposerOpen && replyingToId === child.id"
                        class="inline-reply-form"
                        @submit.prevent="submitReply"
                      >
                        <textarea
                          id="nested-reply-input"
                          v-model="replyContent"
                          class="inline-reply-input"
                          :disabled="replySubmitting"
                          maxlength="2000"
                          placeholder="继续回复..."
                          :aria-invalid="Boolean(replyError)"
                        />
                        <div class="inline-reply-actions">
                          <small>{{ replyContent.length }}/2000</small>
                          <p
                            v-if="replyError"
                            class="reply-form-error"
                          >
                            {{ replyError }}
                          </p>
                          <button
                            class="reply-cancel-btn"
                            type="button"
                            :disabled="replySubmitting"
                            @click="cancelReply"
                          >
                            取消
                          </button>
                          <button
                            class="reply-submit-btn"
                            type="submit"
                            :disabled="replySubmitting || !replyContent.trim()"
                          >
                            <LoaderCircle
                              v-if="replySubmitting"
                              class="spinning"
                              :size="15"
                              aria-hidden="true"
                            />
                            <Send
                              v-else
                              :size="15"
                              aria-hidden="true"
                            />
                            回复
                          </button>
                        </div>
                      </form>
                    </article>
                  </div>
                </article>
              </div>

              <form
                v-if="isAuthenticated && replyComposerOpen && replyingToId == null"
                class="top-reply-form"
                @submit.prevent="submitReply"
              >
                <textarea
                  id="top-reply-input"
                  v-model="replyContent"
                  class="inline-reply-input"
                  :disabled="replySubmitting"
                  maxlength="2000"
                  placeholder="写下你的回复，帮助楼主或参与讨论..."
                  :aria-invalid="Boolean(replyError)"
                />
                <div class="inline-reply-actions">
                  <small>{{ replyContent.length }}/2000</small>
                  <p
                    v-if="replyError"
                    class="reply-form-error"
                  >
                    {{ replyError }}
                  </p>
                  <button
                    class="reply-cancel-btn"
                    type="button"
                    :disabled="replySubmitting"
                    @click="cancelReply"
                  >
                    取消
                  </button>
                  <button
                    class="reply-submit-btn"
                    type="submit"
                    :disabled="replySubmitting || !replyContent.trim()"
                  >
                    <LoaderCircle
                      v-if="replySubmitting"
                      class="spinning"
                      :size="15"
                      aria-hidden="true"
                    />
                    <Send
                      v-else
                      :size="15"
                      aria-hidden="true"
                    />
                    发布回复
                  </button>
                </div>
              </form>

              <button
                v-else-if="isAuthenticated"
                class="start-top-reply-btn"
                type="button"
                @click="startReply(null)"
              >
                <ReplyIcon :size="15" />
                发表回复
              </button>
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
            <h3>帖子详情暂不可用</h3>
            <p>可以先关闭弹层，稍后重新打开试试。</p>
          </div>
        </section>
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

            <fieldset class="attachment-uploader">
              <legend>上传图片</legend>
              <p class="attachment-uploader__hint">
                支持 JPG/PNG/GIF，每张 ≤10MB，最多 {{ MAX_POST_IMAGES }} 张。
              </p>

              <div
                v-if="postAttachments.length"
                class="attachment-uploader__preview-list"
              >
                <div
                  v-for="(item, index) in postAttachments"
                  :key="item.previewUrl || index"
                  class="attachment-uploader__item"
                >
                  <img
                    :src="item.previewUrl"
                    :alt="item.name"
                    class="attachment-uploader__thumb"
                  >
                  <button
                    type="button"
                    class="attachment-uploader__remove"
                    :disabled="postUploading"
                    :aria-label="`移除 ${item.name}`"
                    @click="removePostAttachment(index)"
                  >
                    <X
                      :size="14"
                      aria-hidden="true"
                    />
                  </button>
                </div>
              </div>

              <button
                v-if="postAttachments.length < MAX_POST_IMAGES"
                type="button"
                class="attachment-uploader__trigger"
                :disabled="postUploading || publishing"
                @click="triggerPostImageUpload"
              >
                <ImagePlus
                  :size="20"
                  aria-hidden="true"
                />
                {{ postUploading ? '上传中...' : '选择图片' }}
              </button>

              <input
                ref="postAttachmentFileInput"
                type="file"
                multiple
                accept="image/png,image/jpeg,image/webp"
                hidden
                :disabled="postUploading || publishing"
                @change="handlePostAttachmentFileChange"
              >
            </fieldset>

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

    <UserProfileModal
      :user-id="profileUserId"
      :visible="showProfileModal"
      related-type="POST"
      :related-id="profileRelatedId"
      @close="showProfileModal = false"
    />
  </DashboardShell>
</template>

<style scoped>
.posts-page {
  display: grid;
  gap: 18px;
  width: 100%;
  max-width: none;
  color: var(--color-text);
}

.panel-card,
.page-feedback {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.filter-heading h2,
.list-toolbar h2,
.detail-card-header h2,
.detail-section h3,
.dialog-header h2,
.post-preview h3 {
  margin: 0;
}

.section-kicker {
  color: var(--color-brand);
  font-size: 0.82rem;
  font-weight: 900;
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
  border: 1px solid var(--color-brand);
  background: var(--color-brand);
  color: var(--color-surface);
  box-shadow: 0 14px 26px var(--shadow-button);
}

.primary-action:hover:not(:disabled) {
  background: var(--color-brand-hover);
}

.ghost-action,
.page-button,
.icon-action {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand);
}

.ghost-action {
  padding: 0 12px;
}

.ghost-action:hover:not(:disabled),
.page-button:hover:not(:disabled),
.icon-action:hover:not(:disabled) {
  border-color: var(--color-border);
  background: var(--color-brand-soft);
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
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
  outline: 0;
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  color: var(--color-success-text);
  line-height: 1.55;
}

.page-feedback--success {
  border-color: var(--color-success-soft);
  background: var(--color-success-bg);
}

.page-feedback--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
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
  --metric-bg: var(--color-surface);
  --metric-border: var(--color-border);
  --metric-icon: var(--color-brand);
  --metric-label: var(--color-text-muted);
  --metric-value: var(--color-text);
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
  --metric-bg: var(--color-brand-soft);
  --metric-border: var(--color-brand-border);
  --metric-icon: var(--color-brand);
  --metric-label: var(--color-brand-hover);
  --metric-value: var(--color-brand-text);
}

.metric-tile--replies {
  --metric-bg: var(--color-brand-soft);
  --metric-border: var(--color-info-soft);
  --metric-icon: var(--color-info);
  --metric-label: var(--color-info-text);
  --metric-value: var(--color-info-text);
}

.metric-tile--likes {
  --metric-bg: var(--color-brand-soft);
  --metric-border: var(--color-brand-border);
  --metric-icon: var(--color-brand);
  --metric-label: var(--color-brand-hover);
  --metric-value: var(--color-brand-text);
}

.metric-tile--mine {
  --metric-bg: var(--color-brand-soft);
  --metric-border: var(--color-info-soft);
  --metric-icon: var(--color-info);
  --metric-label: var(--color-info-text);
  --metric-value: var(--color-info-text);
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
  color: var(--color-text);
  font-size: 1.08rem;
}

.filter-count {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
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
  color: var(--color-text);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: 0;
}

.filter-field input,
.filter-field select {
  min-height: 42px;
  padding: 0 12px;
}

.search-field {
  padding-left: 12px;
  color: var(--color-text-muted);
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
  grid-template-columns: 1fr;
  gap: 18px;
  align-items: start;
}

.post-detail-layer {
  z-index: 95;
}

.post-detail-modal {
  width: min(720px, 92vw);
  max-height: 88vh;
  overflow-y: auto;
  display: grid;
  gap: 16px;
}

.detail-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

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
  transition:
    background 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
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

.like-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.like-count {
  font-weight: 900;
  color: inherit;
}

.detail-content-text {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 1.02rem;
  color: var(--color-text);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: inherit;
  text-align: left;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.post-card:hover,
.post-card.selected {
  border-color: var(--color-border);
  background: var(--color-surface-muted);
  box-shadow: 0 14px 28px var(--shadow-card);
}

.post-card.selected::before {
  content: "";
  position: absolute;
  inset: 14px auto 14px 0;
  width: 3px;
  border-radius: 999px;
  background: var(--color-brand);
}

.post-type-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.post-type-icon--SECOND_HAND {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.post-type-icon--TEAM_FRIEND {
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.post-type-icon--CAMPUS_SOCIAL {
  background: var(--color-info-bg);
  color: var(--color-info);
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
  color: var(--color-text);
  font-size: 1.05rem;
  line-height: 1.35;
}

.post-card-heading small,
.post-card-copy,
.post-card-footer,
.detail-section p,
.reply-card p {
  color: var(--color-text-muted);
  line-height: 1.6;
}

.post-card-copy {
  margin: 0;
}

.my-reply-snippets {
  display: grid;
  gap: 6px;
}

.my-reply-snippets span {
  padding: 8px 10px;
  border-left: 3px solid var(--color-brand);
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  line-height: 1.55;
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
  color: var(--color-brand);
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
  color: var(--color-text-muted);
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
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: var(--color-text);
}

.empty-state p {
  max-width: 440px;
  line-height: 1.6;
}

.empty-state--danger {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

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
  content: '：';
}

.detail-facts dd {
  margin: 0;
  color: var(--color-text);
  font-weight: 700;
  font-size: 0.88rem;
  line-height: 1.45;
}

.detail-section {
  display: grid;
  gap: 10px;
}

.inline-profile-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 6px;
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface-muted);
  color: var(--color-brand-text);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease;
  white-space: nowrap;
}

.inline-profile-btn:hover {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
}

.like-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: color 150ms ease;
}

.like-action:hover {
  color: var(--color-brand);
}

.like-action--active {
  color: var(--color-brand);
  font-weight: 700;
}

.like-action--active svg {
  color: var(--color-brand);
  fill: var(--color-brand-soft);
}

.detail-like {
  font-size: 0.92rem;
  font-weight: 600;
}

.detail-mark-count {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 12px;
  color: var(--color-text-secondary);
}

.mark-filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: background 180ms ease, border-color 180ms ease;
}

.mark-filter-toggle:hover {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
}

.mark-filter-toggle input[type="checkbox"] {
  accent-color: var(--color-brand);
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.detail-section h3 {
  color: var(--color-text);
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
  color: var(--color-text-muted);
  font-weight: 900;
}

.reply-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.reply-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface-muted);
  color: var(--color-brand-text);
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 150ms ease,
    border-color 150ms ease;
}

.reply-btn:hover {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
}

.reply-login-hint {
  margin: 0;
  padding: 13px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  text-align: center;
  font-size: 0.92rem;
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

.accepted-tag {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--color-success-bg);
  color: var(--color-success-text);
  font-size: 0.8rem;
  font-weight: 900;
}

.nested-replies {
  display: grid;
  gap: 8px;
  margin-top: 4px;
  padding-left: 16px;
  border-left: 2px solid var(--color-border);
}

.reply-card--nested {
  padding: 10px;
  background: var(--color-surface-muted);
  border-color: var(--color-border);
}

.inline-reply-form {
  display: grid;
  gap: 8px;
  margin-top: 4px;
  padding: 10px;
  border: 1px solid var(--color-brand-border);
  border-radius: 8px;
  background: var(--color-brand-soft);
}

.inline-reply-input {
  width: 100%;
  min-height: 88px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  line-height: 1.55;
  resize: vertical;
  box-sizing: border-box;
}

.inline-reply-input:focus {
  outline: none;
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.inline-reply-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.inline-reply-actions small {
  margin-right: auto;
  color: var(--color-text-muted);
}

.reply-form-error {
  color: var(--color-danger-soft);
  margin: 0;
  font-size: 0.88rem;
}

.reply-cancel-btn {
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
}

.reply-submit-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  border: none;
  border-radius: 6px;
  background: var(--color-brand);
  color: var(--color-surface);
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
}

.reply-submit-btn:disabled,
.reply-cancel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.top-reply-form {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.start-top-reply-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 10px;
  padding: 8px 14px;
  border: 1px dashed var(--color-brand-border);
  border-radius: 6px;
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
}

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

.publish-dialog {
  width: min(760px, calc(100vw - 44px));
  max-height: calc(100vh - 44px);
  overflow: auto;
  box-shadow: var(--shadow-elevated);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  text-align: left;
}

.choice-control svg {
  color: var(--color-brand);
}

.choice-control span {
  display: grid;
  gap: 3px;
}

.choice-control strong {
  color: var(--color-text);
}

.choice-control small {
  color: var(--color-text-muted);
  line-height: 1.4;
}

.choice-control:hover,
.choice-control.selected {
  border-color: var(--color-border);
  background: var(--color-surface-muted);
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
  border-color: var(--color-danger-soft);
}

.publish-field small {
  color: var(--color-text-muted);
}

.field-error {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.88rem;
  font-weight: 800;
  line-height: 1.45;
}

.post-preview {
  display: grid;
  gap: 9px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.post-preview-heading span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-brand);
  font-weight: 900;
}

.post-preview-heading small {
  color: var(--color-text-muted);
}

.post-preview h3 {
  color: var(--color-text);
  font-size: 1rem;
}

.post-preview p {
  margin: 0;
  color: var(--color-text-muted);
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

@media (max-width: 900px) {
  .metric-strip,
  .filter-grid,
  .detail-facts,
  .category-selector {
    grid-template-columns: 1fr;
  }

  .filter-heading,
  .list-toolbar,
  .detail-card-header,
  .detail-header-actions,
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

.attachment-uploader {
  display: grid;
  gap: 10px;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.attachment-uploader legend {
  color: var(--color-text);
  font-weight: 900;
}

.attachment-uploader__hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.attachment-uploader__preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.attachment-uploader__item {
  position: relative;
  width: 88px;
  height: 88px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.attachment-uploader__thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.attachment-uploader__remove {
  position: absolute;
  top: 3px;
  right: 3px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transition: opacity 150ms ease;
}

.attachment-uploader__item:hover .attachment-uploader__remove {
  opacity: 1;
}

.attachment-uploader__remove:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.attachment-uploader__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: fit-content;
  min-height: 42px;
  padding: 0 16px;
  border: 1px dashed var(--color-brand-border);
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
  font-weight: 900;
  cursor: pointer;
  transition:
    background 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.attachment-uploader__trigger:hover:not(:disabled) {
  background: var(--color-brand);
  border-color: var(--color-brand);
  color: var(--color-surface);
}

.attachment-uploader__trigger:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

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

@media (max-width: 640px) {
  .detail-image-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .detail-image-grid__img {
    height: 110px;
  }

  .attachment-uploader__item {
    width: 72px;
    height: 72px;
  }

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

  .post-detail-modal {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }

  .publish-dialog {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }
}
</style>

