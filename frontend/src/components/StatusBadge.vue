<script setup>
import { computed } from 'vue'
import { CheckCircle2, Clock3, ShieldAlert } from '@lucide/vue'
import { STATUS_OPTIONS, optionLabel, statusTone } from '../constants'

const props = defineProps({
  status: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    default: ''
  },
  tone: {
    type: String,
    default: ''
  }
})

const badgeTone = computed(() => props.tone || statusTone(props.status))
const displayLabel = computed(() => props.label || optionLabel(STATUS_OPTIONS, props.status))
const icon = computed(() => {
  if (badgeTone.value === 'success') return CheckCircle2
  if (badgeTone.value === 'danger') return ShieldAlert
  return Clock3
})
</script>

<template>
  <span
    class="status-badge"
    :class="`status-badge--${badgeTone}`"
  >
    <component
      :is="icon"
      :size="14"
      aria-hidden="true"
    />
    {{ displayLabel }}
  </span>
</template>
