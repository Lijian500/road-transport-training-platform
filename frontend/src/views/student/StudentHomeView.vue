<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStudentOverview, type StudentOverview } from '@/api/records'
import { useAuthStore } from '@/stores/auth'
import { recordLabel, recordTime } from '@/utils/recordDisplay'

const router = useRouter()
const auth = useAuthStore()
const overview = ref<StudentOverview>()
const loading = ref(false)
const error = ref('')

/** 读取当前学员的待办与培训完成概览。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await getStudentOverview()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '学习中心加载失败'
  } finally {
    loading.value = false
  }
}
/** 跳转到指定类型的本人档案。 */
function openRecords(activity = '') {
  void router.push({ path: '/student/records', query: activity ? { activity } : {} })
}
/** 从既有任务详情进入学习或考试，保留原有准入和计时确认。 */
function openTask(planId: string) {
  void router.push(`/student/plans/${planId}`)
}
onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <header class="page-title">
      <h1>你好，{{ auth.session?.displayName }}</h1>
      <p>完成每一次安全培训，让每一程更安心。</p>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"
      ><el-button link @click="load">重新加载</el-button></el-alert
    >
    <template v-if="overview">
      <div class="home-cards">
        <button @click="openRecords()">
          <span>全部培训</span><strong>{{ overview.totalCount }}</strong
          ><small>查看学习档案 →</small>
        </button>
        <button @click="openRecords('TO_STUDY')">
          <span>待学习</span><strong>{{ overview.toStudyCount }}</strong
          ><small>有效期内的学习任务 →</small>
        </button>
        <button @click="openRecords('TO_EXAM')">
          <span>待考试</span><strong>{{ overview.toExamCount }}</strong
          ><small>未开始或正在进行的考试 →</small>
        </button>
        <button @click="openRecords('COMPLETED')">
          <span>已完成</span><strong>{{ overview.completedCount }}</strong
          ><small>查看培训成果 →</small>
        </button>
      </div>
      <section class="home-tasks">
        <header>
          <h2>继续培训</h2>
          <el-button link type="primary" @click="router.push('/student/plans')"
            >全部培训任务</el-button
          >
        </header>
        <el-empty v-if="!overview.recentTasks.length" description="当前没有待完成的培训任务" />
        <article v-for="task in overview.recentTasks" :key="task.taskId">
          <div>
            <h3>{{ task.planName }}</h3>
            <p>
              截止 {{ recordTime(task.endAt) }} · 学习{{ recordLabel(task.studyStatus) }} · 考试{{
                recordLabel(task.examStatus)
              }}
            </p>
          </div>
          <el-button type="primary" @click="openTask(task.planId)">继续培训</el-button>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped>
.home-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin: 24px 0;
}
.home-cards button {
  display: grid;
  gap: 14px;
  padding: 24px;
  text-align: left;
  color: var(--app-text);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  cursor: pointer;
}
.home-cards button:hover {
  border-color: var(--app-primary);
}
.home-cards strong {
  font-size: 34px;
  color: var(--app-primary);
}
.home-cards small,
p {
  color: var(--app-text-muted);
}
.home-tasks {
  padding: 24px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
}
.home-tasks header,
.home-tasks article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.home-tasks article {
  padding: 16px 0;
  border-top: 1px solid var(--app-border);
}
h2 {
  margin-top: 0;
}
@media (width <= 800px) {
  .home-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (width <= 480px) {
  .home-cards {
    grid-template-columns: 1fr;
  }
  .home-tasks article {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
