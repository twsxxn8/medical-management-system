import { request } from "../utils/request";

export type ApiResponse<T> = {
  success: boolean;
  message?: string | null;
  data?: T;
};

export const healthApi = {
  async getHealth(): Promise<{ status: string }> {
    const resp = await request.get<ApiResponse<{ status: string }>>("/api/health");
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    if (!resp.data.data) throw new Error("No data");
    return resp.data.data;
  }
};

