<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  changeEnterpriseStatus,
  createEnterprise,
  getEnterprises,
  updateEnterprise,
  type Enterprise,
  type EnterprisePayload,
  type Status,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { validatePassword } from '@/utils/validation'

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
      @page-change="query.pageNumber = $event; load()"
      @size-change="query.pageSize = $event; query.pageNumber = 1; load()"
    >
      <template #search>
        <el-input v-model="query.keyword" clearable placeholder="企业名称/编码" @keyup.enter="load" />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="query.pageNumber = 1; load()">查询</el-button>
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
      <el-table-column fixed="right" label="操作" width="180">
        <template #default="{ row }">
          <PermissionButton permission="admin:enterprise:update" link type="primary" @click="openEdit(row)">
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
            <el-form-item label="联系电话"><el-input v-model.trim="form.contactPhone" /></el-form-item>
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
              <el-form-item label="管理员手机"><el-input v-model.trim="form.adminPhone" /></el-form-item>
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
  </section>
</template>
