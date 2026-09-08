import { test as base, expect } from '@playwright/test'

/** 页面脚本异常直接报告，避免白屏仅表现为后续元素等待超时。 */
export const test = base.extend<{ pageErrors: void }>({
  pageErrors: [
    async ({ page }, use) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(error.message))
      await use()
      expect(errors, '页面运行时异常').toEqual([])
    },
    { auto: true },
  ],
})
