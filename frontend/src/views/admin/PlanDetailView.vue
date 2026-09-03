<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import {
  cancelPlan,
  getPlan,
  getPlanCourseCandidates,
  getPlanParticipantCandidates,
  publishPlan,
  updatePlan,
  type Plan,
  type PlanCourseOption,
  type PlanParticipantOption,
  type PlanStatus,
} from '@/api/training'
import { ApiError } from '@/api/http'
import { getEnabledExamPaperOptions, type PaperOption } from '@/api/exam'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import { usePermissionStore } from '@/stores/permission'

const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()
const planId = String(route.params.id)
const loading = ref(false)
const saving = ref(false)
const plan = ref<Plan>()
const courseOptions = ref<PlanCourseOption[]>([])
const participantOptions = ref<PlanParticipantOption[]>([])
const paperOptions = ref<PaperOption[]>([])
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  description: '',
  startAt: '',
  endAt: '',
  examRequired: false,
  examPaperId: '',
  examPassScore: 60,
  faceCheckEnabled: false,
  faceCheckMinIntervalSeconds: 300,
  faceCheckMaxIntervalSeconds: 600,
  faceCheckTimeoutSeconds: 60,
  faceCheckMaxAttempts: 3,
  courseIds: [] as string[],
  userIds: [] as string[],
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endAt: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}
const editable = computed(
  () => plan.value?.status === 'DRAFT' && permissionStore.has('admin:plan:update'),
)
const cancellable = computed(
  () => plan.value?.status === 'PUBLISHED' && new Date(plan.value.startAt).getTime() > Date.now(),
)
const selectedPaper = computed(() =>
  paperOptions.value.find((item) => item.id === form.examPaperId),
)

/** 加载计划详情，并为草稿准备独立的课程和学员候选项。 */
async function load() {
  loading.value = true
  try {
    const result = await getPlan(planId)
    plan.value = result
    syncForm(result)
    if (result.status === 'DRAFT' && permissionStore.has('admin:plan:update')) {
      const [courses, participants, papers] = await Promise.all([
        getPlanCourseCandidates(),
        getPlanParticipantCandidates(),
        getEnabledExamPaperOptions(),
      ])
      courseOptions.value = mergeCourseOptions(courses, result)
      participantOptions.value = mergeParticipantOptions(participants, result)
      paperOptions.value = papers
    }
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 将服务端计划内容同步到编辑表单。 */
function syncForm(value: Plan) {
  Object.assign(form, {
    name: value.name,
    description: value.description || '',
    startAt: value.startAt,
    endAt: value.endAt,
    examRequired: value.examRequired,
    examPaperId: value.examPaperId || '',
    examPassScore: value.examPassScore || 60,
    faceCheckEnabled: value.faceCheckEnabled,
    faceCheckMinIntervalSeconds: value.faceCheckMinIntervalSeconds,
    faceCheckMaxIntervalSeconds: value.faceCheckMaxIntervalSeconds,
    faceCheckTimeoutSeconds: value.faceCheckTimeoutSeconds,
    faceCheckMaxAttempts: value.faceCheckMaxAttempts,
    courseIds: value.courses.map((item) => item.courseId),
    userIds: value.users.map((item) => item.userId),
  })
}

/** 选择试卷后使用试卷默认及格分。 */
function selectPaper(id: string) {
  const paper = paperOptions.value.find((item) => item.id === id)
  if (paper) form.examPassScore = paper.passScore
}

/** 合并已失效但仍在草稿中的课程快照，确保页面能显示原选择。 */
function mergeCourseOptions(candidates: PlanCourseOption[], value: Plan) {
  const optionMap = new Map(candidates.map((item) => [item.courseId, item]))
  value.courses.forEach((course) => {
    if (!optionMap.has(course.courseId)) {
      optionMap.set(course.courseId, {
        courseId: course.courseId,
        name: `${course.courseName}（当前不可选）`,
        requiredDurationSeconds: course.requiredDurationSeconds,
        coursewareCount: course.coursewares.length,
        totalVideoDurationSeconds: course.coursewares.reduce(
          (total, item) => total + item.durationSeconds,
          0,
        ),
      })
    }
  })
  return [...optionMap.values()]
}

/** 合并已失效但仍在草稿中的学员快照，确保页面能显示原选择。 */
function mergeParticipantOptions(candidates: PlanParticipantOption[], value: Plan) {
  const optionMap = new Map(candidates.map((item) => [item.userId, item]))
  value.users.forEach((user) => {
    if (!optionMap.has(user.userId)) {
      optionMap.set(user.userId, {
        userId: user.userId,
        orgId: user.orgId,
        orgName: user.orgName,
        username: user.username,
        displayName: `${user.displayName}（当前不可选）`,
        faceReferenceEnrolled: user.faceReferenceEnrolled,
      })
    }
  })
  return [...optionMap.values()]
}

/** 保存草稿基础信息、课程和学员选择。 */
async function saveDraft(showSuccess = true) {
  if (!editable.value || !(await formRef.value?.validate().catch(() => false))) {
    return false
  }
  if (new Date(form.startAt).getTime() >= new Date(form.endAt).getTime()) {
    ElMessage.warning('开始时间必须早于结束时间')
    return false
  }
  if (
    form.faceCheckEnabled &&
    form.faceCheckMinIntervalSeconds > form.faceCheckMaxIntervalSeconds
  ) {
    ElMessage.warning('抽验最小间隔不能大于最大间隔')
    return false
  }
  if (form.examRequired && !selectedPaper.value) {
    ElMessage.warning('请选择已启用的考试试卷')
    return false
  }
  if (
    selectedPaper.value &&
    (form.examPassScore < 1 || form.examPassScore > selectedPaper.value.totalScore)
  ) {
    ElMessage.warning(`计划及格分须在1至${selectedPaper.value.totalScore}分之间`)
    return false
  }
  saving.value = true
  try {
    const result = await updatePlan(planId, {
      ...form,
      examPaperId: form.examRequired ? form.examPaperId : undefined,
      examPassScore: form.examRequired ? form.examPassScore : undefined,
    })
    plan.value = result
    syncForm(result)
    if (showSuccess) ElMessage.success('计划草稿已保存')
    return true
  } catch (error) {
    showError(error)
    return false
  } finally {
    saving.value = false
  }
}

/** 有编辑权限时先保存当前配置，否则直接发布已保存的草稿。 */
async function publishConfiguredPlan() {
  const unenrolledUsers = selectedUnenrolledUsers()
  if (form.faceCheckEnabled && unenrolledUsers.length) {
    ElMessage.warning(`以下学员尚未登记人脸：${unenrolledUsers.join('、')}`)
    return
  }
  if (editable.value && !(await saveDraft(false))) {
    return
  }
  await ElMessageBox.confirm('发布后计划、课程、学员和规则不可修改，确定继续吗？', '发布确认', {
    type: 'warning',
  })
  saving.value = true
  try {
    plan.value = await publishPlan(planId)
    syncForm(plan.value)
    ElMessage.success('培训计划已发布')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 返回当前计划已选择但尚未登记人脸的学员姓名。 */
function selectedUnenrolledUsers() {
  if (participantOptions.value.length) {
    return participantOptions.value
      .filter((option) => form.userIds.includes(option.userId) && !option.faceReferenceEnrolled)
      .map((option) => option.displayName)
  }
  return (plan.value?.users || [])
    .filter((user) => !user.faceReferenceEnrolled)
    .map((user) => user.displayName)
}

/** 取消尚未开始的已发布计划。 */
async function cancelPublishedPlan() {
  await ElMessageBox.confirm('取消后学员任务将同步标记为已取消，确定继续吗？', '取消确认', {
    type: 'warning',
  })
  saving.value = true
  try {
    plan.value = await cancelPlan(planId)
    syncForm(plan.value)
    ElMessage.success('培训计划已取消')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 返回课程候选项的展示文案。 */
function courseOptionLabel(option: PlanCourseOption) {
  return `${option.name} · ${option.coursewareCount}个视频 · ${formatDuration(option.requiredDurationSeconds)}`
}

/** 返回学员候选项的展示文案。 */
function participantOptionLabel(option: PlanParticipantOption) {
  return `${option.displayName}（${option.username}）${option.orgName ? ` · ${option.orgName}` : ''}${option.faceReferenceEnrolled ? '' : ' · 未登记人脸'}`
}

/** 将秒数格式化为易读时长。 */
function formatDuration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.ceil((seconds % 3600) / 60)
  return `${hours ? `${hours}小时` : ''}${minutes ? `${minutes}分钟` : '0分钟'}`
}

/** 格式化计划时间用于只读展示。 */
function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

/** 返回计划状态中文文案。 */
function statusLabel(status: PlanStatus) {
  return {
    DRAFT: '草稿',
    PUBLISHED: '待开始',
    IN_PROGRESS: '进行中',
    FINISHED: '已结束',
    CANCELLED: '已取消',
  }[status]
}

/** 返回计划状态标签颜色。 */
function statusType(status: PlanStatus) {
  if (status === 'IN_PROGRESS') return 'success'
  if (status === 'DRAFT' || status === 'PUBLISHED') return 'warning'
  return 'info'
}

/** 返回任务分配状态中文文案。 */
function assignmentStatusLabel(status: string) {
  return status === 'CANCELLED' ? '已取消' : '已分配'
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

/** 统一展示接口错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <header class="detail-header">
      <div>
        <el-button link type="primary" @click="router.push('/admin/plans')"
          >← 返回计划列表</el-button
        >
        <h1>{{ plan?.name || '培训计划详情' }}</h1>
        <p>草稿可编辑；发布后展示的是不可变的课程规则、课件清单和学员快照。</p>
      </div>
      <div v-if="plan" class="header-actions">
        <el-tag :type="statusType(plan.status)" size="large">{{ statusLabel(plan.status) }}</el-tag>
        <PermissionButton
          v-if="plan.status === 'DRAFT'"
          permission="admin:plan:publish"
          type="primary"
          :loading="saving"
          @click="publishConfiguredPlan"
        >
          {{ editable ? '保存并发布' : '发布计划' }}
        </PermissionButton>
        <PermissionButton
          v-if="cancellable"
          permission="admin:plan:cancel"
          type="warning"
          :loading="saving"
          @click="cancelPublishedPlan"
        >
          取消计划
        </PermissionButton>
      </div>
    </header>

    <el-card v-if="plan" shadow="never">
      <template #header><strong>基础信息</strong></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="计划名称" prop="name">
          <el-input v-model="form.name" :disabled="!editable" maxlength="128" />
        </el-form-item>
        <el-form-item label="计划说明">
          <el-input
            v-model="form.description"
            :disabled="!editable"
            maxlength="1000"
            rows="3"
            type="textarea"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="开始时间" prop="startAt">
            <el-date-picker
              v-model="form.startAt"
              :disabled="!editable"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="结束时间" prop="endAt">
            <el-date-picker
              v-model="form.endAt"
              :disabled="!editable"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
        </div>
        <el-form-item label="需要考试">
          <el-switch v-model="form.examRequired" :disabled="!editable" />
          <span class="form-tip">学习完成且考试及格后确认结业</span>
        </el-form-item>
        <template v-if="form.examRequired">
          <el-form-item label="考试试卷">
            <el-select
              v-if="editable"
              v-model="form.examPaperId"
              filterable
              placeholder="请选择已启用试卷"
              @change="selectPaper"
            >
              <el-option
                v-for="paperOption in paperOptions"
                :key="paperOption.id"
                :label="`${paperOption.name}（${paperOption.totalScore}分 / ${paperOption.durationMinutes}分钟）`"
                :value="paperOption.id"
              />
            </el-select>
            <span v-else>
              试卷 {{ plan.examPaperId }}，限时 {{ plan.examDurationMinutes }} 分钟
            </span>
          </el-form-item>
          <el-form-item label="计划及格分">
            <el-input-number
              v-model="form.examPassScore"
              :disabled="!editable"
              :min="1"
              :max="selectedPaper?.totalScore || 10000"
            />
          </el-form-item>
        </template>
        <el-form-item label="人脸抽验">
          <el-switch v-model="form.faceCheckEnabled" :disabled="!editable" />
          <span class="form-tip">
            {{ form.faceCheckEnabled ? '按累计有效学时随机触发' : '未启用' }}
          </span>
        </el-form-item>
        <div v-if="form.faceCheckEnabled" class="form-grid face-rule-grid">
          <el-form-item label="随机间隔">
            <div class="interval-inputs">
              <el-input-number
                v-model="form.faceCheckMinIntervalSeconds"
                :disabled="!editable"
                :min="60"
                :max="86400"
                controls-position="right"
              />
              <span>至</span>
              <el-input-number
                v-model="form.faceCheckMaxIntervalSeconds"
                :disabled="!editable"
                :min="60"
                :max="86400"
                controls-position="right"
              />
              <span>秒</span>
            </div>
          </el-form-item>
          <el-form-item label="响应时限">
            <el-input-number
              v-model="form.faceCheckTimeoutSeconds"
              :disabled="!editable"
              :min="10"
              :max="300"
              controls-position="right"
            />
            <span class="form-tip">秒</span>
          </el-form-item>
          <el-form-item label="最多提交">
            <el-input-number
              v-model="form.faceCheckMaxAttempts"
              :disabled="!editable"
              :min="1"
              :max="10"
              controls-position="right"
            />
            <span class="form-tip">次</span>
          </el-form-item>
        </div>
        <el-form-item label="选择课程">
          <el-select
            v-model="form.courseIds"
            :disabled="!editable"
            filterable
            multiple
            placeholder="请选择至少一门已启用课程"
          >
            <el-option
              v-for="option in courseOptions"
              :key="option.courseId"
              :label="courseOptionLabel(option)"
              :value="option.courseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择学员">
          <el-select
            v-model="form.userIds"
            :disabled="!editable"
            filterable
            multiple
            placeholder="请选择至少一名已启用学员"
          >
            <el-option
              v-for="option in participantOptions"
              :key="option.userId"
              :label="participantOptionLabel(option)"
              :value="option.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editable">
          <PermissionButton
            permission="admin:plan:update"
            type="primary"
            :loading="saving"
            @click="saveDraft()"
          >
            保存草稿
          </PermissionButton>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="plan" class="snapshot-card" shadow="never">
      <template #header>
        <strong>{{ plan.status === 'DRAFT' ? '当前课程配置' : '发布课程快照' }}</strong>
      </template>
      <el-empty v-if="!plan.courses.length" description="尚未选择课程" />
      <el-table v-else :data="plan.courses" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.coursewares" class="nested-table" size="small">
              <el-table-column label="课件标题" min-width="180" prop="title" />
              <el-table-column label="视频时长" min-width="100">
                <template #default="{ row: item }">{{
                  formatDuration(item.durationSeconds)
                }}</template>
              </el-table-column>
              <el-table-column label="顺序" width="80" prop="sortOrder" />
            </el-table>
          </template>
        </el-table-column>
        <el-table-column label="课程名称" min-width="180" prop="courseName" />
        <el-table-column label="规定学时" min-width="110">
          <template #default="{ row }">{{ formatDuration(row.requiredDurationSeconds) }}</template>
        </el-table-column>
        <el-table-column label="允许拖动" width="95">
          <template #default="{ row }">{{ row.allowSeek ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="上报间隔" width="100">
          <template #default="{ row }">{{ row.progressReportIntervalSeconds }}秒</template>
        </el-table-column>
        <el-table-column label="学时误差" width="100">
          <template #default="{ row }">{{ row.studyToleranceSeconds }}秒</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="plan" class="snapshot-card" shadow="never">
      <template #header
        ><strong>{{ plan.status === 'DRAFT' ? '当前参训人员' : '发布学员快照' }}</strong></template
      >
      <el-empty v-if="!plan.users.length" description="尚未选择学员" />
      <el-table v-else :data="plan.users" row-key="id">
        <el-table-column label="姓名" min-width="120" prop="displayName" />
        <el-table-column label="用户名" min-width="130" prop="username" />
        <el-table-column label="部门" min-width="140" prop="orgName" />
        <el-table-column label="人脸登记" min-width="100">
          <template #default="{ row }">{{
            row.faceReferenceEnrolled ? '已登记' : '未登记'
          }}</template>
        </el-table-column>
        <el-table-column label="分配状态" min-width="100">
          <template #default="{ row }">{{ assignmentStatusLabel(row.assignmentStatus) }}</template>
        </el-table-column>
        <el-table-column label="学习状态" min-width="110">
          <template #default="{ row }">{{ studyStatusLabel(row.studyStatus) }}</template>
        </el-table-column>
        <el-table-column label="完成状态" min-width="120">
          <template #default="{ row }">{{ completionStatusLabel(row.completionStatus) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert
      v-if="plan && plan.status !== 'DRAFT'"
      class="snapshot-card"
      type="info"
      :closable="false"
      :title="`培训周期：${formatDateTime(plan.startAt)} — ${formatDateTime(plan.endAt)}`"
    />
  </section>
</template>

<style scoped>
.detail-header,
.header-actions,
.form-grid {
  display: flex;
  gap: 16px;
}

.detail-header {
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.detail-header h1 {
  margin: 12px 0 6px;
}

.detail-header p,
.form-tip {
  color: #7b879b;
}

.face-rule-grid {
  align-items: flex-start;
}

.interval-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  align-items: center;
}

.form-grid > * {
  flex: 1;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
}

.snapshot-card {
  margin-top: 20px;
}

.nested-table {
  margin: 4px 48px 16px;
  width: calc(100% - 96px);
}

:deep(.el-select),
:deep(.el-date-editor) {
  width: 100%;
}

@media (width <= 800px) {
  .detail-header,
  .form-grid {
    flex-direction: column;
  }
}
</style>
