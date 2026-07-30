<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
})
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  try {
    const session = await authStore.login(form)
    if (session.mustChangePassword) {
      await router.replace('/change-password')
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
    await router.replace(redirect || `/${session.defaultWorkspace}`)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '登录失败，请稍后重试')
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <p>ROAD TRANSPORT SAFETY</p>
      <h1>道路运输企业在线安全培训与学时监管系统</h1>
      <span>统一入口 · 管理工作台 · 学员学习中心</span>
    </section>

    <section class="login-card">
      <header>
        <h2>登录系统</h2>
        <p>请输入平台分配的账号和密码。</p>
      </header>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>
        <el-button :loading="authStore.loading" type="primary" @click="submit">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  padding: clamp(24px, 6vw, 80px);
  grid-template-columns: minmax(0, 1fr) minmax(320px, 440px);
  gap: clamp(40px, 8vw, 120px);
  align-items: center;
  color: #fff;
  background:
    radial-gradient(circle at 20% 25%, rgb(69 135 255 / 38%), transparent 34%),
    linear-gradient(135deg, #0d1e3a, #163d75);
}

.login-intro p {
  margin: 0 0 18px;
  color: #86b1ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.login-intro h1 {
  max-width: 720px;
  margin: 0 0 20px;
  font-size: clamp(34px, 5vw, 62px);
  line-height: 1.14;
}

.login-intro span {
  color: #b6c7e2;
}

.login-card {
  padding: 36px;
  color: #172033;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 28px 70px rgb(0 0 0 / 24%);
}

.login-card header {
  margin-bottom: 28px;
}

.login-card h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.login-card header p {
  margin: 0;
  color: #71809a;
  font-size: 14px;
}

.login-card .el-button {
  width: 100%;
}

@media (width <= 820px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-intro h1 {
    font-size: clamp(30px, 9vw, 48px);
  }
}
</style>
