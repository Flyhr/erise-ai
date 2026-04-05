<template>
  <div class="page-shell">
    <AppPageHeader
      :title="form.title || `${typeLabel}编辑`"
      eyebrow="结构化内容"
      :subtitle="pageSubtitle"
      show-back
      back-label="返回列表"
      :back-to="backTarget"
    >
      <template #actions>
        <el-button v-if="isPreview" type="primary" @click="openEditMode">进入编辑</el-button>
        <template v-else>
          <el-button :loading="saving" @click="save">保存</el-button>
          <el-button type="primary" plain @click="openPreviewMode">浏览</el-button>
        </template>
      </template>
    </AppPageHeader>

    <ProjectSubnav v-if="projectId" :project-id="projectId" />

    <AppSectionCard title="基础信息" description="结构化内容会作为独立实体保存，供搜索与 AI 使用。">
      <el-form :model="form" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" :disabled="isPreview" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" :disabled="isPreview" />
        </el-form-item>
      </el-form>
    </AppSectionCard>

    <AppSectionCard :title="`${typeLabel}内容`" description="编辑器和浏览态沿用同一套页面框架，保证项目内体验一致。">
      <component :is="editorComponent" v-model="contentModel" :readonly="isPreview" />
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getContentItem, updateContentItem } from '@/api/content'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import BoardCanvasEditor from '@/components/content/BoardCanvasEditor.vue'
import DataTableEditor from '@/components/content/DataTableEditor.vue'
import SheetGridEditor from '@/components/content/SheetGridEditor.vue'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const itemId = Number(props.id)
const saving = ref(false)
const itemType = ref<'SHEET' | 'BOARD' | 'DATA_TABLE'>('SHEET')
const projectId = ref<number | null>(null)
const form = reactive({ title: '', summary: '' })
const contentModel = ref<any>({ columns: 6, rows: [] })

const isPreview = computed(() => route.query.mode === 'preview')
const typeLabel = computed(() => ({ SHEET: '表格', BOARD: '画板', DATA_TABLE: '数据表' })[itemType.value])
const editorComponent = computed(() => ({ SHEET: SheetGridEditor, BOARD: BoardCanvasEditor, DATA_TABLE: DataTableEditor })[itemType.value])
const pageSubtitle = computed(() =>
  isPreview.value ? `当前为${typeLabel.value}浏览模式。` : `对${typeLabel.value}的修改会保存为项目内独立内容实体。`,
)
const backTarget = computed(() => {
  if (!projectId.value) {
    return '/projects'
  }
  const routeType = itemType.value === 'DATA_TABLE' ? 'data-table' : itemType.value.toLowerCase()
  return `/projects/${projectId.value}/contents/${routeType}`
})

const defaultSheet = () => ({ columns: 6, rows: Array.from({ length: 8 }, () => Array.from({ length: 6 }, () => '')) })
const defaultBoard = () => ({ width: 960, height: 540, background: '#ffffff', strokes: [] })
const defaultDataTable = () => ({
  columns: [
    { key: 'field_1', label: '名称', type: 'TEXT' },
    { key: 'field_2', label: '状态', type: 'TEXT' },
  ],
  rows: [{ field_1: '', field_2: '' }],
})

const parseContent = (type: 'SHEET' | 'BOARD' | 'DATA_TABLE', raw: string) => {
  try {
    const parsed = raw ? JSON.parse(raw) : null
    if (type === 'BOARD') return parsed || defaultBoard()
    if (type === 'DATA_TABLE') return parsed || defaultDataTable()
    return parsed || defaultSheet()
  } catch {
    if (type === 'BOARD') return defaultBoard()
    if (type === 'DATA_TABLE') return defaultDataTable()
    return defaultSheet()
  }
}

const buildPlainText = () => {
  if (itemType.value === 'BOARD') {
    return `${form.summary || form.title}\n画板笔迹数量：${contentModel.value?.strokes?.length || 0}`.trim()
  }
  if (itemType.value === 'DATA_TABLE') {
    const columns = contentModel.value.columns || []
    const rows = contentModel.value.rows || []
    return rows
      .map((row: Record<string, string>) => columns.map((column: any) => `${column.label}: ${row[column.key] || ''}`).join(' | '))
      .join('\n')
  }
  return (contentModel.value.rows || [])
    .map((row: string[]) => row.join(' | ').trim())
    .filter(Boolean)
    .join('\n')
}

const buildCoverMetaJson = () => {
  if (itemType.value !== 'BOARD') return undefined
  return JSON.stringify({ strokeCount: contentModel.value?.strokes?.length || 0 })
}

const load = async () => {
  const detail = await getContentItem(itemId)
  projectId.value = detail.projectId
  itemType.value = detail.itemType
  form.title = detail.title
  form.summary = detail.summary || ''
  contentModel.value = parseContent(detail.itemType, detail.contentJson)
}

const save = async () => {
  saving.value = true
  try {
    await updateContentItem(itemId, {
      title: form.title.trim() || `未命名${typeLabel.value}`,
      summary: form.summary,
      contentJson: JSON.stringify(contentModel.value),
      plainText: buildPlainText(),
      coverMetaJson: buildCoverMetaJson(),
    })
    ElMessage.success(`${typeLabel.value}已保存`)
  } finally {
    saving.value = false
  }
}

const openPreviewMode = () => router.push({ path: `/contents/${itemId}/edit`, query: { mode: 'preview' } })
const openEditMode = () => router.push(`/contents/${itemId}/edit`)

onMounted(load)
</script>
