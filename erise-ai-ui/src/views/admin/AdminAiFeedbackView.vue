<template>
  <div class="page-shell ai-feedback-page">
    <AppSectionCard title="用户反馈" :unpadded="true">
      <AppFilterBar>
        <el-input v-model="filters.q" clearable placeholder="搜索用户、项目、反馈备注或回答片段" @clear="handleSearch"
          @keyup.enter="handleSearch" />
        <el-select v-model="filters.feedbackType" clearable placeholder="反馈类型" @change="handleSearch">
          <el-option label="赞同" value="UP" />
          <el-option label="不满意" value="DOWN" />
        </el-select>
        <el-date-picker v-model="filters.createdDate" type="date" value-format="YYYY-MM-DD" placeholder="日期"
          @change="handleSearch" />
        <template #actions>
          <el-button @click="resetFilters">重置</el-button>
        </template>
      </AppFilterBar>

      <div v-if="loading" class="page-state">
        <el-skeleton animated :rows="6" />
      </div>

      <el-result v-else-if="loadError" class="page-state" icon="warning" title="反馈列表加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadPage">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <AppDataTable :data="records" stripe>
          <el-table-column label="反馈" width="120">
            <template #default="{ row }">
              <AppStatusTag :label="row.feedbackType === 'UP' ? '赞同' : '不满意'"
                :tone="row.feedbackType === 'UP' ? 'success' : 'warning'" />
            </template>
          </el-table-column>
          <el-table-column label="用户 / 项目" min-width="180">
            <template #default="{ row }">
              <div class="meta-block">
                <strong>{{ row.username || `用户 #${row.userId}` }}</strong>
                <span>{{ row.projectName || '--' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="回答片段" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ row.answerExcerpt || '--' }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.feedbackNote || '--' }}</template>
          </el-table-column>
          <el-table-column label="模型" min-width="140">
            <template #default="{ row }">{{ row.modelCode || '--' }}</template>
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
import { onMounted, reactive, ref } from 'vue'
import { getAiFeedback, type AiFeedbackAdminView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const records = ref<AiFeedbackAdminView[]>([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(true)
const loadError = ref('')

const filters = reactive({
  q: '',
  feedbackType: '',
  createdDate: '',
})

const loadPage = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const page = await getAiFeedback({
      pageNum: pageNum.value,
      pageSize,
      q: filters.q || undefined,
      feedbackType: filters.feedbackType || undefined,
      createdDate: filters.createdDate || undefined,
    })
    records.value = page.records
    total.value = page.total
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '反馈列表加载失败，请稍后重试')
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
  filters.feedbackType = ''
  filters.createdDate = ''
  pageNum.value = 1
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.ai-feedback-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-state {
  padding: 24px;
}

.meta-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-block strong {
  color: #101828;
}

.meta-block span,
.table-count {
  color: #667085;
  font-size: 12px;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px 22px;
  border-top: 1px solid rgba(192, 199, 212, 0.18);
}

@media (max-width: 960px) {
  .table-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
