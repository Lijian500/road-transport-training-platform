import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

test('单实例WebSocket心跳压力实验（不代表学时落库吞吐）', async ({ page }) => {
  const state = JSON.parse(
    readFileSync(resolve(process.env.TRAIN_DEMO_STATE || '../tmp/demo/state.json'), 'utf8'),
  )
  await page.request.get('/api/auth/csrf')
  const csrf =
    (await page.context().storageState()).cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')
      ?.value || ''
  const login = await page.request.post('/api/auth/login', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf) },
    data: { username: `${state.prefix}_student_a`, password: state.password },
  })
  expect((await login.json()).code).toBe('SUCCESS')
  await page.goto('/student')
  const reports = []
  for (const concurrency of [1, 10, 50]) {
    const report = await page.evaluate(async (count) => {
      const start = performance.now()
      const latencies: number[] = []
      let errors = 0
      await Promise.all(
        Array.from(
          { length: count },
          (_, client) =>
            new Promise<void>((done) => {
              const socket = new WebSocket(
                `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/learning`,
              )
              let sent = 0
              let started = 0
              let closed = false
              const timer = setTimeout(() => finish(true), 15000)
              /** 关闭本连接并完成统计，异常只计一次。 */
              function finish(failed: boolean) {
                if (closed) return
                closed = true
                if (failed) errors++
                clearTimeout(timer)
                socket.close()
                done()
              }
              /** 每连接仅保持一个在途心跳，使用响应往返时间统计。 */
              function send() {
                started = performance.now()
                socket.send(
                  JSON.stringify({
                    type: 'HEARTBEAT',
                    requestId: `load-${client}-${++sent}`,
                    sentAt: new Date().toISOString(),
                    payload: {},
                  }),
                )
              }
              socket.onopen = send
              socket.onmessage = (event) => {
                if (JSON.parse(event.data).type !== 'PONG') {
                  finish(true)
                  return
                }
                latencies.push(performance.now() - started)
                if (sent === 20) finish(false)
                else send()
              }
              socket.onerror = () => finish(true)
              socket.onclose = () => finish(sent < 20)
            }),
        ),
      )
      latencies.sort((a, b) => a - b)
      const elapsedMillis = performance.now() - start
      return {
        concurrency: count,
        responses: latencies.length,
        errors,
        elapsedMillis,
        responsesPerSecond: (latencies.length * 1000) / elapsedMillis,
        p50Millis: latencies[Math.ceil(latencies.length * 0.5) - 1] ?? null,
        p95Millis: latencies[Math.ceil(latencies.length * 0.95) - 1] ?? null,
        maxMillis: latencies.at(-1) ?? null,
      }
    }, concurrency)
    reports.push(report)
  }
  const result = {
    measuredAt: new Date().toISOString(),
    scope: 'authenticated heartbeat only; no learning binding or database credit',
    reports,
  }
  mkdirSync('../tmp/experiments', { recursive: true })
  writeFileSync('../tmp/experiments/heartbeat.json', JSON.stringify(result, null, 2) + '\n', 'utf8')
  console.log(JSON.stringify(result))
  for (const report of reports) {
    expect(report.errors).toBe(0)
    expect(report.responses).toBe(report.concurrency * 20)
  }
})
