import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import * as Icons from "@element-plus/icons-vue";
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import { initHttpRouter } from "./api/http";
import { useAuthStore } from "./stores/auth";
import "./assets/styles.css";
import { initTheme } from "./theme";

initTheme();

const app = createApp(App);
const pinia = createPinia();

Object.entries(Icons).forEach(([name, component]) => {
  app.component(name, component);
});

app.use(pinia);

// 初始化 http 拦截器与路由器的连接和认证错误回调
const authStore = useAuthStore();
initHttpRouter(router, () => {
  authStore.clear();
});

app.use(router);
app.use(ElementPlus);
app.mount("#app");
