<template>
  <el-card class="glass-card" shadow="never">
    <template #header>AI 模型配置</template>
    <el-table :data="models" stripe>
      <el-table-column prop="modelCode" label="模型编码" min-width="180" />
      <el-table-column prop="modelName" label="模型名称" min-width="180" />
      <el-table-column prop="providerCode" label="提供方" width="120" />
      <el-table-column label="启用" width="100">
        <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="默认" width="100">
        <template #default="scope">{{ scope.row.isDefault ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="流式" width="100">
        <template #default="scope">{{ scope.row.supportStream ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="maxContextTokens" label="上下文 Token" width="140" />
      <el-table-column prop="priorityNo" label="优先级" width="100" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="220" />
      <el-table-column prop="apiKeyRef" label="密钥引用" min-width="140" />
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