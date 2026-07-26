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

// ==================== AI 聊天 (Chat) ====================

export interface ChatConversationResponse {
  id: number;
  title: string;
  createTime: string;
  updateTime: string;
  lastMessage: string;
}

export interface ChatMessageResponse {
  id: number;
  role: "user" | "assistant";
  content: string;
  createTime: string;
}

/** 聊天 SSE 流式 done 事件的数据结构 */
export interface ChatDoneData {
  conversationId: number;
  reply: string;
}

export const chatApi = {
  /** 同步聊天（后备方案） */
  async chat(
    message: string,
    conversationId?: number
  ): Promise<{ conversationId: number; reply: string }> {
    const resp = await request.post<
      ApiResponse<{ conversationId: number; reply: string }>
    >("/api/ai/chat", {
      conversationId: conversationId ?? null,
      message,
    });
    if (!resp.data.success)
      throw new Error(resp.data.message ?? "聊天失败");
    return resp.data.data!;
  },

  /** SSE 流式聊天（主要接口） */
  streamChat(
    message: string,
    conversationId: number | null,
    callbacks: StreamCallbacks
  ): AbortController {
    return streamChatSse(
      "/api/ai/chat/stream",
      message,
      conversationId,
      callbacks
    );
  },

  /** 获取对话列表 */
  async listConversations(): Promise<ChatConversationResponse[]> {
    const resp = await request.get<
      ApiResponse<ChatConversationResponse[]>
    >("/api/ai/conversations");
    if (!resp.data.success)
      throw new Error(resp.data.message ?? "获取对话列表失败");
    return resp.data.data ?? [];
  },

  /** 删除对话 */
  async deleteConversation(id: number): Promise<void> {
    const resp = await request.delete<ApiResponse<null>>(
      `/api/ai/conversations/${id}`
    );
    if (!resp.data.success)
      throw new Error(resp.data.message ?? "删除对话失败");
  },

  /** 获取指定对话的消息列表 */
  async getMessages(conversationId: number): Promise<ChatMessageResponse[]> {
    const resp = await request.get<
      ApiResponse<ChatMessageResponse[]>
    >(`/api/ai/conversations/${conversationId}/messages`);
    if (!resp.data.success)
      throw new Error(resp.data.message ?? "获取消息失败");
    return resp.data.data ?? [];
  },
};

/** 聊天专用 SSE 流式请求（body 格式与诊断不同：{ conversationId, message }） */
function streamChatSse(
  endpoint: string,
  message: string,
  conversationId: number | null,
  callbacks: StreamCallbacks
): AbortController {
  const controller = new AbortController();
  const token = getToken();

  fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ conversationId, message }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        callbacks.onError(
          `AI 服务返回错误，状态码：${response.status}`
        );
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
