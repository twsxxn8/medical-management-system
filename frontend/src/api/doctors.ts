import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `DoctorEntity` 对齐的字段（/api/doctors 返回）
export type Doctor = {
  id: number;
  userId: number;
  realName?: string | null; // 从 user 表关联查询的姓名
  departmentId: number;
  title?: string | null;
  specialty?: string | null;
  consultationFee?: number | null;
  introduction?: string | null;
  schedule?: string | null;
  status: number; // 0=停诊，1=应诊（与 SQL 注释一致）
  createTime?: string;
  updateTime?: string;
};

// 新增医生入参（与后端 `CreateDoctorRequest` 对齐）
export type CreateDoctorPayload = {
  userId: number;
  departmentId: number;
  title?: string;
  specialty?: string;
  consultationFee?: number | null;
  introduction?: string;
  schedule?: string;
};

// 更新医生入参（与后端 `UpdateDoctorRequest` 对齐；不含 userId）
export type UpdateDoctorPayload = {
  departmentId: number;
  title?: string;
  specialty?: string;
  consultationFee?: number | null;
  introduction?: string;
  schedule?: string;
  status?: number;
};

export const doctorsApi = {
  // 查询医生列表（GET /api/doctors，可选 departmentId 过滤）
  async list(params?: { departmentId?: number }): Promise<Doctor[]> {
    const resp = await request.get<ApiResponse<Doctor[]>>("/api/doctors", { params });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增医生（POST /api/doctors）
  async create(payload: CreateDoctorPayload): Promise<Doctor> {
    const resp = await request.post<ApiResponse<Doctor>>("/api/doctors", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 更新医生信息（PUT /api/doctors/{id}）
  async update(id: number, payload: UpdateDoctorPayload): Promise<Doctor> {
    const resp = await request.put<ApiResponse<Doctor>>(`/api/doctors/${id}`, payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 启用/停诊（PUT /api/doctors/{id}/status）
  async updateStatus(id: number, status: number): Promise<Doctor> {
    const resp = await request.put<ApiResponse<Doctor>>(`/api/doctors/${id}/status`, { status });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

};

