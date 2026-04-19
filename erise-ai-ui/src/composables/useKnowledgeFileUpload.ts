import { ElMessage } from 'element-plus'
import { completeUpload, initUpload, uploadFileBinary } from '@/api/file'
import { trackKnowledgeStatusRecord } from '@/composables/useKnowledgeStatusPolling'
import type { FileView } from '@/types/models'
import { resolveErrorMessage } from '@/utils/formatters'

const KNOWLEDGE_FILE_EXTENSIONS = ['doc', 'docx', 'pdf', 'md', 'txt']

export const knowledgeFileAccept =
  '.doc,.docx,.pdf,.md,.txt,text/plain,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'

interface UseKnowledgeFileUploadOptions {
  resolveProjectId: () => number | undefined
  onUploaded?: (uploadedFile: FileView) => Promise<void> | void
}

export const useKnowledgeFileUpload = ({ resolveProjectId, onUploaded }: UseKnowledgeFileUploadOptions) => {
  const beforeUpload = async (rawFile: File) => {
    const projectId = resolveProjectId()
    if (!projectId) {
      ElMessage.warning('请先选择项目，再上传知识文件。')
      return false
    }

    const extension = rawFile.name.includes('.') ? rawFile.name.split('.').pop()?.toLowerCase() || '' : ''
    if (!KNOWLEDGE_FILE_EXTENSIONS.includes(extension)) {
      ElMessage.warning('当前仅支持上传 PDF、Word、TXT 和 Markdown 文件。')
      return false
    }

    try {
      const init = await initUpload({
        projectId,
        fileName: rawFile.name,
        fileSize: rawFile.size,
        mimeType: rawFile.type || 'application/octet-stream',
      })
      await uploadFileBinary(init.fileId, rawFile)
      const uploadedFile = await completeUpload(init.fileId)
      trackKnowledgeStatusRecord(`FILE:${init.fileId}`)
      await onUploaded?.(uploadedFile)
      ElMessage.success('文件上传成功，已进入解析队列。')
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, '文件上传失败，请稍后重试'))
    }

    return false
  }

  return {
    beforeUpload,
    knowledgeFileAccept,
  }
}
