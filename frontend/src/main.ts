import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
// Base reset and the `--el-*` custom properties every Element Plus component
// needs. Component styles themselves are imported on demand: template usage is
// handled by unplugin-vue-components (see vite.config.ts), the imperative APIs
// and the v-loading directive below are imported directly and therefore need
// their styles explicitly.
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import router from './router'
import './styles.css'

createApp(App).use(createPinia()).use(router).use(ElLoading).mount('#app')
