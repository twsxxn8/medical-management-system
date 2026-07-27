<template>
  <div class="ai-chat-page">
    <!-- 左侧对话列表 -->
    <div class="chat-sidebar">
      <el-button type="primary" class="new-chat-btn" @click="handleNewChat" style="width: 100%">
        <el-icon><Plus /></el-icon>
        新对话
      </el-button>

      <div class="conversation-list">
        <div v-if="conversations.length === 0 && !loadingList" class="empty-conv">
          <p>暂无对话记录</p>
          <p class="hint">点击"新对话"开始</p>
        </div>

        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conv-item', { active: conv.id === activeConversationId }]"
          @click="handleSelectConversation(conv.id)"
        >
          <div class="conv-header">
            <span class="conv-title">{{ conv.title }}</span>
            <el-button
              type="danger"
              link
              size="small"
              @click.stop="handleDeleteConversation(conv.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <div class="conv-meta" v-if="conv.lastMessage">{{ conv.lastMessage }}</div>
          <div class="conv-time">{{ formatTime(conv.updateTime) }}</div>
        </div>
      </div>

      <div class="sidebar-footer">
        <el-alert type="warning" :closable="false">
          AI 生成内容仅供参考，不构成医疗诊断。如有不适，请及时就医。
        </el-alert>
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="chat-main">
      <!-- 顶部工具栏 -->
      <div class="chat-header">
        <div class="chat-title">
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ currentTitle || "AI 智能医疗助手" }}</span>
        </div>
        <div class="header-actions">
          <el-button
            v-if="streaming"
            type="warning"
            size="small"
            @click="handleStopStreaming"
          >
            <el-icon><VideoPause /></el-icon>
            停止生成
          </el-button>
        </div>
      </div>

      <!-- 聊天消息区 -->
      <div ref="chatBox" class="chat-box">
        <div v-if="messages.length === 0" class="empty-tip">
          <el-icon :size="64" color="#dcdfe6"><ChatDotRound /></el-icon>
          <p>我是您的智能医疗助手，有什么可以帮您的？</p>
          <div class="sample-questions">
            <el-tag
              v-for="q in sampleQuestions"
              :key="q"
              class="sample-tag"
              type="info"
              effect="plain"
              @click="handleSampleQuestion(q)"
            >
              {{ q }}
            </el-tag>
          </div>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-row', msg.role === 'user' ? 'user-row' : 'ai-row']"
        >
          <div class="avatar">
            <el-avatar
              :size="36"
              :icon="msg.role === 'user' ? UserFilled : FirstAidKit"
            />
          </div>
          <div class="message-content">
            <div class="message-bubble" v-html="formatContent(msg.content)"></div>

            <!-- 打字光标 -->
            <span v-if="msg.streaming" class="typing-cursor">|</span>

            <!-- 操作按钮（流式结束后显示） -->
            <div v-if="msg.role === 'assistant' && !msg.streaming && msg.content" class="msg-actions">
              <el-button type="primary" link size="small" @click="copyReply(msg.content)">
                <el-icon><CopyDocument /></el-icon>
                复制
              </el-button>
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
          placeholder="请输入您的健康问题..."
          maxlength="2000"
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from "vue";
import {
  ChatDotRound,
  Plus,
  UserFilled,
  FirstAidKit,
  CopyDocument,
  Delete,
  VideoPause
} from "@element-plus/icons-vue";
import { chatApi, type ChatDoneData } from "../api/ai";
import { ElMessage, ElMessageBox } from "element-plus";

/** 前端展示用聊天消息 */
interface DisplayChatMessage {
  role: "user" | "assistant";
  content: string;
  streaming?: boolean;
}

/** 对话列表 */
interface ConvItem {
  id: number;
  title: string;
  createTime: string;
  updateTime: string;
  lastMessage?: string;
}

// ==================== 状态 ====================

const conversations = ref<ConvItem[]>([]);
const activeConversationId = ref<number | null>(null);
const messages = ref<DisplayChatMessage[]>([]);
const inputText = ref("");
const streaming = ref(false);
const loadingList = ref(false);
const chatBox = ref<HTMLElement | null>(null);
let currentAbortController: AbortController | null = null;

/** 示例问题 */
const sampleQuestions = [
  "我最近经常头痛，可能是什么原因？",
  "感冒和流感有什么区别？",
  "高血压患者饮食需要注意什么？",
  "如何提高睡眠质量？",
  "体检报告显示血脂偏高怎么办？"
];

/** 当前对话标题 */
const currentTitle = computed(() => {
  if (!activeConversationId.value) return "";
  const conv = conversations.value.find(c => c.id === activeConversationId.value);
  return conv?.title || "";
});

// ==================== 生命周期 ====================

onMounted(async () => {
  await loadConversations();
});

// ==================== 对话管理 ====================

/** 加载对话列表 */
async function loadConversations() {
  loadingList.value = true;
  try {
    conversations.value = await chatApi.listConversations();
  } catch (e: any) {
    ElMessage.error(e.message ?? "获取对话列表失败");
  } finally {
    loadingList.value = false;
  }
}

/** 新建对话 */
function handleNewChat() {
  activeConversationId.value = null;
  messages.value = [];
  inputText.value = "";
  if (currentAbortController) {
    currentAbortController.abort();
    currentAbortController = null;
  }
  streaming.value = false;
  inputText.value = "";
}

/** 选择对话，加载历史消息 */
async function handleSelectConversation(id: number) {
  if (id === activeConversationId.value) return;

  // 切换前中断当前流
  if (streaming.value && currentAbortController) {
    currentAbortController.abort();
    streaming.value = false;
  }

  activeConversationId.value = id;
  try {
    const msgs = await chatApi.getMessages(id);
    messages.value = msgs.map(m => ({
      role: m.role as "user" | "assistant",
      content: m.content
    }));
    await scrollToBottom();
  } catch (e: any) {
    ElMessage.error(e.message ?? "获取消息失败");
    messages.value = [];
  }
}

/** 删除对话 */
async function handleDeleteConversation(id: number) {
  try {
    await ElMessageBox.confirm("确定删除该对话？删除后不可恢复。", "确认删除", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning"
    });
    await chatApi.deleteConversation(id);
    conversations.value = conversations.value.filter(c => c.id !== id);
    if (activeConversationId.value === id) {
      handleNewChat();
    }
    ElMessage.success("对话已删除");
  } catch (e: any) {
    if (e !== "cancel" && e?.message) {
      ElMessage.error(e.message ?? "删除失败");
    }
  }
}

// ==================== 聊天操作 ====================

/** 点击示例问题 */
function handleSampleQuestion(q: string) {
  inputText.value = q;
  handleSend();
}

/** 发送消息 */
async function handleSend() {
  const text = inputText.value.trim();
  if (!text || streaming.value) return;

  // 追加用户消息到本地
  messages.value.push({ role: "user", content: text });
  inputText.value = "";
  await scrollToBottom();

  // 创建 AI 占位消息
  const aiMsg: DisplayChatMessage = {
    role: "assistant",
    content: "",
    streaming: true
  };
  messages.value.push(aiMsg);
  await scrollToBottom();

  streaming.value = true;

  // 调用 SSE 流式聊天
  currentAbortController = chatApi.streamChat(
    text,
    activeConversationId.value,
    {
      onToken(token: string) {
        aiMsg.content += token;
        scrollToBottom();
      },
      onDone(fullContent: string) {
        aiMsg.streaming = false;
        streaming.value = false;
        // 解析 done 事件数据：{ conversationId, reply }
        try {
          const parsed: ChatDoneData = JSON.parse(fullContent);
          // 如果是新对话，设置 activeConversationId
          if (!activeConversationId.value) {
            activeConversationId.value = parsed.conversationId;
          }
          // 刷新对话列表以更新标题和时间
          loadConversations();
        } catch {
          // done 数据可能不是 JSON
        }
        scrollToBottom();
      },
      onError(message: string) {
        aiMsg.content += `\n[错误: ${message}]`;
        aiMsg.streaming = false;
        streaming.value = false;
      }
    }
  );
}

/** 停止生成 */
function handleStopStreaming() {
  if (currentAbortController) {
    currentAbortController.abort();
    currentAbortController = null;
  }
  // 给最后一条消息追加中断标记
  if (messages.value.length > 0) {
    const lastMsg = messages.value[messages.value.length - 1];
    if (lastMsg.role === "assistant" && lastMsg.streaming) {
      lastMsg.streaming = false;
      if (!lastMsg.content) {
        lastMsg.content = "[用户中断]";
      } else {
        lastMsg.content += "\n\n[用户中断]";
      }
    }
  }
  streaming.value = false;
}

// ==================== 工具方法 ====================

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick();
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight;
  }
}

/** 复制回复 */
function copyReply(content: string) {
  navigator.clipboard.writeText(content).then(() => {
    ElMessage.success("已复制到剪贴板");
  }).catch(() => {
    ElMessage.error("复制失败");
  });
}

/** 格式化时间 */
function formatTime(timeStr: string): string {
  if (!timeStr) return "";
  const date = new Date(timeStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  if (diff < 60_000) return "刚刚";
  if (diff < 3_600_000) return Math.floor(diff / 60_000) + " 分钟前";
  if (diff < 86_400_000) return Math.floor(diff / 3_600_000) + " 小时前";
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const mins = String(date.getMinutes()).padStart(2, "0");
  return `${month}-${day} ${hours}:${mins}`;
}

/** 格式化消息内容（简单换行处理） */
function formatContent(content: string): string {
  if (!content) return "";
  return content
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\n/g, "<br>");
}
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  height: calc(100vh - 120px);
  gap: 0;
  background: #f0f2f5;
  border-radius: 8px;
  overflow: hidden;
}

/* 左侧对话列表 */
.chat-sidebar {
  width: 280px;
  min-width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.new-chat-btn {
  margin: 12px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
}

.empty-conv {
  text-align: center;
  padding: 40px 16px;
  color: #909399;
}

.empty-conv .hint {
  font-size: 12px;
  margin-top: 4px;
  color: #c0c4cc;
}

.conv-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 6px;
  transition: background 0.2s;
  border: 1px solid transparent;
}

.conv-item:hover {
  background: #f5f7fa;
}

.conv-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.conv-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #e4e7ed;
}

/* 右侧聊天区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  min-width: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.chat-box {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

.empty-tip {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-tip p {
  margin-top: 16px;
  font-size: 16px;
}

.sample-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 20px;
}

.sample-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.sample-tag:hover {
  color: #409eff;
  border-color: #409eff;
}

/* 消息行 */
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.ai-row {
  flex-direction: row;
}

.user-row {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 75%;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.ai-row .message-bubble {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 4px;
}

.user-row .message-bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 4px;
}

.msg-actions {
  margin-top: 4px;
  padding-left: 4px;
}

/* 打字光标 */
.typing-cursor {
  display: inline-block;
  font-weight: bold;
  color: #409eff;
  animation: blink 1s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* 输入区 */
.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
}

.input-area :deep(.el-textarea__inner) {
  font-size: 14px;
}

.input-area .el-button {
  height: 40px;
}

/* 用户消息中的 markdown 文本颜色 */
.user-row .message-bubble {
  color: #fff;
}

/* 响应式：小屏幕隐藏侧边栏 */
@media (max-width: 768px) {
  .chat-sidebar {
    display: none;
  }
}
</style>
