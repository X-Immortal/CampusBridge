<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { BadgeCheck, KeyRound, LogIn, RefreshCw, School, Send, ShieldCheck, UserCircle } from '@lucide/vue'
import StatusBadge from '../components/StatusBadge.vue'
import { optionLabel, STATUS_OPTIONS, VERIFICATION_METHODS } from '../constants'
import { useAuth } from '../stores/auth'

const { state, isAuthenticated, currentUserId, verificationStatus, refreshSession, submitVerification } = useAuth()
const loading = ref(false)
const submitting = ref(false)
const feedback = ref('')
const feedbackType = ref('success')

const verificationForm = reactive({
  method: 'STUDENT_CARD_IMAGE',
  realName: '',
  studentNumber: '',
  school: '',
  department: '',
  evidenceUrl: ''
})

const authStatus = computed(() => (isAuthenticated.value ? 'LOGGED_IN' : 'GUEST'))
const accountEnabledStatus = computed(() => (state.user?.enabled === 0 ? 'DISABLED' : 'ENABLED'))

function setFeedback(message, type = 'success') {
  feedback.value = message
  feedbackType.value = type
}

async function handleRefresh() {
  loading.value = true
  feedback.value = ''
  try {
    await refreshSession()
    setFeedback('账号与校内认证状态已刷新。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    loading.value = false
  }
}

async function handleVerificationSubmit() {
  submitting.value = true
  feedback.value = ''
  try {
    await submitVerification(verificationForm)
    setFeedback('认证资料已提交，等待审核。')
  } catch (error) {
    setFeedback(error.message, 'danger')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (isAuthenticated.value) refreshSession()
})
</script>

<template>
  <section class="page home-page">
    <div class="workbench-shell">
      <section class="overview-panel">
        <p class="eyebrow">
          <ShieldCheck
            :size="16"
            aria-hidden="true"
          />
          FE01 身份与校内认证
        </p>
        <h1>登录后，在这里完成校内认证。</h1>
        <p class="overview-copy">
          当前页面只承接 FE01 范围：账号状态、当前用户同步、校内认证提交与查询。登录注册入口保持干净，认证动作进入工作台后再进行。
        </p>

        <div class="status-row">
          <article>
            <span>登录状态</span>
            <strong>{{ isAuthenticated ? state.user?.username || '已登录用户' : '游客模式' }}</strong>
            <StatusBadge :status="authStatus" />
          </article>
          <article>
            <span>校内认证</span>
            <strong>{{ optionLabel(STATUS_OPTIONS, verificationStatus) }}</strong>
            <StatusBadge :status="verificationStatus" />
          </article>
        </div>

        <div class="metric-row">
          <article>
            <KeyRound
              :size="18"
              aria-hidden="true"
            />
            <span>当前用户 ID</span>
            <strong>{{ currentUserId || '-' }}</strong>
          </article>
          <article>
            <UserCircle
              :size="18"
              aria-hidden="true"
            />
            <span>账号状态</span>
            <strong>{{ optionLabel(STATUS_OPTIONS, accountEnabledStatus) }}</strong>
          </article>
          <article>
            <School
              :size="18"
              aria-hidden="true"
            />
            <span>认证方式</span>
            <strong>{{ optionLabel(VERIFICATION_METHODS, state.verification?.method) }}</strong>
          </article>
        </div>

        <div
          v-if="feedback"
          class="alert compact-alert"
          :class="feedbackType === 'danger' ? 'alert--danger' : 'alert--success'"
          role="status"
        >
          <BadgeCheck
            :size="18"
            aria-hidden="true"
          />
          <span>{{ feedback }}</span>
        </div>

        <div class="overview-actions">
          <RouterLink
            v-if="!isAuthenticated"
            class="button"
            to="/auth"
          >
            <LogIn
              :size="18"
              aria-hidden="true"
            />
            去登录
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
      </section>

      <form
        class="verification-panel"
        aria-labelledby="verify-title"
        @submit.prevent="handleVerificationSubmit"
      >
        <div class="verification-heading">
          <div>
            <p class="eyebrow">
              <ShieldCheck
                :size="16"
                aria-hidden="true"
              />
              登录后认证
            </p>
            <h2 id="verify-title">
              校内认证资料
            </h2>
          </div>
          <StatusBadge :status="verificationStatus" />
        </div>

        <div
          v-if="!isAuthenticated"
          class="locked-state"
        >
          <ShieldCheck
            :size="30"
            aria-hidden="true"
          />
          <strong>请先登录账号</strong>
          <span>登录后即可提交学生证、校园邮箱或人工审核资料。</span>
          <RouterLink
            class="button"
            to="/auth"
          >
            <LogIn
              :size="18"
              aria-hidden="true"
            />
            打开登录页
          </RouterLink>
        </div>

        <template v-else>
          <div class="verification-grid">
            <div class="field">
              <label for="verify-method">认证方式</label>
              <select
                id="verify-method"
                v-model="verificationForm.method"
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
                required
              >
            </div>
            <div class="field">
              <label for="verify-number">学号</label>
              <input
                id="verify-number"
                v-model.trim="verificationForm.studentNumber"
                required
              >
            </div>
            <div class="field">
              <label for="verify-school">学校</label>
              <input
                id="verify-school"
                v-model.trim="verificationForm.school"
                required
              >
            </div>
            <div class="field">
              <label for="verify-department">院系</label>
              <input
                id="verify-department"
                v-model.trim="verificationForm.department"
              >
            </div>
            <div class="field">
              <label for="verify-evidence">材料链接</label>
              <input
                id="verify-evidence"
                v-model.trim="verificationForm.evidenceUrl"
                placeholder="学生证或校园邮箱证明材料链接"
              >
            </div>
          </div>

          <div class="verification-actions">
            <button
              class="button"
              type="submit"
              :disabled="submitting"
            >
              <Send
                :size="17"
                aria-hidden="true"
              />
              提交认证
            </button>
            <span>当前方式：{{ optionLabel(VERIFICATION_METHODS, state.verification?.method) }}</span>
          </div>
        </template>
      </form>
    </div>
  </section>
</template>

<style scoped>
.home-page {
  min-height: calc(100vh - 72px);
  display: grid;
  align-items: center;
  padding-top: 22px;
  padding-bottom: 22px;
}

.workbench-shell {
  display: grid;
  grid-template-columns: minmax(360px, 0.88fr) minmax(480px, 1.12fr);
  gap: 20px;
  align-items: stretch;
}

.overview-panel,
.verification-panel {
  display: grid;
  align-content: start;
  gap: 18px;
  min-height: 620px;
  padding: 24px;
  border: 1px solid rgba(219, 228, 238, 0.9);
  border-radius: var(--radius-card);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-soft);
}

.overview-panel {
  background:
    radial-gradient(circle at 14% 10%, rgba(138, 63, 120, 0.1), transparent 26%),
    #ffffff;
}

.overview-panel h1 {
  margin: 0;
  color: var(--color-primary);
  font-size: clamp(2rem, 3vw, 3.1rem);
  line-height: 1.18;
  letter-spacing: 0;
}

.overview-copy {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.7;
}

.status-row,
.metric-row {
  display: grid;
  gap: 12px;
}

.status-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.status-row article,
.metric-row article {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: #f8fafc;
}

.status-row span,
.metric-row span,
.verification-actions span {
  color: var(--color-muted);
  font-size: 0.86rem;
}

.status-row strong {
  color: var(--color-primary);
  font-size: 1.2rem;
}

.metric-row {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-row article {
  align-content: start;
}

.metric-row svg {
  color: var(--color-action);
}

.metric-row strong {
  color: var(--color-primary);
  font-size: 1.05rem;
}

.compact-alert {
  padding: 10px 12px;
}

.overview-actions,
.verification-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: auto;
}

.verification-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.verification-heading h2 {
  margin: 6px 0 0;
  color: var(--color-primary);
  font-size: 1.8rem;
}

.verification-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
}

.locked-state {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: 410px;
  padding: 28px;
  border: 1px dashed #cbd5e1;
  border-radius: var(--radius-card);
  background: #f8fafc;
  color: var(--color-muted);
  text-align: center;
}

.locked-state svg {
  color: var(--color-action);
}

.locked-state strong {
  color: var(--color-primary);
  font-size: 1.25rem;
}

@media (max-width: 1050px) {
  .home-page {
    align-items: start;
  }

  .workbench-shell {
    grid-template-columns: 1fr;
  }

  .overview-panel,
  .verification-panel {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .status-row,
  .metric-row,
  .verification-grid {
    grid-template-columns: 1fr;
  }
}
</style>
