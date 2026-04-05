export type ThemeName = 'sunrise' | 'midnight' | 'eyecare' | 'graphite' | 'custom'
export type ThemeScheme = 'light' | 'dark'

export interface ThemeOption {
  name: ThemeName
  label: string
  description: string
  swatches: [string, string, string]
}

export interface CustomThemeColors {
  accent: string
  surface: string
  canvas: string
}

interface ThemePreset {
  label: string
  description: string
  accent: string
  surface: string
  canvas: string
  scheme: ThemeScheme
}

const THEME_KEY = 'erise-theme'
const CUSTOM_THEME_KEY = 'erise-theme-custom'
const validThemes: ThemeName[] = ['sunrise', 'midnight', 'eyecare', 'graphite', 'custom']
const legacyThemeMap: Record<string, ThemeName> = {
  olive: 'eyecare',
  ocean: 'sunrise',
  graphite: 'graphite',
}

const themePresets: Record<Exclude<ThemeName, 'custom'>, ThemePreset> = {
  sunrise: {
    label: '晨曦蓝',
    description: '默认的企业工作台风格，明亮克制，适合日常办公与知识协作。',
    accent: '#2358d6',
    surface: '#ffffff',
    canvas: '#f2f5fb',
    scheme: 'light',
  },
  midnight: {
    label: '夜幕蓝',
    description: '深色工作环境，适合长时间浏览、审阅和夜间使用。',
    accent: '#7dd3fc',
    surface: '#0f172a',
    canvas: '#040814',
    scheme: 'dark',
  },
  eyecare: {
    label: '护眼绿',
    description: '降低对比刺激，适合高频阅读文档和长时间整理资料。',
    accent: '#4d7c0f',
    surface: '#fbf8ef',
    canvas: '#eef2df',
    scheme: 'light',
  },
  graphite: {
    label: '石墨灰',
    description: '偏理性的中性商务风格，强调密度和信息可扫读性。',
    accent: '#334155',
    surface: '#ffffff',
    canvas: '#eef2f7',
    scheme: 'light',
  },
}

export const defaultCustomThemeColors: CustomThemeColors = {
  accent: '#b45309',
  surface: '#fffaf2',
  canvas: '#f5efe5',
}

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const normalizeHex = (value: string) => {
  const hex = value.trim().replace('#', '')
  if (hex.length === 3) {
    return `#${hex
      .split('')
      .map((item) => item + item)
      .join('')}`
  }
  if (hex.length === 6) {
    return `#${hex}`
  }
  return '#2358d6'
}

const hexToRgb = (value: string) => {
  const hex = normalizeHex(value).slice(1)
  return [0, 2, 4].map((index) => Number.parseInt(hex.slice(index, index + 2), 16)) as [number, number, number]
}

const rgbToHex = (rgb: [number, number, number]) =>
  `#${rgb
    .map((item) => clamp(Math.round(item), 0, 255).toString(16).padStart(2, '0'))
    .join('')}`

const mix = (from: string, to: string, ratio: number) => {
  const [r1, g1, b1] = hexToRgb(from)
  const [r2, g2, b2] = hexToRgb(to)
  const amount = clamp(ratio, 0, 1)
  return rgbToHex([
    r1 + (r2 - r1) * amount,
    g1 + (g2 - g1) * amount,
    b1 + (b2 - b1) * amount,
  ] as [number, number, number])
}

const alpha = (value: string, opacity: number) => {
  const [r, g, b] = hexToRgb(value)
  return `rgba(${r}, ${g}, ${b}, ${clamp(opacity, 0, 1)})`
}

const luminance = (value: string) => {
  const [r, g, b] = hexToRgb(value).map((item) => {
    const channel = item / 255
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

const pickTextColor = (background: string) => (luminance(background) > 0.42 ? '#152022' : '#f8fafc')
const resolveCustomScheme = (colors: CustomThemeColors): ThemeScheme =>
  luminance(mix(colors.surface, colors.canvas, 0.5)) > 0.34 ? 'light' : 'dark'

const buildThemeVariables = (colors: CustomThemeColors, scheme: ThemeScheme) => {
  const accent = normalizeHex(colors.accent)
  const surface = normalizeHex(colors.surface)
  const canvas = normalizeHex(colors.canvas)
  const text = pickTextColor(surface)
  const secondaryText = mix(text, canvas, scheme === 'dark' ? 0.22 : 0.48)
  const muted = mix(text, canvas, scheme === 'dark' ? 0.34 : 0.58)
  const accentSoft = mix(accent, surface, scheme === 'dark' ? 0.78 : 0.72)
  const accentStrong = mix(accent, scheme === 'dark' ? '#ffffff' : '#0f172a', scheme === 'dark' ? 0.12 : 0.18)
  const fillLight = mix(surface, canvas, scheme === 'dark' ? 0.18 : 0.32)
  const bgAlt = mix(surface, canvas, scheme === 'dark' ? 0.4 : 0.58)
  const border = alpha(text, scheme === 'dark' ? 0.18 : 0.12)
  const borderLight = alpha(text, scheme === 'dark' ? 0.12 : 0.08)
  const sidebar = mix(surface, canvas, scheme === 'dark' ? 0.08 : 0.24)

  return {
    '--bg': canvas,
    '--bg-alt': bgAlt,
    '--card': alpha(surface, scheme === 'dark' ? 0.88 : 0.9),
    '--line': border,
    '--text': text,
    '--muted': muted,
    '--accent': accent,
    '--accent-soft': accentSoft,
    '--accent-strong': accentStrong,
    '--panel': alpha(accent, scheme === 'dark' ? 0.18 : 0.08),
    '--header': alpha(surface, scheme === 'dark' ? 0.82 : 0.78),
    '--danger': scheme === 'dark' ? '#fb7185' : '#be123c',
    '--warning': scheme === 'dark' ? '#fbbf24' : '#b45309',
    '--success': scheme === 'dark' ? '#86efac' : '#15803d',
    '--surface-soft': alpha(surface, scheme === 'dark' ? 0.74 : 0.66),
    '--surface-strong': alpha(surface, scheme === 'dark' ? 0.92 : 0.84),
    '--shadow-color': scheme === 'dark' ? 'rgba(3, 7, 18, 0.42)' : 'rgba(15, 23, 42, 0.08)',
    '--sidebar': sidebar,
    '--sidebar-ink': text,
    '--el-color-primary': accent,
    '--el-color-primary-dark-2': accentStrong,
    '--el-color-primary-light-3': mix(accent, '#ffffff', 0.2),
    '--el-color-primary-light-5': mix(accent, '#ffffff', 0.36),
    '--el-color-primary-light-8': mix(accent, '#ffffff', 0.7),
    '--el-bg-color': surface,
    '--el-bg-color-overlay': surface,
    '--el-bg-color-page': canvas,
    '--el-fill-color-light': fillLight,
    '--el-fill-color-blank': surface,
    '--el-fill-color': mix(surface, canvas, scheme === 'dark' ? 0.12 : 0.2),
    '--el-text-color-primary': text,
    '--el-text-color-regular': secondaryText,
    '--el-text-color-secondary': muted,
    '--el-border-color': border,
    '--el-border-color-light': borderLight,
    '--el-menu-active-color': accent,
    '--el-mask-color': scheme === 'dark' ? 'rgba(2, 6, 23, 0.72)' : 'rgba(15, 23, 42, 0.45)',
    '--radius-xs': '10px',
    '--radius-sm': '14px',
    '--radius-md': '18px',
    '--radius-lg': '24px',
    '--radius-xl': '32px',
    '--content-width': '1320px',
  }
}

const resolveThemeColors = (themeName: ThemeName) => {
  if (themeName === 'custom') {
    const customTheme = getCustomThemeColors()
    return {
      colors: customTheme,
      scheme: resolveCustomScheme(customTheme),
    }
  }

  const preset = themePresets[themeName]
  return {
    colors: {
      accent: preset.accent,
      surface: preset.surface,
      canvas: preset.canvas,
    },
    scheme: preset.scheme,
  }
}

export const themeOptions: ThemeOption[] = [
  ...Object.entries(themePresets).map(([name, preset]) => ({
    name: name as ThemeName,
    label: preset.label,
    description: preset.description,
    swatches: [preset.accent, preset.surface, preset.canvas] as [string, string, string],
  })),
  {
    name: 'custom',
    label: '自定义主题',
    description: '自行调整主色、面板和画布颜色。',
    swatches: [defaultCustomThemeColors.accent, defaultCustomThemeColors.surface, defaultCustomThemeColors.canvas],
  },
]

export const getStoredTheme = (): ThemeName => {
  const stored = localStorage.getItem(THEME_KEY)
  const mapped = stored ? legacyThemeMap[stored] || stored : 'sunrise'
  return validThemes.includes(mapped as ThemeName) ? (mapped as ThemeName) : 'sunrise'
}

export const getCustomThemeColors = (): CustomThemeColors => {
  const stored = localStorage.getItem(CUSTOM_THEME_KEY)
  if (!stored) {
    return { ...defaultCustomThemeColors }
  }
  try {
    const parsed = JSON.parse(stored) as Partial<CustomThemeColors>
    return {
      accent: normalizeHex(parsed.accent || defaultCustomThemeColors.accent),
      surface: normalizeHex(parsed.surface || defaultCustomThemeColors.surface),
      canvas: normalizeHex(parsed.canvas || defaultCustomThemeColors.canvas),
    }
  } catch {
    return { ...defaultCustomThemeColors }
  }
}

export const updateCustomThemeColors = (payload: CustomThemeColors) => {
  const normalized = {
    accent: normalizeHex(payload.accent),
    surface: normalizeHex(payload.surface),
    canvas: normalizeHex(payload.canvas),
  }
  localStorage.setItem(CUSTOM_THEME_KEY, JSON.stringify(normalized))
  return normalized
}

export const getThemePreview = (themeName: ThemeName, customColors?: CustomThemeColors) => {
  if (themeName === 'custom') {
    const colors = customColors || getCustomThemeColors()
    return [colors.accent, colors.surface, colors.canvas] as [string, string, string]
  }
  return themeOptions.find((item) => item.name === themeName)?.swatches || themeOptions[0].swatches
}

export const applyTheme = (themeName: ThemeName) => {
  const theme = resolveThemeColors(themeName)
  const variables = buildThemeVariables(theme.colors, theme.scheme)
  Object.entries(variables).forEach(([key, value]) => {
    document.documentElement.style.setProperty(key, value)
  })
  document.documentElement.dataset.theme = themeName
  document.documentElement.style.colorScheme = theme.scheme
  localStorage.setItem(THEME_KEY, themeName)
}

export const initTheme = () => {
  applyTheme(getStoredTheme())
}
