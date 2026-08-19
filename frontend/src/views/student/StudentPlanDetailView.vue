<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { getStudentPlan, type StudentPlan } from '@/api/training'
import { getPlanLearningProgress, type PlanLearningProgress } from '@/api/learning'
import { ApiError } from '@/api/http'
import { usePermissionStore } from '@/stores/permission'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const plan = ref<StudentPlan>()
const learningProgress = ref<PlanLearningProgress>()
const permissionStore = usePermissionStore()

/** 加载当前登录学员被分配的计划及冻结课程规则。 */
async function load() {
  loading.value = true
  try {
    const planId = String(route.params.id)
    plan.value = await getStudentPlan(planId)
    if (permissionStore.has('student:learning:study') && plan.value.status === 'IN_PROGRESS') {
      learningProgress.value = await getPlanLearningProgress(planId)
    }
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 返回指定计划课程的服务端学习进度。 */
function courseProgress(planCourseId: string) {
  return learningProgress.value?.courses.find((value) => value.planCourseId === planCourseId)
}

/** 进入当前计划课程的视频学习页面。 */
function startStudy(planCourseId: string) {
  return router.push(`/student/plans/${plan.value?.planId}/courses/${planCourseId}/study`)
}

/** 将毫秒有效学时格式化为易读文本。 */
function formatMillis(milliseconds: number) {
  return formatDuration(Math.floor(milliseconds / 1000))
}

/** 将秒数格式化为易读时长。 */
function formatDuration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.ceil((seconds % 3600) / 60)
  return `${hours ? `${hours}小时` : ''}${minutes ? `${minutes}分钟` : '0分钟'}`
}

/** 格式化计划时间。 */
function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

/** 返回培训计划状态中文文案。 */
function statusLabel(status: StudentPlan['status']) {
  return {
    PUBLISHED: '待开始',
    IN_PROGRESS: '进行中',
    FINISHED: '已结束',
    CANCELLED: '已取消',
  }[status]
}

/** 返回培训计划状态标签颜色。 */
function statusType(status: StudentPlan['status']) {
  if (status === 'IN_PROGRESS') return 'success'
  if (status === 'PUBLISHED') return 'warning'
  return 'info'
}

/** 返回学习状态中文文案。 */
function studyStatusLabel(status: string) {
  return (
    (
      { NOT_STARTED: '未开始', IN_PROGRESS: '学习中', COMPLETED: '已完成' } as Record<
        string,
        string
      >
    )[status] || status
  )
}

/** 返回计划完成状态中文文案。 */
function completionStatusLabel(status: string) {
  return (
    ({ NOT_COMPLETED: '未完成', COMPLETED: '已完成' } as Record<string, string>)[status] || status
  )
}

/** 统一展示任务详情错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '培训任务加载失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <header class="detail-header">
      <div>
        <el-button link type="primary" @click="router.push('/student/plans')"
          >← 返回我的任务</el-button
        >
        <h1>{{ plan?.name || '培训任务详情' }}</h1>
        <p>{{ plan?.description || '管理员未填写计划说明' }}</p>
      </div>
      <el-tag v-if="plan" :type="statusType(plan.status)" size="large">
        {{ statusLabel(plan.status) }}
      </el-tag>
    </header>

    <template v-if="plan">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="开始时间">{{
          formatDateTime(plan.startAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{
          formatDateTime(plan.endAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="学习状态">
          {{ studyStatusLabel(plan.studyStatus) }}
        </el-descriptions-item>
        <el-descriptions-item label="完成状态">
          {{ completionStatusLabel(plan.completionStatus) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-card class="course-card" shadow="never">
        <template #header><strong>计划课程与结业规则</strong></template>
        <el-empty v-if="!plan.courses.length" description="暂无课程" />
        <el-collapse v-else>
          <el-collapse-item
            v-for="course in plan.courses"
            :key="course.id"
            :name="course.id"
            :title="course.courseName"
          >
            <div class="course-actions">
              <div v-if="courseProgress(course.id)" class="course-progress">
                <span>
                  有效学时
                  {{ formatMillis(courseProgress(course.id)!.effectiveDurationMillis) }} /
                  {{ formatMillis(courseProgress(course.id)!.requiredDurationMillis) }}
                </span>
                <el-tag
                  :type="courseProgress(course.id)!.status === 'COMPLETED' ? 'success' : 'info'"
                >
                  {{ studyStatusLabel(courseProgress(course.id)!.status) }}
                </el-tag>
              </div>
              <el-button
                v-if="
                  plan.status === 'IN_PROGRESS' && permissionStore.has('student:learning:study')
                "
                type="primary"
                @click="startStudy(course.id)"
              >
                {{ courseProgress(course.id)?.status === 'COMPLETED' ? '回看课程' : '开始学习' }}
              </el-button>
            </div>
            <el-descriptions :column="4" border size="small">
              <el-descriptions-item label="规定学时">
                {{ formatDuration(course.requiredDurationSeconds) }}
              </el-descriptions-item>
              <el-descriptions-item label="允许拖动">
                {{ course.allowSeek ? '是' : '否' }}
              </el-descriptions-item>
              <el-descriptions-item label="上报间隔">
                {{ course.progressReportIntervalSeconds }}秒
              </el-descriptions-item>
              <el-descriptions-item label="学时误差">
                {{ course.studyToleranceSeconds }}秒
              </el-descriptions-item>
            </el-descriptions>
            <el-table :data="course.coursewares" class="courseware-table" size="small">
              <el-table-column label="课件标题" min-width="180" prop="title" />
              <el-table-column label="视频时长" min-width="110">
                <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
              </el-table-column>
              <el-table-column label="顺序" width="80" prop="sortOrder" />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <el-alert
        v-if="learningProgress?.synchronizationPending"
        class="course-card"
        title="学习已经完成，培训任务状态正在同步，请稍后刷新。"
        type="warning"
        :closable="false"
      />
    </template>
  </section>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.detail-header h1 {
  margin: 12px 0 6px;
}

.detail-header p {
  color: #6f7c93;
}

.course-card {
  margin-top: 22px;
}

.courseware-table {
  margin-top: 16px;
}

.course-actions,
.course-progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.course-actions {
  margin-bottom: 16px;
}

.course-progress {
  color: #5f6c85;
}
</style>
