<script setup lang="ts">
defineProps<{ src?: string; label: string }>()
</script>

<template>
  <el-image
    v-if="src"
    class="record-photo"
    :src="src"
    :alt="label"
    :preview-src-list="[src]"
    fit="cover"
    preview-teleported
  >
    <template #error><span class="photo-placeholder">加载失败，请刷新</span></template>
  </el-image>
  <span v-else class="photo-placeholder">暂无照片</span>
</template>

<style scoped>
.record-photo {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  vertical-align: middle;
}
.photo-placeholder {
  color: var(--app-text-muted);
  font-size: 12px;
}
</style>
