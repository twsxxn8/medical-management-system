import axios from "axios";  //封装axios请求，避免重复代码
import { clearAuth, getToken } from "./authStorage";

export const request = axios.create({
  baseURL: "/",
  timeout: 15000
});

// 请求拦截器：自动携带 Token（sessionStorage，按标签页隔离）
request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器：处理 401 未授权
request.interceptors.response.use(
  (resp) => resp,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth();
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

