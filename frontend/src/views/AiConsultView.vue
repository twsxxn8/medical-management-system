<template>
  <div class="ai-consult-page">
    <el-card class="chat-card" shadow="hover">
      <template #header>
        <div class="chat-header">
          <div class="title">
            <el-icon><Monitor /></el-icon>
            <span>AI 智能问诊助手</span>
            <el-tag size="small" type="success" effect="plain" style="margin-left: 8px">SSE 流式</el-tag>
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
            <div class="message-bubble" v-html="formatMarkdown(msg.content)"></div>

            <!-- 流式生成中：显示闪烁光标 -->
            <span v-if="msg.streaming" class="typing-cursor">|</span>

            <!-- 结构化卡片：仅在流式完成后对 AI 第一条回复解析 -->
            <div
              v-if="msg.role === 'assistant' && !msg.streaming && index === firstAiIndex"
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
                    <ul>
                      <li v-for="(d, i) in parseList(msg.content, '可能疾病方向|可能的疾病方向')" :key="i">{{ d }}</li>
                    </ul>
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
                    <ul>
                      <li v-for="(d, i) in parseList(msg.content, '建议的检查项目|建议检查项目|建议检查')" :key="i">{{ d }}</li>
                    </ul>
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
                    <div class="dept-text">{{ parseSection(msg.content, "就诊的科室|就诊科室|就诊") }}</div>
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
                    <ul>
                      <li v-for="(d, i) in parseList(msg.content, '日常注意事项|注意事项|日常')" :key="i">{{ d }}</li>
                    </ul>
                  </el-card>
                </el-col>
              </el-row>

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
import { aiApi, type ChatMessage } from "../api/ai";
import { ElMessage } from "element-plus";

/** 消息列表（含 streaming 标记） */
interface DisplayMessage extends ChatMessage {
  /** 该消息是否正在流式生成中 */
  streaming?: boolean;
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

const firstAiIndex = computed(() =>
  messages.value.findIndex(m => m.role === "assistant" && !m.streaming)
);

function selectQuickSymptom(text: string) {
  inputText.value = text + "，请问可能是什么问题？";
}

function handleSend() {
  const text = inputText.value.trim();
  if (!text || streaming.value) return;

  // 添加用户消息
  messages.value.push({ role: "user", content: text });
  inputText.value = "";
  streaming.value = true;

  // 创建 AI 占位消息（streaming = true）
  const aiMsg: DisplayMessage = { role: "assistant", content: "", streaming: true };
  messages.value.push(aiMsg);
  scrollToBottom();

  // 构建历史（排除当前正在流式生成的占位消息）
  const history: ChatMessage[] = [];
  for (const m of messages.value) {
    if (m.streaming) continue; // 跳过当前流式消息
    history.push({ role: m.role, content: m.content });
  }

  // 流式调用
  currentAbortController = aiApi.streamDiagnosis(text, history, {
    onToken(token: string) {
      aiMsg.content += token;
      scrollToBottom();
    },
    onDone(fullContent: string) {
      aiMsg.content = fullContent;
      aiMsg.streaming = false;
      streaming.value = false;
      currentAbortController = null;
    },
    onError(message: string) {
      if (aiMsg.content) {
        // 已有部分内容，追加错误提示
        aiMsg.content += "\n\n[流中断: " + message + "]";
      } else {
        aiMsg.content = "请求失败：" + message;
      }
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
  // 将最后一个流式消息标记为完成
  const lastMsg = messages.value[messages.value.length - 1];
  if (lastMsg && lastMsg.streaming) {
    lastMsg.streaming = false;
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

/* ===== 文本格式化（同旧版） ===== */

function formatMarkdown(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/^\s*[-*]\s+(.*)$/gm, "<li>$1</li>")
    .replace(/\n/g, "<br>");
}

/** 通用段落级解析：匹配 sectionName（支持 | 分隔的多关键词） */
function parseSection(text: string, sectionNames: string): string {
  const normalized = text.replace(/###\s*/g, "");
  const lines = normalized.split("\n");
  const keywords = sectionNames.split("|");
  let inTarget = false;
  const result: string[] = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    const matched = keywords.some(k => new RegExp(k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i").test(trimmed));
    if (matched) {
      inTarget = true;
      continue;
    }

    if (inTarget && /^#{1,3}\s/.test(trimmed)) break;
    if (inTarget && /^[可能|建议|日常|就诊|推荐|注意|免责|声明|以上]/.test(trimmed)) break;

    if (inTarget) {
      const cleaned = trimmed.replace(/^\d+[\.\)、]\s*/, "").replace(/^[-*]\s*/, "");
      if (cleaned && !cleaned.startsWith("##") && !cleaned.startsWith("---")) {
        result.push(cleaned);
      }
    }
  }
  return result.length ? result.join(" / ") : "详见上方分析";
}

/** 从文本中提取指定标题下的列表项（编号行） */
function parseList(text: string, sectionNames: string): string[] {
  const normalized = text.replace(/###\s*/g, "");
  const lines = normalized.split("\n");
  const keywords = sectionNames.split("|");
  const result: string[] = [];
  let inTarget = false;
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    const matched = keywords.some(k => new RegExp(k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i").test(trimmed));
    if (matched) {
      inTarget = true;
      continue;
    }

    if (inTarget && /^#{1,3}\s/.test(trimmed)) break;
    if (inTarget && /^[可能|建议|日常|就诊|推荐|注意|免责|声明|以上]/.test(trimmed)) break;

    if (inTarget) {
      const cleaned = trimmed
        .replace(/^\d+[\.\)、]\s*/, "")
        .replace(/^[-*]\s*/, "")
        .replace(/^[：:]/, "");
      if (cleaned && !cleaned.startsWith("##") && !cleaned.startsWith("---")) {
        result.push(cleaned);
      }
    }
  }
  return result.length ? result : ["详见上方分析"];
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
  max-width: 70%;
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

/* 打字光标闪烁动画 */
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

.structured-cards {
  margin-top: 12px;
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

.dept-text {
  color: #409eff;
  font-weight: bold;
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
