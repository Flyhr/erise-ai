<template>
  <div class="profile-settings-page">
    <div class="profile-settings-layout">
      <aside class="profile-rail">
        <div class="profile-rail__brand">
          <div class="profile-rail__mark">
            <span class="material-symbols-outlined">auto_awesome</span>
          </div>
          <div>
            <div class="profile-rail__title">Erise Ai 知识库 </div>
          </div>
        </div>

        <el-menu :default-active="activeTab" class="profile-rail__menu" @select="handleTabSelect">
          <el-menu-item index="profile">
            <el-icon>
              <User />
            </el-icon>
            <span>个人信息</span>
          </el-menu-item>
          <el-menu-item index="security">
            <el-icon>
              <Lock />
            </el-icon>
            <span>账号安全</span>
          </el-menu-item>
        </el-menu>


      </aside>

      <main class="profile-main">
        <header class="profile-header">
          <div>
            <h1 class="profile-header__title">{{ activeTab === 'profile' ? '个人资料' : '账号安全' }}</h1>
            <p class="profile-header__desc">
              {{ activeTab === 'profile'
                ? ''
                : '' }}
            </p>
          </div>

          <div class="profile-header__actions">
            <el-button round @click="goBackWorkbench">
              <span class="material-symbols-outlined profile-header__button-icon">arrow_back</span>
              <span>{{ isAdminRoute ? '返回后台' : '返回工作台' }}</span>
            </el-button>
            <el-button type="danger" plain round @click="handleLogout">退出登录</el-button>
          </div>
        </header>

        <template v-if="activeTab === 'profile'">
          <section class="profile-card profile-card--main">
            <div class="profile-card__split">
              <div class="profile-avatar-panel">
                <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleAvatarChange"
                  accept="image/png,image/jpeg,image/webp" class="profile-avatar-upload">
                  <button type="button" class="profile-avatar-button">
                    <el-avatar :src="resolvedAvatarUrl || undefined" :size="148" class="profile-avatar-button__image">
                      {{ userInitial }}
                    </el-avatar>
                    <div class="profile-avatar-button__mask">
                      <span class="material-symbols-outlined">photo_camera</span>
                      <span>更换头像</span>
                    </div>
                  </button>
                </el-upload>

                <div class="profile-avatar-panel__meta">
                  <strong>{{ profile.displayName || authStore.user?.displayName || authStore.user?.username || '当前账号'
                    }}</strong>
                  <span>{{ selectedAvatarName || '点击头像可从本地选择图片上传并预览。建议上传 2 MB 以内方形头像。' }}</span>
                </div>
              </div>

              <div class="profile-form-panel">
                <el-form label-position="top" :model="profile" class="profile-form">
                  <el-form-item label="账号">
                    <el-input :model-value="authStore.user?.username || ''" disabled />
                  </el-form-item>

                  <el-form-item label="昵称">
                    <el-input v-model="profile.displayName" maxlength="40" show-word-limit placeholder="请输入昵称" />
                  </el-form-item>

                  <el-form-item label="邮箱">
                    <el-input v-model="profile.email" placeholder="请输入邮箱地址" />
                  </el-form-item>

                  <el-form-item label="个人简介">
                    <el-input v-model="profile.bio" type="textarea" :rows="5" maxlength="500" show-word-limit
                      placeholder="介绍你的职责、擅长领域或常用协作方式" />
                  </el-form-item>
                </el-form>

                <div class="profile-form-panel__footer">
                  <span class="profile-form-panel__hint">头像和资料保存后会同步到当前账号。</span>
                  <div class="profile-form-panel__actions">
                    <el-button round @click="resetProfile">恢复当前资料</el-button>
                    <el-button type="primary" round @click="saveProfile">保存资料</el-button>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="profile-card profile-card--security">
            <article class="security-item">
              <div class="security-item__copy">
                <p class="security-item__label">邮箱验证</p>
                <strong>{{ profile.email || authStore.user?.email || '未填写邮箱' }}</strong>
              </div>
              <div class="security-item__actions">
                <el-tag round effect="plain" type="warning">未验证</el-tag>
                <el-button round disabled>暂未开放</el-button>
              </div>
            </article>

            <article class="security-item">
              <div class="security-item__copy">
                <p class="security-item__label">手机号码</p>
                <strong>未绑定</strong>
              </div>
              <div class="security-item__actions">
                <el-tag round effect="plain" type="info">待支持</el-tag>
                <el-button round disabled>暂未开放</el-button>
              </div>
            </article>

            <article class="security-item">
              <div class="security-item__copy">
                <p class="security-item__label">账户密码</p>
                <strong>建议定期更新登录密码</strong>
              </div>
              <div class="security-item__actions">
                <el-tag round effect="plain" type="success">已启用保护</el-tag>
                <el-button type="primary" round @click="passwordDialogVisible = true">更改密码</el-button>
              </div>
            </article>

            <article v-if="!authStore.isAdmin" class="security-item security-item--danger">
              <div class="security-item__copy">
                <p class="security-item__label">注销账号</p>
                <strong>永久停用当前账号</strong>
              </div>
              <div class="security-item__actions">
                <el-tag round effect="plain" type="danger">高风险操作</el-tag>
                <el-button type="danger" plain round @click="deleteDialogVisible = true">注销账号</el-button>
              </div>
            </article>
          </section>
        </template>
      </main>
    </div>

    <el-dialog v-model="passwordDialogVisible" title="修改账户密码" width="460px" destroy-on-close>
      <el-form label-position="top" :model="passwordForm" class="profile-dialog-form">
        <el-form-item label="旧密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="closePasswordDialog">取消</el-button>
        <el-button type="primary" round @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deleteDialogVisible" title="注销账号" width="460px" destroy-on-close>
      <el-alert :closable="false" show-icon title="注销账号后将无法继续登录当前账号。此操作不可撤销。" type="error" />
      <el-form label-position="top" :model="deleteForm" class="profile-dialog-form profile-dialog-form--danger">
        <el-form-item label="请输入当前密码以确认注销">
          <el-input v-model="deleteForm.password" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="closeDeleteDialog">取消</el-button>
        <el-button type="danger" round @click="deleteAccount">确认注销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { updatePassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/utils/formatters'

const authStore = useAuthStore()
const activeTab = ref<'profile' | 'security'>('profile')
const selectedAvatarName = ref('')
const passwordDialogVisible = ref(false)
const deleteDialogVisible = ref(false)

const profile = reactive({
  displayName: '',
  email: '',
  avatarUrl: '',
  bio: '',
})

const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const confirmPassword = ref('')
const deleteForm = reactive({ password: '' })

const syncProfileFromUser = (
  user?: {
    displayName?: string | null
    email?: string | null
    avatarUrl?: string | null
    bio?: string | null
  } | null,
) => {
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

const isAdminRoute = computed(() => authStore.isAdmin)
const resolvedAvatarUrl = computed(() => profile.avatarUrl || authStore.user?.avatarUrl || '')
const userInitial = computed(() => (profile.displayName || authStore.user?.username || 'U').trim().slice(0, 1).toUpperCase())

const handleTabSelect = (index: string) => {
  activeTab.value = index as 'profile' | 'security'
}

const compressImageToDataUrl = async (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const image = new Image()
      image.onload = () => {
        const maxEdge = 360
        const ratio = Math.min(1, maxEdge / image.width, maxEdge / image.height)
        const width = Math.max(1, Math.round(image.width * ratio))
        const height = Math.max(1, Math.round(image.height * ratio))
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const context = canvas.getContext('2d')
        if (!context) {
          reject(new Error('无法处理头像图片'))
          return
        }
        context.drawImage(image, 0, 0, width, height)
        resolve(canvas.toDataURL('image/jpeg', 0.88))
      }
      image.onerror = () => reject(new Error('头像图片读取失败'))
      image.src = String(reader.result)
    }
    reader.onerror = () => reject(new Error('头像图片读取失败'))
    reader.readAsDataURL(file)
  })

const handleAvatarChange = async (uploadFile: UploadFile) => {
  const file = uploadFile.raw
  if (!file) {
    return
  }

  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('仅支持上传 PNG、JPG 或 WebP 图片')
    return
  }
  if (file.size > 1024 * 1024 * 3) {
    ElMessage.warning('头像图片请控制在 3 MB 以内')
    return
  }

  try {
    profile.avatarUrl = await compressImageToDataUrl(file)
    selectedAvatarName.value = `已选择：${file.name}`
    ElMessage.success('头像已载入，点击“保存资料”后生效')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '头像处理失败，请重试'))
  }
}

const resetProfile = () => {
  syncProfileFromUser(authStore.user)
  selectedAvatarName.value = ''
  ElMessage.success('已恢复到当前保存的资料')
}

const saveProfile = async () => {
  try {
    await authStore.updateProfile({
      displayName: profile.displayName,
      email: profile.email,
      avatarUrl: profile.avatarUrl || undefined,
      bio: profile.bio,
    })
    selectedAvatarName.value = ''
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '资料保存失败，请稍后重试'))
  }
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  confirmPassword.value = ''
}

const closePasswordDialog = () => {
  passwordDialogVisible.value = false
  resetPasswordForm()
}

const savePassword = async () => {
  if (!passwordForm.oldPassword.trim() || !passwordForm.newPassword.trim()) {
    ElMessage.error('请完整填写密码信息')
    return
  }
  if (passwordForm.newPassword.length < 8) {
    ElMessage.error('新密码长度不能少于 8 位')
    return
  }
  if (confirmPassword.value !== passwordForm.newPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }

  try {
    await updatePassword(passwordForm)
    ElMessage.success('密码已更新')
    closePasswordDialog()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '密码更新失败，请稍后重试'))
  }
}

const closeDeleteDialog = () => {
  deleteDialogVisible.value = false
  deleteForm.password = ''
}

const deleteAccount = async () => {
  if (!deleteForm.password.trim()) {
    ElMessage.error('请输入当前密码')
    return
  }
  try {
    await authStore.deleteAccount({ password: deleteForm.password })
    ElMessage.success('账号已注销')
    window.location.href = '/login'
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '账号注销失败，请稍后重试'))
  }
}

const goBackWorkbench = () => {
  window.location.href = isAdminRoute.value ? '/admin' : '/workspace'
}

const handleLogout = async () => {
  await authStore.logout()
  window.location.href = '/login'
}
</script>

<style scoped>
.profile-settings-page {
  width: min(100%, 1240px);
  margin: 0 auto;
}

.profile-settings-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.profile-rail,
.profile-card {
  border-radius: 24px;
  border: 1px solid rgba(192, 199, 212, 0.24);
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.profile-rail {
  position: sticky;
  top: 24px;
  padding: 22px 18px;
}

.profile-rail__brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 22px;
}

.profile-rail__mark {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  color: #ffffff;
  box-shadow: 0 14px 30px rgba(64, 158, 255, 0.28);
}

.profile-rail__title {
  color: #181c20;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.profile-rail__desc {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}

.profile-rail__menu {
  border-right: none;
  background: transparent;
}

.profile-rail__menu :deep(.el-menu-item) {
  margin-bottom: 8px;
  border-radius: 16px;
  height: 46px;
  color: #5d6676;
  font-weight: 700;
}

.profile-rail__menu :deep(.el-menu-item:hover) {
  background: #f1f3fa;
  color: #181c20;
}

.profile-rail__menu :deep(.el-menu-item.is-active) {
  background: rgba(64, 158, 255, 0.12);
  color: #0060a9;
  box-shadow: inset 0 0 0 1px rgba(0, 96, 169, 0.08);
}

.profile-rail__menu :deep(.el-menu-item.is-active::after) {
  display: none;
}

.profile-rail__summary {
  margin-top: 20px;
  padding: 16px;
  border-radius: 18px;
  background: #f1f3fa;
}

.profile-rail__summary-label {
  display: block;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.profile-rail__summary-value {
  display: block;
  margin-top: 8px;
  color: #181c20;
  font-size: 15px;
  font-weight: 800;
}

.profile-rail__summary-copy {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.profile-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  flex-wrap: wrap;
}

.profile-header__eyebrow {
  margin: 0 0 8px;
  color: #0060a9;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.profile-header__title {
  margin: 0;
  color: #181c20;
  font-size: clamp(30px, 4vw, 38px);
  line-height: 1.08;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.profile-header__desc {
  max-width: 720px;
  margin: 12px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.72;
}

.profile-header__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-header__button-icon {
  font-size: 18px;
}

.profile-card {
  padding: 28px;
}

.profile-card--main .profile-card__split {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.profile-avatar-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.profile-avatar-upload {
  display: inline-flex;
}

.profile-avatar-button {
  position: relative;
  width: 176px;
  height: 176px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  box-shadow: 0 0 0 6px #f1f3fa;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.profile-avatar-button:hover {
  box-shadow: 0 0 0 6px rgba(64, 158, 255, 0.16);
  transform: translateY(-1px);
}

.profile-avatar-button__image {
  width: 176px;
  height: 176px;
}

.profile-avatar-button__mask {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: rgba(0, 96, 169, 0.42);
  color: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.profile-avatar-button:hover .profile-avatar-button__mask {
  opacity: 1;
}

.profile-avatar-button__mask span:last-child {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.profile-avatar-panel__meta {
  margin-top: 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-avatar-panel__meta strong {
  color: #181c20;
  font-size: 16px;
  font-weight: 800;
}

.profile-avatar-panel__meta span {
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.profile-form-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-form :deep(.el-form-item__label) {
  color: #404752;
  font-size: 14px;
  font-weight: 700;
}

.profile-form :deep(.el-input__wrapper),
.profile-form :deep(.el-textarea__inner) {
  border-radius: 16px;
  background: #f1f3fa;
  box-shadow: none;
}

.profile-form :deep(.el-input__wrapper) {
  min-height: 48px;
}

.profile-form :deep(.el-input__wrapper.is-focus),
.profile-form :deep(.el-textarea__inner:focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.12);
}

.profile-form-panel__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 16px;
  border-top: 1px solid rgba(192, 199, 212, 0.2);
}

.profile-form-panel__hint {
  color: #667085;
  font-size: 13px;
}

.profile-form-panel__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-card--security {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  padding: 18px 0;
  border-bottom: 1px solid rgba(192, 199, 212, 0.18);
}

.security-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.security-item:first-child {
  padding-top: 0;
}

.security-item__copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.security-item__label {
  margin: 0;
  color: #0060a9;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.security-item__copy strong {
  color: #181c20;
  font-size: 16px;
  font-weight: 800;
}

.security-item__copy span {
  color: #667085;
  font-size: 13px;
  line-height: 1.72;
}

.security-item__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.security-item--danger .security-item__copy strong {
  color: #93000a;
}

.profile-dialog-form {
  margin-top: 18px;
}

.profile-dialog-form--danger {
  margin-top: 12px;
}

.profile-dialog-form :deep(.el-form-item__label) {
  color: #404752;
  font-size: 14px;
  font-weight: 700;
}

.profile-dialog-form :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  background: #f1f3fa;
  box-shadow: none;
}

.profile-dialog-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.12);
}

@media (max-width: 1100px) {
  .profile-settings-layout {
    grid-template-columns: 1fr;
  }

  .profile-rail {
    position: static;
  }

  .profile-card--main .profile-card__split {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .profile-card {
    padding: 22px 18px;
  }

  .profile-header__actions,
  .profile-form-panel__actions,
  .security-item__actions {
    width: 100%;
  }

  .profile-header__actions :deep(.el-button),
  .profile-form-panel__actions :deep(.el-button),
  .security-item__actions :deep(.el-button) {
    flex: 1;
  }

  .security-item,
  .profile-form-panel__footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
