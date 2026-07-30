<script setup lang="ts">
import { usePermissionStore } from '@/stores/permission'

defineOptions({
  inheritAttrs: false,
})

defineProps<{
  permission: string
}>()

const permissionStore = usePermissionStore()
</script>

<template>
  <el-button v-if="permissionStore.has(permission)" v-bind="$attrs">
    <slot />
  </el-button>
</template>
