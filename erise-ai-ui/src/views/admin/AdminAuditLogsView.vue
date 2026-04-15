<template>
  <div class="page-shell admin-logs-page">
    <section class="admin-logs__toolbar">
      <div class="admin-logs__search">
        <el-input v-model="keyword" clearable placeholder="搜索操作人、动作、资源或详情" @clear="handleSearch" @keyup.enter="handleSearch">
          <template #prefix>
            <span class="material-symbols-outlined">search</span>
          </template>
          <template #suffix>
            <SearchSuffixButton @click="handleSearch" />
          </template>
        </el-input>
      </div>

      <el-button class="admin-logs__reset-button" @click="resetFilters">重置</el-button>
    </section>

    <section class="admin-logs__table-shell">
      <div class="admin-logs__table-header">
        <div>
          <h2>审计日志</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-logs__state">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="rect" style="width: 100%; height: 56px; border-radius: 18px;" />
            <el-skeleton-item
              variant="rect"
              style="width: 100%; height: 72px; margin-top: 14px; border-radius: 16px;"
            />
            <el-skeleton-item
              variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;"
            />
            <el-skeleton-item
              variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;"
            />
          </template>
        </el-skeleton>
      </div>

      <el-result v-else-if="loadError" class="admin-logs__state" icon="warning" title="日志加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadLogs">重新加载</el-button>
        </template>
      </el-result>

      <div v-else-if="logs.length" class="admin-logs__table-wrapper">
        <AppDataTable :data="logs" stripe>
          <el-table-column label="操作人" min-width="160">
            <template #default="{ row }">
              <div class="admin-logs__operator">
                <strong>{{ row.operatorUsername || '系统' }}</strong>
                <span>日志 #{{ row.id }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="动作" min-width="220">
            <template #default="{ row }">
              <div class="admin-logs__action">
                <strong>{{ actionLabel(row.actionCode) }}</strong>
                <span>{{ resourceLabel(row) }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="详情" min-width="340" show-overflow-tooltip>
            <template #default="{ row }">
              <span :title="row.detailJson || '无详情'">{{ formatDetailPreview(row.detailJson) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </AppDataTable>
      </div>

      <el-empty v-else :image-size="84" description="当前筛选条件下还没有符合要求的日志记录。" />

      <div v-if="!loading && !loadError && total > 0" class="admin-logs__table-footer">
        <span class="admin-logs__table-count">共 {{ total }} 条日志</span>
        <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminAuditLogs, type AdminAuditLogView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import SearchSuffixButton from '@/components/common/SearchSuffixButton.vue'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const logs = ref<AdminAuditLogView[]>([])
const keyword = ref('')
const pageNum = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref('')
const pageSize = 10

const resourceLabel = (row: AdminAuditLogView) => {
  if (row.resourceType && row.resourceId != null) {
    return `${resourceTypeLabel(row.resourceType)} #${row.resourceId}`
  }
  if (row.resourceType) {
    return resourceTypeLabel(row.resourceType)
  }
  if (row.resourceId != null) {
    return `资源 #${row.resourceId}`
  }
  return '未绑定资源'
}

const actionLabel = (value?: string) =>
  ({
    FILE_UPLOAD: '文件上传',
    FILE_DOWNLOAD: '文件下载',
    FILE_PREVIEW: '文件预览',
    FILE_DELETE: '文件删除',
    DOCUMENT_SAVE: '文档保存',
    DOCUMENT_PUBLISH: '文档发布',
    DOCUMENT_DELETE: '文档删除',
    SEARCH: '站内搜索',
    AI_CHAT: '智能问答',
    AI_SESSION_CREATE: '创建智能会话',
    AI_SESSION_DELETE: '删除智能会话',
    ADMIN_USER_STATUS: '修改用户状态',
    ADMIN_TASK_RETRY: '重试后台任务',
    ADMIN_MODEL_UPDATE: '更新模型配置',
  }[String(value || '').toUpperCase()] ||
    value ||
    '未命名动作')

const resourceTypeLabel = (value?: string) =>
  ({
    FILE: '文件',
    DOCUMENT: '文档',
    USER: '用户',
    AI_SESSION: '智能会话',
    ADMIN_TASK: '后台任务',
    AI_MODEL: '模型',
  }[String(value || '').toUpperCase()] ||
    value ||
    '资源')

const stringifyPreviewValue = (value: unknown) => {
  if (value == null) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return JSON.stringify(value)
}

const formatDetailPreview = (detailJson?: string) => {
  if (!detailJson) {
    return '无详情'
  }
  try {
    const parsed = JSON.parse(detailJson)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const preview = Object.entries(parsed)
        .slice(0, 3)
        .map(([key, value]) => `${key}: ${stringifyPreviewValue(value)}`)
        .join(' · ')
      return preview || detailJson
    }
    return stringifyPreviewValue(parsed)
  } catch {
    return detailJson
  }
}

const loadLogs = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const page = await getAdminAuditLogs({
      pageNum: pageNum.value,
      pageSize,
      q: keyword.value.trim() || undefined,
    })
    logs.value = page.records
    total.value = page.total
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '日志加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  pageNum.value = 1
  await loadLogs()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await loadLogs()
}

const resetFilters = async () => {
  keyword.value = ''
  pageNum.value = 1
  await loadLogs()
}

onMounted(loadLogs)
</script>

<style scoped>
.admin-logs-page {
  gap: 18px;
}

.admin-logs__toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.admin-logs__search {
  flex: 1;
  min-width: min(100%, 320px);
  max-width: 520px;
}

.admin-logs__search :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  background: #e0e2e9;
  box-shadow: none;
}

.admin-logs__search :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.12);
}

.admin-logs__search :deep(.el-input__prefix-inner) {
  color: #5f6775;
}

.admin-logs__reset-button {
  min-height: 46px;
  padding: 0 18px;
  border-radius: 14px;
  font-weight: 800;
}

.admin-logs__table-shell {
  overflow: hidden;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(192, 199, 212, 0.24);
}

.admin-logs__table-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 18px;
  border-bottom: 1px solid rgba(192, 199, 212, 0.22);
}

.admin-logs__table-header h2 {
  margin: 0;
  color: #101828;
  font-size: 20px;
  font-weight: 800;
}

.admin-logs__table-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(0, 96, 169, 0.08);
  color: #0060a9;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.admin-logs__state {
  padding: 24px;
}

.admin-logs__table-wrapper {
  overflow-x: auto;
}

.admin-logs__table-wrapper :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #f1f3fa;
  color: #5f6775;
  font-size: 12px;
  font-weight: 800;
}

.admin-logs__table-wrapper :deep(.el-table td.el-table__cell) {
  padding-top: 14px;
  padding-bottom: 14px;
}

.admin-logs__operator,
.admin-logs__action {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.admin-logs__operator strong,
.admin-logs__action strong {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.admin-logs__operator span,
.admin-logs__action span {
  color: #667085;
  font-size: 12px;
}

.admin-logs__table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px 22px;
  border-top: 1px solid rgba(192, 199, 212, 0.18);
}

@media (max-width: 960px) {
  .admin-logs__table-header,
  .admin-logs__table-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .admin-logs__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-logs__search {
    max-width: none;
  }
}
</style>
