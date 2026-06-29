import { request } from "../utils/request";
import type { ApiResponse } from "./health";

// 与后端 `MedicineStockLogEntity` 对齐的字段
export type MedicineStockLog = {
  id: number;
  medicineId: number;
  type: number; // 1=入库，2=出库
  quantity: number;
  operatorId: number;
  remark?: string | null;
  createTime?: string;
};

// 新增库存日志入参
export type CreateStockLogPayload = {
  medicineId: number;
  type: number;
  quantity: number;
  operatorId: number;
  remark?: string;
};

export const stockLogsApi = {
  // 查询库存日志列表（可选 medicineId 过滤）
  async list(params?: { medicineId?: number }): Promise<MedicineStockLog[]> {
    const resp = await request.get<ApiResponse<MedicineStockLog[]>>("/api/medicine-stock-logs", { params });
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? [];
  },

  // 新增库存变动（POST /api/medicine-stock-logs）
  async create(payload: CreateStockLogPayload): Promise<MedicineStockLog> {
    const resp = await request.post<ApiResponse<MedicineStockLog>>("/api/medicine-stock-logs", payload);
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  }
};
