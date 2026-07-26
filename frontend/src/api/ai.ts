import { request } from "../utils/request";
import { getToken } from "../utils/authStorage";
import type { ApiResponse } from "./health";

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

/** AI 结构化诊断结果（与后端 DiagnosisResult 对齐） */
export interface DiagnosisResult {
  possible_diseases: DiseaseItem[];
  recommended_checks: string[];
  recommended_department: string;
  urgency: "立即就医" | "尽快就诊" | "可观察";
  precautions: string[];
  note?: string;
}

export interface DiseaseItem {
  name: string;
  probability: "高" | "中" | "低";
  description: string;
}

/** SSE 流式回调接口 */
export interface StreamCallbacks {
  onToken: (token: string) => void;
  onDone: (fullContent: string) => void;
  onError: (message: string) => void;
  /** 缓存命中回调（fromCache SSE 事件触发） */
  onCacheHit?: (fromCache: boolean) => void;
}

export const aiApi = {
  // ==================== 文本模式（兼容旧版） ====================

  /** AI 智能问诊（同步版本） */
  async askDiagnosis(symptoms: string, history: ChatMessage[] = []): Promise<string> {
    const resp = await request.post<ApiResponse<{ reply: string }>>("/api/ai/diagnosis", {
      symptoms,
      history
    });
    if (!resp.data.success) throw new Error(resp.data.message ?? "AI 问诊失败");
    return resp.data.data!.reply;
  },

  /** AI 智能问诊（SSE 流式文本） */
  streamDiagnosis(
    symptoms: string,
    history: ChatMessage[],
    callbacks: StreamCallbacks
  ): AbortController {
    return streamSse("/api/ai/diagnosis/stream", symptoms, history, callbacks);
  },

  // ==================== 结构化模式（JSON Schema） ====================

  /** AI 智能问诊（同步结构化，返回 DiagnosisResult） */
  async askDiagnosisStructured(
    symptoms: string,
    history: ChatMessage[] = []
  ): Promise<DiagnosisResult> {
    const resp = await request.post<ApiResponse<DiagnosisResult>>(
      "/api/ai/diagnosis/structured",
      { symptoms, history }
    );
    if (!resp.data.success) throw new Error(resp.data.message ?? "AI 问诊失败");
    return resp.data.data!;
  },

  /** AI 智能问诊（SSE 流式结构化：打字机效果 + onDone 接收完整 DiagnosisResult） */
  streamDiagnosisStructured(
    symptoms: string,
    history: ChatMessage[],
    callbacks: StreamCallbacks
  ): AbortController {
    return streamSse("/api/ai/diagnosis/structured/stream", symptoms, history, callbacks);
  }
};

/** 通用 SSE 流式请求：fetch + ReadableStream 解析 */
function streamSse(
  endpoint: string,
  symptoms: string,
  history: ChatMessage[],
  callbacks: StreamCallbacks
): AbortController {
  const controller = new AbortController();
  const token = getToken();

  fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ symptoms, history }),
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        callbacks.onError(`AI 服务返回错误，状态码：${response.status}`);
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        callbacks.onError("浏览器不支持流式读取");
        return;
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split("\n\n");
        buffer = parts.pop() || "";

        for (const part of parts) {
          if (!part.trim()) continue;

          const lines = part.split("\n");
          let eventName = "";
          let eventData = "";

          for (const line of lines) {
            if (line.startsWith("event:")) {
              eventName = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
              eventData = line.substring(5).trim();
            }
          }

          if (eventName === "token" && eventData) {
            callbacks.onToken(eventData);
          } else if (eventName === "done" && eventData) {
            callbacks.onDone(eventData);
          } else if (eventName === "fromCache" && eventData) {
            callbacks.onCacheHit?.(eventData === "true");
          } else if (eventName === "error") {
            callbacks.onError(eventData || "AI 服务调用失败");
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== "AbortError") {
        callbacks.onError("网络请求失败：" + err.message);
      }
    });

  return controller;
}
