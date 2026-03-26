<template>
  <div class="grid-2">
    <el-card class="glass-card" shadow="never">
      <template #header>个人信息</template>
      <el-form :model="profile" label-position="top">
        <el-form-item label="显示名称">
          <el-input v-model="profile.displayName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profile.email" />
        </el-form-item>
        <el-form-item label="头像地址">
          <el-input v-model="profile.avatarUrl" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="profile.bio" type="textarea" :rows="4" />
        </el-form-item>
        <el-button type="primary" @click="saveProfile">保存资料</el-button>
      </el-form>
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
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { updatePassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const profile = reactive({
  displayName: authStore.user?.displayName || '',
  email: authStore.user?.email || '',
  avatarUrl: '',
  bio: '',
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
})

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
