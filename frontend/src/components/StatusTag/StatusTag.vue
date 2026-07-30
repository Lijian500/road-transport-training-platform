<script setup lang="ts">
defineProps<{
  status: string
}>()
</script>

<template>
  <el-tag :type="status === 'ENABLED' ? 'success' : 'info'">
    {{ status === 'ENABLED' ? '启用' : '禁用' }}
  </el-tag>
</template>
