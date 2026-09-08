import { createHash } from 'node:crypto'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { readEnvironment } from './api-client.mjs'

export const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
export const mediaScenarios = ['study', 'exam', 'face_pass', 'face_fail', 'face_timeout',
  'exam_timeout', 'recovery', 'isolation', 'camera']
export const loadGroups = [1, 5, 10]

/** 所有入口按仓库根目录解释路径，不受pnpm工作目录影响。 */
export function mediaEnvironment() {
  const [major, minor] = process.versions.node.split('.').map(Number)
  if (major < 22 || (major === 22 && minor < 12)) throw new Error('真实媒体验收需要Node.js 22.12或更新版本，请先切换当前终端的Node路径。')
  return readEnvironment(resolve(repositoryRoot, process.env.TRAIN_ENV_FILE || '.env.local'))
}

/** 返回独立场景账号标识，负向测试和每组压力实验均不复用学习进度。 */
export function mediaStudentKeys() {
  return [...mediaScenarios, ...loadGroups.flatMap((count) =>
    Array.from({ length: count }, (_, index) => `load_${count}_${index + 1}`))]
}

/** 一次报告全部缺失项，不输出密钥值；显式真实验收不能降级为模拟结果。 */
export function requireMediaConfiguration(env, { faces = true } = {}) {
  const errors = []
  for (const key of ['OSS_ENABLED', ...(faces ? ['FACE_ENABLED'] : [])]) {
    if (env[key]?.toLowerCase() !== 'true') errors.push(`${key}=true`)
  }
  for (const key of ['OSS_BUCKET', 'OSS_ACCESS_KEY_ID', 'OSS_ACCESS_KEY_SECRET']) {
    if (!env[key]?.trim() || /^(change-me|your[-_])/i.test(env[key])) errors.push(key)
  }
  const fileKeys = ['TRAIN_DEMO_VIDEO', ...(faces ? ['FACE_DETECTION_MODEL_PATH',
    'FACE_RECOGNITION_MODEL_PATH', 'TRAIN_FACE_REFERENCE', 'TRAIN_FACE_SAME_PERSON',
    'TRAIN_FACE_DIFFERENT_PERSON', 'TRAIN_FACE_MULTIPLE'] : [])]
  const files = {}
  for (const key of fileKeys) {
    const path = env[key] && resolve(repositoryRoot, env[key])
    if (!path || !existsSync(path) || !statSync(path).isFile() || statSync(path).size === 0) {
      errors.push(`${key}（需要存在且非空的本地文件）`)
    } else files[key] = path
  }
  const seconds = Number(env.TRAIN_DEMO_VIDEO_SECONDS)
  if (!Number.isInteger(seconds) || seconds < 60 || seconds > 180) {
    errors.push('TRAIN_DEMO_VIDEO_SECONDS（真实视频时长，60至180秒）')
  }
  if (files.TRAIN_DEMO_VIDEO && (!/\.mp4$/i.test(files.TRAIN_DEMO_VIDEO)
    || statSync(files.TRAIN_DEMO_VIDEO).size > 100 * 1024 * 1024)) {
    errors.push('TRAIN_DEMO_VIDEO（100MiB以内的MP4）')
  }
  for (const key of fileKeys.filter((value) => value.startsWith('TRAIN_FACE_'))) {
    if (files[key] && (!/\.(jpe?g|png)$/i.test(files[key]) || statSync(files[key]).size > 5 * 1024 * 1024)) {
      errors.push(`${key}（5MiB以内的JPEG或PNG）`)
    }
  }
  if (faces && files.TRAIN_FACE_REFERENCE && files.TRAIN_FACE_SAME_PERSON) {
    const hash = (path) => createHash('sha256').update(readFileSync(path)).digest('hex')
    if (hash(files.TRAIN_FACE_REFERENCE) === hash(files.TRAIN_FACE_SAME_PERSON)) {
      errors.push('TRAIN_FACE_SAME_PERSON（必须是同一人的另一张照片，不能复制登记照）')
    }
  }
  if (errors.length) throw new Error(`真实媒体验收条件不足：\n- ${errors.join('\n- ')}`)
  return { files, seconds }
}

/** 只接受脚本创建的数据集；不接收任意企业或学员ID作为测试目标。 */
export function readDemoState(env) {
  const path = resolve(repositoryRoot, env.TRAIN_DEMO_STATE || 'tmp/demo/state.json')
  if (!existsSync(path)) throw new Error('演示数据不存在，请先运行seed-demo.mjs --media。')
  const state = JSON.parse(readFileSync(path, 'utf8'))
  if (!/^demo_\d+$/.test(state.prefix)) throw new Error('演示前缀不合法，拒绝操作。')
  return { path, state }
}

/** 启动浏览器前列出尚未准备的场景，避免空plans对象被误认成发布完成。 */
export function requireMediaState(state, { load = false } = {}) {
  const keys = load ? loadGroups.map((count) => `load_${count}`) : mediaScenarios
  const studentKeys = load ? mediaStudentKeys().filter((key) => key.startsWith('load_')) : mediaScenarios
  const missing = [
    ...keys.filter((key) => !state.media?.plans?.[key]?.id).map((key) => `计划 ${key}`),
    ...studentKeys.filter((key) => !state.media?.students?.[key]?.id
      || !state.media.students[key].username).map((key) => `学员 ${key}`),
  ]
  if (missing.length) throw new Error(`真实媒体验收数据不完整：${missing.join('、')}。请运行seed-demo.mjs --media和publish-demo.mjs --media。`)
}
