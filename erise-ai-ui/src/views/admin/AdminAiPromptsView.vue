<template>
  <div class="page-shell ai-prompts-page">

    <AppFilterBar>
      <el-input v-model="filters.q" clearable placeholder="搜索模板、名称或场景" @clear="handleSearch"
        @keyup.enter="handleSearch" />
      <el-select v-model="filters.scene" clearable placeholder="场景" @change="handleSearch">
        <el-option label="通用对话" value="general_chat" />
        <el-option label="项目对话" value="project_chat" />
        <el-option label="文档对话" value="document_chat" />
      </el-select>
      <el-switch v-model="filters.enabledOnly" inline-prompt active-text="启" inactive-text="全" @change="handleSearch" />
      <template #actions>
        <div class="page-actions">
          <el-button :disabled="!selectedTemplateCode" @click="openVersionDialog">新增版本</el-button>
          <el-button type="primary" @click="openTemplateDialog">新建模板</el-button>
        </div>
        <el-button @click="resetFilters">重置</el-button>



      </template>
    </AppFilterBar>

    <section class="prompt-layout">
      <AppSectionCard title="模板列表" :unpadded="true">
        <div v-if="loadingTemplates" class="page-state">
          <el-skeleton animated :rows="6" />
        </div>
        <el-result v-else-if="templateError" class="page-state" icon="warning" title="模板加载失败"
          :sub-title="templateError">
          <template #extra>
            <el-button type="primary" @click="loadTemplates">重新加载</el-button>
          </template>
        </el-result>
        <template v-else>
          <AppDataTable :data="templates" stripe row-key="templateCode" @row-click="selectTemplate">
            <el-table-column label="模板" min-width="220">
              <template #default="{ row }">
                <div class="meta-block">
                  <strong>{{ row.templateName }}</strong>
                  <span>{{ row.templateCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="场景" min-width="140">
              <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
            </el-table-column>
            <el-table-column label="启用版本" width="120">
              <template #default="{ row }">{{ row.enabledVersionNo ? `v${row.enabledVersionNo}` : '--' }}</template>
            </el-table-column>
            <el-table-column label="最新版本" width="120">
              <template #default="{ row }">v{{ row.latestVersionNo }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <AppStatusTag :label="row.enabled ? '已启用' : '未启用'" :tone="row.enabled ? 'success' : 'warning'" />
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
          </AppDataTable>
          <div class="table-footer">
            <span class="table-count">共 {{ templateTotal }} 个模板</span>
            <CompactPager variant="project" :page-num="templatePageNum" :page-size="templatePageSize"
              :total="templateTotal" @change="handleTemplatePageChange" />
          </div>
        </template>
      </AppSectionCard>

      <AppSectionCard :title="selectedTemplateTitle"
        :description="selectedTemplateCode ? `模板编码：${selectedTemplateCode}` : '从左侧选择模板后查看版本详情。'" :unpadded="true">
        <div v-if="!selectedTemplateCode" class="page-state">
          <el-empty description="请选择一个模板查看版本历史" />
        </div>
        <div v-else-if="loadingVersions" class="page-state">
          <el-skeleton animated :rows="5" />
        </div>
        <el-result v-else-if="versionError" class="page-state" icon="warning" title="版本加载失败" :sub-title="versionError">
          <template #extra>
            <el-button type="primary" @click="loadVersions(selectedTemplateCode)">重新加载</el-button>
          </template>
        </el-result>
        <template v-else>
          <AppDataTable :data="versions" stripe>
            <el-table-column label="版本" width="100">
              <template #default="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <AppStatusTag :label="row.enabled ? '已启用' : '已停用'" :tone="row.enabled ? 'success' : 'info'" />
              </template>
            </el-table-column>
            <el-table-column label="内容预览" min-width="320" show-overflow-tooltip>
              <template #default="{ row }">{{ row.systemPrompt }}</template>
            </el-table-column>
            <el-table-column label="更新人" width="120">
              <template #default="{ row }">{{ row.createdBy || '--' }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
                <el-button text @click="toggleStatus(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </AppDataTable>
        </template>
      </AppSectionCard>
    </section>

    <el-dialog v-model="templateDialogVisible" title="新建 Prompt 模板" width="720px" destroy-on-close>
      <el-form :model="templateForm" label-position="top">
        <el-form-item label="模板编码">
          <el-input v-model="templateForm.templateCode" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="templateForm.templateName" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="templateForm.scene" class="w-full">
            <el-option label="通用对话" value="general_chat" />
            <el-option label="项目对话" value="project_chat" />
            <el-option label="文档对话" value="document_chat" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统 Prompt">
          <el-input v-model="templateForm.systemPrompt" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="用户 Prompt 包装器">
          <el-input v-model="templateForm.userPromptWrapper" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="创建后立即启用">
          <el-switch v-model="templateForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTemplate">创建模板</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionDialogVisible" :title="versionDialogTitle" width="720px" destroy-on-close>
      <el-form :model="versionForm" label-position="top">
        <el-form-item label="模板名称">
          <el-input v-model="versionForm.templateName" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="versionForm.scene" class="w-full">
            <el-option label="通用对话" value="general_chat" />
            <el-option label="项目对话" value="project_chat" />
            <el-option label="文档对话" value="document_chat" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统 Prompt">
          <el-input v-model="versionForm.systemPrompt" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="用户 Prompt 包装器">
          <el-input v-model="versionForm.userPromptWrapper" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item v-if="versionMode === 'create'" label="创建后立即启用">
          <el-switch v-model="versionForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitVersion">
          {{ versionMode === 'edit' ? '保存修改' : '创建版本' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAiPromptTemplate,
  createAiPromptTemplateVersion,
  getAiPromptTemplateVersions,
  getAiPromptTemplates,
  updateAiPromptTemplate,
  updateAiPromptTemplateStatus,
  type AiPromptTemplateSummaryView,
  type AiPromptTemplateVersionView,
} from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const templates = ref<AiPromptTemplateSummaryView[]>([])
const versions = ref<AiPromptTemplateVersionView[]>([])
const templatePageNum = ref(1)
const templatePageSize = 10
const templateTotal = ref(0)
const loadingTemplates = ref(true)
const loadingVersions = ref(false)
const templateError = ref('')
const versionError = ref('')
const selectedTemplateCode = ref('')
const selectedTemplateName = ref('')
const templateDialogVisible = ref(false)
const versionDialogVisible = ref(false)
const versionMode = ref<'create' | 'edit'>('create')
const editingVersionId = ref<number>()
const submitting = ref(false)

const filters = reactive({
  q: '',
  scene: '',
  enabledOnly: false,
})

const templateForm = reactive({
  templateCode: '',
  templateName: '',
  scene: 'general_chat',
  systemPrompt: '',
  userPromptWrapper: '',
  enabled: true,
})

const versionForm = reactive({
  templateName: '',
  scene: 'general_chat',
  systemPrompt: '',
  userPromptWrapper: '',
  enabled: false,
})

const selectedTemplateTitle = computed(() => (selectedTemplateCode.value ? `版本历史：${selectedTemplateName.value}` : '版本历史'))
const versionDialogTitle = computed(() => (versionMode.value === 'edit' ? '编辑版本' : `新增版本：${selectedTemplateName.value}`))

const sceneLabel = (scene?: string) =>
({
  general_chat: '通用对话',
  project_chat: '项目对话',
  document_chat: '文档对话',
}[scene || ''] || scene || '--')

const loadTemplates = async () => {
  loadingTemplates.value = true
  templateError.value = ''
  try {
    const page = await getAiPromptTemplates({
      pageNum: templatePageNum.value,
      pageSize: templatePageSize,
      q: filters.q || undefined,
      scene: filters.scene || undefined,
      enabledOnly: filters.enabledOnly || undefined,
    })
    templates.value = page.records
    templateTotal.value = page.total
    if (!selectedTemplateCode.value && page.records.length) {
      await selectTemplate(page.records[0])
    }
  } catch (error) {
    templateError.value = resolveErrorMessage(error, 'Prompt 模板加载失败，请稍后重试')
  } finally {
    loadingTemplates.value = false
  }
}

const loadVersions = async (templateCode: string) => {
  loadingVersions.value = true
  versionError.value = ''
  try {
    versions.value = await getAiPromptTemplateVersions(templateCode)
  } catch (error) {
    versionError.value = resolveErrorMessage(error, 'Prompt 版本加载失败，请稍后重试')
  } finally {
    loadingVersions.value = false
  }
}

const selectTemplate = async (row: AiPromptTemplateSummaryView) => {
  selectedTemplateCode.value = row.templateCode
  selectedTemplateName.value = row.templateName
  await loadVersions(row.templateCode)
}

const handleSearch = async () => {
  templatePageNum.value = 1
  await loadTemplates()
}

const handleTemplatePageChange = async (value: number) => {
  templatePageNum.value = value
  await loadTemplates()
}

const resetFilters = async () => {
  filters.q = ''
  filters.scene = ''
  filters.enabledOnly = false
  templatePageNum.value = 1
  await loadTemplates()
}

const openTemplateDialog = () => {
  templateForm.templateCode = ''
  templateForm.templateName = ''
  templateForm.scene = 'general_chat'
  templateForm.systemPrompt = ''
  templateForm.userPromptWrapper = ''
  templateForm.enabled = true
  templateDialogVisible.value = true
}

const openVersionDialog = () => {
  versionMode.value = 'create'
  editingVersionId.value = undefined
  versionForm.templateName = selectedTemplateName.value
  versionForm.scene = versions.value[0]?.scene || 'general_chat'
  versionForm.systemPrompt = ''
  versionForm.userPromptWrapper = ''
  versionForm.enabled = false
  versionDialogVisible.value = true
}

const openEditDialog = (row: AiPromptTemplateVersionView) => {
  versionMode.value = 'edit'
  editingVersionId.value = row.id
  versionForm.templateName = row.templateName
  versionForm.scene = row.scene
  versionForm.systemPrompt = row.systemPrompt
  versionForm.userPromptWrapper = row.userPromptWrapper || ''
  versionForm.enabled = row.enabled
  versionDialogVisible.value = true
}

const submitTemplate = async () => {
  submitting.value = true
  try {
    await createAiPromptTemplate({
      templateCode: templateForm.templateCode.trim(),
      templateName: templateForm.templateName.trim(),
      scene: templateForm.scene,
      systemPrompt: templateForm.systemPrompt.trim(),
      userPromptWrapper: templateForm.userPromptWrapper.trim() || undefined,
      enabled: templateForm.enabled,
    })
    ElMessage.success('Prompt 模板已创建')
    templateDialogVisible.value = false
    await loadTemplates()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'Prompt 模板创建失败'))
  } finally {
    submitting.value = false
  }
}

const submitVersion = async () => {
  submitting.value = true
  try {
    if (versionMode.value === 'edit' && editingVersionId.value) {
      await updateAiPromptTemplate(editingVersionId.value, {
        templateName: versionForm.templateName.trim(),
        scene: versionForm.scene,
        systemPrompt: versionForm.systemPrompt.trim(),
        userPromptWrapper: versionForm.userPromptWrapper.trim() || undefined,
      })
      ElMessage.success('Prompt 版本已更新')
    } else {
      await createAiPromptTemplateVersion(selectedTemplateCode.value, {
        templateName: versionForm.templateName.trim() || undefined,
        scene: versionForm.scene,
        systemPrompt: versionForm.systemPrompt.trim(),
        userPromptWrapper: versionForm.userPromptWrapper.trim() || undefined,
        enabled: versionForm.enabled,
      })
      ElMessage.success('Prompt 新版本已创建')
    }
    versionDialogVisible.value = false
    await loadTemplates()
    if (selectedTemplateCode.value) {
      await loadVersions(selectedTemplateCode.value)
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'Prompt 版本操作失败'))
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row: AiPromptTemplateVersionView) => {
  await updateAiPromptTemplateStatus(row.id, !row.enabled)
  ElMessage.success(row.enabled ? '已停用当前版本' : '已启用当前版本')
  await loadTemplates()
  await loadVersions(selectedTemplateCode.value)
}

onMounted(loadTemplates)
</script>

<style scoped>
.ai-prompts-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.prompt-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 18px;
}

.page-actions {
  display: flex;
  gap: 12px;
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

.w-full {
  width: 100%;
}

@media (max-width: 1200px) {
  .prompt-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .table-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
