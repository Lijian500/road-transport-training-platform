<script setup lang="ts">
import { reactive } from 'vue'
import { RouterLink } from 'vue-router'

const form = reactive({
  username: '',
  password: '',
})
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
        <p>当前为项目骨架，认证接口将在业务实施阶段接入。</p>
      </header>
      <el-form :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>
        <el-button disabled type="primary">登录功能待接入</el-button>
      </el-form>
      <div class="login-card__preview">
        <span>界面预览</span>
        <RouterLink to="/admin">管理端</RouterLink>
        <RouterLink to="/student">学员端</RouterLink>
      </div>
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
  border: 1px solid rgb(255 255 255 / 65%);
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
  line-height: 1.6;
}

.login-card .el-button {
  width: 100%;
}

.login-card__preview {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid #edf0f5;
  font-size: 13px;
}

.login-card__preview span {
  margin-right: auto;
  color: #8591a6;
}

.login-card__preview a {
  color: #155eef;
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
