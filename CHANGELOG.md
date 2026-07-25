# 变更记录（Changelog）

本文档记录医疗管理系统的所有重要变更，遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范。

---

## [未发布] — 2026-07

### 新增 — feat/ai-streaming（#1）

**分支：** `feat/ai-streaming` → `develop`  
**提交：** `d2a54e3`  
**日期：** 2026-07-25  
**类型：** Feature  
**影响范围：** AI 问诊模块

#### 变更概述

将 AI 智能问诊从「同步等待完整结果」升级为「SSE 流式逐 token 推送」，用户可实时看到 AI 回复生成过程（打字机效果），体验对齐 ChatGPT。

#### 技术方案

```
改造前：RestTemplate.postForObject()
         → 阻塞等待完整响应（5-15s）
         → 前端一次性渲染

改造后：HttpURLConnection + stream:true
         → 逐行解析 SSE data: {...} chunk
         → SseEmitter.send(token) 实时推送
         → 前端 fetch + ReadableStream 逐字追加 DOM
```

#### 变更文件清单

| 文件 | 操作 | 行数 | 说明 |
|------|------|------|------|
| `backend/pom.xml` | 修改 | +5 | 新增 `spring-boot-starter-webflux` 依赖（后续 WebClient 使用） |
| `backend/.../service/AiStreamService.java` | 新增 | +180 | 核心流式服务：HttpURLConnection 直连 LLM API，parse SSE chunk，SseEmitter 推送 |
| `backend/.../controller/AiController.java` | 修改 | +48 | 新增 `POST /api/ai/diagnosis/stream` 端点，`produces = text/event-stream` |
| `frontend/src/api/ai.ts` | 修改 | +70 | 新增 `streamDiagnosis()` 方法，fetch + ReadableStream 消费 SSE |
| `frontend/src/views/AiConsultView.vue` | 修改 | +80 | 打字光标动画、逐字渲染、停止生成按钮 |

#### API 变更

**新增端点：**

```
POST /api/ai/diagnosis/stream
Content-Type: application/json
Accept: text/event-stream

Request Body:
{
  "symptoms": "头痛、发热38度",
  "history": [
    {"role": "user", "content": "..."},
    {"role": "assistant", "content": "..."}
  ]
}

Response (SSE):
  event:token     data:头
  event:token     data:痛
  event:token     data:可能
  ...
  event:done      data:完整回复文本
  event:error     data:错误信息
```

**保留兼容：** `POST /api/ai/diagnosis` 同步端点不变。

#### 关键技术点

- **后端流式：** 使用原生 `HttpURLConnection`（非 WebClient）避免与现有 Spring MVC 冲突，设置 `stream: true` 请求参数
- **SSE 协议：** 每收到一个 delta token，通过 `SseEmitter.send(event("token").data(content))` 推送；流结束发送 `event("done")`
- **连接管理：** `SseEmitter(60_000L)` 与 LLM API `readTimeout=60s` 对齐；前端 `AbortController` 中断时自动关闭上游连接
- **前端消费：** `fetch + ReadableStream + TextDecoder` 解析 SSE 事件流，避免 EventSource 仅支持 GET 的限制
- **打字光标：** CSS `@keyframes blink` 实现闪烁 `|` 效果，`streaming: false` 后移除

#### 测试验证

- [ ] 正常流式问诊：输入症状 → AI 逐字生成 → 完成后展示结构化卡片
- [ ] 停止生成：流式过程中点击「停止」→ 中断请求 → 显示 `[用户中断]`
- [ ] 网络异常：断网或 API 超时 → 显示错误提示
- [ ] 空输入：symptoms 为空 → 返回错误事件
- [ ] 同步接口兼容：`POST /api/ai/diagnosis` 仍正常工作

---

## [v1.0] — 2025-08

### 初始版本

- Spring Boot 2.7 + Vue 3 医疗管理系统
- 10 个业务模块 CRUD
- Spring Security + JWT 认证授权
- Redis Token 黑名单
- 基础 AI 问诊（RestTemplate 同步调用）
- IP 计数器限流
