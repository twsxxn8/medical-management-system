<template>
  <div class="ai-consult-page">
    <el-card class="chat-card" shadow="hover">
      <template #header>
        <div class="chat-header">
          <div class="title">
            <el-icon><Monitor /></el-icon>
            <span>AI 智能问诊助手</span>
          </div>
          <el-button type="danger" link size="small" @click="clearChat">
            <el-icon><Delete /></el-icon>
            清空对话
          </el-button>
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

            <!-- 结构化卡片：仅对 AI 第一条回复解析 -->
            <div v-if="msg.role === 'assistant' && index === firstAiIndex" class="structured-cards">
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
                      <li v-for="(d, i) in parseList(msg.content, '可能的疾病方向')" :key="i">{{ d }}</li>
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
                      <li v-for="(d, i) in parseList(msg.content, '建议的检查项目')" :key="i">{{ d }}</li>
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
                    <div class="dept-text">{{ parseSection(msg.content, '建议就诊的科室') }}</div>
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
                      <li v-for="(d, i) in parseList(msg.content, '日常注意事项')" :key="i">{{ d }}</li>
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

        <!-- AI 正在输入提示 -->
        <div v-if="loading" class="message-row ai-row">
          <div class="avatar">
            <el-avatar :size="36" :icon="FirstAidKit" />
          </div>
          <div class="message-content">
            <div class="message-bubble loading-bubble">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>AI 正在分析中...</span>
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
          :disabled="loading"
          @keydown.enter.prevent="handleSend"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!inputText.trim() || loading"
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
  Loading,
  Delete
} from "@element-plus/icons-vue";
import { aiApi, type ChatMessage } from "../api/ai";
import { ElMessage } from "element-plus";

const messages = ref<ChatMessage[]>([]);
const inputText = ref("");
const loading = ref(false);
const chatBox = ref<HTMLElement | null>(null);

const quickSymptoms = [
  "头痛、头晕",
  "发热、咳嗽",
  "腹痛、腹泻",
  "胸闷、心悸",
  "皮肤瘙痒、红疹",
  "关节疼痛"
];

const firstAiIndex = computed(() => messages.value.findIndex(m => m.role === "assistant"));

function selectQuickSymptom(text: string) {
  inputText.value = text + "，请问可能是什么问题？";
}

async function handleSend() {
  const text = inputText.value.trim();
  if (!text || loading.value) return;

  messages.value.push({ role: "user", content: text });
  inputText.value = "";
  loading.value = true;
  scrollToBottom();

  try {
    const history = messages.value.filter(m => m.role !== "assistant" || m !== messages.value[messages.value.length - 1]);
    const reply = await aiApi.askDiagnosis(text, history);
    messages.value.push({ role: "assistant", content: reply });
  } catch (e: any) {
    messages.value.push({ role: "assistant", content: "请求失败：" + (e.message || "未知错误") });
  } finally {
    loading.value = false;
    nextTick(scrollToBottom);
  }
}

function clearChat() {
  messages.value = [];
  inputText.value = "";
}

function copyReply(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success("已复制到剪贴板");
  });
}

function scrollToBottom() {
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight;
  }
}

watch(messages, () => nextTick(scrollToBottom), { deep: true });

function formatMarkdown(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/^\s*[-*]\s+(.*)$/gm, "<li>$1</li>")
    .replace(/\n/g, "<br>");
}

function parseSection(text: string, sectionName: string): string {
  const lines = text.split("\n");
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes(sectionName)) {
      const next = lines[i + 1];
      if (next && !next.includes("：") && !next.includes("建议") && !next.includes("注意")) {
        return next.replace(/^[-*\d.\s]+/, "").trim();
      }
    }
  }
  return "详见上方分析";
}

function parseList(text: string, sectionName: string): string[] {
  const lines = text.split("\n");
  const result: string[] = [];
  let inSection = false;
  for (const line of lines) {
    if (line.includes(sectionName)) {
      inSection = true;
      continue;
    }
    if (inSection) {
      if (/^\s*\d+\.\s|^\s*[-*]\s/.test(line)) {
        result.push(line.replace(/^\s*\d+\.\s|^\s*[-*]\s/, "").trim());
      } else if (line.trim() === "" || line.includes("：")) {
        break;
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

.loading-bubble {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
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
