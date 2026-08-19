import type { RouteRecordRaw } from 'vue-router'

const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: {
      requiresAuth: true,
      workspace: 'admin',
      permission: 'admin:workspace:view',
    },
    children: [
      {
        path: '',
        name: 'admin-home',
        component: () => import('@/views/admin/AdminHomeView.vue'),
        meta: {
          title: '管理工作台',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:dashboard:view',
        },
      },
      {
        path: 'enterprises',
        name: 'admin-enterprises',
        component: () => import('@/views/admin/EnterpriseView.vue'),
        meta: {
          title: '组织管理',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:enterprise:view',
        },
      },
      {
        path: 'orgs',
        name: 'admin-orgs',
        component: () => import('@/views/admin/OrgView.vue'),
        meta: {
          title: '部门管理',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:org:view',
        },
      },
      {
        path: 'addresses',
        name: 'admin-addresses',
        component: () => import('@/views/admin/AddressView.vue'),
        meta: {
          title: '地址管理',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:address:view',
        },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserView.vue'),
        meta: {
          title: '用户管理',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:user:view',
        },
      },
      {
        path: 'courses',
        name: 'admin-courses',
        component: () => import('@/views/admin/CourseView.vue'),
        meta: {
          title: '课程管理',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:course:view',
        },
      },
      {
        path: 'courses/:id',
        name: 'admin-course-detail',
        component: () => import('@/views/admin/CourseDetailView.vue'),
        meta: {
          title: '课程详情',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:course:view',
        },
      },
      {
        path: 'plans',
        name: 'admin-plans',
        component: () => import('@/views/admin/PlanView.vue'),
        meta: {
          title: '培训计划',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:plan:view',
        },
      },
      {
        path: 'plans/:id',
        name: 'admin-plan-detail',
        component: () => import('@/views/admin/PlanDetailView.vue'),
        meta: {
          title: '培训计划详情',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:plan:view',
        },
      },
      {
        path: 'roles',
        name: 'admin-roles',
        component: () => import('@/views/admin/RoleView.vue'),
        meta: {
          title: '角色管理',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:role:view',
        },
      },
      {
        path: 'permissions',
        name: 'admin-permissions',
        component: () => import('@/views/admin/PermissionView.vue'),
        meta: {
          title: '权限目录',
          requiresAuth: true,
          workspace: 'admin',
          permission: 'admin:permission:view',
        },
      },
    ],
  },
]

export default adminRoutes
