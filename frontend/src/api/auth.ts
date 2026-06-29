import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 登录请求参数
export type LoginPayload = {
  username: string;
  password: string;
};

// 登录响应数据
export type LoginResult = {
  token: string;
  userId: number;
  username: string;
  role: string; // 新增角色字段
};

export const authApi = {
  // 用户登录（POST /api/auth/login）
  async login(payload: LoginPayload): Promise<LoginResult> {
    const resp = await request.post<ApiResponse<LoginResult>>("/api/auth/login", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "登录失败");
    return resp.data.data!;
  },

  // 用户登出（POST /api/auth/logout）：将 Token 加入 Redis 黑名单
  async logout(): Promise<void> {
    await request.post("/api/auth/logout").catch(() => {});
  }
};
