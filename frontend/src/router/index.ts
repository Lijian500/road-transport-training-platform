import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'

import adminRoutes from './admin'
import studentRoutes from './student'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: {
        title: '登录',
      },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('@/views/ChangePasswordView.vue'),
      meta: {
        title: '修改密码',
        requiresAuth: true,
      },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: {
        title: '无权访问',
      },
    },
    ...adminRoutes,
    ...studentRoutes,
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: {
        title: '页面不存在',
      },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const permissionStore = usePermissionStore()
  if (!authStore.initialized) {
    await authStore.restoreSession()
  }
  if (to.meta.requiresAuth && !authStore.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authStore.authenticated) {
    return authStore.mustChangePassword
      ? { name: 'change-password' }
      : `/${authStore.session?.defaultWorkspace ?? 'student'}`
  }
  if (
    authStore.authenticated &&
    authStore.mustChangePassword &&
    to.name !== 'change-password'
  ) {
    return { name: 'change-password' }
  }
  if (to.meta.permission && !permissionStore.has(to.meta.permission)) {
    return { name: 'forbidden' }
  }
  if (
    to.meta.workspace &&
    authStore.session &&
    !authStore.session.workspaces.includes(to.meta.workspace)
  ) {
    return { name: 'forbidden' }
  }
  return true
})

router.afterEach((to) => {
  const appTitle = import.meta.env.VITE_APP_TITLE || '道路运输在线培训系统'
  document.title = to.meta.title ? `${to.meta.title} - ${appTitle}` : appTitle
})

export default router
