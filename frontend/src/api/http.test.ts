import {
  AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import { client, http, type ApiResult } from './http'

interface RetryConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

function response<T>(
  config: InternalAxiosRequestConfig,
  data: ApiResult<T>,
  status = 200,
): AxiosResponse<ApiResult<T>> {
  return {
    config,
    data,
    headers: {},
    status,
    statusText: status === 200 ? 'OK' : 'Unauthorized',
  }
}

describe('http refresh', () => {
  const originalAdapter = client.defaults.adapter

  afterEach(() => {
    client.defaults.adapter = originalAdapter
  })

  it('并发401只刷新一次并各自重试一次', async () => {
    let refreshCount = 0
    let businessCount = 0
    client.defaults.adapter = async (rawConfig: AxiosRequestConfig) => {
      const config = rawConfig as RetryConfig
      if (config.url === '/auth/refresh') {
        refreshCount += 1
        await Promise.resolve()
        return response(config, {
          code: 'SUCCESS',
          message: '操作成功',
          data: null,
        })
      }

      businessCount += 1
      if (!config._retried) {
        const unauthorized = response(
          config,
          { code: 'A0001', message: '请先登录', data: null },
          401,
        )
        throw new AxiosError(
          'Unauthorized',
          'ERR_BAD_REQUEST',
          config,
          undefined,
          unauthorized,
        )
      }
      return response(config, {
        code: 'SUCCESS',
        message: '操作成功',
        data: config.url,
      })
    }

    const values = await Promise.all([
      http.get<string>('/resource-a'),
      http.get<string>('/resource-b'),
    ])

    expect(values).toEqual(['/resource-a', '/resource-b'])
    expect(refreshCount).toBe(1)
    expect(businessCount).toBe(4)
  })
})
