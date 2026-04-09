import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useKnowledgeFileUpload } from './useKnowledgeFileUpload'

const { initUpload, uploadFileBinary, completeUpload, messageApi } = vi.hoisted(() => ({
  initUpload: vi.fn(),
  uploadFileBinary: vi.fn(),
  completeUpload: vi.fn(),
  messageApi: {
    warning: vi.fn(),
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/api/file', () => ({
  initUpload,
  uploadFileBinary,
  completeUpload,
}))

vi.mock('element-plus', () => ({
  ElMessage: messageApi,
}))

describe('useKnowledgeFileUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('blocks upload when no project is selected', async () => {
    const { beforeUpload } = useKnowledgeFileUpload({
      resolveProjectId: () => undefined,
    })

    const result = await beforeUpload(new File(['demo'], 'guide.pdf', { type: 'application/pdf' }))

    expect(result).toBe(false)
    expect(messageApi.warning).toHaveBeenCalled()
    expect(initUpload).not.toHaveBeenCalled()
  })

  it('blocks unsupported extensions', async () => {
    const { beforeUpload } = useKnowledgeFileUpload({
      resolveProjectId: () => 1,
    })

    const result = await beforeUpload(new File(['demo'], 'guide.csv', { type: 'text/csv' }))

    expect(result).toBe(false)
    expect(messageApi.warning).toHaveBeenCalled()
    expect(initUpload).not.toHaveBeenCalled()
  })

  it('uploads and completes supported files', async () => {
    initUpload.mockResolvedValue({ fileId: 12, storageKey: 'k', uploadUrl: '/upload' })
    uploadFileBinary.mockResolvedValue(undefined)
    completeUpload.mockResolvedValue(undefined)
    const onUploaded = vi.fn()

    const { beforeUpload } = useKnowledgeFileUpload({
      resolveProjectId: () => 2,
      onUploaded,
    })

    const result = await beforeUpload(new File(['demo'], 'guide.pdf', { type: 'application/pdf' }))

    expect(result).toBe(false)
    expect(initUpload).toHaveBeenCalledWith({
      projectId: 2,
      fileName: 'guide.pdf',
      fileSize: 4,
      mimeType: 'application/pdf',
    })
    expect(uploadFileBinary).toHaveBeenCalledWith(12, expect.any(File))
    expect(completeUpload).toHaveBeenCalledWith(12)
    expect(onUploaded).toHaveBeenCalled()
    expect(messageApi.success).toHaveBeenCalled()
  })
})
