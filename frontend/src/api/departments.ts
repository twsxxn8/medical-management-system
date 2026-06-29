import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `DepartmentEntity` 对齐的字段（/api/departments 返回）。
export type Department = {
  id: number;
  name: string;
  parentId?: number | null;
  description?: string | null;
  location?: string | null;
  createTime?: string;
  updateTime?: string;
};

// 新增科室入参（与后端 `CreateDepartmentRequest` 对齐）。
export type CreateDepartmentPayload = {
  name: string;
  parentId?: number | null;
  description?: string;
  location?: string;
};

// 定义分页响应类型
export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 当前页码
  size: number;   // 每页条数
};

export const departmentsApi = {
  async list(params?: { page?: number; size?: number }): Promise<PageResponse<Department>> {
    const resp = await request.get<ApiResponse<PageResponse<Department>>>("/api/departments", { params });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data!;
  },

  // 新增科室（POST /api/departments）
  async create(payload: CreateDepartmentPayload): Promise<Department> {
    const resp = await request.post<ApiResponse<Department>>("/api/departments", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 删除科室（DELETE /api/departments/{id}）
  async delete(id: number): Promise<void> {
    const resp = await request.delete<ApiResponse<void>>(`/api/departments/${id}`);
    if (!resp.data.success) throw new Error(resp.data.message ?? "删除失败");
  }
};

