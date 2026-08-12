<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  assignUserRoles,
  changeUserStatus,
  createUser,
  getOrgTree,
  getRoleOptions,
  getUsers,
  resetUserPassword,
  updateUser,
  type OrgNode,
  type Role,
  type Status,
  type User,
  type UserPayload,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { isValidPassword, PASSWORD_RULE_MESSAGE, validatePassword } from '@/utils/validation'

const loading = ref(false)
const saving = ref(false)
const rows = ref<User[]>([])
const total = ref(0)
const orgTree = ref<OrgNode[]>([])
const roles = ref<Role[]>([])
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  orgId: '',
  status: '' as Status | '',
})

const dialogVisible = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInstance>()
const emptyUser = (): UserPayload => ({
  username: '',
  displayName: '',
  phone: '',
  orgId: '',
  temporaryPassword: '',
  roleIds: [],
})
const form = reactive<UserPayload>(emptyUser())
/** 校验新增用户必选初始角色，编辑资料时不参与验证。 */
function validateInitialRoles(_rule: unknown, value: string[], callback: (error?: Error) => void) {
  if (!editingId.value && !value.length) {
    callback(new Error('请选择至少一个初始角色'))
    return
  }
  callback()
}

/** 校验新增用户的临时密码，编辑资料时不参与验证。 */
function validateCreatePassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (editingId.value) {
    callback()
    return
  }
  validatePassword(_rule, value, callback)
}

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  roleIds: [{ validator: validateInitialRoles, trigger: 'change' }],
  temporaryPassword: [{ validator: validateCreatePassword, trigger: 'blur' }],
}

const resetVisible = ref(false)
const resetUser = ref<User>()
const temporaryPassword = ref('')
const roleVisible = ref(false)
const roleUser = ref<User>()
const selectedRoleIds = ref<string[]>([])

async function loadBaseData() {
  const [orgResult, roleResult] = await Promise.all([getOrgTree(), getRoleOptions()])
  orgTree.value = orgResult
  roles.value = roleResult
}

async function load() {
  loading.value = true
  try {
    const result = await getUsers({
      ...query,
      orgId: query.orgId || undefined,
    })
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
  Object.assign(form, emptyUser(), {
    orgId: orgTree.value[0]?.id || '',
    roleIds: roles.value.filter((role) => role.code === 'STUDENT').map((role) => role.id),
  })
  dialogVisible.value = true
}

function openEdit(row: User) {
  editingId.value = row.id
  Object.assign(form, emptyUser(), {
    username: row.username,
    displayName: row.displayName,
    phone: row.phone,
    orgId: row.orgId,
    roleIds: row.roleIds,
  })
  dialogVisible.value = true
}

/** 保存新增或编辑的组织用户。 */
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        displayName: form.displayName,
        phone: form.phone,
        orgId: form.orgId,
      })
    } else {
      await createUser(form)
    }
    ElMessage.success(editingId.value ? '用户信息已更新' : '用户创建成功')
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: User) {
  const status: Status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${status === 'ENABLED' ? '启用' : '禁用'}用户“${row.displayName}”吗？`,
    '状态确认',
    { type: 'warning' },
  )
  try {
    await changeUserStatus(row.id, status)
    ElMessage.success('用户状态已更新')
    await load()
  } catch (error) {
    showError(error)
  }
}

function openReset(row: User) {
  resetUser.value = row
  temporaryPassword.value = ''
  resetVisible.value = true
}

async function resetPassword() {
  if (!resetUser.value || !isValidPassword(temporaryPassword.value)) {
    ElMessage.warning(PASSWORD_RULE_MESSAGE)
    return
  }
  saving.value = true
  try {
    await resetUserPassword(resetUser.value.id, temporaryPassword.value)
    ElMessage.success('密码已重置，用户下次登录需修改密码')
    resetVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

function openRoles(row: User) {
  roleUser.value = row
  selectedRoleIds.value = [...row.roleIds]
  roleVisible.value = true
}

async function saveRoles() {
  if (!roleUser.value) {
    return
  }
  saving.value = true
  try {
    await assignUserRoles(roleUser.value.id, selectedRoleIds.value)
    ElMessage.success('用户角色已更新')
    roleVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 切换用户列表页码。 */
function changePage(pageNumber: number) {
  query.pageNumber = pageNumber
  void load()
}

/** 切换用户列表每页数量并返回第一页。 */
function changePageSize(pageSize: number) {
  query.pageSize = pageSize
  query.pageNumber = 1
  void load()
}

/** 从第一页查询用户列表。 */
function search() {
  query.pageNumber = 1
  void load()
}

function resetSearch() {
  Object.assign(query, { keyword: '', orgId: '', status: '', pageNumber: 1 })
  void load()
}

function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(async () => {
  try {
    await loadBaseData()
  } catch (error) {
    showError(error)
  }
  await load()
})
</script>

<template>
  <section>
    <header class="page-title">
      <h1>用户管理</h1>
      <p>维护本组织账号、所属部门、状态和角色。</p>
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
          placeholder="用户名/姓名/手机号"
          @keyup.enter="load"
        />
        <el-tree-select
          v-model="query.orgId"
          :data="orgTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          check-strictly
          clearable
          placeholder="全部部门"
        />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </template>
      <template #actions>
        <PermissionButton permission="admin:user:create" type="primary" @click="openCreate">
          新建用户
        </PermissionButton>
      </template>
      <el-table-column label="用户名" min-width="130" prop="username" />
      <el-table-column label="姓名" min-width="110" prop="displayName" />
      <el-table-column label="部门" min-width="130" prop="orgName" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">{{ row.roleNames.join('、') || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="300">
        <template #default="{ row }">
          <PermissionButton
            permission="admin:user:update"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </PermissionButton>
          <PermissionButton permission="admin:user:assign-role" link @click="openRoles(row)">
            角色
          </PermissionButton>
          <PermissionButton permission="admin:user:reset-password" link @click="openReset(row)">
            重置密码
          </PermissionButton>
          <PermissionButton permission="admin:user:status" link @click="toggleStatus(row)">
            {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
          </PermissionButton>
        </template>
      </el-table-column>
    </AppTable>

    <AppDialog
      v-model="dialogVisible"
      :loading="saving"
      :title="editingId ? '编辑用户' : '新建用户'"
      @confirm="save"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" :disabled="Boolean(editingId)" />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model.trim="form.displayName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="form.phone" />
        </el-form-item>
        <el-form-item label="所属部门" prop="orgId">
          <el-tree-select
            v-model="form.orgId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            default-expand-all
          />
        </el-form-item>
        <template v-if="!editingId">
          <el-form-item label="初始角色" prop="roleIds">
            <el-select v-model="form.roleIds" multiple>
              <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="临时密码" prop="temporaryPassword">
            <el-input v-model="form.temporaryPassword" show-password type="password" />
          </el-form-item>
        </template>
      </el-form>
    </AppDialog>

    <AppDialog v-model="resetVisible" :loading="saving" title="重置密码" @confirm="resetPassword">
      <el-alert
        :closable="false"
        :title="`将重置 ${resetUser?.displayName || ''} 的密码，并使其所有旧会话失效。`"
        type="warning"
      />
      <el-form label-position="top">
        <el-form-item label="临时密码">
          <el-input v-model="temporaryPassword" show-password type="password" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model="roleVisible" :loading="saving" title="分配角色" @confirm="saveRoles">
      <el-checkbox-group v-model="selectedRoleIds" class="role-options">
        <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">
          {{ role.name }}
        </el-checkbox>
      </el-checkbox-group>
    </AppDialog>
  </section>
</template>

<style scoped>
.role-options {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.el-alert {
  margin-bottom: 20px;
}
</style>
