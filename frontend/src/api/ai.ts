import { request } from "../utils/request";
import type { ApiResponse } from "./health";

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export const aiApi = {
  // AI 智能问诊（支持多轮对话历史）
  async askDiagnosis(symptoms: string, history: ChatMessage[] = []): Promise<string> {
    const resp = await request.post<ApiResponse<{ reply: string }>>("/api/ai/diagnosis", {
      symptoms,
      history
    });
    if (!resp.data.success) throw new Error(resp.data.message ?? "AI 问诊失败");
    return resp.data.data!.reply;
  }
};
