<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  ElTree,
  type FormInstance,
  type FormRules,
} from 'element-plus'

import {
  assignRolePermissions,
  changeRoleStatus,
  createRole,
  deleteRole,
  getPermissionTree,
  getRoles,
  updateRole,
  type PermissionNode,
  type Role,
  type Status,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Role[]>([])
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
const form = reactive({
  code: '',
  name: '',
  description: '',
})
const rules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

const permissionVisible = ref(false)
const permissionRole = ref<Role>()
const permissionTree = ref<PermissionNode[]>([])
const permissionTreeRef = ref<InstanceType<typeof ElTree>>()

async function load() {
  loading.value = true
  try {
    const result = await getRoles(query)
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
  Object.assign(form, { code: '', name: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row: Role) {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, description: row.description || '' })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateRole(editingId.value, {
        name: form.name,
        description: form.description,
      })
    } else {
      await createRole(form)
    }
    ElMessage.success(editingId.value ? '角色已更新' : '角色已创建')
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: Role) {
  const status: Status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${status === 'ENABLED' ? '启用' : '禁用'}角色“${row.name}”吗？`,
    '状态确认',
    { type: 'warning' },
  )
  try {
    await changeRoleStatus(row.id, status)
    ElMessage.success('角色状态已更新')
    await load()
  } catch (error) {
    showError(error)
  }
}

async function remove(row: Role) {
  await ElMessageBox.confirm(`确定删除角色“${row.name}”吗？`, '删除确认', { type: 'warning' })
  try {
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    await load()
  } catch (error) {
    showError(error)
  }
}

async function openPermissions(row: Role) {
  permissionRole.value = row
  if (!permissionTree.value.length) {
    permissionTree.value = await getPermissionTree()
  }
  permissionVisible.value = true
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(row.permissionIds, false)
}

async function savePermissions() {
  if (!permissionRole.value) {
    return
  }
  saving.value = true
  try {
    const permissionIds = permissionTreeRef.value?.getCheckedKeys(false).map(String) ?? []
    await assignRolePermissions(permissionRole.value.id, permissionIds)
    ElMessage.success('角色权限已更新')
    permissionVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-title">
      <h1>角色管理</h1>
      <p>创建企业自定义角色并分配菜单和操作权限，内置角色不可修改。</p>
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
        <el-input v-model="query.keyword" clearable placeholder="角色名称/编码" @keyup.enter="load" />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="query.pageNumber = 1; load()">查询</el-button>
      </template>
      <template #actions>
        <PermissionButton permission="admin:role:create" type="primary" @click="openCreate">
          新建角色
        </PermissionButton>
      </template>
      <el-table-column label="角色编码" min-width="150" prop="code" />
      <el-table-column label="角色名称" min-width="130" prop="name" />
      <el-table-column label="说明" min-width="180" prop="description" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.builtIn ? 'warning' : ''">{{ row.builtIn ? '内置' : '自定义' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="260">
        <template #default="{ row }">
          <template v-if="!row.builtIn">
            <PermissionButton permission="admin:role:update" link type="primary" @click="openEdit(row)">
              编辑
            </PermissionButton>
            <PermissionButton permission="admin:role:assign-permission" link @click="openPermissions(row)">
              授权
            </PermissionButton>
            <PermissionButton permission="admin:role:status" link @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
            </PermissionButton>
            <PermissionButton permission="admin:role:delete" link type="danger" @click="remove(row)">
              删除
            </PermissionButton>
          </template>
          <span v-else class="muted">系统维护</span>
        </template>
      </el-table-column>
    </AppTable>

    <AppDialog
      v-model="dialogVisible"
      :loading="saving"
      :title="editingId ? '编辑角色' : '新建角色'"
      @confirm="save"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model.trim="form.code" :disabled="Boolean(editingId)" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model.trim="form.name" />
        </el-form-item>
        <el-form-item label="角色说明">
          <el-input v-model.trim="form.description" :rows="3" type="textarea" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog
      v-model="permissionVisible"
      :loading="saving"
      title="分配角色权限"
      width="620px"
      @confirm="savePermissions"
    >
      <el-alert
        :closable="false"
        title="仅可授予当前账号自身拥有的企业级权限。"
        type="info"
      />
      <el-tree
        ref="permissionTreeRef"
        :data="permissionTree"
        :props="{ label: 'name', children: 'children' }"
        check-strictly
        default-expand-all
        node-key="id"
        show-checkbox
      >
        <template #default="{ data }">
          <span>{{ data.name }} <small>{{ data.code }}</small></span>
        </template>
      </el-tree>
    </AppDialog>
  </section>
</template>

<style scoped>
.muted,
small {
  color: #8a96a8;
}

.el-alert {
  margin-bottom: 16px;
}
</style>
