import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/** 读取本机环境文件，显式进程变量优先，永不打印敏感值。 */
export function readEnvironment(file = '.env.local') {
  const values = {}
  for (const line of readFileSync(resolve(file), 'utf8').split(/\r?\n/)) {
    const match = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/)
    if (match) values[match[1]] = match[2].trim().replace(/^(['"])(.*)\1$/, '$2')
  }
  return { ...values, ...process.env }
}

/** 保持独立账号的Cookie和CSRF会话，供演示数据和真实接口验收使用。 */
export class ApiClient {
  /** 网关地址通过参数或环境指定，不持久化认证Cookie。 */
  constructor(baseURL = process.env.TRAIN_API_URL || 'http://127.0.0.1:8080') {
    this.baseURL = baseURL
    this.cookies = new Map()
  }

  /** 调用Result接口，返回原始业务结果以支持拒绝场景断言。 */
  async raw(path, method = 'GET', data) {
    const response = await fetch(`${this.baseURL}/api${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        Cookie: [...this.cookies].map(([key, value]) => `${key}=${value}`).join('; '),
        'X-XSRF-TOKEN': decodeURIComponent(this.cookies.get('XSRF-TOKEN') || ''),
      },
      body: data === undefined ? undefined : JSON.stringify(data),
      signal: AbortSignal.timeout(30000),
    })
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0]
      const index = pair.indexOf('=')
      this.cookies.set(pair.slice(0, index), pair.slice(index + 1))
    }
    if (!response.headers.get('content-type')?.includes('json')) {
      throw new Error(`${method} ${path}: HTTP ${response.status}，网关尚未就绪或路由错误`)
    }
    return { ...(await response.json()), httpStatus: response.status }
  }

  /** 业务失败立即中断，避免继续生成依赖错误数据的演示记录。 */
  async call(path, method = 'GET', data) {
    const result = await this.raw(path, method, data)
    if (result.code !== 'SUCCESS') throw new Error(`${method} ${path}: ${result.code} ${result.message}`)
    return result.data
  }

  /** 登录前先获取CSRF；使用者自行处理新账号的首次改密流程。 */
  async login(username, password) {
    await this.call('/auth/csrf')
    return this.call('/auth/login', 'POST', { username, password })
  }
}
