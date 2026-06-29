import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `MedicineEntity` 对齐的字段（/api/medicines 返回）
export type Medicine = {
  id: number;
  name: string;
  category?: string | null;
  specification?: string | null;
  unit?: string | null;
  price: number;
  stock: number;
  manufacturer?: string | null;
  approvalNumber?: string | null;
  storageCondition?: string | null;
  status: number; // 0=下架，1=上架（与 SQL 注释一致）
  createTime?: string;
  updateTime?: string;
};

// 新增药品入参（与后端 `CreateMedicineRequest` 对齐）
export type CreateMedicinePayload = {
  name: string;
  category?: string;
  specification?: string;
  unit?: string;
  price: number;
  stock: number;
  manufacturer?: string;
  approvalNumber?: string;
  storageCondition?: string;
};

// 更新药品入参（与后端 `UpdateMedicineRequest` 对齐）
export type UpdateMedicinePayload = {
  name?: string;
  category?: string;
  specification?: string;
  unit?: string;
  price?: number;
  stock?: number;
  manufacturer?: string;
  approvalNumber?: string;
  storageCondition?: string;
  status?: number;
};

export const medicinesApi = {
  // 查询药品列表（GET /api/medicines）
  async list(): Promise<Medicine[]> {
    const resp = await request.get<ApiResponse<Medicine[]>>("/api/medicines");
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增药品（POST /api/medicines）
  async create(payload: CreateMedicinePayload): Promise<Medicine> {
    const resp = await request.post<ApiResponse<Medicine>>("/api/medicines", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 更新药品信息（PUT /api/medicines/{id}）
  async update(id: number, payload: UpdateMedicinePayload): Promise<Medicine> {
    const resp = await request.put<ApiResponse<Medicine>>(`/api/medicines/${id}`, payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 上下架（PUT /api/medicines/{id}/status）
  async updateStatus(id: number, status: number): Promise<Medicine> {
    const resp = await request.put<ApiResponse<Medicine>>(`/api/medicines/${id}/status`, { status });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  }
};
