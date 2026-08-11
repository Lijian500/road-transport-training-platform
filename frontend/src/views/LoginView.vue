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
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 校验账号密码并进入当前用户的默认工作台。 */
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

/** 提示尚未接入后端的辅助登录能力。 */
function showPendingFeature(feature: string) {
  ElMessage.info(`${feature}功能暂未开放`)
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-label="平台介绍">
      <svg class="star-background" aria-hidden="true" width="100%" height="100%">
        <defs>
          <pattern
            id="login-stars"
            x="0"
            y="0"
            width="40"
            height="40"
            patternUnits="userSpaceOnUse"
          >
            <circle cx="5" cy="8" r="0.8" fill="#fff" opacity="0.12" />
            <circle cx="22" cy="3" r="0.5" fill="#fff" opacity="0.08" />
            <circle cx="35" cy="18" r="0.6" fill="#fff" opacity="0.1" />
            <circle cx="14" cy="30" r="0.7" fill="#fff" opacity="0.07" />
            <circle cx="28" cy="35" r="0.5" fill="#fff" opacity="0.09" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#login-stars)" />
        <circle cx="50%" cy="110%" r="45%" fill="none" stroke="#3b82f6" opacity="0.08" />
        <circle cx="50%" cy="110%" r="55%" fill="none" stroke="#f59e0b" opacity="0.05" />
      </svg>

      <div class="brand-content">
        <div class="brand-row">
          <span class="brand-icon" aria-hidden="true">🚖</span>
          <span class="brand-name">交安云培训</span>
          <span class="industry-tag">交通行业</span>
        </div>
        <h1>
          专业的交通行业<br />
          <em>在线安全培训</em>平台
        </h1>
        <p>面向出租车、网约车、公交、货运等从业人员，提供合规的安全教育与持证培训课程。</p>
      </div>

      <div class="scene-area" aria-hidden="true">
        <svg class="road-scene" viewBox="0 0 500 220">
          <defs>
            <filter id="login-lamp-glow" x="-100%" y="-100%" width="300%" height="300%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <radialGradient id="login-lamp-beam" cx="50%" cy="0%" r="100%">
              <stop offset="0%" stop-color="#fef08a" stop-opacity="0.25" />
              <stop offset="100%" stop-color="#fef08a" stop-opacity="0" />
            </radialGradient>
            <filter id="login-green-glow">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <clipPath id="login-scene-clip">
              <rect x="-10" y="-10" width="520" height="240" />
            </clipPath>
          </defs>

          <rect x="0" y="150" width="500" height="70" rx="4" fill="#1e293b" />
          <g fill="#f59e0b" opacity="0.7">
            <rect x="10" y="182" width="50" height="6" rx="3" />
            <rect x="90" y="182" width="50" height="6" rx="3" />
            <rect x="170" y="182" width="50" height="6" rx="3" />
            <rect x="250" y="182" width="50" height="6" rx="3" />
            <rect x="330" y="182" width="50" height="6" rx="3" />
            <rect x="410" y="182" width="50" height="6" rx="3" />
          </g>
          <rect x="0" y="145" width="500" height="8" fill="#334155" />

          <g>
            <rect x="10" y="60" width="50" height="88" rx="3" fill="#1e3a5f" />
            <g fill="#60a5fa">
              <rect x="22" y="72" width="10" height="12" rx="1" opacity="0.7" />
              <rect x="36" y="72" width="10" height="12" rx="1" opacity="0.5" />
              <rect x="22" y="90" width="10" height="12" rx="1" opacity="0.8" />
              <rect x="36" y="90" width="10" height="12" rx="1" opacity="0.3" />
              <rect x="22" y="108" width="10" height="12" rx="1" opacity="0.6" />
              <rect x="36" y="108" width="10" height="12" rx="1" opacity="0.4" />
            </g>

            <rect x="70" y="90" width="60" height="58" rx="3" fill="#0f2744" />
            <g fill="#93c5fd">
              <rect x="80" y="100" width="10" height="10" rx="1" opacity="0.6" />
              <rect x="95" y="100" width="10" height="10" rx="1" opacity="0.8" />
              <rect x="110" y="100" width="10" height="10" rx="1" opacity="0.4" />
              <rect x="80" y="116" width="10" height="10" rx="1" opacity="0.5" />
              <rect x="95" y="116" width="10" height="10" rx="1" opacity="0.7" />
              <rect x="110" y="116" width="10" height="10" rx="1" opacity="0.3" />
            </g>

            <rect x="340" y="50" width="70" height="98" rx="3" fill="#1e3a5f" />
            <g fill="#fbbf24">
              <rect x="352" y="62" width="12" height="12" rx="1" opacity="0.6" />
              <rect x="370" y="62" width="12" height="12" rx="1" opacity="0.4" />
              <rect x="388" y="62" width="12" height="12" rx="1" opacity="0.7" />
            </g>
            <g fill="#60a5fa">
              <rect x="352" y="82" width="12" height="12" rx="1" opacity="0.5" />
              <rect x="370" y="82" width="12" height="12" rx="1" opacity="0.8" />
              <rect x="388" y="82" width="12" height="12" rx="1" opacity="0.3" />
              <rect x="352" y="102" width="12" height="12" rx="1" opacity="0.6" />
              <rect x="370" y="102" width="12" height="12" rx="1" opacity="0.4" />
              <rect x="388" y="102" width="12" height="12" rx="1" opacity="0.7" />
            </g>

            <rect x="420" y="80" width="55" height="68" rx="3" fill="#0f2744" />
            <rect x="430" y="90" width="10" height="10" rx="1" fill="#93c5fd" opacity="0.7" />
            <rect x="448" y="90" width="10" height="10" rx="1" fill="#93c5fd" opacity="0.4" />
            <rect x="430" y="108" width="10" height="10" rx="1" fill="#fbbf24" opacity="0.5" />
            <rect x="448" y="108" width="10" height="10" rx="1" fill="#fbbf24" opacity="0.8" />
          </g>

          <g>
            <rect x="300" y="90" width="8" height="55" rx="2" fill="#475569" />
            <rect x="296" y="86" width="16" height="42" rx="3" fill="#1e293b" />
            <circle cx="304" cy="95" r="5" fill="#374151" />
            <circle cx="304" cy="107" r="5" fill="#374151" />
            <circle cx="304" cy="119" r="6" fill="#22c55e" filter="url(#login-green-glow)" />
          </g>

          <g>
            <line x1="150" y1="80" x2="150" y2="148" stroke="#475569" stroke-width="3" />
            <path d="M150 80 Q160 70 172 72" fill="none" stroke="#475569" stroke-width="3" />
            <ellipse cx="172" cy="80" rx="28" ry="40" fill="url(#login-lamp-beam)" />
            <circle
              cx="172"
              cy="72"
              r="6"
              fill="#fef08a"
              filter="url(#login-lamp-glow)"
              opacity="0.95"
            />
            <circle cx="172" cy="72" r="3" fill="#fff" opacity="0.9" />
          </g>

          <g clip-path="url(#login-scene-clip)">
            <g class="taxi">
              <ellipse
                cx="88"
                cy="28"
                rx="22"
                ry="6"
                fill="#fef08a"
                opacity="0.18"
                transform="rotate(-5, 88, 28)"
              />
              <rect x="0" y="14" width="82" height="28" rx="5" fill="#f59e0b" />
              <path d="M12 14 L20 2 L60 2 L70 14 Z" fill="#fbbf24" />
              <rect x="18" y="4" width="17" height="10" rx="2" fill="#bae6fd" opacity="0.9" />
              <rect x="39" y="4" width="17" height="10" rx="2" fill="#bae6fd" opacity="0.85" />
              <rect x="31" y="-4" width="18" height="7" rx="2" fill="#1a1a2e" />
              <text x="33" y="1" fill="#f59e0b" font-size="5" font-weight="700">TAXI</text>
              <line x1="36" y1="14" x2="36" y2="42" stroke="#d97706" opacity="0.5" />
              <circle cx="18" cy="42" r="8" fill="#1e293b" />
              <circle cx="18" cy="42" r="4" fill="#475569" />
              <circle cx="64" cy="42" r="8" fill="#1e293b" />
              <circle cx="64" cy="42" r="4" fill="#475569" />
              <rect x="74" y="22" width="8" height="7" rx="2" fill="#fef08a" opacity="0.95" />
              <rect x="0" y="24" width="5" height="6" rx="1" fill="#ef4444" opacity="0.8" />
            </g>
          </g>
        </svg>

        <div class="scene-ground">
          <dl class="platform-stats">
            <div>
              <dt>50万+</dt>
              <dd>从业人员</dd>
            </div>
            <div>
              <dt>200+</dt>
              <dd>培训课程</dd>
            </div>
            <div>
              <dt>96%</dt>
              <dd>通过率</dd>
            </div>
          </dl>
        </div>
      </div>
    </section>

    <section class="form-panel" aria-label="账号登录">
      <div class="login-box">
        <div class="compact-brand">
          <span class="compact-brand-icon" aria-hidden="true">🚖</span>
          <span>交安云培训</span>
        </div>

        <header class="form-heading">
          <h2>欢迎回来</h2>
        </header>

        <el-form
          ref="formRef"
          class="login-form"
          :model="form"
          :rules="rules"
          hide-required-asterisk
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item class="login-field" label="账号" prop="username">
            <el-input
              v-model.trim="form.username"
              autocomplete="username"
              placeholder="手机号 / 从业证件号"
            />
          </el-form-item>
          <el-form-item class="login-field password-field" label="密码" prop="password">
            <el-input
              v-model="form.password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              type="password"
            />
          </el-form-item>

          <div class="forgot-row">
            <button type="button" @click="showPendingFeature('找回密码')">忘记密码</button>
          </div>

          <button class="submit-button" type="submit" :disabled="authStore.loading">
            <span v-if="authStore.loading" class="loading-spinner" aria-hidden="true" />
            {{ authStore.loading ? '登录中...' : '登 录' }}
          </button>

          <button
            class="secondary-button"
            type="button"
            @click="showPendingFeature('短信验证码登录')"
          >
            短信验证码登录
          </button>
        </el-form>

        <p class="register-row">
          还没有账号？
          <button type="button" @click="showPendingFeature('注册')">立即注册</button>
        </p>

        <div class="security-note">
          <svg
            aria-hidden="true"
            width="12"
            height="12"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect x="3" y="11" width="18" height="11" rx="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
          <span>数据加密传输 · 接入交通运输部平台</span>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: flex;
  min-width: 320px;
  min-height: 100vh;
  color: #0f172a;
  background: #fff;
}

.brand-panel {
  position: relative;
  display: flex;
  overflow: hidden;
  width: 52%;
  min-height: 100vh;
  flex-direction: column;
  background: linear-gradient(155deg, #0f172a 0%, #1e3a5f 55%, #0f2744 100%);
}

.star-background {
  position: absolute;
  z-index: 0;
  inset: 0;
  pointer-events: none;
}

.brand-content {
  position: relative;
  z-index: 2;
  padding: 36px 44px 0;
}

.brand-row,
.compact-brand {
  display: flex;
  align-items: center;
}

.brand-row {
  gap: 10px;
  margin-bottom: 32px;
}

.brand-icon,
.compact-brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: #f59e0b;
}

.brand-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgb(245 158 11 / 40%);
  font-size: 20px;
}

.brand-name,
.compact-brand,
.brand-content h1,
.form-heading h2,
.platform-stats dt {
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
}

.brand-name {
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.industry-tag {
  margin-left: 4px;
  padding: 2px 10px;
  color: #f59e0b;
  border: 1px solid rgb(245 158 11 / 30%);
  border-radius: 100px;
  background: rgb(245 158 11 / 15%);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand-content h1 {
  margin: 0 0 12px;
  color: #fff;
  font-size: clamp(27px, 2.35vw, 34px);
  font-style: normal;
  font-weight: 700;
  line-height: 1.28;
}

.brand-content h1 em {
  color: #f59e0b;
  font-style: normal;
}

.brand-content > p {
  max-width: 360px;
  margin: 0;
  color: rgb(255 255 255 / 50%);
  font-size: 14px;
  line-height: 1.7;
}

.scene-area {
  position: absolute;
  z-index: 1;
  right: 0;
  bottom: 0;
  left: 0;
}

.road-scene {
  position: absolute;
  bottom: 100px;
  left: 50%;
  overflow: visible;
  width: 90%;
  opacity: 0.9;
  transform: translateX(-50%);
}

.taxi {
  animation: taxi-drive 10.5s linear infinite;
}

.scene-ground {
  position: relative;
  height: 80px;
  background: #0f172a;
}

.platform-stats {
  position: absolute;
  top: 18px;
  left: 50%;
  display: flex;
  overflow: hidden;
  width: 84%;
  margin: 0;
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: 14px;
  background: rgb(255 255 255 / 5%);
  transform: translateX(-50%);
}

.platform-stats > div {
  flex: 1;
  padding: 12px 8px;
  text-align: center;
}

.platform-stats > div:not(:last-child) {
  border-right: 1px solid rgb(255 255 255 / 7%);
}

.platform-stats dt {
  color: #f59e0b;
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
}

.platform-stats dd {
  margin: 4px 0 0;
  color: rgb(255 255 255 / 40%);
  font-size: 11px;
}

.form-panel {
  display: flex;
  width: 48%;
  min-height: 100vh;
  padding: 48px 6%;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.login-box {
  width: 100%;
  max-width: 360px;
}

.compact-brand {
  gap: 8px;
  margin-bottom: 48px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.compact-brand-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  font-size: 16px;
}

.form-heading {
  margin-bottom: 40px;
}

.form-heading h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  line-height: 1.25;
}

.login-field {
  margin-bottom: 20px;
}

.password-field {
  margin-bottom: 10px;
}

.login-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 6px;
  padding: 0;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  line-height: 1.4;
}

.login-form :deep(.el-input__wrapper) {
  padding: 0 0 10px;
  border-radius: 0;
  background: transparent;
  box-shadow: 0 -1.5px 0 0 #e2e8f0 inset;
  transition: box-shadow 0.2s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 -1.5px 0 0 #cbd5e1 inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 -1.5px 0 0 #f59e0b inset;
}

.login-form :deep(.el-input__inner) {
  height: 30px;
  color: #0f172a;
  font-size: 15px;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #a8b3c2;
}

.login-form :deep(.el-input__password) {
  color: #cbd5e1;
}

.login-form :deep(.el-form-item__error) {
  padding-top: 3px;
  font-size: 11px;
}

.forgot-row {
  margin-bottom: 36px;
  text-align: right;
}

.forgot-row button,
.register-row button {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.forgot-row button {
  color: #94a3b8;
  font-size: 12px;
}

.forgot-row button:hover,
.forgot-row button:focus-visible {
  color: #f59e0b;
}

.submit-button,
.secondary-button {
  width: 100%;
  min-height: 46px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease;
}

.submit-button {
  display: flex;
  margin-bottom: 20px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  border: 1px solid #0f172a;
  background: #0f172a;
}

.submit-button:hover:not(:disabled) {
  border-color: #1e3a5f;
  background: #1e3a5f;
  transform: translateY(-1px);
}

.submit-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgb(255 255 255 / 35%);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spinner-rotate 0.8s linear infinite;
}

.secondary-button {
  color: #64748b;
  border: 1px solid #e2e8f0;
  background: transparent;
}

.secondary-button:hover,
.secondary-button:focus-visible {
  color: #0f172a;
  border-color: #f59e0b;
}

.register-row {
  margin: 36px 0 0;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}

.register-row button {
  color: #f59e0b;
  font-weight: 600;
}

.register-row button:hover,
.register-row button:focus-visible {
  color: #d97706;
}

.security-note {
  display: flex;
  margin-top: 48px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: #cbd5e1;
  font-size: 11px;
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.submit-button:focus-visible,
.secondary-button:focus-visible,
.forgot-row button:focus-visible,
.register-row button:focus-visible {
  outline: 2px solid #f59e0b;
  outline-offset: 3px;
}

@keyframes taxi-drive {
  from {
    transform: translate(-100px, 110px);
  }

  to {
    transform: translate(560px, 110px);
  }
}

@keyframes spinner-rotate {
  to {
    transform: rotate(360deg);
  }
}

@media (width <= 960px) {
  .brand-panel {
    width: 46%;
  }

  .form-panel {
    width: 54%;
    padding-right: 7%;
    padding-left: 7%;
  }

  .brand-content {
    padding-right: 32px;
    padding-left: 32px;
  }

  .industry-tag {
    display: none;
  }
}

@media (width <= 720px) {
  .login-page {
    min-height: 100vh;
    flex-direction: column;
  }

  .brand-panel {
    width: 100%;
    min-height: 310px;
  }

  .brand-content {
    padding: 24px 24px 0;
  }

  .brand-row {
    margin-bottom: 20px;
  }

  .brand-content h1 {
    font-size: 27px;
  }

  .brand-content > p {
    max-width: 440px;
    padding-right: 18%;
    font-size: 13px;
  }

  .road-scene {
    bottom: 30px;
    left: auto;
    right: -60px;
    width: 330px;
    transform: none;
  }

  .scene-ground {
    height: 28px;
  }

  .platform-stats {
    display: none;
  }

  .form-panel {
    width: 100%;
    min-height: auto;
    padding: 42px 24px 38px;
  }

  .compact-brand {
    display: none;
  }

  .form-heading {
    margin-bottom: 34px;
  }

  .security-note {
    margin-top: 38px;
  }
}

@media (width <= 420px) {
  .brand-panel {
    min-height: 290px;
  }

  .brand-content h1 {
    font-size: 24px;
  }

  .brand-content > p {
    max-width: 285px;
    padding-right: 0;
  }

  .road-scene {
    right: -105px;
    width: 300px;
    opacity: 0.72;
  }

  .security-note {
    white-space: normal;
    text-align: center;
  }
}

@media (height <= 720px) and (width > 720px) {
  .brand-content {
    padding-top: 24px;
  }

  .brand-row {
    margin-bottom: 20px;
  }

  .compact-brand {
    margin-bottom: 30px;
  }

  .form-heading {
    margin-bottom: 30px;
  }

  .register-row {
    margin-top: 24px;
  }

  .security-note {
    margin-top: 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .taxi,
  .loading-spinner {
    animation: none;
  }

  .taxi {
    transform: translate(190px, 110px);
  }
}
</style>
