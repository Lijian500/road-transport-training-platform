import { expect } from '@playwright/test'
import { test } from './fixtures'

test('车辆新增、编辑、停用及重新启用的页面流程', async ({ page }) => {
  let vehicle: Record<string, unknown> | undefined
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname.replace('/api', '')
      let data: unknown
      if (path === '/auth/csrf') data = { token: 'ui-fixture' }
      else if (path === '/auth/me')
        data = {
          userId: '1',
          enterpriseId: '1',
          displayName: '演示管理员',
          platformAdmin: false,
          mustChangePassword: false,
          roles: ['ENTERPRISE_ADMIN'],
          workspaces: ['admin'],
          defaultWorkspace: 'admin',
          permissions: [
            'admin:vehicle:view',
            'admin:vehicle:create',
            'admin:vehicle:update',
            'admin:vehicle:status',
          ],
        }
      else if (path.endsWith('/departments')) data = [{ id: '10', name: '安全培训部' }]
      else if (path === '/admin/vehicles' && request.method() === 'GET')
        data = { records: vehicle ? [vehicle] : [], total: vehicle ? 1 : 0 }
      else if (path === '/admin/vehicles' && request.method() === 'POST') {
        vehicle = {
          ...request.postDataJSON(),
          id: '101',
          status: 'ENABLED',
          createdAt: '2026-09-07T10:00:00',
        }
        data = vehicle
      } else if (path === '/admin/vehicles/101' || path === '/admin/vehicles/101/status') {
        vehicle = { ...vehicle, ...request.postDataJSON() }
        data = vehicle
      } else {
        await route.fulfill({ status: 500, json: { code: 'UNEXPECTED_UI_REQUEST', message: path } })
        return
      }
      await route.fulfill({ json: { code: 'SUCCESS', message: '成功', data } })
    },
  )
  await page.goto('/admin/vehicles')
  await page.getByRole('button', { name: '新增车辆' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('车牌号').fill('川A00002')
  await dialog.getByLabel('车辆类型').fill('演示货车')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('cell', { name: '川A00002', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await dialog.getByLabel('车辆类型').fill('演示客车')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('cell', { name: '演示客车', exact: true })).toBeVisible()
  for (const action of ['停用', '启用']) {
    await page.getByRole('button', { name: action, exact: true }).click()
    await page.getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.getByRole('cell', { name: action, exact: true })).toBeVisible()
  }
  await page.screenshot({ path: '../output/playwright/vehicles-ui-fixture.png', fullPage: true })
})
