<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ project?.name }}</h1>
        <div class="page-subtitle">{{ project?.description || '围绕这个项目组织文件、文档和问答。' }}</div>
      </div>
      <el-button plain @click="$router.push('/projects')">返回列表</el-button>
    </div>

    <div class="grid-3" v-if="project">
      <el-card class="glass-card" shadow="never"><strong>{{ project.fileCount }}</strong><div class="muted">文件数量</div></el-card>
      <el-card class="glass-card" shadow="never"><strong>{{ project.documentCount }}</strong><div class="muted">文档数量</div></el-card>
      <el-card class="glass-card" shadow="never"><strong>{{ project.projectStatus }}</strong><div class="muted">项目状态</div></el-card>
    </div>

    <el-card class="glass-card" shadow="never">
      <template #header>项目入口</template>
      <div style="display: flex; gap: 12px; flex-wrap: wrap">
        <el-button type="primary" @click="$router.push(`/projects/${projectId}/files`)">文件库</el-button>
        <el-button type="primary" plain @click="$router.push(`/projects/${projectId}/documents`)">文档库</el-button>
        <el-button type="primary" plain @click="$router.push(`/projects/${projectId}/ai`)">项目 AI</el-button>
        <el-button plain @click="$router.push({ path: '/search', query: { projectId } })">搜索</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getProject } from '@/api/project'
import type { ProjectDetailView } from '@/types/models'

const props = defineProps<{ id: string }>()
const projectId = Number(props.id)
const project = ref<ProjectDetailView>()

onMounted(async () => {
  project.value = await getProject(projectId)
})
</script>
