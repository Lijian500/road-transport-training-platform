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
        name: 'student-home',
        component: () => import('@/views/student/StudentHomeView.vue'),
        meta: {
          title: '学员学习中心',
          requiresAuth: true,
          workspace: 'student',
        },
      },
    ],
  },
]

export default studentRoutes
