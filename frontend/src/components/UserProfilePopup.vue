<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  LoaderCircle,
  Mail,
  ShieldBan,
  ShieldCheck,
  UserRound,
  X
} from '@lucide/vue'
import api from '../services/api'
import { useAuth } from '../stores/auth'

const props = defineProps({
  userId: { type: [String, Number], default: null },
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

const router = useRouter()
const { currentUserId } = useAuth()

const profile = ref(null)
const loading = ref(false)
const error = ref('')
const blocking = ref(false)
const isBlocked = ref(false)

const isSelf = computed(() =>
  props.userId != null &&
  currentUserId.value != null &&
  String(props.userId) === String(currentUserId.value)
)

const displayName = computed(() => {
  if (profile.value?.nickname) return profile.value.nickname
  if (profile.value?.username) return profile.value.username
  return `用户 ${props.userId}`
})

async function loadProfile() {
  if (!props.userId) return
  loading.value = true
  error.value = ''
  profile.value = null
  try {
    profile.value = await api.getUserProfile(props.userId)
    if (!isSelf.value) {
      const blockResult = await api.checkBlock(props.userId)
      isBlocked.value = Boolean(blockResult?.blocked)
    }
  } catch (err) {
    error.value = err.message || '用户资料加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleBlock() {
  if (!props.userId || blocking.value) return
  blocking.value = true
  try {
    if (isBlocked.value) {
      await api.unblockUser(props.userId)
      isBlocked.value = false
    } else {
      await api.blockUser(props.userId)
      isBlocked.value = true
    }
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    blocking.value = false
  }
}

function startConversation() {
  emit('close')
  router.push({ name: 'conversation-list', query: { peerId: String(props.userId) } })
}

watch(() => props.open, (val) => {
  if (val && props.userId) {
    loadProfile()
  }
})

onMounted(() => {
  if (props.open && props.userId) {
    loadProfile()
  }
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="user-profile-overlay"
      @click.self="emit('close')"
    >
      <div class="user-profile-popup">
        <header class="popup-header">
          <h3>用户资料</h3>
          <button
            class="icon-action"
            type="button"
            aria-label="关闭"
            @click="emit('close')"
          >
            <X
              :size="20"
              aria-hidden="true"
            />
          </button>
        </header>

        <div
          v-if="loading"
          class="popup-loading"
        >
          <LoaderCircle
            class="spinning"
            :size="24"
            aria-hidden="true"
          />
          <span>加载中</span>
        </div>

        <div
          v-else-if="error"
          class="popup-error"
        >
          {{ error }}
        </div>

        <template v-else-if="profile">
          <div class="profile-avatar">
            <div class="avatar-circle">
              <UserRound
                :size="32"
                aria-hidden="true"
              />
            </div>
            <div class="profile-names">
              <strong>{{ displayName }}</strong>
              <span v-if="profile.username && profile.username !== displayName">@{{ profile.username }}</span>
            </div>
          </div>

          <dl class="profile-facts">
            <div v-if="profile.campus">
              <dt>校区</dt>
              <dd>{{ profile.campus }}</dd>
            </div>
            <div v-if="profile.college">
              <dt>学院</dt>
              <dd>{{ profile.college }}</dd>
            </div>
            <div v-if="profile.bio">
              <dt>简介</dt>
              <dd>{{ profile.bio }}</dd>
            </div>
          </dl>

          <div
            v-if="!isSelf"
            class="profile-actions"
          >
            <button
              class="primary-action"
              type="button"
              @click="startConversation"
            >
              <Mail
                :size="16"
                aria-hidden="true"
              />
              私信
            </button>
            <button
              class="block-action"
              :class="{ 'block-action--active': isBlocked }"
              type="button"
              :disabled="blocking"
              @click="toggleBlock"
            >
              <ShieldBan
                v-if="!isBlocked"
                :size="16"
                aria-hidden="true"
              />
              <ShieldCheck
                v-else
                :size="16"
                aria-hidden="true"
              />
              {{ isBlocked ? '取消拉黑' : '拉黑' }}
            </button>
          </div>
          <div
            v-else
            class="profile-self-hint"
          >
            这是你自己的账号
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.user-profile-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.35);
}

.user-profile-popup {
  width: min(380px, 92vw);
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  display: grid;
  gap: 14px;
  padding: 20px;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.popup-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: #111827;
}

.popup-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6b7280;
  padding: 20px 0;
  justify-content: center;
}

.popup-error {
  color: #b91c1c;
  font-size: 0.88rem;
  font-weight: 700;
  padding: 12px;
  background: #fef2f2;
  border-radius: 8px;
}

.profile-avatar {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #eef2ff;
  display: grid;
  place-items: center;
  color: #6366f1;
  flex-shrink: 0;
}

.profile-names {
  display: grid;
  gap: 2px;
}

.profile-names strong {
  font-size: 1.05rem;
  color: #111827;
}

.profile-names span {
  font-size: 0.82rem;
  color: #6b7280;
}

.profile-facts {
  display: grid;
  gap: 8px;
  margin: 0;
}

.profile-facts > div {
  display: grid;
  grid-template-columns: 60px 1fr;
  gap: 8px;
  align-items: start;
}

.profile-facts dt {
  color: #6b7280;
  font-size: 0.82rem;
  font-weight: 700;
}

.profile-facts dd {
  margin: 0;
  color: #374151;
  font-size: 0.88rem;
}

.profile-actions {
  display: flex;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
}

.block-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
}

.block-action:hover {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #b91c1c;
}

.block-action--active {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #b91c1c;
}

.block-action--active:hover {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.profile-self-hint {
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 8px;
  color: #6b7280;
  font-size: 0.85rem;
  text-align: center;
}
</style>
