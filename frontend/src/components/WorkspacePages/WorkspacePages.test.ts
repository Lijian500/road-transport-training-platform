import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import WorkspacePages from './WorkspacePages.vue'

/** 挂载真实路由，控件桩仅用来触发导航和关闭事件。 */
async function setup() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin', component: { template: '<div>首页内容</div>' }, meta: { title: '首页' } },
      {
        path: '/admin/users',
        component: { template: '<div>人员内容</div>' },
        meta: { title: '人员' },
      },
      {
        path: '/admin/exam',
        component: { template: '<div>试卷内容</div>' },
        meta: { title: '试卷' },
      },
    ],
  })
  await router.push('/admin')
  const wrapper = mount(WorkspacePages, {
    props: { home: '/admin' },
    global: {
      plugins: [router],
      stubs: {
        ElTabs: { name: 'ElTabs', props: ['modelValue'], template: '<div><slot /></div>' },
        ElTabPane: {
          name: 'ElTabPane',
          props: ['name', 'label'],
          template: '<span>{{ label }}</span>',
        },
        ElDropdown: { name: 'ElDropdown', template: '<div><slot /><slot name="dropdown" /></div>' },
        ElDropdownMenu: { template: '<div><slot /></div>' },
        ElDropdownItem: { template: '<div><slot /></div>' },
        ElButton: { template: '<button><slot /></button>' },
      },
    },
  })
  return { wrapper, router }
}

describe('历史页面标签', () => {
  it('切换筛选不会重复增加标签，关闭当前页跳至相邻页', async () => {
    const { wrapper, router } = await setup()
    await router.push('/admin/users')
    await router.push('/admin/exam?tab=questions')
    await router.replace('/admin/exam?tab=papers')
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ElTabPane' })).toHaveLength(3)
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('tab-remove', '/admin/exam')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/users')
    expect(wrapper.findAllComponents({ name: 'ElTabPane' })).toHaveLength(2)
    wrapper.unmount()
  })

  it('支持关闭其他及全部关闭，最后只保留默认首页', async () => {
    const { wrapper, router } = await setup()
    await router.push('/admin/users')
    await flushPromises()
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'others')
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ElTabPane' })).toHaveLength(1)
    expect(router.currentRoute.value.path).toBe('/admin/users')
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'all')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin')
    expect(wrapper.findAllComponents({ name: 'ElTabPane' })).toHaveLength(1)
    wrapper.unmount()
  })

  it('页面离开守卫拒绝导航时保留标签和当前位置', async () => {
    const { wrapper, router } = await setup()
    await router.push('/admin/users')
    const removeGuard = router.beforeEach(() => false)
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'all')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/users')
    expect(wrapper.findAllComponents({ name: 'ElTabPane' })).toHaveLength(2)
    removeGuard()
    wrapper.unmount()
  })
})
