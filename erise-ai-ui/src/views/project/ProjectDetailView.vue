<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ project?.name }}</h1>
        <div class="page-subtitle">{{ project?.description || '围绕这个项目组织文件、文档、表格、画板、数据表和 AI 对话。' }}</div>
      </div>
      <el-button plain @click="$router.push('/projects')">返回列表</el-button>
    </div>

    <div class="grid-3" v-if="project">
      <el-card class="glass-card metric-card" shadow="never">
        <div class="metric-card__label">文件数量</div>
        <div class="metric-card__value">{{ project.fileCount }}</div>
      </el-card>
      <el-card class="glass-card metric-card" shadow="never">
        <div class="metric-card__label">文档数量</div>
        <div class="metric-card__value">{{ project.documentCount }}</div>
      </el-card>
      <el-card class="glass-card metric-card" shadow="never">
        <div class="metric-card__label">项目状态</div>
        <div class="metric-card__value" style="font-size: 24px">{{ project.projectStatus }}</div>
      </el-card>
    </div>

    <el-card class="glass-card" shadow="never">
      <template #header>项目入口</template>
      <div class="entry-grid">
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/files`)">
          <strong>文件库</strong>
          <span>上传、预览、下载和 Office 文件在线编辑。</span>
        </button>
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/documents`)">
          <strong>文档库</strong>
          <span>富文档编辑、版本发布和知识同步。</span>
        </button>
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/contents/sheet`)">
          <strong>表格</strong>
          <span>用于排期、清单和轻量表格内容。</span>
        </button>
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/contents/board`)">
          <strong>画板</strong>
          <span>支持草图、标注和自由笔迹。</span>
        </button>
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/contents/data-table`)">
          <strong>数据表</strong>
          <span>管理字段和结构化记录。</span>
        </button>
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/ai`)">
          <strong>项目 AI</strong>
          <span>基于项目知识进行问答和摘要。</span>
        </button>
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

<style scoped>
.entry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.entry-card {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.78);
  cursor: pointer;
}

.entry-card strong {
  font-size: 18px;
}

.entry-card span {
  color: var(--muted);
  line-height: 1.7;
}
</style>