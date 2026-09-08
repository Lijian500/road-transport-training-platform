import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'

interface DemoState {
  prefix: string
  password: string
  vehicle_a: { id: string; plateNumber: string }
  org_a: { id: string }
}

/** 只读取独立演示数据；显式启用live却缺少数据时直接失败。 */
function demoState(): DemoState {
  const path = process.env.TRAIN_DEMO_STATE || '../tmp/demo/state.json'
  return JSON.parse(readFileSync(resolve(path), 'utf8'))
}

/** 通过真实CSRF和登录接口建立浏览器Cookie，不模拟任何业务响应。 */
async function login(page: Page, username: string, password: string) {
  const csrf = await page.request.get('/api/auth/csrf')
  expect((await csrf.json()).code).toBe('SUCCESS')
  const state = await page.context().storageState()
  const token = state.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value || ''
  const response = await page.request.post('/api/auth/login', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
    data: { username, password },
  })
  expect((await response.json()).code).toBe('SUCCESS')
}

/** 携带当前CSRF调用写接口，用于真实权限和唯一性断言。 */
async function write(page: Page, path: string, method: 'POST' | 'PUT', data: unknown) {
  const cookies = (await page.context().storageState()).cookies
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value || ''
  return page.request.fetch(`/api${path}`, {
    method,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
    data,
  })
}

test('真实企业车辆查询、重复车牌拒绝、跨企业修改拒绝', async ({ page }) => {
  const state = demoState()
  await login(page, `${state.prefix}_a`, state.password)
  await page.goto('/admin/vehicles')
  await expect(
    page.getByRole('cell', { name: state.vehicle_a.plateNumber, exact: true }),
  ).toBeVisible()
  const duplicate = await write(page, '/admin/vehicles', 'POST', {
    plateNumber: state.vehicle_a.plateNumber,
    vehicleType: '重复演示车',
    orgId: state.org_a.id,
  })
  expect((await duplicate.json()).code).toBe('S9001')
  await login(page, `${state.prefix}_b`, state.password)
  const forbidden = await write(page, `/admin/vehicles/${state.vehicle_a.id}/status`, 'PUT', {
    status: 'DISABLED',
  })
  expect((await forbidden.json()).code).toBe('S9002')
  await login(page, `${state.prefix}_a`, state.password)
  const vehicles = await page.request.get('/api/admin/vehicles')
  expect(
    (await vehicles.json()).data.records.find(
      (row: { id: string }) => row.id === state.vehicle_a.id,
    ).status,
  ).toBe('ENABLED')
  await page.screenshot({ path: '../output/playwright/vehicles-live.png', fullPage: true })
})

test('真实学员首页和档案查询，以及管理员接口权限隔离', async ({ page }) => {
  const state = demoState()
  await login(page, `${state.prefix}_student_a`, state.password)
  await page.goto('/student')
  await expect(page.getByRole('button', { name: /全部培训.*查看学习档案/ })).toBeVisible()
  await page.getByRole('link', { name: '我的学习档案', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的学习档案', exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  const records = await page.request.get('/api/training/student/records')
  expect((await records.json()).code).toBe('SUCCESS')
  const vehicles = await page.request.get('/api/admin/vehicles')
  expect((await vehicles.json()).code).toBe('A0006')
  const sessions = await page.request.get('/api/learning/records/admin/sessions?taskId=1')
  expect((await sessions.json()).code).toBe('A0006')
  await page.screenshot({ path: '../output/playwright/records-live.png', fullPage: true })
})

test('真实WebSocket连接与心跳往返', async ({ page }) => {
  const state = demoState()
  await login(page, `${state.prefix}_student_a`, state.password)
  await page.goto('/student')
  const result = await page.evaluate(
    () =>
      new Promise<string>((resolveResult, reject) => {
        const socket = new WebSocket(
          `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/learning`,
        )
        const timer = setTimeout(() => {
          socket.close()
          reject(new Error('心跳响应超时'))
        }, 10000)
        socket.onopen = () =>
          socket.send(
            JSON.stringify({
              type: 'HEARTBEAT',
              requestId: 'live-heartbeat',
              sentAt: new Date().toISOString(),
              payload: {},
            }),
          )
        socket.onmessage = (event) => {
          clearTimeout(timer)
          socket.close()
          resolveResult(JSON.parse(event.data).type)
        }
        socket.onclose = (event) => {
          clearTimeout(timer)
          reject(new Error(`WebSocket关闭：${event.code} ${event.reason}`))
        }
        socket.onerror = () => {
          clearTimeout(timer)
          socket.close()
          reject(new Error('WebSocket连接失败'))
        }
      }),
  )
  expect(result).toBe('PONG')
})
