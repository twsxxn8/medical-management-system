import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `AppointmentEntity` 对齐的字段（/api/appointments 返回）
export type Appointment = {
  id: number;
  patientId: number;
  doctorId: number;
  departmentId: number;
  appointmentDate: string; // LocalDate -> ISO 字符串（yyyy-MM-dd）
  timeSlot: string; // 例如：上午/下午/晚上
  status: number; // 0=未确认,1=已确认,2=已完成,3=已取消,4=已过期
  reason?: string | null;
  remark?: string | null;
  createTime?: string;
  updateTime?: string;
};

// 新增预约入参（与后端 `CreateAppointmentRequest` 对齐；后端会默认 status=0）
export type CreateAppointmentPayload = {
  patientId: number;
  doctorId: number;
  departmentId: number;
  appointmentDate: string; // yyyy-MM-dd
  timeSlot: string;
  reason?: string;
  remark?: string;
};

// 更新预约入参（与后端 `UpdateAppointmentRequest` 对齐）
export type UpdateAppointmentPayload = {
  patientId: number;
  doctorId: number;
  departmentId: number;
  appointmentDate: string;
  timeSlot: string;
  status?: number;
  reason?: string;
  remark?: string;
};

export const appointmentsApi = {
  // 查询预约列表（GET /api/appointments，可选 patientId/doctorId/date 过滤）
  async list(params?: { patientId?: number; doctorId?: number; date?: string }): Promise<Appointment[]> {
    const resp = await request.get<ApiResponse<Appointment[]>>("/api/appointments", { params });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增预约（POST /api/appointments）
  async create(payload: CreateAppointmentPayload): Promise<Appointment> {
    const resp = await request.post<ApiResponse<Appointment>>("/api/appointments", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 更新预约（PUT /api/appointments/{id}）
  async update(id: number, payload: UpdateAppointmentPayload): Promise<Appointment> {
    const resp = await request.put<ApiResponse<Appointment>>(`/api/appointments/${id}`, payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 修改预约状态（PUT /api/appointments/{id}/status）
  async updateStatus(id: number, status: number): Promise<Appointment> {
    const resp = await request.put<ApiResponse<Appointment>>(`/api/appointments/${id}/status`, { status });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  },

  // 删除预约（DELETE /api/appointments/{id}，仅管理员可用）
  async delete(id: number): Promise<void> {
    const resp = await request.delete<ApiResponse<void>>(`/api/appointments/${id}`);
    if (!resp.data.success) throw new Error(resp.data.message ?? "删除失败");
  }
};

