import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'cb-theme'
const VALID_THEMES = ['light', 'dark', 'system']
const DEFAULT_THEME = 'system'

const theme = ref(DEFAULT_THEME)
const effectiveTheme = ref('light')

function readStoredTheme() {
  if (typeof window === 'undefined') return DEFAULT_THEME

  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    return VALID_THEMES.includes(stored) ? stored : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

function writeStoredTheme(value) {
  if (typeof window === 'undefined') return

  try {
    window.localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // Ignore storage failures; the in-memory state still keeps the UI usable.
  }
}

function getSystemPreference() {
  if (typeof window === 'undefined') return false

  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function computeEffectiveTheme(mode) {
  if (mode === 'system') {
    return getSystemPreference() ? 'dark' : 'light'
  }
  return mode
}

function applyTheme(mode) {
  if (typeof document === 'undefined') return

  const effective = computeEffectiveTheme(mode)
  effectiveTheme.value = effective
  document.documentElement.setAttribute('data-theme', effective)
}

let mediaQueryListener = null

function startSystemListener() {
  if (typeof window === 'undefined') return

  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQueryListener = () => {
    if (theme.value === 'system') {
      applyTheme('system')
    }
  }

  if (mediaQuery.addEventListener) {
    mediaQuery.addEventListener('change', mediaQueryListener)
  } else if (mediaQuery.addListener) {
    mediaQuery.addListener(mediaQueryListener)
  }
}

export function useTheme() {
  onMounted(() => {
    theme.value = readStoredTheme()
    applyTheme(theme.value)
    startSystemListener()
  })

  watch(theme, (newTheme) => {
    writeStoredTheme(newTheme)
    applyTheme(newTheme)
  })

  function setTheme(nextTheme) {
    if (VALID_THEMES.includes(nextTheme)) {
      theme.value = nextTheme
    }
  }

  function cycleTheme() {
    const order = ['light', 'dark', 'system']
    const next = order[(order.indexOf(theme.value) + 1) % order.length]
    setTheme(next)
  }

  return {
    theme,
    effectiveTheme,
    setTheme,
    cycleTheme
  }
}

export function getInitialThemeScript() {
  return `
    (function () {
      try {
        var theme = localStorage.getItem('${STORAGE_KEY}') || '${DEFAULT_THEME}';
        var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        var effective = theme === 'system' ? (prefersDark ? 'dark' : 'light') : theme;
        document.documentElement.setAttribute('data-theme', effective);
      } catch (e) {}
    })();
  `.trim()
}
