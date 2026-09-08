import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'
import type { StudentPlan } from '../src/api/training'
import { repositoryRoot, loadGroups } from '../../scripts/lib/media-config.mjs'
import {
  api,
  loginPage,
  mediaSuite,
  openStudy,
  play,
  session,
  studyVideo,
  type StudyRun,
} from './media-support'

/** 取实际样本的分位数，空样本返回null而非伪造零延迟。 */
function percentile(values: number[], fraction: number) {
  const sorted = [...values].sort((left, right) => left - right)
  return sorted.length
    ? sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * fraction) - 1)]
    : null
}

/** 观察60秒实际媒体位置增量；课件切换时从零继续累计，避免新视频位置掩盖上一段播放。 */
async function observePlayback(run: StudyRun) {
  return studyVideo(run.page).evaluate(
    (video: HTMLVideoElement) =>
      new Promise<{
        mediaPositionMillis: number
        decodedFrames: number
      }>((resolveSample) => {
        let source = video.currentSrc
        let previous = video.currentTime
        let mediaPositionMillis = previous * 1000
        let frames = video.getVideoPlaybackQuality().totalVideoFrames
        let decodedFrames = frames
        const sample = () => {
          const currentFrames = video.getVideoPlaybackQuality().totalVideoFrames
          if (source !== video.currentSrc) {
            source = video.currentSrc
            previous = 0
            frames = 0
          }
          mediaPositionMillis += Math.max(0, video.currentTime - previous) * 1000
          decodedFrames += Math.max(0, currentFrames - frames)
          previous = video.currentTime
          frames = currentFrames
        }
        video.addEventListener('timeupdate', sample)
        video.addEventListener('ended', sample)
        window.setTimeout(() => {
          sample()
          video.pause()
          video.removeEventListener('timeupdate', sample)
          video.removeEventListener('ended', sample)
          resolveSample({ mediaPositionMillis, decodedFrames })
        }, 60000)
      }),
  )
}

for (const count of loadGroups) {
  test(`${count}名独立学员绑定真实会话并正常播放60秒`, async ({ browser }, info) => {
    const suite = mediaSuite(false)
    const runs: StudyRun[] = []
    try {
      for (let index = 1; index <= count; index++) {
        const page = await loginPage(
          browser,
          suite.state.media.students[`load_${count}_${index}`]!.username,
          suite.state.password,
        )
        try {
          runs.push(await openStudy(page, suite.state.media.plans[`load_${count}`]!))
        } catch (error) {
          await page.context().close()
          throw error
        }
      }
      const results = await Promise.all(
        runs.map(async (run) => {
          const started = performance.now()
          await play(run)
          const [playback, startStateVisibleMillis] = await Promise.all([
            observePlayback(run),
            (async () => {
              await expect
                .poll(
                  async () =>
                    (await api<StudentPlan>(run.page, `/training/student/plans/${run.plan.id}`))
                      .studyStatus,
                  { timeout: 30000, intervals: [250, 500] },
                )
                .toBe('IN_PROGRESS')
              return performance.now() - started
            })(),
          ])
          await expect.poll(async () => (await session(run)).status).toBe('PAUSED')
          const current = await session(run)
          const position = playback.mediaPositionMillis
          expect(playback.decodedFrames).toBeGreaterThan(0)
          expect(
            position,
            '60秒观察期间应实际播放至少59秒，缓冲或后台节流不能算作已播放',
          ).toBeGreaterThanOrEqual(59000)
          expect(
            run.results.filter((item) => item.creditedDurationMillis > 0).length,
          ).toBeGreaterThanOrEqual(4)
          expect(current.effectiveDurationMillis).toBeGreaterThan(0)
          expect(current.effectiveDurationMillis).toBeLessThanOrEqual(Math.ceil(position))
          const result = {
            sessionId: run.sessionId,
            elapsedMillis: performance.now() - started,
            mediaPositionMillis: position,
            decodedFrames: playback.decodedFrames,
            effectiveDurationMillis: current.effectiveDurationMillis,
            mediaMinusEffectiveMillis: position - current.effectiveDurationMillis,
            startStateVisibleMillis,
          }
          await run.page.getByRole('button', { name: '正常签退' }).click()
          return result
        }),
      )
      const latencies = runs.flatMap((run) => run.latencies)
      const evidence = {
        scope:
          '真实浏览器1倍速播放、独立学员与学习会话；状态可见延迟包含查询开销，不等于Broker内部投递延迟',
        recordedAt: new Date().toISOString(),
        concurrentStudents: count,
        samples: results,
        progressResponseCount: latencies.length,
        p50Millis: percentile(latencies, 0.5),
        p95Millis: percentile(latencies, 0.95),
      }
      const directory = resolve(repositoryRoot, 'tmp/experiments')
      mkdirSync(directory, { recursive: true })
      writeFileSync(
        resolve(directory, `study-${count}-${Date.now()}.json`),
        JSON.stringify(evidence, null, 2) + '\n',
        'utf8',
      )
      await info.attach('real-study-load.json', {
        body: JSON.stringify(evidence),
        contentType: 'application/json',
      })
    } finally {
      await Promise.all(runs.map((run) => run.page.context().close()))
    }
  })
}
