import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
import { mediaEnvironment, repositoryRoot } from './lib/media-config.mjs'

/** 本机浏览器检查视频元数据及三秒真实解码，不将此结果等同于OSS/CORS验收。 */
async function main() {
  const env = mediaEnvironment()
  if (!env.TRAIN_DEMO_VIDEO || !Number.isFinite(Number(env.TRAIN_DEMO_VIDEO_SECONDS))) {
    throw new Error('缺少TRAIN_DEMO_VIDEO或TRAIN_DEMO_VIDEO_SECONDS。')
  }
  const { chromium } = createRequire(resolve(repositoryRoot, 'frontend/package.json'))('@playwright/test')
  const browser = await chromium.launch({ channel: env.PLAYWRIGHT_CHANNEL || (process.platform === 'win32' ? 'msedge' : 'chromium') })
  try {
    const page = await browser.newPage({ viewport: { width: 960, height: 540 } })
    await page.setContent('<body style="margin:0;background:#112033"><video muted playsinline style="width:960px;height:540px"></video></body>')
    await page.locator('video').evaluate((video, data) => {
      video.src = `data:video/mp4;base64,${data}`
    }, readFileSync(resolve(repositoryRoot, env.TRAIN_DEMO_VIDEO)).toString('base64'))
    await page.waitForFunction(() => document.querySelector('video').readyState >= 2)
    const duration = await page.locator('video').evaluate((video) => video.duration)
    assert.ok(Math.abs(duration - Number(env.TRAIN_DEMO_VIDEO_SECONDS)) <= 0.1, '浏览器时长与声明不符')
    await page.locator('video').evaluate((video) => video.play())
    await page.waitForTimeout(3000)
    const result = await page.locator('video').evaluate((video) => ({
      durationSeconds: video.duration, observedPositionSeconds: video.currentTime,
      width: video.videoWidth, height: video.videoHeight,
      decodedFrames: video.getVideoPlaybackQuality().totalVideoFrames, mediaError: video.error?.code ?? null,
    }))
    assert.equal(result.mediaError, null)
    assert.ok(result.decodedFrames > 0 && result.observedPositionSeconds >= 2.5)
    const directory = resolve(repositoryRoot, 'tmp/media')
    mkdirSync(directory, { recursive: true })
    const evidence = { scope: '本机data URL视频元数据与三秒解码；未验证OSS、CORS和学时', recordedAt: new Date().toISOString(), ...result }
    writeFileSync(resolve(directory, 'video-browser-check.json'), JSON.stringify(evidence, null, 2) + '\n', 'utf8')
    await page.screenshot({ path: resolve(directory, 'training-demo-preview.png') })
    console.log(JSON.stringify(evidence))
  } finally {
    await browser.close()
  }
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
