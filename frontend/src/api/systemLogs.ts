import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `SystemLogEntity` 对齐的字段（/api/system-logs 返回）
export type SystemLog = {
  id: number;
  userId: number;
  operation: string;
  method?: string | null;
  params?: string | null;
  ipAddress?: string | null;
  duration?: number | null;
  createTime?: string;
};

export const systemLogsApi = {
  // 查询系统日志列表（GET /api/system-logs）
  async list(): Promise<SystemLog[]> {
    const resp = await request.get<ApiResponse<SystemLog[]>>("/api/system-logs");
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  }
};
