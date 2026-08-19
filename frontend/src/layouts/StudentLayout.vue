<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function logout() {
  await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  await authStore.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="workspace">
    <header class="workspace__header">
      <div>
        <span>道路运输在线培训</span>
        <strong>学员学习中心</strong>
      </div>
      <nav aria-label="学员端导航">
        <RouterLink to="/student/plans">我的培训任务</RouterLink>
        <RouterLink v-if="authStore.session?.workspaces.includes('admin')" to="/admin">
          管理工作台
        </RouterLink>
        <button type="button" @click="logout">退出登录</button>
      </nav>
    </header>
    <main class="workspace__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.workspace {
  min-height: 100vh;
  background: linear-gradient(180deg, #edf4ff 0, #f7f9fc 320px);
}

.workspace__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px clamp(24px, 6vw, 80px);
  background: rgb(255 255 255 / 88%);
  border-bottom: 1px solid #dfe7f3;
}

.workspace__header div {
  display: grid;
  gap: 4px;
}

.workspace__header span {
  color: #6a7891;
  font-size: 12px;
}

.workspace__header strong {
  font-size: 20px;
}

nav {
  display: flex;
  gap: 8px;
}

nav a {
  padding: 9px 12px;
  border-radius: 8px;
}

nav button {
  padding: 9px 12px;
  color: #5f6c85;
  background: transparent;
  border: 0;
  cursor: pointer;
}

nav a.router-link-active {
  color: #155eef;
  background: #eaf1ff;
}

.workspace__content {
  padding: clamp(32px, 6vw, 80px);
}

@media (width <= 640px) {
  .workspace__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }
}
</style>
