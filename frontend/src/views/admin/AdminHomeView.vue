<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p>OVERVIEW</p>
        <h1>管理工作台</h1>
        <span>欢迎回来，{{ authStore.session?.displayName }}</span>
      </div>
    </header>
    <div class="overview-grid">
      <article>
        <span>当前身份</span>
        <strong>{{ authStore.session?.platformAdmin ? '平台超级管理员' : '企业用户' }}</strong>
      </article>
      <article>
        <span>所属企业</span>
        <strong>{{ authStore.session?.enterpriseName || '平台管理中心' }}</strong>
      </article>
      <article>
        <span>已授予权限</span>
        <strong>{{ authStore.permissions.includes('*') ? '全部' : authStore.permissions.length }}</strong>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page-header p {
  margin: 0 0 8px;
  color: #155eef;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

h1 {
  margin: 0 0 8px;
}

.page-header span,
article span {
  color: #71809a;
}

.overview-grid {
  display: grid;
  margin-top: 28px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

article {
  display: grid;
  gap: 12px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 14px;
}

article strong {
  font-size: 22px;
}

@media (width <= 760px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
