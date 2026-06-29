import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `PatientEntity` 对齐的字段（/api/patients 返回）
export type Patient = {
  id: number;
  userId: number;
  name?: string | null;
  idCard: string;
  gender?: string | null;
  birthDate?: string | null; // LocalDate -> ISO 字符串（yyyy-MM-dd）
  address?: string | null;
  emergencyContact?: string | null;
  emergencyPhone?: string | null;
  medicalHistory?: string | null;
  createTime?: string;
};

// 新增患者档案入参（与后端 `CreatePatientRequest` 对齐）
export type CreatePatientPayload = {
  userId: number | null;
  idCard: string;
  gender?: string;
  birthDate?: string | null;
  address?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  medicalHistory?: string;
};

// 更新患者档案入参（与后端 `UpdatePatientRequest` 对齐；不含 userId / idCard）
export type UpdatePatientPayload = {
  gender?: string;
  birthDate?: string | null;
  address?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  medicalHistory?: string;
};

export const patientsApi = {
  // 查询患者列表（GET /api/patients）
  async list(): Promise<Patient[]> {
    const resp = await request.get<ApiResponse<Patient[]>>("/api/patients");
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增患者档案（POST /api/patients）
  async create(payload: CreatePatientPayload): Promise<Patient> {
    const resp = await request.post<ApiResponse<Patient>>("/api/patients", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 更新患者档案（PUT /api/patients/{id}）
  async update(id: number, payload: UpdatePatientPayload): Promise<Patient> {
    const resp = await request.put<ApiResponse<Patient>>(`/api/patients/${id}`, payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 删除患者（DELETE /api/patients/{id}，仅管理员可用）
  async delete(id: number): Promise<void> {
    const resp = await request.delete<ApiResponse<void>>(`/api/patients/${id}`);
    if (!resp.data.success) throw new Error(resp.data.message ?? "删除失败");
  }
};

