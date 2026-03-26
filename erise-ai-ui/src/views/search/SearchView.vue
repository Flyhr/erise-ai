<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>全局搜索</h1>
        <div class="page-subtitle">在文件名、文档标题、文档正文和知识块之间做权限过滤后的关键词检索。</div>
      </div>
    </div>

    <el-card class="glass-card" shadow="never">
      <div style="display: grid; grid-template-columns: 1fr 220px 120px; gap: 12px">
        <el-input v-model="query" placeholder="输入关键词" @keyup.enter="runSearch" />
        <el-input-number v-model="projectId" :min="1" placeholder="项目 ID" />
        <el-button type="primary" @click="runSearch">搜索</el-button>
      </div>
    </el-card>

    <el-card class="glass-card" shadow="never">
      <template #header>搜索结果</template>
      <div v-if="results.length" class="section-stack">
        <div v-for="item in results" :key="`${item.sourceType}-${item.sourceId}`" class="glass-card" style="padding: 16px">
          <div style="display: flex; justify-content: space-between; gap: 12px">
            <div>
              <div style="font-size: 16px; font-weight: 600">{{ item.title }}</div>
              <div class="meta-row">
                <span>{{ item.sourceType }}</span>
                <span>项目 #{{ item.projectId }}</span>
              </div>
              <div class="page-subtitle">{{ item.snippet || '无摘要' }}</div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-box">输入关键词开始检索。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { search } from '@/api/search'
import type { SearchResultView } from '@/types/models'

const route = useRoute()
const query = ref(String(route.query.q || ''))
const projectId = ref<number | undefined>(route.query.projectId ? Number(route.query.projectId) : undefined)
const results = ref<SearchResultView[]>([])

const runSearch = async () => {
  if (!query.value) return
  const page = await search({ q: query.value, projectId: projectId.value, pageNum: 1, pageSize: 50 })
  results.value = page.records
}

onMounted(() => {
  if (query.value) {
    runSearch()
  }
})
</script>
