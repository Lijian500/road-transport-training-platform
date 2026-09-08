import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'
import type { Course, StudentPlan } from '../src/api/training'
import type { ExamRecord } from '../src/api/exam'
import type { LearningEventResult } from '../src/api/learning'
import {
  api,
  finishStudy,
  loginPage,
  mediaSuite,
  openStudy,
  pendingFace,
  play,
  raw,
  session,
  studyVideo,
  submitFace,
  verifyCompletion,
} from './media-support'

let suite: ReturnType<typeof mediaSuite>
test.beforeAll(() => {
  suite = mediaSuite()
})

test('真实浏览器上传MP4、解码元数据及跨域Range读取', async ({ browser }) => {
  const page = await loginPage(browser, `${suite.state.prefix}_a`, suite.state.password)
  try {
    const course = await api<Course>(page, '/training/courses', 'POST', {
      name: `${suite.state.prefix} 浏览器上传 ${Date.now()}`,
      requiredDurationSeconds: 60,
      allowSeek: false,
      progressReportIntervalSeconds: 10,
      studyToleranceSeconds: 2,
    })
    await page.goto(`/admin/courses/${course.id}`)
    await page
      .locator('input[type=file][accept=".mp4,video/mp4"]')
      .setInputFiles(suite.files.TRAIN_DEMO_VIDEO!)
    await expect
      .poll(
        async () => (await api<Course>(page, `/training/courses/${course.id}`)).coursewareCount,
        { timeout: 180000, intervals: [1000] },
      )
      .toBe(1)
    const uploaded = await api<Course>(page, `/training/courses/${course.id}`)
    expect(Math.abs(uploaded.coursewares[0]!.durationSeconds - suite.seconds)).toBeLessThanOrEqual(
      1,
    )
  } finally {
    await page.context().close()
  }
})

for (const scenario of ['study', 'exam', 'face_pass'] as const) {
  test(`真实闭环：${scenario}，学习、考试与档案统计一致`, async ({ browser }, info) => {
    const page = await loginPage(
      browser,
      suite.state.media.students[scenario]!.username,
      suite.state.password,
    )
    const admin = await loginPage(browser, `${suite.state.prefix}_a`, suite.state.password)
    try {
      const run = await openStudy(page, suite.state.media.plans[scenario]!)
      const video = studyVideo(page)
      const range = await video.evaluate(async (element: HTMLVideoElement) => {
        const response = await fetch(element.currentSrc, { headers: { Range: 'bytes=0-15' } })
        return {
          status: response.status,
          length: (await response.arrayBuffer()).byteLength,
          duration: element.duration,
        }
      })
      expect(range.status).toBe(206)
      expect(range.length).toBe(16)
      expect(Math.abs(range.duration - suite.seconds)).toBeLessThanOrEqual(1)
      // 第二课件未解锁时，即使直接请求签名也必须被拒绝。
      const clientId = await page.evaluate(() =>
        localStorage.getItem('road-training:learning-client-instance'),
      )
      const locked = await raw(
        page,
        `/learning/sessions/${run.sessionId}/coursewares/${run.course.coursewares[1]!.coursewareSnapshotId}/play-url?clientInstanceId=${clientId}`,
      )
      expect(locked.code).toBe('L3006')
      const completed = await finishStudy(
        run,
        scenario === 'face_pass' ? suite.files.TRAIN_FACE_SAME_PERSON : undefined,
      )
      expect(completed.effectiveDurationMillis).toBe(completed.requiredDurationMillis)
      await page.getByRole('button', { name: '正常签退' }).click()
      if (scenario !== 'study') {
        const examId = await passExam(page, run.plan.id)
        const other = await loginPage(
          browser,
          `${suite.state.prefix}_student_b`,
          suite.state.password,
        )
        try {
          expect((await raw(other, `/exams/records/${examId}`)).code).toBe('T1322')
        } finally {
          await other.context().close()
        }
      }
      const evidence = await verifyCompletion(run, admin)
      await info.attach('real-completion.json', {
        body: JSON.stringify(evidence),
        contentType: 'application/json',
      })
      await page.goto('/student/records')
      await expect(page.getByText(run.plan.name, { exact: true })).toBeVisible()
      await page.screenshot({
        path: `../output/playwright/${scenario}-media-real.png`,
        fullPage: true,
      })
    } finally {
      await page.context().close()
      await admin.context().close()
    }
  })
}

/** 答案经真实保存接口落库，刷新后恢复；重复交卷不得产生第二份成绩。 */
async function passExam(page: Page, planId: string) {
  await expect
    .poll(
      async () => (await api<StudentPlan>(page, `/training/student/plans/${planId}`)).studyStatus,
      { timeout: 30000, intervals: [500] },
    )
    .toBe('COMPLETED')
  expect((await api<StudentPlan>(page, `/training/student/plans/${planId}`)).completionStatus).toBe(
    'NOT_COMPLETED',
  )
  await page.goto(`/student/plans/${planId}/exam`)
  await page.getByRole('radio', { name: /完成签退/ }).check()
  await expect(page.getByText('答案已保存', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('radio', { name: /完成签退/ })).toBeChecked()
  const record = await api<ExamRecord>(page, `/exams/plans/${planId}/records`, 'POST')
  const first = await api<ExamRecord>(page, `/exams/records/${record.id}/submit`, 'POST')
  const duplicate = await api<ExamRecord>(page, `/exams/records/${record.id}/submit`, 'POST')
  expect(duplicate).toEqual(first)
  expect(first.passed).toBe(true)
  expect(first.score).toBe(100)
  return first.id
}

for (const scenario of ['face_fail', 'face_timeout'] as const) {
  test(`真实抽验：${scenario}，停止计时且监管保留失败或零提交记录`, async ({ browser }) => {
    const page = await loginPage(
      browser,
      suite.state.media.students[scenario]!.username,
      suite.state.password,
    )
    const admin = await loginPage(browser, `${suite.state.prefix}_a`, suite.state.password)
    try {
      const run = await openStudy(page, suite.state.media.plans[scenario]!)
      await play(run)
      const task = await pendingFace(run)
      const before = await session(run)
      if (scenario === 'face_fail') {
        const requestId = randomUUID()
        const first = await submitFace(run, task, suite.files.TRAIN_FACE_MULTIPLE!, requestId)
        expect(first.status).toBe('PENDING')
        expect(first.attemptCount).toBe(1)
        expect(await submitFace(run, task, suite.files.TRAIN_FACE_MULTIPLE!, requestId)).toEqual(
          first,
        )
        const failed = await submitFace(run, task, suite.files.TRAIN_FACE_DIFFERENT_PERSON!)
        expect(failed.status).toBe('FAILED')
        expect(failed.attemptCount).toBe(2)
      }
      await expect
        .poll(async () => (await session(run)).status, { timeout: 75000, intervals: [1000] })
        .toBe('TERMINATED')
      expect((await session(run)).effectiveDurationMillis).toBe(before.effectiveDurationMillis)
      const records = await api<{
        records: Array<{ status: string; attemptCount: number; attempts: unknown[] }>
      }>(admin, `/learning/records/admin/sessions/${run.sessionId}/face-checks`)
      expect(records.records[0]!.status).toBe(scenario === 'face_fail' ? 'FAILED' : 'TIMED_OUT')
      expect(records.records[0]!.attempts).toHaveLength(scenario === 'face_fail' ? 2 : 0)
    } finally {
      await page.context().close()
      await admin.context().close()
    }
  })
}

test('真实考试截止后自动判分，未通过不结业', async ({ browser }) => {
  const page = await loginPage(
    browser,
    suite.state.media.students.exam_timeout!.username,
    suite.state.password,
  )
  try {
    const run = await openStudy(page, suite.state.media.plans.exam_timeout!)
    await finishStudy(run)
    await page.getByRole('button', { name: '正常签退' }).click()
    await expect
      .poll(
        async () =>
          (await api<StudentPlan>(page, `/training/student/plans/${run.plan.id}`)).studyStatus,
        { timeout: 30000, intervals: [500] },
      )
      .toBe('COMPLETED')
    const record = await api<ExamRecord>(page, `/exams/plans/${run.plan.id}/records`, 'POST')
    await expect
      .poll(async () => (await api<ExamRecord>(page, `/exams/records/${record.id}`)).status, {
        timeout: 90000,
        intervals: [1000],
      })
      .toBe('TIMEOUT')
    const result = await api<ExamRecord>(page, `/exams/records/${record.id}`)
    expect(result.passed).toBe(false)
    expect(result.score).toBe(0)
    expect(
      (await api<StudentPlan>(page, `/training/student/plans/${run.plan.id}`)).completionStatus,
    ).toBe('NOT_COMPLETED')
  } finally {
    await page.context().close()
  }
})

test('真实断网恢复、暂停、重复事件、乱序、旧会话和跨企业访问', async ({ browser }) => {
  const page = await loginPage(
    browser,
    suite.state.media.students.recovery!.username,
    suite.state.password,
  )
  const other = await loginPage(browser, `${suite.state.prefix}_student_b`, suite.state.password)
  try {
    const run = await openStudy(page, suite.state.media.plans.recovery!)
    await play(run)
    await expect.poll(async () => (await session(run)).effectiveDurationMillis).toBeGreaterThan(0)
    await studyVideo(page).evaluate((video: HTMLVideoElement) => video.pause())
    await expect.poll(async () => (await session(run)).status).toBe('PAUSED')
    const paused = await session(run)
    await page.waitForTimeout(2000)
    expect((await session(run)).effectiveDurationMillis).toBe(paused.effectiveDurationMillis)
    await play(run)
    await page.context().setOffline(true)
    await expect
      .poll(() => studyVideo(page).evaluate((video: HTMLVideoElement) => video.paused), {
        timeout: 45000,
      })
      .toBe(true)
    await page.context().setOffline(false)
    await expect.poll(async () => (await session(run)).status, { timeout: 45000 }).toBe('PAUSED')
    expect(await studyVideo(page).evaluate((video: HTMLVideoElement) => video.paused)).toBe(true)
    const current = await session(run)
    const clientInstanceId = await page.evaluate(() =>
      localStorage.getItem('road-training:learning-client-instance'),
    )
    const accepted = run.messages.findLast((message) => message.type === 'PAUSE')!
    const payload = {
      clientInstanceId,
      requestId: accepted.requestId,
      sequence: accepted.seq,
      eventType: 'PAUSE',
      ...(accepted.payload as object),
    }
    const duplicate = await api<LearningEventResult>(
      page,
      `/learning/sessions/${run.sessionId}/events`,
      'POST',
      payload,
    )
    expect(duplicate.acceptedSequence).toBe(accepted.seq)
    expect((await session(run)).effectiveDurationMillis).toBe(current.effectiveDurationMillis)
    expect(
      (
        await raw(page, `/learning/sessions/${run.sessionId}/events`, 'POST', {
          ...payload,
          requestId: randomUUID(),
          sequence: current.lastSequence + 2,
        })
      ).code,
    ).toBe('L3005')
    expect((await raw(other, `/learning/sessions/${run.sessionId}`)).code).toBe('L3002')
    expect((await raw(other, `/training/student/records/${current.taskId}`)).code).toBe('T1205')
    const secondTab = await page.context().newPage()
    await secondTab.goto(`/student/plans/${run.plan.id}/courses/${run.course.planCourseId}/study`)
    await expect(
      page.getByText('学习连接已被其他页面接管，本页面不会继续累计学时。', { exact: true }),
    ).toBeVisible()
    await page.waitForTimeout(2000)
    expect((await session(run)).effectiveDurationMillis).toBe(current.effectiveDurationMillis)
    await api(page, `/learning/sessions/${run.sessionId}/terminate`, 'POST')
    expect(
      (
        await raw(page, `/learning/sessions/${run.sessionId}/events`, 'POST', {
          ...payload,
          requestId: randomUUID(),
          sequence: current.lastSequence + 1,
        })
      ).code,
    ).toBe('L3008')
  } finally {
    await page.context().setOffline(false)
    await page.context().close()
    await other.context().close()
  }
})
