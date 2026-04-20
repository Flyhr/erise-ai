<template>
  <div class="page-shell ai-index-tasks-page">
    <AppSectionCard title="索引任务状态" :unpadded="true">
      <AppFilterBar>
        <el-input v-model="filters.q" clearable placeholder="搜索资源标题、任务类型或错误信息" @clear="handleSearch"
          @keyup.enter="handleSearch" />
        <el-select v-model="filters.taskOrigin" clearable placeholder="来源" @change="handleSearch">
          <el-option label="文件解析" value="FILE_PARSE" />
          <el-option label="RAG 索引" value="RAG" />
          <el-option label="临时附件" value="TEMP_FILE_PARSE" />
        </el-select>
        <el-select v-model="filters.taskStatus" clearable placeholder="状态" @change="handleSearch">
          <el-option label="等待中" value="PENDING" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="待修复" value="NEEDS_REPAIR" />
        </el-select>
        <el-switch v-model="filters.errorOnly" inline-prompt active-text="错" inactive-text="全" @change="handleSearch" />
        <template #actions>
          <el-button @click="resetFilters">重置</el-button>
        </template>
      </AppFilterBar>

      <div v-if="loading" class="page-state">
        <el-skeleton animated :rows="6" />
      </div>

      <el-result v-else-if="loadError" class="page-state" icon="warning" title="索引任务加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadPage">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <AppDataTable :data="records" stripe>
          <el-table-column label="来源" width="120">
            <template #default="{ row }">
              <AppStatusTag :label="originLabel(row.taskOrigin)" :tone="originTone(row.taskOrigin)" />
            </template>
          </el-table-column>
          <el-table-column label="任务类型" min-width="150">
            <template #default="{ row }">{{ row.taskType }}</template>
          </el-table-column>
          <el-table-column label="资源" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.resourceTitle || '--' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <AppStatusTag :label="statusLabel(row.taskStatus)" :tone="statusTone(row.taskStatus)" />
            </template>
          </el-table-column>
          <el-table-column label="重试次数" width="100">
            <template #default="{ row }">{{ row.retryCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="最后错误" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.lastError || '--' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.retryable" type="primary" text @click="retryTask(row)">重试</el-button>
              <span v-else class="muted-action">--</span>
            </template>
          </el-table-column>
        </AppDataTable>

        <div class="table-footer">
          <span class="table-count">共 {{ total }} 条</span>
          <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total"
            @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiIndexTasks, retryAdminTask, type AiIndexTaskAdminView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const records = ref<AiIndexTaskAdminView[]>([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(true)
const loadError = ref('')

const filters = reactive({
  q: '',
  taskOrigin: '',
  taskStatus: '',
  errorOnly: false,
})

const originLabel = (value?: string) =>
({
  FILE_PARSE: '文件解析',
  RAG: 'RAG 索引',
  TEMP_FILE_PARSE: '临时附件',
}[value || ''] || value || '--')

const originTone = (value?: string) =>
  ({
    FILE_PARSE: 'primary',
    RAG: 'success',
    TEMP_FILE_PARSE: 'warning',
  }[value || ''] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

const statusLabel = (value?: string) =>
({
  PENDING: '等待中',
  PROCESSING: '处理中',
  SUCCESS: '成功',
  READY: '成功',
  FAILED: '失败',
  NEEDS_REPAIR: '待修复',
}[String(value || '').toUpperCase()] || value || '--')

const statusTone = (value?: string) =>
  ({
    PENDING: 'warning',
    PROCESSING: 'primary',
    SUCCESS: 'success',
    READY: 'success',
    FAILED: 'danger',
    NEEDS_REPAIR: 'danger',
  }[String(value || '').toUpperCase()] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

const loadPage = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const page = await getAiIndexTasks({
      pageNum: pageNum.value,
      pageSize,
      q: filters.q || undefined,
      taskOrigin: filters.taskOrigin || undefined,
      taskStatus: filters.taskStatus || undefined,
      errorOnly: filters.errorOnly || undefined,
    })
    records.value = page.records
    total.value = page.total
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '索引任务加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const retryTask = async (task: AiIndexTaskAdminView) => {
  await retryAdminTask(task.taskOrigin, task.id)
  ElMessage.success('任务已重新触发')
  await loadPage()
}

const handleSearch = async () => {
  pageNum.value = 1
  await loadPage()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await loadPage()
}

const resetFilters = async () => {
  filters.q = ''
  filters.taskOrigin = ''
  filters.taskStatus = ''
  filters.errorOnly = false
  pageNum.value = 1
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.ai-index-tasks-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-state {
  padding: 24px;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px 22px;
  border-top: 1px solid rgba(192, 199, 212, 0.18);
}

.table-count,
.muted-action {
  color: #667085;
  font-size: 12px;
}

@media (max-width: 960px) {
  .table-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
