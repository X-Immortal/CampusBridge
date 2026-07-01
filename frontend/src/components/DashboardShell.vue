<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  Compass,
  LogIn,
  LogOut,
  Palette,
  PlusCircle,
  School,
  Search,
  UserRound
} from '@lucide/vue'
import {
  canShowNavItem,
  isNavItemActive,
  primaryNavItems,
  publishTaskAction,
  userMenuItems
} from '../app/navigation'
import { useDashboardSidebar } from '../composables/useDashboardSidebar'
import { useNotificationCenter } from '../composables/useNotificationCenter'
import { useAuth } from '../stores/auth'
import { usePreferences } from '../composables/usePreferences'
import { useToast } from '../composables/useToast'
import BackToTop from './BackToTop.vue'
import ThemePreferences from './ThemePreferences.vue'

const props = defineProps({
  activeLabel: {
    type: String,
    default: ''
  },
  publishMode: {
    type: Boolean,
    default: false
  }
})

const router = useRouter()
const route = useRoute()
const auth = useAuth()
const { state, isAuthenticated, isGuest, currentUserId, logout, exitGuestMode } = auth
const { sidebarCollapsed, toggleSidebarCollapsed } = useDashboardSidebar()
const { unreadCount: notificationUnreadCount, refreshNotificationSummary } = useNotificationCenter()
const { showToast } = useToast()
usePreferences()
const accountMenuOpen = ref(false)
const accountMenuRef = ref(null)
const preferencesOpen = ref(false)
const preferencesRef = ref(null)
const avatarLoadFailed = ref(false)
const searchKeyword = ref('')
const publishMode = computed(() => props.publishMode)

const activeSection = computed(() => route.meta?.section || '')

const searchPlaceholder = computed(() => {
  if (activeSection.value === 'posts') return '搜索帖子标题、内容或关键词'
  if (activeSection.value === 'tasks') return '搜索任务标题、内容或关键词'
  return '搜索任务、帖子或关键词'
})

function handleSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return

  if (activeSection.value === 'posts') {
    router.push({ name: 'post-list', query: { keyword } })
  } else if (activeSection.value === 'tasks') {
    router.push({ name: 'task-list', query: { keyword } })
  } else {
    router.push({ name: 'homepage', query: { keyword } })
  }
  searchKeyword.value = ''
}

const visibleNavItems = computed(() => primaryNavItems.filter((item) => canShowNavItem(item, auth)))

function isItemActive(item) {
  return isNavItemActive(item, route) || item.label === props.activeLabel
}

function handlePublishTask() {
  if (!isAuthenticated.value) {
    showToast('发布任务需要登录', { type: 'warning' })
    router.push({ name: 'auth', query: { redirect: '/tasks/new' } })
    return
  }
  if (state.verification?.status !== 'VERIFIED') {
    showToast('完成认证后可发布任务', { type: 'warning' })
    router.push({ name: 'workspace-verification' })
    return
  }
  router.push({ name: 'task-publish' })
}

const menuItems = computed(() => userMenuItems(auth))

const userName = computed(() => {
  if (isGuest.value) return '游客'
  if (isAuthenticated.value) return state.profile?.nickname || state.user?.username || '张同学'
  return '未登录'
})
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
  if (isGuest.value) return '游客模式，可浏览公开内容'
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
  if (accountMenuOpen.value) preferencesOpen.value = false
}

function closePreferences() {
  preferencesOpen.value = false
}

function togglePreferences() {
  preferencesOpen.value = !preferencesOpen.value
  if (preferencesOpen.value) accountMenuOpen.value = false
}

function handleDocumentPointerDown(event) {
  if (accountMenuOpen.value && accountMenuRef.value && !accountMenuRef.value.contains(event.target)) {
    closeAccountMenu()
  }
  if (preferencesOpen.value && preferencesRef.value && !preferencesRef.value.contains(event.target)) {
    closePreferences()
  }
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') {
    closeAccountMenu()
    closePreferences()
  }
}

async function handleLogout() {
  logout()
  closeAccountMenu()
  await router.push('/auth')
}

async function handleExitGuestMode() {
  exitGuestMode()
  closeAccountMenu()
  showToast('已退出游客模式', { type: 'info' })
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
          v-for="item in visibleNavItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: isItemActive(item) }"
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
          :placeholder="searchPlaceholder"
          :aria-label="searchPlaceholder"
        >
      </form>

      <div class="topbar-account">
        <button
          class="topbar-publish-button"
          type="button"
          :aria-label="publishTaskAction.label"
          @click="handlePublishTask"
        >
          <PlusCircle
            :size="18"
            aria-hidden="true"
          />
          <span>{{ publishTaskAction.label }}</span>
        </button>
        <RouterLink
          class="notice-button"
          to="/workspace/notifications"
          :aria-label="notificationBadgeCount ? `消息通知，${notificationBadgeCount} 条未读` : '消息通知'"
        >
          <Bell
            :size="25"
            aria-hidden="true"
          />
          <span v-if="notificationBadgeCount > 0">{{ notificationBadgeLabel }}</span>
        </RouterLink>
        <div
          ref="preferencesRef"
          class="topbar-preferences"
        >
          <button
            class="preferences-toggle"
            type="button"
            aria-label="外观设置"
            title="外观设置"
            aria-haspopup="dialog"
            :aria-expanded="preferencesOpen"
            @click="togglePreferences"
          >
            <Palette
              :size="20"
              aria-hidden="true"
            />
          </button>
          <ThemePreferences v-model:open="preferencesOpen" />
        </div>
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
            <span
              v-if="isGuest"
              class="guest-mode-chip"
            >游客</span>
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
              v-for="item in menuItems"
              :key="item.key"
              class="account-menu-item"
              :to="item.to"
              role="menuitem"
              @click="closeAccountMenu"
            >
              <component
                :is="item.icon"
                :size="17"
                aria-hidden="true"
              />
              {{ item.label }}
            </RouterLink>

            <button
              v-if="isAuthenticated"
              class="account-menu-item"
              type="button"
              role="menuitem"
              @click="togglePreferences(); closeAccountMenu()"
            >
              <Palette
                :size="17"
                aria-hidden="true"
              />
              外观主题
            </button>

            <div
              v-if="isAuthenticated || isGuest"
              class="account-dropdown-divider"
            />

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
            <template v-else-if="isGuest">
              <RouterLink
                class="account-menu-item"
                to="/auth"
                role="menuitem"
                @click="closeAccountMenu"
              >
                <LogIn
                  :size="17"
                  aria-hidden="true"
                />
                登录或注册
              </RouterLink>
              <button
                class="account-menu-item account-menu-item--danger"
                type="button"
                role="menuitem"
                @click="handleExitGuestMode"
              >
                <LogOut
                  :size="17"
                  aria-hidden="true"
                />
                退出游客模式
              </button>
            </template>
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

    <nav
      v-if="!publishMode"
      class="mobile-bottom-nav"
      aria-label="移动端主导航"
    >
      <RouterLink
        v-for="item in visibleNavItems"
        :key="item.key"
        class="mobile-bottom-nav__item"
        :class="{ active: isItemActive(item) }"
        :to="item.to"
      >
        <component
          :is="item.icon"
          :size="22"
          aria-hidden="true"
        />
        <span>{{ item.shortLabel || item.label }}</span>
      </RouterLink>
      <button
        class="mobile-bottom-nav__item mobile-bottom-nav__item--publish"
        type="button"
        :aria-label="publishTaskAction.shortLabel"
        @click="handlePublishTask"
      >
        <PlusCircle
          :size="24"
          aria-hidden="true"
        />
        <span>{{ publishTaskAction.shortLabel }}</span>
      </button>
      <button
        class="mobile-bottom-nav__item"
        type="button"
        aria-label="游客浏览"
        @click="isGuest ? router.push('/auth') : router.push('/workspace')"
      >
        <Compass
          :size="22"
          aria-hidden="true"
        />
        <span>{{ isAuthenticated ? '我的' : '我的' }}</span>
      </button>
    </nav>

    <BackToTop />
  </section>
</template>

<style scoped>
.dashboard-shell {
  --sidebar-width: 293px;
  --topbar-height: 76px;
  min-height: 100vh;
  background: var(--color-bg);
  color: var(--color-text);
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
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.collapse-button {
  position: absolute;
  bottom: 18px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-brand);
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    color 180ms ease;
}

.collapse-button:hover {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 0 10px;
  color: var(--color-brand);
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
  color: var(--color-brand-hover);
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
  color: var(--color-text-muted);
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
  background: linear-gradient(90deg, var(--color-brand-soft) 0%, var(--color-brand-soft) 100%);
  color: var(--color-brand);
  box-shadow: var(--shadow-card);
}

.nav-item.active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--color-brand);
}

.is-collapsed .dashboard-sidebar {
  padding-inline: 12px;
}

.is-collapsed .collapse-button {
  bottom: 42px;
  right: 50%;
  transform: translateX(50%);
}

.is-collapsed .brand {
  justify-content: center;
  padding-inline: 0;
  gap: 0;
}

.is-collapsed .brand-text,
.is-collapsed .nav-item span {
  display: none;
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
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  backdrop-filter: blur(18px);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 46px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.search-box:focus-within {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 0.92rem;
}

.search-box input::placeholder {
  color: var(--color-text-muted);
}

.topbar-account {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  min-width: max-content;
}

.topbar-preferences {
  position: relative;
}

.preferences-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition:
    color var(--transition-base),
    border-color var(--transition-base),
    background-color var(--transition-base),
    box-shadow var(--transition-base);
}

.preferences-toggle:hover,
.preferences-toggle.active {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  box-shadow: var(--shadow-focus);
}

.preferences-toggle:focus-visible {
  outline: 3px solid var(--color-brand-glow);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .preferences-toggle {
    width: 38px;
    height: 38px;
  }
}

.notice-button,
.account-button,
.account-menu-item {
  border: 0;
  background: transparent;
  color: var(--color-text);
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
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.notice-button span {
  position: absolute;
  top: 0;
  right: 2px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-brand);
  color: var(--color-surface);
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 20px;
}

.topbar-divider {
  width: 1px;
  height: 34px;
  background: var(--color-border);
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

.guest-mode-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--color-warning-bg, rgba(245, 158, 11, 0.12));
  color: var(--color-warning, #b45309);
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.04em;
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
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand);
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-dropdown);
}

.account-dropdown-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 9px 12px;
  border-bottom: 1px solid var(--color-border);
}

.account-dropdown-head div {
  display: grid;
  gap: 3px;
}

.account-dropdown-head strong {
  color: var(--color-text);
  font-size: 0.98rem;
  line-height: 1.25;
}

.account-dropdown-head small {
  color: var(--color-text-muted);
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
  color: var(--color-text-secondary);
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
  background: var(--color-brand-soft);
  color: var(--color-brand);
  outline: 0;
}

.account-menu-item--danger {
  color: var(--color-danger);
}

.account-menu-item--danger:hover,
.account-menu-item--danger:focus-visible {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.dashboard-main {
  min-height: 100vh;
  margin-left: var(--sidebar-width);
  padding: calc(var(--topbar-height) + 22px) 24px 36px;
}

.topbar-publish-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-brand);
  border-radius: 8px;
  background: var(--color-brand);
  color: var(--color-surface);
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.topbar-publish-button:hover:not(:disabled) {
  background: var(--color-brand-hover);
  border-color: var(--color-brand-hover);
  box-shadow: var(--shadow-button);
}

.account-dropdown-divider {
  height: 1px;
  margin: 4px 0;
  background: var(--color-border);
}

.mobile-bottom-nav {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 35;
  display: none;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  align-items: center;
  height: 64px;
  padding: 0 8px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
}

.mobile-bottom-nav__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 54px;
  padding: 4px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.72rem;
  font-weight: 800;
  text-decoration: none;
  cursor: pointer;
  transition:
    background-color 180ms ease,
    color 180ms ease;
}

.mobile-bottom-nav__item:hover,
.mobile-bottom-nav__item.active {
  color: var(--color-brand);
}

.mobile-bottom-nav__item--publish {
  color: var(--color-brand);
}

.dashboard-shell--publish {
  --sidebar-width: 0px;
  --topbar-height: 68px;
  background: var(--color-surface);
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
    border-bottom: 1px solid var(--color-border);
  }

  .collapse-button {
    display: none;
  }

  .brand {
    justify-content: flex-start;
  }

  .is-collapsed .brand-text,
  .is-collapsed .nav-item span {
    display: block;
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
    padding: 20px 16px 84px;
  }

  .mobile-bottom-nav {
    display: grid;
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
