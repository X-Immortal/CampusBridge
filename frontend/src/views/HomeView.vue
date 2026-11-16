<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  AtSign,
  BadgeCheck,
  Briefcase,
  Building2,
  CalendarClock,
  ChartLine,
  Check,
  CheckCircle2,
  ClipboardList,
  Eye,
  IdCard,
  ImagePlus,
  Info,
  LayoutGrid,
  LoaderCircle,
  MapPin,
  Monitor,
  Moon,
  Palette,
  RefreshCw,
  Save,
  ShieldCheck,
  Sun,
  Tag,
  UserPen,
  UserRound,
  X
} from '@lucide/vue'
import { workspaceNavItems } from '../app/navigation'
import AccountSettingsModal from '../components/AccountSettingsModal.vue'
import DashboardShell from '../components/DashboardShell.vue'
import ModuleTopbar from '../components/layout/ModuleTopbar.vue'
import ModuleEntryCard from '../components/layout/ModuleEntryCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { usePreferences } from '../composables/usePreferences'
import { useTheme } from '../composables/useTheme'
import { useToast } from '../composables/useToast'
import { optionLabel, STATUS_OPTIONS, VERIFICATION_METHODS } from '../constants'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const route = useRoute()
const { showToast } = useToast()
const { accent, density, setAccent, setDensity } = usePreferences()
const { theme, setTheme } = useTheme()

const {
  state,
  isAuthenticated,
  verificationStatus,
  refreshSession,
  submitVerification,
  resendEmailVerificationCode,
  confirmEmailVerification,
  cancelCurrentVerification,
  updateMyProfile
} = useAuth()

const loading = ref(false)
const submitting = ref(false)
const avatarLoadFailed = ref(false)

const verificationForm = reactive({
  method: 'STUDENT_CARD_IMAGE',
  realName: '',
  studentNumber: '',
  school: '',
  department: '',
  campusEmail: '',
  evidenceUrl: ''
})

const showAccountModal = ref(false)
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
const verificationCancelling = ref(false)

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

const currentSubSection = computed(() => route.meta?.subSection || 'overview')

const themeModes = [
  { key: 'light', label: '日间', icon: Sun },
  { key: 'dark', label: '夜间', icon: Moon },
  { key: 'system', label: '跟随系统', icon: Monitor }
]

const accentOptions = [
  { key: 'purple', label: '默认紫', color: '#4f46e5' },
  { key: 'blue', label: '清新蓝', color: '#0369a1' },
  { key: 'orange', label: '活力橙', color: '#ea580c' },
  { key: 'green', label: '自然绿', color: '#15803d' }
]

const densityOptions = [
  { key: 'compact', label: '紧凑', hint: '更小间距' },
  { key: 'comfortable', label: '舒适', hint: '默认' },
  { key: 'spacious', label: '宽松', hint: '更大间距' }
]

const userName = computed(() =>
  isAuthenticated.value ? state.profile?.nickname || state.user?.username || '张同学' : '未登录'
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
  const items = [
    { label: '登录账号', done: isAuthenticated.value },
    { label: '提交资料', done: hasRecord },
    { label: '平台审核', done: hasRecord && verificationStatus.value !== 'UNVERIFIED' },
    { label: '认证通过', done: verified }
  ]
  for (let i = 0; i < items.length; i++) {
    items[i].connectorDone = items[i].done && i + 1 < items.length && items[i + 1].done
  }
  return items
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
    showToast('请先登录后再编辑个人档案。', { type: 'danger' })
    return
  }

  hydrateProfileForm(myProfile.value)
  clearProfileErrors()
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
  try {
    const result = await api.uploadAvatar(file)
    const url = typeof result === 'string' ? result : result?.avatarUrl
    if (url) {
      profileForm.avatarUrl = url
      avatarLoadFailed.value = false
      if (state.profile) {
        state.profile.avatarUrl = url
      }
      showToast('头像已上传并更新。')
    }
  } catch (error) {
    showToast(error.message || '头像上传失败，请稍后重试。', { type: 'danger' })
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
  try {
    const result = await api.uploadAttachment({
      file,
      expectedOwnerType: 'VERIFICATION'
    })
    const url = result?.fileUrl || result?.url
    if (!url) {
      throw new Error('上传成功但未返回材料地址。')
    }
    verificationForm.evidenceUrl = url
    verificationEvidenceFileName.value = file.name
    showToast('认证材料照片已上传。')
  } catch (error) {
    const detail = error.code ? ` [${error.code}]` : ''
    showToast((error.message || '认证材料上传失败，请稍后重试。') + detail, { type: 'danger' })
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
    showToast('请先登录后再保存个人档案。', { type: 'danger' })
    return
  }

  const { valid, payload } = validateProfileForm()
  if (!valid) {
    showToast('请检查档案表单中标出的内容。', { type: 'danger' })
    return
  }

  profileSubmitting.value = true
  try {
    const savedProfile = await updateMyProfile(payload)
    hydrateProfileForm(savedProfile || {})
    profileEditing.value = false
    showToast('个人档案已保存，公开主页信息会同步更新。')
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    profileSubmitting.value = false
  }
}

async function handleRefresh() {
  if (!isAuthenticated.value) return
  loading.value = true
  try {
    await refreshSession()
    showToast('个人档案与认证状态已刷新。')
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    loading.value = false
  }
}

async function handleSubmitVerification() {
  if (requiresVerificationEvidence.value && !verificationForm.evidenceUrl) {
    showToast(`请先上传${verificationEvidenceLabel.value}。`, { type: 'danger' })
    return
  }
  if (isEmailVerificationMethod.value && !isValidEmail(verificationForm.campusEmail)) {
    showToast('请填写有效的校园邮箱地址。', { type: 'danger' })
    return
  }

  submitting.value = true
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
      showToast(formatEmailCodeFeedback(result))
    } else {
      await refreshSession()
      showToast('校内认证资料已提交，等待平台审核。')
    }
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    submitting.value = false
  }
}

async function handleConfirmEmailCode() {
  const code = emailVerificationCode.value.trim()
  if (!/^\d{6}$/.test(code)) {
    showToast('请输入 6 位邮箱验证码。', { type: 'danger' })
    return
  }

  emailCodeSubmitting.value = true
  try {
    await confirmEmailVerification({ code })
    await refreshSession()
    emailVerificationCode.value = ''
    showToast('校园邮箱认证已完成。')
  } catch (error) {
    showToast(error.message || '验证码确认失败，请重新检查。', { type: 'danger' })
  } finally {
    emailCodeSubmitting.value = false
  }
}

async function handleResendEmailCode() {
  emailCodeResending.value = true
  try {
    const result = await resendEmailVerificationCode()
    showToast(formatEmailCodeFeedback(result))
  } catch (error) {
    showToast(error.message || '验证码发送失败，请稍后重试。', { type: 'danger' })
  } finally {
    emailCodeResending.value = false
  }
}

async function handleCancelVerification() {
  verificationCancelling.value = true
  try {
    await cancelCurrentVerification()
    emailVerificationCode.value = ''
    verificationForm.evidenceUrl = ''
    verificationEvidenceFileName.value = ''
    showToast('已撤回本次校园认证，你可以重新填写资料。')
  } catch (error) {
    showToast(error.message || '撤回认证失败，请稍后重试。', { type: 'danger' })
  } finally {
    verificationCancelling.value = false
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

onMounted(async () => {
  if (!isAuthenticated.value) return
  loading.value = true
  try {
    await refreshSession()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <DashboardShell>
    <main class="workspace-page">
      <ModuleTopbar
        eyebrow="工作台"
        title="我的工作台"
        description="管理个人资料、认证、消息和偏好。"
      >
        <template #extra>
          <StatusBadge :status="verificationStatus" />
        </template>
        <ModuleEntryCard
          v-for="item in workspaceNavItems"
          :key="item.key"
          :to="item.to"
          :icon="item.icon"
          :title="item.label"
          :description="item.description"
          :active="currentSubSection === item.key"
        />
      </ModuleTopbar>

      <div class="workspace-content">
        <template v-if="currentSubSection === 'overview'">
          <article class="panel-card panel-card--wide identity-profile-card">
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
                @click="showAccountModal = true"
              >
                <UserPen
                  :size="17"
                  aria-hidden="true"
                />
                账号信息修改
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

          <article
            v-if="verification && verificationStatus === 'PENDING' && verification.method !== 'CAMPUS_EMAIL'"
            class="panel-card verification-overview"
          >
            <div class="panel-heading">
              <div>
                <p class="section-kicker">
                  认证状态
                </p>
                <h2>审核中</h2>
              </div>
              <StatusBadge
                status="PENDING"
                tone="warning"
                label="审核中"
              />
            </div>
            <p class="panel-copy">
              你的认证资料已提交，正在等待管理员审核，请耐心等待。
            </p>
            <div class="step-list">
              <div
                v-for="step in stepItems"
                :key="step.label"
                class="step-item"
                :class="{ done: step.done, 'connector-done': step.connectorDone }"
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
            </dl>
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
                :class="{ done: step.done, 'connector-done': step.connectorDone }"
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
        </template>

        <template v-if="currentSubSection === 'profile'">
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
        </template>

        <template v-if="currentSubSection === 'verification'">
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
                  accept="image/png,image/jpeg,image/webp"
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
                  :disabled="emailCodeResending || verificationCancelling"
                  @click="handleResendEmailCode"
                >
                  <RefreshCw
                    :class="{ spinning: emailCodeResending }"
                    :size="17"
                    aria-hidden="true"
                  />
                  重发验证码
                </button>
                <button
                  class="ghost-action"
                  type="button"
                  :disabled="verificationCancelling"
                  @click="handleCancelVerification"
                >
                  <LoaderCircle
                    v-if="verificationCancelling"
                    class="spinning"
                    :size="17"
                    aria-hidden="true"
                  />
                  <X
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                  取消本次认证
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
                  @click="showToast('认证资料已在上方展示。')"
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
            v-if="verification && verificationStatus === 'PENDING' && verification.method !== 'CAMPUS_EMAIL'"
            class="panel-card verification-overview"
          >
            <div class="panel-heading">
              <div>
                <p class="section-kicker">
                  认证状态
                </p>
                <h2>审核中</h2>
              </div>
              <StatusBadge
                status="PENDING"
                tone="warning"
                label="审核中"
              />
            </div>
            <p class="panel-copy">
              你的认证资料已提交，正在等待管理员审核，请耐心等待。
            </p>
            <div class="pending-verification-actions">
              <button
                class="ghost-action"
                type="button"
                :disabled="verificationCancelling"
                @click="handleCancelVerification"
              >
                <LoaderCircle
                  v-if="verificationCancelling"
                  class="spinning"
                  :size="17"
                  aria-hidden="true"
                />
                <X
                  v-else
                  :size="17"
                  aria-hidden="true"
                />
                撤回认证
              </button>
            </div>
            <div class="step-list">
              <div
                v-for="step in stepItems"
                :key="step.label"
                class="step-item"
                :class="{ done: step.done, 'connector-done': step.connectorDone }"
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
            </dl>
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
                :class="{ done: step.done, 'connector-done': step.connectorDone }"
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

          <div
            v-if="verification?.rejectReason"
            class="panel-card reject-box"
          >
            <strong>驳回原因</strong>
            <span>{{ verification.rejectReason }}</span>
          </div>
        </template>

        <template v-if="currentSubSection === 'settings'">
          <article class="panel-card panel-card--wide settings-card">
            <div class="panel-heading">
              <div>
                <p class="section-kicker">
                  账号安全
                </p>
                <h2>账号信息</h2>
              </div>
            </div>
            <p class="panel-copy">
              修改登录邮箱、密码或其他账号安全设置。
            </p>
            <button
              class="primary-action"
              type="button"
              @click="showAccountModal = true"
            >
              <UserPen
                :size="18"
                aria-hidden="true"
              />
              账号信息修改
            </button>

            <div class="settings-section">
              <div class="panel-heading">
                <div>
                  <p class="section-kicker">
                    外观
                  </p>
                  <h2>主题模式</h2>
                </div>
                <Palette
                  :size="22"
                  aria-hidden="true"
                />
              </div>
              <div class="settings-options">
                <button
                  v-for="m in themeModes"
                  :key="m.key"
                  type="button"
                  class="settings-option"
                  :class="{ active: theme === m.key }"
                  :aria-pressed="theme === m.key"
                  @click="setTheme(m.key)"
                >
                  <component
                    :is="m.icon"
                    :size="18"
                    aria-hidden="true"
                  />
                  <span>{{ m.label }}</span>
                  <Check
                    v-if="theme === m.key"
                    class="check-mark"
                    :size="16"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </div>

            <div class="settings-section">
              <div class="panel-heading">
                <div>
                  <p class="section-kicker">
                    个性化
                  </p>
                  <h2>强调色</h2>
                </div>
                <Palette
                  :size="22"
                  aria-hidden="true"
                />
              </div>
              <div class="settings-options settings-options--swatches">
                <button
                  v-for="a in accentOptions"
                  :key="a.key"
                  type="button"
                  class="settings-swatch"
                  :class="{ active: accent === a.key }"
                  :style="{ '--swatch-color': a.color }"
                  :aria-pressed="accent === a.key"
                  @click="setAccent(a.key)"
                >
                  <span
                    class="swatch-dot"
                    :style="{ backgroundColor: a.color }"
                  />
                  <span class="swatch-label">{{ a.label }}</span>
                  <Check
                    v-if="accent === a.key"
                    class="check-mark"
                    :size="16"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </div>

            <div class="settings-section">
              <div class="panel-heading">
                <div>
                  <p class="section-kicker">
                    布局
                  </p>
                  <h2>界面密度</h2>
                </div>
                <LayoutGrid
                  :size="22"
                  aria-hidden="true"
                />
              </div>
              <div class="settings-options">
                <button
                  v-for="d in densityOptions"
                  :key="d.key"
                  type="button"
                  class="settings-option settings-option--density"
                  :class="{ active: density === d.key }"
                  :aria-pressed="density === d.key"
                  @click="setDensity(d.key)"
                >
                  <span class="density-preview">A</span>
                  <span class="density-label">{{ d.label }}</span>
                  <small class="density-hint">{{ d.hint }}</small>
                  <Check
                    v-if="density === d.key"
                    class="check-mark"
                    :size="16"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </div>
          </article>
        </template>
      </div>
    </main>
  </DashboardShell>

  <AccountSettingsModal
    :visible="showAccountModal"
    @close="showAccountModal = false"
  />
</template>

<style scoped>
.workspace-page {
  display: grid;
  gap: 18px;
  min-height: 100%;
}

.workspace-content {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.82fr);
  gap: 18px;
  align-content: start;
}

.workspace-content > .panel-card--wide {
  grid-column: 1 / -1;
}

.panel-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.module-topbar :deep(.status-badge--success),
.verification-overview :deep(.status-badge--success) {
  min-height: 32px;
  padding: 0 13px;
  border: 1px solid var(--color-success-soft);
  background: var(--color-success-bg);
  color: var(--color-success-text);
  font-size: 0.86rem;
  font-weight: 900;
  box-shadow: var(--shadow-card);
}

.verification-overview .panel-heading h2 {
  font-size: 1.35rem;
  color: var(--color-text);
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 12px;
  min-height: 172px;
  padding: 22px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.panel-heading h2 {
  margin: 4px 0 0;
  color: var(--color-text);
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
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand-hover);
  font-size: 2rem;
  font-weight: 900;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.profile-avatar-large:hover:not(:disabled) {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
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
  background: rgba(0, 0, 0, 0.55);
  color: var(--color-surface);
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
  background: var(--color-brand-soft);
  border: 1px solid var(--color-border);
  color: var(--color-brand-hover);
  font-size: 0.78rem;
  font-weight: 900;
}

.credit-level-icon {
  display: inline-flex;
  align-items: center;
  color: var(--color-brand-text);
}

.credit-level-text {
  white-space: nowrap;
}

.credit-points {
  color: var(--color-text-muted);
  font-size: 0.74rem;
  white-space: nowrap;
}

.credit-progress-track {
  display: block;
  max-width: 220px;
  height: 6px;
  margin-top: 2px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-divider);
}

.credit-progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-brand-text), var(--color-brand-border-hover));
  transition: width 320ms ease;
}

.profile-main-copy h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.42rem;
  font-weight: 900;
  line-height: 1.18;
  overflow-wrap: anywhere;
}

.profile-main-copy > span {
  color: var(--color-text-muted);
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
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.profile-progress-block div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-text-muted);
  font-weight: 900;
}

.profile-progress-block strong {
  color: var(--color-brand-hover);
}

.profile-progress-track {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-divider);
}

.profile-progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-brand-text), var(--color-accent));
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.profile-meta-grid dt,
.identity-row-list dt {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  font-weight: 900;
}

.profile-meta-grid dt svg,
.identity-row-list dt svg {
  color: var(--color-brand-text);
}

.profile-meta-grid dd,
.identity-row-list dd {
  margin: 0;
  color: var(--color-text);
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
  color: var(--color-text);
  font-weight: 900;
}

.soft-tag {
  background: var(--color-accent-bg);
  color: var(--color-brand-text);
}

.soft-tag--blue {
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
}

.soft-tag--muted {
  background: var(--color-surface-active);
  color: var(--color-text-muted);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.visibility-split strong {
  color: var(--color-text);
}

.visibility-split span {
  color: var(--color-text-muted);
  font-size: 0.86rem;
  line-height: 1.45;
}

.panel-copy,
.locked-state span {
  margin: 0;
  color: var(--color-text-muted);
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
  color: var(--color-text-muted);
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
  background: var(--color-divider);
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
  background: var(--color-surface);
  color: var(--color-text-muted);
  box-shadow: inset 0 0 0 1px var(--color-divider);
}

.step-item.done {
  color: var(--color-brand-text);
}

.step-item.done::before {
  background: linear-gradient(to right, var(--color-brand-text) 50%, var(--color-divider) 50%);
}

.step-item.connector-done::before {
  background: var(--color-brand-text);
}

.step-item:last-child.done::before {
  background: var(--color-brand-text);
}

.step-item.done svg {
  background: var(--color-brand-text);
  color: var(--color-surface);
  box-shadow: var(--shadow-card);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.record-grid dt {
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.record-grid dd {
  margin: 0;
  color: var(--color-text);
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
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-muted);
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
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
}

.benefit-item span:last-child {
  display: grid;
  gap: 4px;
}

.benefit-item strong {
  color: var(--color-text);
  font-size: 0.98rem;
}

.benefit-item small {
  color: var(--color-text-muted);
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
  color: var(--color-text);
  font-size: 0.86rem;
  font-weight: 900;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
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
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.field input[aria-invalid="true"],
.field textarea[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.field input:disabled,
.field select:disabled,
.field textarea:disabled {
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
}

.field-error {
  color: var(--color-danger);
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
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.evidence-upload-box--empty {
  border-color: var(--color-danger-soft);
  background: var(--color-danger-bg);
}

.evidence-required-hint {
  margin: 0;
  color: var(--color-danger-text);
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
  color: var(--color-text);
  font-size: 0.92rem;
  overflow-wrap: anywhere;
}

.evidence-upload-box small {
  color: var(--color-text-muted);
  font-size: 0.82rem;
  line-height: 1.45;
}

.upload-evidence-button {
  min-width: 122px;
}

.evidence-link {
  width: fit-content;
  color: var(--color-brand-text);
  font-size: 0.86rem;
  font-weight: 900;
  text-decoration: none;
}

.evidence-link:hover {
  color: var(--color-brand-hover);
}

.email-code-panel {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.email-code-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
}

.email-code-panel h3,
.email-code-panel p {
  margin: 0;
}

.email-code-panel h3 {
  color: var(--color-text);
  font-size: 1.05rem;
}

.email-code-panel p {
  color: var(--color-text-muted);
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
  border: 1px solid var(--color-brand-text);
  background: var(--color-brand-text);
  color: var(--color-surface);
  box-shadow: var(--shadow-button);
}

.primary-action:not(:disabled):hover {
  border-color: var(--color-brand-hover);
  background: var(--color-brand-hover);
}

.primary-action:disabled,
.ghost-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.ghost-action {
  padding: 0 12px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-brand-text);
}

.ghost-action:not(:disabled):hover {
  border-color: var(--color-border-dashed);
  background: var(--color-brand-soft);
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
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-brand-text);
  text-align: center;
}

.locked-state-visual {
  display: grid;
  place-items: center;
  width: 86px;
  height: 86px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-brand-hover), var(--color-brand-hover));
  color: var(--color-surface) !important;
  box-shadow: var(--shadow-button-hover);
}

.locked-state strong {
  color: var(--color-text);
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
  border: 1px solid var(--color-danger-soft);
  border-radius: 8px;
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.settings-card {
  gap: 22px;
}

.settings-section {
  display: grid;
  gap: 12px;
}

.settings-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.settings-options--swatches {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.settings-option,
.settings-swatch {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease;
}

.settings-option:hover,
.settings-swatch:hover,
.settings-option:focus-visible,
.settings-swatch:focus-visible {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  outline: 0;
}

.settings-option.active,
.settings-swatch.active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.settings-swatch {
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-height: 72px;
  padding: 10px;
}

.swatch-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.25);
}

.swatch-label {
  font-size: 0.84rem;
}

.settings-option--density {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 4px 10px;
  padding: 10px 12px;
}

.density-preview {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-brand);
  font-weight: 800;
  grid-row: span 2;
}

.density-label {
  grid-column: 2;
  grid-row: 1;
}

.density-hint {
  grid-column: 2;
  grid-row: 2;
  color: var(--color-text-muted);
  font-size: 0.78rem;
  font-weight: 600;
}

.check-mark {
  margin-left: auto;
  color: var(--color-brand);
}

.settings-option--density .check-mark {
  grid-column: 3;
  grid-row: span 2;
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
  .workspace-content {
    grid-template-columns: 1fr;
  }

  .settings-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settings-options--swatches {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-meta-grid,
  .visibility-split {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workspace-content {
    grid-template-columns: 1fr;
  }

  .settings-options,
  .settings-options--swatches {
    grid-template-columns: 1fr;
  }

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
</style>
