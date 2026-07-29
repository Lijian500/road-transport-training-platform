<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="workspace">
    <header class="workspace__header">
      <div>
        <span>道路运输在线培训</span>
        <strong>学员学习中心</strong>
      </div>
      <nav aria-label="学员端导航">
        <RouterLink to="/student">学习首页</RouterLink>
        <RouterLink to="/admin">管理工作台</RouterLink>
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
