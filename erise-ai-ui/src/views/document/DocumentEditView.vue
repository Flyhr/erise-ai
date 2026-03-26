<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ form.title || '文档编辑' }}</h1>
        <div class="page-subtitle">草稿保存写入结构化内容，发布后会重建知识块供搜索和 AI 使用。</div>
      </div>
      <div style="display: flex; gap: 12px">
        <el-button :loading="saving" @click="save">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布版本</el-button>
      </div>
    </div>

    <el-card class="glass-card" shadow="never">
      <el-form :model="form" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <div style="border: 1px solid var(--line); border-radius: 18px; overflow: hidden">
        <div style="display: flex; gap: 8px; padding: 12px; border-bottom: 1px solid var(--line)">
          <el-button size="small" @click="editor?.chain().focus().toggleBold().run()">粗体</el-button>
          <el-button size="small" @click="editor?.chain().focus().toggleItalic().run()">斜体</el-button>
          <el-button size="small" @click="editor?.chain().focus().toggleBulletList().run()">列表</el-button>
          <el-button size="small" @click="editor?.chain().focus().toggleCodeBlock().run()">代码块</el-button>
        </div>
        <editor-content :editor="editor" style="min-height: 420px; padding: 18px" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import StarterKit from '@tiptap/starter-kit'
import { Editor, EditorContent } from '@tiptap/vue-3'
import { getDocument, publishDocument, updateDocument } from '@/api/document'

const props = defineProps<{ id: string }>()
const form = reactive({ title: '', summary: '' })
const saving = ref(false)
const publishing = ref(false)
const documentId = Number(props.id)
const editor = ref<Editor>()

const save = async () => {
  if (!editor.value) return
  saving.value = true
  try {
    await updateDocument(documentId, {
      title: form.title,
      summary: form.summary,
      contentJson: JSON.stringify(editor.value.getJSON()),
      contentHtmlSnapshot: editor.value.getHTML(),
      plainText: editor.value.getText(),
    })
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

const publish = async () => {
  await save()
  publishing.value = true
  try {
    await publishDocument(documentId)
    ElMessage.success('文档已发布')
  } finally {
    publishing.value = false
  }
}

onMounted(async () => {
  const detail = await getDocument(documentId)
  form.title = detail.title
  form.summary = detail.summary || ''
  editor.value = new Editor({
    extensions: [StarterKit],
    content: detail.contentHtmlSnapshot || '<p></p>',
  })
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>
