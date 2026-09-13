import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { expect, it, vi } from 'vitest'

import PlanDetailView from './PlanDetailView.vue'

const api = vi.hoisted(() => ({ getPlan: vi.fn() }))
vi.mock('@/api/training', () => api)
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ has: () => false }) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '100' } }) }))

it('只读计划直接回显课程和学员名称，无需候选项接口', async () => {
  api.getPlan.mockResolvedValue({
    name: '安全培训', status: 'IN_PROGRESS', startAt: '2026-09-01', endAt: '2026-09-30',
    examRequired: false, faceCheckEnabled: false,
    courses: [{ id: '1', courseId: '123456', courseName: '驾驶安全', requiredDurationSeconds: 60, coursewares: [] }],
    users: [{ id: '2', userId: '789012', displayName: '张三', username: 'zhangsan', studyStatus: 'NOT_STARTED', completionStatus: 'NOT_COMPLETED' }],
  })
  const wrapper = mount(PlanDetailView, { global: { plugins: [ElementPlus], stubs: { PermissionButton: true, ElTable: true } } })
  try {
    await flushPromises()
    const tags = wrapper.find('.snapshot-tags').text()
    expect(tags).toContain('驾驶安全')
    expect(wrapper.findAll('.snapshot-tags')[1]!.text()).toContain('张三（zhangsan）')
    expect(wrapper.text()).not.toContain('123456')
    expect(wrapper.text()).not.toContain('789012')
    expect(wrapper.text()).not.toContain('返回计划列表')
  } finally {
    wrapper.unmount()
  }
})
