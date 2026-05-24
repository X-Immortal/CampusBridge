<script setup>
import { computed, reactive, ref } from 'vue'
import { BadgeCheck, LoaderCircle, LogIn, RefreshCw, Send, ShieldCheck, UserPlus } from '@lucide/vue'
import StatusBadge from '../components/StatusBadge.vue'
import { VERIFICATION_METHODS, optionLabel } from '../constants'
import { useAuth } from '../stores/auth'

const { state, isAuthenticated, verificationStatus, login, register, refreshSession, submitVerification } = useAuth()

const mode = ref('login')
const feedback = ref('')
const feedbackType = ref('success')

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  phone: '',
  email: ''
})

const verificationForm = reactive({
  method: 'STUDENT_CARD_IMAGE',
  realName: '',
  studentNumber: '',
  school: '',
  department: '',
  evidenceUrl: ''
})

const verificationTone = computed(() => (verificationStatus.value === 'VERIFIED' ? 'success' : 'warning'))

function setFeedback(message, type = 'success') {
  feedback.value = message
  feedbackType.value = type
}

async function handleLogin() {
  try {
    await login(loginForm)
    setFeedback('登录成功，已同步当前用户和认证状态。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  }
}

async function handleRegister() {
  try {
    await register(registerForm)
    setFeedback('注册成功，请切换到登录并使用新账号进入。')
    mode.value = 'login'
  } catch (error) {
    setFeedback(error.message, 'danger')
  }
}

async function handleRefresh() {
  try {
    await refreshSession()
    setFeedback('账号状态已刷新。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  }
}

async function handleVerificationSubmit() {
  try {
    await submitVerification(verificationForm)
    setFeedback('认证资料已提交，等待审核。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  }
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-header">
      <p class="eyebrow">
        <ShieldCheck
          :size="16"
          aria-hidden="true"
        />
        FE01 身份与校内认证
      </p>
      <h1 class="page-title">
        登录、注册和认证状态管理。
      </h1>
      <p class="page-subtitle">
        对齐后端 `POST /auth/register`、`POST /auth/login`、`GET /users/me`、`GET/POST /verifications`，提交后给出明确成功或错误反馈。
      </p>
    </header>

    <div
      v-if="feedback"
      class="alert"
      :class="feedbackType === 'danger' ? 'alert--danger' : 'alert--success'"
      role="status"
    >
      <BadgeCheck
        v-if="feedbackType !== 'danger'"
        :size="18"
        aria-hidden="true"
      />
      <ShieldCheck
        v-else
        :size="18"
        aria-hidden="true"
      />
      <span>{{ feedback }}</span>
    </div>

    <section class="grid grid--two">
      <div class="panel">
        <div
          class="auth-tabs"
          role="tablist"
          aria-label="认证入口"
        >
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            @click="mode = 'login'"
          >
            <LogIn
              :size="17"
              aria-hidden="true"
            />
            登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >
            <UserPlus
              :size="17"
              aria-hidden="true"
            />
            注册
          </button>
        </div>

        <form
          v-if="mode === 'login'"
          class="stack"
          @submit.prevent="handleLogin"
        >
          <div class="field">
            <label for="login-username">用户名</label>
            <input
              id="login-username"
              v-model.trim="loginForm.username"
              autocomplete="username"
              required
            >
          </div>
          <div class="field">
            <label for="login-password">密码</label>
            <input
              id="login-password"
              v-model="loginForm.password"
              type="password"
              autocomplete="current-password"
              required
            >
          </div>
          <button
            class="button"
            type="submit"
            :disabled="state.loading"
          >
            <LoaderCircle
              v-if="state.loading"
              :size="17"
              aria-hidden="true"
            />
            <LogIn
              v-else
              :size="17"
              aria-hidden="true"
            />
            登录
          </button>
        </form>

        <form
          v-else
          class="stack"
          @submit.prevent="handleRegister"
        >
          <div class="form-row">
            <div class="field">
              <label for="register-username">用户名</label>
              <input
                id="register-username"
                v-model.trim="registerForm.username"
                autocomplete="username"
                required
              >
            </div>
            <div class="field">
              <label for="register-phone">手机号</label>
              <input
                id="register-phone"
                v-model.trim="registerForm.phone"
                inputmode="tel"
              >
            </div>
          </div>
          <div class="form-row">
            <div class="field">
              <label for="register-email">邮箱</label>
              <input
                id="register-email"
                v-model.trim="registerForm.email"
                type="email"
                autocomplete="email"
              >
            </div>
            <div class="field">
              <label for="register-password">密码</label>
              <input
                id="register-password"
                v-model="registerForm.password"
                type="password"
                autocomplete="new-password"
                required
              >
            </div>
          </div>
          <button
            class="button"
            type="submit"
            :disabled="state.loading"
          >
            <LoaderCircle
              v-if="state.loading"
              :size="17"
              aria-hidden="true"
            />
            <UserPlus
              v-else
              :size="17"
              aria-hidden="true"
            />
            创建账号
          </button>
        </form>
      </div>

      <aside class="panel panel--quiet account-panel">
        <div class="panel-title">
          <h2>当前账号</h2>
          <button
            class="icon-button"
            type="button"
            title="刷新账号状态"
            @click="handleRefresh"
          >
            <RefreshCw
              :size="17"
              aria-hidden="true"
            />
          </button>
        </div>
        <dl>
          <div>
            <dt>账号</dt>
            <dd>{{ state.user?.username || '未登录' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ state.user?.role || 'USER' }}</dd>
          </div>
          <div>
            <dt>认证状态</dt>
            <dd>
              <StatusBadge
                :status="verificationStatus"
                :tone="verificationTone"
              />
            </dd>
          </div>
          <div>
            <dt>认证方式</dt>
            <dd>{{ optionLabel(VERIFICATION_METHODS, state.verification?.method) }}</dd>
          </div>
        </dl>
      </aside>
    </section>

    <form
      class="panel stack"
      @submit.prevent="handleVerificationSubmit"
    >
      <div class="panel-title">
        <h2>校内认证</h2>
        <span class="muted">BE02 联调入口</span>
      </div>
      <div class="form-row">
        <div class="field">
          <label for="verify-method">认证方式</label>
          <select
            id="verify-method"
            v-model="verificationForm.method"
            :disabled="!isAuthenticated"
          >
            <option
              v-for="method in VERIFICATION_METHODS"
              :key="method.value"
              :value="method.value"
            >
              {{ method.label }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="verify-name">真实姓名</label>
          <input
            id="verify-name"
            v-model.trim="verificationForm.realName"
            :disabled="!isAuthenticated"
            required
          >
        </div>
      </div>
      <div class="form-row">
        <div class="field">
          <label for="verify-number">学号</label>
          <input
            id="verify-number"
            v-model.trim="verificationForm.studentNumber"
            :disabled="!isAuthenticated"
            required
          >
        </div>
        <div class="field">
          <label for="verify-school">学校</label>
          <input
            id="verify-school"
            v-model.trim="verificationForm.school"
            :disabled="!isAuthenticated"
            required
          >
        </div>
      </div>
      <div class="form-row">
        <div class="field">
          <label for="verify-department">院系</label>
          <input
            id="verify-department"
            v-model.trim="verificationForm.department"
            :disabled="!isAuthenticated"
          >
        </div>
        <div class="field">
          <label for="verify-evidence">材料链接</label>
          <input
            id="verify-evidence"
            v-model.trim="verificationForm.evidenceUrl"
            :disabled="!isAuthenticated"
            placeholder="可填写学生证或校园邮箱证明材料链接"
          >
        </div>
      </div>
      <button
        class="button"
        type="submit"
        :disabled="!isAuthenticated"
      >
        <Send
          :size="17"
          aria-hidden="true"
        />
        提交认证
      </button>
    </form>
  </section>
</template>

<style scoped>
.auth-page {
  display: grid;
  gap: 20px;
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 18px;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #f8fafc;
}

.auth-tabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-muted);
  font-weight: 800;
}

.auth-tabs button.active {
  background: #ffffff;
  color: var(--color-action);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.account-panel dl {
  display: grid;
  gap: 14px;
  margin: 0;
}

.account-panel dl > div {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
}

.account-panel dl > div:first-child {
  padding-top: 0;
  border-top: 0;
}

.account-panel dt {
  color: var(--color-muted);
  font-weight: 700;
}

.account-panel dd {
  margin: 0;
  color: var(--color-primary);
  font-weight: 800;
}

@media (max-width: 640px) {
  .account-panel dl > div {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
