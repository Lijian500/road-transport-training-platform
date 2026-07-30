<script setup lang="ts">
import AppPagination from '@/components/AppPagination/AppPagination.vue'

defineProps<{
  data: unknown[]
  loading?: boolean
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
  <section class="app-table">
    <header v-if="$slots.search || $slots.actions" class="app-table__toolbar">
      <div class="app-table__search">
        <slot name="search" />
      </div>
      <div class="app-table__actions">
        <slot name="actions" />
      </div>
    </header>
    <el-table v-loading="loading" :data="data" row-key="id">
      <slot />
    </el-table>
    <AppPagination
      :page-number="pageNumber"
      :page-size="pageSize"
      :total="total"
      @page-change="emit('pageChange', $event)"
      @size-change="emit('sizeChange', $event)"
    />
  </section>
</template>

<style scoped>
.app-table {
  overflow: hidden;
  padding: 20px;
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 14px;
}

.app-table__toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.app-table__search,
.app-table__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (width <= 720px) {
  .app-table__toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
