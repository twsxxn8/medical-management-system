import { request } from "../utils/request";
import type { ApiResponse } from "./health";

/**
 * 统计数据接口。
 */
export type StatisticsOverview = {
  totalPatients: number;
  totalDoctors: number;
  todayAppointments: number;
  totalDepartments: number;
};

/**
 * 首页统计 API。
 */
export const statisticsApi = {
  /**
   * 获取首页概览统计数据。
   */
  async getOverview(): Promise<StatisticsOverview> {
    const resp = await request.get<ApiResponse<StatisticsOverview>>("/api/statistics/overview");
    if (!resp.data.success) throw new Error(resp.data.message ?? "Request failed");
    return resp.data.data ?? { totalPatients: 0, totalDoctors: 0, todayAppointments: 0, totalDepartments: 0 };
  }
};
