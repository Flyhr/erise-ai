<template>
  <div class="architect-profile-page">
    <nav class="top-nav">
      <div class="top-nav__left">
        <span class="top-nav__brand">个人信息</span>
        <div class="top-nav__links">
          <!-- <button type="button" class="top-nav__link" @click="handlePlaceholder()">Dashboard</button>
          <button type="button" class="top-nav__link" @click="handlePlaceholder()">Projects</button>
          <button type="button" class="top-nav__link" @click="handlePlaceholder()">Team</button>
          <button type="button" class="top-nav__link" @click="handlePlaceholder()">Assets</button> -->
        </div>
      </div>

      <div class="top-nav__right">
        <button type="button" class="top-icon-btn" @click="handlePlaceholder()">
          <span class="material-symbols-outlined">notifications</span>
          <span class="top-icon-btn__dot"></span>
        </button>
        <button type="button" class="top-icon-btn" @click="handlePlaceholder()">
          <span class="material-symbols-outlined">settings</span>
        </button>
        <div class="top-nav__divider"></div>
        <button type="button" class="user-trigger" @click="handlePlaceholder()">
          <img class="user-trigger__avatar" :src="resolvedAvatarUrl" alt="User avatar" />
          <span class="material-symbols-outlined user-trigger__icon">expand_more</span>
        </button>
      </div>
    </nav>

    <div class="page-shell">
      <aside class="sidebar">
        <div class="sidebar__profile">
          <div class="sidebar__avatar-wrap">
            <img class="sidebar__avatar" :src="resolvedAvatarUrl" alt="Architect profile" />
            <div class="sidebar__online"></div>
          </div>
          <div>
            <h3 class="sidebar__name">{{ profile.displayName || authStore.user?.displayName || 'Project Lead' }}</h3>
            <p class="sidebar__role">Admin Account</p>
          </div>
        </div>

        <nav class="sidebar__nav">
          <ul>
            <li>
              <button type="button" class="sidebar__item" :class="{ 'is-active': activeTab === 'profile' }"
                @click="activeTab = 'profile'">
                <span class="material-symbols-outlined">person</span>
                <span>个人信息</span>
              </button>
            </li>
            <li>
              <button type="button" class="sidebar__item" :class="{ 'is-active': activeTab === 'security' }"
                @click="activeTab = 'security'">
                <span class="material-symbols-outlined">security</span>
                <span>账号安全</span>
              </button>
            </li>
            <li>
              <button type="button" class="sidebar__item" @click="handlePlaceholder()">
                <span class="material-symbols-outlined">palette</span>
                <span>主题</span>
              </button>
            </li>
            <li>
              <button type="button" class="sidebar__item" @click="handlePlaceholder()">
                <span class="material-symbols-outlined">api</span>
                <span>API配置</span>
              </button>
            </li>
          </ul>
        </nav>

        <div class="sidebar__footer">
          <button type="button" class="portfolio-btn" @click="handlePlaceholder()">
            <span class="material-symbols-outlined">open_in_new</span>
            <span>View Portfolio</span>
          </button>
        </div>
      </aside>

      <main class="main-canvas">
        <div class="main-inner">
          <template v-if="activeTab === 'profile'">
            <!-- <header class="page-header"> -->
            <!-- <h1 class="page-header__title">个人信息</h1> -->
            <!-- <p class="page-header__desc">Manage your public profile and personal identity within the Architectural
                  Admin ecosystem.</p> -->
            <!-- </header> -->

            <section class="profile-card">
              <div class="profile-grid">
                <div class="avatar-panel">
                  <button type="button" class="avatar-upload" @click="handlePlaceholder()">
                    <img class="avatar-upload__image" :src="resolvedAvatarUrl" alt="User profile large" />
                    <div class="avatar-upload__mask">
                      <span class="material-symbols-outlined">photo_camera</span>
                      <span>Update Photo</span>
                    </div>
                  </button>
                  <div class="avatar-panel__meta">
                    <p class="avatar-panel__title">Admin Profile</p>
                    <p class="avatar-panel__hint">Recommended: 800x800px</p>
                  </div>
                </div>

                <div class="form-panel">
                  <div class="field-group">
                    <label class="field-label field-label--lock">
                      <span>账号</span>
                      <span class="material-symbols-outlined field-label__lock">lock</span>
                    </label>
                    <div class="readonly-field">{{ authStore.user?.username || 'architect_lead_2024' }}</div>
                  </div>

                  <div class="field-group">
                    <label class="field-label">昵称</label>
                    <input v-model="profile.displayName" class="text-field" type="text" placeholder="Project Lead" />
                  </div>

                  <div class="field-group">
                    <label class="field-label">邮箱</label>
                    <input v-model="profile.email" class="text-field" type="email"
                      placeholder="lead@architect-admin.io" />
                  </div>

                  <div class="field-group">
                    <label class="field-label">头像地址</label>
                    <input v-model="profile.avatarUrl" class="text-field" type="url"
                      placeholder="https://example.com/avatar.png" />
                  </div>

                  <div class="field-group">
                    <label class="field-label">简介</label>
                    <textarea v-model="profile.bio" class="text-area" rows="5" maxlength="500"
                      placeholder="Write a brief introduction about your role and expertise..."></textarea>
                    <p class="field-counter">{{ bioLength }} / 500 characters</p>
                  </div>

                  <div class="actions-row">
                    <button type="button" class="btn btn--ghost" @click="resetProfile">Cancel</button>
                    <button type="button" class="btn btn--primary" @click="saveProfile">Save Changes</button>
                  </div>
                </div>
              </div>
            </section>

            <section class="status-grid">
              <div class="status-card">
                <div class="status-card__left">
                  <div class="status-card__icon status-card__icon--primary">
                    <span class="material-symbols-outlined">verified_user</span>
                  </div>
                  <div>
                    <p class="status-card__title">Identity Verification</p>
                    <p class="status-card__desc">Validated via Enterprise SSO</p>
                  </div>
                </div>
                <span class="status-chip status-chip--success">Verified</span>
              </div>

              <div class="status-card">
                <div class="status-card__left">
                  <div class="status-card__icon status-card__icon--secondary">
                    <span class="material-symbols-outlined">key</span>
                  </div>
                  <div>
                    <p class="status-card__title">Last Login</p>
                    <p class="status-card__desc">{{ lastLoginText }}</p>
                  </div>
                </div>
                <span class="status-chip status-chip--neutral">Secure</span>
              </div>
            </section>
          </template>

          <template v-else>
            <header class="page-header">
              <h1 class="page-header__title">账号安全</h1>
              <p class="page-header__desc">Manage your authentication credentials with the same design language as the
                provided layout.</p>
            </header>

            <section class="profile-card security-card">
              <div class="security-grid">
                <div class="security-aside">
                  <div class="security-badge">
                    <span class="material-symbols-outlined">verified_user</span>
                    <span>2FA is enabled</span>
                  </div>
                  <h2 class="security-aside__title">Change Password</h2>
                  <p class="security-aside__desc">Ensure your password is strong, unique, and kept secure across your
                    administrative workspace.</p>
                  <button type="button" class="security-link" @click="handlePlaceholder()">Forgot your
                    password?</button>
                </div>

                <div class="security-form">
                  <div class="field-group">
                    <label class="field-label">Current Password</label>
                    <input v-model="passwordForm.oldPassword" class="text-field" type="password"
                      placeholder="••••••••••••" />
                  </div>

                  <div class="security-form__split">
                    <div class="field-group">
                      <label class="field-label">New Password</label>
                      <input v-model="passwordForm.newPassword" class="text-field" type="password"
                        placeholder="请输入新密码" />
                    </div>
                    <div class="field-group">
                      <label class="field-label">Confirm New Password</label>
                      <input v-model="confirmPassword" class="text-field" type="password" placeholder="请再次输入新密码" />
                    </div>
                  </div>

                  <div class="actions-row actions-row--spread">
                    <span class="security-tip">密码修改成功后，下次登录请使用新密码。</span>
                    <button type="button" class="btn btn--outline" @click="savePassword">Update Password</button>
                  </div>
                </div>
              </div>
            </section>
          </template>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updatePassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/utils/formatters'

const authStore = useAuthStore()
const activeTab = ref<'profile' | 'security'>('profile')

const profile = reactive({
  displayName: authStore.user?.displayName || '',
  email: authStore.user?.email || '',
  avatarUrl: authStore.user?.avatarUrl || '',
  bio: authStore.user?.bio || '',
})

const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const confirmPassword = ref('')

const syncProfileFromUser = (user?: {
  displayName?: string | null
  email?: string | null
  avatarUrl?: string | null
  bio?: string | null
}) => {
  profile.displayName = user?.displayName || ''
  profile.email = user?.email || ''
  profile.avatarUrl = user?.avatarUrl || ''
  profile.bio = user?.bio || ''
}

watch(
  () => authStore.user,
  (user) => {
    syncProfileFromUser(user)
  },
  { immediate: true },
)

const bioLength = computed(() => profile.bio.length)
const resolvedAvatarUrl = computed(
  () =>
    profile.avatarUrl ||
    authStore.user?.avatarUrl ||
    'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80',
)

const lastLoginText = computed(() => {
  const email = authStore.user?.email || 'current account'
  return `Recent access for ${email}`
})

const handlePlaceholder = (message = '当前功能还未开发') => {
  ElMessageBox.alert(message, '提示', {
    confirmButtonText: '确定',
    type: 'info',
  })
}

const resetProfile = () => {
  syncProfileFromUser(authStore.user)
  ElMessage.success('已恢复到当前保存的资料')
}

const saveProfile = async () => {
  try {
    await authStore.updateProfile(profile)
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '资料保存失败，请稍后重试'))
  }
}

const savePassword = async () => {
  if (confirmPassword.value !== passwordForm.newPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }

  try {
    await updatePassword(passwordForm)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    confirmPassword.value = ''
    ElMessage.success('密码已更新')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '密码更新失败，请稍后重试'))
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Inter:wght@400;500;600&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght@100..700');

:global(body) {
  background: #f8f9ff;
}

.architect-profile-page {
  min-height: 100vh;
  background: #f8f9ff;
  color: #181c20;
  font-family: 'Inter', sans-serif;
}

.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}

.top-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  height: 64px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  box-shadow: 0 1px 0 rgba(64, 71, 82, 0.08), 0 4px 16px rgba(64, 158, 255, 0.05);
}

.top-nav__left,
.top-nav__right {
  display: flex;
  align-items: center;
}

.top-nav__left {
  gap: 32px;
}

.top-nav__right {
  gap: 16px;
}

.top-nav__brand {
  font-family: 'Manrope', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.top-nav__links {
  display: flex;
  gap: 24px;
}

.top-nav__link {
  border: none;
  background: transparent;
  padding: 0;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s ease;
}

.top-nav__link:hover {
  color: #409eff;
}

.top-icon-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: 999px;
  color: #64748b;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.top-icon-btn:hover {
  background: #ebeef4;
  color: #0060a9;
}

.top-icon-btn__dot {
  position: absolute;
  top: 9px;
  right: 9px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #ba1a1a;
}

.top-nav__divider {
  width: 1px;
  height: 32px;
  background: rgba(192, 199, 212, 0.3);
  margin: 0 8px;
}

.user-trigger {
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  cursor: pointer;
}

.user-trigger__avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  object-fit: cover;
}

.user-trigger__icon {
  color: #94a3b8;
  transition: color 0.2s ease;
}

.user-trigger:hover .user-trigger__icon {
  color: #0060a9;
}

.page-shell {
  display: flex;
  min-height: 100vh;
  padding-top: 64px;
}

.sidebar {
  position: fixed;
  top: 64px;
  left: 0;
  width: 256px;
  height: calc(100vh - 64px);
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  padding: 32px 0;
  z-index: 40;
}

.sidebar__profile {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  margin-bottom: 32px;
}

.sidebar__avatar-wrap {
  position: relative;
}

.sidebar__avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  object-fit: cover;
}

.sidebar__online {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: #55af28;
  border: 2px solid #f8fafc;
}

.sidebar__name {
  margin: 0;
  color: #0f172a;
  font-family: 'Manrope', sans-serif;
  font-size: 14px;
  font-weight: 800;
}

.sidebar__role {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.sidebar__nav {
  flex: 1;
}

.sidebar__nav ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.sidebar__item {
  width: calc(100% - 16px);
  margin-left: 16px;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  color: #64748b;
  font-family: 'Manrope', sans-serif;
  font-size: 14px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
  transition: all 0.25s ease;
}

.sidebar__item:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.sidebar__item.is-active {
  color: #2563eb;
  background: #ffffff;
  border-radius: 8px 0 0 8px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
  transform: translateX(4px);
}

.sidebar__footer {
  padding: 0 24px;
}

.portfolio-btn {
  width: 100%;
  border: none;
  border-radius: 12px;
  padding: 13px 16px;
  background: #e6e8ef;
  color: #181c20;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.portfolio-btn:hover {
  background: #e0e2e9;
}

.main-canvas {
  flex: 1;
  margin-left: 256px;
  padding: 40px;
}

.main-inner {
  max-width: 896px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 40px;
}

.page-header__title {
  color: #181c20;
  font-family: 'Manrope', sans-serif;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.03em;

}

.page-header__desc {
  margin: 0;
  color: #404752;
  font-size: 14px;
}

.profile-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 48px;
  box-shadow: 0 12px 32px -4px rgba(0, 96, 169, 0.08);
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 48px;
  align-items: start;
}

.avatar-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-upload {
  position: relative;
  width: 160px;
  height: 160px;
  border: none;
  border-radius: 999px;
  overflow: hidden;
  padding: 0;
  cursor: pointer;
  background: transparent;
  box-shadow: 0 0 0 4px #f1f3fa;
  transition: box-shadow 0.2s ease;
}

.avatar-upload:hover {
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.18);
}

.avatar-upload__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-upload__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 96, 169, 0.4);
  color: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
  backdrop-filter: blur(2px);
}

.avatar-upload:hover .avatar-upload__mask {
  opacity: 1;
}

.avatar-upload__mask span:last-child {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.avatar-panel__meta {
  margin-top: 24px;
  text-align: center;
}

.avatar-panel__title {
  margin: 0;
  font-family: 'Manrope', sans-serif;
  font-size: 14px;
  font-weight: 800;
}

.avatar-panel__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #404752;
}

.form-panel,
.security-form {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  color: #404752;
  font-size: 14px;
  font-weight: 600;
}

.field-label--lock {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.field-label__lock {
  font-size: 14px;
}

.readonly-field,
.text-field,
.text-area {
  width: 100%;
  border: none;
  border-radius: 8px;
  background: #e0e2e9;
  color: #181c20;
  font-size: 15px;
  font-weight: 500;
  font-family: inherit;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.readonly-field {
  padding: 15px 16px;
  color: rgba(24, 28, 32, 0.6);
  cursor: not-allowed;
}

.text-field {
  height: 52px;
  padding: 0 16px;
}

.text-area {
  min-height: 132px;
  padding: 14px 16px;
  resize: none;
}

.text-field:focus,
.text-area:focus {
  outline: none;
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.15);
}

.text-field::placeholder,
.text-area::placeholder {
  color: #9aa3b2;
}

.field-counter {
  margin: 0;
  text-align: right;
  font-size: 12px;
  color: #404752;
}

.actions-row {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  align-items: center;
  padding-top: 12px;
}

.actions-row--spread {
  justify-content: space-between;
}

.btn {
  border: none;
  border-radius: 8px;
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn--ghost {
  background: transparent;
  color: #0060a9;
}

.btn--ghost:hover {
  background: rgba(0, 96, 169, 0.05);
}

.btn--primary {
  padding-inline: 32px;
  color: #ffffff;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  box-shadow: 0 16px 28px rgba(0, 96, 169, 0.2);
}

.btn--primary:hover {
  transform: translateY(-1px) scale(1.02);
  box-shadow: 0 20px 34px rgba(0, 96, 169, 0.24);
}

.btn--outline {
  border: 1px solid rgba(0, 96, 169, 0.2);
  background: #ffffff;
  color: #0060a9;
}

.btn--outline:hover {
  background: #f1f3fa;
}

.status-grid {
  margin-top: 32px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.status-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.status-card__left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.status-card__icon--primary {
  background: #d3e4ff;
  color: #0060a9;
}

.status-card__icon--secondary {
  background: #e1e2e7;
  color: #5c5e62;
}

.status-card__title {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: #181c20;
}

.status-card__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: #404752;
}

.status-chip {
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.status-chip--success {
  background: #55af28;
  color: #133b00;
}

.status-chip--neutral {
  background: #e6e8ef;
  color: #404752;
}

.security-card {
  padding: 40px 48px;
}

.security-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 48px;
}

.security-aside__title {
  margin: 24px 0 8px;
  font-family: 'Manrope', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: #181c20;
}

.security-aside__desc {
  margin: 0 0 18px;
  color: #404752;
  font-size: 14px;
  line-height: 1.7;
}

.security-badge {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border-radius: 12px;
  background: #f1f3fa;
  padding: 14px 16px;
  color: #0060a9;
  font-size: 12px;
  font-weight: 800;
}

.security-link {
  border: none;
  background: transparent;
  padding: 0;
  color: #0060a9;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.security-link:hover {
  text-decoration: underline;
}

.security-form__split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.security-tip {
  color: #404752;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .top-nav__links {
    display: none;
  }

  .main-canvas {
    padding: 28px;
  }

  .profile-card,
  .security-card {
    padding: 32px;
  }

  .profile-grid,
  .security-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .avatar-panel {
    align-items: flex-start;
  }

  .avatar-panel__meta {
    text-align: left;
  }
}

@media (max-width: 860px) {
  .top-nav {
    padding: 0 16px;
  }

  .sidebar {
    position: static;
    width: 100%;
    height: auto;
    padding: 24px 0;
  }

  .page-shell {
    flex-direction: column;
  }

  .main-canvas {
    margin-left: 0;
    padding: 24px 16px 40px;
  }

  .page-header__title {
    font-size: 30px;
  }

  .status-grid,
  .security-form__split {
    grid-template-columns: 1fr;
  }

  .actions-row--spread {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 560px) {
  .top-nav__right {
    gap: 10px;
  }

  .top-nav__divider {
    display: none;
  }

  .profile-card,
  .security-card {
    padding: 24px 18px;
  }

  .page-header {
    margin-bottom: 24px;
  }

  .page-header__title {
    font-size: 26px;
  }

  .actions-row {
    flex-direction: column;
    align-items: stretch;
  }

  .btn,
  .portfolio-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
