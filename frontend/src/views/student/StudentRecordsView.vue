<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getMyRecords, type RecordItem } from '@/api/records'
import AppTable from '@/components/AppTable/AppTable.vue'
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import LearningRecordDetail from '@/components/LearningRecordDetail/LearningRecordDetail.vue'
import { recordDuration, recordLabel, recordTime } from '@/utils/recordDisplay'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const rows = ref<Array<RecordItem & { id: string }>>([])
const total = ref(0)
const dates = ref<string[]>([])
const selectedTask = ref('')
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  completionStatus: '',
  activity: '',
})
let requestVersion = 0

/** 根据当前筛选分页读取档案，忽略已被新查询替代的响应。 */
async function load() {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const page = await getMyRecords({
      ...query,
      fromDate: dates.value?.[0],
      toDate: dates.value?.[1],
    })
    if (version !== requestVersion) return
    rows.value = page.records.map((row) => ({ ...row, id: row.training.taskId }))
    total.value = page.total
  } catch (reason) {
    if (version === requestVersion)
      error.value = reason instanceof Error ? reason.message : '档案加载失败'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

/** 筛选变化后返回第一页。 */
function search() {
  query.pageNumber = 1
  void load()
}
/** 切换档案页码。 */
function changePage(value: number) {
  query.pageNumber = value
  void load()
}
/** 调整每页数量。 */
function changeSize(value: number) {
  query.pageSize = value
  search()
}
/** 应用首页传入的待办类型，忽略不支持的值。 */
function applyRoute() {
  const value = String(route.query.activity || '')
  query.activity = ['TO_STUDY', 'TO_EXAM', 'COMPLETED'].includes(value) ? value : ''
  search()
}
watch(() => route.query.activity, applyRoute)
onMounted(applyRoute)
</script>

<template>
  <section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <AppTable
      :data="rows"
      :loading="loading"
      :total="total"
      :page-number="query.pageNumber"
      :page-size="query.pageSize"
      @page-change="changePage"
      @size-change="changeSize"
    >
      <template #search>
        <AppFilterField label="计划名称"
          ><el-input v-model="query.keyword" clearable @keyup.enter="search"
        /></AppFilterField>
        <AppFilterField label="完成状态"
          ><el-select v-model="query.completionStatus" clearable placeholder="全部">
            <el-option label="已完成" value="COMPLETED" /><el-option
              label="未完成"
              value="NOT_COMPLETED"
            /> </el-select
        ></AppFilterField>
        <AppFilterField label="任务类型"
          ><el-select v-model="query.activity" clearable placeholder="全部">
            <el-option label="待学习" value="TO_STUDY" /><el-option
              label="待考试"
              value="TO_EXAM"
            />
            <el-option label="已完成" value="COMPLETED" /> </el-select
        ></AppFilterField>
        <AppFilterField label="培训开始日期"
          ><el-date-picker
            v-model="dates"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
        /></AppFilterField>
        <div class="app-filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </template>
      <el-table-column label="培训计划" min-width="190" prop="training.planName" />
      <el-table-column label="计划状态" width="100"
        ><template #default="{ row }">{{
          recordLabel(row.training.planStatus)
        }}</template></el-table-column
      >
      <el-table-column label="有效学时" min-width="150"
        ><template #default="{ row }">{{
          recordDuration(row.effectiveDurationMillis)
        }}</template></el-table-column
      >
      <el-table-column label="规定学时" min-width="150"
        ><template #default="{ row }">{{
          recordDuration(row.training.requiredDurationMillis)
        }}</template></el-table-column
      >
      <el-table-column label="考试成绩" width="100"
        ><template #default="{ row }">{{
          row.training.examScore ?? '—'
        }}</template></el-table-column
      >
      <el-table-column label="培训状态" width="100"
        ><template #default="{ row }">{{
          recordLabel(row.training.completionStatus)
        }}</template></el-table-column
      >
      <el-table-column label="完成时间" min-width="185"
        ><template #default="{ row }">{{
          recordTime(row.training.completedAt)
        }}</template></el-table-column
      >
      <el-table-column label="操作" fixed="right" width="90"
        ><template #default="{ row }">
          <el-button link type="primary" @click="selectedTask = row.training.taskId"
            >查看档案</el-button
          >
        </template></el-table-column
      >
    </AppTable>
    <el-drawer
      :model-value="Boolean(selectedTask)"
      title="学习档案详情"
      size="90%"
      destroy-on-close
      @closed="selectedTask = ''"
      @update:model-value="
        (value: boolean) => {
          if (!value) selectedTask = ''
        }
      "
    >
      <LearningRecordDetail v-if="selectedTask" :task-id="selectedTask" audience="student" />
    </el-drawer>
  </section>
</template>
