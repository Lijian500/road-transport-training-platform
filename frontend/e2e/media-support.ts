import { readFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { expect, type Browser, type Page } from '@playwright/test'
import {
  mediaEnvironment,
  readDemoState,
  requireMediaConfiguration,
  requireMediaState,
} from '../../scripts/lib/media-config.mjs'
import type { Plan, StudentPlan } from '../src/api/training'
import type {
  CourseProgress,
  FaceCheckTask,
  LearningEventResult,
  LearningSession,
} from '../src/api/learning'
import type { RecordDetail } from '../src/api/records'

export interface MediaState {
  prefix: string
  password: string
  media: {
    students: Record<string, { id: string; username: string }>
    plans: Record<string, Plan>
  }
}

export interface StudyRun {
  page: Page
  plan: Plan
  sessionId: string
  course: CourseProgress
  results: LearningEventResult[]
  latencies: number[]
  messages: Array<{ type: string; requestId: string; seq?: number; payload: unknown }>
}

/** 配置在测试执行前校验，绝不在条件不足时将真实测试计为跳过或通过。 */
export function mediaSuite(faces = true) {
  const env = mediaEnvironment()
  const config = requireMediaConfiguration(env, { faces })
  const { state } = readDemoState(env)
  const data = state as MediaState
  requireMediaState(data, { load: !faces })
  return { ...config, env, state: data }
}

/** 调用真实Result接口，保留业务错误码供负向场景断言。 */
export async function raw<T = unknown>(page: Page, path: string, method = 'GET', data?: unknown) {
  const token =
    (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value || ''
  const response = await page.request.fetch(`/api${path}`, {
    method,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
    data,
  })
  expect(response.headers()['content-type'], `${method} ${path}应返回JSON`).toContain('json')
  return (await response.json()) as { code: string; message: string; data: T }
}

/** 只接受成功的业务结果，不将空数组或任意HTTP错误当作校验成功。 */
export async function api<T>(page: Page, path: string, method = 'GET', data?: unknown): Promise<T> {
  const result = await raw<T>(page, path, method, data)
  expect(result.code, `${method} ${path}: ${result.message}`).toBe('SUCCESS')
  return result.data
}

/** 每位学员使用独立Cookie上下文，并通过真实登录接口认证。 */
export async function loginPage(browser: Browser, username: string, password: string) {
  const context = await browser.newContext({
    baseURL: process.env.TRAIN_UI_URL || 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 1000 },
  })
  const page = await context.newPage()
  try {
    await api(page, '/auth/csrf')
    await api(page, '/auth/login', 'POST', { username, password })
    return page
  } catch (error) {
    await context.close()
    throw error
  }
}

/** 等待真实计划生效，并拒绝复用已学习过的样本，保证实验可比较。 */
export async function openStudy(page: Page, plan: Plan): Promise<StudyRun> {
  if (!plan) throw new Error('场景计划不存在，请重新执行publish-demo.mjs --media。')
  await expect
    .poll(() => Date.now(), { timeout: 150000, intervals: [1000] })
    .toBeGreaterThanOrEqual(new Date(plan.startAt).getTime())
  const task = await api<StudentPlan>(page, `/training/student/plans/${plan.id}`)
  const courseId = task.courses[0]!.id
  const course = await api<CourseProgress>(page, `/learning/plans/${plan.id}/courses/${courseId}`)
  expect(course.effectiveDurationMillis, '场景已使用，请更换TRAIN_DEMO_STATE准备新样本').toBe(0)
  const run: StudyRun = {
    page,
    plan,
    course,
    sessionId: '',
    results: [],
    latencies: [],
    messages: [],
  }
  const starts = new Map<string, number>()
  page.on('websocket', (socket) => {
    socket.on('framesent', ({ payload }) => {
      const message = JSON.parse(String(payload))
      if (['SIGN_IN', 'PLAY', 'PROGRESS', 'PAUSE', 'SIGN_OUT'].includes(message.type)) {
        run.messages.push(message)
        starts.set(message.requestId, performance.now())
      }
    })
    socket.on('framereceived', ({ payload }) => {
      const message = JSON.parse(String(payload))
      if (message.type === 'PROGRESS_CONFIRMED') {
        run.results.push(message.payload)
        const start = starts.get(message.requestId)
        if (start !== undefined) run.latencies.push(performance.now() - start)
      }
    })
  })
  await page.goto(`/student/plans/${plan.id}/courses/${courseId}/study`)
  await page.getByRole('button', { name: '学习签到', exact: true }).click()
  await expect
    .poll(async () => studyVideo(page).evaluate((video: HTMLVideoElement) => video.readyState))
    .toBeGreaterThanOrEqual(1)
  const session = await api<LearningSession>(page, '/learning/sessions/active')
  run.sessionId = session.id
  expect(session.status).toBe('SIGNED_IN')
  return run
}

/** 锁定课程视频，避免将抽验弹窗中的摄像头video误当作学习视频。 */
export function studyVideo(page: Page) {
  return page.locator('.player-panel video')
}

/** 以1倍速真实播放；不修改位置或伪造学习事件来缩短视频时长。 */
export async function play(run: StudyRun) {
  await expect
    .poll(() =>
      studyVideo(run.page).evaluate(
        (video: HTMLVideoElement) => video.readyState >= 2 && !video.ended,
      ),
    )
    .toBe(true)
  await studyVideo(run.page).evaluate(async (video: HTMLVideoElement) => {
    video.muted = true
    video.playbackRate = 1
    await video.play()
  })
  await expect
    .poll(() => studyVideo(run.page).evaluate((video: HTMLVideoElement) => video.paused))
    .toBe(false)
  await expect.poll(async () => (await session(run)).status).toBe('STUDYING')
  await expect
    .poll(() =>
      studyVideo(run.page).evaluate(
        (video: HTMLVideoElement) => video.getVideoPlaybackQuality().totalVideoFrames,
      ),
    )
    .toBeGreaterThan(0)
}

/** 读取学习服务已确认状态，心跳与客户端播放位置不作为计时结果。 */
export function session(run: StudyRun) {
  return api<LearningSession>(run.page, `/learning/sessions/${run.sessionId}`)
}

/** 上传授权照片走真实抽验接口；此步骤不替代单独的物理摄像头验收。 */
export async function submitFace(
  run: StudyRun,
  task: FaceCheckTask,
  file: string,
  requestId = randomUUID(),
) {
  const token =
    (await run.page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value || ''
  const response = await run.page.request.post(
    `/api/learning/face-checks/${task.taskId}/submissions`,
    {
      headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
      multipart: {
        requestId,
        photo: {
          name: /\.png$/i.test(file) ? 'authorized.png' : 'authorized.jpg',
          mimeType: /\.png$/i.test(file) ? 'image/png' : 'image/jpeg',
          buffer: readFileSync(file),
        },
      },
    },
  )
  const body = await response.json()
  expect(body.code, body.message).toBe('SUCCESS')
  return body.data as FaceCheckTask
}

/** 等待抽验推送与服务端状态一致，并验证等待时不累计有效学时。 */
export async function pendingFace(run: StudyRun) {
  await expect
    .poll(
      async () => {
        const current = await session(run)
        // 短视频结束后由页面选中下一课件，继续真实播放直至抽验触发。
        if (['SIGNED_IN', 'PAUSED'].includes(current.status)) await play(run)
        return current.status
      },
      { timeout: 150000, intervals: [1000] },
    )
    .toBe('FACE_PENDING')
  await expect(run.page.getByRole('dialog', { name: '学习人脸抽验' })).toBeVisible()
  const before = await session(run)
  await run.page.waitForTimeout(2000)
  expect((await session(run)).effectiveDurationMillis).toBe(before.effectiveDurationMillis)
  const task = await api<FaceCheckTask>(run.page, `/learning/sessions/${run.sessionId}/face-check`)
  expect(task.status).toBe('PENDING')
  return task
}

/** 按页面正常操作完成全部课件；不足学时由补学入口补足，保持真实播放。 */
export async function finishStudy(run: StudyRun, samePersonPhoto?: string) {
  const deadline = Date.now() + 720000
  await play(run)
  while (Date.now() < deadline) {
    const current = await session(run)
    if (current.status === 'COMPLETED') return current
    expect(['SIGNED_OUT', 'TERMINATED']).not.toContain(current.status)
    if (current.status === 'FACE_PENDING') {
      if (!samePersonPhoto) throw new Error('出现未配置的人脸抽验。')
      const task = await pendingFace(run)
      const result = await submitFace(run, task, samePersonPhoto)
      expect(result.status).toBe('PASSED')
      await run.page.getByRole('button', { name: '知道了', exact: true }).click()
    } else if (current.status === 'PAUSED' || current.status === 'SIGNED_IN') {
      const supplement = run.page.getByRole('button', { name: '从头补学当前课件' })
      if (await supplement.isVisible()) await supplement.click()
      else await play(run)
    }
    await run.page.waitForTimeout(500)
  }
  throw new Error('真实视频学习超过12分钟，检查播放、抽验和补学状态。')
}

/** 结业后对照学员档案、管理档案和计划统计，记录真实投影等待时间。 */
export async function verifyCompletion(run: StudyRun, admin: Page) {
  const current = await session(run)
  const started = performance.now()
  await expect
    .poll(
      async () =>
        (await api<RecordDetail>(run.page, `/training/student/records/${current.taskId}`)).training
          .completionStatus,
      { timeout: 30000, intervals: [500, 1000] },
    )
    .toBe('COMPLETED')
  const detail = await api<RecordDetail>(run.page, `/training/student/records/${current.taskId}`)
  const management = await api<RecordDetail>(
    admin,
    `/training/statistics/participants/${current.taskId}/record`,
  )
  expect(management).toEqual(detail)
  expect(detail.courses.every((course) => course.status === 'COMPLETED')).toBe(true)
  expect(detail.courses[0]!.effectiveDurationMillis).toBe(detail.courses[0]!.requiredDurationMillis)
  const overview = await api<{ completedCount: number; trainingCompletedCount?: number }>(
    admin,
    `/training/statistics/overview?planId=${run.plan.id}`,
  )
  expect(overview.completedCount).toBe(1)
  return {
    taskId: current.taskId,
    synchronizationWaitMillis: performance.now() - started,
    overview,
  }
}
