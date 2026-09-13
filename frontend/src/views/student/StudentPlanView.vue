<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { getStudentPlans, getStudentPlanProgress, type PlanStatus, type StudentPlan } from '@/api/training'
import { ApiError } from '@/api/http'
import { formatTrainingDate, formatLearningDuration, learningPercentage } from '@/utils/trainingDisplay'
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import AppTable from '@/components/AppTable/AppTable.vue'

const router = useRouter()
const loading = ref(false)
const rows = ref<StudentPlan[]>([])
const total = ref(0)
const progress = ref<Record<string, { completed: number; required: number }>>({})
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  status: '' as Exclude<PlanStatus, 'DRAFT'> | '',
})

/** 分页加载当前登录学员被分配的培训任务。 */
async function load() {
  loading.value = true
  try {
    const result = await getStudentPlans(query)
    rows.value = result.records
    total.value = result.total
    progress.value = {}
    if (result.records.length) {
      try {
        const values = await getStudentPlanProgress(result.records.map((row) => row.planId))
        progress.value = Object.fromEntries(values.map((value) => [value.planId, {
          completed: value.effectiveDurationMillis,
          required: value.requiredDurationMillis,
        }]))
      } catch {
        ElMessage.warning('任务进度加载失败，请重新查询')
      }
    }
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 从第一页按计划状态查询培训任务。 */
function search() {
  query.pageNumber = 1
  void load()
}

/** 切换培训任务列表页码。 */
function changePage(pageNumber: number) {
  query.pageNumber = pageNumber
  void load()
}

/** 切换每页任务数量并返回第一页。 */
function changePageSize(pageSize: number) {
  query.pageSize = pageSize
  query.pageNumber = 1
  void load()
}

/** 进入只属于当前学员的培训任务详情。 */
function openDetail(row: StudentPlan) {
  void router.push(`/student/plans/${row.planId}`)
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

/** 格式化计划培训周期。 */
function formatDateTime(value: string) {
  return formatTrainingDate(value)
}

/** 统一展示任务查询错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '培训任务加载失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section>

    <AppTable
      :data="rows"
      :loading="loading"
      :page-number="query.pageNumber"
      :page-size="query.pageSize"
      :total="total"
      @page-change="changePage"
      @size-change="changePageSize"
    >
      <template #search>
        <AppFilterField label="计划状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="待开始" value="PUBLISHED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已结束" value="FINISHED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </AppFilterField>
        <div class="app-filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </template>
      <el-table-column label="计划名称" min-width="200" prop="name" />
      <el-table-column label="培训周期" min-width="285">
        <template #default="{ row }">
          {{ formatDateTime(row.startAt) }} — {{ formatDateTime(row.endAt) }}
        </template>
      </el-table-column>
      <el-table-column label="是否需要考试" width="120">
        <template #default="{ row }">{{ row.examRequired ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="培训进度" min-width="240">
        <template #default="{ row }">
          <template v-if="progress[row.planId]">
            <span>{{ formatLearningDuration(progress[row.planId]!.completed) }} / {{ formatLearningDuration(progress[row.planId]!.required) }}</span>
            <el-progress :percentage="learningPercentage(progress[row.planId]!.completed, progress[row.planId]!.required)" />
          </template>
          <span v-else>暂无进度数据</span>
        </template>
      </el-table-column>
      <el-table-column label="任务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.completionStatus === 'COMPLETED' ? 'success' : statusType(row.status)">
            {{ row.completionStatus === 'COMPLETED' ? '已完成' : statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="学习状态" min-width="120">
        <template #default="{ row }">{{ studyStatusLabel(row.studyStatus) }}</template>
      </el-table-column>
      <el-table-column label="完成状态" min-width="130">
        <template #default="{ row }">{{ completionStatusLabel(row.completionStatus) }}</template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看课程</el-button>
        </template>
      </el-table-column>
    </AppTable>
  </section>
</template>
