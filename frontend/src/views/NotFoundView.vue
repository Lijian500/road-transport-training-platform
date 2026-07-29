<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="not-found">
    <strong>404</strong>
    <h1>页面不存在</h1>
    <RouterLink to="/">返回登录页</RouterLink>
  </main>
</template>

<style scoped>
.not-found {
  display: grid;
  min-height: 100vh;
  place-content: center;
  text-align: center;
}

.not-found strong {
  color: #155eef;
  font-size: 72px;
}

.not-found h1 {
  margin: 8px 0 24px;
}

.not-found a {
  color: #155eef;
}
</style>
