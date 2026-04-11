<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-aside">
        <div class="brand-mark">
          <span class="material-symbols-outlined brand-mark__icon">auto_awesome</span>
        </div>
        <h1 class="login-aside__title">Erise AI 知识库</h1>
        <p class="login-aside__desc">
          AI 赋予无限可能，开启高效智能知识库管理
        </p>
      </aside>

      <main class="login-main">
        <div class="login-card-wrap">
          <div class="brand-mobile">
            <div class="brand-mark brand-mark--small">
              <span class="material-symbols-outlined brand-mark__icon brand-mark__icon--small">auto_awesome</span>
            </div>
            <h1 class="brand-mobile__title">Erise AI 知识库</h1>
          </div>

          <div class="login-card">
            <div class="tabs-wrap">
              <el-tabs v-model="mode" stretch>
                <el-tab-pane label="登录" name="login" />
                <el-tab-pane label="注册" name="register" />
              </el-tabs>
            </div>

            <el-alert v-if="errorText" :title="errorText" type="error" show-icon :closable="false"
              class="error-alert" />

            <el-form :model="form" label-position="top" @submit.prevent>
              <el-form-item label="用户名">
                <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
              </el-form-item>

              <!-- <el-form-item v-if="mode === 'register'" label="显示名称">
                <el-input v-model="form.displayName" placeholder="您的称呼" />
              </el-form-item> -->

              <el-form-item v-if="mode === 'register'" label="邮箱">
                <el-input v-model="form.email" autocomplete="email" placeholder="example@domain.com" />
              </el-form-item>

              <el-form-item label="密码">
                <el-input v-model="form.password" type="password" show-password autocomplete="current-password"
                  placeholder="••••••••" @keyup.enter="submit" />
              </el-form-item>

              <el-form-item label="验证码">
                <div class="captcha-row">
                  <el-input v-model="form.captchaCode" maxlength="4" placeholder="4位验证码" @keyup.enter="submit" />
                  <button class="captcha-button" type="button" @click="loadCaptcha">
                    <img :src="captcha.captchaImage" alt="captcha" class="captcha-image" />
                  </button>
                </div>
              </el-form-item>

              <div v-if="mode === 'login'" class="login-extra">
                <el-checkbox size="small" @change="handleFeatureInDevelopment">记住我</el-checkbox>
                <a href="javascript:void(0)" class="extra-link" @click="handleFeatureInDevelopment">
                  忘记密码?
                </a>
              </div>

              <el-button class="submit-button" type="primary" size="large" :loading="submitting" @click="submit">
                {{ mode === 'login' ? '立即登录' : '注册并进入知识库' }}
              </el-button>
            </el-form>
            <ThirdPartyLogin />
            <!-- <div class="divider">
              <div class="divider__line"></div>
              <span class="divider__text">第三方快捷登录</span>
            </div> -->

            <!-- <div class="social-row">
              <button class="social-button" @click="handleFeatureInDevelopment">
                <svg class="social-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M8.22 5.06c.1.01.2.02.3.04l-.11-.04c-.06-.02-.13-.04-.19-.05zm.88 11.23c.03-.1.06-.2.1-.3l-.1.3zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm6.27 12.33c-.09.68-.42 1.25-.97 1.69-.53.43-1.18.64-1.92.64-.53 0-1.02-.11-1.46-.32-.43-.2-.79-.49-1.05-.85l-.17-.23c-.3.4-.69.72-1.16.96-.46.24-1 .36-1.57.36-.78 0-1.45-.23-1.99-.7-.52-.46-.82-1.07-.88-1.8h-.02c-.05-.59.1-1.14.44-1.61.34-.47.81-.8 1.41-1.02-.4-.25-.71-.58-.92-.98-.21-.4-.32-.86-.32-1.37 0-.78.25-1.43.76-1.96s1.17-.79 1.99-.79c.67 0 1.24.16 1.7.47.45.31.78.71 1 1.2.22-.49.56-.89 1.01-1.2.45-.31 1.01-.47 1.67-.47.83 0 1.5.26 2.01.79.51.53.76 1.18.76 1.96 0 .53-.11.99-.33 1.39-.21.4-.53.73-.95.98.61.22 1.09.56 1.44 1.03.35.47.51 1.01.48 1.61h-.02z" />
                </svg>
              </button>
              <button class="social-button" @click="handleFeatureInDevelopment">
                <svg class="social-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.31 14.69c-.43.43-.98.71-1.63.85-.65.14-1.32.14-1.97 0-.65-.14-1.2-.42-1.63-.85-.43-.43-.71-.98-.85-1.63-.14-.65-.14-1.32 0-1.97.14-.65.42-.85 1.63-.85.65-.14 1.32-.14 1.97 0 .65.14 1.2.42 1.63.85.43.43.71.98.85 1.63.14.65.14 1.32 0 1.97-.14.65-.42 1.2-.85 1.63z" />
                </svg>
              </button>
            </div> -->
          </div>

          <div class="login-footer">© 2026 ERISE AI 知识库. ALL RIGHTS RESERVED.</div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, register } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/utils/formatters'
import ThirdPartyLogin from '@/components/common/ThirdPartyLogin.vue'
const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const errorText = ref('')
const captcha = reactive({ captchaId: '', captchaImage: '' })
const form = reactive({
  username: '',
  // displayName: '',
  email: '',
  password: '',
  captchaCode: ''
})

const loadCaptcha = async () => {
  try {
    const data = await getCaptcha()
    Object.assign(captcha, data)
  } catch (error) {
    console.error('验证码加载失败', error)
  }
}

const submit = async () => {
  if (!captcha.captchaId) {
    ElMessage.warning('请稍后，验证码加载中')
    return
  }

  errorText.value = ''
  submitting.value = true

  try {
    if (mode.value === 'login') {
      await authStore.login({
        username: form.username,
        password: form.password,
        captchaId: captcha.captchaId,
        captchaCode: form.captchaCode,
      })
      ElMessage.success('登录成功')
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
      ElMessage.success('注册成功')
    }
    await router.push('/workspace')
  } catch (error) {
    errorText.value = resolveErrorMessage(error, '暂时无法登录，请稍后重试。')
  } finally {
    submitting.value = false
    form.captchaCode = ''
    await loadCaptcha()
  }
}

const handleFeatureInDevelopment = () => {
  ElMessage.info('正在开发中')
}

onMounted(() => {
  loadCaptcha()
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap');

body {
  font-family: 'Plus Jakarta Sans', sans-serif;
  background: #f8fafc;
}

.login-page {
  min-height: 100vh;
  background: #f8fafc;
}

.login-shell {
  min-height: 100vh;
  display: flex;
}

.login-aside {
  width: 40%;
  min-width: 380px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  border-right: 1px solid #eef2f7;
  background:
    radial-gradient(circle at top, rgba(99, 102, 241, 0.10), transparent 34%),
    linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
}

.login-aside__title {
  margin: 0 0 16px;
  font-size: 48px;
  line-height: 1.1;
  font-weight: 800;
  color: #0f172a;
  text-align: center;
}

.login-aside__desc {
  max-width: 360px;
  margin: 0;
  text-align: center;
  color: #64748b;
  font-size: 18px;
  line-height: 1.8;
}

.brand-mark {
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.25);
}

.brand-mark__icon {
  font-size: 48px;
  font-variation-settings: 'FILL' 1;
}

.brand-mark--small {
  width: 52px;
  height: 52px;
  margin-bottom: 14px;
  border-radius: 16px;
}

.brand-mark__icon--small {
  font-size: 30px;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: #ffffff;
}

.login-card-wrap {
  width: 100%;
  max-width: 460px;
}

.brand-mobile {
  display: none;
  margin-bottom: 24px;
  text-align: center;
  align-items: center;
  flex-direction: column;
}

.brand-mobile__title {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}

.login-card {
  padding: 32px;
  border: 1px solid #eef2f7;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.05), 0 10px 10px -5px rgba(15, 23, 42, 0.03);
}

.tabs-wrap {
  margin-bottom: 24px;
}

.error-alert {
  margin-bottom: 24px;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-row :deep(.el-input) {
  flex: 1;
}

.captcha-button {
  width: 120px;
  height: 40px;
  padding: 0;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  background: #f8fafc;
}

.captcha-button:hover {
  opacity: 0.85;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.login-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 24px;
}

.extra-link {
  color: #6366f1;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.extra-link:hover {
  color: #4f46e5;
}

.submit-button {
  width: 100%;
}

.divider {
  position: relative;
  margin: 32px 0 24px;
  text-align: center;
}

.divider__line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: #eef2f7;
}

.divider__text {
  position: relative;
  display: inline-block;
  padding: 0 16px;
  background: #fff;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.social-row {
  display: flex;
  justify-content: center;
  gap: 20px;
}

.social-button {
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s ease;
}

.social-button:hover {
  transform: translateY(-1px);
  background: #f1f5f9;
}

.social-icon {
  width: 20px;
  height: 20px;
  fill: currentColor;
}

.login-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.08em;
}

.el-button--primary {
  --el-button-bg-color: #6366f1;
  --el-button-border-color: #6366f1;
  --el-button-hover-bg-color: #4f46e5;
  --el-button-hover-border-color: #4f46e5;
  border-radius: 10px;
  font-weight: 700;
}

.el-input__wrapper {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background-color: #ffffff;
}

.el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #6366f1 inset !important;
}

.el-tabs__item.is-active {
  color: #6366f1;
  font-weight: 700;
}

.el-tabs__active-bar {
  background-color: #6366f1;
}

.el-form-item__label {
  margin-bottom: 6px !important;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 992px) {
  .login-aside {
    display: none;
  }

  .brand-mobile {
    display: flex;
  }

  .login-main {
    min-height: 100vh;
    background:
      radial-gradient(circle at top, rgba(99, 102, 241, 0.08), transparent 26%),
      #f8fafc;
  }
}

@media (max-width: 576px) {
  .login-main {
    padding: 16px;
  }

  .login-card {
    padding: 22px 18px;
    border-radius: 20px;
  }

  .captcha-row {
    gap: 8px;
  }

  .captcha-button {
    width: 104px;
  }

  .brand-mobile__title {
    font-size: 26px;
  }
}
</style>
