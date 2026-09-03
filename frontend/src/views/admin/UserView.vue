<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  assignUserRoles,
  changeUserStatus,
  completeFaceReferenceUploadSession,
  createFaceReferenceUploadSession,
  createUser,
  deleteFaceReference,
  getFaceReferencePreviewUrl,
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
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { usePermissionStore } from '@/stores/permission'
import { uploadSignedBlob } from '@/utils/ossUpload'
import { isValidPassword, PASSWORD_RULE_MESSAGE, validatePassword } from '@/utils/validation'

const loading = ref(false)
const permissionStore = usePermissionStore()
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
const faceVisible = ref(false)
const faceUser = ref<User>()
const faceFile = ref<File>()
const facePreviewUrl = ref('')
const facePreviewLoading = ref(false)

/** 判断当前账号是否可维护登记照。 */
function canManageFaceReference() {
  return permissionStore.has('admin:face-check:manage')
}

/** 打开登记照弹窗，并清理上一次选择和短期预览。 */
function openFaceReference(row: User) {
  faceUser.value = row
  faceFile.value = undefined
  facePreviewUrl.value = ''
  faceVisible.value = true
}

/** 选择并校验用于登记的人脸图片。 */
function selectFaceFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    ElMessage.warning('登记照仅支持 JPG 或 PNG 格式')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('登记照不能超过 5MB')
    return
  }
  faceFile.value = file
}

/** 通过短期签名直传登记照并完成用户绑定。 */
async function saveFaceReference() {
  const user = faceUser.value
  const file = faceFile.value
  if (!user || !file || !canManageFaceReference()) {
    ElMessage.warning(file ? '当前账号无登记照维护权限' : '请先选择登记照')
    return
  }
  saving.value = true
  try {
    const uploadSession = await createFaceReferenceUploadSession(user.id, {
      originalFilename: file.name,
      contentType: file.type,
      fileSizeBytes: file.size,
      clientLastModified: file.lastModified,
    })
    await uploadSignedBlob(uploadSession.uploadRequest, file)
    const reference = await completeFaceReferenceUploadSession(user.id, uploadSession.id)
    user.faceReferenceEnrolled = reference.enrolled
    user.faceReferenceUpdatedAt = reference.updatedAt
    faceFile.value = undefined
    facePreviewUrl.value = ''
    ElMessage.success('登记照已更新')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 获取并展示一次性短期登记照预览地址。 */
async function previewFaceReference() {
  if (!faceUser.value?.faceReferenceEnrolled) return
  facePreviewLoading.value = true
  try {
    facePreviewUrl.value = (await getFaceReferencePreviewUrl(faceUser.value.id)).url
  } catch (error) {
    showError(error)
  } finally {
    facePreviewLoading.value = false
  }
}

/** 经确认后删除用户当前登记照。 */
async function removeFaceReference() {
  const user = faceUser.value
  if (!user || !canManageFaceReference()) return
  await ElMessageBox.confirm(`确定删除“${user.displayName}”的登记照吗？`, '删除确认', {
    type: 'warning',
  })
  saving.value = true
  try {
    await deleteFaceReference(user.id)
    user.faceReferenceEnrolled = false
    user.faceReferenceUpdatedAt = undefined
    facePreviewUrl.value = ''
    faceFile.value = undefined
    ElMessage.success('登记照已删除')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

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
        <AppFilterField label="用户关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="用户名 / 姓名 / 手机号"
            @keyup.enter="load"
          />
        </AppFilterField>
        <AppFilterField label="所属部门">
          <el-tree-select
            v-model="query.orgId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="全部"
          />
        </AppFilterField>
        <AppFilterField label="用户状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </AppFilterField>
        <div class="app-filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </div>
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
      <el-table-column label="人脸登记" min-width="150">
        <template #default="{ row }">
          <el-tag :type="row.faceReferenceEnrolled ? 'success' : 'info'">
            {{ row.faceReferenceEnrolled ? '已登记' : '未登记' }}
          </el-tag>
          <small v-if="row.faceReferenceUpdatedAt" class="face-updated-at">
            {{ new Date(row.faceReferenceUpdatedAt).toLocaleString('zh-CN', { hour12: false }) }}
          </small>
        </template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="360">
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
          <el-button
            v-if="
              permissionStore.has('admin:face-check:view') ||
              permissionStore.has('admin:face-check:manage')
            "
            link
            type="primary"
            @click="openFaceReference(row)"
          >
            登记照
          </el-button>
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

    <el-dialog
      v-model="faceVisible"
      :close-on-click-modal="!saving"
      :title="`${faceUser?.displayName || ''} · 人脸登记照`"
      destroy-on-close
      width="560px"
    >
      <el-alert
        :closable="false"
        :title="
          faceUser?.faceReferenceEnrolled
            ? `已登记${faceUser.faceReferenceUpdatedAt ? `，更新时间：${new Date(faceUser.faceReferenceUpdatedAt).toLocaleString('zh-CN', { hour12: false })}` : ''}`
            : '当前用户尚未登记人脸照片'
        "
        :type="faceUser?.faceReferenceEnrolled ? 'success' : 'warning'"
      />
      <div v-loading="facePreviewLoading" class="face-preview">
        <img v-if="facePreviewUrl" :src="facePreviewUrl" alt="用户登记照预览" />
        <el-empty v-else description="登记照默认不加载，请按需获取短期预览" :image-size="80" />
      </div>
      <div class="face-actions">
        <el-button
          v-if="faceUser?.faceReferenceEnrolled"
          :loading="facePreviewLoading"
          @click="previewFaceReference"
        >
          获取预览
        </el-button>
        <label v-if="canManageFaceReference()" class="face-file-button">
          <input accept="image/jpeg,image/png" type="file" @change="selectFaceFile" />
          <span>{{
            faceFile ? '重新选择' : faceUser?.faceReferenceEnrolled ? '选择替换照片' : '选择照片'
          }}</span>
        </label>
        <el-button
          v-if="canManageFaceReference() && faceUser?.faceReferenceEnrolled"
          type="danger"
          plain
          :disabled="saving"
          @click="removeFaceReference"
        >
          删除登记照
        </el-button>
      </div>
      <p v-if="faceFile" class="face-file-name">待上传：{{ faceFile.name }}</p>
      <template #footer>
        <el-button :disabled="saving" @click="faceVisible = false">关闭</el-button>
        <el-button
          v-if="canManageFaceReference()"
          type="primary"
          :loading="saving"
          :disabled="!faceFile"
          @click="saveFaceReference"
        >
          {{ faceUser?.faceReferenceEnrolled ? '上传并替换' : '上传并登记' }}
        </el-button>
      </template>
    </el-dialog>
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

.face-updated-at {
  display: block;
  margin-top: 5px;
  color: #8792a6;
}

.face-preview {
  display: grid;
  min-height: 260px;
  margin: 16px 0;
  overflow: hidden;
  background: #f6f8fb;
  border-radius: 8px;
  place-items: center;
}

.face-preview img {
  width: 100%;
  max-height: 360px;
  object-fit: contain;
}

.face-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.face-file-button {
  padding: 8px 15px;
  color: #155eef;
  border: 1px solid #b7cffb;
  border-radius: 4px;
  cursor: pointer;
}

.face-file-button input {
  display: none;
}

.face-file-name {
  color: #6f7c93;
}
</style>
