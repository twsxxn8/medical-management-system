import { request } from "../utils/request";
import type { ApiResponse } from "./health";

export interface HealthComponents {
  database?: string;
  redis?: string;
}

export interface HealthInfo {
  status: string;
  components?: Record<string, any>;
}

export interface CircuitBreakerInfo {
  name: string;
  state: "CLOSED" | "OPEN" | "HALF_OPEN";
  failureRate: string;
  slowCallRate: string;
  numberOfSuccessfulCalls: number;
  numberOfFailedCalls: number;
  numberOfSlowCalls: number;
  numberOfNotPermittedCalls: number;
}

export interface ProviderInfo {
  name: string;
  model: string;
  priority: string;
}

export interface AiOverview {
  providers: ProviderInfo[];
  circuitBreakers: CircuitBreakerInfo[];
  overallStatus: "HEALTHY" | "DEGRADED";
}

export const monitorApi = {
  /** 系统健康检查（含 DB/Redis 状态） */
  async getHealth(): Promise<HealthInfo> {
    const resp = await request.get<ApiResponse<HealthInfo>>("/api/health");
    if (!resp.data.success) throw new Error(resp.data.message ?? "请求失败");
    return resp.data.data!;
  },

  /** 断路器状态一览 */
  async getCircuitBreakers(): Promise<CircuitBreakerInfo[]> {
    const resp = await request.get<ApiResponse<CircuitBreakerInfo[]>>(
      "/api/health/circuit-breakers"
    );
    if (!resp.data.success) throw new Error(resp.data.message ?? "请求失败");
    return resp.data.data ?? [];
  },

  /** AI 服务概览 */
  async getAiStats(): Promise<AiOverview> {
    const resp = await request.get<ApiResponse<AiOverview>>("/api/ai/stats");
    if (!resp.data.success) throw new Error(resp.data.message ?? "请求失败");
    return resp.data.data!;
  }
};
