import { onMounted, ref, watch } from 'vue'

const STORAGE_KEYS = {
  accent: 'cb-accent',
  density: 'cb-density'
}

const DEFAULTS = {
  accent: 'purple',
  density: 'comfortable'
}

const VALID_ACCENTS = ['purple', 'blue', 'orange', 'green']
const VALID_DENSITIES = ['compact', 'comfortable', 'spacious']

const accent = ref(DEFAULTS.accent)
const density = ref(DEFAULTS.density)

function readStored(key) {
  if (typeof window === 'undefined') return DEFAULTS[key]

  try {
    const stored = window.localStorage.getItem(STORAGE_KEYS[key])
    const valid = key === 'accent' ? VALID_ACCENTS : VALID_DENSITIES
    return valid.includes(stored) ? stored : DEFAULTS[key]
  } catch {
    return DEFAULTS[key]
  }
}

function writeStored(key, value) {
  if (typeof window === 'undefined') return

  try {
    window.localStorage.setItem(STORAGE_KEYS[key], value)
  } catch {
    // Ignore storage failures; the in-memory state keeps the UI usable.
  }
}

function apply() {
  if (typeof document === 'undefined') return

  document.documentElement.setAttribute('data-accent', accent.value)
  document.documentElement.setAttribute('data-density', density.value)
}

export function usePreferences() {
  onMounted(() => {
    accent.value = readStored('accent')
    density.value = readStored('density')
    apply()
  })

  watch([accent, density], () => {
    writeStored('accent', accent.value)
    writeStored('density', density.value)
    apply()
  })

  function setAccent(nextAccent) {
    if (VALID_ACCENTS.includes(nextAccent)) {
      accent.value = nextAccent
    }
  }

  function setDensity(nextDensity) {
    if (VALID_DENSITIES.includes(nextDensity)) {
      density.value = nextDensity
    }
  }

  return {
    accent,
    density,
    setAccent,
    setDensity
  }
}

export function getInitialPreferencesScript() {
  return `
    (function () {
      try {
        var accent = localStorage.getItem('${STORAGE_KEYS.accent}') || '${DEFAULTS.accent}';
        var density = localStorage.getItem('${STORAGE_KEYS.density}') || '${DEFAULTS.density}';
        document.documentElement.setAttribute('data-accent', accent);
        document.documentElement.setAttribute('data-density', density);
      } catch (e) {}
    })();
  `.trim()
}
