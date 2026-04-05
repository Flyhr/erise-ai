<template>
  <div class="section-stack">
    <AppSectionCard title="AI 模型配置" description="核查模型启用状态、默认路由和上下文能力。" :unpadded="true">
      <AppDataTable :data="models" stripe>
        <el-table-column prop="modelCode" label="模型编码" min-width="180" />
        <el-table-column prop="modelName" label="模型名称" min-width="180" />
        <el-table-column prop="providerCode" label="提供方" width="120" />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <AppStatusTag :label="row.enabled ? '是' : '否'" :tone="row.enabled ? 'success' : 'warning'" />
          </template>
        </el-table-column>
        <el-table-column label="默认" width="100">
          <template #default="{ row }">{{ row.isDefault ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="流式" width="100">
          <template #default="{ row }">{{ row.supportStream ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="maxContextTokens" label="上下文令牌" width="140" />
        <el-table-column prop="priorityNo" label="优先级" width="100" />
        <el-table-column prop="baseUrl" label="服务地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="apiKeyRef" label="密钥引用" min-width="140" show-overflow-tooltip />
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAiModels, type ModelConfigView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'

const models = ref<ModelConfigView[]>([])

onMounted(async () => {
  models.value = await getAiModels()
})
</script>