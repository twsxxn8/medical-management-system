import { request } from "../utils/request";
import { getToken } from "../utils/authStorage";
import type { ApiResponse } from "./health";

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

/**
 * SSE 流式回调接口：前端传入回调处理每个 token 和完成事件。
 */
export interface StreamCallbacks {
  /** 收到一个 token */
  onToken: (token: string) => void;
  /** 流完成，fullContent 为完整回复 */
  onDone: (fullContent: string) => void;
  /** 发生错误 */
  onError: (message: string) => void;
}

export const aiApi = {
  /** AI 智能问诊（同步版本，兼容旧版） */
  async askDiagnosis(symptoms: string, history: ChatMessage[] = []): Promise<string> {
    const resp = await request.post<ApiResponse<{ reply: string }>>("/api/ai/diagnosis", {
      symptoms,
      history
    });
    if (!resp.data.success) throw new Error(resp.data.message ?? "AI 问诊失败");
    return resp.data.data!.reply;
  },

  /**
   * AI 智能问诊（SSE 流式版本）
   *
   * 使用 fetch + ReadableStream 读取 SSE 事件流，
   * 每收到一个 token 立即回调 {@link StreamCallbacks#onToken}，
   * 流结束后回调 {@link StreamCallbacks#onDone}。
   *
   * 前端断连时 fetch 的 AbortController 会自动取消请求，
   * 后端 SseEmitter 捕获 IOException 后也会关闭上游 LLM 连接。
   */
  streamDiagnosis(
    symptoms: string,
    history: ChatMessage[],
    callbacks: StreamCallbacks
  ): AbortController {
    const controller = new AbortController();
    const token = getToken();

    fetch("/api/ai/diagnosis/stream", {
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

          // SSE 事件以 \n\n 分隔
          const parts = buffer.split("\n\n");
          // 最后一段可能不完整，保留到下次
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
};
