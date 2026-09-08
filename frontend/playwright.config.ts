import { defineConfig, devices } from '@playwright/test'

/** UI契约测试可独立运行；真实验收必须显式启用并提供演示数据。 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60000,
  expect: { timeout: 30000 },
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    channel:
      process.env.PLAYWRIGHT_CHANNEL || (process.platform === 'win32' ? 'msedge' : 'chromium'),
    baseURL: process.env.TRAIN_UI_URL || 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 1000 },
    // 真实验收不采集可能包含会话Cookie的trace或网络归档。
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  webServer: process.env.TRAIN_UI_URL
    ? undefined
    : {
        command: 'pnpm dev --host 127.0.0.1',
        url: 'http://127.0.0.1:5173',
        reuseExistingServer: !process.env.CI,
        env: { VITE_GATEWAY_TARGET: process.env.TRAIN_API_URL || 'http://127.0.0.1:8080' },
        timeout: 120000,
      },
  projects: [
    { name: 'ui', testMatch: '**/*.ui.spec.ts' },
    ...(process.env.TRAIN_LIVE === 'true'
      ? [
          { name: 'live', testMatch: '**/*.live.spec.ts' },
          { name: 'load', testMatch: '**/*.load.spec.ts' },
        ]
      : []),
  ],
})
