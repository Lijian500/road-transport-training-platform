<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { getStatisticsOverview, type StatisticsOverview } from '@/api/statistics'
import { recordDuration } from '@/utils/recordDisplay'

const authStore = useAuthStore()
const permission = usePermissionStore()
const router = useRouter()
const overview = ref<StatisticsOverview>()
const loading = ref(false)
const error = ref('')
const entries = computed(() =>
  (authStore.session?.platformAdmin
    ? [
        { path: '/admin/enterprises', label: '组织管理', permission: 'admin:enterprise:view' },
        { path: '/admin/addresses', label: '行政区域', permission: 'admin:address:view' },
      ]
    : [
        { path: '/admin/users', label: '人员管理', permission: 'admin:user:view' },
        { path: '/admin/vehicles', label: '车辆管理', permission: 'admin:vehicle:view' },
        { path: '/admin/courses', label: '课程管理', permission: 'admin:course:view' },
        { path: '/admin/plans', label: '培训计划', permission: 'admin:plan:view' },
        { path: '/admin/exam', label: '考试管理', permission: 'admin:exam:view' },
        {
          path: '/admin/statistics',
          label: '培训统计与学时监管',
          permission: 'admin:statistics:view',
        },
      ]
  ).filter((entry) => permission.has(entry.permission)),
)

/** 企业统计只向具有统计权限的企业账号加载。 */
async function loadOverview() {
  if (authStore.session?.platformAdmin || !permission.has('admin:statistics:view')) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await getStatisticsOverview()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '培训概览加载失败'
  } finally {
    loading.value = false
  }
}
/** 打开已授权的业务入口。 */
function openEntry(path: string) {
  void router.push(path)
}
onMounted(loadOverview)
</script>

<template>
  <section v-loading="loading">

    <div class="overview-grid">
      <article>
        <span>当前身份</span>
        <strong>{{ authStore.session?.platformAdmin ? '平台超级管理员' : '组织用户' }}</strong>
      </article>
      <article>
        <span>所属组织</span>
        <strong>{{ authStore.session?.enterpriseName || '平台管理中心' }}</strong>
      </article>
      <article>
        <span>已授予权限</span>
        <strong>{{
          authStore.permissions.includes('*') ? '全部' : authStore.permissions.length
        }}</strong>
      </article>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="overview" class="overview-grid">
      <article>
        <span>培训计划</span><strong>{{ overview.planCount }}</strong>
      </article>
      <article>
        <span>参训人次 / 已完成人次</span
        ><strong>{{ overview.participantCount }} / {{ overview.completedCount }}</strong>
      </article>
      <article>
        <span>有效学时</span
        ><strong class="duration">{{ recordDuration(overview.effectiveDurationMillis) }}</strong>
      </article>
      <article class="completion">
        <span>培训完成率</span
        ><el-progress :percentage="overview.completionRate" :stroke-width="12" />
      </article>
    </div>
    <section class="entry-panel">
      <h2>业务入口</h2>
      <div class="entries">
        <el-button v-for="entry in entries" :key="entry.path" @click="openEntry(entry.path)">{{
          entry.label
        }}</el-button>
        <el-empty v-if="!entries.length" description="当前账号暂无其他管理权限" :image-size="55" />
      </div>
    </section>
  </section>
</template>

<style scoped>
.entry-panel {
  margin-top: 28px;
  padding: 24px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 14px;
}
.entries {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.entries .el-button {
  margin: 0;
}
.completion {
  grid-column: 1 / -1;
}
article strong.duration {
  font-size: 18px;
}
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
