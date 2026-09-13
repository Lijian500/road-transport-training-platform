import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { randomBytes } from 'node:crypto'
import { dirname, resolve } from 'node:path'
import { ApiClient, readEnvironment } from './lib/api-client.mjs'
import { mediaStudentKeys, repositoryRoot } from './lib/media-config.mjs'

const env = readEnvironment(resolve(repositoryRoot, process.env.TRAIN_ENV_FILE || '.env.local'))
const statePath = resolve(repositoryRoot, env.TRAIN_DEMO_STATE || 'tmp/demo/state.json')
const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : {
  prefix: `demo_${Date.now()}`,
  password: `Demo!${randomBytes(12).toString('hex')}`,
  temporaryPassword: `Temp!${randomBytes(12).toString('hex')}`,
  createdAt: new Date().toISOString(),
}
const baseURL = env.TRAIN_API_URL || 'http://127.0.0.1:8080'

/** 每步持久化到忽略目录，允许失败后继续；不修改既有业务企业。 */
function save() {
  mkdirSync(dirname(statePath), { recursive: true })
  writeFileSync(statePath, JSON.stringify(state, null, 2) + '\n', 'utf8')
}

/** 只对本脚本新建账号执行首次改密，不重置任何既有账号。 */
async function loginDemo(username) {
  const client = new ApiClient(baseURL)
  let session
  try {
    session = await client.login(username, state.password)
  } catch {
    session = await client.login(username, state.temporaryPassword)
  }
  if (session.mustChangePassword) {
    await client.call('/auth/change-password', 'POST', {
      oldPassword: state.temporaryPassword, newPassword: state.password,
    })
  }
  return client
}

/** 从已维护的行政字典选择首个区县，避免写入伪造行政代码。 */
async function district(root) {
  let parent = '0'
  for (let level = 1; level <= 3; level++) {
    const nodes = await root.call(`/admin/addresses/children?parentCode=${parent}`)
    if (!nodes.length) throw new Error('行政字典缺少三级区县，请先导入地址数据。')
    const node = nodes[0]
    if (level === 3) return node.id
    parent = node.areaCode
  }
}

/** 建立两个独立虚构企业、学员、车辆和可续建培训素材。 */
async function main() {
  if (!/^demo_\d+$/.test(state.prefix)) throw new Error('演示前缀不合法，拒绝操作。')
  save()
  const root = new ApiClient(baseURL)
  const session = await root.login(env.APP_BOOTSTRAP_ADMIN_USERNAME, env.APP_BOOTSTRAP_ADMIN_PASSWORD)
  if (!session.platformAdmin || session.mustChangePassword) {
    throw new Error('需要已完成首次改密的平台账号；脚本不会修改平台账号密码。')
  }
  const areaId = await district(root)
  for (const suffix of ['a', 'b']) {
    const key = `enterprise_${suffix}`
    const username = `${state.prefix}_${suffix}`
    if (!state[key]) {
      state[key] = await root.call('/admin/enterprises', 'POST', {
        code: username, name: `论文演示运输企业${suffix.toUpperCase()}`, organizationNature: 'ENTERPRISE',
        areaId, adminUsername: username, adminDisplayName: `演示管理员${suffix.toUpperCase()}`,
        temporaryPassword: state.temporaryPassword,
      })
      save()
    }
    const admin = await loginDemo(username)
    if (!state[`org_${suffix}`]) {
      state[`org_${suffix}`] = await admin.call('/admin/orgs', 'POST', {
        parentId: null, code: `${state.prefix}_dept_${suffix}`, name: '演示安全培训部', sortOrder: 1,
      })
      save()
    }
    if (!state[`student_${suffix}`]) {
      const roles = await admin.call('/admin/roles/options')
      const role = roles.find((item) => item.code === 'STUDENT')
      if (!role) throw new Error('新企业未初始化学员角色。')
      state[`student_${suffix}`] = await admin.call('/admin/users', 'POST', {
        username: `${state.prefix}_student_${suffix}`, displayName: `演示学员${suffix.toUpperCase()}`,
        orgId: state[`org_${suffix}`].id, temporaryPassword: state.temporaryPassword, roleIds: [role.id],
      })
      save()
    }
    await loginDemo(`${state.prefix}_student_${suffix}`)
    if (!state[`vehicle_${suffix}`]) {
      state[`vehicle_${suffix}`] = await admin.call('/admin/vehicles', 'POST', {
        plateNumber: '川A00001', vehicleType: '演示货车', orgId: state[`org_${suffix}`].id,
        remark: '虚构演示数据，不对应实际车辆',
      })
      save()
    }
  }
  const admin = await loginDemo(`${state.prefix}_a`)
  if (process.argv.includes('--media')) {
    state.media ||= { students: {}, plans: {} }
    const roles = await admin.call('/admin/roles/options')
    const role = roles.find((item) => item.code === 'STUDENT')
    if (!role) throw new Error('新企业未初始化学员角色。')
    for (const key of mediaStudentKeys()) {
      const username = `${state.prefix}_${key}`
      if (!state.media.students[key]) {
        state.media.students[key] = await admin.call('/admin/users', 'POST', {
          username, displayName: `验收学员 ${key}`, orgId: state.org_a.id,
          temporaryPassword: state.temporaryPassword, roleIds: [role.id],
        })
        save()
      }
      await loginDemo(username)
    }
  }
  if (!state.course) {
    state.course = await admin.call('/training/courses', 'POST', {
      name: `${state.prefix} 安全培训演示课`, description: '虚构课程，用于流程演示与验收',
      requiredDurationSeconds: 60, allowSeek: false, progressReportIntervalSeconds: 10, studyToleranceSeconds: 2,
    })
    save()
  }
  if (!state.question) {
    state.question = await admin.call('/training/exam/questions', 'POST', {
      questionType: 'SINGLE_CHOICE', content: '演示题：学习结束后应执行哪个操作？',
      options: ['完成签退', '直接关闭页面'], correctAnswer: 'A', analysis: '完成签退后由服务端确认有效学时。',
    })
    save()
  }
  await admin.call(`/training/exam/questions/${state.question.id}/status`, 'PATCH', { status: 'ENABLED' })
  if (!state.paper) {
    state.paper = await admin.call('/training/exam/papers', 'POST', {
      name: `${state.prefix} 演示测验`, description: '仅用于演示', durationMinutes: 5,
      passScore: 100, questionScore: 100, manualQuestionIds: [state.question.id], randomFillCount: 0,
    })
    save()
  }
  if (state.paper.status !== 'ENABLED') {
    state.paper = await admin.call(`/training/exam/papers/${state.paper.id}/enable`, 'POST')
    save()
  }
  state.storage = await admin.call('/training/storage/capability')
  state.preparedAt = new Date().toISOString()
  save()
  console.log(`演示数据已准备：${statePath}（包含随机演示密码，请勿提交或公开）`)
  console.log(`企业2个、学员2个、车辆2辆、课程草稿1个、已启用试卷1份。OSS能力：${state.storage.enabled ? '可用' : '未配置'}`)
  if (process.argv.includes('--media')) console.log(`另已准备${Object.keys(state.media.students).length}个独立媒体验收学员，计划需通过publish-demo.mjs --media发布。`)
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
