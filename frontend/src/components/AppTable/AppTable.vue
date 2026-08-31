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
  container-name: app-table;
  container-type: inline-size;
  overflow: hidden;
  padding: 22px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
}

.app-table__toolbar {
  display: grid;
  align-items: end;
  margin-bottom: 22px;
  padding: 16px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
}

.app-table__search {
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 14px;
}

.app-table__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.app-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

@container app-table (width <= 1120px) {
  .app-table__search {
    grid-template-columns: repeat(3, minmax(160px, 1fr));
  }
}

@container app-table (width <= 880px) {
  .app-table__toolbar {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .app-table__actions {
    justify-content: flex-start;
  }
}

@container app-table (width <= 720px) {
  .app-table__search {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@container app-table (width <= 560px) {
  .app-table__toolbar {
    padding: 14px;
  }

  .app-table__search {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-table__search :deep(.app-filter-actions) {
    width: 100%;
  }
}

@media (width <= 560px) {
  .app-table {
    padding: 14px;
  }
}
</style>
