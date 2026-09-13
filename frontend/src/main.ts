import { createPinia } from 'pinia'
import { createApp } from 'vue'

import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './assets/styles/main.css'

// 全局启用中文，统一选择器和空数据提示。
const locale = {
  ...zhCn,
  el: {
    ...zhCn.el,
    table: { ...zhCn.el.table, emptyText: '无数据' },
    select: { ...zhCn.el.select, placeholder: '请选择', noData: '无数据' },
    empty: { description: '无数据' },
  },
}

createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale }).mount('#app')
