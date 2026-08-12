import {
  createPartUrls,
  getUploadedParts,
  type SignedRequest,
  type UploadSession,
} from '@/api/training'

const MAX_CONCURRENCY = 4
const MAX_RETRIES = 3

export interface UploadProgress {
  uploadedBytes: number
  totalBytes: number
  percentage: number
}

export interface ResumableUploadRecord {
  courseId: string
  sessionId: string
  fileName: string
  fileSize: number
  lastModified: number
  expiresAt: string
}

interface UploadOptions {
  onProgress?: (progress: UploadProgress) => void
  signal?: AbortSignal
}

/** 计算指定分片的文件起止位置。 */
export function getPartRange(
  fileSize: number,
  partSize: number,
  partNumber: number,
): { start: number; end: number } {
  const start = (partNumber - 1) * partSize
  return { start, end: Math.min(start + partSize, fileSize) }
}

/** 判断重新选择的文件是否与本地续传记录完全匹配。 */
export function matchesResumableFile(file: File, record: ResumableUploadRecord): boolean {
  return (
    file.name === record.fileName &&
    file.size === record.fileSize &&
    file.lastModified === record.lastModified
  )
}

/** 使用独立XHR执行OSS请求，不附带业务Cookie或CSRF头。 */
export function uploadSignedBlob(
  request: SignedRequest,
  blob: Blob,
  onProgress?: (loaded: number) => void,
  signal?: AbortSignal,
): Promise<string | null> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(request.method, request.url, true)
    xhr.withCredentials = false
    Object.entries(request.headers || {}).forEach(([name, value]) => {
      const normalizedName = name.toLowerCase()
      if (normalizedName !== 'host' && normalizedName !== 'content-length') {
        xhr.setRequestHeader(name, value)
      }
    })
    xhr.upload.onprogress = (event) => onProgress?.(event.loaded)
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.getResponseHeader('ETag'))
      } else {
        reject(new OssUploadError(xhr.status, `OSS上传失败（HTTP ${xhr.status}）`))
      }
    }
    xhr.onerror = () => reject(new OssUploadError(0, 'OSS网络连接失败'))
    xhr.onabort = () => reject(new DOMException('上传已取消', 'AbortError'))
    signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(blob)
  })
}

/** 并发上传视频缺失分片，403时刷新签名，网络或5xx最多重试3次。 */
export async function uploadMultipartFile(
  file: File,
  session: UploadSession,
  options: UploadOptions = {},
): Promise<void> {
  const uploaded = await getUploadedParts(session.id)
  const uploadedNumbers = new Set(uploaded.map((part) => part.partNumber))
  const completedBytes = uploaded.reduce((sum, part) => sum + part.sizeBytes, 0)
  const progressByPart = new Map<number, number>()
  const missingNumbers = Array.from({ length: session.partCount }, (_, index) => index + 1).filter(
    (partNumber) => !uploadedNumbers.has(partNumber),
  )
  let cursor = 0

  /** 汇总已完成分片和当前XHR的实时进度。 */
  const reportProgress = () => {
    const activeBytes = Array.from(progressByPart.values()).reduce((sum, value) => sum + value, 0)
    const uploadedBytes = Math.min(file.size, completedBytes + activeBytes)
    options.onProgress?.({
      uploadedBytes,
      totalBytes: file.size,
      percentage: Math.round((uploadedBytes / file.size) * 100),
    })
  }

  /** 单个工作协程领取并上传缺失分片。 */
  const worker = async () => {
    while (cursor < missingNumbers.length) {
      options.signal?.throwIfAborted()
      const partNumber = missingNumbers[cursor++]!
      const range = getPartRange(file.size, session.partSizeBytes, partNumber)
      const blob = file.slice(range.start, range.end)
      await uploadPartWithRetry(session.id, partNumber, blob, (loaded) => {
        progressByPart.set(partNumber, loaded)
        reportProgress()
      }, options.signal)
      progressByPart.set(partNumber, blob.size)
      reportProgress()
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(MAX_CONCURRENCY, missingNumbers.length) }, () => worker()),
  )
  options.onProgress?.({ uploadedBytes: file.size, totalBytes: file.size, percentage: 100 })
}

/** 上传单个分片并按错误类型执行有限重试。 */
async function uploadPartWithRetry(
  sessionId: string,
  partNumber: number,
  blob: Blob,
  onProgress: (loaded: number) => void,
  signal?: AbortSignal,
): Promise<void> {
  let request = (await createPartUrls(sessionId, [partNumber]))[0]!
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    try {
      await uploadSignedBlob(request, blob, onProgress, signal)
      return
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw error
      }
      const status = error instanceof OssUploadError ? error.status : 0
      if (status === 403 && attempt < MAX_RETRIES) {
        request = (await createPartUrls(sessionId, [partNumber]))[0]!
        continue
      }
      if ((status === 0 || status >= 500) && attempt < MAX_RETRIES) {
        await delay(300 * 2 ** attempt)
        continue
      }
      throw error
    }
  }
}

/** 等待下一次重试，避免连续冲击网络。 */
function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

class OssUploadError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'OssUploadError'
    this.status = status
  }
}
