<script setup>
import { Inbox } from '@lucide/vue'

defineProps({
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: '接口返回为空，等待后端联调或刷新后重试。'
  }
})
</script>

<template>
  <section
    class="empty-state"
    aria-live="polite"
  >
    <Inbox
      :size="28"
      aria-hidden="true"
    />
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </section>
</template>
