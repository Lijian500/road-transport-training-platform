import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createPartUrls,
  getUploadedParts,
  type SignedRequest,
  type UploadSession,
} from '@/api/training'

import {
  getPartRange,
  matchesResumableFile,
  uploadMultipartFile,
  uploadSignedBlob,
} from './ossUpload'

vi.mock('@/api/training', () => ({
  createPartUrls: vi.fn(),
  getUploadedParts: vi.fn(),
}))

interface ResponsePlan {
  status: number
  delayMs?: number
  networkError?: boolean
}

interface XhrRecord {
  url: string
  headers: Record<string, string>
  withCredentials: boolean
}

const createPartUrlsMock = vi.mocked(createPartUrls)
const getUploadedPartsMock = vi.mocked(getUploadedParts)
let records: XhrRecord[] = []
let activeRequests = 0
let maxActiveRequests = 0
let responsePlan: (url: string) => ResponsePlan = () => ({ status: 200 })

class FakeXMLHttpRequest {
  status = 0
  withCredentials = true
  upload: { onprogress: ((event: ProgressEvent) => void) | null } = { onprogress: null }
  onload: ((event: ProgressEvent) => void) | null = null
  onerror: ((event: ProgressEvent) => void) | null = null
  onabort: ((event: ProgressEvent) => void) | null = null

  private url = ''
  private headers: Record<string, string> = {}
  private aborted = false

  /** 记录XHR目标地址，测试无需模拟浏览器网络栈。 */
  open(_method: string, url: string) {
    this.url = url
  }

  /** 收集最终发往OSS的签名请求头。 */
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value
  }

  /** 模拟异步上传、进度事件及HTTP结果。 */
  send(body: Document | XMLHttpRequestBodyInit | null) {
    const blob = body instanceof Blob ? body : new Blob()
    const plan = responsePlan(this.url)
    records.push({
      url: this.url,
      headers: { ...this.headers },
      withCredentials: this.withCredentials,
    })
    activeRequests += 1
    maxActiveRequests = Math.max(maxActiveRequests, activeRequests)
    this.upload.onprogress?.(new ProgressEvent('progress', { loaded: Math.floor(blob.size / 2) }))

    /** 完成本次模拟请求，已取消的XHR不再触发load。 */
    const finish = () => {
      if (this.aborted) {
        return
      }
      activeRequests -= 1
      this.status = plan.status
      this.upload.onprogress?.(new ProgressEvent('progress', { loaded: blob.size }))
      if (plan.networkError) {
        this.onerror?.(new ProgressEvent('error'))
      } else {
        this.onload?.(new ProgressEvent('load'))
      }
    }

    if (plan.delayMs) {
      window.setTimeout(finish, plan.delayMs)
    } else {
      queueMicrotask(finish)
    }
  }

  /** OSS成功上传分片时返回ETag。 */
  getResponseHeader(name: string) {
    return name.toLowerCase() === 'etag' ? '"test-etag"' : null
  }

  /** 模拟浏览器主动取消XHR。 */
  abort() {
    if (this.aborted) {
      return
    }
    this.aborted = true
    activeRequests = Math.max(0, activeRequests - 1)
    this.onabort?.(new ProgressEvent('abort'))
  }
}

/** 构造视频上传会话。 */
function uploadSession(fileSize: number, partSize: number): UploadSession {
  return {
    id: 'session-1',
    courseId: 'course-1',
    coursewareId: 'courseware-1',
    uploadType: 'VIDEO',
    originalFilename: 'course.mp4',
    fileSizeBytes: fileSize,
    clientLastModified: 1234,
    partSizeBytes: partSize,
    partCount: Math.ceil(fileSize / partSize),
    status: 'INITIATED',
    expiresAt: '2099-01-01T00:00:00',
  }
}

/** 构造指定分片的短期OSS签名。 */
function signedRequest(partNumber: number, suffix = ''): SignedRequest {
  return {
    partNumber,
    url: `https://oss.example.test/part-${partNumber}${suffix}`,
    method: 'PUT',
    headers: {},
    expiresAt: '2099-01-01T00:00:00',
  }
}

beforeEach(() => {
  records = []
  activeRequests = 0
  maxActiveRequests = 0
  responsePlan = () => ({ status: 200 })
  vi.stubGlobal('XMLHttpRequest', FakeXMLHttpRequest)
  getUploadedPartsMock.mockResolvedValue([])
  createPartUrlsMock.mockImplementation(async (_sessionId, partNumbers) =>
    partNumbers.map((partNumber) => signedRequest(partNumber)),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('ossUpload', () => {
  it('按固定大小切分文件且最后一片不越界', () => {
    expect(getPartRange(20, 8, 1)).toEqual({ start: 0, end: 8 })
    expect(getPartRange(20, 8, 2)).toEqual({ start: 8, end: 16 })
    expect(getPartRange(20, 8, 3)).toEqual({ start: 16, end: 20 })
  })

  it('仅在名称、大小和修改时间全部一致时允许续传', () => {
    const file = new File(['video'], 'course.mp4', {
      type: 'video/mp4',
      lastModified: 1234,
    })
    const record = {
      courseId: '1',
      sessionId: '2',
      fileName: 'course.mp4',
      fileSize: file.size,
      lastModified: 1234,
      expiresAt: '2099-01-01T00:00:00',
    }

    expect(matchesResumableFile(file, record)).toBe(true)
    expect(matchesResumableFile(file, { ...record, lastModified: 5678 })).toBe(false)
    expect(matchesResumableFile(file, { ...record, fileName: 'other.mp4' })).toBe(false)
  })

  it('最多并发上传4片并汇总到100%进度', async () => {
    const file = new File([new Uint8Array(60)], 'course.mp4', { type: 'video/mp4' })
    const percentages: number[] = []
    responsePlan = () => ({ status: 200, delayMs: 5 })

    await uploadMultipartFile(file, uploadSession(file.size, 10), {
      onProgress: (progress) => percentages.push(progress.percentage),
    })

    expect(records).toHaveLength(6)
    expect(maxActiveRequests).toBe(4)
    expect(percentages.at(-1)).toBe(100)
    expect(percentages.every((value) => value >= 0 && value <= 100)).toBe(true)
  })

  it('查询OSS现有分片后只上传缺失部分', async () => {
    const file = new File([new Uint8Array(25)], 'course.mp4', { type: 'video/mp4' })
    getUploadedPartsMock.mockResolvedValue([
      { partNumber: 1, sizeBytes: 10, etag: 'etag-1' },
    ])

    await uploadMultipartFile(file, uploadSession(file.size, 10))

    expect(records.map((record) => record.url)).toEqual([
      'https://oss.example.test/part-2',
      'https://oss.example.test/part-3',
    ])
  })

  it('遇到403会重新申请分片签名后重试', async () => {
    const file = new File([new Uint8Array(8)], 'course.mp4', { type: 'video/mp4' })
    let signVersion = 0
    createPartUrlsMock.mockImplementation(async (_sessionId, partNumbers) => {
      signVersion += 1
      return partNumbers.map((partNumber) => signedRequest(partNumber, `-v${signVersion}`))
    })
    responsePlan = (url) => ({ status: url.endsWith('-v1') ? 403 : 200 })

    await uploadMultipartFile(file, uploadSession(file.size, 8))

    expect(createPartUrlsMock).toHaveBeenCalledTimes(2)
    expect(records.map((record) => record.url)).toEqual([
      'https://oss.example.test/part-1-v1',
      'https://oss.example.test/part-1-v2',
    ])
  })

  it('遇到5xx按退避策略重试且成功后结束', async () => {
    const file = new File([new Uint8Array(8)], 'course.mp4', { type: 'video/mp4' })
    let attempts = 0
    responsePlan = () => ({ status: ++attempts < 3 ? 503 : 200 })

    await uploadMultipartFile(file, uploadSession(file.size, 8))

    expect(records).toHaveLength(3)
    expect(createPartUrlsMock).toHaveBeenCalledTimes(1)
  })

  it('OSS直传不携带业务Cookie、CSRF头或受限签名头', async () => {
    const request: SignedRequest = {
      ...signedRequest(1),
      headers: {
        'Content-Type': 'video/mp4',
        Host: 'oss.example.test',
        'Content-Length': '8',
      },
    }

    await uploadSignedBlob(request, new Blob([new Uint8Array(8)]))

    expect(records).toHaveLength(1)
    expect(records[0]?.withCredentials).toBe(false)
    expect(records[0]?.headers).toEqual({ 'Content-Type': 'video/mp4' })
    expect(records[0]?.headers).not.toHaveProperty('X-XSRF-TOKEN')
  })
})
