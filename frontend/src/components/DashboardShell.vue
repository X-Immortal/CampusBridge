<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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
  UserRound
} from '@lucide/vue'
import { useAuth } from '../stores/auth'

defineProps({
  activeLabel: {
    type: String,
    default: '个人主页'
  }
})

const router = useRouter()
const { state, isAuthenticated, logout } = useAuth()
const sidebarCollapsed = ref(false)
const accountMenuOpen = ref(false)
const accountMenuRef = ref(null)

const navItems = [
  { label: '个人主页', icon: Home, to: '/' },
  { label: '任务广场', icon: Grid2X2, to: '/tasks' },
  { label: '发布任务', icon: PlusCircle, to: '/tasks/new' },
  { label: '我的任务', icon: ClipboardList, to: '/tasks/mine' },
  { label: '站内沟通', icon: MessageCircle, to: '/conversations' },
  { label: '消息中心', icon: Bell, to: '/notifications' }
]

const userName = computed(() =>
  isAuthenticated.value ? state.profile?.nickname || state.user?.username || '张同学' : '未登录'
)
const accountMeta = computed(() => {
  if (!isAuthenticated.value) return '登录后同步认证和任务记录'
  return state.profile?.campus || state.profile?.college || state.user?.role || '校园互助账号'
})

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

      <RouterLink
        class="brand"
        to="/"
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

    <div class="dashboard-main">
      <slot />
    </div>
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
  border-radius: 8px;
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
  border-radius: 8px;
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
}
</style>
