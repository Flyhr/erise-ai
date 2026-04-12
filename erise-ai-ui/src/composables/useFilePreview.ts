import { ElLoading, ElMessage } from 'element-plus'
import { previewFileBinary, previewOfficeFile } from '@/api/file'
import { isOfficeEditableFile, resolveErrorMessage } from '@/utils/formatters'

interface PreviewTarget {
  id: number
  fileExt?: string
}

export const useFilePreview = () => {
  const previewFile = async (target: PreviewTarget, fallbackMessage = '文件预览失败，请稍后重试') => {
    const loading = ElLoading.service({
      lock: true,
      text: '正在加载文件预览，请稍候...',
      background: 'rgba(255, 255, 255, 0.82)',
    })

    try {
      if (isOfficeEditableFile(target.fileExt)) {
        await previewOfficeFile(target.id)
        return
      }
      await previewFileBinary(target.id)
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, fallbackMessage))
    } finally {
      loading.close()
    }
  }

  return {
    previewFile,
  }
}
