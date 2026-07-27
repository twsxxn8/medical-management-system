# 变更记录（Changelog）

本文档记录医疗管理系统的所有重要变更，遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范。

---

## [未发布] — 2026-07

---

### 新增 — feat/observability（#8）

**分支：** `feat/observability` → `develop`
**提交：** `0cd2943`
**日期：** 2026-07-27
**类型：** Feature
**影响范围：** 系统可观测性

#### 变更概述

引入 Spring Boot Actuator + Micrometer Prometheus，新增复合健康检查、请求指标 AOP、断路器状态暴露、异常堆栈日志修复，前端新增系统监控仪表盘。

#### 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/pom.xml` | 修改 | 新增 `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| `backend/.../config/AiHealthIndicator.java` | 新增 | 复合健康检查（DB `SELECT 1` + Redis `PING`） |
| `backend/.../config/MetricsAspect.java` | 新增 | AOP 切面：记录每个 Controller 方法的调用次数、成功/失败数、耗时 |
| `backend/.../config/SecurityConfig.java` | 修改 | 放行 `/actuator/**` 和 `/api/health/**` |
| `backend/.../controller/HealthController.java` | 修改 | 注入 `HealthEndpoint` + `AiMetricsService`，返回真实健康状态 |
| `backend/.../service/AiMetricsService.java` | 新增 | 断路器状态汇总 + AI 调用概览 |
| `backend/.../exception/GlobalExceptionHandler.java` | 修改 | 三个 handler 全部添加 `log.warn/log.error`，兜底保留完整堆栈 |
| `backend/src/main/resources/application.yml` | 修改 | 新增 `management` 配置段暴露 Prometheus 端点 |
| `frontend/src/views/MonitorView.vue` | 新增 | 监控仪表盘：健康卡片 + 断路器表格 + Actuator 链接 |
| `frontend/src/api/monitor.ts` | 新增 | 监控 API 封装 |
| `frontend/src/router/index.ts` | 修改 | 新增 `/monitor` 路由 |
| `frontend/src/components/Layout.vue` | 修改 | 新增 "系统监控" 菜单项（仅 ADMIN） |

#### 新增/变更 API

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 复合健康检查（DB + Redis + 磁盘） |
| `GET /actuator/metrics` | Micrometer 全量指标列表 |
| `GET /actuator/prometheus` | Prometheus 格式指标暴露 |
| `GET /actuator/circuitbreakers` | Resilience4j 断路器列表 |
| `GET /api/health` | 增强业务健康检查（status 字段） |
| `GET /api/health/circuit-breakers` | 断路器详情 JSON（状态/失败率/成功/失败数） |
| `GET /api/ai/stats` | AI 调用概览（provider 列表 + 断路器状态） |

#### 关键技术点

- **复合健康检查：** `AiHealthIndicator` 实现 `HealthIndicator`，DB 执行 `SELECT 1`，Redis 执行 `PING`，任一失败即整体 `DOWN`
- **AOP 指标采集：** `MetricsAspect` 用 `@Around` 拦截所有 Controller 方法，Micrometer Counter 按 `uri/controller/method/outcome` 维度记录
- **断路器暴露：** 通过 `CircuitBreakerRegistry.getAllCircuitBreakers()` 遍历，输出状态/失败率/调用次数
- **异常日志修复：** `GlobalExceptionHandler` 三个 handler 各自记录 `log.warn`/`log.error`，兜底 handler 保留 `ex` 完整堆栈

#### 测试验证

- [x] `mvn compile` / `mvn clean package` 编译打包通过
- [x] `/actuator/health` 返回 `{"status":"UP","components":{"ai":{"database":"UP","redis":"UP"},...}}`
- [x] `/actuator/circuitbreakers` 返回 `["deepseek-chat","deepseek-chat-stream","deepseek-embedding"]`
- [x] `/actuator/prometheus` 返回 Prometheus 格式指标（含 `resilience4j_circuitbreaker_state`）
- [x] `/api/health/circuit-breakers` 返回 3 个 breaker 的完整状态 JSON
- [x] `/api/ai/stats` 返回 provider 列表 + 断路器概览
- [x] 前端监控仪表盘正常渲染

---

### 新增 — feat/multi-provider（#7）

**分支：** `feat/multi-provider` → `develop`
**提交：** `<待提交>`
**日期：** 2026-07-27
**类型：** Feature
**影响范围：** AI 调用路由

#### 变更概述

引入多 provider 路由机制，支持配置多个 AI 端点（DeepSeek / 硅基流动 / OpenAI 兼容）。通过 Priority 顺序降级策略，当高优先级 provider 断路器 OPEN 时自动 failover 到下一个可用 provider，提升 AI 服务可用性。Controller 层零改动，完全向后兼容。

#### 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/.../config/AiProviderConfig.java` | 新增 | 不可变 provider 配置快照 POJO |
| `backend/.../config/MultiProviderConfig.java` | 新增 | `@ConfigurationProperties` 多 provider 配置绑定，支持新旧两种配置格式 |
| `backend/.../service/AiProviderRouter.java` | 新增 | 统一调度入口：同步 intra-call failover、流式 pre-call 断路器检查、Embedding provider 选择 |
| `backend/.../service/AiCircuitBreakerService.java` | 修改 | 新增带 `breakerName` 参数的重载方法，不再持有硬编码 breaker 字段，改为从 Registry 动态获取 |
| `backend/.../service/AiService.java` | 修改 | 删除 `@Value` 字段，注入 `AiProviderRouter`，`callChatApi` 加 `AiProviderConfig` 参数 |
| `backend/.../service/AiStreamService.java` | 修改 | 同上，流式 provider 选择 + 结果记录通过 Router |
| `backend/.../service/AiStructuredService.java` | 修改 | 同上，同步 + 流式两条路径均通过 Router |
| `backend/.../service/ChatService.java` | 修改 | 同上，`streamChat` + `chatSync` 通过 Router |
| `backend/.../service/EmbeddingService.java` | 修改 | 删除 `@Value`，通过 `providerRouter.selectBestEmbeddingProvider()` 获取 provider |
| `backend/.../service/SemanticCacheService.java` | 修改 | Embedding 调用通过 `providerRouter.executeEmbedding()` |
| `backend/src/main/resources/application.yml` | 修改 | 新增 `app.ai.providers` 列表，保留旧版 flat 配置向后兼容 |

#### 配置变更

```yaml
app.ai:
  # 旧版 flat 配置保留（providers 为空时自动降级）
  providers:
    - name: deepseek
      priority: 1
      api-key: "${AI_API_KEY}"
      api-url: "https://api.deepseek.com/v1/chat/completions"
      model: "deepseek-v4-flash"
      embedding-url: "https://api.deepseek.com/v1/embeddings"
      embedding-model: "deepseek-chat"
    # - name: siliconflow     # 示例：第二个 provider
    #   priority: 2
    #   ...
```

#### 关键技术点

- **Priority 降级：** 数字越小越优先，同步调用 intra-call failover（失败自动换下一个），流式调用 pre-call 检查（断路器 OPEN 则跳过）
- **动态断路器：** breaker 命名规则 `{providerName}-{type}`，如 `deepseek-chat`、`siliconflow-chat-stream`
- **向后兼容：** 保留旧版 `@Value` 注入链路，`AiCircuitBreakerService` 无参方法委托到 `"deepseek-*"` breaker
- **Controller 层零改动：** 所有端点签名、返回类型完全不��

#### 测试验证

- [x] `mvn compile` / `mvn clean package` 编译通过
- [x] 真实 DeepSeek API 调用正常（"头痛发热38度" → 返回完整诊断建议）
- [x] 结构化问诊正常（返回 `DiagnosisResult` 完整字段）
- [x] 流式 SSE 端点 `event:error` / `event:done` 协议正常
- [x] 对话列表 / 登录 / 限流响应头 正常
- [x] 前端 Vite `localhost:5173` HTTP 200
- [x] 单 provider 配置下行为与改造前完全一致

---

### 新增 — feat/circuit-breaker（#6）

**分支：** `feat/circuit-breaker` → `develop`
**提交：** `87101bf` `477e3d9`
**日期：** 2026-07-27
**类型：** Feature
**影响范围：** AI 调用保护

#### 变更概述

引入 Resilience4j 熔断器保护所有 DeepSeek API 调用（同步/流式/Embedding），在 API 不可用时自动熔断快速失败并返回降级提示，避免请求堆积。修复 5 个代码缺陷。

#### 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/pom.xml` | 修改 | 新增 `resilience4j-spring-boot2:1.7.1` 依赖 |
| `backend/.../service/AiCircuitBreakerService.java` | 新增 | 3 个 CircuitBreaker 实例：deepseek-chat / deepseek-chat-stream / deepseek-embedding |
| `backend/.../service/AiService.java` | 修改 | 同步调用通过 `executeSyncCall()` 包装，熔断降级返回友好提示 |
| `backend/.../service/AiStreamService.java` | 修改 | 流式调用前置熔断检查 + 后置 `recordStreamResult/Exception` 反馈 |
| `backend/.../service/AiStructuredService.java` | 修改 | 同步/流式结构化调用均接入熔断 |
| `backend/.../service/ChatService.java` | 修改 | 同步/流式聊天接入熔断；`callSyncApi` 不再吞异常 |
| `backend/.../service/SemanticCacheService.java` | 修改 | Embedding 调用通过 `executeEmbedding()` 熔断包装 |
| `backend/.../service/EmbeddingService.java` | 无变更 | 预留 `EmbeddingException` 供熔断层使用 |
| `backend/src/main/resources/application.yml` | 修改 | 新增 `resilience4j.circuitbreaker` 配置段；修复 `app:` YAML 重复键 |

#### 配置变更

```yaml
resilience4j.circuitbreaker:
  configs:
    default:
      sliding-window-type: COUNT_BASED
      sliding-window-size: 10
      failure-rate-threshold: 50
      wait-duration-in-open-state: 30s
      permitted-number-of-calls-in-half-open-state: 2
    llm-stream:
      slow-call-duration-threshold: 60s
      slow-call-rate-threshold: 50
  instances:
    deepseek-chat:         # 同步调用
      base-config: default
    deepseek-chat-stream:  # 流式调用
      base-config: llm-stream
    deepseek-embedding:    # Embedding 调用
      base-config: default
      failure-rate-threshold: 30
```

#### 关键技术点

- **三种断路器分离：** 同步/流式/Embedding 各自独立统计，避免流式超时影响同步调用
- **流式异步反馈：** 流式调用无法用 `executeSupplier` 直接包装，采用 `tryAcquireStreamPermission`（前置检查）+ `recordStreamResult/Exception`（后置报告）组合
- **同步异常透传：** `ChatService.callSyncApi` 不再 catch 异常返回降级文本，改为抛出 `RuntimeException` 让断路器感知
- **Fail-soft 降级：** 所有同步调用返回预设中文友好提示，不影响用户体验

#### Bug 修复（#6.1）

- **流式断路器空壳：** `recordStreamResult`/`recordStreamException` 只打日志，改为调用 `CircuitBreaker.onSuccess`/`onError`
- **YAML 重复 `app:` 键：** `upload`/`cors` 错误嵌套在 `resilience4j` 下且有第二个 `app:` 键，合并后配置正常加载
- **PatientEntity 包名不一致：** `com.example.backend.entity` → `com.example.backend.Entity`
- **SemanticCacheService.store 缺熔断保护：** Embedding 调用改用 `circuitBreakerService.executeEmbedding()` 包装
- **.gitignore 补充：** 添加 `dump.rdb` 避免 Redis 快照文件被提交

#### 测试验证

- [x] 同步问诊 API Key 无效时返回熔断降级文本
- [x] 流式 SSE 接口 401 时正确推送 `event:error`
- [x] 结构化 fallback 字段完整（urgency/note/precautions）
- [x] 限流 `X-RateLimit-Remaining` 响应头正常
- [x] Maven 编译通过

---

### 新增 — feat/ai-chat（#5）

**分支：** `feat/ai-chat` → `develop`
**提交：** `215b984`
**日期：** 2026-07-26
**类型：** Feature
**影响范围：** AI 聊天模块

#### 变更概述

新增 AI 多轮聊天对话功能，支持 SSE 流式回复、对话会话持久化（DB）、ChatGPT 风格布局（对话列表侧边栏 + 聊天区域）。与现有 AI 问诊模块独立运行，共享 JWT 认证和限流保护。

#### 变更文件清单

| 文件 | 操作 | 行数 | 说明 |
|------|------|------|------|
| `backend/.../Entity/ChatConversationEntity.java` | 新增 | +76 | 会话表 JPA 实体 |
| `backend/.../Entity/ChatMessageEntity.java` | 新增 | +70 | 消息表 JPA 实体 |
| `backend/.../repository/ChatConversationRepository.java` | 新增 | +22 | 会话 Repository (按用户查询、归属验证删除) |
| `backend/.../repository/ChatMessageRepository.java` | 新增 | +21 | 消息 Repository (按时序查询、级联删除) |
| `backend/.../dto/ChatRequest.java` | 新增 | +31 | 聊天请求 DTO (conversationId nullable + @NotBlank message) |
| `backend/.../dto/ChatConversationResponse.java` | 新增 | +64 | 对话列表响应 DTO (含 lastMessage 摘要) |
| `backend/.../service/ChatService.java` | 新增 | +365 | 核心服务：SSE 流式聊天 + 同步后备 + 对话 CRUD |
| `backend/.../controller/ChatController.java` | 新增 | +106 | REST 控制器：/chat, /chat/stream, /conversations, ... |
| `frontend/src/views/AiChatView.vue` | 新增 | +390 | 聊天 UI：对话列表 + 聊天区 + 打字机效果 |
| `frontend/src/api/ai.ts` | 修改 | +165 | 追加 chatApi (5 个方法) + streamChatSse() |
| `frontend/src/router/index.ts` | 修改 | +3 | 追加 /ai-chat 路由 |
| `frontend/src/components/Layout.vue` | 修改 | +6 | 追加 "AI 智能聊天" 侧边菜单 |
| `SQL_Server.sql` | 修改 | +28 | 追加 chat_conversation + chat_message DDL |

#### DB 变更

**新增表：**

```sql
CREATE TABLE chat_conversation (
    id BIGINT PRIMARY KEY IDENTITY, user_id BIGINT NOT NULL,
    title NVARCHAR(200), create_time DATETIME2, update_time DATETIME2
);
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY IDENTITY, conversation_id BIGINT NOT NULL,
    role NVARCHAR(20), content NVARCHAR(MAX), create_time DATETIME2,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE
);
```

#### API 变更

**新增端点：**

```
POST   /api/ai/chat                       # 同步聊天 → {conversationId, reply}
POST   /api/ai/chat/stream                # SSE 流式聊天 → token/done/error
GET    /api/ai/conversations              # 对话列表 → ChatConversationResponse[]
DELETE /api/ai/conversations/{id}         # 删除对话
GET    /api/ai/conversations/{id}/messages # 获取历史消息
```

**SSE 事件格式（与诊断模块一致）：**
```
event:token     data:文字
event:done      data:{"conversationId":42,"reply":"完整回复..."}
event:error     data:错误信息
```

#### 关键技术点

- **Service 复用：** ChatService 完全复用 AiStreamService 的 HttpURLConnection + SSE chunk 解析模式，不引入 WebClient
- **上下文截断：** 最多保留最近 40 条历史消息传入 LLM，超长对话自动截断并标记
- **自动标题：** 新建对话时自动截取首条消息前 50 字符作为标题
- **数据隔离：** 所有 Repository 查询带 userId 条件，用户只能操作自己的对话
- **Fail-soft：** DB 写入失败不阻塞流式响应
- **限流与安全：** `/api/ai/**` 已继承 JWT 认证 + ip_ai 令牌桶 (capacity=5, rate=2/s)，无需额外配置

#### 测试验证

- [x] 多轮对话：连续 5 轮消息，AI 正确记住前文
- [x] SSE 流式：逐字推送打字机效果正常
- [x] 中断恢复：流式中点击"停止"正确中断并保存已生成内容
- [x] 对话管理：创建/切换/删除对话，刷新页面后消息持久化
- [x] 数据隔离：用户 A 无法访问用户 B 的对话
- [x] 现有功能不受影响：AiConsultView 诊断功能正常

---

### 新增 — feat/rate-limit（#3）

**分支：** `feat/rate-limit` → `develop`
**提交：** `206f1f5`
**日期：** 2026-07-25
**类型：** Feature
**影响范围：** 全局限流

#### 变更概述

将 IP 计数器限流升级为 Redis Lua 令牌桶算法，支持突发流量和 AI 接口独立限流策略。

#### 变更文件清单

| 文件 | 操作 | 行数 | 说明 |
|------|------|------|------|
| `backend/.../service/RateLimitLuaService.java` | 新增 | +96 | 多维令牌桶服务（ip/user/apikey × default/ai） |
| `backend/.../lua/token_bucket.lua` | 新增 | +50 | Redis Lua 令牌桶原子脚本 |
| `backend/.../filter/RateLimitFilter.java` | 修改 | 重写 | 从 INCR 计数器升级为令牌桶 |
| `backend/.../config/SecurityConfig.java` | 修改 | -19 | 简化限流相关安全配置 |

#### API 变更

**响应头（所有端点）：**
```
X-RateLimit-Remaining: 19
X-RateLimit-Retry-After: 0
```

**限流策略：**
- 默认：capacity=20, rate=10/s
- AI 接口：capacity=5, rate=2/s
- 登录注册（`/api/auth/**`）：不限流

#### 关键技术点

- **令牌桶算法：** Redis Lua 脚本保证原子性，支持突发流量（桶容量 > 填充速率）
- **多维度限流：** 支持按 IP、用户、API Key 维度独立配置
- **AI 接口识别：** Filter 自动识别 `/api/ai/**` 路径并应用严格策略
- **标准响应头：** `X-RateLimit-Remaining` / `X-RateLimit-Retry-After` 对齐 HTTP 429 规范

#### 测试验证

- [x] 6 次并发请求 AI 接口 → 第 6 次被限流 (429)，桶内 tokens=0
- [x] Redis 中令牌桶状态正常（`HGETALL token:bucket:ip:...`）

---

### 新增 — feat/semantic-cache（#4）

**分支：** `feat/semantic-cache` → `develop`
**提交：** `cc3fd90`
**日期：** 2026-07-25
**类型：** Feature
**影响范围：** AI 问诊缓存

#### 变更概述

在 LLM 调用前通过 Embedding 向量化用户症状，与 Redis 缓存做余弦相似度匹配。超过阈值直接返回缓存结果，减少 LLM API 调用和费用。

#### 变更文件清单

| 文件 | 操作 | 行数 | 说明 |
|------|------|------|------|
| `backend/.../service/SemanticCacheService.java` | 新增 | +532 | 语义缓存核心服务（check/store/stream） |
| `backend/.../service/EmbeddingService.java` | 新增 | +134 | DeepSeek Embedding API 调用 |
| `backend/.../dto/CachedDiagnosisResult.java` | 新增 | +80 | 缓存包装 DTO（含相似度/原始词） |
| `backend/.../lua/semantic_cache_evict.lua` | 新增 | +24 | Redis Lua 原子 LRU 淘汰脚本 |
| `backend/.../controller/AiController.java` | 修改 | +37 | /structured 端点集成缓存层（X-Cache 头） |
| `backend/.../filter/RateLimitFilter.java` | 修改 | +9 | Redis 不可用时 fail-open |
| `backend/.../filter/JwtAuthenticationFilter.java` | 修改 | +21 | Redis 不可用时 fail-open |
| `frontend/src/api/ai.ts` | 修改 | +4 | DisplayMessage.cached 字段 |
| `frontend/src/views/AiConsultView.vue` | 修改 | +18 | 💾 缓存命中标签 |

#### API 变更

**响应头：**
```
X-Cache: HIT / MISS
```

**缓存命中时额外 SSE 事件：**
```
event:fromCache    data:true
```

#### 关键技术点

- **两层匹配策略：** 优先 Embedding 余弦相似度（阈值 0.88），Embedding API 不可用时自动降级为字符 Jaccard 相似度（阈值 0.55）
- **LRU 淘汰：** Redis Lua 原子脚本淘汰最旧条目，maxEntries=1000
- **Fail-open 设计：** Redis 不可用时缓存静默跳过，不影响正常问诊流程
- **定期清理：** 每 50 次存储触发一次 stale 条目清理
- **前端缓存标识：** 消息增加 `cached` 字段，缓存命中时显示 "💾 缓存命中" 标签

#### 测试验证

- [x] Embedding API 正常调用，返回 1024 维 float 向量
- [x] 余弦相似度匹配正常（"头痛发热" vs "头疼发烧" → 高相似度命中）
- [x] Embedding API 不可用时自动降级为文本 Jaccard 匹配
- [x] Redis 不可用时 fail-open，问诊正常进行
- [x] LRU 淘汰：超过 maxEntries 时最旧条目被原子淘汰

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
