import { ref } from 'vue'

const STORAGE_KEY = 'campusbridge:dashboard-sidebar-collapsed'

function readCollapsedState() {
  if (typeof window === 'undefined') return false

  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function writeCollapsedState(value) {
  if (typeof window === 'undefined') return

  try {
    window.localStorage.setItem(STORAGE_KEY, String(value))
  } catch {
    // Ignore storage failures; the in-memory state still keeps the UI usable.
  }
}

export function useDashboardSidebar() {
  const sidebarCollapsed = ref(readCollapsedState())

  function toggleSidebarCollapsed() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    writeCollapsedState(sidebarCollapsed.value)
  }

  return {
    sidebarCollapsed,
    toggleSidebarCollapsed
  }
}
