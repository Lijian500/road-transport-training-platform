import { readFileSync, statSync, writeFileSync } from 'node:fs'
import { basename, resolve } from 'node:path'
import { ApiClient, readEnvironment } from './lib/api-client.mjs'
import { loadGroups, mediaScenarios, requireMediaConfiguration, repositoryRoot } from './lib/media-config.mjs'

const env = readEnvironment(resolve(repositoryRoot, process.env.TRAIN_ENV_FILE || '.env.local'))
const statePath = resolve(repositoryRoot, env.TRAIN_DEMO_STATE || 'tmp/demo/state.json')
const state = JSON.parse(readFileSync(statePath, 'utf8'))

/** 保存演示对象ID以支持重试，凭据仍只留在忽略目录。 */
function save() {
  writeFileSync(statePath, JSON.stringify(state, null, 2) + '\n', 'utf8')
}

/** 生成服务端使用的本地ISO日期时间。 */
function localTime(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
}

/** 通过既有分片接口上传真实视频，失败时取消本次上传会话。 */
async function uploadVideo(client, courseId, title, seconds) {
  const video = resolve(repositoryRoot, env.TRAIN_DEMO_VIDEO)
  const metadata = statSync(video)
  if (!video.toLowerCase().endsWith('.mp4') || metadata.size > 100 * 1024 * 1024) {
    throw new Error('演示脚本仅接收100MiB以内的自制MP4。')
  }
  const session = await client.call(`/training/courses/${courseId}/coursewares/upload-sessions`, 'POST', {
    originalFilename: basename(video), contentType: 'video/mp4', fileSizeBytes: metadata.size,
    clientLastModified: Math.floor(metadata.mtimeMs), title, durationSeconds: seconds,
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
}

/** 先登记授权照片，再发布启用抽验的计划；原始照片不进入状态文件。 */
async function enrollFace(client, studentId, file) {
  const base = `/admin/users/${studentId}/face-reference`
  if ((await client.call(base)).enrolled) return
  const content = readFileSync(file)
  const session = await client.call(`${base}/upload-sessions`, 'POST', {
    originalFilename: basename(file), contentType: /\.png$/i.test(file) ? 'image/png' : 'image/jpeg',
    fileSizeBytes: content.length, clientLastModified: Math.floor(statSync(file).mtimeMs),
  })
  const signed = session.uploadRequest
  const response = await fetch(signed.url, {
    method: signed.method, headers: signed.headers, body: content, signal: AbortSignal.timeout(120000),
  })
  if (!response.ok) throw new Error(`登记照上传失败，HTTP ${response.status}`)
  await client.call(`${base}/upload-sessions/${session.id}/complete`, 'POST')
}

/** 为每个验收场景创建独立任务，已发布快照保持不变。 */
async function publishMedia(client, config) {
  if (!state.media?.students) throw new Error('请先运行seed-demo.mjs --media。')
  if (!state.media.course) {
    state.media.course = await client.call('/training/courses', 'POST', {
      name: `${state.prefix} 真实媒体验收课`, description: '两个顺序课件，验证补学与真实学时',
      requiredDurationSeconds: config.seconds * 2, allowSeek: false,
      progressReportIntervalSeconds: 10, studyToleranceSeconds: 2,
    })
    save()
  }
  let course = await client.call(`/training/courses/${state.media.course.id}`)
  for (let index = course.coursewareCount; index < 2; index++) {
    await uploadVideo(client, course.id, `自制演示课件${index + 1}`, config.seconds)
  }
  if (course.status !== 'ENABLED') await client.call(`/training/courses/${course.id}/status`, 'PATCH', { status: 'ENABLED' })
  course = await client.call(`/training/courses/${course.id}`)
  if (course.requiredDurationSeconds !== config.seconds * 2
    || course.coursewares.some((item) => item.durationSeconds !== config.seconds)) {
    throw new Error('已准备课程与当前视频时长不一致，请使用新的TRAIN_DEMO_STATE重新生成。')
  }
  if (!state.media.timeoutPaper) {
    state.media.timeoutPaper = await client.call('/training/exam/papers', 'POST', {
      name: `${state.prefix} 超时验收试卷`, durationMinutes: 1, passScore: 100, questionScore: 100,
      manualQuestionIds: [state.question.id], randomFillCount: 0,
    })
    save()
  }
  if (state.media.timeoutPaper.status !== 'ENABLED') {
    state.media.timeoutPaper = await client.call(`/training/exam/papers/${state.media.timeoutPaper.id}/enable`, 'POST')
    save()
  }
  const keys = [...mediaScenarios, ...loadGroups.map((count) => `load_${count}`)]
  for (const key of keys) {
    const students = key.startsWith('load_')
      ? Array.from({ length: Number(key.slice(5)) }, (_, index) => state.media.students[`${key}_${index + 1}`])
      : [state.media.students[key]]
    if (students.some((student) => !student)) throw new Error(`缺少${key}场景账号，请重新执行seed-demo.mjs --media。`)
    const face = key.startsWith('face_') || key === 'camera'
    if (face) for (const student of students) await enrollFace(client, student.id, config.files.TRAIN_FACE_REFERENCE)
    if (state.media.plans[key]) {
      const existing = await client.call(`/training/plans/${state.media.plans[key].id}`)
      if (existing.status !== 'DRAFT') {
        if (['FINISHED', 'CANCELLED'].includes(existing.status)) throw new Error(`${key}计划已失效，请使用新的TRAIN_DEMO_STATE。`)
        state.media.plans[key] = existing
        save()
        continue
      }
    }
    const exam = ['exam', 'face_pass', 'exam_timeout', 'camera'].includes(key)
    const start = Date.now() + 120000
    const payload = {
      name: `${state.prefix} ${key}`, description: '独立真实验收场景',
      startAt: localTime(new Date(start)), endAt: localTime(new Date(start + 7 * 86400000)),
      examRequired: exam, examPaperId: exam ? (key === 'exam_timeout' ? state.media.timeoutPaper.id : state.paper.id) : null,
      examPassScore: exam ? 100 : null, faceCheckEnabled: face,
      faceCheckMinIntervalSeconds: 60, faceCheckMaxIntervalSeconds: 90,
      faceCheckTimeoutSeconds: 60, faceCheckMaxAttempts: 2,
    }
    if (!state.media.plans[key]) {
      state.media.plans[key] = await client.call('/training/plans', 'POST', payload)
      save()
    }
    const id = state.media.plans[key].id
    await client.call(`/training/plans/${id}`, 'PUT', { ...payload, courseIds: [course.id], userIds: students.map((student) => student.id) })
    state.media.plans[key] = await client.call(`/training/plans/${id}/publish`, 'POST')
    save()
  }
  console.log('真实媒体场景已发布：独立学习、考试、抽验成功/失败/超时、恢复及1/5/10学员实验。camera账号保留给人工摄像头验收。')
}

/** 上传自制MP4并通过既有业务接口发布两种培训模式，不绕过媒体校验。 */
async function main() {
  if (!/^demo_\d+$/.test(state.prefix) || !state.course || !state.paper) throw new Error('请先完成seed-demo.mjs。')
  const mediaConfig = process.argv.includes('--media') ? requireMediaConfiguration(env) : null
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
    await uploadVideo(client, course.id, '自制安全培训演示视频', seconds)
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
  console.log('两种基础培训计划已发布。')
  if (mediaConfig) await publishMedia(client, mediaConfig)
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
