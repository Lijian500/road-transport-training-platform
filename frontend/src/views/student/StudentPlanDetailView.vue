<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { getStudentPlan, type StudentPlan } from '@/api/training'
import { getPlanLearningProgress, type PlanLearningProgress } from '@/api/learning'
import { ApiError } from '@/api/http'
import { formatTrainingDate, formatLearningDuration, learningPercentage } from '@/utils/trainingDisplay'
import { usePermissionStore } from '@/stores/permission'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const plan = ref<StudentPlan>()
const learningProgress = ref<PlanLearningProgress>()
const permissionStore = usePermissionStore()

const canEnterExam = computed(() => {
  if (!plan.value?.examRequired || !permissionStore.has('student:exam:take')) return false
  if (['PASSED', 'FAILED'].includes(plan.value.examStatus)) return true
  return plan.value.studyStatus === 'COMPLETED' &&
    (plan.value.status === 'IN_PROGRESS' || plan.value.examStatus === 'IN_PROGRESS')
})

/** 按培训周期开放学习；提前结业的学员仍可回看课程。 */
function canStudy() {
  const value = plan.value
  if (!value || !permissionStore.has('student:learning:study')) return false
  const now = Date.now()
  return now >= new Date(value.startAt).getTime() && now < new Date(value.endAt).getTime() &&
    (value.status === 'IN_PROGRESS' ||
      (value.status === 'FINISHED' && value.completionStatus === 'COMPLETED'))
}

/** 加载当前登录学员被分配的计划及冻结课程规则。 */
async function load() {
  loading.value = true
  try {
    const planId = String(route.params.id)
    plan.value = await getStudentPlan(planId)
    if (permissionStore.has('student:learning:study')) {
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

/** 返回视频累计确认的最远位置，补学回放时不倒退已完成进度。 */
function coursewareProgress(planCourseId: string, snapshotId: string) {
  return courseProgress(planCourseId)?.coursewares.find((item) => item.coursewareSnapshotId === snapshotId)
}

/** 进入当前计划课程的视频学习页面。 */
function startStudy(planCourseId: string) {
  return router.push(`/student/plans/${plan.value?.planId}/courses/${planCourseId}/study`)
}

/** 确认首次考试会开始计时，并进入考试或查看已有结果。 */
async function enterExam() {
  if (!plan.value || !canEnterExam.value) return
  if (plan.value.examStatus === 'NOT_STARTED') {
    try {
      await ElMessageBox.confirm(
        `考试限时 ${plan.value.examDurationMinutes ?? '-'} 分钟，进入后立即开始计时，确定开始吗？`,
        '开始考试',
        {
          type: 'warning',
          confirmButtonText: '开始考试',
          cancelButtonText: '暂不开始',
        },
      )
    } catch {
      return
    }
  }
  await router.push(`/student/plans/${plan.value.planId}/exam`)
}

/** 将毫秒有效学时格式化为易读文本。 */
function formatMillis(milliseconds: number) {
  return formatLearningDuration(milliseconds)
}

/** 将秒数格式化为易读时长。 */
function formatDuration(seconds: number) {
  return formatLearningDuration(seconds * 1000)
}

/** 格式化计划时间。 */
function formatDateTime(value: string) {
  return formatTrainingDate(value)
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

/** 返回考试状态中文文案。 */
function examStatusLabel(status: string) {
  return (
    (
      {
        NOT_REQUIRED: '无需考试',
        NOT_STARTED: '未开始',
        IN_PROGRESS: '考试中',
        PASSED: '已通过',
        FAILED: '未通过',
      } as Record<string, string>
    )[status] || status
  )
}

/** 返回考试状态标签颜色。 */
function examStatusType(status: string) {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'IN_PROGRESS') return 'warning'
  return 'info'
}

/** 返回当前考试入口按钮文案。 */
function examActionLabel(status: string) {
  if (status === 'IN_PROGRESS') return '继续考试'
  if (status === 'PASSED' || status === 'FAILED') return '查看考试结果'
  return '开始考试'
}

/** 统一展示任务详情错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '培训任务加载失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <header class="page-toolbar">
      <div>
        <h1>{{ plan?.name || '培训任务详情' }}</h1>

      </div>
      <el-tag v-if="plan" :type="plan.completionStatus === 'COMPLETED' ? 'success' : statusType(plan.status)" size="large">
        {{ plan.completionStatus === 'COMPLETED' ? '已完成' : statusLabel(plan.status) }}
      </el-tag>
    </header>

    <template v-if="plan">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="计划说明" :span="2">{{ plan.description || '无' }}</el-descriptions-item>
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

      <el-card v-if="plan.examRequired" class="course-card exam-card" shadow="never">
        <template #header>
          <div class="exam-card-header">
            <strong>计划考试</strong>
            <el-tag :type="examStatusType(plan.examStatus)">
              {{ examStatusLabel(plan.examStatus) }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="考试时长">
            {{ plan.examDurationMinutes ?? '-' }}分钟
          </el-descriptions-item>
          <el-descriptions-item label="及格分数">
            {{ plan.examPassScore ?? '-' }}分
          </el-descriptions-item>
        </el-descriptions>
        <div class="exam-actions">
          <span v-if="!canEnterExam && plan.studyStatus !== 'COMPLETED'">学习完成后才能参加考试。</span>
          <span v-else-if="!canEnterExam && plan.status !== 'IN_PROGRESS'">
            {{ plan.status === 'PUBLISHED' ? '计划开始后可参加考试' : '当前计划不可新开考试' }}
          </span>
          <span v-else>同一培训计划只有一次考试记录，系统会自动保存答题进度。</span>
          <el-button
            v-if="permissionStore.has('student:exam:take')"
            type="primary"
            :disabled="!canEnterExam"
            @click="enterExam"
          >
            {{ examActionLabel(plan.examStatus) }}
          </el-button>
        </div>
      </el-card>

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
                  课程总进度（有效学时）
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
                v-if="canStudy()"
                type="primary"
                @click="startStudy(course.id)"
              >
                {{ courseProgress(course.id)?.status === 'COMPLETED' ? '回看课程' : '开始学习' }}
              </el-button>
            </div>
            <el-progress
              v-if="courseProgress(course.id)"
              class="course-total-progress"
              :percentage="learningPercentage(courseProgress(course.id)!.effectiveDurationMillis, courseProgress(course.id)!.requiredDurationMillis)"
            />
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
              <el-table-column label="完成情况" min-width="260">
                <template #default="{ row }">
                  <template v-if="coursewareProgress(course.id, row.id)">
                    <span>
                      {{ studyStatusLabel(coursewareProgress(course.id, row.id)!.status) }} ·
                      {{ formatMillis(coursewareProgress(course.id, row.id)!.maxConfirmedPositionMillis) }} /
                      {{ formatMillis(row.durationSeconds * 1000) }}
                    </span>
                    <el-progress :percentage="learningPercentage(coursewareProgress(course.id, row.id)!.maxConfirmedPositionMillis, row.durationSeconds * 1000)" />
                  </template>
                  <span v-else>暂无进度数据</span>
                </template>
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

.course-total-progress {
  margin-bottom: 16px;
}

.courseware-table {
  margin-top: 16px;
}

.course-actions,
.course-progress,
.exam-card-header,
.exam-actions {
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

.exam-actions {
  margin-top: 16px;
  color: #6f7c93;
}

@media (max-width: 640px) {
  .exam-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
