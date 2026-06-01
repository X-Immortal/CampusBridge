<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  ClipboardList,
  Grid2X2,
  Home,
  LogIn,
  LogOut,
  MessageCircle,
  PlusCircle,
  School,
  Search,
  ShieldCheck,
  Star,
  UserRound
} from '@lucide/vue'
import { useDashboardSidebar } from '../composables/useDashboardSidebar'
import { useNotificationCenter } from '../composables/useNotificationCenter'
import { useAuth } from '../stores/auth'

defineProps({
  activeLabel: {
    type: String,
    default: '个人主页'
  },
  publishMode: {
    type: Boolean,
    default: false
  }
})

const router = useRouter()
const { state, isAuthenticated, currentUserId, logout } = useAuth()
const { sidebarCollapsed, toggleSidebarCollapsed } = useDashboardSidebar()
const { unreadCount: notificationUnreadCount, refreshNotificationSummary } = useNotificationCenter()
const accountMenuOpen = ref(false)
const accountMenuRef = ref(null)
const avatarLoadFailed = ref(false)
const searchKeyword = ref('')

function handleSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  router.push({ name: 'task-list', query: { keyword } })
  searchKeyword.value = ''
}

const navItems = [
  { label: '个人主页', icon: Home, to: '/home' },
  { label: '任务广场', icon: Grid2X2, to: '/tasks' },
  { label: '发布任务', icon: PlusCircle, to: '/tasks/new' },
  { label: '我的任务', icon: ClipboardList, to: '/tasks/mine' },
  { label: '站内沟通', icon: MessageCircle, to: '/conversations' },
  { label: '评价中心', icon: Star, to: '/reviews' },
  { label: '消息中心', icon: Bell, to: '/notifications' }
]

const userName = computed(() =>
  isAuthenticated.value ? state.profile?.nickname || state.user?.username || '张同学' : '未登录'
)
const profileAvatarUrl = computed(() => String(state.profile?.avatarUrl || '').trim())
const profileAvatarSrc = computed(() => {
  if (!profileAvatarUrl.value || avatarLoadFailed.value) return ''
  if (/^https?:\/\/example\.com\//i.test(profileAvatarUrl.value)) return ''
  return profileAvatarUrl.value
})
const profileInitial = computed(() => {
  const name = String(userName.value || '').trim()
  return name ? name.slice(0, 1) : '同'
})
const accountMeta = computed(() => {
  if (!isAuthenticated.value) return '登录后同步认证和任务记录'
  return state.profile?.campus || state.profile?.college || state.user?.role || '校园互助账号'
})
const notificationBadgeCount = computed(() => notificationUnreadCount.value)
const notificationBadgeLabel = computed(() =>
  notificationBadgeCount.value > 99 ? '99+' : String(notificationBadgeCount.value)
)

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

function handleAvatarError() {
  avatarLoadFailed.value = true
}

watch(profileAvatarUrl, () => {
  avatarLoadFailed.value = false
})

watch(
  [isAuthenticated, currentUserId],
  () => {
    refreshNotificationSummary()
  },
  { immediate: true }
)

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <section
    class="dashboard-shell"
    :class="{ 'is-collapsed': sidebarCollapsed && !publishMode, 'dashboard-shell--publish': publishMode }"
  >
    <aside
      v-if="!publishMode"
      class="dashboard-sidebar"
    >
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

      <RouterLink
        class="brand"
        to="/home"
        aria-label="CampusBridge"
      >
        <span class="brand-mark">
          <School
            :size="38"
            aria-hidden="true"
          />
        </span>
        <span class="brand-text">CampusBridge</span>
      </RouterLink>

      <nav
        class="sidebar-nav"
        aria-label="主导航"
      >
        <RouterLink
          v-for="item in navItems"
          :key="item.label"
          class="nav-item"
          :class="{ active: item.label === activeLabel }"
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

    <div class="dashboard-main">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.dashboard-shell {
  --sidebar-width: 293px;
  --topbar-height: 76px;
  min-height: 100vh;
  background: #fbfafc;
  color: #202033;
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
  border-radius: 8px;
  background: #ffffff;
  color: #4f46e5;
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
  color: #78738a;
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: 0;
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

.is-collapsed .dashboard-sidebar {
  padding-inline: 12px;
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
  padding: 0 30px 0 34px;
  border-bottom: 1px solid #e4e7ef;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(18px);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 46px;
  padding: 0 16px;
  border: 1px solid #dbe1ee;
  border-radius: 8px;
  background: #ffffff;
  color: #667085;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.search-box:focus-within {
  border-color: #818cf8;
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.12);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #111827;
  font-size: 0.92rem;
}

.search-box input::placeholder {
  color: #667085;
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
  color: #202033;
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
  background: #f5f3ff;
  color: #4f46e5;
}

.notice-button span {
  position: absolute;
  top: 0;
  right: 2px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #4f46e5;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 20px;
}

.topbar-divider {
  width: 1px;
  height: 34px;
  background: #e4e7ef;
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
  background: linear-gradient(135deg, #e0e7ff, #f5f3ff);
  color: #4f46e5;
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
  border: 1px solid #e4e7ef;
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
  border-bottom: 1px solid #eef0f6;
}

.account-dropdown-head div {
  display: grid;
  gap: 3px;
}

.account-dropdown-head strong {
  color: #111827;
  font-size: 0.98rem;
  line-height: 1.25;
}

.account-dropdown-head small {
  color: #667085;
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
  color: #374151;
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
  background: #f5f3ff;
  color: #4f46e5;
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

.dashboard-shell--publish {
  --sidebar-width: 0px;
  --topbar-height: 68px;
  background: #ffffff;
}

.dashboard-shell--publish .dashboard-topbar {
  left: 0;
  height: var(--topbar-height);
  padding: 0 32px 0 62px;
  grid-template-columns: minmax(280px, 934px) auto;
}

.dashboard-shell--publish .dashboard-main {
  margin-left: 0;
  padding: 76px 28px 20px;
}

.dashboard-shell--publish .search-box {
  height: 44px;
}

@media (max-width: 1100px) {
  .dashboard-shell {
    --sidebar-width: 252px;
  }

  .dashboard-shell.is-collapsed {
    --sidebar-width: 78px;
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

  .dashboard-shell--publish .dashboard-topbar {
    grid-template-columns: minmax(0, 1fr) auto;
    padding-inline: 20px;
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
    border-bottom: 1px solid #e4e7ef;
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

  .dashboard-shell--publish {
    --topbar-height: 132px;
  }

  .dashboard-shell--publish .dashboard-topbar {
    grid-template-columns: 1fr;
    padding-inline: 16px;
  }

  .dashboard-shell--publish .topbar-account {
    justify-content: space-between;
  }

  .dashboard-shell--publish .dashboard-main {
    padding: 16px;
  }
}
</style>
