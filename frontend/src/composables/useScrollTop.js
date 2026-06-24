import { onBeforeUnmount, onMounted, ref } from 'vue'

export function useScrollTop(threshold = 400) {
  const visible = ref(false)

  function onScroll() {
    visible.value = window.scrollY > threshold
  }

  function scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  onMounted(() => {
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
  })

  return {
    visible,
    scrollToTop
  }
}
