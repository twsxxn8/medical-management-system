import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `MedicalRecordEntity` 对齐的字段（/api/medical-records 返回）
export type MedicalRecord = {
  id: number;
  patientId: number;
  doctorId: number;
  appointmentId?: number | null;
  diagnosis: string;
  prescription?: string | null;
  prescriptionMedicines?: string | null;
  symptoms?: string | null;
  visitDate: string;
  createTime?: string;
  updateTime?: string;
};

// 新增病历入参（与后端 `CreateMedicalRecordRequest` 对齐）
export type CreateMedicalRecordPayload = {
  patientId: number;
  doctorId: number;
  appointmentId?: number | null;
  diagnosis: string;
  prescription?: string;
  prescriptionMedicines?: string;
  symptoms?: string;
  visitDate: string;
};

// 更新病历入参（与后端 `UpdateMedicalRecordRequest` 对齐）
export type UpdateMedicalRecordPayload = {
  diagnosis?: string;
  prescription?: string;
  prescriptionMedicines?: string;
  symptoms?: string;
  visitDate?: string;
};

export const medicalRecordsApi = {
  // 查询病历列表（GET /api/medical-records，可选 patientId/doctorId 过滤）
  async list(params?: { patientId?: number; doctorId?: number }): Promise<MedicalRecord[]> {
    const resp = await request.get<ApiResponse<MedicalRecord[]>>("/api/medical-records", { params });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增病历（POST /api/medical-records）
  async create(payload: CreateMedicalRecordPayload): Promise<MedicalRecord> {
    const resp = await request.post<ApiResponse<MedicalRecord>>("/api/medical-records", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 更新病历信息（PUT /api/medical-records/{id}）
  async update(id: number, payload: UpdateMedicalRecordPayload): Promise<MedicalRecord> {
    const resp = await request.put<ApiResponse<MedicalRecord>>(`/api/medical-records/${id}`, payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 删除病历（DELETE /api/medical-records/{id}，仅管理员可用）
  async delete(id: number): Promise<void> {
    const resp = await request.delete<ApiResponse<void>>(`/api/medical-records/${id}`);
    if (!resp.data.success) throw new Error(resp.data.message ?? "删除失败");
  }
};
