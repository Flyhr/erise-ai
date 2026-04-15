import { describe, expect, it, vi } from 'vitest'

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}))

vi.mock('./http', () => ({
  default: {
    post: postMock,
  },
  FILE_UPLOAD_TIMEOUT_MS: 10 * 60 * 1000,
  resolveApiUrl: (path: string) => `/api${path}`,
}))

describe('file api upload', () => {
  it('uses an extended timeout for binary uploads', async () => {
    const { uploadFileBinary } = await import('./file')

    const file = new File(['demo'], 'large.pdf', { type: 'application/pdf' })
    await uploadFileBinary(42, file)

    expect(postMock).toHaveBeenCalledWith(
      '/v1/files/upload',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 10 * 60 * 1000,
      }),
    )
  })
})
