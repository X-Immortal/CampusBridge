import { ref } from 'vue'

const toasts = ref([])
let nextId = 1

export function useToast() {
  function showToast(message, options = {}) {
    const id = nextId++
    const toast = {
      id,
      message,
      type: options.type || 'info',
      duration: options.duration ?? 4000,
      closable: options.closable ?? true
    }

    toasts.value.push(toast)

    if (toast.duration > 0) {
      window.setTimeout(() => closeToast(id), toast.duration)
    }

    return id
  }

  function closeToast(id) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  return {
    toasts,
    showToast,
    closeToast
  }
}

export const toastApi = { showToast: useToast().showToast, closeToast: useToast().closeToast }

export function showToast(message, options) {
  return toastApi.showToast(message, options)
}

export function closeToast(id) {
  return toastApi.closeToast(id)
}
