<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  AtSign,
  BadgeCheck,
  Bell,
  Bookmark,
  BookOpen,
  Briefcase,
  Building2,
  CalendarClock,
  ChartLine,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  CheckCircle2,
  ClipboardList,
  Grid2X2,
  Home,
  Eye,
  IdCard,
  ImagePlus,
  Info,
  LoaderCircle,
  LogIn,
  LogOut,
  MapPin,
  MessageCircle,
  PlusCircle,
  RefreshCw,
  Save,
  School,
  Search,
  Shield,
  ShieldCheck,
  Star,
  Tag,
  UserPen,
  UserRound,
  X
} from '@lucide/vue'
import StatusBadge from '../components/StatusBadge.vue'
import { useDashboardSidebar } from '../composables/useDashboardSidebar'
import { useNotificationCenter } from '../composables/useNotificationCenter'
import { optionLabel, STATUS_OPTIONS, VERIFICATION_METHODS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const {
  state,
  isAuthenticated,
  currentUserId,
  verificationStatus,
  logout,
  refreshSession,
  submitVerification,
  resendEmailVerificationCode,
  confirmEmailVerification,
  updateMyProfile
} = useAuth()

const router = useRouter()
const { sidebarCollapsed, toggleSidebarCollapsed } = useDashboardSidebar()
const { unreadCount: notificationUnreadCount, refreshNotificationSummary } = useNotificationCenter()
const accountMenuOpen = ref(false)
const accountMenuRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const feedback = ref('')
const feedbackType = ref('success')
const avatarLoadFailed = ref(false)

// --- Marked posts ---
const markedPosts = ref([])
const markedPostsLoading = ref(false)
const markedPostsLoaded = ref(false)

async function loadMarkedPosts() {
  if (markedPostsLoaded.value || markedPostsLoading.value) return
  markedPostsLoading.value = true
  try {
    const result = await api.getMarkedPosts()
    markedPosts.value = Array.isArray(result) ? result : []
    markedPostsLoaded.value = true
  } catch {
    markedPosts.value = []
  } finally {
    markedPostsLoading.value = false
  }
}

function goToPost(postId) {
  router.push({ name: 'post-list', query: { postId: String(postId) } })
}

const isAdmin = computed(() => String(state.user?.role || '').toUpperCase() === 'ADMIN')

const navItems = computed(() => [
  { label: '首页推荐', icon: Home, to: '/' },
  { label: '个人主页', icon: Home, active: true, to: '/home' },
  { label: '帖子广场', icon: BookOpen, to: '/posts' },
  { label: '任务广场', icon: Grid2X2, to: '/tasks' },
  { label: '发布任务', icon: PlusCircle, to: '/tasks/new' },
  { label: '我的任务', icon: ClipboardList, to: '/tasks/mine' },
  { label: '站内沟通', icon: MessageCircle, to: '/conversations' },
  { label: '评价中心', icon: Star, to: '/reviews' },
  ...(isAdmin.value ? [{ label: '管理后台', icon: Shield, to: '/admin' }] : [])
])

const verificationForm = reactive({
  method: 'STUDENT_CARD_IMAGE',
  realName: '',
  studentNumber: '',
  school: '',
  department: '',
  campusEmail: '',
  evidenceUrl: ''
})

const profileEditing = ref(false)
const profileSubmitting = ref(false)
const profileErrors = reactive({})
const avatarUploading = ref(false)
const avatarFileInput = ref(null)
const verificationEvidenceFileInput = ref(null)
const verificationEvidenceUploading = ref(false)
const verificationEvidenceFileName = ref('')
const emailVerificationCode = ref('')
const emailCodeSubmitting = ref(false)
const emailCodeResending = ref(false)
const searchKeyword = ref('')

function handleSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  router.push({ name: 'task-list', query: { keyword } })
  searchKeyword.value = ''
}
const profileForm = reactive({
  nickname: '',
  bio: '',
  campus: '',
  college: '',
  avatarUrl: '',
  contactInfo: '',
  interestTagsText: '',
  skillTagsText: ''
})

const publicProfileItems = [
  '昵称、头像、个人简介',
  '校区、学院、兴趣和技能标签',
  '校园认证状态、信用等级和积分'
]

const privateProfileItems = [
  '真实姓名、学号和认证材料',
  '联系方式仅在自己的档案页展示',
  '账号启停状态和注册时间'
]

const userName = computed(() =>
  isAuthenticated.value ? state.profile?.nickname || state.user?.username || '张同学' : '未登录'
)
const accountMeta = computed(() => {
  if (!isAuthenticated.value) return '登录后同步认证和任务记录'
  return state.profile?.campus || state.profile?.college || state.user?.role || '校园互助账号'
})
const notificationBadgeCount = computed(() => notificationUnreadCount.value)
const notificationBadgeLabel = computed(() =>
  notificationBadgeCount.value > 99 ? '99+' : String(notificationBadgeCount.value)
)
const myProfile = computed(() => state.profile || {})
const interestTags = computed(() => normalizeTags(myProfile.value.interestTags))
const skillTags = computed(() => normalizeTags(myProfile.value.skillTags))
const profileUserId = computed(() => myProfile.value.userId || state.user?.id || state.user?.userId || '待同步')
const profileAvatarUrl = computed(() => String(myProfile.value.avatarUrl || '').trim())
const avatarPreviewUrl = computed(() => String(profileEditing.value ? profileForm.avatarUrl : profileAvatarUrl.value).trim())
const profileAvatarSrc = computed(() => {
  if (!avatarPreviewUrl.value || avatarLoadFailed.value) return ''
  if (/^https?:\/\/example\.com\//i.test(avatarPreviewUrl.value)) return ''
  return avatarPreviewUrl.value
})
const profileInitial = computed(() => {
  const name = String(userName.value || '').trim()
  return name ? name.slice(0, 1) : '同'
})
const accountStatusLabel = computed(() => (state.user?.enabled === false ? '已停用' : '正常启用'))
const contactDisplay = computed(() => myProfile.value.contactInfo || '尚未填写，仅自己可见')
const creditLevel = computed(() => myProfile.value.creditLevel ?? 1)
const creditPoints = computed(() => myProfile.value.points ?? 0)

const levelThresholds = [0, 60, 80, 90, 100]

const nextLevelThreshold = computed(() => {
  const currentLevel = creditLevel.value
  if (currentLevel >= 5) return 100
  return levelThresholds[currentLevel] ?? 60
})

const prevLevelThreshold = computed(() => {
  const currentLevel = creditLevel.value
  if (currentLevel <= 1) return 0
  return levelThresholds[currentLevel - 1] ?? 0
})

const levelProgressPercent = computed(() => {
  if (creditLevel.value >= 5) return 100
  const range = nextLevelThreshold.value - prevLevelThreshold.value
  if (range <= 0) return 0
  const progress = creditPoints.value - prevLevelThreshold.value
  return Math.min(100, Math.max(0, Math.round((progress / range) * 100)))
})

const levelLabel = computed(() => {
  const labels = ['', '新手上路', '靠谱帮手', '校园达人', '互助之星']
  return labels[creditLevel.value] || '新手上路'
})

const profileCompletion = computed(() => {
  const profile = myProfile.value
  const filledCount = [
    profile.nickname,
    profile.bio,
    profile.campus,
    profile.college,
    profile.avatarUrl,
    profile.contactInfo,
    interestTags.value.length,
    skillTags.value.length
  ].filter(Boolean).length

  return Math.round((filledCount / 8) * 100)
})
const profileMetaCards = computed(() => [
  {
    label: '用户 ID',
    value: profileUserId.value,
    icon: IdCard
  },
  {
    label: '用户名',
    value: state.user?.username || '未登录',
    icon: ShieldCheck
  },
  {
    label: '账号状态',
    value: accountStatusLabel.value,
    icon: BadgeCheck
  },
  {
    label: '注册时间',
    value: formatDate(state.user?.createdAt),
    icon: CalendarClock
  }
])
const publicIdentityRows = computed(() => [
  {
    label: '校区',
    value: myProfile.value.campus || '未填写',
    icon: MapPin
  },
  {
    label: '学院',
    value: myProfile.value.college || '未填写',
    icon: Building2
  },
  {
    label: '联系方式',
    value: contactDisplay.value,
    icon: AtSign
  }
])
const verification = computed(() => state.verification || null)
const isEmailVerificationMethod = computed(() => verificationForm.method === 'CAMPUS_EMAIL')
const requiresVerificationEvidence = computed(() =>
  ['STUDENT_CARD_IMAGE', 'MANUAL_REVIEW'].includes(verificationForm.method)
)
const pendingEmailVerification = computed(
  () => verificationStatus.value === 'PENDING' && verification.value?.method === 'CAMPUS_EMAIL'
)
const canSubmitVerification = computed(() => {
  if (!isAuthenticated.value) return false
  return !verification.value || ['UNVERIFIED', 'REJECTED'].includes(verificationStatus.value)
})
const lockMessage = computed(() => {
  if (!isAuthenticated.value) return '请先登录账号后再提交校内认证。'
  if (verificationStatus.value === 'PENDING') return '认证资料已提交，请等待审核结果。'
  if (verificationStatus.value === 'VERIFIED') return '你已完成校内认证，无需重复提交。'
  return ''
})
const statusCopy = computed(() => {
  const status = verificationStatus.value
  if (status === 'VERIFIED') return '你已完成校内认证，可以更安心地参与校园互助。'
  if (pendingEmailVerification.value) return '验证码已发送至校园邮箱，请输入验证码完成认证。'
  if (status === 'PENDING') return '你的认证资料正在审核中，通常需要一点时间，请耐心等待。'
  if (status === 'REJECTED') return '认证未通过，请查看原因后重新提交清晰、有效的资料。'
  return '请提交真实的校园身份信息。认证通过后，其他同学只会看到你的认证状态，不会看到学号等敏感信息。'
})
const stepItems = computed(() => {
  const hasRecord = Boolean(verification.value)
  const verified = verificationStatus.value === 'VERIFIED'
  return [
    { label: '登录账号', done: isAuthenticated.value },
    { label: '提交资料', done: hasRecord },
    { label: '平台审核', done: hasRecord && verificationStatus.value !== 'UNVERIFIED' },
    { label: '认证通过', done: verified }
  ]
})

const verificationBenefits = [
  {
    title: '可发布校园任务',
    copy: '通过认证后，可发布任务，寻求他人帮助。',
    icon: Briefcase
  },
  {
    title: '可接取校园任务',
    copy: '通过认证后，可接取任务，获得互助机会。',
    icon: UserRound
  },
  {
    title: '获得校园身份标识',
    copy: '认证通过后，获得专属身份标识。',
    icon: ShieldCheck
  },
  {
    title: '提升账号可信度',
    copy: '提升账号可信度，增加他人信任。',
    icon: ChartLine
  }
]

const verificationEvidenceLabel = computed(() =>
  verificationForm.method === 'MANUAL_REVIEW' ? '人工审核照片' : '学生证照片'
)

const verificationEvidenceHint = computed(() =>
  verificationForm.method === 'MANUAL_REVIEW'
    ? '上传能够证明校园身份的照片，管理员会人工审核。'
    : '上传清晰的学生证照片，系统会尝试自动识别并提交审核。'
)

function setFeedback(message, type = 'success') {
  feedback.value = message
  feedbackType.value = type
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

function toggleAccountMenu() {
  accountMenuOpen.value = !accountMenuOpen.value
}

function handleDocumentPointerDown(event) {
  if (!accountMenuOpen.value || !accountMenuRef.value) return
  if (!accountMenuRef.value.contains(event.target)) closeAccountMenu()
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') closeAccountMenu()
}

async function handleLogout() {
  logout()
  closeAccountMenu()
  await router.push('/auth')
}

function formatDate(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function normalizeTags(value) {
  if (Array.isArray(value)) {
    return [...new Set(value.map((item) => String(item).trim()).filter(Boolean))]
  }

  if (typeof value === 'string') {
    return [
      ...new Set(
        value
          .split(/[,，、;\n]+/)
          .map((item) => item.trim())
          .filter(Boolean)
      )
    ]
  }

  return []
}

function hydrateProfileForm(profile = {}) {
  profileForm.nickname = profile.nickname || ''
  profileForm.bio = profile.bio || ''
  profileForm.campus = profile.campus || ''
  profileForm.college = profile.college || ''
  profileForm.avatarUrl = profile.avatarUrl || ''
  profileForm.contactInfo = profile.contactInfo || ''
  profileForm.interestTagsText = normalizeTags(profile.interestTags).join('，')
  profileForm.skillTagsText = normalizeTags(profile.skillTags).join('，')
}

function clearProfileErrors() {
  Object.keys(profileErrors).forEach((key) => {
    delete profileErrors[key]
  })
}

function setProfileError(key, message) {
  profileErrors[key] = message
}

function validateTextLength(key, label, maxLength) {
  if (profileForm[key].trim().length > maxLength) {
    setProfileError(key, `${label}不能超过 ${maxLength} 个字符。`)
  }
}

function validateTags(key, label) {
  const tags = normalizeTags(profileForm[key])

  if (tags.length > 20) {
    setProfileError(key, `${label}最多填写 20 个。`)
  }

  if (tags.some((tag) => tag.length > 30)) {
    setProfileError(key, `${label}中单个标签不能超过 30 个字符。`)
  }

  return tags
}

function validateProfileForm() {
  clearProfileErrors()
  validateTextLength('nickname', '昵称', 50)
  validateTextLength('bio', '个人简介', 500)
  validateTextLength('campus', '校区', 100)
  validateTextLength('college', '学院', 100)
  validateTextLength('contactInfo', '联系方式', 255)

  const interestTagsPayload = validateTags('interestTagsText', '兴趣标签')
  const skillTagsPayload = validateTags('skillTagsText', '技能标签')

  return {
    valid: Object.keys(profileErrors).length === 0,
    payload: {
      nickname: profileForm.nickname.trim(),
      bio: profileForm.bio.trim(),
      campus: profileForm.campus.trim(),
      college: profileForm.college.trim(),
      contactInfo: profileForm.contactInfo.trim(),
      avatarUrl: profileForm.avatarUrl.trim(),
      interestTags: interestTagsPayload,
      skillTags: skillTagsPayload
    }
  }
}

function handleEditProfile() {
  if (!isAuthenticated.value) {
    setFeedback('请先登录后再编辑个人档案。', 'danger')
    return
  }

  hydrateProfileForm(myProfile.value)
  clearProfileErrors()
  feedback.value = ''
  profileEditing.value = true
}

function handleCancelProfileEdit() {
  hydrateProfileForm(myProfile.value)
  clearProfileErrors()
  profileEditing.value = false
}

function handleAvatarError() {
  avatarLoadFailed.value = true
}

function triggerAvatarUpload() {
  avatarFileInput.value?.click()
}

async function handleAvatarFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return

  avatarUploading.value = true
  feedback.value = ''
  try {
    const result = await api.uploadAvatar(file)
    const url = typeof result === 'string' ? result : result?.avatarUrl
    if (url) {
      profileForm.avatarUrl = url
      avatarLoadFailed.value = false
      if (state.profile) {
        state.profile.avatarUrl = url
      }
      setFeedback('头像已上传并更新。')
    }
  } catch (error) {
    setFeedback(error.message || '头像上传失败，请稍后重试。', 'danger')
  } finally {
    avatarUploading.value = false
    if (avatarFileInput.value) {
      avatarFileInput.value.value = ''
    }
  }
}

function triggerVerificationEvidenceUpload() {
  verificationEvidenceFileInput.value?.click()
}

async function handleVerificationEvidenceFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return

  verificationEvidenceUploading.value = true
  feedback.value = ''
  try {
    const result = await api.uploadAttachment({
      file,
      expectedOwnerType: 'VERIFICATION',
      type: 'IMAGE'
    })
    const url = result?.fileUrl || result?.url
    if (!url) {
      throw new Error('上传成功但未返回材料地址。')
    }
    verificationForm.evidenceUrl = url
    verificationEvidenceFileName.value = file.name
    setFeedback('认证材料照片已上传。')
  } catch (error) {
    setFeedback(error.message || '认证材料上传失败，请稍后重试。', 'danger')
  } finally {
    verificationEvidenceUploading.value = false
    if (verificationEvidenceFileInput.value) {
      verificationEvidenceFileInput.value.value = ''
    }
  }
}

function isValidEmail(value) {
  return /^[^@\s]+@[^@\s]*\.edu\.cn$/i.test(String(value || '').trim())
}

function formatEmailCodeFeedback(result) {
  if (result?.debugCode) {
    return `邮件服务未配置，开发验证码：${result.debugCode}`
  }
  return '验证码已发送至校园邮箱，请输入验证码完成认证。'
}

async function handleSubmitProfile() {
  if (!isAuthenticated.value) {
    setFeedback('请先登录后再保存个人档案。', 'danger')
    return
  }

  const { valid, payload } = validateProfileForm()
  if (!valid) {
    setFeedback('请检查档案表单中标出的内容。', 'danger')
    return
  }

  profileSubmitting.value = true
  feedback.value = ''
  try {
    const savedProfile = await updateMyProfile(payload)
    hydrateProfileForm(savedProfile || {})
    profileEditing.value = false
    setFeedback('个人档案已保存，公开主页信息会同步更新。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    profileSubmitting.value = false
  }
}

async function handleRefresh() {
  if (!isAuthenticated.value) return
  loading.value = true
  feedback.value = ''
  try {
    await refreshSession()
    setFeedback('个人档案与认证状态已刷新。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    loading.value = false
  }
}

async function handleSubmitVerification() {
  if (requiresVerificationEvidence.value && !verificationForm.evidenceUrl) {
    setFeedback(`请先上传${verificationEvidenceLabel.value}。`, 'danger')
    return
  }
  if (isEmailVerificationMethod.value && !isValidEmail(verificationForm.campusEmail)) {
    setFeedback('请填写有效的校园邮箱地址。', 'danger')
    return
  }

  submitting.value = true
  feedback.value = ''
  try {
    const result = await submitVerification({
      method: verificationForm.method,
      realName: verificationForm.realName,
      studentNumber: verificationForm.studentNumber,
      school: verificationForm.school,
      department: verificationForm.department,
      campusEmail: verificationForm.campusEmail,
      evidenceUrl: verificationForm.evidenceUrl
    })
    if (isEmailVerificationMethod.value) {
      setFeedback(formatEmailCodeFeedback(result))
    } else {
      await refreshSession()
      setFeedback('校内认证资料已提交，等待平台审核。')
    }
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    submitting.value = false
  }
}

async function handleConfirmEmailCode() {
  const code = emailVerificationCode.value.trim()
  if (!/^\d{6}$/.test(code)) {
    setFeedback('请输入 6 位邮箱验证码。', 'danger')
    return
  }

  emailCodeSubmitting.value = true
  feedback.value = ''
  try {
    await confirmEmailVerification({ code })
    await refreshSession()
    emailVerificationCode.value = ''
    setFeedback('校园邮箱认证已完成。')
  } catch (error) {
    setFeedback(error.message || '验证码确认失败，请重新检查。', 'danger')
  } finally {
    emailCodeSubmitting.value = false
  }
}

async function handleResendEmailCode() {
  emailCodeResending.value = true
  feedback.value = ''
  try {
    const result = await resendEmailVerificationCode()
    setFeedback(formatEmailCodeFeedback(result))
  } catch (error) {
    setFeedback(error.message || '验证码发送失败，请稍后重试。', 'danger')
  } finally {
    emailCodeResending.value = false
  }
}

watch(
  () => state.profile,
  (profile) => {
    if (!profileEditing.value) hydrateProfileForm(profile || {})
  },
  { immediate: true, deep: true }
)

watch(avatarPreviewUrl, () => {
  avatarLoadFailed.value = false
})

watch(
  () => verificationForm.method,
  () => {
    verificationForm.evidenceUrl = ''
    verificationEvidenceFileName.value = ''
    emailVerificationCode.value = ''
  }
)

watch(
  [isAuthenticated, currentUserId],
  () => {
    refreshNotificationSummary()
  },
  { immediate: true }
)

onMounted(async () => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
  if (!isAuthenticated.value) return
  loading.value = true
  try {
    await refreshSession()
    loadMarkedPosts()
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <section
    class="dashboard-shell"
    :class="{ 'is-collapsed': sidebarCollapsed }"
  >
    <aside class="dashboard-sidebar">
      <button
        class="collapse-button"
        type="button"
        :aria-label="sidebarCollapsed ? '展开导航栏' : '折叠导航栏'"
        @click="toggleSidebarCollapsed"
      >
        <ChevronsRight
          v-if="sidebarCollapsed"
          :size="18"
          aria-hidden="true"
        />
        <ChevronsLeft
          v-else
          :size="18"
          aria-hidden="true"
        />
      </button>

      <a
        class="brand"
        href="/home"
        aria-label="CampusBridge"
      >
        <span class="brand-mark">
          <School
            :size="38"
            aria-hidden="true"
          />
        </span>
        <span class="brand-text">CampusBridge</span>
      </a>

      <nav
        class="sidebar-nav"
        aria-label="主导航"
      >
        <RouterLink
          v-for="item in navItems"
          :key="item.label"
          class="nav-item"
          :class="{ active: item.active }"
          :to="item.to"
          :title="sidebarCollapsed ? item.label : undefined"
        >
          <component
            :is="item.icon"
            :size="23"
            aria-hidden="true"
          />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <header class="dashboard-topbar">
      <form
        class="search-box"
        @submit.prevent="handleSearch"
      >
        <Search
          :size="22"
          aria-hidden="true"
        />
        <input
          v-model.trim="searchKeyword"
          type="search"
          placeholder="搜索任务、技能、地点或关键词"
          aria-label="搜索任务、技能、地点或关键词"
        >
      </form>

      <div class="topbar-account">
        <RouterLink
          class="notice-button"
          to="/notifications"
          :aria-label="notificationBadgeCount ? `消息通知，${notificationBadgeCount} 条未读` : '消息通知'"
        >
          <Bell
            :size="25"
            aria-hidden="true"
          />
          <span v-if="notificationBadgeCount > 0">{{ notificationBadgeLabel }}</span>
        </RouterLink>
        <div class="topbar-divider" />
        <div
          ref="accountMenuRef"
          class="account-menu"
        >
          <button
            class="account-button"
            :class="{ active: accountMenuOpen }"
            type="button"
            aria-haspopup="menu"
            :aria-expanded="accountMenuOpen"
            aria-controls="account-menu-list"
            @click.stop="toggleAccountMenu"
          >
            <span class="avatar">
              <img
                v-if="profileAvatarSrc"
                :src="profileAvatarSrc"
                :alt="`${userName}的头像`"
                referrerpolicy="no-referrer"
                @error="handleAvatarError"
              >
              <span v-else>{{ profileInitial }}</span>
              <UserRound
                v-if="!profileAvatarSrc && !profileInitial"
                :size="24"
                aria-hidden="true"
              />
            </span>
            <span>{{ userName }}</span>
            <ChevronDown
              class="account-chevron"
              :size="19"
              aria-hidden="true"
            />
          </button>

          <div
            v-if="accountMenuOpen"
            id="account-menu-list"
            class="account-dropdown"
            role="menu"
          >
            <div class="account-dropdown-head">
              <span class="avatar avatar--compact">
                <img
                  v-if="profileAvatarSrc"
                  :src="profileAvatarSrc"
                  :alt="`${userName}的头像`"
                  referrerpolicy="no-referrer"
                  @error="handleAvatarError"
                >
                <span v-else>{{ profileInitial }}</span>
                <UserRound
                  v-if="!profileAvatarSrc && !profileInitial"
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <div>
                <strong>{{ userName }}</strong>
                <small>{{ accountMeta }}</small>
              </div>
            </div>

            <RouterLink
              v-if="isAuthenticated"
              class="account-menu-item"
              to="/home"
              role="menuitem"
              @click="closeAccountMenu"
            >
              <ShieldCheck
                :size="17"
                aria-hidden="true"
              />
              认证中心
            </RouterLink>
            <RouterLink
              v-if="isAuthenticated"
              class="account-menu-item"
              to="/tasks/mine"
              role="menuitem"
              @click="closeAccountMenu"
            >
              <ClipboardList
                :size="17"
                aria-hidden="true"
              />
              我的任务
            </RouterLink>
            <button
              v-if="isAuthenticated"
              class="account-menu-item account-menu-item--danger"
              type="button"
              role="menuitem"
              @click="handleLogout"
            >
              <LogOut
                :size="17"
                aria-hidden="true"
              />
              退出登录
            </button>
            <RouterLink
              v-else
              class="account-menu-item"
              to="/auth"
              role="menuitem"
              @click="closeAccountMenu"
            >
              <LogIn
                :size="17"
                aria-hidden="true"
              />
              登录 / 注册
            </RouterLink>
          </div>
        </div>
      </div>
    </header>

    <main class="dashboard-main">
      <section class="page-title-card">
        <div class="title-icon">
          <IdCard
            :size="32"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>个人主页</p>
          <h1>我的身份档案与校园认证</h1>
          <span>完善公开档案、身份标签和认证资料，让每次校园互助都更可信。</span>
        </div>
        <StatusBadge :status="verificationStatus" />
      </section>

      <div
        v-if="feedback"
        class="page-feedback"
        :class="feedbackType === 'danger' ? 'page-feedback--danger' : 'page-feedback--success'"
        role="status"
      >
        <BadgeCheck
          :size="18"
          aria-hidden="true"
        />
        <span>{{ feedback }}</span>
      </div>

      <section class="content-grid">
        <article class="panel-card identity-profile-card">
          <div class="profile-hero">
            <button
              class="profile-avatar-large"
              type="button"
              :aria-label="profileAvatarSrc ? '更换头像' : '上传头像'"
              :disabled="avatarUploading"
              @click="triggerAvatarUpload"
            >
              <img
                v-if="profileAvatarSrc"
                :src="profileAvatarSrc"
                :alt="`${userName}的头像`"
                referrerpolicy="no-referrer"
                @error="handleAvatarError"
              >
              <span v-else>{{ profileInitial }}</span>
              <span class="avatar-upload-overlay">
                <LoaderCircle
                  v-if="avatarUploading"
                  class="spinning"
                  :size="22"
                  aria-hidden="true"
                />
                <ImagePlus
                  v-else
                  :size="22"
                  aria-hidden="true"
                />
              </span>
            </button>
            <input
              ref="avatarFileInput"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              hidden
              @change="handleAvatarFileChange"
            >

            <div class="profile-main-copy">
              <div class="section-kicker-row">
                <p class="section-kicker">
                  身份档案
                </p>
                <div class="credit-badge">
                  <span class="credit-level-icon">
                    <ChartLine
                      :size="15"
                      aria-hidden="true"
                    />
                  </span>
                  <span class="credit-level-text">Lv.{{ creditLevel }} {{ levelLabel }}</span>
                  <span class="credit-points">{{ creditPoints }} 积分</span>
                </div>
              </div>
              <div class="credit-progress-track">
                <span
                  class="credit-progress-fill"
                  :style="{ width: `${levelProgressPercent}%` }"
                />
              </div>
              <h2>{{ userName }}</h2>
              <span>{{ myProfile.bio || '还没有填写个人简介。' }}</span>
              <div class="profile-chip-row">
                <span>
                  <MapPin
                    :size="15"
                    aria-hidden="true"
                  />
                  {{ myProfile.campus || '未填写校区' }}
                </span>
                <span>
                  <Building2
                    :size="15"
                    aria-hidden="true"
                  />
                  {{ myProfile.college || '未填写学院' }}
                </span>
                <span>
                  <ShieldCheck
                    :size="15"
                    aria-hidden="true"
                  />
                  {{ optionLabel(STATUS_OPTIONS, verificationStatus) }}
                </span>
              </div>
            </div>

            <button
              class="ghost-action profile-edit-shortcut"
              type="button"
              :disabled="profileSubmitting || profileEditing"
              @click="handleEditProfile"
            >
              <UserPen
                :size="17"
                aria-hidden="true"
              />
              {{ profileEditing ? '正在编辑' : '编辑档案' }}
            </button>
          </div>

          <div class="profile-progress-block">
            <div>
              <span>档案完整度</span>
              <strong>{{ profileCompletion }}%</strong>
            </div>
            <span class="profile-progress-track">
              <span :style="{ width: `${profileCompletion}%` }" />
            </span>
          </div>

          <dl class="profile-meta-grid">
            <div
              v-for="item in profileMetaCards"
              :key="item.label"
            >
              <dt>
                <component
                  :is="item.icon"
                  :size="16"
                  aria-hidden="true"
                />
                {{ item.label }}
              </dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </article>

        <article class="panel-card profile-editor-card">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                档案编辑
              </p>
              <h2>用户身份信息</h2>
            </div>
            <button
              class="ghost-action"
              type="button"
              :disabled="loading || profileSubmitting"
              @click="handleRefresh"
            >
              <RefreshCw
                :class="{ spinning: loading }"
                :size="17"
                aria-hidden="true"
              />
              刷新
            </button>
          </div>

          <form
            class="profile-form"
            @submit.prevent="handleSubmitProfile"
          >
            <label class="field">
              <span>昵称</span>
              <input
                v-model.trim="profileForm.nickname"
                maxlength="50"
                placeholder="例如：林沐妍"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.nickname)"
              >
              <small
                v-if="profileErrors.nickname"
                class="field-error"
              >{{ profileErrors.nickname }}</small>
            </label>

            <label class="field">
              <span>校区</span>
              <input
                v-model.trim="profileForm.campus"
                maxlength="100"
                placeholder="例如：主校区"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.campus)"
              >
              <small
                v-if="profileErrors.campus"
                class="field-error"
              >{{ profileErrors.campus }}</small>
            </label>

            <label class="field field-wide">
              <span>学院</span>
              <input
                v-model.trim="profileForm.college"
                maxlength="100"
                placeholder="例如：计算机学院"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.college)"
              >
              <small
                v-if="profileErrors.college"
                class="field-error"
              >{{ profileErrors.college }}</small>
            </label>

            <label class="field field-wide">
              <span>个人简介</span>
              <textarea
                v-model.trim="profileForm.bio"
                maxlength="500"
                rows="4"
                placeholder="写下专业、常活动地点或擅长帮助的事情"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.bio)"
              />
              <small
                v-if="profileErrors.bio"
                class="field-error"
              >{{ profileErrors.bio }}</small>
            </label>

            <label class="field field-wide">
              <span>联系方式</span>
              <input
                v-model.trim="profileForm.contactInfo"
                maxlength="255"
                placeholder="例如：站内消息优先"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.contactInfo)"
              >
              <small
                v-if="profileErrors.contactInfo"
                class="field-error"
              >{{ profileErrors.contactInfo }}</small>
            </label>

            <label class="field">
              <span>兴趣标签</span>
              <input
                v-model.trim="profileForm.interestTagsText"
                placeholder="跑腿互助，二手教材，摄影"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.interestTagsText)"
              >
              <small
                v-if="profileErrors.interestTagsText"
                class="field-error"
              >{{ profileErrors.interestTagsText }}</small>
            </label>

            <label class="field">
              <span>技能标签</span>
              <input
                v-model.trim="profileForm.skillTagsText"
                placeholder="Java，PPT排版，文档整理"
                :disabled="!profileEditing || profileSubmitting"
                :aria-invalid="Boolean(profileErrors.skillTagsText)"
              >
              <small
                v-if="profileErrors.skillTagsText"
                class="field-error"
              >{{ profileErrors.skillTagsText }}</small>
            </label>

            <div class="profile-form-actions">
              <template v-if="profileEditing">
                <button
                  class="primary-action"
                  type="submit"
                  :disabled="profileSubmitting"
                >
                  <LoaderCircle
                    v-if="profileSubmitting"
                    class="spinning"
                    :size="18"
                    aria-hidden="true"
                  />
                  <Save
                    v-else
                    :size="18"
                    aria-hidden="true"
                  />
                  保存档案
                </button>
                <button
                  class="ghost-action"
                  type="button"
                  :disabled="profileSubmitting"
                  @click="handleCancelProfileEdit"
                >
                  <X
                    :size="17"
                    aria-hidden="true"
                  />
                  取消
                </button>
              </template>
              <button
                v-else
                class="primary-action"
                type="button"
                @click="handleEditProfile"
              >
                <UserPen
                  :size="18"
                  aria-hidden="true"
                />
                编辑用户档案
              </button>
            </div>
          </form>
        </article>

        <article class="panel-card identity-visibility-card">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                主页可见性
              </p>
              <h2>公开身份预览</h2>
            </div>
            <Eye
              :size="22"
              aria-hidden="true"
            />
          </div>

          <dl class="identity-row-list">
            <div
              v-for="item in publicIdentityRows"
              :key="item.label"
            >
              <dt>
                <component
                  :is="item.icon"
                  :size="16"
                  aria-hidden="true"
                />
                {{ item.label }}
              </dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>

          <div class="identity-tag-block">
            <div>
              <span>
                <Tag
                  :size="15"
                  aria-hidden="true"
                />
                兴趣标签
              </span>
              <div class="tag-list">
                <span
                  v-for="tag in interestTags"
                  :key="`interest-${tag}`"
                  class="soft-tag"
                >{{ tag }}</span>
                <span
                  v-if="!interestTags.length"
                  class="soft-tag soft-tag--muted"
                >未填写</span>
              </div>
            </div>
            <div>
              <span>
                <Tag
                  :size="15"
                  aria-hidden="true"
                />
                技能标签
              </span>
              <div class="tag-list">
                <span
                  v-for="tag in skillTags"
                  :key="`skill-${tag}`"
                  class="soft-tag soft-tag--blue"
                >{{ tag }}</span>
                <span
                  v-if="!skillTags.length"
                  class="soft-tag soft-tag--muted"
                >未填写</span>
              </div>
            </div>
          </div>

          <div class="visibility-split">
            <div>
              <strong>公开展示</strong>
              <span
                v-for="item in publicProfileItems"
                :key="item"
              >{{ item }}</span>
            </div>
            <div>
              <strong>仅自己可见</strong>
              <span
                v-for="item in privateProfileItems"
                :key="item"
              >{{ item }}</span>
            </div>
          </div>
        </article>

        <article
          v-if="verification && verificationStatus === 'VERIFIED'"
          class="panel-card verification-overview"
        >
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                认证状态
              </p>
              <h2>{{ optionLabel(STATUS_OPTIONS, verificationStatus) }}</h2>
            </div>
            <StatusBadge :status="verificationStatus" />
          </div>
          <p class="panel-copy">
            {{ statusCopy }}
          </p>

          <div class="step-list">
            <div
              v-for="step in stepItems"
              :key="step.label"
              class="step-item"
              :class="{ done: step.done }"
            >
              <CheckCircle2
                :size="18"
                aria-hidden="true"
              />
              <span>{{ step.label }}</span>
            </div>
          </div>

          <dl class="record-grid">
            <div>
              <dt>认证方式</dt>
              <dd>{{ optionLabel(VERIFICATION_METHODS, verification?.method) }}</dd>
            </div>
            <div>
              <dt>学校</dt>
              <dd>{{ verification?.school || '未提交' }}</dd>
            </div>
            <div>
              <dt>院系</dt>
              <dd>{{ verification?.department || '未提交' }}</dd>
            </div>
            <div>
              <dt>提交时间</dt>
              <dd>{{ formatDate(verification?.createdAt) }}</dd>
            </div>
          </dl>
        </article>

        <article class="panel-card safety-card">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                认证权益
              </p>
              <h2>通过后你可以</h2>
            </div>
            <Info
              :size="22"
              aria-hidden="true"
            />
          </div>
          <ul class="benefit-list">
            <li
              v-for="item in verificationBenefits"
              :key="item.title"
              class="benefit-item"
            >
              <span class="benefit-icon">
                <component
                  :is="item.icon"
                  :size="20"
                  aria-hidden="true"
                />
              </span>
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ item.copy }}</small>
              </span>
            </li>
          </ul>
        </article>

        <article
          v-if="canSubmitVerification || pendingEmailVerification"
          class="panel-card verification-form-card"
        >
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                提交资料
              </p>
              <h2>填写认证信息</h2>
            </div>
            <button
              class="ghost-action"
              type="button"
              :disabled="loading || !isAuthenticated"
              @click="handleRefresh"
            >
              <RefreshCw
                :class="{ spinning: loading }"
                :size="17"
                aria-hidden="true"
              />
              刷新
            </button>
          </div>

          <form
            v-if="canSubmitVerification"
            class="verification-form"
            @submit.prevent="handleSubmitVerification"
          >
            <label class="field">
              <span>认证方式</span>
              <select v-model="verificationForm.method">
                <option
                  v-for="method in VERIFICATION_METHODS"
                  :key="method.value"
                  :value="method.value"
                >
                  {{ method.label }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>真实姓名</span>
              <input
                v-model.trim="verificationForm.realName"
                required
                placeholder="用于后台实名核验"
              >
            </label>

            <label class="field">
              <span>学号</span>
              <input
                v-model.trim="verificationForm.studentNumber"
                required
                placeholder="不会在公开页面直接展示"
              >
            </label>

            <label class="field">
              <span>学校</span>
              <input
                v-model.trim="verificationForm.school"
                required
                placeholder="例如：南京大学"
              >
            </label>

            <label class="field">
              <span>院系</span>
              <input
                v-model.trim="verificationForm.department"
                placeholder="选填，例如：软件学院"
              >
            </label>

            <label
              v-if="isEmailVerificationMethod"
              class="field field-wide"
            >
              <span>校园邮箱</span>
              <input
                v-model.trim="verificationForm.campusEmail"
                required
                type="email"
                placeholder="请输入学校邮箱，例如 name@school.edu.cn"
              >
            </label>

            <div
              v-else-if="requiresVerificationEvidence"
              class="field field-wide evidence-upload-field"
            >
              <span>{{ verificationEvidenceLabel }}</span>
              <input
                ref="verificationEvidenceFileInput"
                class="sr-only"
                type="file"
                accept="image/*"
                @change="handleVerificationEvidenceFileChange"
              >
              <div
                class="evidence-upload-box"
                :class="{ 'evidence-upload-box--empty': requiresVerificationEvidence && !verificationForm.evidenceUrl }"
              >
                <button
                  class="ghost-action upload-evidence-button"
                  type="button"
                  :disabled="verificationEvidenceUploading"
                  @click="triggerVerificationEvidenceUpload"
                >
                  <LoaderCircle
                    v-if="verificationEvidenceUploading"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <ImagePlus
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  {{ verificationEvidenceUploading ? '上传中' : '上传照片' }}
                </button>
                <div>
                  <strong>{{ verificationEvidenceFileName || '尚未上传照片' }}</strong>
                  <small>{{ verificationEvidenceHint }}</small>
                </div>
              </div>
              <p
                v-if="requiresVerificationEvidence && !verificationForm.evidenceUrl"
                class="evidence-required-hint"
              >
                请先上传{{ verificationEvidenceLabel }}，否则无法提交认证。
              </p>
              <a
                v-if="verificationForm.evidenceUrl"
                class="evidence-link"
                :href="verificationForm.evidenceUrl"
                target="_blank"
                rel="noreferrer"
              >
                查看已上传照片
              </a>
            </div>

            <button
              class="primary-action"
              type="submit"
              :disabled="submitting || verificationEvidenceUploading"
            >
              <LoaderCircle
                v-if="submitting"
                class="spinning"
                :size="18"
                aria-hidden="true"
              />
              <ShieldCheck
                v-else
                :size="18"
                aria-hidden="true"
              />
              提交校内认证
            </button>
          </form>

          <div
            v-else-if="pendingEmailVerification"
            class="email-code-panel"
          >
            <span class="email-code-icon">
              <AtSign
                :size="34"
                aria-hidden="true"
              />
            </span>
            <div>
              <h3>输入邮箱验证码</h3>
              <p>验证码已发送至校园邮箱，输入 6 位验证码即可完成认证。</p>
            </div>
            <label class="field">
              <span>邮箱验证码</span>
              <input
                v-model.trim="emailVerificationCode"
                inputmode="numeric"
                maxlength="6"
                placeholder="请输入 6 位验证码"
              >
            </label>
            <div class="email-code-actions">
              <button
                class="primary-action"
                type="button"
                :disabled="emailCodeSubmitting"
                @click="handleConfirmEmailCode"
              >
                <LoaderCircle
                  v-if="emailCodeSubmitting"
                  class="spinning"
                  :size="18"
                  aria-hidden="true"
                />
                <CheckCircle2
                  v-else
                  :size="18"
                  aria-hidden="true"
                />
                确认认证
              </button>
              <button
                class="ghost-action"
                type="button"
                :disabled="emailCodeResending"
                @click="handleResendEmailCode"
              >
                <RefreshCw
                  :class="{ spinning: emailCodeResending }"
                  :size="17"
                  aria-hidden="true"
                />
                重发验证码
              </button>
            </div>
          </div>

          <div
            v-else
            class="locked-state"
          >
            <span class="locked-state-visual">
              <ShieldCheck
                :size="56"
                aria-hidden="true"
              />
            </span>
            <strong>{{ lockMessage }}</strong>
            <span>你的校园身份已认证成功，无需重复提交。</span>
            <div class="locked-actions">
              <button
                class="primary-action primary-action--compact"
                type="button"
                @click="setFeedback('认证资料已在上方展示。')"
              >
                <ClipboardList
                  :size="17"
                  aria-hidden="true"
                />
                查看认证资料
              </button>
              <button
                class="ghost-action ghost-action--compact"
                type="button"
                :disabled="loading || !isAuthenticated"
                @click="handleRefresh"
              >
                <RefreshCw
                  :class="{ spinning: loading }"
                  :size="17"
                  aria-hidden="true"
                />
                刷新状态
              </button>
            </div>
          </div>
        </article>

        <div
          v-if="verification?.rejectReason"
          class="panel-card reject-box"
        >
          <strong>驳回原因</strong>
          <span>{{ verification.rejectReason }}</span>
        </div>
      </section>

      <section class="marked-posts-section">
        <div class="marked-posts-header">
          <div>
            <p class="section-kicker">我的收藏</p>
            <h2>被标记的帖子</h2>
          </div>
          <Bookmark
            :size="22"
            aria-hidden="true"
          />
        </div>
        <div
          v-if="markedPostsLoading"
          class="marked-posts-loading"
        >
          <LoaderCircle
            class="spinning"
            :size="20"
            aria-hidden="true"
          />
          加载中...
        </div>
        <div
          v-else-if="!markedPosts.length"
          class="marked-posts-empty"
        >
          <p>还没有收藏任何帖子。</p>
          <button
            class="ghost-action ghost-action--compact"
            type="button"
            @click="loadMarkedPosts"
          >
            加载收藏
          </button>
        </div>
        <div
          v-else
          class="marked-posts-grid"
        >
          <article
            v-for="post in markedPosts"
            :key="post.id"
            class="panel-card marked-post-card"
            @click="goToPost(post.id)"
          >
            <div class="marked-post-card-header">
              <span class="marked-post-category">{{ post.category }}</span>
              <span class="marked-post-stats">{{ post.replyCount || 0 }} 回复 · {{ post.likeCount || 0 }} 赞</span>
            </div>
            <h3>{{ post.title || '无标题' }}</h3>
            <p>{{ (post.content || '').slice(0, 100) }}{{ (post.content || '').length > 100 ? '...' : '' }}</p>
          </article>
        </div>
      </section>
    </main>
  </section>
</template>

<style scoped>
.dashboard-shell {
  --sidebar-width: 293px;
  --topbar-height: 76px;
  min-height: 100vh;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 247, 251, 0.96)),
    #fbfafc;
  color: #2b2733;
  font-size: 15px;
  font-family:
    "PingFang SC",
    "Microsoft YaHei",
    "Hiragino Sans GB",
    "Noto Sans SC",
    sans-serif;
}

.dashboard-shell.is-collapsed {
  --sidebar-width: 78px;
}

.dashboard-sidebar,
.dashboard-topbar,
.dashboard-main {
  transition:
    width 220ms ease,
    left 220ms ease,
    margin-left 220ms ease,
    padding 220ms ease;
}

.dashboard-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  height: 100vh;
  padding: 36px 18px 24px;
  border-right: 1px solid #e4e1ea;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 14px 0 42px rgba(92, 82, 125, 0.045);
}

.collapse-button {
  position: absolute;
  top: 18px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e2def0;
  border-radius: 10px;
  background: #ffffff;
  color: #4f46e5;
  box-shadow: 0 8px 22px rgba(116, 109, 195, 0.1);
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.collapse-button:hover {
  border-color: #c7d2fe;
  background: #f5f3ff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 0 10px;
  color: #4f46e5;
  text-decoration: none;
}

.brand-mark {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: #4f46e5;
}

.brand-text {
  max-width: 190px;
  color: #4338ca;
  font-family: Georgia, "Times New Roman", "Noto Serif", serif;
  font-size: 1.08rem;
  font-weight: 900;
  line-height: 1.08;
  overflow-wrap: anywhere;
  white-space: nowrap;
  text-transform: uppercase;
  transition:
    max-width 180ms ease,
    opacity 160ms ease;
}

.sidebar-nav {
  display: grid;
  gap: 10px;
  margin-top: 34px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 50px;
  padding: 0 18px;
  border-radius: 8px;
  color: #8b8994;
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease,
    padding 180ms ease;
}

.nav-item:hover,
.nav-item.active {
  background: linear-gradient(90deg, #eef2ff 0%, #f8f7ff 100%);
  color: #4f46e5;
  box-shadow: 0 14px 30px rgba(79, 70, 229, 0.085);
}

.nav-item.active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: #4f46e5;
}

.nav-item span {
  transition:
    opacity 160ms ease,
    max-width 180ms ease;
}

.is-collapsed .dashboard-sidebar {
  padding-inline: 12px;
}

.is-collapsed .collapse-button {
  top: 42px;
  right: 50%;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  transform: translateX(50%);
}

.is-collapsed .collapse-button svg {
  width: 16px;
  height: 16px;
}

.is-collapsed .brand {
  justify-content: center;
  min-height: 46px;
  padding-inline: 0;
}

.is-collapsed .brand-mark svg {
  width: 30px;
  height: 30px;
}

.is-collapsed .brand-text,
.is-collapsed .nav-item span {
  max-width: 0;
  opacity: 0;
  overflow: hidden;
}

.is-collapsed .sidebar-nav {
  gap: 8px;
  margin-top: 18px;
}

.is-collapsed .nav-item {
  justify-content: center;
  gap: 0;
  min-height: 46px;
  padding: 0;
}

.dashboard-topbar {
  position: fixed;
  top: 0;
  right: 0;
  left: var(--sidebar-width);
  z-index: 25;
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto;
  align-items: center;
  height: var(--topbar-height);
  padding: 0 30px 0 34px;
  border-bottom: 1px solid #e4e1ea;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(18px);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 46px;
  padding: 0 16px;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #a3a0ad;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.search-box:focus-within {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #393545;
  font-size: 0.92rem;
}

.search-box input::placeholder {
  color: #b8b5c1;
}

.topbar-account {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  min-width: max-content;
}

.notice-button,
.account-button,
.account-menu-item {
  border: 0;
  background: transparent;
  color: #393746;
}

.notice-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
}

.notice-button:hover,
.account-button:hover,
.account-button.active {
  background: #f6f4ff;
  color: #746dc3;
}

.notice-button span {
  position: absolute;
  top: 2px;
  right: 3px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #746dc3;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 20px;
}

.topbar-divider {
  width: 1px;
  height: 38px;
  background: #dfdce5;
}

.account-menu {
  position: relative;
}

.account-button {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  min-height: 46px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 900;
}

.account-chevron {
  transition: transform 180ms ease;
}

.account-button.active .account-chevron {
  transform: rotate(180deg);
}

.account-button > span:not(.avatar) {
  white-space: nowrap;
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 32% 28%, #f5f2ff 0 23%, transparent 24%),
    linear-gradient(135deg, #d9d3ff, #f1efff);
  color: #746dc3;
  font-weight: 900;
  overflow: hidden;
}

.avatar--compact {
  width: 38px;
  height: 38px;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 60;
  display: grid;
  gap: 6px;
  width: min(260px, calc(100vw - 32px));
  padding: 10px;
  border: 1px solid #e4e1ea;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 40px rgba(42, 38, 61, 0.14);
}

.account-dropdown-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 9px 12px;
  border-bottom: 1px solid #eeeaf2;
}

.account-dropdown-head div {
  display: grid;
  gap: 3px;
}

.account-dropdown-head strong {
  color: #3c3543;
  font-size: 0.98rem;
  line-height: 1.25;
}

.account-dropdown-head small {
  color: #8a8792;
  line-height: 1.35;
}

.account-menu-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 40px;
  padding: 0 10px;
  border-radius: 8px;
  color: #4a4650;
  font-size: 0.96rem;
  font-weight: 900;
  text-align: left;
  text-decoration: none;
  transition:
    background-color 180ms ease,
    color 180ms ease;
}

.account-menu-item:hover,
.account-menu-item:focus-visible {
  background: #f6f4ff;
  color: #746dc3;
  outline: 0;
}

.account-menu-item--danger {
  color: #b91c1c;
}

.account-menu-item--danger:hover,
.account-menu-item--danger:focus-visible {
  background: #fef2f2;
  color: #991b1b;
}

.dashboard-main {
  min-height: 100vh;
  margin-left: var(--sidebar-width);
  padding: calc(var(--topbar-height) + 22px) 24px 36px;
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
  gap: 20px;
  min-height: 104px;
  padding: 18px 28px;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eeeaff, #f9f7ff);
  color: #746dc3;
}

.title-copy {
  display: grid;
  gap: 5px;
}

.title-copy p,
.section-kicker {
  margin: 0;
  color: #746dc3;
  font-size: 0.82rem;
  font-weight: 900;
}

.title-copy h1 {
  margin: 0;
  color: #3c3543;
  font-size: 1.5rem;
  font-weight: 900;
  line-height: 1.1;
}

.title-copy span {
  color: #8a8792;
  font-size: 0.9rem;
}

.page-title-card :deep(.status-badge--success),
.verification-overview :deep(.status-badge--success) {
  min-height: 32px;
  padding: 0 13px;
  border: 1px solid #86efac;
  background: #dcfce7;
  color: #047857;
  font-size: 0.86rem;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(22, 163, 74, 0.14);
}

.verification-overview .panel-heading h2 {
  font-size: 1.35rem;
  color: #211b2c;
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 12px;
}

.page-feedback--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.page-feedback--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.82fr);
  grid-template-areas:
    "profile profile"
    "profileForm visibility"
    "overview safety";
  gap: 18px;
  margin-top: 18px;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 12px;
  min-height: 172px;
  padding: 22px;
}

.identity-profile-card {
  grid-area: profile;
}

.profile-editor-card {
  grid-area: profileForm;
}

.identity-visibility-card {
  grid-area: visibility;
}

.verification-overview {
  grid-area: overview;
}

.safety-card {
  grid-area: safety;
}

.verification-form-card {
  grid-area: overview;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.panel-heading h2 {
  margin: 4px 0 0;
  color: #3c3543;
  font-size: 1.12rem;
  font-weight: 900;
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
}

.profile-avatar-large {
  position: relative;
  display: grid;
  place-items: center;
  width: 82px;
  height: 82px;
  padding: 0;
  border: 1px solid #ddd6fe;
  border-radius: 50%;
  background: linear-gradient(135deg, #e7e4ff, #f8f6ff);
  color: #5d49cb;
  font-size: 2rem;
  font-weight: 900;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.profile-avatar-large:hover:not(:disabled) {
  border-color: #a78bfa;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.14);
}

.profile-avatar-large:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.profile-avatar-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-upload-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.32);
  color: #ffffff;
  opacity: 0;
  transition: opacity 180ms ease;
}

.profile-avatar-large:hover .avatar-upload-overlay,
.profile-avatar-large:focus-visible .avatar-upload-overlay {
  opacity: 1;
}

.profile-main-copy {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.section-kicker-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.section-kicker-row .section-kicker {
  margin: 0;
}

.credit-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 8px;
  background: #f3f0ff;
  border: 1px solid #ddd6fe;
  color: #5d49cb;
  font-size: 0.78rem;
  font-weight: 900;
}

.credit-level-icon {
  display: inline-flex;
  align-items: center;
  color: #746dc3;
}

.credit-level-text {
  white-space: nowrap;
}

.credit-points {
  color: #8b87a8;
  font-size: 0.74rem;
  white-space: nowrap;
}

.credit-progress-track {
  display: block;
  height: 6px;
  margin-top: 2px;
  overflow: hidden;
  border-radius: 999px;
  background: #e7e2f1;
}

.credit-progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #746dc3, #a78bfa);
  transition: width 320ms ease;
}

.profile-main-copy h2 {
  margin: 0;
  color: #2f2940;
  font-size: 1.42rem;
  font-weight: 900;
  line-height: 1.18;
  overflow-wrap: anywhere;
}

.profile-main-copy > span {
  color: #746f7a;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.profile-chip-row,
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.profile-chip-row span,
.soft-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  max-width: 100%;
  padding: 0 10px;
  border-radius: 999px;
  background: #f3f0ff;
  color: #5d49cb;
  font-size: 0.84rem;
  font-weight: 900;
}

.profile-edit-shortcut {
  align-self: start;
  white-space: nowrap;
}

.profile-progress-block {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #fcfbff;
}

.profile-progress-block div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #746f7a;
  font-weight: 900;
}

.profile-progress-block strong {
  color: #5d49cb;
}

.profile-progress-track {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e7e2f1;
}

.profile-progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #746dc3, #0f766e);
  transition: width 220ms ease;
}

.profile-meta-grid,
.identity-row-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.profile-meta-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.profile-meta-grid div,
.identity-row-list div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #ffffff;
}

.profile-meta-grid dt,
.identity-row-list dt {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #8a8792;
  font-size: 0.82rem;
  font-weight: 900;
}

.profile-meta-grid dt svg,
.identity-row-list dt svg {
  color: #746dc3;
}

.profile-meta-grid dd,
.identity-row-list dd {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px;
}

.profile-form-actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.identity-row-list {
  grid-template-columns: 1fr;
}

.identity-tag-block {
  display: grid;
  gap: 14px;
}

.identity-tag-block > div {
  display: grid;
  gap: 8px;
}

.identity-tag-block > div > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #3c3543;
  font-weight: 900;
}

.soft-tag {
  background: #f0fdfa;
  color: #0f766e;
}

.soft-tag--blue {
  background: #eef2ff;
  color: #4338ca;
}

.soft-tag--muted {
  background: #f1f5f9;
  color: #64748b;
}

.visibility-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.visibility-split div {
  display: grid;
  align-content: start;
  gap: 7px;
  padding: 12px;
  border: 1px solid #e6e2eb;
  border-radius: 8px;
  background: #fbf9fc;
}

.visibility-split strong {
  color: #3c3543;
}

.visibility-split span {
  color: #746f7a;
  font-size: 0.86rem;
  line-height: 1.45;
}

.panel-copy,
.locked-state span {
  margin: 0;
  color: #746f7a;
  font-size: 0.9rem;
  line-height: 1.62;
}

.step-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  padding: 10px 4px 8px;
}

.step-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 64px;
  color: #8a8792;
  font-size: 0.82rem;
  font-weight: 900;
  text-align: center;
}

.step-item::before {
  content: "";
  position: absolute;
  top: 19px;
  left: 0;
  right: 0;
  height: 2px;
  background: #e7e2f1;
  transform: translateY(-50%);
}

.step-item:first-child::before {
  left: 50%;
}

.step-item:last-child::before {
  right: 50%;
}

.step-item svg {
  position: relative;
  z-index: 1;
  width: 28px;
  height: 28px;
  padding: 5px;
  border-radius: 50%;
  background: #ffffff;
  color: #a09ba8;
  box-shadow: inset 0 0 0 1px #e7e2f1;
}

.step-item.done {
  color: #746dc3;
}

.step-item.done::before {
  background: #8b7de0;
}

.step-item.done svg {
  background: #746dc3;
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(116, 109, 195, 0.2);
}

.record-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.record-grid div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 3px 10px;
  padding: 10px 12px;
  border: 1px solid #eeeaf2;
  border-radius: 8px;
  background: #fcfbfd;
}

.record-grid dt {
  color: #8a8792;
  font-size: 0.78rem;
}

.record-grid dd {
  margin: 0;
  color: #3c3543;
  font-size: 0.92rem;
  font-weight: 900;
}

.benefit-list {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.benefit-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 13px 0;
  border-bottom: 1px solid #eeeaf2;
  color: #746f7a;
}

.benefit-item:last-child {
  border-bottom: 0;
}

.benefit-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #f0edff;
  color: #746dc3;
}

.benefit-item span:last-child {
  display: grid;
  gap: 4px;
}

.benefit-item strong {
  color: #3c3543;
  font-size: 0.98rem;
}

.benefit-item small {
  color: #8a8792;
  font-size: 0.82rem;
  line-height: 1.45;
}

.verification-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px;
}

.field {
  display: grid;
  gap: 8px;
}

.field-wide,
.primary-action {
  grid-column: 1 / -1;
}

.field span {
  color: #3c3543;
  font-size: 0.86rem;
  font-weight: 900;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid #ddd9e8;
  border-radius: 8px;
  background: #ffffff;
  color: #393545;
  font-size: 0.9rem;
  outline: 0;
}

.field input,
.field select {
  height: 42px;
  padding: 0 12px;
}

.field textarea {
  min-height: 108px;
  padding: 11px 12px;
  line-height: 1.55;
  resize: vertical;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
}

.field input[aria-invalid="true"],
.field textarea[aria-invalid="true"] {
  border-color: #ef4444;
}

.field input:disabled,
.field select:disabled,
.field textarea:disabled {
  background: #f8f7fb;
  color: #5f5968;
}

.field-error {
  color: #b91c1c;
  font-size: 0.82rem;
  font-weight: 800;
  line-height: 1.4;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.evidence-upload-field {
  gap: 10px;
}

.evidence-upload-box {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px dashed #c9c2ee;
  border-radius: 8px;
  background: #fbf9ff;
}

.evidence-upload-box--empty {
  border-color: #fca5a5;
  background: #fef2f2;
}

.evidence-required-hint {
  margin: 0;
  color: #b91c1c;
  font-size: 0.88rem;
  font-weight: 800;
  line-height: 1.45;
}

.evidence-upload-box div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.evidence-upload-box strong {
  color: #3c3543;
  font-size: 0.92rem;
  overflow-wrap: anywhere;
}

.evidence-upload-box small {
  color: #746f7a;
  font-size: 0.82rem;
  line-height: 1.45;
}

.upload-evidence-button {
  min-width: 122px;
}

.evidence-link {
  width: fit-content;
  color: #746dc3;
  font-size: 0.86rem;
  font-weight: 900;
  text-decoration: none;
}

.evidence-link:hover {
  color: #5d49cb;
}

.email-code-panel {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid #e0ddec;
  border-radius: 8px;
  background: #fbf9ff;
}

.email-code-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: #f0edff;
  color: #746dc3;
}

.email-code-panel h3,
.email-code-panel p {
  margin: 0;
}

.email-code-panel h3 {
  color: #3c3543;
  font-size: 1.05rem;
}

.email-code-panel p {
  color: #746f7a;
  font-size: 0.9rem;
  line-height: 1.55;
}

.email-code-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-action,
.ghost-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  border-radius: 8px;
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
}

.primary-action {
  border: 1px solid #746dc3;
  background: #746dc3;
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(116, 109, 195, 0.16);
}

.primary-action:not(:disabled):hover {
  border-color: #5d49cb;
  background: #5d49cb;
}

.primary-action:disabled,
.ghost-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.ghost-action {
  padding: 0 12px;
  border: 1px solid #e0ddec;
  background: #ffffff;
  color: #746dc3;
}

.ghost-action:not(:disabled):hover {
  border-color: #c9c2ee;
  background: #f7f4ff;
}

.primary-action--compact,
.ghost-action--compact {
  min-width: 150px;
}

.locked-state {
  display: grid;
  place-items: center;
  gap: 14px;
  min-height: 266px;
  border: 1px dashed #d8d1ee;
  border-radius: 8px;
  background: #fcfbff;
  color: #746dc3;
  text-align: center;
}

.locked-state-visual {
  display: grid;
  place-items: center;
  width: 86px;
  height: 86px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8e7ee8, #5d49cb);
  color: #ffffff !important;
  box-shadow: 0 18px 30px rgba(116, 109, 195, 0.22);
}

.locked-state strong {
  color: #3c3543;
  font-size: 1.05rem;
}

.locked-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.reject-box {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #991b1b;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .dashboard-shell {
    --sidebar-width: 279px;
  }

  .dashboard-shell.is-collapsed {
    --sidebar-width: 84px;
  }

  .dashboard-sidebar {
    padding-inline: 14px;
  }

  .brand-text {
    font-size: 1.08rem;
  }

  .nav-item {
    font-size: 1.06rem;
  }

  .dashboard-topbar {
    grid-template-columns: 1fr;
    gap: 12px;
    height: auto;
    min-height: var(--topbar-height);
    padding: 12px 20px;
  }

  .topbar-account {
    justify-content: flex-start;
  }

  .content-grid {
    grid-template-columns: 1fr;
    grid-template-areas:
      "profile"
      "profileForm"
      "visibility"
      "overview"
      "safety";
  }

  .profile-meta-grid,
  .visibility-split {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dashboard-shell,
  .dashboard-shell.is-collapsed {
    --sidebar-width: 0px;
    --topbar-height: 176px;
  }

  .dashboard-sidebar {
    position: static;
    width: 100%;
    height: auto;
    padding: 20px 16px;
    border-right: 0;
    border-bottom: 1px solid #e4e1ea;
  }

  .collapse-button {
    display: none;
  }

  .brand {
    justify-content: flex-start;
  }

  .is-collapsed .brand-text,
  .is-collapsed .nav-item span {
    max-width: none;
    opacity: 1;
  }

  .sidebar-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: 18px;
  }

  .nav-item {
    justify-content: flex-start;
    padding: 0 16px;
    font-size: 1rem;
  }

  .dashboard-topbar {
    position: sticky;
    left: 0;
  }

  .dashboard-main {
    margin-left: 0;
    padding: 20px 16px 32px;
  }

  .page-title-card,
  .profile-hero,
  .verification-form,
  .profile-form,
  .profile-meta-grid,
  .visibility-split,
  .record-grid,
  .step-list {
    grid-template-columns: 1fr;
  }

  .profile-edit-shortcut,
  .profile-form-actions,
  .profile-form-actions .primary-action,
  .profile-form-actions .ghost-action {
    width: 100%;
  }

  .profile-form-actions {
    display: grid;
  }
}

.marked-posts-section {
  display: grid;
  gap: 16px;
  margin-top: 24px;
}

.marked-posts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #6366f1;
}

.marked-posts-header h2 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 900;
  color: #111827;
}

.marked-posts-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px;
  color: #6b7280;
  font-size: 0.9rem;
  font-weight: 700;
}

.marked-posts-empty {
  padding: 32px;
  text-align: center;
  border: 1px dashed #e4e7ef;
  border-radius: 12px;
  color: #6b7280;
}

.marked-posts-empty p {
  margin: 0 0 12px;
}

.marked-posts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.marked-post-card {
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.marked-post-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.08);
}

.marked-post-card h3 {
  margin: 8px 0 6px;
  font-size: 1rem;
  font-weight: 800;
  color: #111827;
}

.marked-post-card p {
  margin: 0;
  color: #6b7280;
  font-size: 0.88rem;
  line-height: 1.5;
}

.marked-post-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.marked-post-category {
  padding: 3px 8px;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.78rem;
  font-weight: 800;
}

.marked-post-stats {
  color: #9ca3af;
  font-size: 0.78rem;
  font-weight: 700;
}
</style>
