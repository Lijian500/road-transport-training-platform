<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import {
  createAddress,
  getAddressChildren,
  updateAddress,
  type AddressNode,
  type AddressPayload,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const loading = ref(false)
const saving = ref(false)
const tree = shallowRef<AddressNode[]>([])
const tableRef = ref<{ toggleRowExpansion: (row: AddressNode) => void }>()
const tableKey = ref(0)
const dialogVisible = ref(false)
const editingId = ref<string>()
const parentName = ref('无（省级地址）')
const formRef = ref<FormInstance>()
const form = reactive<AddressPayload>(emptyForm())
const addressNames = new Map<string, string>()
const rules: FormRules<AddressPayload> = {
  parentCode: [{ required: true, message: '请选择上级地址', trigger: 'change' }],
  areaCode: [
    { required: true, message: '请输入行政代码', trigger: 'blur' },
    { max: 64, message: '行政代码不能超过64个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 50, message: '名称不能超过50个字符', trigger: 'blur' },
  ],
  shortName: [{ max: 50, message: '简称不能超过50个字符', trigger: 'blur' }],
  mergerName: [{ max: 50, message: '组合名不能超过50个字符', trigger: 'blur' }],
  pinyin: [{ max: 30, message: '拼音不能超过30个字符', trigger: 'blur' }],
}

/** 创建空白地址表单。 */
function emptyForm(): AddressPayload {
  return {
    parentCode: '0',
    areaCode: '',
    zipCode: '0',
    cityCode: '',
    name: '',
    shortName: '',
    mergerName: '',
    pinyin: '',
    lng: 0,
    lat: 0,
  }
}

/** 查询省级地址，其他层级在展开时按需加载。 */
async function load() {
  loading.value = true
  try {
    const nodes = await getAddressChildren()
    addressNames.clear()
    rememberAddressNames(nodes)
    tree.value = nodes
    tableKey.value += 1
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 懒加载指定地址的直属下级。 */
async function loadChildren(
  row: AddressNode,
  _treeNode: unknown,
  resolve: (data: AddressNode[]) => void,
) {
  try {
    const nodes = await getAddressChildren(row.areaCode)
    rememberAddressNames(nodes)
    resolve(nodes)
  } catch (error) {
    resolve([])
    showError(error)
  }
}

/** 记录已加载节点名称，用于展示当前地址的上级。 */
function rememberAddressNames(nodes: AddressNode[]) {
  nodes.forEach((node) => addressNames.set(node.areaCode, node.name))
}

/** 打开新增地址弹窗，未指定上级时新增省级地址。 */
function openCreate(parent?: AddressNode) {
  editingId.value = undefined
  parentName.value = parent?.name || '无（省级地址）'
  Object.assign(form, emptyForm(), { parentCode: parent?.areaCode || '0' })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

/** 打开编辑地址弹窗。 */
function openEdit(node: AddressNode) {
  editingId.value = node.id
  parentName.value =
    node.level === 1 ? '无（省级地址）' : addressNames.get(node.parentCode) || node.parentCode
  Object.assign(form, {
    parentCode: node.parentCode,
    areaCode: node.areaCode,
    zipCode: node.zipCode,
    cityCode: node.cityCode,
    name: node.name,
    shortName: node.shortName,
    mergerName: node.mergerName,
    pinyin: node.pinyin,
    lng: node.lng,
    lat: node.lat,
  })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

/** 点击地址行时切换下级地址的展开状态。 */
function toggleRowExpansion(
  row: AddressNode,
  column: { property?: string } | null,
  event: PointerEvent,
) {
  const target = event.target as HTMLElement | null
  if (
    target?.closest('.el-table__expand-icon') ||
    column?.property !== 'name' ||
    !row.hasChildren
  ) {
    return
  }
  tableRef.value?.toggleRowExpansion(row)
}

/** 校验并保存地址。 */
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, form)
    } else {
      await createAddress(form)
    }
    ElMessage.success(editingId.value ? '地址已更新' : '地址已新增')
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 获取层级的中文名称。 */
function levelName(level: number) {
  return ['省、直辖市', '市', '区、县'][level - 1] || '未知'
}

/** 统一展示接口错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-title page-title--actions">
      <div>
        <h1>地址管理</h1>
        <p>维护全国省、市、区县三级行政地址，展开节点可查看下级地址。</p>
      </div>
      <el-button
        v-if="permissionStore.has('admin:address:create')"
        type="primary"
        @click="openCreate()"
      >
        新增省级地址
      </el-button>
    </header>

    <div v-loading="loading" class="table-card address-table">
      <el-table
        v-if="tree.length || loading"
        ref="tableRef"
        :key="tableKey"
        :data="tree"
        :load="loadChildren"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        lazy
        row-key="id"
        @row-click="toggleRowExpansion"
      >
        <el-table-column label="名称" min-width="180" prop="name" />
        <el-table-column label="邮政编码" min-width="120" prop="zipCode" />
        <el-table-column label="行政代码" min-width="150" prop="areaCode" />
        <el-table-column label="类型" min-width="120">
          <template #default="{ row }">{{ levelName(row.level) }}</template>
        </el-table-column>
        <el-table-column label="经度" min-width="130" prop="lng" />
        <el-table-column label="纬度" min-width="130" prop="lat" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="row.level < 3 && permissionStore.has('admin:address:create')"
              link
              @click.stop="openCreate(row)"
            >
              新增
            </el-button>
            <el-button
              v-if="permissionStore.has('admin:address:update')"
              link
              type="primary"
              @click.stop="openEdit(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无地址数据" />
    </div>

    <AppDialog
      v-model="dialogVisible"
      :loading="saving"
      :title="editingId ? '编辑地址' : '新增地址'"
      width="720px"
      @confirm="save"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <div class="address-form-grid">
          <el-form-item label="上级地址" prop="parentCode">
            <el-input :model-value="parentName" disabled />
          </el-form-item>
          <el-form-item label="行政代码" prop="areaCode">
            <el-input v-model.trim="form.areaCode" maxlength="64" />
          </el-form-item>
          <el-form-item label="名称" prop="name">
            <el-input v-model.trim="form.name" maxlength="50" />
          </el-form-item>
          <el-form-item label="简称" prop="shortName">
            <el-input v-model.trim="form.shortName" maxlength="50" />
          </el-form-item>
          <el-form-item label="邮政编码" prop="zipCode">
            <el-input v-model.trim="form.zipCode" maxlength="64" />
          </el-form-item>
          <el-form-item label="区号" prop="cityCode">
            <el-input v-model.trim="form.cityCode" maxlength="64" />
          </el-form-item>
          <el-form-item label="组合名" prop="mergerName">
            <el-input v-model.trim="form.mergerName" maxlength="50" />
          </el-form-item>
          <el-form-item label="拼音" prop="pinyin">
            <el-input v-model.trim="form.pinyin" maxlength="30" />
          </el-form-item>
          <el-form-item label="经度" prop="lng">
            <el-input-number v-model="form.lng" :max="180" :min="-180" :precision="6" />
          </el-form-item>
          <el-form-item label="纬度" prop="lat">
            <el-input-number v-model="form.lat" :max="90" :min="-90" :precision="6" />
          </el-form-item>
        </div>
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.address-table {
  padding: 0;
}

.address-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.address-form-grid :deep(.el-select),
.address-form-grid :deep(.el-input-number) {
  width: 100%;
}

@media (width <= 640px) {
  .address-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
