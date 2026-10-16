/** 用 Playwright CLI 采集真实 Vue 状态；业务、摄像头和实时连接均为明确的本地演示夹具。 */
import { createRequire } from 'node:module'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const task = path.join(root, 'output/playwright/thesis-advisor-two-20261016')
const out = path.join(root, 'docs/thesis/figures/advisor-two-20261016')
const runtime = process.env.THESIS_NODE_MODULES || 'C:/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules'
const requireRuntime = createRequire(path.join(runtime, 'package.json'))
const sharp = requireRuntime('sharp')
const cli = process.env.PLAYWRIGHT_CLI_JS || 'D:/developEnv/node_global/node_modules/@playwright/cli/playwright-cli.js'
const sessionName = 'advisor-two'
const origin = process.env.THESIS_ORIGIN || 'http://127.0.0.1:5173'
const scale = 3
const canvasCssWidth = 580

/** 串行调用独立 CLI 会话，保留完整输出供来源复核。 */
async function command(args, name) {
  const result = spawnSync(process.execPath, [cli, `-s=${sessionName}`, ...args], { cwd: root, encoding: 'utf8', timeout: 120000, windowsHide: true })
  await writeFile(path.join(task, `${name}.log`), `${result.stdout || ''}${result.stderr || ''}`, 'utf8')
  if (result.status !== 0 || result.error || /### Error\b/.test(result.stdout || '')) throw new Error(`${name}: ${result.error || result.stderr || result.stdout}`)
  return result.stdout
}

/** 把函数及非敏感配置写为 CLI 可复现脚本，不依赖私有账号或原有本机脚本。 */
async function runFunction(fn, options, name) {
  const filename = path.join(task, `${name}.js`)
  await writeFile(filename, `async (page) => (${fn.toString()})(page, ${JSON.stringify(options)})\n`, 'utf8')
  return command(['run-code', `--filename=${filename}`], name)
}

/** 安装严格封闭的截图数据源；未知业务接口失败，绝不透传后端。 */
async function installFixture(page, options) {
  const { origin, mediaPath } = options
  const requests = []
  const sockets = new Set()
  const pendingReplies = []
  let holdReconnect = false
  let faceTask = null
  let sentCount = 0
  const stamp = new Date().toISOString()
  const course = { planCourseId: '3001', courseName: '道路运输安全教育（演示）', sortOrder: 1, requiredDurationMillis: 200000, effectiveDurationMillis: 0, allowSeek: false, progressReportIntervalSeconds: 10, studyToleranceSeconds: 2, status: 'IN_PROGRESS', coursewares: [{ coursewareSnapshotId: '5001', title: '道路运输安全基础（演示）', sortOrder: 1, durationMillis: 100000, confirmedPositionMillis: 0, maxConfirmedPositionMillis: 0, status: 'IN_PROGRESS' }, { coursewareSnapshotId: '5002', title: '学习流程与应急处置（演示）', sortOrder: 2, durationMillis: 100000, confirmedPositionMillis: 0, maxConfirmedPositionMillis: 0, status: 'NOT_STARTED' }] }
  const state = { id: '4001', taskId: '2001', planId: '1001', planCourseId: '3001', courseName: course.courseName, status: 'PAUSED', currentCoursewareSnapshotId: '5001', lastSequence: 5, confirmedPositionMillis: 0, effectiveDurationMillis: 0, requiredDurationMillis: 200000, currentFaceCheck: null, createdAt: stamp }
  const identity = { userId: '101', enterpriseId: '1', enterpriseName: '道路运输演示企业', username: 'demo_thesis_01', displayName: '演示学员甲', platformAdmin: false, mustChangePassword: false, roles: ['STUDENT'], permissions: ['*'], workspaces: ['student'], defaultWorkspace: 'student' }
  /** 仅合成纯灰摄像头视频流，避免触发真实设备。 */
  await page.addInitScript(() => {
    if (!navigator.mediaDevices) Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {} })
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      const canvas = document.createElement('canvas')
      canvas.width = 960; canvas.height = 720
      const context = canvas.getContext('2d')
      context.fillStyle = '#737d8a'; context.fillRect(0, 0, 960, 720)
      return canvas.captureStream(1)
    } })
  })
  await page.route(url => typeof url.pathname === 'string' && url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const requestUrl = request.url()
    if (!requestUrl.startsWith(`${origin}/api/`)) return route.abort('blockedbyclient')
    // CLI 的隔离执行环境不提供 Node URL，按固定本地来源解析即可。
    const p = requestUrl.slice(`${origin}/api`.length).split('?')[0]
    const method = request.method()
    requests.push({ method, path: p })
    let data
    if (method === 'GET' && p === '/auth/csrf') data = { token: 'thesis-fixture-only' }
    else if (method === 'GET' && p === '/auth/me') data = identity
    else if (method === 'GET' && p === '/learning/plans/1001/courses/3001') data = course
    else if (method === 'POST' && p === '/learning/sessions') data = state
    else if (method === 'GET' && p === '/learning/sessions/active') data = null
    else if (method === 'GET' && p === '/learning/sessions/4001') data = state
    else if (method === 'GET' && p.endsWith('/attendance-face')) data = false
    else if (method === 'GET' && p.endsWith('/play-url')) data = { url: `${origin}/__thesis-demo__/training-demo.mp4`, method: 'GET', headers: {}, expiresAt: new Date(Date.now() + 3600000).toISOString() }
    else if (method === 'GET' && p.endsWith('/face-check')) data = faceTask?.status === 'PENDING' ? faceTask : null
    else if (method === 'POST' && p === '/learning/face-checks/6001/submissions' && faceTask?.status === 'PENDING') {
      faceTask = { ...faceTask, status: 'PASSED', attemptCount: 1, remainingAttempts: 2, result: 'MATCHED', similarity: 0.76, completedAt: new Date().toISOString() }
      state.status = 'PAUSED'; state.currentFaceCheck = null
      data = faceTask
    } else return route.fulfill({ status: 501, json: { code: 'UNSUPPORTED_THESIS_FIXTURE', message: `未支持的截图夹具请求 ${method} ${p}` } })
    return route.fulfill({ json: { code: 'SUCCESS', message: '纯虚构界面演示夹具响应', data } })
  })
  await page.route(`${origin}/__thesis-demo__/training-demo.mp4`, route => route.fulfill({ path: mediaPath, contentType: 'video/mp4' }))
  /** 每个连接只对本地会话回包，不连接任何真实 WebSocket 服务。 */
  await page.routeWebSocket('**/ws/learning', socket => {
    sockets.add(socket)
    socket.onMessage(raw => {
      const message = JSON.parse(String(raw))
      /** 按当前组件协议返回确认，夹具始终不增加有效学时。 */
      const reply = () => {
        let type; let payload
        if (['BIND_SESSION', 'SYNC_STATE'].includes(message.type)) { type = 'STATE_SYNC'; payload = state }
        else if (message.type === 'HEARTBEAT') { type = 'PONG'; payload = { serverTime: new Date().toISOString() } }
        else if (['PLAY', 'PAUSE', 'PROGRESS', 'SIGN_OUT'].includes(message.type) && state.status !== 'FACE_PENDING') {
          state.lastSequence = message.seq
          state.status = message.type === 'PLAY' ? 'STUDYING' : message.type === 'PAUSE' ? 'PAUSED' : message.type === 'SIGN_OUT' ? 'SIGNED_OUT' : state.status
          state.confirmedPositionMillis = message.payload.videoPositionMillis
          type = 'PROGRESS_CONFIRMED'
          payload = { sessionId: state.id, requestId: message.requestId, acceptedSequence: message.seq, status: state.status, currentCoursewareSnapshotId: state.currentCoursewareSnapshotId, confirmedPositionMillis: state.confirmedPositionMillis, creditedDurationMillis: 0, effectiveDurationMillis: state.effectiveDurationMillis, requiredDurationMillis: state.requiredDurationMillis, coursewareCompleted: false, courseCompleted: false, serverTime: new Date().toISOString() }
        } else { type = 'ERROR'; payload = { code: 'UNSUPPORTED_THESIS_FIXTURE', message: '截图夹具未支持该动作', retryable: false, resyncRequired: false } }
        socket.send(JSON.stringify({ ...message, type, payload, sentAt: new Date().toISOString() }))
      }
      if (holdReconnect && ['BIND_SESSION', 'SYNC_STATE'].includes(message.type)) pendingReplies.push(reply)
      else reply()
    })
    socket.onClose(() => sockets.delete(socket))
  })
  page.__advisorTwo = {
    /** 关闭演示连接并暂缓同步，使原生恢复提示稳定可截图。 */
    disconnect() { holdReconnect = true; for (const socket of sockets) socket.close({ code: 1012, reason: '论文截图：本地演示连接中断' }); sockets.clear() },
    /** 放行重连同步，交由真实前端完成安全暂停。 */
    reconnect() { holdReconnect = false; for (const reply of pendingReplies.splice(0)) reply() },
    /** 从已有播放器进入抽验，确保通过后可展示同一播放器恢复。 */
    triggerFace() {
      faceTask = { taskId: '6001', sessionId: state.id, status: 'PENDING', triggeredAt: new Date().toISOString(), deadlineAt: new Date(Date.now() + 180000).toISOString(), attemptCount: 0, maxAttempts: 3, remainingAttempts: 3, result: null, failureReason: null, similarity: null, completedAt: null }
      state.status = 'FACE_PENDING'; state.currentFaceCheck = faceTask
      for (const socket of sockets) socket.send(JSON.stringify({ type: 'FACE_CHECK_REQUIRED', requestId: `demo-face-${++sentCount}`, studySessionId: state.id, sentAt: new Date().toISOString(), payload: faceTask }))
    },
    /** 返回不含签名、照片和凭据的采集状态。 */
    inspect() { return { requests: [...requests], state: { ...state }, faceTask: faceTask ? { ...faceTask } : null } },
  }
  await page.goto(`${origin}/student/plans/1001/courses/3001/study`)
  await page.locator('.player-panel video').waitFor({ state: 'visible' })
  await page.waitForFunction(() => document.querySelector('.player-panel video')?.readyState >= 2)
  return { title: await page.title(), fixture: '真实 Vue 页面；HTTP/WS 本地演示夹具；纯灰合成摄像头；未透传后端' }
}

/** 沿原生页面状态依次播放、断线、重连、拍照提交与确认，并原样保存局部截图。 */
async function captureStages(page, options) {
  const stages = []
  const player = page.locator('.player-panel video')
  /** 记录裁剪范围、原生文字字号、实际播放器状态和截图来源。 */
  async function capture(name, clip, selector) {
    const safeClip = { x: Math.floor(clip.x), y: Math.floor(clip.y), width: Math.ceil(clip.width), height: Math.ceil(clip.height) }
    await page.screenshot({ path: `${options.task}/${name}.png`, clip: safeClip })
    const fonts = await page.locator(selector).evaluateAll(elements => elements.flatMap(element => Array.from(element.querySelectorAll('*')).filter(node => node.children.length === 0 && node.textContent.trim() && node.getBoundingClientRect().width > 0).map(node => ({ text: node.textContent.trim(), cssPixels: Number.parseFloat(getComputedStyle(node).fontSize) }))))
    stages.push({ name, clipCss: safeClip, fonts, fixture: page.__advisorTwo.inspect(), media: await player.evaluate(video => ({ paused: video.paused, currentTime: video.currentTime, readyState: video.readyState, videoWidth: video.videoWidth, videoHeight: video.videoHeight, decodedFrames: video.getVideoPlaybackQuality().totalVideoFrames })), snapshot: await page.locator(selector).ariaSnapshot() })
  }
  const startingPosition = await player.evaluate(video => video.currentTime)
  await player.evaluate(video => video.play())
  await page.waitForFunction(start => { const video = document.querySelector('.player-panel video'); return video && !video.paused && video.currentTime > start + 1 }, startingPosition)
  await player.hover()
  // 初次元数据恢复会触发 seeking，等待真实前端 PLAY 确认后再采样。
  const playerBox = await player.boundingBox()
  const panelBox = await page.locator('.player-panel').boundingBox()
  await capture('video-playing', { x: panelBox.x, y: panelBox.y, width: panelBox.width, height: playerBox.y + playerBox.height - panelBox.y + 2 }, '.player-panel')
  page.__advisorTwo.disconnect()
  const recovering = page.getByText('实时学习连接正在恢复，播放器已暂停；同步完成后请手动继续。', { exact: true })
  await recovering.waitFor({ state: 'visible' })
  await page.waitForFunction(() => document.querySelector('.player-panel video').paused)
  await capture('video-disconnected', await page.locator('.study-page > .el-alert').filter({ hasText: '实时学习连接正在恢复' }).boundingBox(), '.study-page > .el-alert')
  page.__advisorTwo.reconnect()
  await recovering.waitFor({ state: 'hidden' })
  const pauseAlert = page.locator('.video-wrapper .el-alert').filter({ hasText: '学习已暂停' })
  await pauseAlert.waitFor({ state: 'visible' })
  if (!(await player.evaluate(video => video.paused))) throw new Error('重连后播放器没有保持暂停')
  await capture('video-reconnected', await pauseAlert.boundingBox(), '.video-wrapper .el-alert')
  await player.evaluate(video => video.play())
  await page.waitForFunction(() => !document.querySelector('.player-panel video').paused)
  page.__advisorTwo.triggerFace()
  const dialog = page.locator('.el-dialog').filter({ hasText: '学习人脸抽验' })
  await dialog.waitFor({ state: 'visible' })
  await dialog.getByRole('button', { name: '启用摄像头' }).click()
  await dialog.getByRole('button', { name: '拍照', exact: true }).click()
  await dialog.getByRole('button', { name: '提交核验' }).click()
  await dialog.getByText('人脸抽验已通过', { exact: true }).waitFor()
  const dialogBox = await dialog.boundingBox()
  await page.screenshot({ path: `${options.task}/face-passed-full.png`, clip: dialogBox })
  // 仅去除弹窗两侧无关留白，保留标题、结果、说明和确认按钮，不缩小文字。
  await capture('face-passed', { ...dialogBox, x: dialogBox.x + (dialogBox.width - 568) / 2, width: 568 }, '.el-dialog')
  await dialog.getByRole('button', { name: '知道了', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  await pauseAlert.waitFor({ state: 'visible' })
  await player.hover()
  const alertBox = await pauseAlert.boundingBox()
  const videoBox = await player.boundingBox()
  await capture('face-paused', { x: videoBox.x, y: videoBox.y + videoBox.height - 90, width: videoBox.width, height: alertBox.y + alertBox.height - videoBox.y - videoBox.height + 92 }, '.video-wrapper')
  if (!(await player.evaluate(video => video.paused))) throw new Error('抽验通过后播放器没有保持暂停')
  return { browserVersion: page.context().browser().version(), userAgent: await page.evaluate(() => navigator.userAgent), viewport: page.viewportSize(), deviceScaleFactor: await page.evaluate(() => devicePixelRatio), stages }
}

/** 转义 SVG 文本，确保阶段标签是说明文字而非改造页面。 */
function xml(value) { return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;') }

/** 仅原样拼接截图与独立阶段标签，不重采样、不重写页面文字。 */
async function compose(name, phases, report) {
  let y = 8 * scale
  const composites = []
  const placements = []
  for (const [stage, label] of phases) {
    const imagePath = path.join(task, `${stage}.png`)
    const metadata = await sharp(imagePath).metadata()
    const labelSvg = `<svg width="${canvasCssWidth * scale}" height="${26 * scale}" xmlns="http://www.w3.org/2000/svg"><text x="${10 * scale}" y="${19 * scale}" font-family="Microsoft YaHei" font-size="${16 * scale}" font-weight="bold" fill="#24344c">${xml(label)}</text></svg>`
    composites.push({ input: Buffer.from(labelSvg), top: y, left: 0 })
    y += 26 * scale
    composites.push({ input: imagePath, top: y, left: Math.floor((canvasCssWidth * scale - metadata.width) / 2) })
    placements.push({ stage, width: metadata.width, height: metadata.height, top: y, resampling: false })
    y += metadata.height + 8 * scale
  }
  const output = path.join(out, `${name}.png`)
  await sharp({ create: { width: canvasCssWidth * scale, height: y, channels: 3, background: '#f5f7fb' } }).composite(composites).png().toFile(output)
  const sha256 = createHash('sha256').update(await readFile(output)).digest('hex')
  const pointsPerCssPixel = 15.5 / 2.54 * 72 / canvasCssWidth
  const includedStages = report.stages.filter(stage => phases.some(([phase]) => phase === stage.name))
  return { file: path.relative(root, output).replaceAll('\\', '/'), sha256, pixels: [canvasCssWidth * scale, y], printWidthCm: 15.5, printHeightCm: 15.5 * y / (canvasCssWidth * scale), pointsPerCssPixel, uiFont14Point: 14 * pointsPerCssPixel, label16Point: 16 * pointsPerCssPixel, minimumRecordedFontPoint: Math.min(...includedStages.flatMap(stage => stage.fonts.map(font => font.cssPixels * pointsPerCssPixel))), placements }
}

/** 提取 CLI 的结构化结果，失败时保留原始日志而非忽略异常。 */
function resultJson(text) {
  const match = text.match(/### Result\s*\n([\s\S]*?)(?:\n### |$)/)
  if (!match) throw new Error(`CLI 未返回结构化结果：${text.slice(0, 200)}`)
  return JSON.parse(match[1])
}

/** 准备隔离浏览器并导出两幅组合图与逐阶段采集报告。 */
async function main() {
  await mkdir(task, { recursive: true }); await mkdir(out, { recursive: true })
  const config = { browser: { browserName: 'chromium', isolated: true, launchOptions: { channel: 'chrome', headless: true }, contextOptions: { deviceScaleFactor: scale, viewport: { width: 600, height: 1450 } } }, outputDir: path.join(task, 'browser-cache') }
  const configPath = path.join(task, 'cli.config.json')
  await writeFile(configPath, JSON.stringify(config), 'utf8')
  const composeOnly = process.argv.includes('--compose-only')
  let report
  if (composeOnly) report = JSON.parse(await readFile(path.join(task, 'capture-stages.json'), 'utf8'))
  else {
    await command(['close'], 'close-previous').catch(() => undefined)
    await command(['open', `--config=${configPath}`], 'open')
    await runFunction(installFixture, { origin, mediaPath: path.join(root, 'tmp/media/training-demo.mp4') }, 'install-fixture')
    await command(['snapshot'], 'snapshot-initial')
    report = resultJson(await runFunction(captureStages, { task: task.replaceAll('\\', '/') }, 'capture-stages'))
    await writeFile(path.join(task, 'capture-stages.json'), JSON.stringify(report, null, 2), 'utf8')
  }
  report.createdAt = new Date().toISOString()
  for (const stage of report.stages) {
    const stagePath = path.join(task, `${stage.name}.png`)
    const stageMetadata = await sharp(stagePath).metadata()
    stage.rawFile = path.relative(root, stagePath).replaceAll('\\', '/')
    stage.rawPixels = [stageMetadata.width, stageMetadata.height]
    stage.rawSha256 = createHash('sha256').update(await readFile(stagePath)).digest('hex')
  }
  report.origin = origin
  report.automation = { engine: 'Playwright CLI', cliPath: cli, nodeExecutable: process.execPath, nodeVersion: process.version, captureMode: '隔离、无登录、headless Chrome；600×1450 CSS 视口，设备比例3；本机前端开发服务' }
  report.captureScriptSha256 = createHash('sha256').update(await readFile(fileURLToPath(import.meta.url))).digest('hex')
  report.source = '当前真实 Vue 组件；纯虚构 HTTP/WebSocket 演示夹具；自制 MP4 实际解码；纯灰合成摄像头；不透传业务后端'
  report.purpose = '论文实现过程界面配图；用户已确认本地验收，本轮截图不充当重新验收的实验结果'
  report.fontMeasurementScope = '逐阶段 fonts 与 minimumRecordedFontPoint 仅记录真实 Vue DOM 业务文字；不包含浏览器视频原生控件和 MP4 内栅格文字，二者另行说明，不能据 DOM 最小值宣称全图所有文字达到相同字号'
  const videoCssWidth = report.stages.find(stage => stage.name === 'face-paused').clipCss.width
  report.mediaTypography = { source: 'scripts/make-demo-video.py', sourceWidthPixels: 960, renderedVideoCssWidth: videoCssWidth, titleSourceFontPixels: 38, smallestSourceFontPixels: 23, smallestRasterFontPointEstimate: 23 * videoCssWidth / 960 * (15.5 / 2.54 * 72 / canvasCssWidth), nativeControls: '浏览器 shadow DOM 原生播放按钮、时间和进度条；未作为可测业务 DOM 文字统计，保留原始截图供视觉复核', note: '视频说明小字属于媒体内容，核心业务提示按 DOM 字号单独核对；没有重绘或放大媒体文字' }
  report.mediaSourceSha256 = createHash('sha256').update(await readFile(path.join(root, 'tmp/media/training-demo.mp4'))).digest('hex')
  report.cropNotes = { 'video-playing': '保留真实课件标题及完整播放器画面；截去其后无关操作区', 'video-disconnected': '原生连接恢复提示的完整 alert 区域', 'video-reconnected': '原生暂停和手动继续提示的完整 alert 区域', 'face-passed': '仅裁去原生成功弹窗两侧空白；完整未裁原图另存 face-passed-full.png', 'face-paused': '同一播放器的下部90CSS像素及完整暂停提示，保留原生播放按钮，裁去上方重复视频内容' }
  report.privacy = '照片采集为纯灰合成流；结果图没有照片区域，也不含真实人脸；本地接口与 WS 均不透传业务后端；无真实账号、凭据或签名 URL'
  report.figures = [await compose('video-recovery', [['video-playing', '（a）视频实际播放'], ['video-disconnected', '（b）连接中断：立即暂停播放和进度上报'], ['video-reconnected', '（c）重连同步完成：保持暂停，手动继续']], report), await compose('face-result', [['face-passed', '（a）摄像头拍照提交后：核验通过'], ['face-paused', '（b）确认结果后：保持暂停，手动继续学习']], report)]
  for (const figure of report.figures) if (figure.printHeightCm > 18 || figure.uiFont14Point < 10.5) throw new Error(`打印尺寸不满足约束：${figure.file}`)
  await writeFile(path.join(task, 'screenshot-report.json'), JSON.stringify(report, null, 2), 'utf8')
  await writeFile(path.join(out, 'screenshot-report.json'), JSON.stringify(report, null, 2), 'utf8')
  if (!composeOnly) await command(['snapshot'], 'snapshot-final')
  console.log(JSON.stringify(report.figures, null, 2))
}

await main()
