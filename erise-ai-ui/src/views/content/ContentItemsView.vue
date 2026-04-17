<template>
  <ProjectScopedListShell :project-id="projectId" :title="`${typeLabel}列表`" :keyword="keyword"
    :search-placeholder="`按${typeLabel}标题或摘要搜索`" @update:keyword="keyword = $event" @search="handleSearch">
    <template #actions>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="primary" @click="createItem">新建{{ typeLabel }}</el-button>
    </template>

    <AppSectionCard :title="`${typeLabel}列表`" :unpadded="Boolean(items.length)">
      <AppDataTable v-if="items.length" :data="items" stripe>
        <el-table-column label="名称" min-width="220">
          <template #default="{ row }">
            <div class="content-row__title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default>
            <AppStatusTag :label="typeLabel" tone="primary" />
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.summary || '暂无摘要' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text @click="previewItem(row.id)">浏览</el-button>
              <el-button text @click="openItem(row.id)">编辑</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="removeItem(row.id, row.title)">删除{{ typeLabel }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>
      <AppEmptyState v-else :title="`当前项目还没有${typeLabel}`" description="先创建一条表格内容，后续就可以在项目内统一维护和在 AI 助理中直接引用。" />

      <template #footer>
        <div class="content-footer">
          <span class="page-subtitle" style="margin: 0;">共 {{ total }} 条{{ typeLabel }}记录</span>
          <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </ProjectScopedListShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { createContentItem, deleteContentItem, getContentItems } from '@/api/content'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectScopedListShell from '@/components/common/ProjectScopedListShell.vue'
import type { ContentItemSummaryView } from '@/types/models'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const props = defineProps<{ id: string; type: string }>()

const route = useRoute()
const router = useRouter()
const projectId = Number(props.id)
const items = ref<ContentItemSummaryView[]>([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 12
const total = ref(0)

const normalizeType = (value: string) => {
  if (value === 'board') return 'BOARD'
  if (value === 'data-table' || value === 'data_table') return 'DATA_TABLE'
  return 'SHEET'
}

const contentType = computed<'SHEET' | 'BOARD' | 'DATA_TABLE'>(
  () => normalizeType(props.type) as 'SHEET' | 'BOARD' | 'DATA_TABLE',
)

const typeLabel = computed(
  () =>
    ({
      SHEET: '表格',
      BOARD: '画板',
      DATA_TABLE: '数据表',
    })[contentType.value],
)

// const pageSubtitle = computed(() => `统一管理当前项目下的${typeLabel.value}内容，并保持与文件、文档页面一致的工作流。`)
// const pageHint = computed(() => `可按关键词筛选${typeLabel.value}，并快速进入浏览、编辑和删除操作。`)

const createDefaults = () => {
  if (contentType.value === 'BOARD') {
    return {
      title: '未命名画板',
      summary: '用于流程草图、批注和自由绘制。',
      contentJson: JSON.stringify({ width: 960, height: 540, background: '#ffffff', strokes: [] }),
      plainText: '空白画板',
    }
  }
  if (contentType.value === 'DATA_TABLE') {
    return {
      title: '未命名数据表',
      summary: '用于维护表格字段与记录。',
      contentJson: JSON.stringify({
        columns: [
          { key: 'field_1', label: '名称', type: 'TEXT' },
          { key: 'field_2', label: '状态', type: 'TEXT' },
        ],
        rows: [{ field_1: '', field_2: '' }],
      }),
      plainText: '',
    }
  }
  return {
    title: '未命名表格',
    summary: '适合记录项目清单、计划排期和轻量表格信息。',
    contentJson: JSON.stringify({
      columns: 6,
      rows: Array.from({ length: 8 }, () => Array.from({ length: 6 }, () => '')),
    }),
    plainText: '',
  }
}

const load = async () => {
  const page = await getContentItems({
    projectId,
    itemType: contentType.value,
    q: keyword.value.trim() || undefined,
    pageNum: pageNum.value,
    pageSize,
  })
  items.value = page.records
  total.value = page.total
}

const syncFromRoute = async () => {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await load()
}

const pushRoute = async () => {
  await router.replace({
    path: route.path,
    query: {
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const ensureCurrentPage = async () => {
  if (!items.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize))
    await pushRoute()
  }
}

const handleSearch = async () => {
  pageNum.value = 1
  await pushRoute()
}

const resetFilters = async () => {
  keyword.value = ''
  pageNum.value = 1
  await pushRoute()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

const createItem = async () => {
  const payload = createDefaults()
  try {
    const created = await createContentItem({
      projectId,
      itemType: contentType.value,
      title: payload.title,
      summary: payload.summary,
      contentJson: payload.contentJson,
      plainText: payload.plainText,
    })
    ElMessage.success(`${typeLabel.value}已创建`)
    await router.push(`/contents/${created.id}/edit`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, `${typeLabel.value}创建失败，请稍后重试`))
  }
}

const openItem = (id: number) => router.push(`/contents/${id}/edit`)
const previewItem = (id: number) => router.push({ path: `/contents/${id}/edit`, query: { mode: 'preview' } })

const removeItem = async (id: number, title: string) => {
  await ElMessageBox.confirm(`确认删除“${title}”吗？`, `删除${typeLabel.value}`, { type: 'warning' })
  await deleteContentItem(id)
  ElMessage.success(`${typeLabel.value}已删除`)
  await load()
  await ensureCurrentPage()
}

onMounted(syncFromRoute)

watch(
  () => route.fullPath,
  async () => {
    await syncFromRoute()
  },
)
</script>

<style scoped>
.content-row__title {
  font-size: 15px;
  font-weight: 700;
}

.content-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
