import adminRoutes from './admin'
import studentRoutes from './student'

describe('培训计划路由', () => {
  it('管理端列表和详情使用计划查看权限', () => {
    const routes = adminRoutes[0]?.children ?? []
    const list = routes.find((route) => route.name === 'admin-plans')
    const detail = routes.find((route) => route.name === 'admin-plan-detail')

    expect(list?.path).toBe('plans')
    expect(list?.meta?.permission).toBe('admin:plan:view')
    expect(detail?.path).toBe('plans/:id')
    expect(detail?.meta?.permission).toBe('admin:plan:view')
  })

  it('学员工作台默认进入仅属于当前学员的任务路由', () => {
    const routes = studentRoutes[0]?.children ?? []
    const index = routes.find((route) => route.path === '')
    const list = routes.find((route) => route.name === 'student-plans')
    const detail = routes.find((route) => route.name === 'student-plan-detail')
    const study = routes.find((route) => route.name === 'student-study')

    expect(index?.redirect).toBe('/student/plans')
    expect(list?.meta?.permission).toBe('student:plan:view')
    expect(detail?.meta?.permission).toBe('student:plan:view')
    expect(study?.path).toBe('plans/:planId/courses/:planCourseId/study')
    expect(study?.meta?.permission).toBe('student:learning:study')
  })
})
