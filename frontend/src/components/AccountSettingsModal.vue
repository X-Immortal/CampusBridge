<script setup>
import { reactive, ref } from 'vue'
import {
  AtSign,
  KeyRound,
  LoaderCircle,
  Phone,
  X
} from '@lucide/vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'
import { useToast } from '../composables/useToast'

defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

const { state } = useAuth()
const { showToast } = useToast()

const activeTab = ref('password')
const submitting = ref(false)

// Password
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordErrors = reactive({})

// Email
const emailForm = reactive({ email: '', code: '' })
const emailCodeSent = ref(false)
const emailSending = ref(false)

// Phone
const phoneForm = reactive({ phone: '' })
const phoneErrors = reactive({})

function clearPasswordErrors() {
  Object.keys(passwordErrors).forEach(k => delete passwordErrors[k])
}

async function handleChangePassword() {
  clearPasswordErrors()
  if (!passwordForm.oldPassword) {
    passwordErrors.oldPassword = '请输入当前密码'
    return
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    passwordErrors.newPassword = '新密码至少6位'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordErrors.confirmPassword = '两次输入的新密码不一致'
    return
  }
  submitting.value = true
  try {
    await api.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    showToast('密码已更新', { type: 'success' })
  } catch (e) {
    showToast(e.message || '密码修改失败', { type: 'danger' })
  } finally {
    submitting.value = false
  }
}

function isValidEmail(val) {
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(String(val || '').trim())
}

async function handleSendEmailCode() {
  const email = emailForm.email.trim()
  if (!email || !isValidEmail(email)) {
    showToast('请输入有效的邮箱地址', { type: 'warning' })
    return
  }
  emailSending.value = true
  try {
    await api.sendAccountEmailCode({ email })
    emailCodeSent.value = true
    showToast('验证码已发送', { type: 'success' })
  } catch (e) {
    showToast(e.message || '发送失败', { type: 'danger' })
  } finally {
    emailSending.value = false
  }
}

async function handleConfirmEmail() {
  if (!emailForm.code.trim()) {
    showToast('请输入验证码', { type: 'warning' })
    return
  }
  submitting.value = true
  try {
    await api.confirmAccountEmail({ code: emailForm.code.trim() })
    state.user.email = emailForm.email.trim()
    emailForm.email = ''
    emailForm.code = ''
    emailCodeSent.value = false
    showToast('邮箱已更新', { type: 'success' })
  } catch (e) {
    showToast(e.message || '验证失败', { type: 'danger' })
  } finally {
    submitting.value = false
  }
}

async function handleUpdatePhone() {
  const phone = phoneForm.phone.trim()
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    phoneErrors.phone = '请输入有效的11位手机号'
    return
  }
  submitting.value = true
  try {
    await api.updatePhone({ phone })
    state.user.phone = phone
    showToast('手机号已更新', { type: 'success' })
  } catch (e) {
    showToast(e.message || '更新失败', { type: 'danger' })
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('close')
}
</script>

<template>
  <div
    v-if="visible"
    class="modal-overlay"
    @click.self="close"
  >
    <div
      class="modal-card"
      role="dialog"
      aria-label="账号信息修改"
    >
      <button
        class="modal-close"
        type="button"
        aria-label="关闭"
        @click="close"
      >
        <X :size="22" />
      </button>

      <header class="modal-header">
        <h2>账号信息修改</h2>
      </header>

      <div class="modal-tabs">
        <button
          :class="{ active: activeTab === 'password' }"
          @click="activeTab = 'password'"
        >
          <KeyRound :size="16" /> 修改密码
        </button>
        <button
          :class="{ active: activeTab === 'email' }"
          @click="activeTab = 'email'"
        >
          <AtSign :size="16" /> 修改邮箱
        </button>
        <button
          :class="{ active: activeTab === 'phone' }"
          @click="activeTab = 'phone'"
        >
          <Phone :size="16" /> 修改手机
        </button>
      </div>

      <!-- Password -->
      <form
        v-if="activeTab === 'password'"
        class="modal-form"
        @submit.prevent="handleChangePassword"
      >
        <label class="field">
          <span>当前密码</span>
          <input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="输入当前密码"
            :aria-invalid="Boolean(passwordErrors.oldPassword)"
          >
          <small
            v-if="passwordErrors.oldPassword"
            class="field-error"
          >{{ passwordErrors.oldPassword }}</small>
        </label>
        <label class="field">
          <span>新密码</span>
          <input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="至少6位"
            :aria-invalid="Boolean(passwordErrors.newPassword)"
          >
          <small
            v-if="passwordErrors.newPassword"
            class="field-error"
          >{{ passwordErrors.newPassword }}</small>
        </label>
        <label class="field">
          <span>确认新密码</span>
          <input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            :aria-invalid="Boolean(passwordErrors.confirmPassword)"
          >
          <small
            v-if="passwordErrors.confirmPassword"
            class="field-error"
          >{{ passwordErrors.confirmPassword }}</small>
        </label>
        <button
          class="primary-action"
          type="submit"
          :disabled="submitting"
        >
          <LoaderCircle
            v-if="submitting"
            class="spinning"
            :size="16"
          />
          修改密码
        </button>
      </form>

      <!-- Email -->
      <form
        v-if="activeTab === 'email'"
        class="modal-form"
        @submit.prevent="handleConfirmEmail"
      >
        <label class="field">
          <span>新邮箱</span>
          <input
            v-model.trim="emailForm.email"
            type="email"
            placeholder="请输入新邮箱地址"
            :disabled="emailCodeSent"
          >
        </label>
        <button
          v-if="!emailCodeSent"
          class="primary-action modal-send-btn"
          type="button"
          :disabled="emailSending"
          @click="handleSendEmailCode"
        >
          <LoaderCircle
            v-if="emailSending"
            class="spinning"
            :size="16"
          />
          发送验证码
        </button>
        <template v-else>
          <label class="field">
            <span>验证码</span>
            <input
              v-model.trim="emailForm.code"
              inputmode="numeric"
              maxlength="6"
              placeholder="6位验证码"
            >
          </label>
          <button
            class="primary-action"
            type="submit"
            :disabled="submitting"
          >
            <LoaderCircle
              v-if="submitting"
              class="spinning"
              :size="16"
            />
            确认修改
          </button>
        </template>
      </form>

      <!-- Phone -->
      <form
        v-if="activeTab === 'phone'"
        class="modal-form"
        @submit.prevent="handleUpdatePhone"
      >
        <label class="field">
          <span>手机号</span>
          <input
            v-model.trim="phoneForm.phone"
            type="tel"
            maxlength="11"
            placeholder="11位手机号"
            :aria-invalid="Boolean(phoneErrors.phone)"
          >
          <small
            v-if="phoneErrors.phone"
            class="field-error"
          >{{ phoneErrors.phone }}</small>
        </label>
        <button
          class="primary-action"
          type="submit"
          :disabled="submitting"
        >
          <LoaderCircle
            v-if="submitting"
            class="spinning"
            :size="16"
          />
          更新手机号
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
}

.modal-card {
  position: relative;
  width: min(480px, 92vw);
  max-height: 85vh;
  overflow-y: auto;
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: var(--shadow-elevated);
}

.modal-close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  cursor: pointer;
}

.modal-close:hover { background: var(--color-surface-hover); }

.modal-header h2 {
  margin: 0;
  font-size: 1.15rem;
  color: var(--color-text);
}

.modal-tabs {
  display: flex;
  gap: 6px;
  border-bottom: 1px solid var(--color-divider);
  padding-bottom: 10px;
}

.modal-tabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease;
}

.modal-tabs button:hover:not(.active) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.modal-tabs button.active {
  border-color: var(--color-brand-text);
  background: var(--color-brand-text);
  color: var(--color-surface);
}

.modal-form {
  display: grid;
  gap: 13px;
}

.modal-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.field {
  display: grid;
  gap: 5px;
}

.field span {
  color: var(--color-text);
  font-weight: 700;
  font-size: 0.88rem;
}

.field input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 0.92rem;
  outline: 0;
}

.field input:focus {
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-focus);
}

.field input[aria-invalid="true"] {
  border-color: var(--color-danger-soft);
}

.field-error {
  color: var(--color-danger-soft);
  font-size: 0.8rem;
  margin: 0;
}

.primary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 18px;
  border: 1px solid var(--color-brand-text);
  border-radius: 8px;
  background: var(--color-brand-text);
  color: var(--color-surface);
  font-size: 1rem;
  font-weight: 900;
  cursor: pointer;
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

.ghost-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 900;
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease;
}

.ghost-action:hover:not(:disabled) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.ghost-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-send-btn {
  background: var(--color-brand-soft);
  border-color: var(--color-brand-border);
  color: var(--color-brand-hover);
  box-shadow: var(--shadow-card);
  font-size: 0.92rem;
}

.modal-send-btn:hover:not(:disabled) {
  background: var(--color-brand-soft-hover);
  border-color: var(--color-brand-border-hover);
  box-shadow: var(--shadow-card);
}

.modal-send-btn:disabled {
  opacity: 0.5;
}

@keyframes spin { to { transform: rotate(360deg); } }
.spinning { animation: spin 0.75s linear infinite; }
</style>
