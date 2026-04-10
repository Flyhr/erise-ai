import axios from "axios";
import { ElMessage } from "element-plus";
import type { InternalAxiosRequestConfig } from "axios";
import type { ApiResponse } from "@/types/models";
import { beginRouteLoadingRequest, finishRouteLoadingRequest } from "@/composables/useRouteLoading";
import type { Router } from "vue-router";

const apiBase = import.meta.env.VITE_API_BASE_URL || "/api";
const normalizedApiBase = apiBase.endsWith("/")
  ? apiBase.slice(0, -1)
  : apiBase;

const http = axios.create({
  baseURL: normalizedApiBase,
  timeout: 30000,
});

let router: Router | null = null;
let onAuthError: (() => void) | null = null;

interface RouteLoadingRequestConfig extends InternalAxiosRequestConfig {
  __routeLoadingToken?: number | null;
}

export const initHttpRouter = (r: Router, authErrorCallback?: () => void) => {
  router = r;
  if (authErrorCallback) {
    onAuthError = authErrorCallback;
  }
};

http.interceptors.request.use((config) => {
  const trackedConfig = config as RouteLoadingRequestConfig;
  trackedConfig.__routeLoadingToken = beginRouteLoadingRequest();
  const token = localStorage.getItem("erise-access-token");
  if (token) {
    trackedConfig.headers.Authorization = `Bearer ${token}`;
  }
  return trackedConfig;
});

http.interceptors.response.use(
  (response): any => {
    finishRouteLoadingRequest((response.config as RouteLoadingRequestConfig).__routeLoadingToken);
    const payload = response.data as ApiResponse<unknown>;
    if (payload.code !== 0) {
      const message = payload.message ?? payload.msg ?? "Request failed";
      // 检查是否是认证失败相关的错误
      if (
        payload.code === 401 ||
        payload.code === 403 ||
        message.includes("Access Denied") ||
        message.includes("Unauthorized") ||
        message.includes("Token")
      ) {
        handleAuthError();
        return Promise.reject(new Error(message));
      }
      ElMessage.error(message);
      return Promise.reject(new Error(message));
    }
    return payload.data;
  },
  (error) => {
    finishRouteLoadingRequest((error.config as RouteLoadingRequestConfig | undefined)?.__routeLoadingToken);
    const statusCode = error.response?.status;
    const message =
      error.response?.data?.message ??
      error.response?.data?.msg ??
      error.message ??
      "Request failed";

    // 检查是否是认证失败相关的错误
    if (
      statusCode === 401 ||
      statusCode === 403 ||
      message.includes("Access Denied") ||
      message.includes("Unauthorized") ||
      message.includes("Token")
    ) {
      handleAuthError();
      return Promise.reject(error);
    }

    ElMessage.error(message);
    return Promise.reject(error);
  },
);

function handleAuthError() {
  // 清空认证信息
  localStorage.removeItem("erise-access-token");
  localStorage.removeItem("erise-refresh-token");
  localStorage.removeItem("erise-user");

  // 调用认证错误回调
  if (onAuthError) {
    onAuthError();
  }

  // 如果 router 已初始化，跳转到登入页面
  if (router && !router.currentRoute.value.path.includes("/login")) {
    router.push("/login");
  }
}

export const resolveApiUrl = (path: string) => {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${normalizedApiBase}${normalizedPath}`;
};

export default http;
