import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'
import { mediaEnvironment, readDemoState, requireMediaConfiguration, requireMediaState, repositoryRoot } from './lib/media-config.mjs'

/** 先校验条件，再显式启动真实媒体或学习并发项目；不自动发布计划或修改配置。 */
function main() {
  const args = process.argv.slice(2)
  if (args.some((arg) => !['--check', '--load'].includes(arg))) throw new Error('参数仅支持--check或--load。')
  const env = mediaEnvironment()
  requireMediaConfiguration(env, { faces: !args.includes('--load') })
  if (args.includes('--check')) {
    console.log('本地媒体配置检查通过；仍须验证运行中服务配置、OSS CORS和浏览器真实播放。')
    return
  }
  const { state } = readDemoState(env)
  requireMediaState(state, { load: args.includes('--load') })
  const project = args.includes('--load') ? 'study-load' : 'media'
  // 使用Node执行已安装的Playwright CLI，避免Windows .cmd的二次shell解析。
  const result = spawnSync(process.execPath, ['node_modules/@playwright/test/cli.js',
    'test', '--config=playwright.config.ts', `--project=${project}`], {
    cwd: resolve(repositoryRoot, 'frontend'), stdio: 'inherit', env: { ...env, TRAIN_MEDIA_LIVE: 'true' },
  })
  if (result.error) throw result.error
  process.exitCode = result.status ?? 1
}

try { main() } catch (error) { console.error(error.message); process.exitCode = 1 }
