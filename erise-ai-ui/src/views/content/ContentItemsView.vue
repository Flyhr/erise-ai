<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ typeLabel }}中心</h1>
        <div class="page-subtitle">把项目里的 {{ typeLabel }} 独立成实体管理，便于搜索、编辑和 AI 引用。</div>
      </div>
      <el-button type="primary" @click="createItem">新建{{ typeLabel }}</el-button>
    </div>

    <el-card class="glass-card" shadow="never">
      <div v-if="items.length" class="section-stack">
        <div v-for="item in items" :key="item.id" class="glass-card content-row">
          <div>
            <div class="content-row__title">{{ item.title }}</div>
            <div class="meta-row">
              <span>{{ typeLabel }}</span>
              <span>{{ item.updatedAt }}</span>
            </div>
            <div class="page-subtitle">{{ item.summary || '暂无摘要' }}</div>
          </div>
          <div class="table-actions">
            <el-button text @click="openItem(item.id)">编辑</el-button>
            <el-button text @click="previewItem(item.id)">预览</el-button>
            <el-button text type="danger" @click="removeItem(item.id, item.title)">删除</el-button>
          </div>
        </div>
      </div>
      <div v-else class="empty-box">当前项目还没有{{ typeLabel }}，先创建一个。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { createContentItem, deleteContentItem, getContentItems } from '@/api/content'
import type { ContentItemSummaryView } from '@/types/models'

const props = defineProps<{ id: string; type: string }>()
const router = useRouter()
const projectId = Number(props.id)
const items = ref<ContentItemSummaryView[]>([])

const normalizeType = (value: string) => {
  if (value === 'board') return 'BOARD'
  if (value === 'data-table' || value === 'data_table') return 'DATA_TABLE'
  return 'SHEET'
}

const contentType = computed<'SHEET' | 'BOARD' | 'DATA_TABLE'>(() => normalizeType(props.type) as 'SHEET' | 'BOARD' | 'DATA_TABLE')
const typeLabel = computed(() => ({ SHEET: '表格', BOARD: '画板', DATA_TABLE: '数据表' })[contentType.value])

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
      summary: '结构化字段和记录编辑。',
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
    summary: '适合做项目清单、轻量表格和计划排期。',
    contentJson: JSON.stringify({ columns: 6, rows: Array.from({ length: 8 }, () => Array.from({ length: 6 }, () => '')) }),
    plainText: '',
  }
}

const load = async () => {
  const page = await getContentItems({ projectId, itemType: contentType.value, pageNum: 1, pageSize: 50 })
  items.value = page.records
}

const createItem = async () => {
  const payload = createDefaults()
  const created = await createContentItem({
    projectId,
    itemType: contentType.value,
    title: payload.title,
    summary: payload.summary,
    contentJson: payload.contentJson,
    plainText: payload.plainText,
  })
  ElMessage.success(`${typeLabel.value}已创建`)
  router.push(`/contents/${created.id}/edit`)
}

const openItem = (id: number) => router.push(`/contents/${id}/edit`)
const previewItem = (id: number) => router.push({ path: `/contents/${id}/edit`, query: { mode: 'preview' } })

const removeItem = async (id: number, title: string) => {
  await ElMessageBox.confirm(`确认删除“${title}”吗？`, `删除${typeLabel.value}`, { type: 'warning' })
  await deleteContentItem(id)
  ElMessage.success(`${typeLabel.value}已删除`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.content-row {
  padding: 16px 18px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.content-row__title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

@media (max-width: 900px) {
  .content-row {
    flex-direction: column;
  }
}
</style>