<template>
  <div class="notification-center">
    <el-badge :value="displayUnreadCount" :hidden="unreadCount <= 0" :max="99">
      <button
        type="button"
        class="notification-center__trigger"
        :class="{ 'has-label': showLabel }"
        @click="openDrawer"
      >
        <span class="material-symbols-outlined">notifications</span>
        <span v-if="showLabel">{{ buttonLabel }}</span>
      </button>
    </el-badge>

    <el-drawer
      v-model="visible"
      :size="adminMode ? '420px' : '360px'"
      :with-header="false"
      append-to-body
      class="notification-center__drawer"
    >
      <div class="notification-center__panel">
        <header class="notification-center__header">
          <div>
            <h2>通知中心</h2>
            <p>统一查看系统公告、用户通知与历史消息</p>
          </div>
          <div class="notification-center__header-actions">
            <el-button v-if="adminMode" type="primary" @click="openComposeDialog">发送通知</el-button>
            <el-button v-else text :disabled="unreadCount <= 0" @click="handleReadAll">全部已读</el-button>
          </div>
        </header>

        <div class="notification-center__tabs">
          <el-segmented v-model="activeTab" :options="tabOptions" class="notification-center__segmented" />
        </div>

        <div v-if="adminMode" class="notification-center__batchbar">
          <el-checkbox
            :model-value="allVisibleSelected"
            :indeterminate="selectAllIndeterminate"
            :disabled="filteredNotifications.length === 0"
            @change="handleSelectAllVisibleChange"
          >
            全选当前页
          </el-checkbox>
          <span class="notification-center__batch-count">已选 {{ selectedNotificationIds.length }} 条</span>
          <el-button
            type="danger"
            plain
            :disabled="selectedNotificationIds.length === 0"
            :loading="deleting"
            @click="handleDeleteSelected"
          >
            删除所选
          </el-button>
        </div>

        <div v-if="listLoading" class="notification-center__state">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="rect" style="width: 100%; height: 92px; border-radius: 18px;" />
              <el-skeleton-item variant="rect" style="width: 100%; height: 92px; margin-top: 12px; border-radius: 18px;" />
              <el-skeleton-item variant="rect" style="width: 100%; height: 92px; margin-top: 12px; border-radius: 18px;" />
            </template>
          </el-skeleton>
        </div>

        <el-result
          v-else-if="loadError"
          class="notification-center__state"
          icon="warning"
          title="通知加载失败"
          :sub-title="loadError"
        >
          <template #extra>
            <el-button type="primary" @click="loadNotifications">重新加载</el-button>
          </template>
        </el-result>

        <el-scrollbar v-else-if="filteredNotifications.length" class="notification-center__list-shell">
          <div class="notification-center__list">
            <article
              v-for="item in filteredNotifications"
              :key="item.id"
              class="notification-card"
              :class="{
                'is-unread': !item.read,
                'is-system': isSystemNotification(item),
                'is-read-card': item.read,
              }"
              @click="openDetailDialog(item)"
            >
              <header class="notification-card__header">
                <div class="notification-card__title-group">
                  <el-checkbox
                    v-if="adminMode"
                    :model-value="isNotificationSelected(item.id)"
                    @click.stop
                    @change="handleNotificationSelectionChange(item.id, $event)"
                  />
                  <div v-if="isSystemNotification(item)" class="notification-card__system-icon">
                    <span class="material-symbols-outlined">campaign</span>
                  </div>
                  <div>
                    <h3>{{ item.title }}</h3>
                    <p>{{ item.senderName || '系统通知' }}</p>
                  </div>
                </div>
                <div class="notification-card__meta">
                  <span class="notification-card__time">{{ formatRelativeTime(item.createdAt) }}</span>
                  <span v-if="!item.read" class="notification-card__dot"></span>
                </div>
              </header>

              <p class="notification-card__content">{{ item.content }}</p>

              <footer class="notification-card__footer">
                <span class="notification-card__type">{{ notificationTypeLabel(item.notificationType) }}</span>
                <div class="notification-card__footer-actions">
                  <el-button v-if="!item.read" text type="primary" @click.stop="handleRead(item.id)">标记已读</el-button>
                  <el-button v-if="adminMode" text type="danger" @click.stop="handleDeleteSingle(item.id)">删除</el-button>
                </div>
              </footer>
            </article>
          </div>
        </el-scrollbar>

        <AppEmptyState v-else eyebrow="通知" title="当前没有可查看的通知" :description="emptyDescription" />

        <footer class="notification-center__footer">
          <button type="button" class="notification-center__footer-btn" @click="showFooterPlaceholder('归档')">
            <span class="material-symbols-outlined">archive</span>
            <span>归档</span>
          </button>
          <button type="button" class="notification-center__footer-btn" @click="showFooterPlaceholder('设置')">
            <span class="material-symbols-outlined">settings</span>
            <span>设置</span>
          </button>
        </footer>
      </div>
    </el-drawer>

    <el-dialog
      v-model="detailDialogVisible"
      width="520px"
      align-center
      append-to-body
      destroy-on-close
      class="notification-detail-dialog"
      :show-close="false"
    >
      <div v-if="activeNotification" class="notification-detail-dialog__panel">
        <div class="notification-detail-dialog__hero">
          <div class="notification-detail-dialog__icon">
            <span class="material-symbols-outlined">{{ isSystemNotification(activeNotification) ? 'campaign' : 'notifications' }}</span>
          </div>
          <div class="notification-detail-dialog__copy">
            <h2>{{ activeNotification.title }}</h2>
            <p>{{ activeNotification.senderName || '系统通知' }} · {{ formatDateTime(activeNotification.createdAt) }}</p>
          </div>
        </div>

        <div class="notification-detail-dialog__meta">
          <span class="notification-detail-dialog__chip">{{ notificationTypeLabel(activeNotification.notificationType) }}</span>
          <span class="notification-detail-dialog__chip" :class="{ 'is-read': activeNotification.read }">
            {{ activeNotification.read ? '已读' : '未读' }}
          </span>
        </div>

        <div class="notification-detail-dialog__content">
          {{ activeNotification.content }}
        </div>

        <div class="notification-detail-dialog__actions">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
          <el-button v-if="!activeNotification.read" type="primary" @click="handleDetailAcknowledge">我知道了</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="composeDialogVisible"
      width="560px"
      align-center
      append-to-body
      destroy-on-close
      class="notification-compose-dialog"
      :show-close="false"
    >
      <div class="notification-compose-dialog__panel">
        <div class="notification-compose-dialog__hero">
          <div class="notification-compose-dialog__icon">
            <span class="material-symbols-outlined">campaign</span>
          </div>
          <div class="notification-compose-dialog__copy">
            <h2>发送系统通知</h2>
            <p>向用户发送平台公告、审核结果或维护提醒，通知内容会进入用户通知中心。</p>
          </div>
        </div>

        <el-form
          ref="composeFormRef"
          :model="composeForm"
          :rules="composeRules"
          label-position="top"
          class="notification-compose"
        >
          <el-form-item label="发送范围">
            <el-radio-group v-model="composeForm.scope">
              <el-radio-button label="all">所有用户</el-radio-button>
              <el-radio-button label="specific">指定用户</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="composeForm.scope === 'specific'" label="选择用户" prop="userIds">
            <el-select
              v-model="composeForm.userIds"
              multiple
              filterable
              remote
              reserve-keyword
              collapse-tags
              collapse-tags-tooltip
              placeholder="输入用户名、昵称或邮箱搜索"
              :remote-method="loadRecipientOptions"
              :loading="recipientLoading"
              class="notification-compose__select"
            >
              <el-option v-for="option in recipientOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="通知标题" prop="title">
            <el-input v-model="composeForm.title" maxlength="255" show-word-limit placeholder="请输入通知标题" />
          </el-form-item>

          <el-form-item label="通知正文" prop="content">
            <el-input
              v-model="composeForm.content"
              type="textarea"
              :rows="7"
              maxlength="4000"
              show-word-limit
              placeholder="请输入要发送给用户的通知内容"
            />
          </el-form-item>

          <div class="notification-compose__footer">
            <span>{{ composeHint }}</span>
            <div class="notification-compose__actions">
              <el-button @click="handleComposeCancel">取消</el-button>
              <el-button @click="resetComposeForm">清空</el-button>
              <el-button type="primary" :loading="sending" @click="handleSend">发送通知</el-button>
            </div>
          </div>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { getAdminUsers } from '@/api/admin'
import {
  deleteNotifications,
  getMyNotifications,
  getNotificationUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  sendAdminNotification,
} from '@/api/notification'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import type { UserNotificationView } from '@/types/models'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

interface RecipientOption {
  label: string
  value: number
}

interface ComposeFormState {
  scope: 'all' | 'specific'
  userIds: number[]
  title: string
  content: string
}

type NotificationTab = 'all' | 'system' | 'user'

const props = withDefaults(defineProps<{
  adminMode?: boolean
  showLabel?: boolean
  buttonLabel?: string
}>(), {
  adminMode: false,
  showLabel: false,
  buttonLabel: '通知',
})

const visible = ref(false)
const composeDialogVisible = ref(false)
const activeTab = ref<NotificationTab>('all')
const unreadCount = ref(0)
const listLoading = ref(false)
const loadError = ref('')
const notifications = ref<UserNotificationView[]>([])
const activeNotification = ref<UserNotificationView>()
const detailDialogVisible = ref(false)
const sending = ref(false)
const deleting = ref(false)
const recipientLoading = ref(false)
const recipientOptions = ref<RecipientOption[]>([])
const composeFormRef = ref<FormInstance>()
const selectedNotificationIds = ref<number[]>([])

const composeForm = reactive<ComposeFormState>({
  scope: 'all',
  userIds: [],
  title: '',
  content: '',
})

const tabOptions: Array<{ label: string; value: NotificationTab }> = [
  { value: 'all', label: '全部' },
  { value: 'system', label: '系统通知' },
  { value: 'user', label: '用户通知' },
]

const displayUnreadCount = computed(() => (unreadCount.value > 99 ? '99+' : unreadCount.value))

const filteredNotifications = computed(() =>
  notifications.value.filter((item) => {
    if (activeTab.value === 'system') return isSystemNotification(item)
    if (activeTab.value === 'user') return !isSystemNotification(item)
    return true
  }),
)

const visibleNotificationIds = computed(() => filteredNotifications.value.map((item) => item.id))
const selectedCount = computed(() => selectedNotificationIds.value.length)
const allVisibleSelected = computed(
  () =>
    visibleNotificationIds.value.length > 0 &&
    visibleNotificationIds.value.every((id) => selectedNotificationIds.value.includes(id)),
)
const selectAllIndeterminate = computed(
  () =>
    visibleNotificationIds.value.some((id) => selectedNotificationIds.value.includes(id)) &&
    !allVisibleSelected.value,
)

const composeHint = computed(() =>
  composeForm.scope === 'all'
    ? '当前会向所有用户发送一条新的系统通知。'
    : `当前已选择 ${composeForm.userIds.length} 位用户作为接收对象。`,
)

const emptyDescription = computed(() => {
  if (activeTab.value === 'system') return '当前没有系统通知。'
  if (activeTab.value === 'user') return '当前没有用户通知。'
  return '当前没有通知。'
})

const composeRules: FormRules<ComposeFormState> = {
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知正文', trigger: 'blur' }],
  userIds: [
    {
      validator: (_rule, value: number[], callback) => {
        if (composeForm.scope === 'specific' && (!Array.isArray(value) || value.length === 0)) {
          callback(new Error('请至少选择一个用户'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

const isSystemNotification = (item: UserNotificationView) =>
  ['SYSTEM', 'ADMIN_NOTICE', 'ANNOUNCEMENT', 'FILE_REVIEW'].includes((item.notificationType || '').toUpperCase())

const notificationTypeLabel = (type?: string) =>
  ({
    SYSTEM: '系统通知',
    ADMIN_NOTICE: '运营通知',
    ANNOUNCEMENT: '平台公告',
    FILE_REVIEW: '审核结果',
  }[String(type || '').toUpperCase()] || '普通通知')

const formatRelativeTime = (value?: string) => {
  if (!value) return '--'
  const time = dayjs(value)
  const minutes = dayjs().diff(time, 'minute')
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = dayjs().diff(time, 'hour')
  if (hours < 24) return `${hours} 小时前`
  const days = dayjs().diff(time, 'day')
  if (days < 7) return `${days} 天前`
  return formatDateTime(value, 'YYYY-MM-DD')
}

const fetchUnreadCount = async () => {
  try {
    const result = await getNotificationUnreadCount()
    unreadCount.value = result.unreadCount
  } catch {
    unreadCount.value = 0
  }
}

const loadNotifications = async () => {
  listLoading.value = true
  loadError.value = ''
  try {
    const page = await getMyNotifications({
      pageNum: 1,
      pageSize: 100,
      unreadOnly: false,
    })
    notifications.value = page.records
    selectedNotificationIds.value = []
    await fetchUnreadCount()
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '通知加载失败，请稍后重试')
  } finally {
    listLoading.value = false
  }
}

const loadRecipientOptions = async (keyword = '') => {
  if (!props.adminMode) return
  recipientLoading.value = true
  try {
    const page = await getAdminUsers({
      pageNum: 1,
      pageSize: 20,
      q: keyword.trim() || undefined,
    })
    recipientOptions.value = page.records.map((item) => ({
      value: item.id,
      label: `${item.displayName || item.username} (@${item.username})`,
    }))
  } catch {
    recipientOptions.value = []
  } finally {
    recipientLoading.value = false
  }
}

const openDrawer = async () => {
  visible.value = true
  await loadNotifications()
  if (props.adminMode && !recipientOptions.value.length) {
    await loadRecipientOptions('')
  }
}

const openComposeDialog = async () => {
  composeDialogVisible.value = true
  if (props.adminMode && !recipientOptions.value.length) {
    await loadRecipientOptions('')
  }
}

const openDetailDialog = async (item: UserNotificationView) => {
  activeNotification.value = item
  detailDialogVisible.value = true
  if (!item.read) {
    await handleRead(item.id, false)
  }
}

const handleRead = async (id: number, reload = true) => {
  try {
    await markNotificationRead(id)
    const target = notifications.value.find((item) => item.id === id)
    if (target) {
      target.read = true
    }
    if (activeNotification.value?.id === id) {
      activeNotification.value.read = true
    }
    await fetchUnreadCount()
    if (reload) {
      await loadNotifications()
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '通知状态更新失败，请稍后重试'))
  }
}

const handleReadAll = async () => {
  try {
    await markAllNotificationsRead()
    ElMessage.success('所有通知已标记为已读')
    await loadNotifications()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '批量已读失败，请稍后重试'))
  }
}

const toggleNotificationSelection = (id: number, checked: boolean) => {
  const next = new Set(selectedNotificationIds.value)
  if (checked) {
    next.add(id)
  } else {
    next.delete(id)
  }
  selectedNotificationIds.value = Array.from(next)
}

const handleNotificationSelectionChange = (id: number, checked: unknown) => {
  toggleNotificationSelection(id, Boolean(checked))
}

const isNotificationSelected = (id: number) => selectedNotificationIds.value.includes(id)

const toggleSelectAllVisible = (checked: boolean) => {
  selectedNotificationIds.value = checked ? [...visibleNotificationIds.value] : []
}

const handleSelectAllVisibleChange = (checked: unknown) => {
  toggleSelectAllVisible(Boolean(checked))
}

const deleteNotificationBatch = async (ids: number[]) => {
  const uniqueIds = Array.from(new Set(ids))
  if (!uniqueIds.length) return
  try {
    const confirmText =
      uniqueIds.length === 1
        ? '确定删除这条通知吗？删除后不可恢复。'
        : `确定删除选中的 ${uniqueIds.length} 条通知吗？删除后不可恢复。`
    await ElMessageBox.confirm(confirmText, '删除通知', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      autofocus: false,
    })
    deleting.value = true
    await deleteNotifications({ ids: uniqueIds })
    ElMessage.success('通知已删除')
    if (activeNotification.value && uniqueIds.includes(activeNotification.value.id)) {
      detailDialogVisible.value = false
      activeNotification.value = undefined
    }
    await loadNotifications()
  } catch (error) {
    if (error === 'cancel' || error === 'close' || error === 'action cancel') {
      return
    }
    if (error instanceof Error && /cancel|close/i.test(error.message)) {
      return
    }
    ElMessage.error(resolveErrorMessage(error, '通知删除失败，请稍后重试'))
  } finally {
    deleting.value = false
  }
}

const handleDeleteSelected = async () => {
  await deleteNotificationBatch(selectedNotificationIds.value)
}

const handleDeleteSingle = async (id: number) => {
  await deleteNotificationBatch([id])
}

const resetComposeForm = () => {
  composeForm.scope = 'all'
  composeForm.userIds = []
  composeForm.title = ''
  composeForm.content = ''
  composeFormRef.value?.clearValidate()
}

const handleComposeCancel = () => {
  composeDialogVisible.value = false
}

const handleDetailAcknowledge = async () => {
  if (activeNotification.value && !activeNotification.value.read) {
    await handleRead(activeNotification.value.id)
  }
  detailDialogVisible.value = false
}

const showFooterPlaceholder = (feature: string) => {
  ElMessage.info(`${feature}功能即将开放`)
}

const handleSend = async () => {
  if (!props.adminMode || !composeFormRef.value) return
  try {
    await composeFormRef.value.validate()
    sending.value = true
    await sendAdminNotification({
      title: composeForm.title.trim(),
      content: composeForm.content.trim(),
      sendToAll: composeForm.scope === 'all',
      userIds: composeForm.scope === 'specific' ? composeForm.userIds : undefined,
    })
    ElMessage.success('通知已发送')
    resetComposeForm()
    composeDialogVisible.value = false
    activeTab.value = 'all'
    await loadNotifications()
  } catch (error) {
    if (error instanceof Error && error.message) {
      ElMessage.error(resolveErrorMessage(error, '通知发送失败，请稍后重试'))
    }
  } finally {
    sending.value = false
  }
}

watch(activeTab, () => {
  selectedNotificationIds.value = []
})

onMounted(fetchUnreadCount)
</script>

<style scoped>
.notification-center {
  display: inline-flex;
  align-items: center;
}

.notification-center__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid rgba(31, 41, 55, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.84);
  color: #344054;
  cursor: pointer;
  transition: all 0.2s ease;
}

.notification-center__trigger.has-label {
  padding-inline: 14px;
}

.notification-center__trigger:hover {
  border-color: rgba(0, 96, 169, 0.24);
  color: #0060a9;
  background: #ffffff;
}

.notification-center__drawer :deep(.el-drawer) {
  background: #f8f9ff;
}

.notification-center__drawer :deep(.el-drawer__body) {
  padding: 0;
}

.notification-center__panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px 20px 18px;
}

.notification-center__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.notification-center__header h2 {
  margin: 0;
  color: #101828;
  font-size: 20px;
  font-weight: 800;
}

.notification-center__header p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 12px;
}

.notification-center__header-actions {
  display: inline-flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.notification-center__tabs {
  margin-bottom: 14px;
}

.notification-center__segmented {
  width: 100%;
}

.notification-center__segmented :deep(.el-segmented) {
  width: 100%;
  padding: 4px;
  border-radius: 16px;
  background: #e6e8ef;
}

.notification-center__segmented :deep(.el-segmented__group) {
  width: 100%;
}

.notification-center__segmented :deep(.el-segmented__item) {
  flex: 1;
  min-height: 36px;
  border-radius: 12px;
  color: #5f6775;
  font-size: 12px;
  font-weight: 700;
}

.notification-center__segmented :deep(.el-segmented__item.is-selected) {
  background: #ffffff;
  color: #0060a9;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.notification-center__segmented :deep(.el-segmented__item-selected) {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.notification-center__segmented :deep(.el-segmented__item.is-selected .el-segmented__item-label) {
  color: #0060a9;
}

.notification-center__batchbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(192, 199, 212, 0.22);
}

.notification-center__batch-count {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.notification-center__state {
  padding: 18px 0;
}

.notification-center__list-shell {
  flex: 1;
  min-height: 0;
}

.notification-center__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 6px;
}

.notification-card {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(192, 199, 212, 0.18);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  transition: box-shadow 0.18s ease, transform 0.18s ease, background 0.18s ease;
}

.notification-card:hover {
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.notification-card.is-unread {
  border-left: 4px solid #0060a9;
  background: #ffffff;
}

.notification-card.is-system {
  background: rgba(241, 243, 250, 0.92);
}

.notification-card.is-read-card {
  opacity: 0.78;
}

.notification-card__header,
.notification-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.notification-card__title-group {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.notification-card__system-icon {
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(0, 96, 169, 0.12);
  color: #0060a9;
  flex-shrink: 0;
}

.notification-card__header h3 {
  margin: 0;
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.notification-card__header p,
.notification-card__content,
.notification-card__type,
.notification-compose__footer span {
  margin: 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.7;
}

.notification-card__content {
  margin-top: 10px;
  color: #344054;
  white-space: pre-wrap;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notification-card__footer {
  margin-top: 12px;
  align-items: center;
}

.notification-card__footer-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notification-card__meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.notification-card__time {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.notification-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #409eff;
}

.notification-detail-dialog :deep(.el-dialog) {
  border-radius: 24px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 28px 64px rgba(15, 23, 42, 0.18);
}

.notification-detail-dialog :deep(.el-dialog__header) {
  display: none;
}

.notification-detail-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.notification-detail-dialog__panel {
  padding: 28px;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.1), transparent 38%),
    #ffffff;
}

.notification-detail-dialog__hero {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 16px;
  align-items: center;
  margin-bottom: 18px;
}

.notification-detail-dialog__icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.16);
  color: #0060a9;
}

.notification-detail-dialog__icon .material-symbols-outlined {
  font-size: 30px;
}

.notification-detail-dialog__copy h2 {
  margin: 0;
  color: #101828;
  font-size: 24px;
  font-weight: 800;
}

.notification-detail-dialog__copy p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
}

.notification-detail-dialog__meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.notification-detail-dialog__chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(0, 96, 169, 0.12);
  color: #0060a9;
  font-size: 12px;
  font-weight: 800;
}

.notification-detail-dialog__chip.is-read {
  background: rgba(102, 112, 133, 0.1);
  color: #667085;
}

.notification-detail-dialog__content {
  padding: 18px 18px 20px;
  border-radius: 18px;
  background: rgba(241, 243, 250, 0.92);
  color: #344054;
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
}

.notification-detail-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}

.notification-compose {
  padding-top: 4px;
}

.notification-compose-dialog :deep(.el-dialog) {
  border-radius: 24px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.16);
}

.notification-compose-dialog :deep(.el-dialog__header) {
  display: none;
}

.notification-compose-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.notification-compose-dialog__panel {
  padding: 28px 28px 24px;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.1), transparent 38%),
    #ffffff;
}

.notification-compose-dialog__hero {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 16px;
  align-items: center;
  margin-bottom: 20px;
}

.notification-compose-dialog__icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.16);
  color: #0060a9;
}

.notification-compose-dialog__icon .material-symbols-outlined {
  font-size: 30px;
}

.notification-compose-dialog__copy h2 {
  margin: 0;
  color: #101828;
  font-size: 24px;
  font-weight: 800;
}

.notification-compose-dialog__copy p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
}

.notification-compose__select {
  width: 100%;
}

.notification-compose__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.notification-compose__actions {
  display: inline-flex;
  gap: 10px;
}

.notification-center__footer {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(192, 199, 212, 0.22);
}

.notification-center__footer-btn {
  flex: 1;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 62px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  color: #667085;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.notification-center__footer-btn:hover {
  background: #ffffff;
  color: #0060a9;
  transform: translateY(-1px);
}

.notification-center__footer-btn .material-symbols-outlined {
  font-size: 22px;
}

.notification-center__footer-btn span:last-child {
  font-size: 11px;
  font-weight: 700;
}

@media (max-width: 768px) {
  .notification-center__trigger.has-label span:last-child {
    display: none;
  }

  .notification-center__header,
  .notification-compose__footer,
  .notification-detail-dialog__hero {
    align-items: stretch;
    flex-direction: column;
  }

  .notification-center__segmented :deep(.el-segmented__item) {
    flex: 1 1 calc(50% - 4px);
  }

  .notification-compose-dialog__panel {
    padding: 22px 18px 18px;
  }

  .notification-compose-dialog__hero {
    grid-template-columns: 1fr;
    justify-items: center;
    text-align: center;
  }

  .notification-detail-dialog__panel {
    padding: 22px 18px 18px;
  }

  .notification-detail-dialog__copy {
    text-align: center;
  }

  .notification-detail-dialog__actions {
    flex-direction: column;
  }

  .notification-center__footer {
    flex-direction: column;
  }

  .notification-compose__actions {
    width: 100%;
  }

  .notification-compose__actions :deep(.el-button),
  .notification-detail-dialog__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
