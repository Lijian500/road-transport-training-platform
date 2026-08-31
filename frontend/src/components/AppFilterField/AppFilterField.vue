<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    wide?: boolean
  }>(),
  {
    wide: false,
  },
)
</script>

<template>
  <label class="app-filter-field" :class="{ 'app-filter-field--wide': wide }">
    <span class="app-filter-field__label">{{ label }}</span>
    <span class="app-filter-field__control">
      <slot />
    </span>
  </label>
</template>

<style scoped>
.app-filter-field {
  display: grid;
  align-items: center;
  min-width: 0;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 10px;
}

.app-filter-field--wide,
.app-filter-field:has(:deep(.el-date-editor--daterange)),
.app-filter-field:has(:deep(.el-date-editor--datetimerange)) {
  grid-column: span 2;
}

.app-filter-field__label {
  min-width: 64px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-align: right;
  white-space: nowrap;
}

.app-filter-field__control {
  display: block;
  min-width: 0;
}

.app-filter-field__control :deep(.el-input),
.app-filter-field__control :deep(.el-select),
.app-filter-field__control :deep(.el-tree-select),
.app-filter-field__control :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}

@container app-table (width <= 560px) {
  .app-filter-field--wide,
  .app-filter-field:has(:deep(.el-date-editor--daterange)),
  .app-filter-field:has(:deep(.el-date-editor--datetimerange)) {
    grid-column: span 1;
  }

  .app-filter-field__label {
    min-width: 70px;
  }
}
</style>
