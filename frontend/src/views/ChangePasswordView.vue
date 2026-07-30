<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { validatePassword } from '@/utils/validation'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: validatePassword,
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  submitting.value = true
  try {
    const session = await authStore.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功')
    await router.replace(`/${session.defaultWorkspace}`)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '密码修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="password-page">
    <section class="password-card">
      <h1>修改初始密码</h1>
      <p>为保障账号安全，请先设置新的登录密码。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" show-password type="password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" show-password type="password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" show-password type="password" />
        </el-form-item>
        <el-button :loading="submitting" type="primary" @click="submit">保存并进入系统</el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.password-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background: #eef3f9;
}

.password-card {
  width: min(440px, 100%);
  padding: 36px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 18px 48px rgb(24 46 83 / 12%);
}

h1 {
  margin: 0 0 10px;
}

p {
  margin: 0 0 28px;
  color: #71809a;
}

.el-button {
  width: 100%;
}
</style>
