<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="workspace">
    <aside class="workspace__sidebar">
      <div class="workspace__brand">
        <span>道路运输在线培训</span>
        <strong>管理工作台</strong>
      </div>
      <nav aria-label="管理端导航">
        <RouterLink to="/admin">工作台首页</RouterLink>
      </nav>
      <RouterLink class="workspace__switch" to="/student">切换到学员端</RouterLink>
    </aside>
    <main class="workspace__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.workspace {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 240px 1fr;
}

.workspace__sidebar {
  display: flex;
  flex-direction: column;
  gap: 32px;
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

nav a,
.workspace__switch {
  display: block;
  padding: 11px 12px;
  border-radius: 8px;
}

nav a.router-link-active {
  background: rgb(255 255 255 / 12%);
}

.workspace__switch {
  margin-top: auto;
  color: #a9b9d3;
  font-size: 13px;
}

.workspace__content {
  padding: clamp(24px, 5vw, 64px);
}

@media (width <= 720px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .workspace__sidebar {
    min-height: auto;
  }
}
</style>
