<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

const props = defineProps<{ home: string }>()
const route = useRoute()
const router = useRouter()
const tabs = ref<Array<{ path: string; fullPath: string; title: string }>>([])

/** 同一路径只保留一个标签，同时记住最新查询参数，不同详情使用不同标签。 */
function rememberPage() {
  if (route.path !== props.home && !route.path.startsWith(`${props.home}/`)) return
  const existing = tabs.value.find((tab) => tab.path === route.path)
  if (existing) {
    existing.fullPath = route.fullPath
    return
  }
  tabs.value.push({
    path: route.path,
    fullPath: route.fullPath,
    title: String(route.meta.title || '页面'),
  })
}

/** 切换到已访问的页面，继续交由路由守卫校验权限。 */
function switchPage(path: string | number) {
  void router.push(tabs.value.find((tab) => tab.path === path)?.fullPath || String(path))
}

/** 关闭当前页时优先切换到相邻页，没有其他标签则返回首页。 */
async function closePage(path: string | number) {
  const index = tabs.value.findIndex((tab) => tab.path === path)
  if (index < 0) return
  if (route.path === path) {
    const next = tabs.value[index + 1] || tabs.value[index - 1]
    const failure = await router.push(next?.fullPath || props.home)
    if (failure && route.fullPath !== props.home) return
  }
  tabs.value = tabs.value.filter((tab) => tab.path !== path)
  rememberPage()
}

/** 关闭其他标签；全部关闭时先导航首页，尊重页面的离开守卫。 */
async function closeTabs(command: string) {
  if (command === 'all') {
    const failure = await router.push(props.home)
    if (failure && route.path !== props.home) return
  }
  tabs.value = tabs.value.filter((tab) => tab.path === route.path)
  rememberPage()
}

watch(() => route.fullPath, rememberPage, { immediate: true })
</script>

<template>
  <div class="workspace-pages">
    <nav class="workspace-pages__bar" aria-label="已访问页面">
      <el-tabs
        :model-value="route.path"
        type="card"
        closable
        @tab-change="switchPage"
        @tab-remove="closePage"
      >
        <el-tab-pane v-for="tab in tabs" :key="tab.path" :name="tab.path" :label="tab.title" />
      </el-tabs>
      <el-dropdown trigger="click" @command="closeTabs">
        <el-button plain aria-label="标签页操作">标签操作 ▾</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="others">关闭其他</el-dropdown-item>
            <el-dropdown-item command="all">全部关闭</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </nav>
    <RouterView v-slot="{ Component }">
      <component :is="Component" :key="route.path" />
    </RouterView>
  </div>
</template>

<style scoped>
.workspace-pages__bar {
  display: flex;
  align-items: start;
  gap: 12px;
  margin-bottom: 16px;
}
.el-tabs {
  flex: 1;
  min-width: 0;
}
.workspace-pages__bar :deep(.el-tabs__header) {
  margin: 0;
}
.workspace-pages__bar :deep(.el-tabs__item) {
  height: 36px;
  background: var(--app-surface);
}
.workspace-pages__bar :deep(.el-tabs__item.is-active) {
  background: var(--el-color-primary-light-9);
}
.workspace-pages__bar :deep(.el-tabs__content) {
  display: none;
}
</style>
