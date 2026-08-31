<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <section class="placeholder">
    <p class="placeholder__eyebrow">PROJECT SCAFFOLD</p>
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <slot />
  </section>
</template>

<style scoped>
.placeholder {
  max-width: 760px;
  padding: 40px;
  background: linear-gradient(145deg, #fff, #f8faff);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-md);
}

.placeholder__eyebrow {
  margin: 0 0 10px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

h2 {
  margin: 0 0 12px;
  font-size: clamp(24px, 4vw, 36px);
}

p {
  color: var(--app-text-secondary);
  line-height: 1.75;
}
</style>
