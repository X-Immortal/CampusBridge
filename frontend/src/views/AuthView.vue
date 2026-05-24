<script setup>
import { computed, reactive, ref } from 'vue'
import {
  ArrowRight,
  BadgeCheck,
  Eye,
  EyeOff,
  IdCard,
  LoaderCircle,
  LockKeyhole,
  Mail,
  School,
  UserRound
} from '@lucide/vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'

const { state, login, register } = useAuth()
const router = useRouter()

const mode = ref('login')
const feedback = ref('')
const feedbackType = ref('success')
const showPassword = ref(false)
const rememberMe = ref(true)
const pendingAction = ref('')

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

const formTitle = computed(() => (mode.value === 'login' ? '欢迎回来' : '创建账号'))
const formSubtitle = computed(() => (mode.value === 'login' ? '登录后进入工作台完成校内认证' : '先创建账号，再进入可信互助空间'))
const submitLabel = computed(() => (mode.value === 'login' ? '登录' : '注册'))
const isSubmitting = computed(() => state.loading && Boolean(pendingAction.value))
const submitText = computed(() => (isSubmitting.value ? (pendingAction.value === 'register' ? '正在注册' : '正在登录') : submitLabel.value))

function switchMode(nextMode) {
  if (state.loading) return
  mode.value = nextMode
  feedback.value = ''
}

function setFeedback(message, type = 'success') {
  feedback.value = message
  feedbackType.value = type
}

async function handleLogin() {
  pendingAction.value = 'login'
  feedback.value = ''
  try {
    await login(loginForm)
    setFeedback('登录成功，正在进入认证工作台。')
    await router.push('/')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    pendingAction.value = ''
  }
}

async function handleRegister() {
  pendingAction.value = 'register'
  feedback.value = ''
  try {
    await register(registerForm)
    setFeedback('注册成功，请使用新账号登录。')
    mode.value = 'login'
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    pendingAction.value = ''
  }
}
</script>

<template>
  <section class="auth-screen">
    <aside
      class="visual-panel"
      aria-label="南京大学校园互助视觉"
    >
      <div
        class="campus-photo"
        aria-hidden="true"
      />
    </aside>

    <main class="auth-main">
      <section
        class="auth-card"
        aria-labelledby="auth-title"
        :aria-busy="isSubmitting"
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
            :disabled="state.loading"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'register'"
            :class="{ active: mode === 'register' }"
            :disabled="state.loading"
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
                  :disabled="state.loading"
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
                  :disabled="state.loading"
                >
                <button
                  class="ghost-icon"
                  type="button"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  :disabled="state.loading"
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
                  :disabled="state.loading"
                >
                <span>记住我</span>
              </label>
              <button
                class="text-button"
                type="button"
                :disabled="state.loading"
              >
                忘记密码?
              </button>
            </div>

            <button
              class="submit-button"
              :class="{ 'submit-button--loading': isSubmitting }"
              type="submit"
              :disabled="state.loading"
            >
              <LoaderCircle
                v-if="isSubmitting"
                class="spin-icon"
                :size="20"
                aria-hidden="true"
              />
              <span>{{ submitText }}</span>
              <ArrowRight
                v-if="!isSubmitting"
                :size="21"
                aria-hidden="true"
              />
            </button>
          </form>

          <form
            v-else
            class="auth-form register-form"
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
                  :disabled="state.loading"
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
                    :disabled="state.loading"
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
                    :disabled="state.loading"
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
                  :disabled="state.loading"
                >
              </div>
            </div>

            <button
              class="submit-button"
              :class="{ 'submit-button--loading': isSubmitting }"
              type="submit"
              :disabled="state.loading"
            >
              <LoaderCircle
                v-if="isSubmitting"
                class="spin-icon"
                :size="20"
                aria-hidden="true"
              />
              <span>{{ submitText }}</span>
              <ArrowRight
                v-if="!isSubmitting"
                :size="21"
                aria-hidden="true"
              />
            </button>
          </form>

          <div class="quick-switch">
            <span>{{ mode === 'login' ? '还没有账号？' : '已经有账号？' }}</span>
            <button
              type="button"
              :disabled="state.loading"
              @click="switchMode(mode === 'login' ? 'register' : 'login')"
            >
              {{ mode === 'login' ? '立即注册' : '返回登录' }}
            </button>
          </div>
        </div>
      </section>
    </main>
  </section>
</template>

<style scoped>
.auth-screen {
  height: 100vh;
  display: grid;
  grid-template-columns: minmax(560px, 42vw) minmax(480px, 1fr);
  overflow: hidden;
  background: #f5f0f5;
  color: #241a28;
  font-family: KaiTi, STKaiti, "楷体", "华文楷体", serif;
}

.visual-panel {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  overflow: hidden;
}

.campus-photo {
  position: absolute;
  inset: 0;
  background: url("/auth-left.png") center / cover no-repeat;
}

.auth-main {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px;
  background:
    radial-gradient(circle at 22% 20%, rgba(166, 105, 160, 0.16), transparent 28%),
    linear-gradient(135deg, #f9f7f5 0%, #f4eef4 100%);
}

.auth-card {
  width: min(100%, 600px);
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 28px 86px rgba(55, 42, 67, 0.16);
  backdrop-filter: blur(18px);
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-height: 78px;
  border-bottom: 1px solid #ebe3e6;
}

.auth-tabs button {
  position: relative;
  border: 0;
  background: transparent;
  color: #736974;
  font-size: 1.2rem;
  font-weight: 800;
}

.auth-tabs button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.auth-tabs button.active {
  color: #20151f;
}

.auth-tabs button.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 70px;
  height: 2px;
  border-radius: 999px;
  background: #8a3f78;
  transform: translateX(-50%);
}

.auth-card-body {
  width: min(100%, 430px);
  margin: 0 auto;
  padding: 34px 0 36px;
}

.form-heading {
  display: grid;
  gap: 9px;
  margin-bottom: 24px;
}

.form-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #8a3f78;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.form-heading h2 {
  margin: 0;
  color: #20151f;
  font-size: 2rem;
  font-weight: 600;
}

.form-heading p {
  margin: 0;
  color: #786f77;
  line-height: 1.6;
}

.auth-feedback {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 18px;
  padding: 11px 13px;
  border-radius: 12px;
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
  gap: 18px;
}

.register-form {
  gap: 15px;
}

.auth-field,
.auth-field-grid {
  display: grid;
  gap: 8px;
}

.auth-field-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.auth-field label {
  color: #2c202a;
  font-size: 0.95rem;
  font-weight: 800;
}

.input-shell {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 54px;
  padding: 0 15px;
  border: 1px solid #e0d6dc;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
  color: #a79ba3;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease;
}

.input-shell:has(input:disabled) {
  background: rgba(248, 244, 247, 0.86);
  color: #b8acb4;
}

.input-shell:focus-within {
  border-color: #9d5c8e;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(138, 63, 120, 0.12);
}

.input-shell input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #20151f;
}

.input-shell input::placeholder {
  color: #b9aeb5;
}

.input-shell input:disabled {
  cursor: wait;
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
  color: #9b9098;
}

.ghost-icon:hover {
  background: #f5edf3;
  color: #8a3f78;
}

.ghost-icon:disabled,
.text-button:disabled,
.quick-switch button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: -2px;
}

.remember-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4d414b;
  font-weight: 700;
}

.text-button,
.quick-switch button {
  border: 0;
  background: transparent;
  color: #8a3f78;
  font-weight: 800;
}

.submit-button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 56px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #171217, #2d2730);
  color: #ffffff;
  font-size: 1.1rem;
  font-weight: 800;
  white-space: nowrap;
  box-shadow: 0 16px 30px rgba(36, 24, 43, 0.2);
  transition:
    box-shadow 180ms ease,
    transform 180ms ease;
}

.submit-button--loading {
  cursor: wait;
  background: linear-gradient(135deg, #2a1a2b, #5c3254);
  box-shadow: 0 18px 40px rgba(138, 63, 120, 0.26);
}

.submit-button svg:last-child {
  position: absolute;
  right: 26px;
}

.submit-button:hover:not(:disabled) {
  box-shadow: 0 18px 36px rgba(36, 24, 43, 0.3);
  transform: translateY(-1px);
}

.spin-icon {
  animation: auth-spin 0.9s linear infinite;
}

.quick-switch {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 22px;
  color: #4d414b;
  font-size: 1rem;
}

@media (max-width: 1050px) {
  .auth-screen {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .visual-panel {
    min-height: 46vh;
  }

  .auth-main {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .visual-panel {
    min-height: 38vh;
  }

  .auth-main {
    padding: 18px;
  }

  .auth-card {
    border-radius: 22px;
  }

  .auth-card-body {
    width: 100%;
    padding: 28px 18px 30px;
  }

  .auth-field-grid {
    grid-template-columns: 1fr;
  }

  .form-options,
  .quick-switch {
    align-items: flex-start;
    flex-direction: column;
  }
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .submit-button {
    transition-duration: 0.01ms;
  }

  .spin-icon {
    animation-duration: 1ms;
    animation-iteration-count: 1;
  }
}
</style>
