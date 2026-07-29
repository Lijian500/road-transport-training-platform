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
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 16px;
  box-shadow: 0 14px 40px rgb(23 32 51 / 7%);
}

.placeholder__eyebrow {
  margin: 0 0 10px;
  color: #155eef;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

h2 {
  margin: 0 0 12px;
  font-size: clamp(24px, 4vw, 36px);
}

p {
  color: #5f6c85;
  line-height: 1.75;
}
