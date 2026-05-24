<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { BadgeCheck, KeyRound, RefreshCw, ShieldCheck, UserCircle } from '@lucide/vue'
import StatusBadge from '../components/StatusBadge.vue'
import { optionLabel, VERIFICATION_METHODS } from '../constants'
import { useAuth } from '../stores/auth'

const { state, isAuthenticated, currentUserId, verificationStatus, refreshSession } = useAuth()
const loading = ref(false)
const feedback = ref('')

const authStatus = computed(() => (isAuthenticated.value ? 'LOGGED_IN' : 'GUEST'))
const accountEnabledStatus = computed(() => (state.user?.enabled === 0 ? 'DISABLED' : 'ENABLED'))

async function handleRefresh() {
  loading.value = true
  feedback.value = ''
  try {
    await refreshSession()
    feedback.value = '账号与校内认证状态已刷新。'
  } catch (error) {
    feedback.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (isAuthenticated.value) refreshSession()
})
</script>

<template>
  <section class="page home-page">
    <div class="hero-band">
      <div class="hero-copy">
        <p class="eyebrow">
          <ShieldCheck
            :size="16"
            aria-hidden="true"
          />
          FE01 身份与校内认证
        </p>
        <h1 class="page-title">
          先把登录、注册和认证状态稳稳接住。
        </h1>
        <p class="page-subtitle">
          当前分支只保留 FE01 范围：账号登录注册、本地 Token 状态、当前用户同步和校内认证提交/查询。
        </p>
        <div class="hero-actions">
          <RouterLink
            class="button"
            to="/auth"
          >
            <BadgeCheck
              :size="18"
              aria-hidden="true"
            />
            打开认证档案
          </RouterLink>
          <button
            class="button button--secondary"
            type="button"
            :disabled="loading || !isAuthenticated"
            @click="handleRefresh"
          >
            <RefreshCw
              :size="18"
              aria-hidden="true"
            />
            刷新状态
          </button>
        </div>
      </div>

      <aside
        class="trust-strip"
        aria-label="当前账号状态"
      >
        <div>
          <span class="trust-kicker">登录状态</span>
          <strong>{{ isAuthenticated ? state.user?.username || '已登录用户' : '游客模式' }}</strong>
        </div>
        <StatusBadge :status="authStatus" />
        <StatusBadge
          :status="verificationStatus"
          :label="verificationStatus === 'VERIFIED' ? '校内已认证' : '需要校内认证'"
        />
      </aside>
    </div>

    <section
      v-if="feedback"
      class="alert"
      role="status"
    >
      <RefreshCw
        :size="18"
        aria-hidden="true"
      />
      <span>{{ feedback }}</span>
    </section>

    <section class="grid grid--three">
      <article class="metric-card">
        <span class="metric-label">当前用户</span>
        <strong class="metric-value">{{ currentUserId || '-' }}</strong>
        <span class="metric-label">来自登录结果或 `/users/me`</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">账号状态</span>
        <strong class="metric-value status-text">{{ optionLabel([], accountEnabledStatus) }}</strong>
        <span class="metric-label">用于判断是否可继续访问受保护接口</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">认证方式</span>
        <strong class="metric-value status-text">{{ optionLabel(VERIFICATION_METHODS, state.verification?.method) }}</strong>
        <span class="metric-label">来自 `/verifications` 最新记录</span>
      </article>
    </section>

    <section class="grid grid--two">
      <article class="panel panel--quiet">
        <div class="panel-title">
          <h2>FE01 覆盖范围</h2>
        </div>
        <div class="scope-list">
          <p>
            <KeyRound
              :size="18"
              aria-hidden="true"
            />
            注册、登录、Token 持久化和退出登录。
          </p>
          <p>
            <UserCircle
              :size="18"
              aria-hidden="true"
            />
            登录后同步当前用户基础信息。
          </p>
          <p>
            <ShieldCheck
              :size="18"
              aria-hidden="true"
            />
            提交校内认证并展示认证状态。
          </p>
        </div>
      </article>

      <article class="panel panel--quiet">
        <div class="panel-title">
          <h2>接口契约</h2>
        </div>
        <div class="endpoint-list">
          <code>POST /api/v1/auth/register</code>
          <code>POST /api/v1/auth/login</code>
          <code>GET /api/v1/users/me</code>
          <code>POST /api/v1/verifications</code>
          <code>GET /api/v1/verifications</code>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 22px;
}

.hero-band {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: stretch;
  padding: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: #ffffff;
  box-shadow: var(--shadow-soft);
}

.hero-copy {
  display: grid;
  gap: 18px;
  align-content: center;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.trust-strip {
  display: grid;
  align-content: center;
  gap: 16px;
  padding: 20px;
  border-left: 4px solid var(--color-action);
  border-radius: var(--radius-card);
  background: #f1f7fb;
}

.trust-strip strong {
  display: block;
  margin-top: 6px;
  color: var(--color-primary);
  font-size: 1.35rem;
}

.trust-kicker {
  color: var(--color-muted);
  font-size: 0.82rem;
  font-weight: 700;
}

.status-text {
  font-size: 1.35rem;
}

.scope-list,
.endpoint-list {
  display: grid;
  gap: 12px;
}

.scope-list p {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.endpoint-list code {
  display: block;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #f8fafc;
  color: var(--color-primary);
}

@media (max-width: 980px) {
  .hero-band {
    grid-template-columns: 1fr;
  }
}
</style>
