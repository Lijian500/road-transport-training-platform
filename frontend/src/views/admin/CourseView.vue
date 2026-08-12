<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'

import {
  changeCourseStatus,
  createCourse,
  deleteCourse,
  getCourses,
  type Course,
  type CourseStatus,
} from '@/api/training'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const rows = ref<Course[]>([])
const total = ref(0)
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  status: '' as CourseStatus | '',
})
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  description: '',
  requiredDurationMinutes: 60,
  allowSeek: false,
  progressReportIntervalSeconds: 20,
  studyToleranceSeconds: 30,
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  requiredDurationMinutes: [{ required: true, message: '请输入规定时长', trigger: 'blur' }],
}

/** 分页加载当前组织课程。 */
async function load() {
  loading.value = true
  try {
    const result = await getCourses(query)
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 打开新建课程弹窗并恢复默认规则。 */
function openCreate() {
  Object.assign(form, {
    name: '',
    description: '',
    requiredDurationMinutes: 60,
    allowSeek: false,
    progressReportIntervalSeconds: 20,
    studyToleranceSeconds: 30,
  })
  dialogVisible.value = true
}

/** 创建草稿课程并立即进入详情页上传资源。 */
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    const course = await createCourse({
      name: form.name,
      description: form.description,
      requiredDurationSeconds: Math.round(form.requiredDurationMinutes * 60),
      allowSeek: form.allowSeek,
      progressReportIntervalSeconds: form.progressReportIntervalSeconds,
      studyToleranceSeconds: form.studyToleranceSeconds,
    })
    ElMessage.success('课程已创建，请继续上传封面和视频')
    dialogVisible.value = false
    await router.push(`/admin/courses/${course.id}`)
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 切换课程启用或禁用状态。 */
async function toggleStatus(row: Course) {
  const status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${status === 'ENABLED' ? '启用' : '禁用'}课程“${row.name}”吗？`,
    '状态确认',
    { type: 'warning' },
  )
  try {
    await changeCourseStatus(row.id, status)
    ElMessage.success('课程状态已更新')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 删除从未启用的草稿课程。 */
async function remove(row: Course) {
  await ElMessageBox.confirm(
    `确定删除草稿课程“${row.name}”吗？相关未使用文件将进入清理队列。`,
    '删除确认',
    { type: 'warning' },
  )
  try {
    await deleteCourse(row.id)
    ElMessage.success('课程已删除')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 从第一页执行课程查询。 */
function search() {
  query.pageNumber = 1
  void load()
}

/** 切换课程列表页码。 */
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

/** 将秒数格式化为易读时长。 */
function formatDuration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours ? `${hours}小时` : ''}${minutes}分钟`
}

/** 返回课程状态中文文案。 */
function statusLabel(status: CourseStatus) {
  return { DRAFT: '草稿', ENABLED: '启用', DISABLED: '已禁用' }[status]
}

/** 返回课程状态标签颜色。 */
function statusType(status: CourseStatus) {
  return status === 'ENABLED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'
}

/** 统一展示接口错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-title">
      <h1>课程管理</h1>
      <p>维护企业培训课程、课程规则、封面和视频课件。</p>
    </header>
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
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="课程名称"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="启用" value="ENABLED" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </template>
      <template #actions>
        <PermissionButton permission="admin:course:create" type="primary" @click="openCreate">
          新建课程
        </PermissionButton>
      </template>
      <el-table-column label="课程名称" min-width="200" prop="name" />
      <el-table-column label="规定时长" min-width="110">
        <template #default="{ row }">{{ formatDuration(row.requiredDurationSeconds) }}</template>
      </el-table-column>
      <el-table-column label="视频" min-width="150">
        <template #default="{ row }">
          {{ row.coursewareCount }}个 / {{ formatDuration(row.totalVideoDurationSeconds) }}
        </template>
      </el-table-column>
      <el-table-column label="进度上报" min-width="100">
        <template #default="{ row }">{{ row.progressReportIntervalSeconds }}秒</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="250">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/admin/courses/${row.id}`)">
            {{ row.status === 'ENABLED' ? '预览' : '管理' }}
          </el-button>
          <PermissionButton permission="admin:course:status" link @click="toggleStatus(row)">
            {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
          </PermissionButton>
          <PermissionButton
            v-if="row.status === 'DRAFT'"
            permission="admin:course:delete"
            link
            type="danger"
            @click="remove(row)"
          >
            删除
          </PermissionButton>
        </template>
      </el-table-column>
    </AppTable>

    <AppDialog v-model="dialogVisible" title="新建课程" :loading="saving" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="课程简介">
          <el-input v-model="form.description" maxlength="1000" rows="3" type="textarea" />
        </el-form-item>
        <el-form-item label="规定时长" prop="requiredDurationMinutes">
          <el-input-number v-model="form.requiredDurationMinutes" :min="1" :max="1440" />
          <span class="form-unit">分钟</span>
        </el-form-item>
        <el-form-item label="允许拖动">
          <el-switch v-model="form.allowSeek" />
        </el-form-item>
        <el-form-item label="进度上报">
          <el-input-number
            v-model="form.progressReportIntervalSeconds"
            :min="10"
            :max="30"
          />
          <span class="form-unit">秒</span>
        </el-form-item>
        <el-form-item label="学时误差">
          <el-input-number v-model="form.studyToleranceSeconds" :min="0" :max="300" />
          <span class="form-unit">秒</span>
        </el-form-item>
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.form-unit {
  margin-left: 8px;
  color: #71809a;
}
</style>
