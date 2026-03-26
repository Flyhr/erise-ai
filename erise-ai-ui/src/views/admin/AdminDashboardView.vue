<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>管理仪表盘</h1>
        <div class="page-subtitle">查看用户、项目、文件和文档规模，作为 V1 的整体运行快照。</div>
      </div>
    </div>

    <div class="grid-2">
      <el-card class="glass-card" shadow="never"><strong>{{ overview?.userCount }}</strong><div class="muted">用户数</div></el-card>
      <el-card class="glass-card" shadow="never"><strong>{{ overview?.projectCount }}</strong><div class="muted">项目数</div></el-card>
      <el-card class="glass-card" shadow="never"><strong>{{ overview?.fileCount }}</strong><div class="muted">文件数</div></el-card>
      <el-card class="glass-card" shadow="never"><strong>{{ overview?.documentCount }}</strong><div class="muted">文档数</div></el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminOverview, type AdminOverviewView } from '@/api/admin'

const overview = ref<AdminOverviewView>()

onMounted(async () => {
  overview.value = await getAdminOverview()
})
</script>
