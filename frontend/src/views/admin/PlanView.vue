<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'

import {
  cancelPlan,
  createPlan,
  deletePlan,
  getPlans,
  publishPlan,
  type Plan,
  type PlanStatus,
} from '@/api/training'
import { ApiError } from '@/api/http'
import { formatTrainingDate } from '@/utils/trainingDisplay'
import { getEnabledExamPaperOptions, type PaperOption } from '@/api/exam'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const rows = ref<Plan[]>([])
const total = ref(0)
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  status: '' as PlanStatus | '',
})
const dialogVisible = ref(false)
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
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endAt: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

/** 分页加载当前组织培训计划。 */
async function load() {
  loading.value = true
  try {
    const result = await getPlans(query)
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 打开新建计划弹窗并设置合理的默认时间。 */
function openCreate() {
  const start = new Date(Date.now() + 24 * 60 * 60 * 1000)
  const end = new Date(start.getTime() + 30 * 24 * 60 * 60 * 1000)
  Object.assign(form, {
    name: '',
    description: '',
    startAt: toLocalDateTime(start),
    endAt: toLocalDateTime(end),
    examRequired: false,
    examPaperId: '',
    examPassScore: 60,
    faceCheckEnabled: false,
    faceCheckMinIntervalSeconds: 300,
    faceCheckMaxIntervalSeconds: 600,
    faceCheckTimeoutSeconds: 60,
    faceCheckMaxAttempts: 3,
  })
  dialogVisible.value = true
  void loadPaperOptions()
}

/** 加载培训计划可关联的已启用试卷。 */
async function loadPaperOptions() {
  try {
    paperOptions.value = await getEnabledExamPaperOptions()
  } catch (error) {
    showError(error)
  }
}

/** 选择试卷后使用其默认及格分作为计划初值。 */
function selectPaper(id: string) {
  const paper = paperOptions.value.find((item) => item.id === id)
  if (paper) form.examPassScore = paper.passScore
}

/** 创建草稿并进入详情页选择课程和学员。 */
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  if (form.startAt.slice(0, 10) > form.endAt.slice(0, 10)) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  if (
    form.faceCheckEnabled &&
    form.faceCheckMinIntervalSeconds > form.faceCheckMaxIntervalSeconds
  ) {
    ElMessage.warning('抽验最小间隔不能大于最大间隔')
    return
  }
  const paper = paperOptions.value.find((item) => item.id === form.examPaperId)
  if (form.examRequired && !paper) {
    ElMessage.warning('请选择已启用的考试试卷')
    return
  }
  if (paper && (form.examPassScore < 1 || form.examPassScore > paper.totalScore)) {
    ElMessage.warning(`计划及格分须在1至${paper.totalScore}分之间`)
    return
  }
  saving.value = true
  try {
    const plan = await createPlan({
      ...form,
      startAt: `${form.startAt.slice(0, 10)}T00:00:00`,
      endAt: `${form.endAt.slice(0, 10)}T23:59:59`,
      examPaperId: form.examRequired ? form.examPaperId : undefined,
      examPassScore: form.examRequired ? form.examPassScore : undefined,
    })
    dialogVisible.value = false
    ElMessage.success('计划草稿已创建，请继续选择课程和学员')
    await router.push(`/admin/plans/${plan.id}`)
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 发布已配置课程和学员的计划草稿。 */
async function publish(row: Plan) {
  await ElMessageBox.confirm(`确定发布培训计划“${row.name}”吗？发布后不可修改。`, '发布确认', {
    type: 'warning',
  })
  try {
    await publishPlan(row.id)
    ElMessage.success('培训计划已发布')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 取消尚未开始的已发布计划。 */
async function cancel(row: Plan) {
  await ElMessageBox.confirm(`确定取消培训计划“${row.name}”吗？`, '取消确认', {
    type: 'warning',
  })
  try {
    await cancelPlan(row.id)
    ElMessage.success('培训计划已取消')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 删除培训计划草稿及其临时快照。 */
async function remove(row: Plan) {
  await ElMessageBox.confirm(`确定删除培训计划草稿“${row.name}”吗？`, '删除确认', {
    type: 'warning',
  })
  try {
    await deletePlan(row.id)
    ElMessage.success('计划草稿已删除')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 从第一页执行计划查询。 */
function search() {
  query.pageNumber = 1
  void load()
}

/** 切换计划列表页码。 */
function changePage(pageNumber: number) {
  query.pageNumber = pageNumber
  void load()
}

/** 切换每页数量并返回第一页。 */
function changePageSize(pageSize: number) {
  query.pageSize = pageSize
  query.pageNumber = 1
  void load()
}

/** 判断已发布计划当前是否仍允许取消。 */
function canCancel(row: Plan) {
  return row.status === 'PUBLISHED' && new Date(row.startAt).getTime() > Date.now()
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

/** 格式化计划时间用于列表展示。 */
function formatDateTime(value: string) {
  return formatTrainingDate(value)
}

/** 将日期转换为后端LocalDateTime兼容格式。 */
function toLocalDateTime(value: Date) {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 10)
}

/** 统一展示接口错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
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
        <AppFilterField label="计划名称">
          <el-input v-model="query.keyword" clearable placeholder="请输入" @keyup.enter="search" />
        </AppFilterField>
        <AppFilterField label="计划状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
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
      <template #actions>
        <PermissionButton permission="admin:plan:create" type="primary" @click="openCreate">
          新建计划
        </PermissionButton>
      </template>
      <el-table-column label="计划名称" min-width="190" prop="name" />
      <el-table-column label="培训周期" min-width="285">
        <template #default="{ row }">
          {{ formatDateTime(row.startAt) }} — {{ formatDateTime(row.endAt) }}
        </template>
      </el-table-column>
      <el-table-column label="课程/学员" min-width="110">
        <template #default="{ row }"
          >{{ row.courses.length }}门 / {{ row.users.length }}人</template
        >
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="人脸抽验" width="100">
        <template #default="{ row }">{{ row.faceCheckEnabled ? '已启用' : '未启用' }}</template>
      </el-table-column>
      <el-table-column label="考试" width="100">
        <template #default="{ row }">{{ row.examRequired ? '需要' : '无需' }}</template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/admin/plans/${row.id}`)">
            {{ row.status === 'DRAFT' ? '配置' : '查看' }}
          </el-button>
          <PermissionButton
            v-if="row.status === 'DRAFT'"
            permission="admin:plan:publish"
            link
            @click="publish(row)"
          >
            发布
          </PermissionButton>
          <PermissionButton
            v-if="canCancel(row)"
            permission="admin:plan:cancel"
            link
            type="warning"
            @click="cancel(row)"
          >
            取消
          </PermissionButton>
          <PermissionButton
            v-if="row.status === 'DRAFT'"
            permission="admin:plan:update"
            link
            type="danger"
            @click="remove(row)"
          >
            删除
          </PermissionButton>
        </template>
      </el-table-column>
    </AppTable>

    <AppDialog v-model="dialogVisible" title="新建培训计划" :loading="saving" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="计划名称" prop="name">
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="计划说明">
          <el-input v-model="form.description" maxlength="1000" rows="3" type="textarea" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startAt">
          <el-date-picker
            v-model="form.startAt"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endAt">
          <el-date-picker v-model="form.endAt" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="需要考试">
          <el-switch v-model="form.examRequired" />
          <span class="form-tip">启用后，学习完成且考试及格才确认结业</span>
        </el-form-item>
        <template v-if="form.examRequired">
          <el-form-item label="考试试卷">
            <el-select
              v-model="form.examPaperId"
              filterable
              placeholder="请选择已启用试卷"
              @change="selectPaper"
            >
              <el-option
                v-for="paper in paperOptions"
                :key="paper.id"
                :label="`${paper.name}（${paper.totalScore}分 / ${paper.durationMinutes}分钟）`"
                :value="paper.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="计划及格分">
            <el-input-number v-model="form.examPassScore" :min="1" :max="10000" />
          </el-form-item>
        </template>
        <el-form-item label="人脸抽验">
          <el-switch v-model="form.faceCheckEnabled" />
          <span class="form-tip">启用后，发布前所有学员都必须已登记人脸</span>
        </el-form-item>
        <div v-if="form.faceCheckEnabled" class="face-rule-grid">
          <el-form-item label="随机间隔">
            <div class="interval-inputs">
              <el-input-number
                v-model="form.faceCheckMinIntervalSeconds"
                :min="60"
                :max="86400"
                controls-position="right"
              />
              <span>至</span>
              <el-input-number
                v-model="form.faceCheckMaxIntervalSeconds"
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
              :min="10"
              :max="300"
              controls-position="right"
            />
            <span class="form-tip">秒</span>
          </el-form-item>
          <el-form-item label="最多提交">
            <el-input-number
              v-model="form.faceCheckMaxAttempts"
              :min="1"
              :max="10"
              controls-position="right"
            />
            <span class="form-tip">次</span>
          </el-form-item>
        </div>
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.form-tip {
  margin-left: 10px;
  color: #8792a6;
  font-size: 12px;
}

.face-rule-grid,
.interval-inputs {
  display: flex;
  gap: 12px;
}

.face-rule-grid {
  flex-wrap: wrap;
}

.interval-inputs {
  align-items: center;
}
</style>
