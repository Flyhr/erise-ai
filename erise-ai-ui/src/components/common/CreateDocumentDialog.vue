<template>
  <el-dialog v-model="visible" title="新建文档" width="460px" @close="resetForm">
    <el-form :model="form" label-position="top" @submit.prevent="handleSubmit">
      <el-form-item label="文档名称" required>
        <el-input v-model="form.title" placeholder="输入文档名称" clearable @keyup.enter="handleSubmit" />
      </el-form-item>
      <el-form-item label="文档描述">
        <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="可选的文档描述" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">进入编辑</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  success: [title: string]
}>()

const router = useRouter()
const visible = ref(false)
const submitting = ref(false)

const form = ref({
  title: '',
  summary: '',
})

const resetForm = () => {
  form.value = {
    title: '',
    summary: '',
  }
}

const handleSubmit = async () => {
  const title = form.value.title.trim()
  if (!title) {
    ElMessage.warning('请输入文档名称')
    return
  }

  submitting.value = true
  try {
    await router.push({
      path: '/documents/new/edit',
      query: {
        projectId: props.projectId,
        title,
        ...(form.value.summary.trim() ? { summary: form.value.summary.trim() } : {}),
      },
    })
    emit('success', title)
    visible.value = false
  } finally {
    submitting.value = false
  }
}

const open = () => {
  visible.value = true
}

defineExpose({
  open,
})
</script>

<style scoped></style>
