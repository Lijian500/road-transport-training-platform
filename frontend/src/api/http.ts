import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

export interface ApiResult<T> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNumber: number
  pageSize: number
}

interface RetryConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
  _skipRefresh?: boolean
}

export class ApiError extends Error {
  readonly code: string
  readonly status?: number

  constructor(message: string, code = 'S9999', status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export const client: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: {
    'Content-Type': 'application/json',
  },
})

let refreshPromise: Promise<void> | null = null

function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = client
      .post<ApiResult<unknown>>('/auth/refresh', undefined, {
        _skipRefresh: true,
      } as AxiosRequestConfig)
      .then(() => undefined)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResult<unknown>>) => {
    const config = error.config as RetryConfig | undefined
    const status = error.response?.status
    if (
      status === 401 &&
      config &&
      !config._retried &&
      !config._skipRefresh &&
      !config.url?.includes('/auth/login') &&
      !config.url?.includes('/auth/refresh')
    ) {
      config._retried = true
      try {
        await refreshSession()
        return client.request(config)
      } catch {
        window.dispatchEvent(new CustomEvent('auth:expired'))
      }
    }
    const payload = error.response?.data
    throw new ApiError(
      payload?.message || error.message || '请求失败，请稍后重试',
      payload?.code,
      status,
    )
  },
)

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await client.request<ApiResult<T>>(config)
  const result = response.data
  if (result.code !== 'SUCCESS') {
    throw new ApiError(result.message, result.code, response.status)
  }
  return result.data
}

export const http = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'GET', url })
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'POST', url, data })
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'PUT', url, data })
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'PATCH', url, data })
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'DELETE', url })
  },
}

export async function ensureCsrfToken() {
  await request<{ token: string }>({
    method: 'GET',
    url: '/auth/csrf',
    _skipRefresh: true,
  } as AxiosRequestConfig)
}

export default http
