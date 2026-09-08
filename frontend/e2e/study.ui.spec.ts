import { expect } from '@playwright/test'
import { test } from './fixtures'
import type { LearningSession } from '../src/api/learning'

test('UI夹具：播完仍缺学时时从头补学，使用服务端确认位置且保留累计学时', async ({ page }) => {
  const session: LearningSession = {
    id: '4001',
    taskId: '2001',
    planId: '1001',
    planCourseId: '3001',
    courseName: '补学验收课',
    status: 'PAUSED',
    currentCoursewareSnapshotId: '5001',
    lastSequence: 5,
    confirmedPositionMillis: 60000,
    effectiveDurationMillis: 55000,
    requiredDurationMillis: 60000,
    createdAt: '2026-09-01T09:00:00',
  }
  const messages: Array<{ type: string; seq: number; payload: { videoPositionMillis: number } }> =
    []
  // 该用例只验证页面契约，媒体事件明确由夹具驱动，真实解码另由media项目验证。
  await page.addInitScript(() => {
    const state = new WeakMap<HTMLMediaElement, { position: number; paused: boolean }>()
    /** 保存每个播放器的夹具状态。 */
    function media(element: HTMLMediaElement) {
      if (!state.has(element)) state.set(element, { position: 0, paused: true })
      return state.get(element)!
    }
    Object.defineProperties(HTMLMediaElement.prototype, {
      src: {
        get() {
          return ''
        },
        set() {
          /* 夹具不请求外部媒体。 */
        },
      },
      currentTime: {
        get() {
          return media(this).position
        },
        set(value) {
          media(this).position = value
        },
      },
      paused: {
        get() {
          return media(this).paused
        },
      },
      readyState: {
        get() {
          return 4
        },
      },
    })
    /** 触发可控的媒体元数据事件。 */
    HTMLMediaElement.prototype.load = function () {
      this.dispatchEvent(new Event('loadedmetadata'))
    }
    /** 保留播放器play事件与页面处理器之间的交互。 */
    HTMLMediaElement.prototype.play = async function () {
      media(this).paused = false
      this.dispatchEvent(new Event('play'))
    }
    /** 保留播放器pause事件与页面处理器之间的交互。 */
    HTMLMediaElement.prototype.pause = function () {
      media(this).paused = true
      this.dispatchEvent(new Event('pause'))
    }
  })
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      let data: unknown
      if (path === '/api/auth/csrf') data = { token: 'ui-fixture' }
      else if (path === '/api/auth/me')
        data = {
          userId: '101',
          enterpriseId: '1',
          username: 'fixture_student',
          displayName: '演示学员',
          mustChangePassword: false,
          platformAdmin: false,
          roles: ['STUDENT'],
          permissions: ['student:learning:study', 'student:plan:view'],
          workspaces: ['student'],
          defaultWorkspace: 'student',
        }
      else if (path === '/api/learning/plans/1001/courses/3001')
        data = {
          planCourseId: '3001',
          courseName: '补学验收课',
          sortOrder: 1,
          requiredDurationMillis: 60000,
          effectiveDurationMillis: 55000,
          allowSeek: false,
          progressReportIntervalSeconds: 10,
          studyToleranceSeconds: 2,
          status: 'IN_PROGRESS',
          coursewares: [
            {
              coursewareSnapshotId: '5001',
              title: '已播放课件',
              sortOrder: 1,
              durationMillis: 60000,
              confirmedPositionMillis: 60000,
              maxConfirmedPositionMillis: 60000,
              status: 'COMPLETED',
            },
          ],
        }
      else if (path.endsWith('/play-url'))
        data = { url: 'https://media.invalid/ui-fixture.mp4', method: 'GET', headers: {} }
      else if (path.startsWith('/api/learning/sessions')) data = session
      else {
        await route.fulfill({ status: 500, json: { code: 'UNEXPECTED_UI_REQUEST', message: path } })
        return
      }
      await route.fulfill({ json: { code: 'SUCCESS', data } })
    },
  )
  await page.routeWebSocket('**/ws/learning', (socket) => {
    socket.onMessage((raw) => {
      const message = JSON.parse(String(raw))
      if (['BIND_SESSION', 'SYNC_STATE'].includes(message.type)) {
        socket.send(JSON.stringify({ ...message, type: 'STATE_SYNC', payload: session }))
      } else if (message.type === 'HEARTBEAT') {
        socket.send(
          JSON.stringify({
            ...message,
            type: 'PONG',
            payload: { serverTime: new Date().toISOString() },
          }),
        )
      } else {
        messages.push(message)
        session.lastSequence = message.seq
        session.status = 'STUDYING'
        session.confirmedPositionMillis = message.payload.videoPositionMillis
        socket.send(
          JSON.stringify({
            ...message,
            type: 'PROGRESS_CONFIRMED',
            payload: {
              sessionId: session.id,
              requestId: message.requestId,
              acceptedSequence: message.seq,
              status: session.status,
              currentCoursewareSnapshotId: '5001',
              confirmedPositionMillis: 0,
              creditedDurationMillis: 0,
              effectiveDurationMillis: 55000,
              requiredDurationMillis: 60000,
              coursewareCompleted: false,
              courseCompleted: false,
              serverTime: new Date().toISOString(),
            },
          }),
        )
      }
    })
  })
  await page.goto('/student/plans/1001/courses/3001/study')
  await page.getByRole('button', { name: '从头补学当前课件' }).click()
  await expect
    .poll(() =>
      messages.some(
        (message) => message.type === 'PLAY' && message.payload.videoPositionMillis === 0,
      ),
    )
    .toBe(true)
  expect(messages[0]!.seq).toBe(6)
  await expect
    .poll(() =>
      page.locator('.player-panel video').evaluate((video: HTMLVideoElement) => video.currentTime),
    )
    .toBe(0)
  await expect(page.locator('.progress-summary')).toContainText('55')
  await expect(page.getByRole('button', { name: /已播放课件/ })).toContainText('已完成')
})
