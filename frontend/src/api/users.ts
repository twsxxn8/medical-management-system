import { request } from "../utils/request";
import type { ApiResponse } from "./health";

export type User = {
  id: number;
  username: string;
  realName: string | null;
  role: string;
  phone: string | null;
  status: number;
};

export const usersApi = {
  // 查询用户列表
  async list(): Promise<User[]> {
    const resp = await request.get<ApiResponse<User[]>>("/api/users");
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增用户
  async create(payload: any): Promise<User> {
    const resp = await request.post<ApiResponse<User>>("/api/users", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data!;
  },

  // 删除用户
  async delete(id: number): Promise<void> {
    await request.delete(`/api/users/${id}`);
  }
};
