<template>
  <div class="section-stack">
    <div v-if="description" class="page-subtitle">{{ description }}</div>

    <div class="theme-grid">
      <button
        v-for="theme in themeOptions"
        :key="theme.name"
        type="button"
        class="theme-card"
        :class="{ 'is-active': theme.name === currentTheme }"
        @click="selectTheme(theme.name)"
      >
        <div class="theme-card__title">{{ theme.label }}</div>
        <div class="theme-card__desc">{{ theme.description }}</div>
        <div class="theme-card__preview">
          <span :style="previewStyle(theme.name, 0)" />
          <span :style="previewStyle(theme.name, 1)" />
          <span :style="previewStyle(theme.name, 2)" />
        </div>
      </button>
    </div>

    <div class="theme-custom-grid">
      <label class="theme-color-field">
        <span>主色</span>
        <el-color-picker v-model="customTheme.accent" />
      </label>
      <label class="theme-color-field">
        <span>画布</span>
        <el-color-picker v-model="customTheme.canvas" />
      </label>
      <label class="theme-color-field">
        <span>面板</span>
        <el-color-picker v-model="customTheme.surface" />
      </label>
    </div>

    <div class="theme-custom-preview app-card is-compact">
      <div class="app-card__body">
        <div class="theme-custom-preview__title">自定义预览</div>
        <div class="theme-custom-preview__swatches">
          <span :style="{ background: customTheme.accent }" />
          <span :style="{ background: customTheme.surface }" />
          <span :style="{ background: customTheme.canvas }" />
        </div>
      </div>
    </div>

    <div class="table-actions">
      <el-button @click="resetCustomTheme">重置</el-button>
      <el-button type="primary" @click="applyCustomTheme">应用自定义主题</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import {
  applyTheme,
  defaultCustomThemeColors,
  getCustomThemeColors,
  getStoredTheme,
  getThemePreview,
  themeOptions,
  updateCustomThemeColors,
  type ThemeName,
} from '@/theme'

defineProps<{
  description?: string
}>()

const currentTheme = ref<ThemeName>(getStoredTheme())
const customTheme = reactive(getCustomThemeColors())

const previewStyle = (themeName: ThemeName, index: number) => ({
  background: getThemePreview(themeName, { ...customTheme })[index],
})

const selectTheme = (themeName: ThemeName) => {
  if (themeName === 'custom') {
    applyCustomTheme()
    return
  }
  currentTheme.value = themeName
  applyTheme(themeName)
}

const applyCustomTheme = () => {
  Object.assign(customTheme, updateCustomThemeColors({ ...customTheme }))
  currentTheme.value = 'custom'
  applyTheme('custom')
}

const resetCustomTheme = () => {
  Object.assign(customTheme, defaultCustomThemeColors)
}
</script>