import type { RouteRecordRaw } from 'vue-router'

const studentRoutes: RouteRecordRaw[] = [
  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: {
      requiresAuth: true,
      workspace: 'student',
    },
    children: [
      {
        path: '',
        redirect: '/student/plans',
      },
      {
        path: 'plans',
        name: 'student-plans',
        component: () => import('@/views/student/StudentPlanView.vue'),
        meta: {
          title: '我的培训任务',
          requiresAuth: true,
          workspace: 'student',
          permission: 'student:plan:view',
        },
      },
      {
        path: 'plans/:id',
        name: 'student-plan-detail',
        component: () => import('@/views/student/StudentPlanDetailView.vue'),
        meta: {
          title: '培训任务详情',
          requiresAuth: true,
          workspace: 'student',
          permission: 'student:plan:view',
        },
      },
      {
        path: 'plans/:planId/courses/:planCourseId/study',
        name: 'student-study',
        component: () => import('@/views/student/StudyView.vue'),
        meta: {
          title: '视频学习',
          requiresAuth: true,
          workspace: 'student',
          permission: 'student:learning:study',
        },
      },
    ],
  },
]

export default studentRoutes
