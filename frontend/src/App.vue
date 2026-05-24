<template>
  <div class="app-shell">
    <header
      v-if="!isAuthRoute"
      class="topbar"
    >
      <RouterLink
        class="brand"
        to="/"
        aria-label="返回 CampusBridge 首页"
      >
        <span class="brand-mark">
          <Handshake
            :size="22"
            aria-hidden="true"
          />
        </span>
        <span>
          <strong>CampusBridge</strong>
          <small>校园互助服务平台</small>
        </span>
      </RouterLink>

      <nav
        class="nav"
        aria-label="主导航"
      >
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="nav-link"
          :to="item.to"
        >
          <component
            :is="item.icon"
            :size="17"
            aria-hidden="true"
          />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="account">
        <span
          v-if="isAuthenticated"
          class="account-pill"
        >
          <ShieldCheck
            :size="16"
            aria-hidden="true"
          />
          {{ state.user?.username || '已登录' }}
        </span>
        <RouterLink
          v-else
          class="button button--secondary"
          to="/auth"
        >
          <LogIn
            :size="17"
            aria-hidden="true"
          />
          登录
        </RouterLink>
        <button
          v-if="isAuthenticated"
          class="icon-button"
          type="button"
          title="退出登录"
          @click="logout"
        >
          <LogOut
            :size="17"
            aria-hidden="true"
          />
        </button>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Handshake, Home, LogIn, LogOut, ShieldCheck, UserCircle } from '@lucide/vue'
import { RouterLink, useRoute } from 'vue-router'
import { useAuth } from './stores/auth'

const { state, isAuthenticated, logout } = useAuth()
const route = useRoute()
const isAuthRoute = computed(() => route.name === 'auth')

const navItems = [
  { to: '/', label: '工作台', icon: Home },
  { to: '/auth', label: '登录注册', icon: UserCircle }
]
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
  min-height: 72px;
  padding: 12px 24px;
  border-bottom: 1px solid rgba(219, 228, 238, 0.85);
  background: rgba(248, 250, 252, 0.94);
  backdrop-filter: blur(12px);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 230px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #0f172a;
  color: #ffffff;
}

.brand strong {
  display: block;
  margin: 0;
  color: #0f172a;
  font-size: 1rem;
}

.brand small {
  display: block;
  margin-top: 2px;
  color: #475569;
  font-size: 0.78rem;
}

.nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 11px;
  border-radius: 8px;
  color: #334155;
  font-size: 0.92rem;
  font-weight: 700;
  transition:
    background-color 180ms ease,
    color 180ms ease;
}

.nav-link:hover,
.nav-link.router-link-active {
  background: #e0f2fe;
  color: #075985;
}

.account {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.account-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid #bbf7d0;
  border-radius: 999px;
  background: #f0fdf4;
  color: #166534;
  font-size: 0.88rem;
  font-weight: 700;
}

.main-content {
  min-height: calc(100vh - 72px);
}

@media (max-width: 760px) {
  .topbar {
    grid-template-columns: 1fr;
    width: 100vw;
    max-width: 100vw;
    overflow: hidden;
    padding: 10px 14px;
  }

  .brand {
    min-width: 0;
  }

  .account {
    justify-content: flex-start;
  }
}
</style>
