<template>
  <div class="ai-consult-page">
    <el-card class="chat-card" shadow="hover">
      <template #header>
        <div class="chat-header">
          <div class="title">
            <el-icon><Monitor /></el-icon>
            <span>AI 智能问诊助手</span>
            <el-tag size="small" type="success" effect="plain" style="margin-left: 8px">结构化</el-tag>
          </div>
          <div class="header-actions">
            <el-button v-if="streaming" type="warning" link size="small" @click="stopStreaming">
              <el-icon><VideoPause /></el-icon>
              停止生成
            </el-button>
            <el-button type="danger" link size="small" @click="clearChat">
              <el-icon><Delete /></el-icon>
              清空对话
            </el-button>
          </div>
        </div>
      </template>

      <!-- 快捷症状按钮 -->
      <div class="quick-symptoms">
        <span class="quick-label">常见症状：</span>
        <el-tag
          v-for="item in quickSymptoms"
          :key="item"
          class="symptom-tag"
          type="info"
          effect="plain"
          size="small"
          @click="selectQuickSymptom(item)"
        >
          {{ item }}
        </el-tag>
      </div>

      <!-- 聊天记录区 -->
      <div ref="chatBox" class="chat-box">
        <div v-if="messages.length === 0" class="empty-tip">
          <el-icon :size="48" color="#dcdfe6"><ChatDotRound /></el-icon>
          <p>请描述您的症状，AI 助手将为您提供初步建议</p>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-row', msg.role === 'user' ? 'user-row' : 'ai-row']"
        >
          <div class="avatar">
            <el-avatar :size="36" :icon="msg.role === 'user' ? UserFilled : FirstAidKit" />
          </div>
          <div class="message-content">
            <!-- 文本模式：显示原始内容 -->
            <div v-if="!msg.structured" class="message-bubble" v-html="formatMarkdown(msg.content)"></div>

            <!-- 结构化模式：显示摘要 + 卡片 -->
            <div v-else class="message-bubble">
              <div class="ai-summary">{{ msg.content }}</div>
            </div>

            <!-- 缓存命中标记 -->
            <el-tag
              v-if="msg.role === 'assistant' && msg.cached && !msg.streaming"
              size="small"
              type="success"
              effect="plain"
              style="margin-top: 4px"
            >
              💾 缓存命中
            </el-tag>

            <!-- 打字光标 -->
            <span v-if="msg.streaming" class="typing-cursor">|</span>

            <!-- 结构化卡片：仅在流式完成后渲染 -->
            <div
              v-if="msg.role === 'assistant' && !msg.streaming && msg.structured"
              class="structured-cards"
            >
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-card class="struct-card" shadow="never">
                    <template #header>
                      <div class="struct-header">
                        <el-icon><WarningFilled /></el-icon>
                        <span>可能疾病方向</span>
                      </div>
                    </template>
                    <div v-if="msg.diseases && msg.diseases.length">
                      <div v-for="(d, i) in msg.diseases" :key="i" class="disease-item">
                        <span class="disease-name">{{ d.name }}</span>
                        <el-tag
                          :type="d.probability === '高' ? 'danger' : d.probability === '中' ? 'warning' : 'info'"
                          size="small"
                          effect="plain"
                        >{{ d.probability }}</el-tag>
                        <p class="disease-desc">{{ d.description }}</p>
                      </div>
                    </div>
                    <div v-else class="no-data">详见上方分析</div>
                  </el-card>
                </el-col>
                <el-col :span="12">
                  <el-card class="struct-card" shadow="never">
                    <template #header>
                      <div class="struct-header">
                        <el-icon><FirstAidKit /></el-icon>
                        <span>建议检查项目</span>
                      </div>
                    </template>
                    <ul v-if="msg.checks && msg.checks.length">
                      <li v-for="(c, i) in msg.checks" :key="i">{{ c }}</li>
                    </ul>
                    <div v-else class="no-data">详见上方分析</div>
                  </el-card>
                </el-col>
              </el-row>
              <el-row :gutter="12" style="margin-top: 12px">
                <el-col :span="12">
                  <el-card class="struct-card" shadow="never">
                    <template #header>
                      <div class="struct-header">
                        <el-icon><OfficeBuilding /></el-icon>
                        <span>建议就诊科室</span>
                      </div>
                    </template>
                    <div class="dept-text">{{ msg.department || "详见上方分析" }}</div>
                    <div v-if="msg.urgency" style="margin-top: 8px">
                      <el-tag
                        :type="msg.urgency === '立即就医' ? 'danger' : msg.urgency === '尽快就诊' ? 'warning' : 'success'"
                        size="small"
                      >{{ msg.urgency }}</el-tag>
                    </div>
                  </el-card>
                </el-col>
                <el-col :span="12">
                  <el-card class="struct-card" shadow="never">
                    <template #header>
                      <div class="struct-header">
                        <el-icon><InfoFilled /></el-icon>
                        <span>日常注意事项</span>
                      </div>
                    </template>
                    <ul v-if="msg.precautions && msg.precautions.length">
                      <li v-for="(p, i) in msg.precautions" :key="i">{{ p }}</li>
                    </ul>
                    <div v-else class="no-data">详见上方分析</div>
                  </el-card>
                </el-col>
              </el-row>

              <div v-if="msg.note" class="ai-note">
                <el-icon><InfoFilled /></el-icon>
                {{ msg.note }}
              </div>

              <div class="copy-row">
                <el-button type="primary" link size="small" @click="copyReply(msg.content)">
                  <el-icon><CopyDocument /></el-icon>
                  复制建议
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="请描述您的症状，例如：最近三天头痛、发热38度，伴有咳嗽和乏力..."
          maxlength="500"
          show-word-limit
          :disabled="streaming"
          @keydown.enter.prevent="handleSend"
        />
        <el-button
          type="primary"
          :loading="streaming"
          :disabled="!inputText.trim() || streaming"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>

      <el-alert type="warning" :closable="false" style="margin-top: 12px">
        AI 生成内容仅供参考，不构成医疗诊断。如有不适，请及时就医。
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from "vue";
import {
  ChatDotRound,
  Monitor,
  UserFilled,
  FirstAidKit,
  OfficeBuilding,
  InfoFilled,
  WarningFilled,
  CopyDocument,
  Delete,
  VideoPause
} from "@element-plus/icons-vue";
import { aiApi, type ChatMessage, type DiagnosisResult } from "../api/ai";
import { ElMessage } from "element-plus";

/** 前端展示用消息（含扩展字段） */
interface DisplayMessage extends ChatMessage {
  streaming?: boolean;
  /** 是否为结构化模式 */
  structured?: boolean;
  /** 是否来自语义缓存 */
  cached?: boolean;
  /** 结构化字段（来自 DiagnosisResult） */
  diseases?: { name: string; probability: string; description: string }[];
  checks?: string[];
  department?: string;
  urgency?: string;
  precautions?: string[];
  note?: string;
}

const messages = ref<DisplayMessage[]>([]);
const inputText = ref("");
const streaming = ref(false);
const chatBox = ref<HTMLElement | null>(null);
let currentAbortController: AbortController | null = null;

const quickSymptoms = [
  "头痛、头晕",
  "发热、咳嗽",
  "腹痛、腹泻",
  "胸闷、心悸",
  "皮肤瘙痒、红疹",
  "关节疼痛"
];

function selectQuickSymptom(text: string) {
  inputText.value = text + "，请问可能是什么问题？";
}

function handleSend() {
  const text = inputText.value.trim();
  if (!text || streaming.value) return;

  messages.value.push({ role: "user", content: text });
  inputText.value = "";
  streaming.value = true;

  // 创建 AI 占位消息
  const aiMsg: DisplayMessage = { role: "assistant", content: "", streaming: true, structured: true, cached: false };
  messages.value.push(aiMsg);
  scrollToBottom();

  // 构建历史（排除当前占位消息）
  const history: ChatMessage[] = [];
  for (const m of messages.value) {
    if (m.streaming) continue;
    history.push({ role: m.role, content: m.content });
  }

  // 使用结构化流式接口
  currentAbortController = aiApi.streamDiagnosisStructured(text, history, {
    onToken(token: string) {
      // 过滤 JSON 标记 ````json 和 ```` 代码块标记
      if (token === "```json" || token === "```") return;
      // 去除 JSON 开头可能多余的空白
      if (!aiMsg.content && token === "\n") return;
      aiMsg.content += token;
      scrollToBottom();
    },
    onCacheHit(fromCache: boolean) {
      aiMsg.cached = fromCache;
    },
    onDone(fullContent: string) {
      // 去除可能的 markdown 代码块包裹
      let jsonStr = fullContent.trim();
      if (jsonStr.startsWith("```json")) jsonStr = jsonStr.substring(7);
      if (jsonStr.startsWith("```")) jsonStr = jsonStr.substring(3);
      if (jsonStr.endsWith("```")) jsonStr = jsonStr.substring(0, jsonStr.length - 3);
      jsonStr = jsonStr.trim();

      try {
        const result: DiagnosisResult = JSON.parse(jsonStr);
        aiMsg.diseases = result.possible_diseases || [];
        aiMsg.checks = result.recommended_checks || [];
        aiMsg.department = result.recommended_department || "";
        aiMsg.urgency = result.urgency || "";
        aiMsg.precautions = result.precautions || [];
        aiMsg.note = result.note || "";
      } catch (e) {
        // JSON 解析失败，继续显示原始文本
        aiMsg.structured = false;
      }

      aiMsg.streaming = false;
      streaming.value = false;
      currentAbortController = null;
      scrollToBottom();
    },
    onError(message: string) {
      if (aiMsg.content) {
        aiMsg.content += "\n\n[流中断: " + message + "]";
      } else {
        aiMsg.content = "请求失败：" + message;
      }
      aiMsg.structured = false;
      aiMsg.streaming = false;
      streaming.value = false;
      currentAbortController = null;
      ElMessage.error(message);
    }
  });
}

function stopStreaming() {
  if (currentAbortController) {
    currentAbortController.abort();
    currentAbortController = null;
  }
  const lastMsg = messages.value[messages.value.length - 1];
  if (lastMsg && lastMsg.streaming) {
    lastMsg.streaming = false;
    lastMsg.structured = false;
    if (lastMsg.content) {
      lastMsg.content += "\n\n[用户中断]";
    } else {
      lastMsg.content = "[已中断]";
    }
  }
  streaming.value = false;
}

function clearChat() {
  stopStreaming();
  messages.value = [];
  inputText.value = "";
}

function copyReply(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success("已复制到剪贴板");
  });
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight;
    }
  });
}

watch(messages, () => scrollToBottom(), { deep: true });

function formatMarkdown(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/^\s*[-*]\s+(.*)$/gm, "<li>$1</li>")
    .replace(/\n/g, "<br>");
}
</script>

<style scoped>
.ai-consult-page {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header .title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.quick-symptoms {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-label {
  color: #606266;
  font-size: 13px;
}

.symptom-tag {
  cursor: pointer;
}

.chat-box {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 12px;
}

.empty-tip {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.user-row {
  flex-direction: row-reverse;
}

.message-content {
  max-width: 72%;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 14px;
  word-break: break-word;
}

.ai-row .message-bubble {
  background-color: #ffffff;
  border: 1px solid #e4e7ed;
  color: #303133;
}

.user-row .message-bubble {
  background-color: #409eff;
  color: #ffffff;
}

.ai-summary {
  font-size: 13px;
  line-height: 1.5;
  opacity: 0.8;
}

.typing-cursor {
  display: inline;
  color: #409eff;
  font-weight: bold;
  font-size: 16px;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* 结构化卡片 */
.structured-cards {
  margin-top: 12px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.struct-card {
  background-color: #ffffff;
}

.struct-card :deep(.el-card__header) {
  padding: 10px 14px;
}

.struct-card :deep(.el-card__body) {
  padding: 12px 14px;
}

.struct-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
  color: #303133;
}

.struct-card ul {
  margin: 0;
  padding-left: 18px;
}

.struct-card li {
  margin-bottom: 6px;
  line-height: 1.5;
}

.disease-item {
  margin-bottom: 10px;
}

.disease-item:last-child {
  margin-bottom: 0;
}

.disease-name {
  font-weight: bold;
  margin-right: 8px;
}

.disease-desc {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: #606266;
}

.dept-text {
  color: #409eff;
  font-weight: bold;
  font-size: 15px;
}

.no-data {
  color: #909399;
  font-size: 13px;
}

.ai-note {
  margin-top: 12px;
  padding: 8px 12px;
  background-color: #fdf6ec;
  border-left: 3px solid #e6a23c;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.copy-row {
  margin-top: 8px;
  text-align: right;
}

.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.input-area .el-input {
  flex: 1;
}

.input-area .el-button {
  height: 54px;
  width: 90px;
}
</style>
