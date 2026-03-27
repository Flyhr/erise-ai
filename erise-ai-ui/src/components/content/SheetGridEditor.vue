<template>
  <div class="sheet-editor">
    <div v-if="!readonly" class="sheet-toolbar">
      <el-button plain @click="appendColumn">新增列</el-button>
      <el-button plain @click="appendRow">新增行</el-button>
    </div>

    <div class="sheet-table-wrap">
      <table class="sheet-table">
        <thead>
          <tr>
            <th>#</th>
            <th v-for="(_, columnIndex) in localModel.columns" :key="`head-${columnIndex}`">{{ columnLabel(columnIndex) }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in localModel.rows" :key="`row-${rowIndex}`">
            <td class="sheet-index">{{ rowIndex + 1 }}</td>
            <td v-for="(_, columnIndex) in localModel.columns" :key="`cell-${rowIndex}-${columnIndex}`">
              <el-input v-model="row[columnIndex]" :disabled="readonly" @input="emitChange" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

interface SheetModel {
  columns: number
  rows: string[][]
}

const props = defineProps<{
  modelValue: SheetModel
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SheetModel]
}>()

const cloneModel = (value: SheetModel): SheetModel => ({
  columns: value.columns,
  rows: value.rows.map((row) => [...row]),
})

const localModel = reactive<SheetModel>(cloneModel(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    localModel.columns = value.columns
    localModel.rows = value.rows.map((row) => [...row])
  },
  { deep: true },
)

const emitChange = () => {
  emit('update:modelValue', cloneModel(localModel))
}

const appendColumn = () => {
  localModel.columns += 1
  localModel.rows.forEach((row) => row.push(''))
  emitChange()
}

const appendRow = () => {
  localModel.rows.push(Array.from({ length: localModel.columns }, () => ''))
  emitChange()
}

const columnLabel = (index: number) => {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  let label = ''
  let current = index
  do {
    label = alphabet[current % 26] + label
    current = Math.floor(current / 26) - 1
  } while (current >= 0)
  return label
}
</script>

<style scoped>
.sheet-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sheet-toolbar {
  display: flex;
  gap: 12px;
}

.sheet-table-wrap {
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
}

.sheet-table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
}

.sheet-table th,
.sheet-table td {
  border: 1px solid rgba(148, 163, 184, 0.22);
  padding: 10px;
}

.sheet-table th {
  background: rgba(15, 118, 110, 0.06);
  font-weight: 700;
}

.sheet-index {
  width: 68px;
  text-align: center;
  color: var(--muted);
  font-weight: 600;
}
</style>