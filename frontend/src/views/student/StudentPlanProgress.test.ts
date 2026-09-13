import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import StudentPlanView from './StudentPlanView.vue'
import StudentPlanDetailView from './StudentPlanDetailView.vue'

const api = vi.hoisted(() => ({
  getStudentPlans: vi.fn(),
  getStudentPlanProgress: vi.fn(),
  getStudentPlan: vi.fn(),
  getPlanLearningProgress: vi.fn(),
}))

vi.mock('@/api/training', () => api)
vi.mock('@/api/learning', () => api)
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ has: () => true }) }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '100' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

let wrapper: VueWrapper | undefined

describe('学员培训进度展示', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    const plan = {
      taskId: '500',
      planId: '100',
      name: '安全培训',
      status: 'FINISHED',
      startAt: '2026-09-01T00:00:00',
      endAt: '2026-09-12T23:59:59',
      examRequired: true,
      examStatus: 'PASSED',
      studyStatus: 'IN_PROGRESS',
      completionStatus: 'NOT_COMPLETED',
      courses: [
        {
          id: '200',
          courseName: '驾驶安全',
          requiredDurationSeconds: 60,
          coursewares: [{ id: '300', title: '安全驾驶视频', durationSeconds: 60, sortOrder: 1 }],
        },
      ],
    }
    api.getStudentPlanProgress.mockResolvedValue([{
      planId: '100', requiredDurationMillis: 60000, effectiveDurationMillis: 30000,
    }])
    api.getStudentPlan.mockResolvedValue(plan)
    api.getStudentPlans.mockResolvedValue({ records: [plan], total: 1 })
    api.getPlanLearningProgress.mockResolvedValue({
      courses: [
        {
          planCourseId: '200',
          effectiveDurationMillis: 30000,
          requiredDurationMillis: 60000,
          status: 'IN_PROGRESS',
          coursewares: [
            {
              coursewareSnapshotId: '300',
              confirmedPositionMillis: 10000,
              maxConfirmedPositionMillis: 45000,
              status: 'IN_PROGRESS',
            },
          ],
        },
      ],
    })
  })

  afterEach(() => wrapper?.unmount())

  it.each(['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'])('学习状态为 %s 时校验考试入口', async (studyStatus) => {
    const plan = await api.getStudentPlan()
    api.getStudentPlan.mockResolvedValue({ ...plan, status: 'IN_PROGRESS', examStatus: 'NOT_STARTED', studyStatus })
    wrapper = mount(StudentPlanDetailView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const button = wrapper.findAll('button').find((item) => item.text() === '开始考试')!
    expect(button.attributes('disabled') !== undefined).toBe(studyStatus !== 'COMPLETED')
    expect(wrapper.text()).not.toContain('返回我的任务')
  })

  it.each([
    ['FINISHED', 'COMPLETED', -1, 1, true],
    ['FINISHED', 'COMPLETED', -2, -1, false],
    ['FINISHED', 'NOT_COMPLETED', -1, 1, false],
    ['CANCELLED', 'COMPLETED', -1, 1, false],
    ['FINISHED', 'COMPLETED', 1, 2, false],
  ])('回看入口遵守状态 %s、结业 %s 和培训周期', async (status, completionStatus, startDays, endDays, allowed) => {
    const plan = await api.getStudentPlan()
    const now = Date.now()
    api.getStudentPlan.mockResolvedValue({ ...plan, status, completionStatus,
      startAt: new Date(now + Number(startDays) * 86400000).toISOString(),
      endAt: new Date(now + Number(endDays) * 86400000).toISOString(),
    })
    const progress = await api.getPlanLearningProgress()
    progress.courses[0].status = 'COMPLETED'
    wrapper = mount(StudentPlanDetailView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.get('.el-collapse-item__header').trigger('click')
    expect(wrapper.findAll('button').some((button) => button.text() === '回看课程')).toBe(allowed)
  })

  it('计划仍在进行时，已结业学员的任务显示已完成', async () => {
    const plan = await api.getStudentPlan()
    api.getStudentPlans.mockResolvedValue({ records: [{ ...plan, status: 'IN_PROGRESS', completionStatus: 'COMPLETED' }], total: 1 })
    wrapper = mount(StudentPlanView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElTag' }).text()).toBe('已完成')
  })

  it('已结束任务仍展示实际学时、要求学时和考试要求，周期不含时间', async () => {
    wrapper = mount(StudentPlanView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(api.getStudentPlanProgress).toHaveBeenCalledExactlyOnceWith(['100'])
    expect(api.getPlanLearningProgress).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('是否需要考试')
    expect(wrapper.text()).toContain('30秒 / 1分0秒')
    expect(wrapper.text()).toContain('2026-09-01 — 2026-09-12')
    expect(wrapper.text()).not.toContain('23:59:59')
    expect(wrapper.findComponent({ name: 'ElProgress' }).props('percentage')).toBe(50)
  })

  it('一百条任务只调用一次批量接口', async () => {
    api.getStudentPlans.mockResolvedValue({ records: Array.from({ length: 100 }, (_, index) => ({
      planId: String(index + 1), name: '任务', startAt: '2026-09-01', endAt: '2026-09-12',
    })), total: 100 })
    api.getStudentPlanProgress.mockResolvedValue([])
    wrapper = mount(StudentPlanView, { global: { plugins: [ElementPlus], stubs: { AppTable: true } } })
    await flushPromises()
    expect(api.getStudentPlanProgress).toHaveBeenCalledTimes(1)
    expect(api.getStudentPlanProgress.mock.calls[0]![0]).toHaveLength(100)
    expect(api.getPlanLearningProgress).not.toHaveBeenCalled()
  })

  it('课程按有效学时展示，视频按最远确认位置展示，补学不倒退', async () => {
    wrapper = mount(StudentPlanDetailView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.get('.el-collapse-item__header').trigger('click')
    expect(wrapper.text()).toContain('45秒 / 1分0秒')
    expect(
      wrapper.findAllComponents({ name: 'ElProgress' }).map((item) => item.props('percentage')),
    ).toEqual([50, 75])
    expect(wrapper.text()).not.toContain('23:59:59')
    expect(wrapper.text()).not.toContain('开始学习')
  })
})
