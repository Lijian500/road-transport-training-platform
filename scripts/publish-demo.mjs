import { readFileSync, statSync, writeFileSync } from 'node:fs'
import { basename, resolve } from 'node:path'
import { ApiClient, readEnvironment } from './lib/api-client.mjs'

const env = readEnvironment(process.env.TRAIN_ENV_FILE || '.env.local')
const statePath = resolve(process.env.TRAIN_DEMO_STATE || 'tmp/demo/state.json')
const state = JSON.parse(readFileSync(statePath, 'utf8'))

/** 保存演示对象ID以支持重试，凭据仍只留在忽略目录。 */
function save() {
  writeFileSync(statePath, JSON.stringify(state, null, 2) + '\n', 'utf8')
}

/** 生成服务端使用的本地ISO日期时间。 */
function localTime(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
}

/** 上传自制MP4并通过既有业务接口发布两种培训模式，不绕过媒体校验。 */
async function main() {
  if (!/^demo_\d+$/.test(state.prefix) || !state.course || !state.paper) throw new Error('请先完成seed-demo.mjs。')
  const client = new ApiClient(env.TRAIN_API_URL || 'http://127.0.0.1:8080')
  await client.login(`${state.prefix}_a`, state.password)
  const capability = await client.call('/training/storage/capability')
  if (!capability.enabled) throw new Error(`OSS不可用：${capability.message}`)
  let course = await client.call(`/training/courses/${state.course.id}`)
  if (!course.coursewareCount) {
    const seconds = Number(env.TRAIN_DEMO_VIDEO_SECONDS)
    if (!env.TRAIN_DEMO_VIDEO || !Number.isInteger(seconds) || seconds < 60) {
      throw new Error('请设置TRAIN_DEMO_VIDEO与真实时长TRAIN_DEMO_VIDEO_SECONDS，MP4须至少60秒。')
    }
    const video = resolve(env.TRAIN_DEMO_VIDEO)
    const metadata = statSync(video)
    if (!video.toLowerCase().endsWith('.mp4') || metadata.size > 100 * 1024 * 1024) {
      throw new Error('演示脚本仅接收100MiB以内的自制MP4。')
    }
    const session = await client.call(`/training/courses/${course.id}/coursewares/upload-sessions`, 'POST', {
      originalFilename: basename(video), contentType: 'video/mp4', fileSizeBytes: metadata.size,
      clientLastModified: Math.floor(metadata.mtimeMs), title: '自制安全培训演示视频', durationSeconds: seconds,
    })
    try {
      const content = readFileSync(video)
      for (let part = 1; part <= session.partCount; part++) {
        const [signed] = await client.call(`/training/upload-sessions/${session.id}/part-urls`, 'POST', { partNumbers: [part] })
        const response = await fetch(signed.url, {
          method: signed.method, headers: signed.headers,
          body: content.subarray((part - 1) * session.partSizeBytes, part * session.partSizeBytes),
          signal: AbortSignal.timeout(120000),
        })
        if (!response.ok) throw new Error(`OSS分片${part}失败，HTTP ${response.status}`)
      }
      await client.call(`/training/upload-sessions/${session.id}/complete`, 'POST')
    } catch (error) {
      await client.call(`/training/upload-sessions/${session.id}`, 'DELETE').catch(() => undefined)
      throw error
    }
    course = await client.call(`/training/courses/${course.id}`)
  }
  if (course.status !== 'ENABLED') await client.call(`/training/courses/${course.id}/status`, 'PATCH', { status: 'ENABLED' })
  for (const examRequired of [false, true]) {
    const key = examRequired ? 'plan_exam' : 'plan_study'
    if (state[key] && state[key].status !== 'DRAFT') continue
    const payload = {
      name: `${state.prefix} ${examRequired ? '学习加考试' : '仅学习'}`,
      description: '独立虚构演示计划', startAt: localTime(new Date(Date.now() + 120000)),
      endAt: localTime(new Date(Date.now() + 7 * 86400000)), examRequired,
      examPaperId: examRequired ? state.paper.id : null, examPassScore: examRequired ? 100 : null,
      faceCheckEnabled: false, faceCheckMinIntervalSeconds: 60, faceCheckMaxIntervalSeconds: 90,
      faceCheckTimeoutSeconds: 30, faceCheckMaxAttempts: 2,
    }
    if (!state[key]) {
      state[key] = await client.call('/training/plans', 'POST', payload)
      save()
    }
    await client.call(`/training/plans/${state[key].id}`, 'PUT', { ...payload, courseIds: [course.id], userIds: [state.student_a.id] })
    state[key] = await client.call(`/training/plans/${state[key].id}/publish`, 'POST')
    save()
  }
  console.log('两种培训计划发布成功，将于约2分钟后开始。人脸抽验需另行登记授权照片并发布开启抽验的计划。')
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
