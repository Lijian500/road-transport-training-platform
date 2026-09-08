<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { ApiError } from '@/api/http'
import {
  getParticipantStatistics,
  getPlanStatistics,
  getStatisticsOverview,
  type CompletionStatus,
  type ParticipantStatistics,
  type PlanStatistics,
  type StatisticsExamStatus,
  type StatisticsOverview,
  type StatisticsPlanStatus,
  type StudyStatus,
} from '@/api/statistics'
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import LearningRecordDetail from '@/components/LearningRecordDetail/LearningRecordDetail.vue'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const overviewLoading = ref(false)
const planLoading = ref(false)
const participantLoading = ref(false)
const planOptionLoading = ref(false)
const overview = ref<StatisticsOverview>()
const planRows = ref<PlanStatistics[]>([])
const planTotal = ref(0)
const participantRows = ref<ParticipantStatistics[]>([])
const participantTotal = ref(0)
const recordTaskId = ref('')
const scopePlanId = ref('')
const activeTab = ref('plans')
const planOptions = ref<PlanStatistics[]>([])
let planOptionRequestId = 0
const planQuery = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  status: '' as StatisticsPlanStatus | '',
})
const participantQuery = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  completionStatus: '' as CompletionStatus | '',
})

/** 加载当前计划范围的总览指标。 */
async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getStatisticsOverview(scopePlanId.value || undefined)
  } catch (error) {
    showError(error)
  } finally {
    overviewLoading.value = false
  }
}

/** 分页加载培训计划统计。 */
async function loadPlans() {
  planLoading.value = true
  try {
    const result = await getPlanStatistics(planQuery)
    planRows.value = result.records
    planTotal.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    planLoading.value = false
  }
}

/** 分页加载当前筛选条件下的学员培训明细。 */
async function loadParticipants() {
  participantLoading.value = true
  try {
    const result = await getParticipantStatistics({
      ...participantQuery,
      planId: scopePlanId.value || undefined,
    })
    participantRows.value = result.records
    participantTotal.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    participantLoading.value = false
  }
}

/** 远程搜索可作为统计范围的培训计划。 */
async function searchPlanOptions(keyword = '') {
  const requestId = ++planOptionRequestId
  planOptionLoading.value = true
  try {
    const result = await getPlanStatistics({
      pageNumber: 1,
      pageSize: 100,
      keyword,
    })
    if (requestId !== planOptionRequestId) return
    const selected = planOptions.value.find((item) => item.planId === scopePlanId.value)
    planOptions.value = selected
      ? [selected, ...result.records.filter((item) => item.planId !== selected.planId)]
      : result.records
  } catch (error) {
    if (requestId === planOptionRequestId) showError(error)
  } finally {
    if (requestId === planOptionRequestId) planOptionLoading.value = false
  }
}

/** 展开计划选择器时加载默认候选项。 */
function preparePlanOptions(visible: boolean) {
  if (visible) void searchPlanOptions()
}

/** 切换统计计划范围并同步刷新总览和学员明细。 */
function changeScopePlan() {
  participantQuery.pageNumber = 1
  void Promise.all([loadOverview(), loadParticipants()])
}

/** 点击计划名称后将其设为当前统计范围。 */
function selectPlanScope(row: PlanStatistics) {
  if (!planOptions.value.some((item) => item.planId === row.planId)) {
    planOptions.value.unshift(row)
  }
  scopePlanId.value = row.planId
  changeScopePlan()
}

/** 从第一页按当前条件查询计划统计。 */
function searchPlans() {
  planQuery.pageNumber = 1
  void loadPlans()
}

/** 切换计划统计页码。 */
function changePlanPage(pageNumber: number) {
  planQuery.pageNumber = pageNumber
  void loadPlans()
}

/** 切换计划统计每页数量。 */
function changePlanPageSize(pageSize: number) {
  planQuery.pageSize = pageSize
  planQuery.pageNumber = 1
  void loadPlans()
}

/** 从第一页按当前条件查询学员明细。 */
function searchParticipants() {
  participantQuery.pageNumber = 1
  void loadParticipants()
}

/** 切换学员明细页码。 */
function changeParticipantPage(pageNumber: number) {
  participantQuery.pageNumber = pageNumber
  void loadParticipants()
}

/** 切换学员明细每页数量。 */
function changeParticipantPageSize(pageSize: number) {
  participantQuery.pageSize = pageSize
  participantQuery.pageNumber = 1
  void loadParticipants()
}

/** 将毫秒数转换为易读的有效学时时长。 */
function formatDuration(milliseconds: number) {
  const totalSeconds = Math.max(0, Math.floor((milliseconds || 0) / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  if (hours > 0) return `${hours}小时${minutes ? `${minutes}分钟` : ''}`
  if (minutes > 0) return `${minutes}分钟`
  return totalSeconds > 0 ? `${totalSeconds}秒` : '0分钟'
}

/** 将完成率格式化为百分数。 */
function formatRate(rate: number) {
  const value = Number(rate)
  return `${Number.isFinite(value) ? value.toFixed(2) : '0.00'}%`
}

/** 将后端日期时间转换为本地展示文本。 */
function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  return date.toLocaleString('zh-CN', { hour12: false })
}

/** 返回培训计划状态中文文案。 */
function planStatusLabel(status: StatisticsPlanStatus) {
  return {
    PUBLISHED: '已发布',
    IN_PROGRESS: '进行中',
    FINISHED: '已结束',
    CANCELLED: '已取消',
  }[status]
}

/** 返回培训计划状态标签颜色。 */
function planStatusType(status: StatisticsPlanStatus): TagType {
  if (status === 'FINISHED') return 'success'
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'PUBLISHED') return 'primary'
  return 'info'
}

/** 返回学习状态中文文案。 */
function studyStatusLabel(status: StudyStatus) {
  return { NOT_STARTED: '未开始', IN_PROGRESS: '学习中', COMPLETED: '已学完' }[status]
}

/** 返回学习状态标签颜色。 */
function studyStatusType(status: StudyStatus): TagType {
  return status === 'COMPLETED' ? 'success' : status === 'IN_PROGRESS' ? 'warning' : 'info'
}

/** 返回考试状态中文文案。 */
function examStatusLabel(status: StatisticsExamStatus) {
  return {
    NOT_REQUIRED: '无需考试',
    NOT_STARTED: '未考试',
    IN_PROGRESS: '考试中',
    PASSED: '已通过',
    FAILED: '未通过',
  }[status]
}

/** 返回考试状态标签颜色。 */
function examStatusType(status: StatisticsExamStatus): TagType {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'IN_PROGRESS') return 'warning'
  return 'info'
}

/** 返回培训完成状态中文文案。 */
function completionStatusLabel(status: CompletionStatus) {
  return status === 'COMPLETED' ? '已完成' : '未完成'
}

/** 返回培训完成状态标签颜色。 */
function completionStatusType(status: CompletionStatus): TagType {
  return status === 'COMPLETED' ? 'success' : 'info'
}

/** 统一展示接口错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '统计数据加载失败，请稍后重试')
}

onMounted(() => {
  void Promise.all([loadOverview(), loadPlans(), loadParticipants(), searchPlanOptions()])
})
</script>

<template>
  <section>
    <header class="page-title statistics-title">
      <div>
        <h1>培训统计</h1>
        <p>汇总计划完成情况、学员考试结果和服务端有效学时。</p>
      </div>
      <AppFilterField label="统计范围">
        <el-select
          v-model="scopePlanId"
          :loading="planOptionLoading"
          clearable
          filterable
          remote
          reserve-keyword
          placeholder="全部培训计划"
          :remote-method="searchPlanOptions"
          @change="changeScopePlan"
          @visible-change="preparePlanOptions"
        >
          <el-option
            v-for="plan in planOptions"
            :key="plan.planId"
            :label="plan.planName"
            :value="plan.planId"
          />
        </el-select>
      </AppFilterField>
    </header>

    <div v-loading="overviewLoading" class="metrics-grid">
      <article class="metric-card metric-card--primary">
        <span>培训计划</span>
        <strong>{{ overview?.planCount ?? 0 }}</strong>
        <small>个计划</small>
      </article>
      <article class="metric-card">
        <span>参训人次</span>
        <strong>{{ overview?.participantCount ?? 0 }}</strong>
        <small>已分配任务</small>
      </article>
      <article class="metric-card">
        <span>已开始</span>
        <strong>{{ overview?.startedCount ?? 0 }}</strong>
        <small>人次</small>
      </article>
      <article class="metric-card">
        <span>学习完成</span>
        <strong>{{ overview?.studyCompletedCount ?? 0 }}</strong>
        <small>人次</small>
      </article>
      <article class="metric-card">
        <span>考试通过</span>
        <strong>{{ overview?.examPassedCount ?? 0 }}</strong>
        <small>人次</small>
      </article>
      <article class="metric-card metric-card--success">
        <span>培训完成</span>
        <strong>{{ overview?.completedCount ?? 0 }}</strong>
        <small>人次</small>
      </article>
      <article class="metric-card metric-card--success">
        <span>完成率</span>
        <strong>{{ formatRate(overview?.completionRate ?? 0) }}</strong>
        <small>已完成 / 参训人次</small>
      </article>
      <article class="metric-card">
        <span>应修总学时</span>
        <strong>{{ formatDuration(overview?.requiredDurationMillis ?? 0) }}</strong>
        <small>计划要求</small>
      </article>
      <article class="metric-card metric-card--primary">
        <span>有效学时</span>
        <strong>{{ formatDuration(overview?.effectiveDurationMillis ?? 0) }}</strong>
        <small>服务端累计</small>
      </article>
      <article class="metric-card">
        <span>平均有效学时</span>
        <strong>{{ formatDuration(overview?.averageEffectiveDurationMillis ?? 0) }}</strong>
        <small>每参训人次</small>
      </article>
    </div>

    <el-tabs v-model="activeTab" class="statistics-tabs">
      <el-tab-pane label="计划统计" name="plans">
        <AppTable
          :data="planRows"
          :loading="planLoading"
          :page-number="planQuery.pageNumber"
          :page-size="planQuery.pageSize"
          :total="planTotal"
          @page-change="changePlanPage"
          @size-change="changePlanPageSize"
        >
          <template #search>
            <AppFilterField label="计划名称">
              <el-input
                v-model="planQuery.keyword"
                clearable
                placeholder="请输入计划名称"
                @keyup.enter="searchPlans"
              />
            </AppFilterField>
            <AppFilterField label="计划状态">
              <el-select v-model="planQuery.status" clearable placeholder="全部">
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已结束" value="FINISHED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </AppFilterField>
            <div class="app-filter-actions">
              <el-button type="primary" @click="searchPlans">查询</el-button>
            </div>
          </template>
          <el-table-column label="培训计划" min-width="210">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectPlanScope(row)">
                {{ row.planName }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="planStatusType(row.status)">
                {{ planStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="培训时间" min-width="190">
            <template #default="{ row }">
              <div>{{ formatDateTime(row.startAt) }}</div>
              <div class="secondary-text">至 {{ formatDateTime(row.endAt) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="考试" width="90">
            <template #default="{ row }">{{ row.examRequired ? '需要' : '无需' }}</template>
          </el-table-column>
          <el-table-column label="参训" width="80" prop="participantCount" />
          <el-table-column label="已开始" width="80" prop="startedCount" />
          <el-table-column label="学习完成" width="90" prop="studyCompletedCount" />
          <el-table-column label="考试通过" width="90" prop="examPassedCount" />
          <el-table-column label="培训完成" width="90" prop="completedCount" />
          <el-table-column label="完成率" width="100" fixed="right">
            <template #default="{ row }">
              <strong class="rate-text">{{ formatRate(row.completionRate) }}</strong>
            </template>
          </el-table-column>
        </AppTable>
      </el-tab-pane>

      <el-tab-pane label="学员培训明细" name="participants">
        <AppTable
          :data="participantRows"
          :loading="participantLoading"
          :page-number="participantQuery.pageNumber"
          :page-size="participantQuery.pageSize"
          :total="participantTotal"
          @page-change="changeParticipantPage"
          @size-change="changeParticipantPageSize"
        >
          <template #search>
            <AppFilterField label="学员关键字">
              <el-input
                v-model="participantQuery.keyword"
                clearable
                placeholder="姓名或账号"
                @keyup.enter="searchParticipants"
              />
            </AppFilterField>
            <AppFilterField label="完成状态">
              <el-select v-model="participantQuery.completionStatus" clearable placeholder="全部">
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="未完成" value="NOT_COMPLETED" />
              </el-select>
            </AppFilterField>
            <div class="app-filter-actions">
              <el-button type="primary" @click="searchParticipants">查询</el-button>
            </div>
          </template>
          <el-table-column label="学员" min-width="150">
            <template #default="{ row }">
              <strong>{{ row.displayName }}</strong>
              <div class="secondary-text">{{ row.username }}</div>
            </template>
          </el-table-column>
          <el-table-column label="组织" min-width="140">
            <template #default="{ row }">{{ row.orgName || '-' }}</template>
          </el-table-column>
          <el-table-column label="培训计划" min-width="180" prop="planName" />
          <el-table-column label="学习档案" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="recordTaskId = row.taskId">查看学习过程</el-button>
            </template>
          </el-table-column>
          <el-table-column label="学习状态" width="100">
            <template #default="{ row }">
              <el-tag :type="studyStatusType(row.studyStatus)">
                {{ studyStatusLabel(row.studyStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="考试状态" width="100">
            <template #default="{ row }">
              <el-tag :type="examStatusType(row.examStatus)">
                {{ examStatusLabel(row.examStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="成绩" width="80">
            <template #default="{ row }">{{ row.examScore ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="有效 / 应修学时" min-width="155">
            <template #default="{ row }">
              <strong>{{ formatDuration(row.effectiveDurationMillis) }}</strong>
              <div class="secondary-text">
                应修 {{ formatDuration(row.requiredDurationMillis) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="培训状态" width="100">
            <template #default="{ row }">
              <el-tag :type="completionStatusType(row.completionStatus)">
                {{ completionStatusLabel(row.completionStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="完成时间" min-width="170" fixed="right">
            <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
          </el-table-column>
        </AppTable>
      </el-tab-pane>
    </el-tabs>
    <el-drawer
      :model-value="Boolean(recordTaskId)"
      title="学时监管与抽验记录"
      size="90%"
      destroy-on-close
      @update:model-value="(value: boolean) => { if (!value) recordTaskId = '' }"
    >
      <LearningRecordDetail v-if="recordTaskId" :task-id="recordTaskId" audience="admin" />
    </el-drawer>
  </section>
</template>

<style scoped>
.statistics-title {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

.statistics-title :deep(.app-filter-field) {
  width: min(360px, 100%);
  flex: 0 1 360px;
}

.metrics-grid {
  display: grid;
  min-height: 148px;
  margin-bottom: 26px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  position: relative;
  display: grid;
  min-width: 0;
  padding: 18px;
  overflow: hidden;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  gap: 8px;
}

.metric-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: #d8e0ec;
  content: '';
}

.metric-card--primary::before {
  background: var(--el-color-primary);
}

.metric-card--success::before {
  background: var(--el-color-success);
}

.metric-card span,
.metric-card small,
.secondary-text {
  color: var(--app-text-muted);
}

.metric-card strong {
  overflow: hidden;
  font-size: clamp(20px, 2vw, 28px);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card small {
  font-size: 12px;
}

.statistics-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.secondary-text {
  margin-top: 4px;
  font-size: 12px;
}

.rate-text {
  color: var(--el-color-success);
}

@media (width <= 1280px) {
  .metrics-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 760px) {
  .statistics-title {
    align-items: stretch;
    flex-direction: column;
  }

  .statistics-title :deep(.app-filter-field) {
    width: 100%;
    flex-basis: auto;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 480px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
