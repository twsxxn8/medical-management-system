# 变更记录（Changelog）

本文档记录医疗管理系统的所有重要变更，遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范。

---

## [未发布] — 2026-07

### 新增 — feat/rate-limit（#3）

**分支：** `feat/rate-limit` → `develop`  
**提交：** `206f1f5`  
**日期：** 2026-07-25  
**类型：** Feature  
**影响范围：** 全局接口限流

#### 变更概述

将 RateLimitFilter 从「Redis INCR 计数器模式」升级为「Redis Lua 令牌桶算法」，支持突发流量、多维度限流和 AI 接口独立策略。

#### 变更文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `resources/lua/token_bucket.lua` | 新增 | Redis Lua 令牌桶原子脚本 |
| `service/RateLimitLuaService.java` | 新增 | StringRedisTemplate 执行 Lua，多维度配置 |
| `filter/RateLimitFilter.java` | 重写 | 从计数器升级为令牌桶，放行 auth 端点 |
| `config/SecurityConfig.java` | 改 | RateLimitFilter 注入 RateLimitLuaService |

#### 限流策略

| 场景 | capacity | rate/s |
|------|----------|--------|
| 普通接口（IP） | 20 | 10 |
| AI 接口（IP） | 5 | 2 |
| 普通接口（用户） | 30 | 15 |
| AI 接口（用户） | 8 | 3 |
| API Key | 50 | 20 |

#### 测试验证

- [x] 6 次并发 AI 请求 → 第 6 次被限流（429）
- [x] Redis 中令牌桶状态正确（tokens=0 after burst）
- [x] 登录接口放行不限流

---

### 新增 — feat/structured-output（#2）

**分支：** `feat/structured-output` → `develop`  
**提交：** `a65452c`  
**日期：** 2026-07-25  
**类型：** Feature  
**影响范围：** AI 问诊模块

#### 变更概述

使用 DeepSeek API 的 `response_format: json_object` 模式约束 LLM 输出，AI 回复直接返回结构化 JSON（疾病方向/检查项目/科室/紧急程度/注意事项），前端用 JSON 渲染卡片，删除正则解析逻辑。

#### 变更文件清单

| 文件 | 操作 | 行数 | 说明 |
|------|------|------|------|
| `backend/.../dto/DiagnosisResult.java` | 新增 | +119 | 结构化诊断结果 POJO（含 DiseaseItem 子类） |
| `backend/.../dto/AiDiagnosisRequest.java` | 新增 | +27 | 统一 AI 请求 DTO（替代 @RequestBody Map，解决 UTF-8 编码问题） |
| `backend/.../service/AiStructuredService.java` | 新增 | +243 | 结构化问诊服务（同步+流式） |
| `backend/.../controller/AiController.java` | 修改 | 重构 | 四种调用模式：文本/流式/结构化/结构化流式 |
| `backend/.../resources/application.yml` | 修改 | +5 | 添加 `server.servlet.encoding` 配置 |
| `frontend/src/api/ai.ts` | 修改 | +60 | 新增 `DiagnosisResult` 类型、`streamDiagnosisStructured()`、通用 `streamSse()` |
| `frontend/src/views/AiConsultView.vue` | 修改 | 重写 | 结构化卡片渲染（疾病概率标签/紧急程度颜色/淡入动画） |

#### API 变更

**新增端点：**

```
POST /api/ai/diagnosis/structured          # 同步结构化 → DiagnosisResult JSON
POST /api/ai/diagnosis/structured/stream   # SSE 流式结构化 → token + done(DiagnosisResult)
```

**`DiagnosisResult` 结构：**
```json
{
  "possible_diseases": [
    {"name": "紧张性头痛", "probability": "高", "description": "..."}
  ],
  "recommended_checks": ["血压测量", "血常规"],
  "recommended_department": "神经内科",
  "urgency": "尽快就诊",
  "precautions": ["保持规律作息"],
  "note": "补充说明（可选）"
}
```

#### 关键技术点

- **DeepSeek V4 兼容：** V4 不支持 `json_schema` strict 模式，改用 `json_object` + system prompt 内嵌字段定义
- **UTF-8 编码修复：** 新增 `server.servlet.encoding.force=true` + `AiDiagnosisRequest` DTO 替代 `@RequestBody Map`，解决中文请求体 400 错误
- **SSE + @Valid 冲突：** `@Valid` 在 SseEmitter 端点上与 Spring Security 产生 NPE，改用手动参数校验
- **历史对话 null 防护：** `buildRequestBody()` 中 `history` 参数添加 null guard
- **前端 JSON 渲染：** 删除 `parseSection()`/`parseList()` 正则解析，改用 `JSON.parse()` 直接结构化渲染
- **Markdown 代码块过滤：** 过滤 LLM 可能包裹的 ` ```json ` ` ``` ` 标记

#### 测试验证

- [x] 同步结构化：英文正常，中文正常（通过 `AiDiagnosisRequest` DTO 解决编码）
- [x] SSE 流式结构化：逐 token 推送 + done 事件返回完整 DiagnosisResult JSON
- [x] 历史对话 null：不带 history 字段不抛 NPE
- [x] 降级处理：LLM 返回非法 JSON 时前端退化为文本展示

---

### 新增 — feat/ai-streaming（#1）

**分支：** `feat/ai-streaming` → `develop`  
**提交：** `d2a54e3`（核心代码）、`8747add`（文档）、`<待提交>`（修复）  
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

- [x] 正常流式问诊：输入症状 → AI 逐字生成 → 完成后展示结构化卡片
- [x] 停止生成：流式过程中点击「停止」→ 中断请求 → 显示 `[用户中断]`
- [ ] 网络异常：断网或 API 超时 → 显示错误提示
- [x] 空输入：symptoms 为空 → 返回错误事件
- [x] 同步接口兼容：`POST /api/ai/diagnosis` 仍正常工作

#### Bug 修复

**BugFix — 前端结构化卡片解析失效（DeepSeek V4 输出格式变更）**

- **问题：** DeepSeek V4（`deepseek-v4-flash`）输出格式与 V3 不同，使用 `### 标题` 替代纯文本标题行，且标题措辞有变化（如"可能疾病方向"替代"可能的疾病方向"），导致前端 `parseSection()` / `parseList()` 正则匹配失败，所有结构化卡片显示"详见上方分析"
- **修复：**
  - 重写 `parseSection()` 和 `parseList()`：支持 `###` 过滤、多关键词备选（`|` 分隔）、遇到下一个标题自动停止
  - 模板中的 sectionName 参数改为多关键词格式（如 `"可能疾病方向|可能的疾病方向"`）
- **影响文件：** `frontend/src/views/AiConsultView.vue`

**BugFix — 移除 spring-boot-starter-webflux 依赖**

- **问题：** 引入 WebFlux 后与 Spring MVC Jackson 产生冲突，导致 `@RequestBody Map` 解析 UTF-8 字符失败（`Invalid UTF-8 middle byte 0xdb`），中文字符场景返回 400
- **修复：** 从 `pom.xml` 移除 `spring-boot-starter-webflux`；SSE 流式基于原生 `HttpURLConnection`，不依赖 WebFlux
- **影响文件：** `backend/pom.xml`、`backend/src/main/resources/application.yml`

**配置变更 — API 端点迁移**

- **变更：** AI 默认 API 从硅基流动（`api.siliconflow.cn`）切换至 DeepSeek 官方（`api.deepseek.com`），模型从 `deepseek-ai/DeepSeek-V3` 更新为 `deepseek-v4-flash`
- **原因：** 用户提供的硅基流动 API Key 验证失败（错误码 30014），DeepSeek 官方 API 验证通过
- **影响文件：** `backend/src/main/resources/application.yml`

---

## [v1.0] — 2025-08

### 初始版本

- Spring Boot 2.7 + Vue 3 医疗管理系统
- 10 个业务模块 CRUD
- Spring Security + JWT 认证授权
- Redis Token 黑名单
- 基础 AI 问诊（RestTemplate 同步调用）
- IP 计数器限流
