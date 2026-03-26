<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>项目中心</h1>
        <div class="page-subtitle">按项目组织文件、文档、搜索与 AI 上下文。</div>
      </div>
      <el-button type="primary" @click="dialogVisible = true">新建项目</el-button>
    </div>

    <div class="grid-3">
      <el-card v-for="project in projects" :key="project.id" class="glass-card" shadow="never">
        <div style="display: flex; justify-content: space-between; align-items: start; gap: 12px">
          <div>
            <div style="font-size: 18px; font-weight: 700">{{ project.name }}</div>
            <div class="page-subtitle">{{ project.description || '暂无描述' }}</div>
          </div>
          <el-tag :type="project.projectStatus === 'ACTIVE' ? 'success' : 'info'">{{ project.projectStatus }}</el-tag>
        </div>
        <div class="meta-row" style="margin: 16px 0">
          <span>{{ project.fileCount }} 文件</span>
          <span>{{ project.documentCount }} 文档</span>
        </div>
        <el-button type="primary" plain @click="$router.push(`/projects/${project.id}`)">进入项目</el-button>
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" title="创建项目" width="480px">
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject, getProjects } from '@/api/project'
import type { ProjectDetailView } from '@/types/models'

const projects = ref<ProjectDetailView[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ name: '', description: '' })

const load = async () => {
  const page = await getProjects({ pageNum: 1, pageSize: 30 })
  projects.value = page.records
}

const submit = async () => {
  submitting.value = true
  try {
    await createProject(form)
    ElMessage.success('项目已创建')
    dialogVisible.value = false
    form.name = ''
    form.description = ''
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
