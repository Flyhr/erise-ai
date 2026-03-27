<template>
  <div class="data-table-editor">
    <div v-if="!readonly" class="data-toolbar">
      <el-button plain @click="appendColumn">新增字段</el-button>
      <el-button plain @click="appendRow">新增记录</el-button>
    </div>

    <div class="field-grid">
      <div v-for="(column, index) in localModel.columns" :key="column.key" class="field-card glass-card">
        <div class="field-card__title">字段 {{ index + 1 }}</div>
        <el-input v-model="column.label" :disabled="readonly" placeholder="字段名称" @input="handleColumnChange(index)" />
        <el-select v-model="column.type" :disabled="readonly" @change="emitChange">
          <el-option label="文本" value="TEXT" />
          <el-option label="数字" value="NUMBER" />
          <el-option label="日期" value="DATE" />
        </el-select>
      </div>
    </div>

    <div class="data-table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th v-for="column in localModel.columns" :key="`head-${column.key}`">{{ column.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in localModel.rows" :key="`row-${rowIndex}`">
            <td v-for="column in localModel.columns" :key="`${rowIndex}-${column.key}`">
              <el-input v-model="row[column.key]" :disabled="readonly" @input="emitChange" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

interface DataColumn {
  key: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'DATE'
}

interface DataTableModel {
  columns: DataColumn[]
  rows: Array<Record<string, string>>
}

const props = defineProps<{
  modelValue: DataTableModel
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: DataTableModel]
}>()

const cloneModel = (value: DataTableModel): DataTableModel => ({
  columns: value.columns.map((column) => ({ ...column })),
  rows: value.rows.map((row) => ({ ...row })),
})

const localModel = reactive<DataTableModel>(cloneModel(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    localModel.columns = value.columns.map((column) => ({ ...column }))
    localModel.rows = value.rows.map((row) => ({ ...row }))
  },
  { deep: true },
)

const emitChange = () => emit('update:modelValue', cloneModel(localModel))

const appendColumn = () => {
  const key = `field_${localModel.columns.length + 1}`
  localModel.columns.push({ key, label: `字段 ${localModel.columns.length + 1}`, type: 'TEXT' })
  localModel.rows.forEach((row) => {
    row[key] = ''
  })
  emitChange()
}

const appendRow = () => {
  const row: Record<string, string> = {}
  localModel.columns.forEach((column) => {
    row[column.key] = ''
  })
  localModel.rows.push(row)
  emitChange()
}

const handleColumnChange = (index: number) => {
  const column = localModel.columns[index]
  if (!column) return
  if (!column.label.trim()) {
    column.label = `字段 ${index + 1}`
  }
  emitChange()
}
</script>

<style scoped>
.data-table-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.data-toolbar {
  display: flex;
  gap: 12px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.field-card {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-card__title {
  font-weight: 700;
}

.data-table-wrap {
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
}

.data-table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  border: 1px solid rgba(148, 163, 184, 0.22);
  padding: 10px;
}

.data-table th {
  background: rgba(51, 65, 85, 0.06);
  font-weight: 700;
}
</style>