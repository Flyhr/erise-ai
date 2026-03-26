<template>
  <div class="section-stack">
    <div>
      <div style="font-size: 14px; letter-spacing: 0.2em; color: var(--muted)">ERISE-AI V1</div>
      <h1 style="margin: 10px 0 8px">项目知识与 AI 主链路</h1>
      <p class="page-subtitle">登录后可以直接创建项目、上传文件、编辑文档、搜索知识并发起带引用问答。</p>
    </div>

    <el-tabs v-model="mode" stretch>
      <el-tab-pane label="登录" name="login" />
      <el-tab-pane label="注册" name="register" />
    </el-tabs>

    <el-form :model="form" label-position="top" @submit.prevent>
      <el-form-item label="用户名">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item v-if="mode === 'register'" label="显示名称">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item v-if="mode === 'register'" label="邮箱">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password @keyup.enter="submit" />
      </el-form-item>
      <el-form-item label="验证码">
        <div style="display: grid; grid-template-columns: 1fr 128px; gap: 12px; align-items: center">
          <el-input v-model="form.captchaCode" maxlength="4" />
          <button
            type="button"
            style="border: 1px solid var(--line); background: white; border-radius: 12px; padding: 0; overflow: hidden; cursor: pointer"
            @click="loadCaptcha"
          >
            <img :src="captcha.captchaImage" alt="captcha" style="display: block; width: 100%; height: 44px; object-fit: cover" />
          </button>
        </div>
      </el-form-item>
      <el-button class="full-width" type="primary" size="large" :loading="submitting" @click="submit">
        {{ mode === 'login' ? '登录系统' : '注册并进入' }}
      </el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, register } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const captcha = reactive({ captchaId: '', captchaImage: '' })
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  password: '',
  captchaCode: '',
})

const loadCaptcha = async () => {
  Object.assign(captcha, await getCaptcha())
}

const submit = async () => {
  if (!captcha.captchaId) {
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await authStore.login({
        username: form.username,
        password: form.password,
        captchaId: captcha.captchaId,
        captchaCode: form.captchaCode,
      })
    } else {
      const session = await register({
        username: form.username,
        email: form.email,
        displayName: form.displayName,
        password: form.password,
        captchaId: captcha.captchaId,
        captchaCode: form.captchaCode,
      })
      authStore.applySession(session)
    }
    ElMessage.success('登录成功')
    router.push(authStore.isAdmin ? '/admin' : '/workspace')
  } finally {
    submitting.value = false
    form.captchaCode = ''
    loadCaptcha()
  }
}

onMounted(loadCaptcha)
</script>
