<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  changeEnterpriseStatus,
  createEnterprise,
  getEnterpriseAdministrators,
  getEnterprises,
  resetEnterpriseAdministratorPassword,
  updateEnterprise,
  type Enterprise,
  type EnterpriseAdministrator,
  type EnterprisePayload,
  type Status,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { isValidPassword, PASSWORD_RULE_MESSAGE, validatePassword } from '@/utils/validation'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Enterprise[]>([])
const total = ref(0)
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  status: '' as Status | '',
})
const dialogVisible = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInstance>()
const emptyForm = (): EnterprisePayload => ({
  code: '',
  name: '',
  contactName: '',
  contactPhone: '',
  address: '',
  adminUsername: '',
  adminDisplayName: '',
  adminPhone: '',
  temporaryPassword: '',
})
const form = reactive<EnterprisePayload>(emptyForm())
const rules: FormRules = {
  code: [{ required: true, message: '请输入企业编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '请输入管理员用户名', trigger: 'blur' }],
  adminDisplayName: [{ required: true, message: '请输入管理员姓名', trigger: 'blur' }],
  temporaryPassword: [{ validator: validatePassword, trigger: 'blur' }],
}
const administratorVisible = ref(false)
const administratorLoading = ref(false)
const selectedEnterprise = ref<Enterprise>()
const administrators = ref<EnterpriseAdministrator[]>([])
const resetVisible = ref(false)
const resetAdministrator = ref<EnterpriseAdministrator>()
const temporaryPassword = ref('')

/** 加载当前企业的管理员账号。 */
async function loadAdministrators() {
  if (!selectedEnterprise.value) {
    return
  }
  administratorLoading.value = true
  try {
    administrators.value = await getEnterpriseAdministrators(selectedEnterprise.value.id)
  } catch (error) {
    showError(error)
  } finally {
    administratorLoading.value = false
  }
}

/** 打开企业管理员账号列表。 */
async function openAdministrators(row: Enterprise) {
  selectedEnterprise.value = row
  administrators.value = []
  administratorVisible.value = true
  await loadAdministrators()
}

/** 打开管理员密码重置窗口。 */
function openAdministratorReset(row: EnterpriseAdministrator) {
  resetAdministrator.value = row
  temporaryPassword.value = ''
  resetVisible.value = true
}

/** 重置企业管理员密码并使旧会话失效。 */
async function resetAdministratorPassword() {
  if (
    !selectedEnterprise.value ||
    !resetAdministrator.value ||
    !isValidPassword(temporaryPassword.value)
  ) {
    ElMessage.warning(PASSWORD_RULE_MESSAGE)
    return
  }
  saving.value = true
  try {
    await resetEnterpriseAdministratorPassword(
      selectedEnterprise.value.id,
      resetAdministrator.value.id,
      temporaryPassword.value,
    )
    ElMessage.success('密码已重置，管理员下次登录需修改密码')
    resetVisible.value = false
    await loadAdministrators()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const result = await getEnterprises(query)
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: Enterprise) {
  editingId.value = row.id
  Object.assign(form, emptyForm(), {
    code: row.code,
    name: row.name,
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    address: row.address,
  })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateEnterprise(editingId.value, {
        name: form.name,
        contactName: form.contactName,
        contactPhone: form.contactPhone,
        address: form.address,
      })
    } else {
      await createEnterprise(form)
    }
    ElMessage.success(editingId.value ? '企业信息已更新' : '企业创建成功')
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: Enterprise) {
  const nextStatus: Status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${nextStatus === 'ENABLED' ? '启用' : '禁用'}企业“${row.name}”吗？`,
    '状态确认',
    { type: 'warning' },
  )
  try {
    await changeEnterpriseStatus(row.id, nextStatus)
    ElMessage.success('企业状态已更新')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 切换企业列表页码。 */
function changePage(pageNumber: number) {
  query.pageNumber = pageNumber
  void load()
}

/** 切换企业列表每页数量并返回第一页。 */
function changePageSize(pageSize: number) {
  query.pageSize = pageSize
  query.pageNumber = 1
  void load()
}

/** 从第一页查询企业列表。 */
function search() {
  query.pageNumber = 1
  void load()
}

function resetSearch() {
  query.keyword = ''
  query.status = ''
  query.pageNumber = 1
  void load()
}

function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-title">
      <div>
        <h1>企业管理</h1>
        <p>创建运输企业并初始化企业管理员账号。</p>
      </div>
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
          placeholder="企业名称/编码"
          @keyup.enter="load"
        />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </template>
      <template #actions>
        <PermissionButton permission="admin:enterprise:create" type="primary" @click="openCreate">
          新建企业
        </PermissionButton>
      </template>
      <el-table-column label="企业编码" min-width="130" prop="code" />
      <el-table-column label="企业名称" min-width="180" prop="name" />
      <el-table-column label="联系人" min-width="110" prop="contactName" />
      <el-table-column label="联系电话" min-width="130" prop="contactPhone" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="250">
        <template #default="{ row }">
          <PermissionButton
            permission="admin:enterprise:view"
            link
            @click="openAdministrators(row)"
          >
            管理员账号
          </PermissionButton>
          <PermissionButton
            permission="admin:enterprise:update"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </PermissionButton>
          <PermissionButton permission="admin:enterprise:status" link @click="toggleStatus(row)">
            {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
          </PermissionButton>
        </template>
      </el-table-column>
    </AppTable>

    <AppDialog
      v-model="dialogVisible"
      :loading="saving"
      :title="editingId ? '编辑企业' : '新建企业'"
      width="680px"
      @confirm="save"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="企业编码" prop="code">
              <el-input v-model.trim="form.code" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="企业名称" prop="name">
              <el-input v-model.trim="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model.trim="form.contactName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"
              ><el-input v-model.trim="form.contactPhone"
            /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="企业地址"><el-input v-model.trim="form.address" /></el-form-item>
          </el-col>
          <template v-if="!editingId">
            <el-col :span="12">
              <el-form-item label="管理员账号" prop="adminUsername">
                <el-input v-model.trim="form.adminUsername" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="管理员姓名" prop="adminDisplayName">
                <el-input v-model.trim="form.adminDisplayName" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="管理员手机"
                ><el-input v-model.trim="form.adminPhone"
              /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="临时密码" prop="temporaryPassword">
                <el-input v-model="form.temporaryPassword" show-password type="password" />
              </el-form-item>
            </el-col>
          </template>
        </el-row>
      </el-form>
    </AppDialog>

    <el-dialog
      v-model="administratorVisible"
      :title="`${selectedEnterprise?.name || ''} · 管理员账号`"
      destroy-on-close
      width="760px"
    >
      <el-table v-loading="administratorLoading" :data="administrators">
        <el-table-column label="登录账号" min-width="150" prop="username" />
        <el-table-column label="管理员姓名" min-width="120" prop="displayName" />
        <el-table-column label="手机号" min-width="130" prop="phone" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <PermissionButton
              permission="admin:enterprise:update"
              link
              type="primary"
              @click="openAdministratorReset(row)"
            >
              重置密码
            </PermissionButton>
          </template>
        </el-table-column>
        <template #empty>该企业暂无管理员账号</template>
      </el-table>
      <template #footer>
        <el-button @click="administratorVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <AppDialog
      v-model="resetVisible"
      :loading="saving"
      title="重置管理员密码"
      @confirm="resetAdministratorPassword"
    >
      <el-alert
        :closable="false"
        :title="`将重置账号 ${resetAdministrator?.username || ''} 的密码，并使其所有旧会话失效。`"
        type="warning"
      />
      <el-form label-position="top">
        <el-form-item label="临时密码">
          <el-input v-model="temporaryPassword" show-password type="password" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.el-alert {
  margin-bottom: 20px;
}
</style>
