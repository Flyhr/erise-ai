<template>
  <div class="section-stack admin-models-page">
    <section class="admin-models__summary">
      <article class="admin-models__summary-card">
        <span class="material-symbols-outlined">deployed_code</span>
        <div>
          <strong>{{ models.length }}</strong>
          <span>已接入模型</span>
        </div>
      </article>

      <article class="admin-models__summary-card">
        <span class="material-symbols-outlined">verified</span>
        <div>
          <strong>{{ enabledCount }}</strong>
          <span>当前启用</span>
        </div>
      </article>

      <article class="admin-models__summary-card">
        <span class="material-symbols-outlined">token</span>
        <div>
          <strong>{{ maxContextLabel }}</strong>
          <span>最大上下文窗口</span>
        </div>
      </article>
    </section>

    <AppSectionCard title="AI 模型配置" :unpadded="true">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <span class="material-symbols-outlined admin-models__add-icon">add</span>
          添加模型
        </el-button>
      </template>

      <div v-if="loading" class="admin-models__state">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="rect" style="width: 100%; height: 56px; border-radius: 18px;" />
            <el-skeleton-item variant="rect"
              style="width: 100%; height: 72px; margin-top: 14px; border-radius: 16px;" />
            <el-skeleton-item variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;" />
            <el-skeleton-item variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;" />
          </template>
        </el-skeleton>
      </div>

      <el-result v-else-if="loadError" class="admin-models__state" icon="warning" title="模型配置加载失败"
        :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadModels">重新加载</el-button>
        </template>
      </el-result>

      <div v-else-if="models.length" class="admin-models__table-shell">
        <AppDataTable :data="models" stripe row-key="id" @row-click="openEditDialog">
          <el-table-column prop="modelCode" label="模型编码" min-width="170" />

          <el-table-column label="模型信息" min-width="160">
            <template #default="{ row }">
              <div class="admin-models__model-info">
                <strong>{{ row.modelName }}</strong>
                <span>{{ row.providerCode }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="配置摘要" min-width="260">
            <template #default="{ row }">
              <div class="admin-models__config-cell">
                <span>Base URL：{{ row.baseUrl || '未设置' }}</span>
                <span>API Key：{{ row.apiKeyRef ? '已配置' : '未设置' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <AppStatusTag :label="row.enabled ? '已启用' : '已停用'" :tone="row.enabled ? 'success' : 'warning'" />
            </template>
          </el-table-column>

          <el-table-column label="默认" width="96">
            <template #default="{ row }">
              <AppStatusTag :label="row.isDefault ? '是' : '否'" :tone="row.isDefault ? 'primary' : 'info'" />
            </template>
          </el-table-column>

          <el-table-column label="流式" width="104">
            <template #default="{ row }">
              <AppStatusTag :label="row.supportStream ? '支持' : '关闭'" :tone="row.supportStream ? 'success' : 'info'" />
            </template>
          </el-table-column>

          <el-table-column label="上下文窗口" width="100">
            <template #default="{ row }">{{ formatTokenCountInK(row.maxContextTokens) }}</template>
          </el-table-column>

          <el-table-column prop="priorityNo" label="优先级" width="94" />

          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" text @click.stop="openEditDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </AppDataTable>
      </div>

      <el-empty v-else :image-size="80" description="当前还没有可管理的模型配置。" />
    </AppSectionCard>

    <el-dialog v-model="dialogVisible" title="编辑模型配置" width="640px" :close-on-click-modal="false" destroy-on-close>
      <div class="admin-models__dialog-note">
        <span class="material-symbols-outlined">info</span>
        <span>默认模型同样可以编辑，系统只会用“默认”标签标记当前运行模型。</span>
      </div>

      <div class="admin-models__config-summary">
        <div>
          <span>模型型号</span>
          <strong>{{ form.modelCode || '--' }}</strong>
        </div>
        <div>
          <span>Base URL</span>
          <strong>{{ form.baseUrl || '未设置' }}</strong>
        </div>
        <div>
          <span>API Key</span>
          <strong>{{ form.apiKeyRef ? '已配置' : '未设置' }}</strong>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="admin-models__form-grid">
          <el-form-item label="模型编码">
            <el-input :model-value="form.modelCode" disabled />
          </el-form-item>

          <el-form-item label="默认模型">
            <el-input :model-value="form.isDefault ? '是' : '否'" disabled />
          </el-form-item>

          <el-form-item label="模型名称" prop="modelName">
            <el-input v-model="form.modelName" maxlength="120" show-word-limit />
          </el-form-item>

          <el-form-item label="提供方" prop="providerCode">
            <el-input v-model="form.providerCode" maxlength="64" placeholder="如 OPENAI、DEEPSEEK" />
          </el-form-item>

          <el-form-item label="Base URL">
            <el-input v-model="form.baseUrl" placeholder="如 https://api.openai.com/v1" />
          </el-form-item>

          <el-form-item label="API Key">
            <el-input v-model="form.apiKeyRef" type="password" show-password placeholder="输入 API Key" />
          </el-form-item>

          <el-form-item label="上下文 Tokens（K）" prop="maxContextTokensK">
            <el-input v-model="form.maxContextTokensK" placeholder="如 128 或 4.096">
              <template #append>K</template>
            </el-input>
          </el-form-item>

          <el-form-item label="优先级" prop="priorityNo">
            <el-input-number v-model="form.priorityNo" :min="0" :step="1" controls-position="right" />
          </el-form-item>

          <el-form-item label="能力开关" class="is-span-2">
            <div class="admin-models__switch-grid">
              <div class="admin-models__switch-card">
                <div>
                  <strong>启用模型</strong>
                  <span>控制该模型是否可被系统路由使用。</span>
                </div>
                <el-switch v-model="form.enabled" />
              </div>

              <div class="admin-models__switch-card">
                <div>
                  <strong>流式输出</strong>
                  <span>控制是否支持流式响应。</span>
                </div>
                <el-switch v-model="form.supportStream" />
              </div>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="添加模型" width="640px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <div class="admin-models__form-grid">
          <el-form-item label="模型编码" prop="modelCode" class="is-span-2">
            <el-input v-model="createForm.modelCode" maxlength="120" placeholder="如 deepseek-chat、gpt-4o" />
          </el-form-item>

          <el-form-item label="模型名称" prop="modelName">
            <el-input v-model="createForm.modelName" maxlength="120" placeholder="如 DeepSeek Chat" />
          </el-form-item>

          <el-form-item label="提供方" prop="providerCode">
            <el-input v-model="createForm.providerCode" maxlength="64" placeholder="如 OPENAI、DEEPSEEK" />
          </el-form-item>

          <el-form-item label="Base URL" prop="baseUrl" class="is-span-2">
            <el-input v-model="createForm.baseUrl" placeholder="如 https://api.openai.com/v1" />
          </el-form-item>

          <el-form-item label="API Key" prop="apiKeyRef" class="is-span-2">
            <el-input v-model="createForm.apiKeyRef" type="password" show-password placeholder="输入 API Key" />
          </el-form-item>

          <el-form-item label="上下文 Tokens（K）" prop="maxContextTokensK">
            <el-input v-model="createForm.maxContextTokensK" placeholder="如 128 或 4.096">
              <template #append>K</template>
            </el-input>
          </el-form-item>

          <el-form-item label="优先级" prop="priorityNo">
            <el-input-number v-model="createForm.priorityNo" :min="0" :step="1" controls-position="right" />
          </el-form-item>

          <el-form-item label="能力开关" class="is-span-2">
            <div class="admin-models__switch-grid">
              <div class="admin-models__switch-card">
                <div>
                  <strong>启用模型</strong>
                  <span>控制该模型是否可被系统路由使用。</span>
                </div>
                <el-switch v-model="createForm.enabled" />
              </div>

              <div class="admin-models__switch-card">
                <div>
                  <strong>流式输出</strong>
                  <span>控制是否支持流式响应。</span>
                </div>
                <el-switch v-model="createForm.supportStream" />
              </div>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">添加模型</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createAiModel, getAiModels, updateAiModel, type ModelConfigView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import { formatTokenCountInK, resolveErrorMessage, toEditableTokenCountK } from '@/utils/formatters'

interface ModelEditForm {
  id: number | null
  modelCode: string
  isDefault: boolean
  modelName: string
  providerCode: string
  enabled: boolean
  supportStream: boolean
  maxContextTokensK: string
  priorityNo: number
  baseUrl: string
  apiKeyRef: string
}

interface ModelCreateForm {
  modelCode: string
  modelName: string
  providerCode: string
  enabled: boolean
  supportStream: boolean
  maxContextTokensK: string
  priorityNo: number
  baseUrl: string
  apiKeyRef: string
}

const models = ref<ModelConfigView[]>([])
const loading = ref(true)
const loadError = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ModelEditForm>({
  id: null,
  modelCode: '',
  isDefault: false,
  modelName: '',
  providerCode: '',
  enabled: true,
  supportStream: true,
  maxContextTokensK: '',
  priorityNo: 0,
  baseUrl: '',
  apiKeyRef: '',
})

const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<ModelCreateForm>({
  modelCode: '',
  modelName: '',
  providerCode: '',
  enabled: true,
  supportStream: true,
  maxContextTokensK: '',
  priorityNo: 1,
  baseUrl: '',
  apiKeyRef: '',
})

const rules: FormRules<ModelEditForm> = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入提供方编码', trigger: 'blur' }],
  maxContextTokensK: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !value.trim()) {
          callback()
          return
        }
        const numericValue = Number(value)
        if (!Number.isFinite(numericValue) || numericValue <= 0) {
          callback(new Error('请输入大于 0 的 K 值'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
  priorityNo: [
    {
      validator: (_rule, value: number, callback) => {
        if (!Number.isFinite(value) || value < 0) {
          callback(new Error('优先级不能小于 0'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

const createRules: FormRules<ModelCreateForm> = {
  modelCode: [{ required: true, message: '请输入模型编码', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入提供方编码', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  apiKeyRef: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  maxContextTokensK: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !value.trim()) {
          callback()
          return
        }
        const n = Number(value)
        if (!Number.isFinite(n) || n <= 0) {
          callback(new Error('请输入大于 0 的 K 值'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
  priorityNo: [
    {
      validator: (_rule, value: number, callback) => {
        if (!Number.isFinite(value) || value < 0) {
          callback(new Error('优先级不能小于 0'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

const enabledCount = computed(() => models.value.filter((item) => item.enabled).length)

const maxContextLabel = computed(() => {
  const maxValue = models.value.reduce((currentMax, item) => Math.max(currentMax, item.maxContextTokens || 0), 0)
  return formatTokenCountInK(maxValue)
})

const resetForm = () => {
  form.id = null
  form.modelCode = ''
  form.isDefault = false
  form.modelName = ''
  form.providerCode = ''
  form.enabled = true
  form.supportStream = true
  form.maxContextTokensK = ''
  form.priorityNo = 0
  form.baseUrl = ''
  form.apiKeyRef = ''
}

const resetCreateForm = () => {
  createForm.modelCode = ''
  createForm.modelName = ''
  createForm.providerCode = ''
  createForm.enabled = true
  createForm.supportStream = true
  createForm.maxContextTokensK = ''
  createForm.priorityNo = 1
  createForm.baseUrl = ''
  createForm.apiKeyRef = ''
  createFormRef.value?.clearValidate()
}

const loadModels = async () => {
  loading.value = true
  loadError.value = ''
  try {
    models.value = await getAiModels()
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '模型配置加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const openEditDialog = (row: ModelConfigView) => {
  resetForm()
  form.id = row.id
  form.modelCode = row.modelCode
  form.isDefault = row.isDefault
  form.modelName = row.modelName
  form.providerCode = row.providerCode
  form.enabled = row.enabled
  form.supportStream = row.supportStream
  form.maxContextTokensK = toEditableTokenCountK(row.maxContextTokens)
  form.priorityNo = row.priorityNo ?? 0
  form.baseUrl = row.baseUrl || ''
  form.apiKeyRef = row.apiKeyRef || ''
  dialogVisible.value = true
}

const openCreateDialog = () => {
  resetCreateForm()
  createDialogVisible.value = true
}

const submitEdit = async () => {
  if (!formRef.value || form.id == null) return

  try {
    await formRef.value.validate()
    submitting.value = true
    const tokenValue = form.maxContextTokensK.trim()
    await updateAiModel(form.id, {
      modelName: form.modelName.trim(),
      providerCode: form.providerCode.trim().toUpperCase(),
      enabled: form.enabled,
      supportStream: form.supportStream,
      maxContextTokens: tokenValue ? Math.round(Number(tokenValue) * 1000) : null,
      priorityNo: form.priorityNo,
      baseUrl: form.baseUrl.trim(),
      apiKeyRef: form.apiKeyRef.trim(),
    })
    ElMessage.success('模型配置已更新')
    dialogVisible.value = false
    await loadModels()
  } catch (error) {
    if (error instanceof Error && error.message) {
      ElMessage.error(resolveErrorMessage(error, '模型配置更新失败，请稍后重试'))
    }
  } finally {
    submitting.value = false
  }
}

const submitCreate = async () => {
  if (!createFormRef.value) return

  try {
    await createFormRef.value.validate()
    creating.value = true
    const tokenValue = createForm.maxContextTokensK.trim()
    await createAiModel({
      modelCode: createForm.modelCode.trim(),
      modelName: createForm.modelName.trim(),
      providerCode: createForm.providerCode.trim().toUpperCase(),
      enabled: createForm.enabled,
      supportStream: createForm.supportStream,
      maxContextTokens: tokenValue ? Math.round(Number(tokenValue) * 1000) : null,
      priorityNo: createForm.priorityNo,
      baseUrl: createForm.baseUrl.trim(),
      apiKeyRef: createForm.apiKeyRef.trim(),
    })
    ElMessage.success('模型已添加')
    createDialogVisible.value = false
    await loadModels()
  } catch (error) {
    if (error instanceof Error && error.message) {
      ElMessage.error(resolveErrorMessage(error, '模型添加失败，请稍后重试'))
    }
  } finally {
    creating.value = false
  }
}

onMounted(loadModels)
</script>

<style scoped>
.admin-models-page {
  gap: 18px;
}

.admin-models__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.admin-models__summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(192, 199, 212, 0.24);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.admin-models__summary-card .material-symbols-outlined {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(0, 96, 169, 0.08);
  color: #0060a9;
  font-size: 22px;
}

.admin-models__summary-card strong {
  display: block;
  color: #101828;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.1;
}

.admin-models__summary-card span:last-child {
  color: #667085;
  font-size: 13px;
}

.admin-models__add-icon {
  font-size: 16px;
  margin-right: 4px;
}

.admin-models__state {
  padding: 24px;
}

.admin-models__table-shell {
  overflow: hidden;
}

.admin-models__table-shell :deep(.el-table__row) {
  cursor: pointer;
}

.admin-models__table-shell :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #f1f3fa;
  color: #5f6775;
  font-size: 12px;
  font-weight: 800;
}

.admin-models__table-shell :deep(.el-table tr) {
  transition: background-color 0.18s ease;
}

.admin-models__table-shell :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f8fbff;
}

.admin-models__table-shell :deep(.el-table td.el-table__cell) {
  padding-top: 18px;
  padding-bottom: 18px;
}

.admin-models__model-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-models__model-info strong {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.admin-models__model-info span {
  color: #667085;
  font-size: 12px;
}

.admin-models__config-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-models__config-cell span {
  color: #526071;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.admin-models__dialog-note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f5f8fc;
  color: #526071;
  font-size: 13px;
}

.admin-models__dialog-note .material-symbols-outlined {
  color: #0060a9;
  font-size: 18px;
}

.admin-models__config-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.admin-models__config-summary div {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(192, 199, 212, 0.24);
  background: #f8fafc;
}

.admin-models__config-summary span {
  display: block;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.admin-models__config-summary strong {
  display: block;
  margin-top: 4px;
  color: #101828;
  font-size: 13px;
  font-weight: 700;
  word-break: break-word;
}

.admin-models__form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.admin-models__form-grid .is-span-2 {
  grid-column: 1 / -1;
}

.admin-models__switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.admin-models__switch-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(192, 199, 212, 0.24);
  background: #f8fafc;
}

.admin-models__switch-card strong {
  display: block;
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.admin-models__switch-card span {
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .admin-models__summary {
    grid-template-columns: 1fr;
  }

  .admin-models__config-summary {
    grid-template-columns: 1fr;
  }

  .admin-models__form-grid,
  .admin-models__switch-grid {
    grid-template-columns: 1fr;
  }
}
</style>
