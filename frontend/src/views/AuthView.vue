<script setup>
import { computed, reactive, ref } from 'vue'
import {
  ArrowRight,
  BadgeCheck,
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  LogIn,
  Mail,
  School,
  UserPlus,
  UserRound
} from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'

const { state, login, register } = useAuth()
const route = useRoute()
const router = useRouter()
const { showToast } = useToast()

const mode = ref('login')
const showPassword = ref(false)
const pendingAction = ref('')
const REMEMBER_LOGIN_KEY = 'campusbridge.rememberedLogin'

function readRememberedLogin() {
  try {
    return JSON.parse(localStorage.getItem(REMEMBER_LOGIN_KEY) || '{}')
  } catch {
    return {}
  }
}

const rememberedLogin = readRememberedLogin()
const rememberMe = ref(Boolean(rememberedLogin.username || rememberedLogin.password))

const loginForm = reactive({
  username: rememberedLogin.username || '',
  password: rememberedLogin.password || ''
})

const registerForm = reactive({
  username: '',
  password: '',
  phone: '',
  email: '',
  code: ''
})
const registerCodeSent = ref(false)
const registerCodeSending = ref(false)

const formTitle = computed(() => (mode.value === 'login' ? '欢迎回来' : '创建账号'))
const formSubtitle = computed(
  () => (mode.value === 'login' ? '看看有什么需要吧' : '加入 CampusBridge，开启校园互助之旅')
)
const submitLabel = computed(() => (mode.value === 'login' ? '登录' : '注册'))
const isSubmitting = computed(() => state.loading && Boolean(pendingAction.value))
const submitText = computed(() =>
  isSubmitting.value
    ? pendingAction.value === 'register'
      ? '正在注册'
      : '正在登录'
    : submitLabel.value
)

function switchMode(nextMode) {
  if (state.loading) return
  mode.value = nextMode
}

function syncRememberedLogin() {
  if (rememberMe.value) {
    localStorage.setItem(
      REMEMBER_LOGIN_KEY,
      JSON.stringify({ username: loginForm.username, password: loginForm.password })
    )
    return
  }
  localStorage.removeItem(REMEMBER_LOGIN_KEY)
}

async function enterAsGuest() {
  if (state.loading) return
  await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
}

async function handleLogin() {
  pendingAction.value = 'login'
  try {
    await login(loginForm)
    syncRememberedLogin()
    showToast('登录成功，正在进入认证工作台。', { type: 'success' })
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/home')
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    pendingAction.value = ''
  }
}

async function sendRegisterCode() {
  const email = registerForm.email.trim()
  if (!email) {
    showToast('请先填写邮箱', { type: 'warning' })
    return
  }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    showToast('请填写有效的邮箱地址', { type: 'warning' })
    return
  }
  registerCodeSending.value = true
  try {
    await api.sendRegistrationCode({ email })
    registerCodeSent.value = true
    showToast('验证码已发送', { type: 'success' })
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    registerCodeSending.value = false
  }
}

async function handleRegister() {
  pendingAction.value = 'register'

  if (!registerForm.username.trim()) {
    showToast('请输入用户名', { type: 'warning' })
    return
  }
  if (!registerForm.phone.trim()) {
    showToast('请输入手机号', { type: 'warning' })
    return
  }
  const email = registerForm.email.trim()
  if (!email) {
    showToast('请输入邮箱', { type: 'warning' })
    return
  }
  if (!registerForm.code.trim()) {
    showToast('请先发送并填写邮箱验证码', { type: 'warning' })
    return
  }
  if (!registerForm.password) {
    showToast('请输入密码', { type: 'warning' })
    return
  }

  try {
    await register({ ...registerForm, email, code: registerForm.code.trim() })
    showToast('注册成功，请使用用户名和密码登录。', { type: 'success' })
    mode.value = 'login'
    registerCodeSent.value = false
    registerForm.code = ''
  } catch (error) {
    showToast(error.message, { type: 'danger' })
  } finally {
    pendingAction.value = ''
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="page-title-card">
      <div class="title-icon">
        <School
          :size="34"
          aria-hidden="true"
        />
      </div>
      <div class="title-copy">
        <p>CampusBridge</p>
        <h1>{{ formTitle }}</h1>
        <span>{{ formSubtitle }}</span>
      </div>
    </section>

    <section class="auth-layout">
      <aside class="auth-side-card">
        <div class="brand-spot">
          <span class="brand-mark">
            <School
              :size="42"
              aria-hidden="true"
            />
          </span>
          <h2>CampusBridge</h2>
          <p>校园互助服务平台</p>
        </div>
        <ul class="feature-list">
          <li>
            <span class="feature-icon">
              <BadgeCheck
                :size="18"
                aria-hidden="true"
              />
            </span>
            <span>校内实名认证，协作更可信</span>
          </li>
          <li>
            <span class="feature-icon">
              <BadgeCheck
                :size="18"
                aria-hidden="true"
              />
            </span>
            <span>发布校园任务，同学互帮互助</span>
          </li>
          <li>
            <span class="feature-icon">
              <BadgeCheck
                :size="18"
                aria-hidden="true"
              />
            </span>
            <span>站内沟通留痕，平台保障安全</span>
          </li>
        </ul>
      </aside>

      <article
        class="panel-card auth-form-card"
        :aria-busy="isSubmitting"
      >
        <div class="tab-row">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            :disabled="state.loading"
            @click="switchMode('login')"
          >
            <LogIn
              :size="18"
              aria-hidden="true"
            />
            登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            :disabled="state.loading"
            @click="switchMode('register')"
          >
            <UserPlus
              :size="18"
              aria-hidden="true"
            />
            注册
          </button>
        </div>

        <form
          v-if="mode === 'login'"
          class="auth-form"
          @submit.prevent="handleLogin"
        >
          <label class="publish-field">
            <span>用户名</span>
            <div class="input-shell">
              <UserRound
                :size="18"
                aria-hidden="true"
              />
              <input
                v-model.trim="loginForm.username"
                autocomplete="username"
                placeholder="请输入用户名"
                required
                :disabled="state.loading"
              >
            </div>
          </label>

          <label class="publish-field">
            <span>密码</span>
            <div class="input-shell">
              <LockKeyhole
                :size="18"
                aria-hidden="true"
              />
              <input
                v-model="loginForm.password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                required
                :disabled="state.loading"
              >
              <button
                class="toggle-password"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :disabled="state.loading"
                @click="showPassword = !showPassword"
              >
                <EyeOff
                  v-if="showPassword"
                  :size="18"
                  aria-hidden="true"
                />
                <Eye
                  v-else
                  :size="18"
                  aria-hidden="true"
                />
              </button>
            </div>
          </label>

          <label class="remember-row">
            <input
              v-model="rememberMe"
              type="checkbox"
              :disabled="state.loading"
            >
            <span>记住我</span>
          </label>

          <button
            class="primary-action"
            :class="{ 'primary-action--loading': isSubmitting }"
            type="submit"
            :disabled="state.loading"
          >
            <LoaderCircle
              v-if="isSubmitting"
              class="spinning"
              :size="19"
              aria-hidden="true"
            />
            <span>{{ submitText }}</span>
            <ArrowRight
              v-if="!isSubmitting"
              :size="20"
              aria-hidden="true"
            />
          </button>
          <button
            class="guest-entry"
            type="button"
            :disabled="state.loading"
            @click="enterAsGuest"
          >
            先以游客身份浏览
          </button>
        </form>

        <form
          v-else
          class="auth-form"
          @submit.prevent="handleRegister"
        >
          <label class="publish-field">
            <span>用户名</span>
            <div class="input-shell">
              <UserRound
                :size="18"
                aria-hidden="true"
              />
              <input
                v-model.trim="registerForm.username"
                autocomplete="username"
                placeholder="请设置用户名"
                required
                :disabled="state.loading"
              >
            </div>
          </label>

          <div class="auth-field-row">
            <label class="publish-field">
              <span>手机号</span>
              <div class="input-shell">
                <UserRound
                  :size="18"
                  aria-hidden="true"
                />
                <input
                  v-model.trim="registerForm.phone"
                  inputmode="tel"
                  placeholder="请输入手机号"
                  :disabled="state.loading"
                >
              </div>
            </label>
            <label class="publish-field">
              <span>邮箱</span>
              <div class="input-shell">
                <Mail
                  :size="18"
                  aria-hidden="true"
                />
                <input
                  v-model.trim="registerForm.email"
                  type="email"
                  autocomplete="email"
                  placeholder="请输入邮箱"
                  :disabled="state.loading"
                >
              </div>
            </label>
          </div>

          <label class="publish-field">
            <span>密码</span>
            <div class="input-shell">
              <LockKeyhole
                :size="18"
                aria-hidden="true"
              />
              <input
                v-model="registerForm.password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="设置登录密码"
                required
                :disabled="state.loading"
              >
            </div>
          </label>

          <div
            v-if="registerForm.email.trim()"
            class="auth-field-row code-row"
          >
            <button
              class="primary-action code-send-btn"
              type="button"
              :disabled="registerCodeSending || state.loading || registerCodeSent"
              @click="sendRegisterCode"
            >
              <LoaderCircle
                v-if="registerCodeSending"
                class="spinning"
                :size="17"
              />
              <Mail
                v-else
                :size="17"
              />
              发送邮箱验证码
            </button>
            <label class="publish-field">
              <span>验证码</span>
              <div class="input-shell">
                <input
                  v-model.trim="registerForm.code"
                  inputmode="numeric"
                  maxlength="6"
                  placeholder="6位验证码"
                  required
                  :disabled="state.loading"
                >
              </div>
            </label>
          </div>

          <button
            class="primary-action"
            :class="{ 'primary-action--loading': isSubmitting }"
            type="submit"
            :disabled="state.loading"
          >
            <LoaderCircle
              v-if="isSubmitting"
              class="spinning"
              :size="19"
              aria-hidden="true"
            />
            <span>{{ submitText }}</span>
            <ArrowRight
              v-if="!isSubmitting"
              :size="20"
              aria-hidden="true"
            />
          </button>
        </form>

        <div class="switch-hint">
          <span>{{ mode === 'login' ? '还没有账号？' : '已经有账号？' }}</span>
          <button
            type="button"
            :disabled="state.loading"
            @click="switchMode(mode === 'login' ? 'register' : 'login')"
          >
            {{ mode === 'login' ? '立即注册' : '返回登录' }}
          </button>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  gap: 20px;
  width: min(100%, 1140px);
  margin: 0 auto;
  padding: 40px 24px 56px;
  color: var(--color-text);
  font-family: "PingFang SC", "Microsoft YaHei", "Hiragino Sans GB", "Noto Sans SC", sans-serif;
}

.page-title-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 24px;
  min-height: 112px;
  padding: 24px 32px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.title-icon {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand-text);
}

.title-copy {
  display: grid;
  gap: 6px;
}

.title-copy p {
  margin: 0;
  color: var(--color-brand-text);
  font-size: 0.92rem;
  font-weight: 900;
}

.title-copy h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.8rem;
  font-weight: 900;
  line-height: 1.16;
}

.title-copy span {
  color: var(--color-text-muted);
  line-height: 1.5;
}

.auth-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.42fr) minmax(400px, 1fr);
  gap: 20px;
  align-items: start;
}

.panel-card {
  display: grid;
  align-content: start;
  gap: 20px;
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.tab-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  min-height: 52px;
  border-bottom: 1px solid var(--color-border);
}

.tab-row button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 1.06rem;
  font-weight: 800;
  padding-bottom: 12px;
  transition: color 180ms ease;
}

.tab-row button.active {
  color: var(--color-brand-text);
}

.tab-row button.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 56px;
  height: 2px;
  border-radius: 999px;
  background: var(--color-brand-text);
  transform: translateX(-50%);
}

.tab-row button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.auth-form {
  display: grid;
  gap: 18px;
}

.auth-field-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.publish-field {
  display: grid;
  gap: 6px;
}

.publish-field span {
  color: var(--color-text);
  font-size: 0.9rem;
  font-weight: 800;
}

.input-shell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.input-shell:focus-within {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.input-shell input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 0.92rem;
}

.input-shell input::placeholder {
  color: var(--color-text-muted);
}

.input-shell input:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.toggle-password {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
}

.toggle-password:hover:not(:disabled) {
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
}

.toggle-password:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.remember-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 0.9rem;
  font-weight: 700;
}

.remember-row input {
  width: 16px;
  height: 16px;
  accent-color: var(--color-brand-text);
}

.primary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 50px;
  padding: 0 18px;
  border: 1px solid var(--color-brand-hover);
  border-radius: 8px;
  background: var(--color-brand-text);
  color: var(--color-surface);
  font-size: 1.04rem;
  font-weight: 900;
  box-shadow: var(--shadow-button);
  transition: background-color 180ms ease, box-shadow 180ms ease;
}

.primary-action:hover:not(:disabled) {
  background: var(--color-brand-hover);
  box-shadow: var(--shadow-button-hover);
}

.primary-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.code-send-btn {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
  color: var(--color-brand-hover);
  box-shadow: var(--shadow-card);
  font-size: 0.92rem;
  min-height: 46px;
  flex-shrink: 0;
}

.code-row {
  grid-template-columns: auto 1fr !important;
}

.code-send-btn:hover:not(:disabled) {
  background: var(--color-brand-soft-hover);
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-card);
}

.code-send-btn:disabled {
  opacity: 0.5;
}

.primary-action--loading {
  background: var(--color-brand-hover);
}

.guest-entry {
  min-height: 44px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.95rem;
  font-weight: 800;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease;
}

.guest-entry:hover:not(:disabled) {
  border-color: var(--color-brand-border-hover);
  background: var(--color-brand-soft);
  color: var(--color-brand-hover);
}

.guest-entry:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.switch-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 0.94rem;
}

.switch-hint button {
  border: 0;
  background: transparent;
  color: var(--color-brand-text);
  font-weight: 800;
}

.switch-hint button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.auth-side-card {
  display: grid;
  align-content: start;
  gap: 24px;
  padding: 32px 24px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: linear-gradient(160deg, var(--color-brand-soft), var(--color-brand-soft));
  box-shadow: var(--shadow-card);
}

.brand-spot {
  display: grid;
  gap: 10px;
  text-align: center;
}

.brand-mark {
  display: inline-grid;
  place-items: center;
  justify-self: center;
  width: 72px;
  height: 72px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-brand-soft), var(--color-brand-soft));
  color: var(--color-brand-text);
}

.brand-spot h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.3rem;
  font-weight: 900;
}

.brand-spot p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.92rem;
}

.feature-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 700;
  line-height: 1.4;
}

.feature-icon {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-brand-text);
  box-shadow: var(--shadow-card);
}

.spinning {
  animation: auth-spin 0.9s linear infinite;
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 24px 16px 36px;
  }

  .page-title-card {
    padding: 20px;
  }

  .title-icon {
    width: 56px;
    height: 56px;
  }

  .auth-field-row {
    grid-template-columns: 1fr;
  }
}
</style>
