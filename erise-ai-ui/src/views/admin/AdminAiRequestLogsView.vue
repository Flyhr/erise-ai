<template>
  <div class="page-shell ai-request-logs-page">
    <section class="ai-request-logs__summary">
      <article class="summary-card">
        <span class="summary-card__label">近 {{ stats.windowDays }} 天请求</span>
        <strong>{{ stats.totalRequests }}</strong>
        <small>{{ stats.successRequests }} 成功 / {{ stats.failedRequests }} 失败</small>
      </article>
      <article class="summary-card">
        <span class="summary-card__label">估算成本</span>
        <strong>{{ estimatedCostText }}</strong>
        <small>{{ stats.currencyCode === 'MIXED' ? '多币种汇总' : `币种 ${stats.currencyCode}` }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-card__label">总 Tokens</span>
        <strong>{{ formatLargeNumber(stats.totalTokens) }}</strong>
        <small>平均延迟 {{ stats.averageLatencyMs }} ms</small>
      </article>
    </section>

    <AppSectionCard title="AI 请求日志" :unpadded="true">
      <template #actions>
        <el-select v-model="windowDays" class="days-select" @change="loadStats">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="14" label="近 14 天" />
          <el-option :value="30" label="近 30 天" />
        </el-select>
      </template>

      <AppFilterBar>
        <el-input v-model="filters.q" clearable placeholder="搜索请求号、用户、项目、模型或错误信息" @clear="handleSearch"
          @keyup.enter="handleSearch" />
        <el-select v-model="filters.modelCode" clearable placeholder="模型" @change="handleSearch">
          <el-option v-for="model in models" :key="model.id" :label="model.modelName" :value="model.modelCode" />
        </el-select>
        <el-select v-model="filters.scene" clearable placeholder="场景" @change="handleSearch">
          <el-option label="通用对话" value="general_chat" />
          <el-option label="项目对话" value="project_chat" />
          <el-option label="文档对话" value="document_chat" />
        </el-select>
        <el-select v-model="filters.successMode" clearable placeholder="结果" @change="handleSearch">
          <el-option label="仅成功" value="success" />
          <el-option label="仅失败" value="failed" />
        </el-select>
        <el-date-picker v-model="filters.createdDate" type="date" value-format="YYYY-MM-DD" placeholder="日期"
          @change="handleSearch" />
        <el-switch v-model="filters.errorOnly" inline-prompt active-text="错" inactive-text="全" @change="handleSearch" />
        <template #actions>
          <el-button @click="resetFilters">重置</el-button>
        </template>
      </AppFilterBar>

      <div v-if="loading" class="page-state">
        <el-skeleton animated :rows="6" />
      </div>

      <el-result v-else-if="loadError" class="page-state" icon="warning" title="请求日志加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadPage">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <AppDataTable :data="records" stripe>
          <el-table-column label="请求" min-width="220">
            <template #default="{ row }">
              <div class="request-meta">
                <strong>{{ row.requestId }}</strong>
                <span>{{ row.username || `用户 #${row.userId}` }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="180">
            <template #default="{ row }">
              <div class="request-meta">
                <strong>{{ row.modelCode }}</strong>
                <span>{{ row.providerCode }} / {{ sceneLabel(row.scene) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="项目" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.projectName || '--' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <AppStatusTag :label="row.successFlag ? '成功' : '失败'" :tone="row.successFlag ? 'success' : 'danger'" />
            </template>
          </el-table-column>
          <el-table-column label="Tokens" width="120">
            <template #default="{ row }">{{ formatLargeNumber(row.totalTokenCount || 0) }}</template>
          </el-table-column>
          <el-table-column label="延迟" width="110">
            <template #default="{ row }">{{ row.latencyMs ? `${row.latencyMs} ms` : '--' }}</template>
          </el-table-column>
          <el-table-column label="错误信息" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || row.errorCode || '--' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getAiCostStats,
  getAiModels,
  getAiRequestLogs,
  type AiCostStatsView,
  type AiRequestLogAdminView,
  type ModelConfigView,
} from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const records = ref<AiRequestLogAdminView[]>([])
const models = ref<ModelConfigView[]>([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const windowDays = ref(7)
const loading = ref(true)
const loadError = ref('')
const stats = reactive<AiCostStatsView>({
  windowDays: 7,
  totalRequests: 0,
  successRequests: 0,
  failedRequests: 0,
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  averageLatencyMs: 0,
  currencyCode: 'USD',
  estimatedCost: 0,
  modelBreakdown: [],
})

const filters = reactive({
  q: '',
  modelCode: '',
  scene: '',
  successMode: '',
  createdDate: '',
  errorOnly: false,
})

const estimatedCostText = computed(() => {
  const value = Number(stats.estimatedCost || 0)
  return `${stats.currencyCode === 'MIXED' ? '' : `${stats.currencyCode} `}${value.toFixed(value >= 1 ? 2 : 4)}`
})

const formatLargeNumber = (value: number) => {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
  return String(value)
}

const sceneLabel = (scene?: string) =>
({
  general_chat: '通用',
  project_chat: '项目',
  document_chat: '文档',
}[scene || ''] || scene || '--')

const loadModels = async () => {
  models.value = await getAiModels()
}

const loadStats = async () => {
  Object.assign(stats, await getAiCostStats(windowDays.value))
}

const loadPage = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const page = await getAiRequestLogs({
      pageNum: pageNum.value,
      pageSize,
      q: filters.q || undefined,
      modelCode: filters.modelCode || undefined,
      scene: filters.scene || undefined,
      successFlag:
        filters.successMode === 'success' ? true : filters.successMode === 'failed' ? false : undefined,
      errorOnly: filters.errorOnly || undefined,
      createdDate: filters.createdDate || undefined,
    })
    records.value = page.records
    total.value = page.total
  } catch (error) {
    loadError.value = resolveErrorMessage(error, 'AI 请求日志加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
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
  filters.modelCode = ''
  filters.scene = ''
  filters.successMode = ''
  filters.createdDate = ''
  filters.errorOnly = false
  pageNum.value = 1
  await loadPage()
}

onMounted(async () => {
  await Promise.all([loadModels(), loadStats(), loadPage()])
})
</script>

<style scoped>
.ai-request-logs-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ai-request-logs__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px;
  border-radius: 18px;
  border: 1px solid rgba(192, 199, 212, 0.24);
  background: #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.summary-card__label,
.request-meta span {
  color: #667085;
  font-size: 12px;
}

.summary-card strong {
  color: #101828;
  font-size: 26px;
  font-weight: 800;
}

.summary-card small {
  color: #7a8392;
}

.days-select {
  width: 120px;
}

.page-state {
  padding: 24px;
}

.request-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.request-meta strong {
  color: #101828;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px 22px;
  border-top: 1px solid rgba(192, 199, 212, 0.18);
}

.table-count {
  color: #667085;
  font-size: 13px;
}

@media (max-width: 960px) {
  .ai-request-logs__summary {
    grid-template-columns: 1fr;
  }

  .table-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
