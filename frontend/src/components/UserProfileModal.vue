<script setup>
import { computed, ref, watch } from 'vue'
import {
  Award,
  BadgeCheck,
  LoaderCircle,
  MapPin,
  School,
  Star,
  UserRound,
  X
} from '@lucide/vue'
import api from '../services/api'

const props = defineProps({
  userId: { type: [Number, String], default: null },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

const profile = ref(null)
const reviews = ref([])
const loading = ref(false)
const error = ref('')
const avatarFailed = ref(false)

const levelLabels = ['', '新手上路', '靠谱帮手', '校园达人', '互助之星']
const reviewLabels = { 1: '体验很差', 2: '低于预期', 3: '基本完成', 4: '值得信任', 5: '非常可靠' }

const levelLabel = computed(() => levelLabels[profile.value?.creditLevel] || '新手上路')
const averageRating = computed(() => {
  if (!reviews.value.length) return '0.0'
  const avg = reviews.value.reduce((sum, r) => sum + (Number(r.rating) || 0), 0) / reviews.value.length
  return avg.toFixed(1)
})

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function load() {
  if (!props.userId) return
  loading.value = true
  error.value = ''
  try {
    const [profileResult, reviewsResult] = await Promise.allSettled([
      api.getUserProfile(props.userId),
      api.getUserReviews(props.userId)
    ])
    avatarFailed.value = false
    profile.value = profileResult.status === 'fulfilled' ? profileResult.value : null
    reviews.value = reviewsResult.status === 'fulfilled' && Array.isArray(reviewsResult.value)
      ? reviewsResult.value
      : []
  } catch {
    error.value = '加载用户信息失败'
  } finally {
    loading.value = false
  }
}

watch(() => [props.userId, props.visible], ([uid, vis]) => {
  if (uid && vis) load()
})

function close() {
  emit('close')
}
</script>

<template>
  <div
    v-if="visible"
    class="profile-modal-overlay"
    @click.self="close"
  >
    <div
      class="profile-modal"
      role="dialog"
      aria-label="用户信息"
    >
      <button
        class="profile-modal-close"
        type="button"
        aria-label="关闭"
        @click="close"
      >
        <X :size="22" />
      </button>

      <div
        v-if="loading"
        class="profile-modal-loading"
      >
        <LoaderCircle
          class="spinning"
          :size="32"
        />
        <span>加载用户信息…</span>
      </div>

      <template v-else-if="error">
        <p class="profile-modal-error">
          {{ error }}
        </p>
      </template>

      <template v-else-if="profile">
        <header class="profile-modal-header">
          <div class="profile-modal-avatar">
            <img
              v-if="profile.avatarUrl && !avatarFailed"
              :src="profile.avatarUrl"
              :alt="`${profile.nickname} 头像`"
              @error="avatarFailed = true"
            >
            <UserRound
              v-else
              :size="48"
            />
          </div>
          <div class="profile-modal-hero">
            <h2>{{ profile.nickname || '未设置昵称' }}</h2>
            <div class="profile-modal-chips">
              <span
                v-if="profile.verified"
                class="profile-chip profile-chip--verified"
              >
                <BadgeCheck :size="14" /> 已认证
              </span>
              <span class="profile-chip profile-chip--level">
                <Award :size="14" /> Lv.{{ profile.creditLevel ?? 1 }} {{ levelLabel }}
              </span>
              <span class="profile-chip">
                <Star :size="14" /> {{ profile.points ?? 0 }} 积分
              </span>
            </div>
          </div>
        </header>

        <section class="profile-modal-section">
          <h3>基本信息</h3>
          <dl class="profile-modal-dl">
            <div v-if="profile.bio">
              <dt>个人简介</dt>
              <dd>{{ profile.bio }}</dd>
            </div>
            <div>
              <dt><MapPin :size="15" /> 校区</dt>
              <dd>{{ profile.campus || '未填写' }}</dd>
            </div>
            <div>
              <dt><School :size="15" /> 学院</dt>
              <dd>{{ profile.college || '未填写' }}</dd>
            </div>
          </dl>
        </section>

        <section
          v-if="profile.interestTags?.length || profile.skillTags?.length"
          class="profile-modal-section"
        >
          <h3>标签</h3>
          <div class="profile-modal-tags">
            <span
              v-for="tag in profile.interestTags"
              :key="'interest-' + tag"
              class="profile-modal-tag profile-modal-tag--interest"
            >{{ tag }}</span>
            <span
              v-for="tag in profile.skillTags"
              :key="'skill-' + tag"
              class="profile-modal-tag profile-modal-tag--skill"
            >{{ tag }}</span>
          </div>
        </section>

        <section class="profile-modal-section">
          <h3>
            收到的评价
            <span class="profile-modal-review-count">
              {{ reviews.length }} 条 · 均分 {{ averageRating }}
            </span>
          </h3>
          <div
            v-if="!reviews.length"
            class="profile-modal-empty"
          >
            暂无评价记录
          </div>
          <div
            v-else
            class="profile-modal-reviews"
          >
            <article
              v-for="review in reviews"
              :key="review.id"
              class="profile-modal-review-card"
            >
              <div class="profile-modal-review-top">
                <span class="profile-modal-review-rating">
                  <Star
                    v-for="n in 5"
                    :key="n"
                    :size="14"
                    :class="{ filled: n <= (review.rating || 0) }"
                  />
                  <strong>{{ reviewLabels[review.rating] || `${review.rating} 分` }}</strong>
                </span>
                <small>{{ formatDateTime(review.createdAt) }}</small>
              </div>
              <p v-if="review.comment">
                {{ review.comment }}
              </p>
            </article>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.profile-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
}

.profile-modal {
  position: relative;
  width: min(680px, 92vw);
  max-height: 85vh;
  overflow-y: auto;
  display: grid;
  gap: 20px;
  padding: 28px;
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: var(--shadow-elevated);
}

.profile-modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 180ms ease;
}

.profile-modal-close:hover {
  background: var(--color-surface-hover);
}

.profile-modal-loading {
  display: grid;
  place-items: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--color-text-muted);
}

.profile-modal-error {
  text-align: center;
  color: var(--color-danger-soft);
  padding: 32px 0;
}

.profile-modal-header {
  display: flex;
  align-items: center;
  gap: 18px;
}

.profile-modal-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-surface-muted);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--color-text-muted);
}

.profile-modal-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-modal-hero h2 {
  margin: 0 0 8px;
  font-size: 1.25rem;
  color: var(--color-text);
}

.profile-modal-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.profile-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 600;
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
}

.profile-chip--verified {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.profile-chip--level {
  background: var(--color-brand-soft);
  color: var(--color-brand-text);
}

.profile-modal-section {
  display: grid;
  gap: 10px;
}

.profile-modal-section h3 {
  margin: 0;
  font-size: 0.95rem;
  color: var(--color-text);
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.profile-modal-review-count {
  font-weight: 400;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.profile-modal-dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.profile-modal-dl > div {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 6px;
}

.profile-modal-dl dt {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.profile-modal-dl dd {
  margin: 0;
  font-size: 0.92rem;
  color: var(--color-text);
  word-break: break-word;
}

.profile-modal-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.profile-modal-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 0.83rem;
  font-weight: 600;
}

.profile-modal-tag--interest {
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.profile-modal-tag--skill {
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
}

.profile-modal-empty {
  padding: 14px;
  border: 1px dashed var(--color-border-dashed);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  text-align: center;
  font-size: 0.9rem;
}

.profile-modal-reviews {
  display: grid;
  gap: 10px;
}

.profile-modal-review-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.profile-modal-review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.profile-modal-review-rating {
  display: flex;
  align-items: center;
  gap: 4px;
}

.profile-modal-review-rating svg {
  color: var(--color-border);
}

.profile-modal-review-rating svg.filled {
  color: var(--color-warning);
}

.profile-modal-review-rating strong {
  margin-left: 4px;
  font-size: 0.88rem;
  color: var(--color-text);
}

.profile-modal-review-top small {
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.profile-modal-review-card p {
  margin: 0;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
  line-height: 1.55;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.spinning {
  animation: spin 0.75s linear infinite;
}
</style>
