<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="result-page">
    <section>
      <strong>403</strong>
      <h1>无权访问该页面</h1>
      <p>当前账号没有所需权限，请联系企业管理员。</p>
      <el-button type="primary" @click="router.back()">返回上一页</el-button>
    </section>
  </main>
</template>

<style scoped>
.result-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  text-align: center;
}

strong {
  color: #155eef;
  font-size: 72px;
}

h1 {
  margin: 8px 0;
}

p {
  margin: 0 0 24px;
  color: #71809a;
}
</style>
