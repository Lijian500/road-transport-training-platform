<script setup lang="ts">
defineProps<{
  total: number
  pageNumber: number
  pageSize: number
}>()

const emit = defineEmits<{
  pageChange: [pageNumber: number]
  sizeChange: [pageSize: number]
}>()
</script>

<template>
  <footer class="app-pagination">
    <el-pagination
      :current-page="pageNumber"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      background
      layout="total, sizes, prev, pager, next"
      @current-change="emit('pageChange', $event)"
      @size-change="emit('sizeChange', $event)"
    />
  </footer>
</template>

<style scoped>
.app-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (width <= 720px) {
  .app-pagination {
    overflow-x: auto;
    justify-content: flex-start;
  }
}
</style>
