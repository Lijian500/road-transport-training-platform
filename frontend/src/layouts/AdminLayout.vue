<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'

const router = useRouter()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()

const menus = [
  { path: '/admin', label: '工作台首页', permission: 'admin:dashboard:view', audience: 'all' },
  {
    path: '/admin/enterprises',
    label: '组织管理',
    permission: 'admin:enterprise:view',
    audience: 'platform',
  },
  {
    path: '/admin/addresses',
    label: '地址管理',
    permission: 'admin:address:view',
    audience: 'platform',
  },
  { path: '/admin/orgs', label: '部门管理', permission: 'admin:org:view', audience: 'enterprise' },
  {
    path: '/admin/users',
    label: '用户管理',
    permission: 'admin:user:view',
    audience: 'enterprise',
  },
  {
    path: '/admin/courses',
    label: '课程管理',
    permission: 'admin:course:view',
    audience: 'enterprise',
  },
  {
    path: '/admin/plans',
    label: '培训计划',
    permission: 'admin:plan:view',
    audience: 'enterprise',
  },
  {
    path: '/admin/exam',
    label: '考试管理',
    permission: 'admin:exam:view',
    audience: 'enterprise',
  },
  {
    path: '/admin/statistics',
    label: '培训统计',
    permission: 'admin:statistics:view',
    audience: 'enterprise',
  },
  {
    path: '/admin/roles',
    label: '角色管理',
    permission: 'admin:role:view',
    audience: 'enterprise',
  },
  {
    path: '/admin/permissions',
    label: '权限目录',
    permission: 'admin:permission:view',
    audience: 'enterprise',
  },
]

const visibleMenus = computed(() =>
  menus.filter((menu) => {
    if (!permissionStore.has(menu.permission)) {
      return false
    }
    if (menu.audience === 'platform') {
      return authStore.session?.platformAdmin
    }
    if (menu.audience === 'enterprise') {
      return !authStore.session?.platformAdmin
    }
    return true
  }),
)

/** 退出当前登录并返回登录页。 */
async function logout() {
  await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  await authStore.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="workspace">
    <aside class="workspace__sidebar">
      <div class="workspace__brand">
        <span class="workspace__brand-mark">路</span>
        <span class="workspace__brand-copy">
          <small>道路运输在线培训</small>
          <strong>管理工作台</strong>
        </span>
      </div>
      <nav aria-label="管理端导航">
        <RouterLink v-for="menu in visibleMenus" :key="menu.path" :to="menu.path">
          <span class="workspace__nav-dot" />
          <span>{{ menu.label }}</span>
        </RouterLink>
      </nav>
      <RouterLink
        v-if="authStore.session?.workspaces.includes('student')"
        class="workspace__switch"
        to="/student"
      >
        <span>切换到学员端</span>
        <strong>→</strong>
      </RouterLink>
    </aside>
    <section class="workspace__main">
      <header class="workspace__header">
        <div class="workspace__context">
          <small>当前工作空间</small>
          <strong>{{ authStore.session?.enterpriseName || '平台管理中心' }}</strong>
        </div>
        <div class="workspace__account">
          <span class="workspace__avatar">
            {{ authStore.session?.displayName?.slice(0, 1) || '管' }}
          </span>
          <span class="workspace__account-copy">
            <strong>{{ authStore.session?.displayName }}</strong>
            <small>{{ authStore.session?.username }}</small>
          </span>
          <el-button plain @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="workspace__content">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<style scoped>
.workspace {
  display: grid;
  min-height: 100vh;
  background: transparent;
  grid-template-columns: 256px minmax(0, 1fr);
}

.workspace__sidebar {
  display: flex;
  position: sticky;
  top: 0;
  flex-direction: column;
  gap: 26px;
  height: 100vh;
  padding: 24px 18px;
  color: #fff;
  background:
    radial-gradient(circle at 15% 4%, rgb(116 142 237 / 26%), transparent 16rem),
    linear-gradient(180deg, #182449 0%, #101a38 100%);
  box-shadow: 12px 0 35px rgb(20 30 63 / 10%);
}

.workspace__brand {
  display: flex;
  align-items: center;
  padding: 2px 8px 18px;
  border-bottom: 1px solid rgb(255 255 255 / 10%);
  gap: 12px;
}

.workspace__brand-mark {
  display: grid;
  flex: 0 0 42px;
  height: 42px;
  color: #fff;
  background: linear-gradient(145deg, #6885f2, #365bd9);
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 13px;
  box-shadow: 0 10px 22px rgb(13 27 79 / 35%);
  font-size: 19px;
  font-weight: 800;
  place-items: center;
}

.workspace__brand-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.workspace__brand-copy small {
  overflow: hidden;
  color: #aebbe1;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace__brand-copy strong {
  font-size: 18px;
  letter-spacing: 0.02em;
}

nav {
  display: grid;
  gap: 5px;
}

nav a,
.workspace__switch {
  display: flex;
  align-items: center;
  padding: 11px 13px;
  border-radius: 10px;
  transition:
    color 160ms ease,
    background-color 160ms ease,
    transform 160ms ease;
}

nav a {
  color: #c5cee8;
  gap: 11px;
}

nav a:hover {
  color: #fff;
  background: rgb(255 255 255 / 7%);
  transform: translateX(2px);
}

nav a.router-link-exact-active {
  color: #fff;
  background: linear-gradient(90deg, rgb(92 122 234 / 44%), rgb(92 122 234 / 18%));
  box-shadow: inset 3px 0 #8ca3ff;
}

.workspace__nav-dot {
  width: 7px;
  height: 7px;
  background: currentColor;
  border-radius: 50%;
  opacity: 0.55;
}

nav a.router-link-exact-active .workspace__nav-dot {
  box-shadow: 0 0 0 4px rgb(140 163 255 / 16%);
  opacity: 1;
}

.workspace__switch {
  margin-top: auto;
  justify-content: space-between;
  color: #bdc8e7;
  background: rgb(255 255 255 / 6%);
  border: 1px solid rgb(255 255 255 / 8%);
  font-size: 13px;
}

.workspace__switch:hover {
  color: #fff;
  background: rgb(255 255 255 / 10%);
}

.workspace__main {
  min-width: 0;
}

.workspace__header {
  display: flex;
  position: sticky;
  z-index: 20;
  top: 0;
  align-items: center;
  justify-content: space-between;
  height: 74px;
  padding: 0 clamp(24px, 3.5vw, 48px);
  background: rgb(255 255 255 / 88%);
  border-bottom: 1px solid rgb(227 231 240 / 88%);
  backdrop-filter: blur(18px);
}

.workspace__context,
.workspace__account-copy {
  display: grid;
  gap: 3px;
}

.workspace__context small,
.workspace__account-copy small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.workspace__context strong {
  font-size: 16px;
}

.workspace__account {
  display: flex;
  align-items: center;
  gap: 10px;
}

.workspace__avatar {
  display: grid;
  width: 36px;
  height: 36px;
  color: #fff;
  background: linear-gradient(145deg, #5475eb, var(--app-primary));
  border-radius: 11px;
  box-shadow: 0 7px 16px rgb(49 86 217 / 20%);
  font-size: 14px;
  font-weight: 700;
  place-items: center;
}

.workspace__account-copy {
  min-width: 96px;
}

.workspace__account-copy strong {
  font-size: 13px;
}

.workspace__content {
  width: 100%;
  max-width: 1680px;
  margin: 0 auto;
  padding: clamp(24px, 3.5vw, 48px);
}

@media (width <= 760px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .workspace__sidebar {
    position: static;
    gap: 16px;
    height: auto;
    padding-bottom: 16px;
  }

  nav {
    display: flex;
    overflow-x: auto;
  }

  nav a {
    flex: 0 0 auto;
  }

  .workspace__switch {
    margin-top: 0;
  }
}

@media (width <= 560px) {
  .workspace__header {
    height: 66px;
    padding-inline: 18px;
  }

  .workspace__context small,
  .workspace__account-copy {
    display: none;
  }

  .workspace__content {
    padding: 22px 16px 32px;
  }
}
</style>
