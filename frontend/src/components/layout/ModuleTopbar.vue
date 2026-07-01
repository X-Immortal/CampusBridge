<script setup>
/**
 * 统一模块顶栏
 * 用于承载「当前模块识别 + 二级功能入口」的顶部区域。
 */
defineProps({
  eyebrow: {
    type: String,
    default: ''
  },
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <section class="module-topbar">
    <div class="module-topbar__heading">
      <div>
        <p
          v-if="eyebrow"
          class="module-topbar__eyebrow"
        >
          {{ eyebrow }}
        </p>
        <h1 class="module-topbar__title">
          {{ title }}
        </h1>
        <p
          v-if="description"
          class="module-topbar__desc"
        >
          {{ description }}
        </p>
      </div>
      <div class="module-topbar__extra">
        <slot name="extra" />
      </div>
    </div>
    <div
      v-if="$slots.default"
      class="module-topbar__entries"
    >
      <slot />
    </div>
  </section>
</template>

<style scoped>
.module-topbar {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card, 8px);
  background: color-mix(in srgb, var(--color-surface) 96%, var(--color-bg));
}

.module-topbar__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.module-topbar__eyebrow {
  margin: 0 0 4px;
  color: var(--color-brand-text);
  font-size: 0.82rem;
  font-weight: 900;
}

.module-topbar__title {
  margin: 0;
  color: var(--color-text);
  font-size: 1.18rem;
  font-weight: 850;
  line-height: 1.25;
}

.module-topbar__desc {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  font-size: 0.9rem;
  line-height: 1.55;
}

.module-topbar__extra {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.module-topbar__entries {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  gap: 14px;
}

@media (max-width: 1180px) {
  .module-topbar__entries {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .module-topbar__heading {
    flex-direction: column;
    gap: 12px;
  }

  .module-topbar__extra {
    width: 100%;
    justify-content: flex-start;
  }

  .module-topbar__entries {
    grid-template-columns: 1fr;
  }
}
</style>
