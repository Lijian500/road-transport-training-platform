<script setup lang="ts">
const visible = defineModel<boolean>({ required: true })

withDefaults(
  defineProps<{
    title: string
    loading?: boolean
    width?: string
    confirmText?: string
  }>(),
  {
    loading: false,
    width: '560px',
    confirmText: '保存',
  },
)

const emit = defineEmits<{
  confirm: []
  closed: []
}>()
</script>

<template>
  <el-dialog
    v-model="visible"
    :close-on-click-modal="!loading"
    :title="title"
    :width="width"
    destroy-on-close
    @closed="emit('closed')"
  >
    <slot />
    <template #footer>
      <el-button :disabled="loading" @click="visible = false">取消</el-button>
      <el-button :loading="loading" type="primary" @click="emit('confirm')">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>
