<script setup>
import { computed, reactive, ref } from 'vue'
import {
  ArrowRight,
  BadgeCheck,
  Eye,
  EyeOff,
  HeartHandshake,
  IdCard,
  LoaderCircle,
  LockKeyhole,
  Mail,
  MessageCircle,
  RefreshCw,
  School,
  Send,
  ShieldCheck,
  Star,
  UserRound
} from '@lucide/vue'
import { RouterLink } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { VERIFICATION_METHODS, optionLabel } from '../constants'
import { useAuth } from '../stores/auth'

const { state, isAuthenticated, verificationStatus, login, register, refreshSession, submitVerification } = useAuth()

const mode = ref('login')
const feedback = ref('')
const feedbackType = ref('success')
const showPassword = ref(false)
const rememberMe = ref(true)

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

const formTitle = computed(() => (mode.value === 'login' ? '欢迎回来' : '创建账号'))
const formSubtitle = computed(() => (mode.value === 'login' ? '登录你的账号，继续互助之旅' : '完成基础信息，加入可信校园互助'))
const submitLabel = computed(() => (mode.value === 'login' ? '登录' : '注册'))
const verificationTone = computed(() => (verificationStatus.value === 'VERIFIED' ? 'success' : 'warning'))

function switchMode(nextMode) {
  mode.value = nextMode
  feedback.value = ''
}

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
  <section class="auth-screen">
    <div class="brand-panel">
      <RouterLink
        class="auth-brand"
        to="/"
        aria-label="返回 CampusBridge 首页"
      >
        <span class="auth-logo">
          <HeartHandshake
            :size="34"
            aria-hidden="true"
          />
        </span>
        <span>
          <strong>校园互助</strong>
          <small>HELP EACH OTHER</small>
        </span>
      </RouterLink>

      <div class="brand-copy">
        <p class="brand-kicker">
          FE01 AUTH
        </p>
        <h1>互帮互助 · 连接校园</h1>
        <p>让每一次帮助，都发生在可信任的校园里。</p>
        <span
          class="gold-line"
          aria-hidden="true"
        />
      </div>

      <div
        class="campus-illustration"
        aria-hidden="true"
      >
        <span class="campus-arc" />
        <span class="campus-ground" />
        <span class="campus-building building-left" />
        <span class="campus-building building-mid" />
        <span class="campus-clock" />
      </div>

      <div class="trust-points">
        <article>
          <ShieldCheck
            :size="25"
            aria-hidden="true"
          />
          <strong>安全可信</strong>
          <span>校内认证 严格审核</span>
        </article>
        <article>
          <MessageCircle
            :size="25"
            aria-hidden="true"
          />
          <strong>高效沟通</strong>
          <span>账号先行 状态清晰</span>
        </article>
        <article>
          <Star
            :size="25"
            aria-hidden="true"
          />
          <strong>互助共赢</strong>
          <span>认证通过 再启互助</span>
        </article>
      </div>

      <p class="copyright">
        © 2026 校园互助平台
      </p>
    </div>

    <main class="auth-main">
      <section
        class="auth-card"
        aria-labelledby="auth-title"
      >
        <div
          class="auth-tabs"
          role="tablist"
          aria-label="登录注册切换"
        >
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'login'"
            :class="{ active: mode === 'login' }"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'register'"
            :class="{ active: mode === 'register' }"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <div class="auth-card-body">
          <header class="form-heading">
            <p class="form-eyebrow">
              <School
                :size="16"
                aria-hidden="true"
              />
              CampusBridge
            </p>
            <h2 id="auth-title">
              {{ formTitle }}
            </h2>
            <p>{{ formSubtitle }}</p>
          </header>

          <div
            v-if="feedback"
            class="auth-feedback"
            :class="feedbackType === 'danger' ? 'auth-feedback--danger' : 'auth-feedback--success'"
            role="status"
          >
            <BadgeCheck
              :size="18"
              aria-hidden="true"
            />
            <span>{{ feedback }}</span>
          </div>

          <form
            v-if="mode === 'login'"
            class="auth-form"
            @submit.prevent="handleLogin"
          >
            <div class="auth-field">
              <label for="login-username">手机号 / 邮箱 / 学号</label>
              <div class="input-shell">
                <UserRound
                  :size="20"
                  aria-hidden="true"
                />
                <input
                  id="login-username"
                  v-model.trim="loginForm.username"
                  autocomplete="username"
                  placeholder="请输入手机号 / 邮箱 / 学号"
                  required
                >
              </div>
            </div>

            <div class="auth-field">
              <label for="login-password">密码</label>
              <div class="input-shell">
                <LockKeyhole
                  :size="20"
                  aria-hidden="true"
                />
                <input
                  id="login-password"
                  v-model="loginForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  required
                >
                <button
                  class="ghost-icon"
                  type="button"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <EyeOff
                    v-if="showPassword"
                    :size="20"
                    aria-hidden="true"
                  />
                  <Eye
                    v-else
                    :size="20"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </div>

            <div class="form-options">
              <label class="remember-row">
                <input
                  v-model="rememberMe"
                  type="checkbox"
                >
                <span>记住我</span>
              </label>
              <button
                class="text-button"
                type="button"
              >
                忘记密码?
              </button>
            </div>

            <button
              class="submit-button"
              type="submit"
              :disabled="state.loading"
            >
              <LoaderCircle
                v-if="state.loading"
                :size="20"
                aria-hidden="true"
              />
              <span>{{ submitLabel }}</span>
              <ArrowRight
                :size="21"
                aria-hidden="true"
              />
            </button>
          </form>

          <form
            v-else
            class="auth-form"
            @submit.prevent="handleRegister"
          >
            <div class="auth-field">
              <label for="register-username">学号 / 用户名</label>
              <div class="input-shell">
                <IdCard
                  :size="20"
                  aria-hidden="true"
                />
                <input
                  id="register-username"
                  v-model.trim="registerForm.username"
                  autocomplete="username"
                  placeholder="请输入学号或用户名"
                  required
                >
              </div>
            </div>

            <div class="auth-field-grid">
              <div class="auth-field">
                <label for="register-phone">手机号</label>
                <div class="input-shell">
                  <UserRound
                    :size="20"
                    aria-hidden="true"
                  />
                  <input
                    id="register-phone"
                    v-model.trim="registerForm.phone"
                    inputmode="tel"
                    placeholder="请输入手机号"
                  >
                </div>
              </div>
              <div class="auth-field">
                <label for="register-email">邮箱</label>
                <div class="input-shell">
                  <Mail
                    :size="20"
                    aria-hidden="true"
                  />
                  <input
                    id="register-email"
                    v-model.trim="registerForm.email"
                    type="email"
                    autocomplete="email"
                    placeholder="请输入邮箱"
                  >
                </div>
              </div>
            </div>

            <div class="auth-field">
              <label for="register-password">密码</label>
              <div class="input-shell">
                <LockKeyhole
                  :size="20"
                  aria-hidden="true"
                />
                <input
                  id="register-password"
                  v-model="registerForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  placeholder="设置登录密码"
                  required
                >
              </div>
            </div>

            <button
              class="submit-button"
              type="submit"
              :disabled="state.loading"
            >
              <LoaderCircle
                v-if="state.loading"
                :size="20"
                aria-hidden="true"
              />
              <span>{{ submitLabel }}</span>
              <ArrowRight
                :size="21"
                aria-hidden="true"
              />
            </button>
          </form>

          <div class="quick-switch">
            <span>{{ mode === 'login' ? '还没有账号？' : '已经有账号？' }}</span>
            <button
              type="button"
              @click="switchMode(mode === 'login' ? 'register' : 'login')"
            >
              {{ mode === 'login' ? '立即注册' : '返回登录' }}
            </button>
          </div>

          <section class="account-strip">
            <div>
              <span>当前账号</span>
              <strong>{{ state.user?.username || '未登录' }}</strong>
            </div>
            <StatusBadge
              :status="verificationStatus"
              :tone="verificationTone"
            />
            <button
              class="refresh-button"
              type="button"
              title="刷新账号状态"
              @click="handleRefresh"
            >
              <RefreshCw
                :size="17"
                aria-hidden="true"
              />
            </button>
          </section>
        </div>
      </section>

      <form
        class="verification-card"
        @submit.prevent="handleVerificationSubmit"
      >
        <div class="verification-heading">
          <div>
            <p class="form-eyebrow">
              <ShieldCheck
                :size="16"
                aria-hidden="true"
              />
              校内认证
            </p>
            <h2>认证后开启完整互助权限</h2>
          </div>
          <StatusBadge
            :status="verificationStatus"
            :tone="verificationTone"
          />
        </div>

        <div class="verification-grid">
          <div class="auth-field">
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
          <div class="auth-field">
            <label for="verify-name">真实姓名</label>
            <input
              id="verify-name"
              v-model.trim="verificationForm.realName"
              :disabled="!isAuthenticated"
              required
            >
          </div>
          <div class="auth-field">
            <label for="verify-number">学号</label>
            <input
              id="verify-number"
              v-model.trim="verificationForm.studentNumber"
              :disabled="!isAuthenticated"
              required
            >
          </div>
          <div class="auth-field">
            <label for="verify-school">学校</label>
            <input
              id="verify-school"
              v-model.trim="verificationForm.school"
              :disabled="!isAuthenticated"
              required
            >
          </div>
          <div class="auth-field">
            <label for="verify-department">院系</label>
            <input
              id="verify-department"
              v-model.trim="verificationForm.department"
              :disabled="!isAuthenticated"
            >
          </div>
          <div class="auth-field">
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
          class="verify-submit"
          type="submit"
          :disabled="!isAuthenticated"
        >
          <Send
            :size="17"
            aria-hidden="true"
          />
          提交认证
        </button>

        <p class="verification-note">
          {{ isAuthenticated ? `认证方式：${optionLabel(VERIFICATION_METHODS, state.verification?.method)}` : '请先登录后提交校内认证。' }}
        </p>
      </form>
    </main>
  </section>
</template>

<style scoped>
.auth-screen {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 45vw) minmax(0, 1fr);
  background:
    radial-gradient(circle at 18% 14%, rgba(193, 164, 111, 0.18), transparent 30%),
    linear-gradient(115deg, #fbf8f2 0%, #ffffff 44%, #eef0f2 44%, #f8f9fb 100%);
  color: #171717;
  font-family: KaiTi, STKaiti, "楷体", "华文楷体", serif;
}

.brand-panel {
  position: relative;
  display: grid;
  align-content: space-between;
  min-height: 100vh;
  padding: 42px clamp(28px, 4vw, 66px) 34px;
  overflow: hidden;
}

.auth-brand {
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 14px;
  width: fit-content;
}

.auth-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  color: #ad8a4d;
}

.auth-brand strong,
.auth-brand small {
  display: block;
}

.auth-brand strong {
  color: #171717;
  font-size: 1.35rem;
  letter-spacing: 0;
}

.auth-brand small {
  margin-top: 4px;
  color: #77716a;
  font-size: 0.78rem;
  letter-spacing: 0.08em;
}

.brand-copy {
  position: relative;
  z-index: 2;
  display: grid;
  gap: 20px;
  max-width: 560px;
}

.brand-kicker {
  margin: 0;
  color: #ad8a4d;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.brand-copy h1 {
  margin: 0;
  color: #171717;
  font-size: clamp(2.4rem, 3.35vw, 3.75rem);
  font-weight: 500;
  line-height: 1.12;
  letter-spacing: 0;
  white-space: nowrap;
}

.brand-copy p:not(.brand-kicker) {
  margin: 0;
  color: #78716c;
  font-size: 1.1rem;
  line-height: 1.7;
}

.gold-line {
  width: 48px;
  height: 2px;
  background: #b99a60;
}

.campus-illustration {
  position: absolute;
  inset: auto 0 110px 0;
  height: 380px;
  opacity: 0.7;
}

.campus-arc {
  position: absolute;
  left: -18%;
  bottom: 10px;
  width: 125%;
  height: 260px;
  border-top: 2px solid rgba(185, 154, 96, 0.45);
  border-radius: 50% 50% 0 0;
  transform: rotate(-3deg);
}

.campus-ground {
  position: absolute;
  left: -8%;
  right: 6%;
  bottom: 0;
  height: 140px;
  border-top: 1px solid rgba(185, 154, 96, 0.22);
  border-radius: 55% 45% 0 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.52), rgba(255, 255, 255, 0.92));
}

.campus-building {
  position: absolute;
  bottom: 70px;
  border: 1px solid rgba(185, 154, 96, 0.22);
  background: rgba(255, 255, 255, 0.45);
  box-shadow: inset 0 0 0 999px rgba(255, 255, 255, 0.18);
}

.building-left {
  left: 18%;
  width: 260px;
  height: 86px;
  transform: skewY(-6deg);
}

.building-mid {
  right: 13%;
  width: 86px;
  height: 230px;
  border-radius: 38px 38px 0 0;
}

.campus-clock {
  position: absolute;
  right: calc(13% + 22px);
  bottom: 240px;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(185, 154, 96, 0.28);
  border-radius: 50%;
  background:
    linear-gradient(90deg, transparent 48%, rgba(185, 154, 96, 0.35) 49%, rgba(185, 154, 96, 0.35) 51%, transparent 52%),
    linear-gradient(0deg, transparent 48%, rgba(185, 154, 96, 0.35) 49%, rgba(185, 154, 96, 0.35) 51%, transparent 52%),
    rgba(255, 255, 255, 0.58);
}

.trust-points {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  max-width: 640px;
  color: #ad8a4d;
}

.trust-points article {
  display: grid;
  gap: 8px;
}

.trust-points strong {
  color: #171717;
  font-size: 1rem;
}

.trust-points span {
  color: #8a8177;
  font-size: 0.86rem;
  line-height: 1.5;
}

.copyright {
  position: relative;
  z-index: 2;
  margin: 24px 0 0;
  color: #918b84;
  font-size: 0.82rem;
}

.auth-main {
  min-height: 100vh;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 22px;
  padding: 42px 28px;
}

.auth-card,
.verification-card {
  width: min(100%, 670px);
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 28px 80px rgba(24, 24, 27, 0.14);
  backdrop-filter: blur(18px);
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-height: 96px;
  border-bottom: 1px solid #ebe7df;
}

.auth-tabs button {
  position: relative;
  border: 0;
  background: transparent;
  color: #6f6b66;
  font-size: 1.25rem;
  font-weight: 700;
}

.auth-tabs button.active {
  color: #171717;
}

.auth-tabs button.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 66px;
  height: 2px;
  border-radius: 999px;
  background: #b99a60;
  transform: translateX(-50%);
}

.auth-card-body {
  width: min(100%, 470px);
  margin: 0 auto;
  padding: 54px 0 44px;
}

.form-heading {
  display: grid;
  gap: 10px;
  margin-bottom: 34px;
}

.form-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #ad8a4d;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.form-heading h2,
.verification-heading h2 {
  margin: 0;
  color: #171717;
  font-size: 2rem;
  font-weight: 600;
  letter-spacing: 0;
}

.form-heading p:not(.form-eyebrow) {
  margin: 0;
  color: #7a746d;
  line-height: 1.6;
}

.auth-feedback {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 22px;
  padding: 12px 14px;
  border-radius: 14px;
  line-height: 1.5;
}

.auth-feedback--success {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.auth-feedback--danger {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.auth-form {
  display: grid;
  gap: 22px;
}

.auth-field,
.auth-field-grid {
  display: grid;
  gap: 9px;
}

.auth-field-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.auth-field label {
  color: #27221d;
  font-size: 0.95rem;
  font-weight: 700;
}

.input-shell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  padding: 0 16px;
  border: 1px solid #dedbd5;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.85);
  color: #aaa39b;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease;
}

.input-shell:focus-within {
  border-color: #b99a60;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(185, 154, 96, 0.14);
}

.input-shell input,
.auth-field input,
.auth-field select {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #171717;
}

.auth-field > input,
.auth-field > select {
  min-height: 48px;
  padding: 0 12px;
  border: 1px solid #dedbd5;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
}

.input-shell input::placeholder {
  color: #b8b2ac;
}

.ghost-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #aaa39b;
}

.ghost-icon:hover {
  background: #f4f0e8;
  color: #6f5b33;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: -4px;
}

.remember-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4b4640;
  font-weight: 600;
}

.text-button,
.quick-switch button {
  border: 0;
  background: transparent;
  color: #ad8a4d;
  font-weight: 800;
}

.submit-button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 58px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #111111, #252525);
  color: #ffffff;
  font-size: 1.1rem;
  font-weight: 800;
  white-space: nowrap;
  box-shadow: 0 16px 30px rgba(17, 17, 17, 0.18);
  transition:
    box-shadow 180ms ease,
    background-color 180ms ease;
}

.submit-button span {
  white-space: nowrap;
}

.submit-button svg:last-child {
  position: absolute;
  right: 26px;
}

.submit-button:hover:not(:disabled) {
  box-shadow: 0 18px 34px rgba(17, 17, 17, 0.25);
}

.quick-switch {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 28px;
  color: #4b4640;
  font-size: 1rem;
}

.account-strip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding: 14px 16px;
  border: 1px solid #ebe7df;
  border-radius: 16px;
  background: #fbfaf7;
}

.account-strip span,
.verification-note {
  color: #7a746d;
  font-size: 0.84rem;
}

.account-strip strong {
  display: block;
  margin-top: 4px;
  color: #171717;
}

.refresh-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid #dedbd5;
  border-radius: 50%;
  background: #ffffff;
  color: #6f5b33;
}

.verification-card {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.verification-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.verification-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.verify-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: fit-content;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid #b99a60;
  border-radius: 999px;
  background: #fffaf0;
  color: #6f5b33;
  font-weight: 800;
}

.verification-note {
  margin: 0;
}

@media (max-width: 1100px) {
  .auth-screen {
    grid-template-columns: 1fr;
  }

  .brand-copy h1 {
    white-space: normal;
  }

  .brand-panel {
    min-height: auto;
    gap: 44px;
  }

  .campus-illustration {
    height: 260px;
    bottom: 82px;
  }
}

@media (max-width: 720px) {
  .auth-screen {
    background: #fbf8f2;
  }

  .brand-panel {
    padding: 26px 18px 18px;
  }

  .brand-copy h1 {
    font-size: 2.2rem;
  }

  .campus-illustration,
  .copyright {
    display: none;
  }

  .trust-points,
  .auth-field-grid,
  .verification-grid {
    grid-template-columns: 1fr;
  }

  .auth-main {
    min-height: auto;
    padding: 18px;
  }

  .auth-card,
  .verification-card {
    border-radius: 22px;
  }

  .auth-card-body {
    width: 100%;
    padding: 34px 20px;
  }

  .auth-tabs {
    min-height: 72px;
  }

  .form-options,
  .quick-switch,
  .verification-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
