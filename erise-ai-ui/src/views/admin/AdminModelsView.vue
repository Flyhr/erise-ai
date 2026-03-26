<template>
  <el-card class="glass-card" shadow="never">
    <template #header>模型配置</template>
    <el-table :data="models" stripe>
      <el-table-column prop="modelName" label="模型名" />
      <el-table-column prop="providerCode" label="提供方" width="160" />
      <el-table-column prop="enabled" label="启用" width="120" />
      <el-table-column prop="isDefault" label="默认" width="120" />
      <el-table-column prop="configJson" label="配置" min-width="260" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAiModels, type ModelConfigView } from '@/api/admin'

const models = ref<ModelConfigView[]>([])

onMounted(async () => {
  models.value = await getAiModels()
})
</script>
