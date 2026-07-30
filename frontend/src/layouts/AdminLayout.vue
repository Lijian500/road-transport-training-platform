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
    label: '企业管理',
    permission: 'admin:enterprise:view',
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
        <span>道路运输在线培训</span>
        <strong>管理工作台</strong>
      </div>
      <nav aria-label="管理端导航">
        <RouterLink v-for="menu in visibleMenus" :key="menu.path" :to="menu.path">
          {{ menu.label }}
        </RouterLink>
      </nav>
      <RouterLink
        v-if="authStore.session?.workspaces.includes('student')"
        class="workspace__switch"
        to="/student"
      >
        切换到学员端
      </RouterLink>
    </aside>
    <section class="workspace__main">
      <header class="workspace__header">
        <div>
          <strong>{{ authStore.session?.enterpriseName || '平台管理中心' }}</strong>
          <span>{{ authStore.session?.displayName }}（{{ authStore.session?.username }}）</span>
        </div>
        <el-button text @click="logout">退出登录</el-button>
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
  grid-template-columns: 240px minmax(0, 1fr);
}

.workspace__sidebar {
  display: flex;
  position: sticky;
  top: 0;
  flex-direction: column;
  gap: 28px;
  height: 100vh;
  padding: 28px 22px;
  color: #fff;
  background: #142a4c;
}

.workspace__brand {
  display: grid;
  gap: 6px;
}

.workspace__brand span {
  color: #a9b9d3;
  font-size: 13px;
}

.workspace__brand strong {
  font-size: 22px;
}

nav {
  display: grid;
  gap: 6px;
}

nav a,
.workspace__switch {
  display: block;
  padding: 11px 12px;
  border-radius: 8px;
}

nav a.router-link-exact-active {
  background: rgb(255 255 255 / 12%);
}

.workspace__switch {
  margin-top: auto;
  color: #a9b9d3;
  font-size: 13px;
}

.workspace__main {
  min-width: 0;
}

.workspace__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 70px;
  padding: 0 clamp(24px, 4vw, 48px);
  background: #fff;
  border-bottom: 1px solid #e5eaf2;
}

.workspace__header div {
  display: grid;
  gap: 3px;
}

.workspace__header span {
  color: #7b879b;
  font-size: 12px;
}

.workspace__content {
  padding: clamp(24px, 4vw, 48px);
}

@media (width <= 760px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .workspace__sidebar {
    position: static;
    height: auto;
  }

  nav {
    display: flex;
    overflow-x: auto;
  }
}
</style>
