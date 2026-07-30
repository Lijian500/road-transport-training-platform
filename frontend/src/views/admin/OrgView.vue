<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  createOrg,
  deleteOrg,
  getOrgTree,
  updateOrg,
  type OrgNode,
  type OrgPayload,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'

const loading = ref(false)
const saving = ref(false)
const tree = ref<OrgNode[]>([])
const dialogVisible = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInstance>()
const form = reactive<OrgPayload>({
  parentId: null,
  name: '',
  code: '',
  sortOrder: 0,
})
const rules: FormRules = {
  parentId: [{ required: true, message: '请选择上级组织', trigger: 'change' }],
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    tree.value = await getOrgTree()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: OrgNode) {
  editingId.value = undefined
  Object.assign(form, {
    parentId: parent?.id || tree.value[0]?.id || null,
    name: '',
    code: '',
    sortOrder: 0,
  })
  dialogVisible.value = true
}

function openEdit(node: OrgNode) {
  editingId.value = node.id
  Object.assign(form, {
    parentId: node.parentId,
    name: node.name,
    code: node.code,
    sortOrder: node.sortOrder,
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
      await updateOrg(editingId.value, form)
    } else {
      await createOrg(form)
    }
    ElMessage.success(editingId.value ? '部门已更新' : '部门已创建')
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function remove(node: OrgNode) {
  await ElMessageBox.confirm(`确定删除部门“${node.name}”吗？`, '删除确认', { type: 'warning' })
  try {
    await deleteOrg(node.id)
    ElMessage.success('部门已删除')
    await load()
  } catch (error) {
    showError(error)
  }
}

function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-title page-title--actions">
      <div>
        <h1>部门管理</h1>
        <p>维护当前企业的组织层级，企业根节点不可删除。</p>
      </div>
      <PermissionButton permission="admin:org:create" type="primary" @click="openCreate()">
        新建部门
      </PermissionButton>
    </header>
    <div v-loading="loading" class="tree-card">
      <el-empty v-if="!tree.length && !loading" description="暂无组织数据" />
      <el-tree
        v-else
        :data="tree"
        :default-expand-all="true"
        :expand-on-click-node="false"
        node-key="id"
      >
        <template #default="{ data }">
          <div class="tree-node">
            <div>
              <strong>{{ data.name }}</strong>
              <span>{{ data.code }} · {{ data.type === 'ENTERPRISE' ? '企业' : '部门' }}</span>
            </div>
            <div class="tree-node__actions">
              <PermissionButton permission="admin:org:create" link @click.stop="openCreate(data)">
                新增下级
              </PermissionButton>
              <template v-if="data.type === 'DEPARTMENT'">
                <PermissionButton permission="admin:org:update" link type="primary" @click.stop="openEdit(data)">
                  编辑
                </PermissionButton>
                <PermissionButton permission="admin:org:delete" link type="danger" @click.stop="remove(data)">
                  删除
                </PermissionButton>
              </template>
            </div>
          </div>
        </template>
      </el-tree>
    </div>

    <AppDialog
      v-model="dialogVisible"
      :loading="saving"
      :title="editingId ? '编辑部门' : '新建部门'"
      @confirm="save"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级组织" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="tree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            default-expand-all
            node-key="id"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model.trim="form.name" />
        </el-form-item>
        <el-form-item label="部门编码" prop="code">
          <el-input v-model.trim="form.code" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.tree-card {
  min-height: 260px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 14px;
}

.tree-node {
  display: flex;
  flex: 1;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.tree-node > div:first-child {
  display: flex;
  gap: 12px;
  align-items: baseline;
}

.tree-node span {
  color: #8491a6;
  font-size: 12px;
}

.tree-node__actions {
  padding-right: 12px;
}
</style>
