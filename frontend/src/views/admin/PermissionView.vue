<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getPermissionTree, type PermissionNode } from '@/api/admin'
import { ApiError } from '@/api/http'

const loading = ref(false)
const permissions = ref<PermissionNode[]>([])

async function load() {
  loading.value = true
  try {
    permissions.value = await getPermissionTree()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '权限目录加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-title">
      <h1>权限目录</h1>
      <p>权限由系统迁移脚本固定维护，企业角色只能进行分配。</p>
    </header>
    <div class="table-card">
      <el-table
        v-loading="loading"
        :data="permissions"
        :tree-props="{ children: 'children' }"
        default-expand-all
        row-key="id"
      >
        <el-table-column label="权限名称" min-width="180" prop="name" />
        <el-table-column label="权限编码" min-width="250" prop="code" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'MENU' ? 'primary' : 'info'">
              {{ row.type === 'MENU' ? '菜单' : '操作' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="110" prop="scope" />
      </el-table>
    </div>
  </section>
</template>
