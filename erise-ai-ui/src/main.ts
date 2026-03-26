import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './assets/styles.css'

const app = createApp(App)
const pinia = createPinia()

Object.entries(Icons).forEach(([name, component]) => {
  app.component(name, component)
})

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
