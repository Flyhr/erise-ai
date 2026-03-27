<template>
  <div class="auth-stack">
    <div>
      <div class="auth-eyebrow">ERISE-AI V1</div>
      <h1 class="auth-title">把项目知识、文档协作和 AI 助手放在同一个入口</h1>
      <p class="page-subtitle">
        登录后可以创建项目、上传文件、在线编辑文档、沉淀结构化内容，并基于项目知识发起带引用的 AI 问答。
      </p>
    </div>

    <el-tabs v-model="mode" stretch>
      <el-tab-pane label="登录" name="login" />
      <el-tab-pane label="注册" name="register" />
    </el-tabs>

    <el-form :model="form" label-position="top" @submit.prevent>
      <el-form-item label="用户名">
        <el-input v-model="form.username" autocomplete="username" />
      </el-form-item>
      <el-form-item v-if="mode === 'register'" label="显示名称">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item v-if="mode === 'register'" label="邮箱">
        <el-input v-model="form.email" autocomplete="email" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password autocomplete="current-password" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item label="验证码">
        <div class="captcha-row">
          <el-input v-model="form.captchaCode" maxlength="4" @keyup.enter="submit" />
          <button class="captcha-button" type="button" @click="loadCaptcha">
            <img :src="captcha.captchaImage" alt="captcha" class="captcha-image" />
          </button>
        </div>
      </el-form-item>
      <el-button class="full-width" type="primary" size="large" :loading="submitting" @click="submit">
        {{ mode === 'login' ? '登录系统' : '注册并进入工作台' }}
      </el-button>
    </el-form>

    <div class="auth-footnote">
      管理员登录后也会先进入普通工作台，右上角可随时进入管理后台。
    </div>
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
    router.push('/workspace')
  } finally {
    submitting.value = false
    form.captchaCode = ''
    void loadCaptcha()
  }
}

onMounted(loadCaptcha)
</script>

<style scoped>
.auth-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.auth-eyebrow {
  font-size: 14px;
  letter-spacing: 0.2em;
  color: var(--muted);
}

.auth-title {
  margin: 10px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
  letter-spacing: -0.04em;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: 12px;
  align-items: center;
}

.captcha-button {
  border: 1px solid var(--line);
  background: var(--surface-strong);
  border-radius: 12px;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
}

.captcha-image {
  display: block;
  width: 100%;
  height: 44px;
  object-fit: cover;
}

.auth-footnote {
  font-size: 13px;
  color: var(--muted);
  line-height: 1.7;
}
</style>