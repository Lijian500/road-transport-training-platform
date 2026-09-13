<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import AccountMenu from '@/components/AccountMenu/AccountMenu.vue'
import WorkspacePages from '@/components/WorkspacePages/WorkspacePages.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/** 退出当前登录并返回登录页。 */
async function logout() {
  await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  await authStore.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="workspace">
    <header class="workspace__header">
      <div class="workspace__brand">
        <span class="workspace__brand-mark">学</span>
        <span class="workspace__brand-copy">
          <small>道路运输在线培训</small>
          <strong>学员学习中心</strong>
        </span>
      </div>
      <nav aria-label="学员端导航">
        <RouterLink to="/student" active-class="" exact-active-class="router-link-active"
          >学习首页</RouterLink
        >
        <RouterLink to="/student/plans">我的培训任务</RouterLink>
        <RouterLink to="/student/records">我的学习档案</RouterLink>
        <RouterLink v-if="authStore.session?.workspaces.includes('admin')" to="/admin">
          管理工作台
        </RouterLink>
        <AccountMenu />
        <button type="button" @click="logout">退出登录</button>
      </nav>
    </header>
    <main class="workspace__content">
      <WorkspacePages home="/student" />
    </main>
  </div>
</template>

<style scoped>
.workspace {
  min-height: 100vh;
  background:
    radial-gradient(circle at 8% 0, rgb(70 107 224 / 12%), transparent 26rem),
    linear-gradient(180deg, #f0f4ff 0, var(--app-bg) 360px);
}

.workspace__header {
  display: flex;
  position: sticky;
  z-index: 20;
  top: 0;
  align-items: center;
  justify-content: space-between;
  min-height: 76px;
  padding: 14px clamp(24px, 6vw, 80px);
  background: rgb(255 255 255 / 86%);
  border-bottom: 1px solid rgb(227 231 240 / 90%);
  box-shadow: 0 8px 28px rgb(29 44 83 / 4%);
  backdrop-filter: blur(18px);
}

.workspace__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.workspace__brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  color: #fff;
  background: linear-gradient(145deg, #5475eb, var(--app-primary));
  border-radius: 13px;
  box-shadow: 0 8px 20px rgb(49 86 217 / 22%);
  font-size: 18px;
  font-weight: 800;
  place-items: center;
}

.workspace__brand-copy {
  display: grid;
  gap: 3px;
}

.workspace__brand-copy small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.workspace__brand-copy strong {
  color: var(--app-text);
  font-size: 18px;
}

nav {
  display: flex;
  gap: 8px;
}

nav a,
nav button {
  padding: 9px 12px;
  border-radius: 9px;
  font-weight: 600;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

nav button {
  color: var(--app-text-secondary);
  background: transparent;
  border: 0;
  cursor: pointer;
}

nav a:hover,
nav button:hover {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

nav a.router-link-active {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

.workspace__content {
  width: 100%;
  max-width: 1480px;
  margin: 0 auto;
  padding: 16px clamp(20px, 6vw, 80px) clamp(32px, 5vw, 64px);
}

@media (width <= 640px) {
  .workspace__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  nav {
    overflow-x: auto;
    width: 100%;
  }

  nav a,
  nav button {
    flex: 0 0 auto;
  }
}
</style>
