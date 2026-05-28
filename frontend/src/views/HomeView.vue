<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BadgeCheck,
  Bell,
  CalendarClock,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  ClipboardCheck,
  ClipboardList,
  Grid2X2,
  Home,
  Info,
  LoaderCircle,
  LockKeyhole,
  LogIn,
  LogOut,
  MessageCircle,
  PlusCircle,
  RefreshCw,
  School,
  Search,
  ShieldCheck,
  UserRound
} from '@lucide/vue'
import StatusBadge from '../components/StatusBadge.vue'
import { optionLabel, STATUS_OPTIONS, VERIFICATION_METHODS } from '../constants'
import { useAuth } from '../stores/auth'

const {
  state,
  isAuthenticated,
  verificationStatus,
  logout,
  refreshSession,
  submitVerification
} = useAuth()

const router = useRouter()
const sidebarCollapsed = ref(false)
const accountMenuOpen = ref(false)
const accountMenuRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const feedback = ref('')
const feedbackType = ref('success')

const navItems = [
  { label: '个人主页', icon: Home, active: true, to: '/' },
  { label: '任务广场', icon: Grid2X2, to: '/tasks' },
  { label: '发布任务', icon: PlusCircle, to: '/tasks/new' },
  { label: '我的任务', icon: ClipboardList, to: '/tasks/mine' },
  { label: '站内沟通', icon: MessageCircle, to: '/conversations' },
  { label: '消息中心', icon: Bell, to: '/notifications' }
]

const verificationForm = reactive({
  method: 'STUDENT_CARD_IMAGE',
  realName: '',
  studentNumber: '',
  school: '',
  department: '',
  evidenceUrl: ''
})

const userName = computed(() =>
  isAuthenticated.value ? state.profile?.nickname || state.user?.username || '张同学' : '未登录'
)
const accountMeta = computed(() => {
  if (!isAuthenticated.value) return '登录后同步认证和任务记录'
  return state.profile?.campus || state.profile?.college || state.user?.role || '校园互助账号'
})
const verification = computed(() => state.verification || null)
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

async function handleRefresh() {
  if (!isAuthenticated.value) return
  loading.value = true
  feedback.value = ''
  try {
    await refreshSession()
    setFeedback('认证状态已刷新。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    loading.value = false
  }
}

async function handleSubmitVerification() {
  submitting.value = true
  feedback.value = ''
  try {
    await submitVerification({
      method: verificationForm.method,
      realName: verificationForm.realName,
      studentNumber: verificationForm.studentNumber,
      school: verificationForm.school,
      department: verificationForm.department,
      evidenceUrl: verificationForm.evidenceUrl
    })
    await refreshSession()
    setFeedback('校内认证资料已提交，等待平台审核。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
  if (!isAuthenticated.value) return
  loading.value = true
  try {
    await refreshSession()
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
        @click="sidebarCollapsed = !sidebarCollapsed"
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
        href="/"
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
      <label class="search-box">
        <Search
          :size="22"
          aria-hidden="true"
        />
        <input
          type="search"
          placeholder="搜索任务、技能、地点或关键词"
          aria-label="搜索任务、技能、地点或关键词"
        >
      </label>

      <div class="topbar-account">
        <button
          class="notice-button"
          type="button"
          aria-label="消息通知"
        >
          <Bell
            :size="25"
            aria-hidden="true"
          />
          <span>3</span>
        </button>
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
              <UserRound
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
                <UserRound
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
              to="/"
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
          <ShieldCheck
            :size="32"
            aria-hidden="true"
          />
        </div>
        <div class="title-copy">
          <p>校内认证</p>
          <h1>校园身份认证中心</h1>
          <span>提交真实身份信息，建立可信的校园互助环境。</span>
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
        <article class="panel-card verification-overview">
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
              <ClipboardCheck
                :size="17"
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
                提交前请确认
              </p>
              <h2>需要准备什么</h2>
            </div>
            <Info
              :size="24"
              aria-hidden="true"
            />
          </div>
          <ul class="rule-list">
            <li>
              <ShieldCheck
                :size="17"
                aria-hidden="true"
              />
              请填写与你本人一致的真实姓名、学号和学校信息。
            </li>
            <li>
              <LockKeyhole
                :size="17"
                aria-hidden="true"
              />
              材料链接可选；如果提交，请确保审核期间可以正常访问。
            </li>
            <li>
              <Info
                :size="17"
                aria-hidden="true"
              />
              审核中或已通过时，不需要重复提交。
            </li>
          </ul>
        </article>

        <article class="panel-card verification-form-card">
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

            <label class="field field-wide">
              <span>材料链接</span>
              <input
                v-model.trim="verificationForm.evidenceUrl"
                placeholder="学生证、校园邮箱或人工审核材料链接"
              >
            </label>

            <button
              class="primary-action"
              type="submit"
              :disabled="submitting"
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
            v-else
            class="locked-state"
          >
            <ShieldCheck
              :size="34"
              aria-hidden="true"
            />
            <strong>{{ lockMessage }}</strong>
            <span>如需更新状态，可点击右上角刷新按钮重新同步后端认证记录。</span>
          </div>
        </article>

        <article class="panel-card privacy-card">
          <div class="panel-heading">
            <div>
              <p class="section-kicker">
                安全说明
              </p>
              <h2>你的资料如何使用</h2>
            </div>
            <CalendarClock
              :size="24"
              aria-hidden="true"
            />
          </div>
          <p>
            认证信息仅用于平台核验校园身份。其他用户不会在公开页面看到你的真实姓名、学号或材料链接。
          </p>
          <p>
            审核完成后，页面只展示认证状态和必要的学校信息，帮助大家判断账号是否可信。
          </p>
          <div
            v-if="verification?.rejectReason"
            class="reject-box"
          >
            <strong>驳回原因</strong>
            <span>{{ verification.rejectReason }}</span>
          </div>
        </article>
      </section>
    </main>
  </section>
</template>

<style scoped>
.dashboard-shell {
  --sidebar-width: 304px;
  --topbar-height: 96px;
  min-height: 100vh;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 247, 251, 0.96)),
    #fbfafc;
  color: #2b2733;
  font-family:
    KaiTi,
    STKaiti,
    "楷体",
    "华文楷体",
    "Microsoft YaHei",
    serif;
}

.dashboard-shell.is-collapsed {
  --sidebar-width: 92px;
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
  padding: 46px 20px 28px;
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
  color: #746dc3;
  box-shadow: 0 8px 22px rgba(116, 109, 195, 0.1);
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.collapse-button:hover {
  border-color: #c9c2ee;
  background: #f7f4ff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 0 14px;
  color: #7067be;
  text-decoration: none;
}

.brand-mark {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: #746dc3;
}

.brand-text {
  max-width: 168px;
  color: #7067be;
  font-size: 1.28rem;
  font-weight: 900;
  line-height: 1.08;
  overflow-wrap: anywhere;
  text-transform: uppercase;
  transition:
    max-width 180ms ease,
    opacity 160ms ease;
}

.sidebar-nav {
  display: grid;
  gap: 12px;
  margin-top: 42px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 58px;
  padding: 0 22px;
  border-radius: 8px;
  color: #8b8994;
  font-size: 1.14rem;
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
  background: linear-gradient(90deg, #f1ecff 0%, #fbf9ff 100%);
  color: #746dc3;
  box-shadow: 0 14px 30px rgba(116, 109, 195, 0.085);
}

.nav-item.active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: #746dc3;
}

.nav-item span {
  transition:
    opacity 160ms ease,
    max-width 180ms ease;
}

.is-collapsed .dashboard-sidebar {
  padding-inline: 14px;
}

.is-collapsed .collapse-button {
  right: 50%;
  transform: translateX(50%);
}

.is-collapsed .brand {
  justify-content: center;
  padding-inline: 0;
}

.is-collapsed .brand-text,
.is-collapsed .nav-item span {
  max-width: 0;
  opacity: 0;
  overflow: hidden;
}

.is-collapsed .sidebar-nav {
  margin-top: 42px;
}

.is-collapsed .nav-item {
  justify-content: center;
  gap: 0;
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
  padding: 0 42px 0 50px;
  border-bottom: 1px solid #e4e1ea;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(18px);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 56px;
  padding: 0 20px;
  border: 1px solid #ddd9e8;
  border-radius: 13px;
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
  font-size: 1.08rem;
}

.search-box input::placeholder {
  color: #b8b5c1;
}

.topbar-account {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
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
  width: 48px;
  height: 48px;
  border-radius: 13px;
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
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #746dc3;
  color: #ffffff;
  font-size: 0.84rem;
  font-weight: 800;
  line-height: 22px;
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
  gap: 13px;
  min-height: 56px;
  padding: 0 10px;
  border-radius: 13px;
  font-size: 1.14rem;
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
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 32% 28%, #f5f2ff 0 23%, transparent 24%),
    linear-gradient(135deg, #d9d3ff, #f1efff);
  color: #746dc3;
}

.avatar--compact {
  width: 38px;
  height: 38px;
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
  padding: calc(var(--topbar-height) + 30px) 30px 42px;
}

.page-title-card,
.panel-card,
.page-feedback {
  border: 1px solid #e4e1ea;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 36px rgba(42, 38, 61, 0.045);
}

.page-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
  min-height: 132px;
  padding: 26px 38px;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 78px;
  height: 82px;
  border-radius: 18px;
  background: linear-gradient(135deg, #eeeaff, #f9f7ff);
  color: #746dc3;
}

.title-copy {
  display: grid;
  gap: 8px;
}

.title-copy p,
.section-kicker {
  margin: 0;
  color: #746dc3;
  font-size: 0.92rem;
  font-weight: 900;
}

.title-copy h1 {
  margin: 0;
  color: #3c3543;
  font-size: 1.9rem;
  font-weight: 900;
  line-height: 1.1;
}

.title-copy span {
  color: #8a8792;
  font-size: 1rem;
}

.page-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 18px;
  padding: 12px 14px;
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
  grid-template-columns: minmax(0, 1.42fr) minmax(320px, 0.88fr);
  grid-template-areas:
    "overview safety"
    "form privacy";
  gap: 22px;
  margin-top: 22px;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 18px;
  min-height: 190px;
  padding: 24px;
}

.verification-overview {
  grid-area: overview;
}

.safety-card {
  grid-area: safety;
}

.verification-form-card {
  grid-area: form;
}

.privacy-card {
  grid-area: privacy;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.panel-heading h2 {
  margin: 4px 0 0;
  color: #3c3543;
  font-size: 1.38rem;
  font-weight: 900;
}

.panel-copy,
.privacy-card p,
.locked-state span {
  margin: 0;
  color: #746f7a;
  line-height: 1.7;
}

.step-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.step-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 42px;
  border: 1px solid #e6e2eb;
  border-radius: 10px;
  background: #fbf9fc;
  color: #8a8792;
  font-weight: 900;
}

.step-item.done {
  border-color: #d4cdef;
  background: #f4f1ff;
  color: #746dc3;
}

.record-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.record-grid div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid #eeeaf2;
  border-radius: 10px;
  background: #fcfbfd;
}

.record-grid dt {
  color: #8a8792;
  font-size: 0.88rem;
}

.record-grid dd {
  margin: 0;
  color: #3c3543;
  font-weight: 900;
}

.rule-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.rule-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #746f7a;
  line-height: 1.6;
}

.rule-list svg {
  flex: 0 0 auto;
  margin-top: 4px;
  color: #746dc3;
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
  font-weight: 900;
}

.field input,
.field select {
  width: 100%;
  height: 48px;
  border: 1px solid #ddd9e8;
  border-radius: 10px;
  background: #ffffff;
  color: #393545;
  padding: 0 13px;
  outline: 0;
}

.field input:focus,
.field select:focus {
  border-color: #9a93dc;
  box-shadow: 0 0 0 4px rgba(116, 109, 195, 0.12);
}

.primary-action,
.ghost-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 46px;
  border-radius: 10px;
  font-weight: 900;
}

.primary-action {
  border: 1px solid #746dc3;
  background: #746dc3;
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(116, 109, 195, 0.16);
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

.locked-state {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: 300px;
  border: 1px dashed #ddd8ec;
  border-radius: 12px;
  background: #fcfbff;
  color: #746dc3;
  text-align: center;
}

.locked-state strong {
  color: #3c3543;
  font-size: 1.16rem;
}

.reject-box {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #fecaca;
  border-radius: 10px;
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
    --sidebar-width: 248px;
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
      "overview"
      "safety"
      "form"
      "privacy";
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
  .verification-form,
  .record-grid,
  .step-list {
    grid-template-columns: 1fr;
  }
}
</style>
