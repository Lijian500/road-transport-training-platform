import { expect, type Page } from '@playwright/test'
import { test } from './fixtures'

const record = {
  taskId: '2001',
  planId: '1001',
  userId: '101',
  displayName: '演示学员',
  planName: '已结束的安全培训',
  planStatus: 'FINISHED',
  startAt: '2026-01-01T09:00:00',
  endAt: '2026-01-31T18:00:00',
  studyStatus: 'COMPLETED',
  examStatus: 'PASSED',
  completionStatus: 'COMPLETED',
  examScore: 100,
  completedAt: '2026-01-02T10:00:00',
  requiredDurationMillis: 60000,
}

/** 提供明确标记的接口契约夹具，未列入的接口失败而非静默放行。 */
async function mockRecords(page: Page) {
  const requests: string[] = []
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname.replace('/api', '')
      requests.push(`${request.method()} ${path}`)
      let data: unknown
      if (path === '/auth/csrf') data = { token: 'ui-fixture' }
      else if (path === '/auth/me')
        data = {
          userId: '101',
          enterpriseId: '1',
          username: 'fixture_student',
          displayName: '演示学员',
          platformAdmin: false,
          mustChangePassword: false,
          roles: ['STUDENT'],
          permissions: ['student:plan:view'],
          workspaces: ['student'],
          defaultWorkspace: 'student',
        }
      else if (path.endsWith('/overview'))
        data = {
          totalCount: 1,
          toStudyCount: 0,
          toExamCount: 0,
          completedCount: 1,
          recentTasks: [],
        }
      else if (path === '/training/student/records')
        data = {
          records: [{ training: record, effectiveDurationMillis: 60000 }],
          total: 1,
          pageNumber: 1,
          pageSize: 10,
        }
      else if (path === '/training/student/records/2001')
        data = {
          training: record,
          courses: [
            {
              planCourseId: '3001',
              courseName: '安全演示课',
              requiredDurationMillis: 60000,
              effectiveDurationMillis: 60000,
              status: 'COMPLETED',
            },
          ],
        }
      else if (path === '/learning/records/student/sessions')
        data = {
          records: [
            {
              id: '4001',
              taskId: '2001',
              planCourseId: '3001',
              courseName: '安全演示课',
              status: 'TERMINATED',
              createdAt: '2026-01-02T09:00:00',
              terminationReason: 'FACE_CHECK_TIMEOUT',
            },
          ],
          total: 1,
        }
      else if (path.endsWith('/events')) data = { records: [], total: 0 }
      else if (path.endsWith('/face-checks'))
        data = {
          records: [
            {
              id: '5001',
              status: 'TIMED_OUT',
              triggeredAt: '2026-01-02T09:01:00',
              deadlineAt: '2026-01-02T09:02:00',
              failureReason: 'FACE_CHECK_TIMEOUT',
              attemptCount: 0,
              attempts: [],
            },
          ],
          total: 1,
        }
      else {
        await route.fulfill({ status: 500, json: { code: 'UNEXPECTED_UI_REQUEST', message: path } })
        return
      }
      await route.fulfill({ json: { code: 'SUCCESS', message: '成功', data } })
    },
  )
  return requests
}

test('历史档案展示已结束计划、学时和零提交抽验，浏览过程不触发写入', async ({ page }) => {
  const requests = await mockRecords(page)
  await page.goto('/student/records')
  await expect(page.getByText(record.planName)).toBeVisible()
  await page.getByRole('button', { name: '查看档案', exact: true }).click()
  await expect(page.getByRole('heading', { name: record.planName })).toBeVisible()
  await expect(page.getByText('0小时1分0秒').first()).toBeVisible()
  await page.getByRole('button', { name: '查看过程', exact: true }).click()
  await page.getByRole('tab', { name: '人脸抽验' }).click()
  await page.locator('.el-table__expand-icon').last().click()
  await expect(page.getByText('本次抽验没有提交照片')).toBeVisible()
  expect(requests.every((request) => request.startsWith('GET '))).toBe(true)
  await page.screenshot({ path: '../output/playwright/records-ui-fixture.png', fullPage: true })
})

test('首页待办卡片携带筛选条件，导航只高亮当前入口', async ({ page }) => {
  await mockRecords(page)
  await page.goto('/student')
  await page.getByRole('button', { name: /待考试/ }).click()
  await expect(page).toHaveURL(/\/student\/records\?activity=TO_EXAM/)
  await expect(page.getByRole('link', { name: '学习首页', exact: true })).not.toHaveClass(
    /router-link-active/,
  )
  await expect(page.getByRole('link', { name: '我的学习档案', exact: true })).toHaveClass(
    /router-link-active/,
  )
})

test('服务端拒绝档案请求时展示错误，不伪造空档案成功', async ({ page }) => {
  await mockRecords(page)
  await page.route('**/api/training/student/records?**', (route) =>
    route.fulfill({
      status: 403,
      json: { code: 'AUTH_FORBIDDEN', message: '无权查看该学习档案' },
    }),
  )
  await page.goto('/student/records')
  await expect(page.getByRole('alert')).toContainText('无权查看该学习档案')
})
