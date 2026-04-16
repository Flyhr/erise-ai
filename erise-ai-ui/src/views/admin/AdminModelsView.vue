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
        <span class="material-symbols-outlined">star</span>
        <div>
          <strong>{{ defaultModelLabel }}</strong>
          <span>默认模型</span>
        </div>
      </article>
    </section>

    <AppSectionCard title="模型配置" description="管理模型启停、默认路由、优先级与成本单价。" :unpadded="true">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <span class="material-symbols-outlined admin-models__add-icon">add</span>
          新增模型
        </el-button>
      </template>

      <div v-if="loading" class="admin-models__state">
        <el-skeleton animated :rows="6" />
      </div>

      <el-result
        v-else-if="loadError"
        class="admin-models__state"
        icon="warning"
        title="模型配置加载失败"
        :sub-title="loadError"
      >
        <template #extra>
          <el-button type="primary" @click="loadModels">重新加载</el-button>
        </template>
      </el-result>

      <template v-else-if="models.length">
        <div class="admin-models__table-shell">
          <AppDataTable :data="models" stripe row-key="id" @row-click="openEditDialog">
            <el-table-column label="模型" min-width="220">
              <template #default="{ row }">
                <div class="admin-models__model-info">
                  <strong>{{ row.modelName }}</strong>
                  <span>{{ row.modelCode }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="提供方" min-width="120">
              <template #default="{ row }">{{ row.providerCode }}</template>
            </el-table-column>

            <el-table-column label="配置摘要" min-width="280">
              <template #default="{ row }">
                <div class="admin-models__config-cell">
                  <span>Base URL：{{ row.baseUrl || '未配置' }}</span>
                  <span>API Key：{{ row.apiKeyRef ? '已配置' : '未配置' }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <AppStatusTag :label="row.enabled ? '已启用' : '已停用'" :tone="row.enabled ? 'success' : 'warning'" />
              </template>
            </el-table-column>

            <el-table-column label="默认" width="96">
              <template #default="{ row }">
                <AppStatusTag :label="row.isDefault ? '默认' : '候选'" :tone="row.isDefault ? 'primary' : 'info'" />
              </template>
            </el-table-column>

            <el-table-column label="流式" width="96">
              <template #default="{ row }">
                <AppStatusTag :label="row.supportStream ? '支持' : '关闭'" :tone="row.supportStream ? 'success' : 'info'" />
              </template>
            </el-table-column>

            <el-table-column label="上下文" width="110">
              <template #default="{ row }">{{ formatTokenCountInK(row.maxContextTokens) }}</template>
            </el-table-column>

            <el-table-column label="输入单价" width="140">
              <template #default="{ row }">{{ priceLabel(row.inputPricePerMillion, row.currencyCode) }}</template>
            </el-table-column>

            <el-table-column label="输出单价" width="140">
              <template #default="{ row }">{{ priceLabel(row.outputPricePerMillion, row.currencyCode) }}</template>
            </el-table-column>

            <el-table-column prop="priorityNo" label="优先级" width="94" />

            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <div class="admin-models__actions">
                  <el-button type="primary" text @click.stop="openEditDialog(row)">编辑</el-button>
                  <el-button
                    text
                    :disabled="row.isDefault || !row.enabled || switchingDefaultId === row.id"
                    @click.stop="handleSwitchDefault(row)"
                  >
                    {{ row.isDefault ? '当前默认' : '设为默认' }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </AppDataTable>
        </div>
      </template>

      <el-empty v-else :image-size="80" description="当前还没有可管理的模型配置。" />
    </AppSectionCard>

    <el-dialog v-model="dialogVisible" title="编辑模型配置" width="720px" :close-on-click-modal="false" destroy-on-close>
      <div class="admin-models__dialog-note">
        <span class="material-symbols-outlined">info</span>
        <span>默认模型只允许一个。启停、优先级与单价修改会直接影响 AI 路由与成本统计。</span>
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
            <el-input v-model="form.providerCode" maxlength="64" placeholder="例如 DEEPSEEK、OPENAI" />
          </el-form-item>

          <el-form-item label="Base URL">
            <el-input v-model="form.baseUrl" placeholder="例如 https://api.ofox.ai/v1" />
          </el-form-item>

          <el-form-item label="API Key">
            <el-input v-model="form.apiKeyRef" type="password" show-password placeholder="输入 API Key 或引用值" />
          </el-form-item>

          <el-form-item label="上下文 Tokens(K)" prop="maxContextTokensK">
            <el-input v-model="form.maxContextTokensK" placeholder="例如 128">
              <template #append>K</template>
            </el-input>
          </el-form-item>

          <el-form-item label="优先级" prop="priorityNo">
            <el-input-number v-model="form.priorityNo" :min="0" :step="1" controls-position="right" />
          </el-form-item>

          <el-form-item label="输入单价 / 百万 Tokens">
            <el-input-number v-model="form.inputPricePerMillion" :min="0" :precision="4" :step="0.1" controls-position="right" />
          </el-form-item>

          <el-form-item label="输出单价 / 百万 Tokens">
            <el-input-number v-model="form.outputPricePerMillion" :min="0" :precision="4" :step="0.1" controls-position="right" />
          </el-form-item>

          <el-form-item label="币种">
            <el-input v-model="form.currencyCode" maxlength="16" placeholder="例如 CNY、USD" />
          </el-form-item>

          <el-form-item label="能力开关" class="is-span-2">
            <div class="admin-models__switch-grid">
              <div class="admin-models__switch-card">
                <div>
                  <strong>启用模型</strong>
                  <span>关闭后不再参与模型路由，也不能被设为默认模型。</span>
                </div>
                <el-switch v-model="form.enabled" />
              </div>

              <div class="admin-models__switch-card">
                <div>
                  <strong>流式输出</strong>
                  <span>控制当前模型是否支持流式回复。</span>
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

    <el-dialog v-model="createDialogVisible" title="新增模型" width="720px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <div class="admin-models__form-grid">
          <el-form-item label="模型编码" prop="modelCode" class="is-span-2">
            <el-input v-model="createForm.modelCode" maxlength="120" placeholder="例如 deepseek-chat、gpt-4.1-mini" />
          </el-form-item>

          <el-form-item label="模型名称" prop="modelName">
            <el-input v-model="createForm.modelName" maxlength="120" />
          </el-form-item>

          <el-form-item label="提供方" prop="providerCode">
            <el-input v-model="createForm.providerCode" maxlength="64" placeholder="例如 DEEPSEEK、OPENAI" />
          </el-form-item>

          <el-form-item label="Base URL" prop="baseUrl">
            <el-input v-model="createForm.baseUrl" placeholder="例如 https://api.ofox.ai/v1" />
          </el-form-item>

          <el-form-item label="API Key" prop="apiKeyRef">
            <el-input v-model="createForm.apiKeyRef" type="password" show-password placeholder="输入 API Key 或引用值" />
          </el-form-item>

          <el-form-item label="上下文 Tokens(K)" prop="maxContextTokensK">
            <el-input v-model="createForm.maxContextTokensK" placeholder="例如 128">
              <template #append>K</template>
            </el-input>
          </el-form-item>

          <el-form-item label="优先级" prop="priorityNo">
            <el-input-number v-model="createForm.priorityNo" :min="0" :step="1" controls-position="right" />
          </el-form-item>

          <el-form-item label="输入单价 / 百万 Tokens">
            <el-input-number v-model="createForm.inputPricePerMillion" :min="0" :precision="4" :step="0.1" controls-position="right" />
          </el-form-item>

          <el-form-item label="输出单价 / 百万 Tokens">
            <el-input-number v-model="createForm.outputPricePerMillion" :min="0" :precision="4" :step="0.1" controls-position="right" />
          </el-form-item>

          <el-form-item label="币种">
            <el-input v-model="createForm.currencyCode" maxlength="16" placeholder="例如 CNY、USD" />
          </el-form-item>

          <el-form-item label="能力开关" class="is-span-2">
            <div class="admin-models__switch-grid">
              <div class="admin-models__switch-card">
                <div>
                  <strong>启用模型</strong>
                  <span>创建后立即参与模型列表与路由。</span>
                </div>
                <el-switch v-model="createForm.enabled" />
              </div>

              <div class="admin-models__switch-card">
                <div>
                  <strong>流式输出</strong>
                  <span>创建后默认允许流式回复。</span>
                </div>
                <el-switch v-model="createForm.supportStream" />
              </div>

              <div class="admin-models__switch-card">
                <div>
                  <strong>设为默认模型</strong>
                  <span>创建完成后立即切换成系统默认模型。</span>
                </div>
                <el-switch v-model="createForm.isDefault" />
              </div>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">新增模型</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createAiModel,
  getAiModels,
  switchDefaultAiModel,
  updateAiModel,
  type ModelConfigView,
} from '@/api/admin'
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
  inputPricePerMillion?: number
  outputPricePerMillion?: number
  currencyCode: string
  baseUrl: string
  apiKeyRef: string
}

interface ModelCreateForm {
  modelCode: string
  modelName: string
  providerCode: string
  enabled: boolean
  isDefault: boolean
  supportStream: boolean
  maxContextTokensK: string
  priorityNo: number
  inputPricePerMillion?: number
  outputPricePerMillion?: number
  currencyCode: string
  baseUrl: string
  apiKeyRef: string
}

const models = ref<ModelConfigView[]>([])
const loading = ref(true)
const loadError = ref('')
const dialogVisible = ref(false)
const createDialogVisible = ref(false)
const submitting = ref(false)
const creating = ref(false)
const switchingDefaultId = ref<number>()
const formRef = ref<FormInstance>()
const createFormRef = ref<FormInstance>()

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
  inputPricePerMillion: undefined,
  outputPricePerMillion: undefined,
  currencyCode: 'CNY',
  baseUrl: '',
  apiKeyRef: '',
})

const createForm = reactive<ModelCreateForm>({
  modelCode: '',
  modelName: '',
  providerCode: '',
  enabled: true,
  isDefault: false,
  supportStream: true,
  maxContextTokensK: '',
  priorityNo: 1,
  inputPricePerMillion: undefined,
  outputPricePerMillion: undefined,
  currencyCode: 'CNY',
  baseUrl: '',
  apiKeyRef: '',
})

const tokenValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
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
}

const rules: FormRules<ModelEditForm> = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入提供方编码', trigger: 'blur' }],
  maxContextTokensK: [{ validator: tokenValidator, trigger: ['blur', 'change'] }],
}

const createRules: FormRules<ModelCreateForm> = {
  modelCode: [{ required: true, message: '请输入模型编码', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入提供方编码', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  apiKeyRef: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  maxContextTokensK: [{ validator: tokenValidator, trigger: ['blur', 'change'] }],
}

const enabledCount = computed(() => models.value.filter((item) => item.enabled).length)
const defaultModel = computed(() => models.value.find((item) => item.isDefault))
const defaultModelLabel = computed(() => defaultModel.value?.modelName || '--')

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
  form.inputPricePerMillion = undefined
  form.outputPricePerMillion = undefined
  form.currencyCode = 'CNY'
  form.baseUrl = ''
  form.apiKeyRef = ''
  formRef.value?.clearValidate()
}

const resetCreateForm = () => {
  createForm.modelCode = ''
  createForm.modelName = ''
  createForm.providerCode = ''
  createForm.enabled = true
  createForm.isDefault = false
  createForm.supportStream = true
  createForm.maxContextTokensK = ''
  createForm.priorityNo = 1
  createForm.inputPricePerMillion = undefined
  createForm.outputPricePerMillion = undefined
  createForm.currencyCode = 'CNY'
  createForm.baseUrl = ''
  createForm.apiKeyRef = ''
  createFormRef.value?.clearValidate()
}

const sortModels = (items: ModelConfigView[]) =>
  [...items].sort((left, right) => {
    const defaultDiff = Number(Boolean(right.isDefault)) - Number(Boolean(left.isDefault))
    if (defaultDiff !== 0) {
      return defaultDiff
    }
    return (left.priorityNo ?? 999) - (right.priorityNo ?? 999)
  })

const loadModels = async () => {
  loading.value = true
  loadError.value = ''
  try {
    models.value = sortModels(await getAiModels())
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '模型配置加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const priceLabel = (value?: number, currencyCode?: string) => {
  if (value == null) {
    return '--'
  }
  return `${value.toFixed(value >= 10 ? 2 : 4)} ${currencyCode || 'CNY'}`
}

const normalizeNullableNumber = (value?: number) => (value == null || Number.isNaN(value) ? null : value)

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
  form.inputPricePerMillion = row.inputPricePerMillion
  form.outputPricePerMillion = row.outputPricePerMillion
  form.currencyCode = row.currencyCode || 'CNY'
  form.baseUrl = row.baseUrl || ''
  form.apiKeyRef = row.apiKeyRef || ''
  dialogVisible.value = true
}

const openCreateDialog = () => {
  resetCreateForm()
  createDialogVisible.value = true
}

const handleSwitchDefault = async (row: ModelConfigView) => {
  if (!row.id || row.isDefault) {
    return
  }
  switchingDefaultId.value = row.id
  try {
    await switchDefaultAiModel(row.id)
    ElMessage.success(`已将 ${row.modelName} 设为默认模型`)
    await loadModels()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '默认模型切换失败，请稍后重试'))
  } finally {
    switchingDefaultId.value = undefined
  }
}

const submitEdit = async () => {
  if (!formRef.value || form.id == null) {
    return
  }
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
      inputPricePerMillion: normalizeNullableNumber(form.inputPricePerMillion),
      outputPricePerMillion: normalizeNullableNumber(form.outputPricePerMillion),
      currencyCode: form.currencyCode.trim().toUpperCase() || 'CNY',
      priorityNo: form.priorityNo,
      baseUrl: form.baseUrl.trim(),
      apiKeyRef: form.apiKeyRef.trim(),
    })
    ElMessage.success('模型配置已更新')
    dialogVisible.value = false
    await loadModels()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '模型配置更新失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

const submitCreate = async () => {
  if (!createFormRef.value) {
    return
  }
  try {
    await createFormRef.value.validate()
    creating.value = true
    const tokenValue = createForm.maxContextTokensK.trim()
    await createAiModel({
      modelCode: createForm.modelCode.trim(),
      modelName: createForm.modelName.trim(),
      providerCode: createForm.providerCode.trim().toUpperCase(),
      enabled: createForm.enabled,
      isDefault: createForm.isDefault,
      supportStream: createForm.supportStream,
      maxContextTokens: tokenValue ? Math.round(Number(tokenValue) * 1000) : null,
      inputPricePerMillion: normalizeNullableNumber(createForm.inputPricePerMillion),
      outputPricePerMillion: normalizeNullableNumber(createForm.outputPricePerMillion),
      currencyCode: createForm.currencyCode.trim().toUpperCase() || 'CNY',
      priorityNo: createForm.priorityNo,
      baseUrl: createForm.baseUrl.trim(),
      apiKeyRef: createForm.apiKeyRef.trim(),
    })
    ElMessage.success('模型已新增')
    createDialogVisible.value = false
    await loadModels()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '模型新增失败，请稍后重试'))
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

.admin-models__table-shell :deep(.el-table td.el-table__cell) {
  padding-top: 18px;
  padding-bottom: 18px;
}

.admin-models__model-info,
.admin-models__config-cell,
.admin-models__actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-models__model-info strong {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.admin-models__model-info span,
.admin-models__config-cell span {
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.admin-models__actions {
  align-items: flex-start;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

@media (max-width: 1080px) {
  .admin-models__summary,
  .admin-models__switch-grid,
  .admin-models__form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
