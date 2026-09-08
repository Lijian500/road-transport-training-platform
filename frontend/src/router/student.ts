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
          title: '学习中心',
          requiresAuth: true,
          workspace: 'student',
          permission: 'student:plan:view',
        },
      },
      {
        path: 'records',
        name: 'student-records',
        component: () => import('@/views/student/StudentRecordsView.vue'),
        meta: {
          title: '我的学习档案',
          requiresAuth: true,
          workspace: 'student',
          permission: 'student:plan:view',
        },
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
      {
        path: 'plans/:planId/exam',
        name: 'student-exam',
        component: () => import('@/views/student/StudentExamView.vue'),
        meta: {
          title: '在线考试',
          requiresAuth: true,
          workspace: 'student',
          permission: 'student:exam:take',
        },
      },
    ],
  },
]

export default studentRoutes
