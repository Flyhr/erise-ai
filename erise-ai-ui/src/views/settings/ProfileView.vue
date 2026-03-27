<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>个人设置</h1>
        <div class="page-subtitle">这里集中管理头像、资料、主题和密码。自定义调色入口也保留在右上角头像菜单里。</div>
      </div>
    </div>

    <div class="grid-2">
      <el-card class="glass-card profile-card" shadow="never">
        <template #header>基础资料</template>
        <div class="profile-avatar-row">
          <el-avatar :src="profile.avatarUrl" :size="72">{{ initials }}</el-avatar>
          <div>
            <div class="profile-avatar-row__title">{{ profile.displayName || '未命名用户' }}</div>
            <div class="page-subtitle">支持直接填写头像 URL，顶部菜单会实时展示头像。</div>
          </div>
        </div>

        <el-form :model="profile" label-position="top">
          <el-form-item label="显示名称">
            <el-input v-model="profile.displayName" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profile.email" />
          </el-form-item>
          <el-form-item label="头像地址">
            <el-input v-model="profile.avatarUrl" placeholder="https://example.com/avatar.png" />
          </el-form-item>
          <el-form-item label="个人简介">
            <el-input v-model="profile.bio" type="textarea" :rows="4" />
          </el-form-item>
          <el-button type="primary" @click="saveProfile">保存资料</el-button>
        </el-form>
      </el-card>

      <div class="section-stack">
        <el-card class="glass-card" shadow="never">
          <template #header>主题外观</template>
          <div class="page-subtitle profile-theme-note">预设主题可在这里直接切换；如果要自定义主色、背景和卡片色，请使用右上角头像菜单中的“主题外观”。</div>
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
        </el-card>

        <el-card class="glass-card" shadow="never">
          <template #header>修改密码</template>
          <el-form :model="passwordForm" label-position="top">
            <el-form-item label="旧密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-button type="primary" plain @click="savePassword">更新密码</el-button>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { updatePassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { applyTheme, getStoredTheme, getThemePreview, themeOptions, type ThemeName } from '@/theme'

const authStore = useAuthStore()
const currentTheme = ref<ThemeName>(getStoredTheme())
const profile = reactive({
  displayName: authStore.user?.displayName || '',
  email: authStore.user?.email || '',
  avatarUrl: authStore.user?.avatarUrl || '',
  bio: authStore.user?.bio || '',
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
})

watch(
  () => authStore.user,
  (user) => {
    profile.displayName = user?.displayName || ''
    profile.email = user?.email || ''
    profile.avatarUrl = user?.avatarUrl || ''
    profile.bio = user?.bio || ''
  },
  { immediate: true },
)

const initials = computed(() => (profile.displayName || authStore.user?.username || 'U').slice(0, 1).toUpperCase())
const previewStyle = (themeName: ThemeName, index: number) => ({ background: getThemePreview(themeName)[index] })

const selectTheme = (themeName: ThemeName) => {
  currentTheme.value = themeName
  applyTheme(themeName)
}

const saveProfile = async () => {
  await authStore.updateProfile(profile)
  ElMessage.success('资料已更新')
}

const savePassword = async () => {
  await updatePassword(passwordForm)
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  ElMessage.success('密码已更新')
}
</script>

<style scoped>
.profile-card {
  padding-bottom: 8px;
}

.profile-avatar-row {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 18px;
}

.profile-avatar-row__title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.profile-theme-note {
  margin-bottom: 16px;
}
</style>