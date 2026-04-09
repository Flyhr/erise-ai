import { ElMessage } from 'element-plus'
import { previewFileBinary, previewOfficeFile } from '@/api/file'
import { isOfficeEditableFile, resolveErrorMessage } from '@/utils/formatters'

interface PreviewTarget {
  id: number
  fileExt?: string
}

export const useFilePreview = () => {
  const previewFile = async (target: PreviewTarget, fallbackMessage = '文件预览失败，请稍后重试') => {
    try {
      if (isOfficeEditableFile(target.fileExt)) {
        await previewOfficeFile(target.id)
        return
      }
      await previewFileBinary(target.id)
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, fallbackMessage))
    }
  }

  return {
    previewFile,
  }
}
