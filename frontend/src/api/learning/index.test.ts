import { beforeEach, describe, expect, it, vi } from 'vitest'

import { submitFaceCheck } from './index'

const httpMock = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('@/api/http', () => ({
  http: httpMock,
}))

describe('人脸抽验接口', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    httpMock.post.mockResolvedValue({})
  })

  it('使用multipart同时提交照片和幂等请求ID', async () => {
    const photo = new Blob(['face-image'], { type: 'image/jpeg' })

    await submitFaceCheck('700', photo, 'request-1')

    expect(httpMock.post).toHaveBeenCalledOnce()
    const [url, data, config] = httpMock.post.mock.calls[0]!
    expect(url).toBe('/learning/face-checks/700/submissions')
    expect(data).toBeInstanceOf(FormData)
    expect((data as FormData).get('requestId')).toBe('request-1')
    expect((data as FormData).get('photo')).toBeInstanceOf(File)
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
  })
})
