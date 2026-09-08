<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getVehicles,
  getVehicleDepartments,
  createVehicle,
  updateVehicle,
  changeVehicleStatus,
  type Vehicle,
} from '@/api/vehicles'
import { usePermissionStore } from '@/stores/permission'
import AppTable from '@/components/AppTable/AppTable.vue'
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import { recordTime } from '@/utils/recordDisplay'

const permission = usePermissionStore()
const rows = ref<Vehicle[]>([])
const departments = ref<Array<{ id: string; name: string }>>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const editingId = ref('')
const query = reactive({ pageNumber: 1, pageSize: 10, keyword: '', status: '', orgId: '' })
const form = reactive({ plateNumber: '', vehicleType: '', orgId: '', remark: '' })
let version = 0

/** 分页读取车辆，筛选变化时丢弃旧响应。 */
async function load() {
  const current = ++version
  loading.value = true
  try {
    const page = await getVehicles({ ...query, orgId: query.orgId || undefined })
    if (current !== version) return
    rows.value = page.records
    total.value = page.total
  } catch (reason) {
    if (current === version) showError(reason)
  } finally {
    if (current === version) loading.value = false
  }
}
/** 获取车辆功能自身授权的部门选项。 */
async function loadDepartments() {
  try {
    departments.value = await getVehicleDepartments()
  } catch (reason) {
    showError(reason)
  }
}
/** 按条件从第一页查询。 */
function search() {
  query.pageNumber = 1
  void load()
}
/** 切换分页。 */
function changePage(value: number) {
  query.pageNumber = value
  void load()
}
/** 调整每页数量。 */
function changeSize(value: number) {
  query.pageSize = value
  search()
}
/** 初始化新增或编辑表单。 */
function openForm(row?: Vehicle) {
  editingId.value = row?.id || ''
  Object.assign(form, {
    plateNumber: row?.plateNumber || '',
    vehicleType: row?.vehicleType || '',
    orgId: row?.orgId || '',
    remark: row?.remark || '',
  })
  dialog.value = true
  void loadDepartments()
}
/** 校验必填信息后保存，保留失败时的编辑内容。 */
async function save() {
  if (!form.plateNumber.trim() || !form.vehicleType.trim()) {
    ElMessage.warning('请填写车牌号和车辆类型')
    return
  }
  saving.value = true
  try {
    const data = { ...form, orgId: form.orgId || undefined }
    if (editingId.value) await updateVehicle(editingId.value, data)
    else await createVehicle(data)
    dialog.value = false
    ElMessage.success('车辆已保存')
    await load()
  } catch (reason) {
    showError(reason)
  } finally {
    saving.value = false
  }
}
/** 确认后切换启停状态。 */
async function toggleStatus(row: Vehicle) {
  const status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(
      `确定${status === 'ENABLED' ? '启用' : '停用'}车辆 ${row.plateNumber} 吗？`,
      '车辆状态',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await changeVehicleStatus(row.id, status)
    ElMessage.success('车辆状态已更新')
    await load()
  } catch (reason) {
    showError(reason)
  }
}
/** 展示业务校验和请求失败信息。 */
function showError(reason: unknown) {
  ElMessage.error(reason instanceof Error ? reason.message : '车辆操作失败')
}
onMounted(() => {
  void load()
  void loadDepartments()
})
</script>

<template>
  <section>
    <header class="page-title">
      <h1>车辆管理</h1>
      <p>维护本企业车辆基础信息及所属部门。</p>
    </header>
    <AppTable
      :data="rows"
      :total="total"
      :loading="loading"
      :page-number="query.pageNumber"
      :page-size="query.pageSize"
      @page-change="changePage"
      @size-change="changeSize"
    >
      <template #search>
        <AppFilterField label="车牌号"
          ><el-input v-model="query.keyword" clearable @keyup.enter="search"
        /></AppFilterField>
        <AppFilterField label="所属部门"
          ><el-select v-model="query.orgId" clearable placeholder="全部"
            ><el-option
              v-for="dept in departments"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id" /></el-select
        ></AppFilterField>
        <AppFilterField label="状态"
          ><el-select v-model="query.status" clearable placeholder="全部"
            ><el-option label="启用" value="ENABLED" /><el-option
              label="停用"
              value="DISABLED" /></el-select
        ></AppFilterField>
        <div class="app-filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </template>
      <template #actions
        ><el-button v-if="permission.has('admin:vehicle:create')" type="primary" @click="openForm()"
          >新增车辆</el-button
        ></template
      >
      <el-table-column label="车牌号" prop="plateNumber" min-width="140" />
      <el-table-column label="车辆类型" prop="vehicleType" min-width="130" />
      <el-table-column label="所属部门" min-width="140"
        ><template #default="{ row }">{{ row.orgName || '未分配' }}</template></el-table-column
      >
      <el-table-column label="状态" width="90"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{
            row.status === 'ENABLED' ? '启用' : '停用'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
      <el-table-column label="创建时间" min-width="180"
        ><template #default="{ row }">{{ recordTime(row.createdAt) }}</template></el-table-column
      >
      <el-table-column label="操作" fixed="right" width="135"
        ><template #default="{ row }">
          <el-button
            v-if="permission.has('admin:vehicle:update')"
            link
            type="primary"
            @click="openForm(row)"
            >编辑</el-button
          >
          <el-button
            v-if="permission.has('admin:vehicle:status')"
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'primary'"
            @click="toggleStatus(row)"
            >{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button
          >
        </template></el-table-column
      >
    </AppTable>
    <el-dialog
      v-model="dialog"
      :title="editingId ? '编辑车辆' : '新增车辆'"
      width="min(560px, 95vw)"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px" @submit.prevent="save">
        <el-form-item label="车牌号" required
          ><el-input v-model="form.plateNumber" maxlength="16"
        /></el-form-item>
        <el-form-item label="车辆类型" required
          ><el-input v-model="form.vehicleType" maxlength="64" placeholder="如：重型货车、客车"
        /></el-form-item>
        <el-form-item label="所属部门"
          ><el-select v-model="form.orgId" clearable placeholder="未分配"
            ><el-option
              v-for="dept in departments"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id" /></el-select
        ></el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="form.remark" type="textarea" maxlength="255" show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      >
    </el-dialog>
  </section>
</template>
