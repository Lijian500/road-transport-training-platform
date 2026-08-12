<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  changeEnterpriseStatus,
  createEnterprise,
  getAddressChildren,
  getEnterpriseAdministrators,
  getEnterprises,
  resetEnterpriseAdministratorPassword,
  updateEnterprise,
  type AddressNode,
  type Enterprise,
  type EnterpriseAdministrator,
  type EnterprisePayload,
  type OrganizationNature,
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
const addressLoading = ref(false)
const rows = ref<Enterprise[]>([])
const total = ref(0)
const query = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  status: '' as Status | '',
  organizationNature: '' as OrganizationNature | '',
})
const dialogVisible = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInstance>()
const provinces = ref<AddressNode[]>([])
const cities = ref<AddressNode[]>([])
const districts = ref<AddressNode[]>([])
const provinceId = ref('')
const cityId = ref('')
const districtId = ref('')

/** 创建组织表单的初始值。 */
const emptyForm = (): EnterprisePayload => ({
  code: '',
  name: '',
  organizationNature: 'ENTERPRISE',
  areaId: '',
  contactName: '',
  contactPhone: '',
  address: '',
  adminUsername: '',
  adminDisplayName: '',
  adminPhone: '',
  temporaryPassword: '',
})
const form = reactive<EnterprisePayload>(emptyForm())
/** 校验新增组织必填字段，编辑时忽略仅用于初始化管理员的字段。 */
function validateCreateOnlyField(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!editingId.value && !value) {
    callback(new Error('请填写管理员初始化信息'))
    return
  }
  callback()
}

/** 校验新增组织的临时密码，编辑时不参与验证。 */
function validateCreatePassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (editingId.value) {
    callback()
    return
  }
  validatePassword(_rule, value, callback)
}

const rules: FormRules = {
  code: [{ required: true, message: '请输入组织编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  organizationNature: [{ required: true, message: '请选择组织类型', trigger: 'change' }],
  areaId: [{ required: true, message: '请选择符合组织类型要求的行政区域', trigger: 'change' }],
  adminUsername: [{ validator: validateCreateOnlyField, trigger: 'blur' }],
  adminDisplayName: [{ validator: validateCreateOnlyField, trigger: 'blur' }],
  temporaryPassword: [{ validator: validateCreatePassword, trigger: 'blur' }],
}
const administratorVisible = ref(false)
const administratorLoading = ref(false)
const selectedEnterprise = ref<Enterprise>()
const administrators = ref<EnterpriseAdministrator[]>([])
const resetVisible = ref(false)
const resetAdministrator = ref<EnterpriseAdministrator>()
const temporaryPassword = ref('')

/** 加载当前组织的管理员账号。 */
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

/** 打开组织管理员账号列表。 */
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

/** 重置组织管理员密码并使旧会话失效。 */
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

/** 加载组织分页列表。 */
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

/** 首次按需加载省级地址选项。 */
async function ensureProvinces() {
  if (!provinces.value.length) {
    provinces.value = await getAddressChildren()
  }
}

/** 清空表单中的行政区域选择和下级缓存。 */
function clearAreaSelection() {
  provinceId.value = ''
  cityId.value = ''
  districtId.value = ''
  cities.value = []
  districts.value = []
  form.areaId = ''
  formRef.value?.clearValidate('areaId')
}

/** 切换组织类型后仅在原选择不符合新规则时清理辖区。 */
function handleNatureChange() {
  if (form.organizationNature === 'ENTERPRISE' && !districtId.value) {
    clearAreaSelection()
    return
  }
  handleDistrictChange(districtId.value)
}

/** 选择省份并懒加载直属市级地址。 */
async function handleProvinceChange(value: string) {
  cityId.value = ''
  districtId.value = ''
  cities.value = []
  districts.value = []
  form.areaId = form.organizationNature === 'REGULATOR' ? value || '' : ''
  const selectedProvince = provinces.value.find((item) => item.id === value)
  if (!selectedProvince) {
    return
  }
  addressLoading.value = true
  try {
    const result = await getAddressChildren(selectedProvince.areaCode)
    if (provinceId.value === value) {
      cities.value = result
    }
  } catch (error) {
    showError(error)
  } finally {
    addressLoading.value = false
  }
}

/** 选择城市并懒加载直属区县地址。 */
async function handleCityChange(value: string) {
  districtId.value = ''
  districts.value = []
  form.areaId = form.organizationNature === 'REGULATOR' ? value || provinceId.value : ''
  const selectedCity = cities.value.find((item) => item.id === value)
  if (!selectedCity) {
    return
  }
  addressLoading.value = true
  try {
    const result = await getAddressChildren(selectedCity.areaCode)
    if (cityId.value === value) {
      districts.value = result
    }
  } catch (error) {
    showError(error)
  } finally {
    addressLoading.value = false
  }
}

/** 选择区县，或在行管场景回退到当前最深的省市节点。 */
function handleDistrictChange(value: string) {
  form.areaId =
    value || (form.organizationNature === 'REGULATOR' ? cityId.value || provinceId.value : '')
}

/** 打开新建组织窗口。 */
async function openCreate() {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  clearAreaSelection()
  dialogVisible.value = true
  try {
    await ensureProvinces()
  } catch (error) {
    showError(error)
  }
}

/** 根据接口返回的地址路径回填省市区选项。 */
async function restoreAreaPath(row: Enterprise) {
  clearAreaSelection()
  await ensureProvinces()
  const [province, city, district] = row.areaPath || []
  provinceId.value = province?.id || ''
  cityId.value = city?.id || ''
  districtId.value = district?.id || ''
  const [cityOptions, districtOptions] = await Promise.all([
    province ? getAddressChildren(province.areaCode) : Promise.resolve([]),
    city ? getAddressChildren(city.areaCode) : Promise.resolve([]),
  ])
  cities.value = cityOptions
  districts.value = districtOptions
  form.areaId = row.areaId || ''
}

/** 打开编辑组织窗口并回显已绑定辖区。 */
async function openEdit(row: Enterprise) {
  editingId.value = row.id
  Object.assign(form, emptyForm(), {
    code: row.code,
    name: row.name,
    organizationNature: row.organizationNature || 'ENTERPRISE',
    areaId: row.areaId || '',
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    address: row.address,
  })
  dialogVisible.value = true
  addressLoading.value = true
  try {
    await restoreAreaPath(row)
  } catch (error) {
    showError(error)
  } finally {
    addressLoading.value = false
  }
}

/** 保存新增或编辑的组织资料。 */
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateEnterprise(editingId.value, {
        name: form.name,
        areaId: form.areaId,
        contactName: form.contactName,
        contactPhone: form.contactPhone,
        address: form.address,
      })
    } else {
      await createEnterprise(form)
    }
    ElMessage.success(editingId.value ? '组织信息已更新' : '组织创建成功')
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 启用或禁用指定组织。 */
async function toggleStatus(row: Enterprise) {
  const nextStatus: Status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${nextStatus === 'ENABLED' ? '启用' : '禁用'}组织“${row.name}”吗？`,
    '状态确认',
    { type: 'warning' },
  )
  try {
    await changeEnterpriseStatus(row.id, nextStatus)
    ElMessage.success('组织状态已更新')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 切换组织列表页码。 */
function changePage(pageNumber: number) {
  query.pageNumber = pageNumber
  void load()
}

/** 切换组织列表每页数量并返回第一页。 */
function changePageSize(pageSize: number) {
  query.pageSize = pageSize
  query.pageNumber = 1
  void load()
}

/** 从第一页查询组织列表。 */
function search() {
  query.pageNumber = 1
  void load()
}

/** 清空组织列表筛选条件。 */
function resetSearch() {
  query.keyword = ''
  query.status = ''
  query.organizationNature = ''
  query.pageNumber = 1
  void load()
}

/** 返回组织性质的中文名称。 */
function natureLabel(nature: OrganizationNature) {
  return nature === 'REGULATOR' ? '行管' : '企业'
}

/** 格式化省市区路径用于列表展示。 */
function formatAreaPath(row: Enterprise) {
  return row.areaPath?.map((item) => item.name).join(' / ') || '-'
}

/** 统一展示接口或网络错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

/** 初始化省级地址选项和组织列表。 */
async function initialize() {
  try {
    await ensureProvinces()
  } catch (error) {
    showError(error)
  }
  await load()
}

onMounted(initialize)
</script>

<template>
  <section>
    <header class="page-title">
      <div>
        <h1>组织管理</h1>
        <p>创建企业或行管组织，并初始化组织管理员账号与行政辖区。</p>
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
          placeholder="组织名称/编码"
          @keyup.enter="search"
        />
        <el-select v-model="query.organizationNature" clearable placeholder="全部类型">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="行管" value="REGULATOR" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </template>
      <template #actions>
        <PermissionButton permission="admin:enterprise:create" type="primary" @click="openCreate">
          新建组织
        </PermissionButton>
      </template>
      <el-table-column label="组织编码" min-width="130" prop="code" />
      <el-table-column label="组织名称" min-width="160" prop="name" />
      <el-table-column label="组织类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.organizationNature === 'REGULATOR' ? 'warning' : ''">
            {{ natureLabel(row.organizationNature) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="行政区域" min-width="190">
        <template #default="{ row }">{{ formatAreaPath(row) }}</template>
      </el-table-column>
      <el-table-column label="详细地址" min-width="180" prop="address" show-overflow-tooltip />
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
      :title="editingId ? '编辑组织' : '新建组织'"
      width="760px"
      @confirm="save"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="组织编码" prop="code">
              <el-input v-model.trim="form.code" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组织名称" prop="name">
              <el-input v-model.trim="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组织类型" prop="organizationNature">
              <el-select
                v-model="form.organizationNature"
                :disabled="Boolean(editingId)"
                @change="handleNatureChange"
              >
                <el-option label="企业" value="ENTERPRISE" />
                <el-option label="行管" value="REGULATOR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="行政区域" prop="areaId">
              <div v-loading="addressLoading" class="area-selectors">
                <el-select
                  v-model="provinceId"
                  clearable
                  placeholder="省"
                  @change="handleProvinceChange"
                >
                  <el-option
                    v-for="item in provinces"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
                <el-select
                  v-model="cityId"
                  :disabled="!provinceId"
                  clearable
                  placeholder="市"
                  @change="handleCityChange"
                >
                  <el-option
                    v-for="item in cities"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
                <el-select
                  v-model="districtId"
                  :disabled="!cityId"
                  clearable
                  placeholder="区/县"
                  @change="handleDistrictChange"
                >
                  <el-option
                    v-for="item in districts"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
                <small>
                  {{
                    form.organizationNature === 'ENTERPRISE'
                      ? '企业必须选择到区县级'
                      : '行管可选择省、市或区县作为管辖范围'
                  }}
                </small>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model.trim="form.contactName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model.trim="form.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址"><el-input v-model.trim="form.address" /></el-form-item>
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
              <el-form-item label="管理员手机">
                <el-input v-model.trim="form.adminPhone" />
              </el-form-item>
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
        <template #empty>该组织暂无管理员账号</template>
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
.area-selectors {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.area-selectors small {
  grid-column: 1 / -1;
  color: #8491a6;
  line-height: 1.4;
}

.el-alert {
  margin-bottom: 20px;
}
</style>
