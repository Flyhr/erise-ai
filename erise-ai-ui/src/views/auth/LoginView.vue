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

            <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" @submit.prevent>
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model.trim="form.username"
                  autocomplete="username"
                  :maxlength="mode === 'register' ? 20 : 64"
                  placeholder="请输入用户名"
                />
              </el-form-item>

              <el-form-item v-if="mode === 'register'" label="邮箱" prop="email">
                <el-input
                  v-model.trim="form.email"
                  autocomplete="email"
                  maxlength="128"
                  placeholder="请输入邮箱地址"
                />
              </el-form-item>

              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  show-password
                  :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                  placeholder="••••••••"
                  @keyup.enter="submit"
                />
              </el-form-item>

              <el-form-item label="验证码" prop="captchaCode">
                <div class="captcha-row">
                  <el-input v-model="form.captchaCode" maxlength="4" placeholder="4位验证码" @keyup.enter="submit" />
                  <button class="captcha-button" type="button" @click="loadCaptcha">
                    <img :src="captcha.captchaImage" alt="验证码" class="captcha-image" />
                  </button>
                </div>
              </el-form-item>

              <div v-if="mode === 'register'" class="register-hints">
                <strong>注册要求</strong>
                <span>用户名 4-20 位且不能包含空格，密码 6-20 位，验证码需填写 4 位。</span>
              </div>

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
          </div>

          <div class="login-footer">© 2026 ERISE AI 知识库 · 保留所有权利</div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getCaptcha, register } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/utils/formatters'
import ThirdPartyLogin from '@/components/common/ThirdPartyLogin.vue'

const authStore = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const errorText = ref('')
const formRef = ref<FormInstance>()
const captcha = reactive({ captchaId: '', captchaImage: '' })
const form = reactive({
  username: '',
  email: '',
  password: '',
  captchaCode: ''
})

const usernameRegisterRule = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalized = (value || '').trim()
  if (!normalized) {
    callback(new Error('请输入用户名'))
    return
  }
  if (mode.value === 'register') {
    if (normalized.includes(' ')) {
      callback(new Error('用户名不能包含空格'))
      return
    }
    if (normalized.length < 4 || normalized.length > 20) {
      callback(new Error('用户名长度需为 4-20 位'))
      return
    }
  }
  callback()
}

const emailRule = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalized = (value || '').trim()
  if (!normalized) {
    callback(new Error('请输入邮箱地址'))
    return
  }
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailPattern.test(normalized)) {
    callback(new Error('请输入正确的邮箱地址'))
    return
  }
  callback()
}

const passwordRule = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入密码'))
    return
  }
  if (mode.value === 'register' && (value.length < 6 || value.length > 20)) {
    callback(new Error('密码长度需为 6-20 位'))
    return
  }
  callback()
}

const captchaRule = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalized = (value || '').trim()
  if (!normalized) {
    callback(new Error('请输入验证码'))
    return
  }
  if (normalized.length !== 4) {
    callback(new Error('请输入4位验证码'))
    return
  }
  callback()
}

const formRules = computed<FormRules>(() => ({
  username: [{ validator: usernameRegisterRule, trigger: ['blur', 'change'] }],
  email: [{ validator: emailRule, trigger: ['blur', 'change'] }],
  password: [{ validator: passwordRule, trigger: ['blur', 'change'] }],
  captchaCode: [{ validator: captchaRule, trigger: ['blur', 'change'] }],
}))

const mapAuthErrorMessage = (error: unknown) => {
  const rawMessage = resolveErrorMessage(
    error,
    mode.value === 'login' ? '暂时无法登录，请稍后重试。' : '暂时无法完成注册，请稍后重试。',
  )
  const mappings: Array<[RegExp, string]> = [
    [/invalid captcha/i, '验证码错误或已过期，请重新输入'],
    [/username already exists/i, '用户名已存在，请更换后重试'],
    [/email .*exists/i, '邮箱已被使用，请更换后重试'],
    [/duplicate entry .*uk_ea_user_email/i, '邮箱已被使用，请更换后重试'],
    [/duplicate entry .*uk_ea_user_username/i, '用户名已存在，请更换后重试'],
    [/invalid username or password/i, '用户名或密码错误，请重新输入'],
    [/user is disabled/i, '账号已被禁用，请联系管理员'],
  ]
  const matched = mappings.find(([pattern]) => pattern.test(rawMessage))
  return matched ? matched[1] : rawMessage
}

const loadCaptcha = async () => {
  try {
    const data = await getCaptcha()
    Object.assign(captcha, data)
  } catch (error) {
    errorText.value = '验证码加载失败，请刷新页面后重试'
    console.error('验证码加载失败', error)
  }
}

const submit = async () => {
  if (!captcha.captchaId) {
    ElMessage.warning('请稍后，验证码加载中')
    return
  }

  errorText.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    errorText.value = mode.value === 'login' ? '请先完善登录信息。' : '请先按提示完善注册信息。'
    return
  }
  submitting.value = true

  try {
    if (mode.value === 'login') {
      // 1. 登录逻辑：调用Pinia仓库的登录方法
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
        password: form.password,
        captchaId: captcha.captchaId,
        captchaCode: form.captchaCode,
      })
      authStore.applySession(session)
      ElMessage.success('注册成功')
    }
    // Force a fresh app bootstrap after auth so stale dev-module caches
    // cannot break the first post-login lazy route load.
    window.location.assign(authStore.isAdmin ? '/admin' : '/workspace')
  } catch (error) {
    errorText.value = mapAuthErrorMessage(error)
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

watch(mode, async () => {
  errorText.value = ''
  await nextTick()
  formRef.value?.clearValidate()
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');

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

.register-hints {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: -4px 0 18px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.register-hints strong {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.register-hints span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
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
