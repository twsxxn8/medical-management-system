# 智慧医疗管理系统

基于 **Spring Boot 3.2 + Vue 3** 的全栈医疗管理系统，集成 **DeepSeek 大模型 AI 智能问诊**，支持 SSE 流式逐字输出、多轮对话、语义缓存、熔断保护、多模型路由、Prometheus 可观测性。

---

> **当前分支：** `master`  
> **Spring Boot：** 3.2.7  **Java：** 17+  **Resilience4j：** 2.2.0  
> **最新更新：** 2026-07-27 — 9 个 Feature 全部完成

---

## 功能概览

- **预约挂号**：患者在线预约、医生排班管理、多状态流转（待确认/已确认/已完成/已取消）
- **医生管理**：科室分配、职称管理、出诊状态控制
- **药品管理**：药品信息维护、库存出入库记录、库存预警
- **电子病历**：病历创建与维护、诊断与处方管理
- **患者管理**：患者信息管理、就诊历史追溯
- **AI 智能问诊**：基于 DeepSeek 大模型的智能预问诊，支持 SSE 流式逐字输出（打字机效果）
- **AI 多轮聊天**：ChatGPT 风格对话界面，会话持久化，上下文记忆
- **AI 结构化输出**：`response_format: json_object` 约束 LLM 输出合法 JSON，前端卡片渲染
- **语义缓存**：Embedding 向量化 + 余弦相似度匹配，减少 LLM API 调用
- **令牌桶限流**：Redis Lua 原子脚本，AI 接口独立限流策略
- **熔断保护**：Resilience4j 熔断器保护所有 DeepSeek API 调用，自动降级
- **多模型路由**：支持配置多个 AI provider（DeepSeek / 硅基流动 / OpenAI），Priority 自动 failover
- **系统可观测性**：Actuator + Micrometer + Prometheus，复合健康检查，前端监控仪表盘
- **系统管理**：用户管理、RBAC 角色权限、操作日志

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.7 |
| 语言 | Java | 17+ |
| 数据库 | SQL Server | - |
| 缓存 | Redis（Lettuce 连接池） | - |
| ORM | Spring Data JPA（Hibernate 6） | - |
| 安全 | Spring Security 6 + JWT（jjwt 0.12.6） | - |
| AI 调用 | HttpURLConnection（流式）+ RestTemplate（同步） | - |
| 熔断器 | Resilience4j 2.2.0 | - |
| 可观测性 | Actuator + Micrometer + Prometheus | - |
| 前端框架 | Vue 3 + TypeScript | - |
| UI 框架 | Element Plus | - |
| 构建工具 | Maven / Vite | - |

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 16+
- SQL Server
- Redis

### 1. 克隆项目

```bash
git clone https://github.com/twsxxn8/medical-management-system.git
cd medical-management-system
```

### 2. 初始化数据库

在 SQL Server 中执行 `SQL_Server.sql` 创建数据库和表结构。

### 3. 配置环境变量

在启动前设置以下环境变量（或修改 `application.yml` 中的默认值）：

```bash
# 数据库配置
DB_USERNAME=sa
DB_PASSWORD=your_password

# JWT 密钥（生产环境请更换为随机长字符串）
JWT_SECRET=your-jwt-secret-key-at-least-32-chars

# AI 接口密钥（DeepSeek 官方 API）
AI_API_KEY=your_api_key_here

# 可选配置（已有默认值）
AI_API_URL=https://api.deepseek.com/v1/chat/completions
AI_MODEL=deepseek-v4-flash
UPLOAD_PATH=./uploads
```

### 4. 启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

后端默认运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，已配置 API 代理转发到后端。

## 项目结构

```
medical-management-system/
├── backend/                          # Spring Boot 3.2 后端
│   ├── src/main/java/com/example/backend/
│   │   ├── Entity/                   # JPA 实体类
│   │   ├── common/                   # 通用返回封装（Result）
│   │   ├── config/                   # 配置类
│   │   │   ├── AiProviderConfig.java     # 多 provider 配置 POJO
│   │   │   ├── MultiProviderConfig.java  # @ConfigurationProperties 绑定
│   │   │   ├── AiHealthIndicator.java    # 复合健康检查（DB + Redis）
│   │   │   ├── MetricsAspect.java        # AOP 请求指标记录
│   │   │   ├── SecurityConfig.java       # Spring Security 6.x Lambda DSL
│   │   │   ├── CorsConfig.java           # CORS 配置
│   │   │   └── RedisConfig.java          # Redis 序列化配置
│   │   ├── controller/               # RESTful 接口层
│   │   │   ├── AiController.java         # AI 问诊（4 种模式）
│   │   │   ├── ChatController.java       # AI 多轮聊天
│   │   │   ├── HealthController.java     # 健康检查 + 断路器状态
│   │   │   └── ...（10 个业务 Controller）
│   │   ├── dto/                      # 数据传输对象
│   │   ├── exception/                # 全局异常处理（含堆栈记录）
│   │   ├── filter/                   # 过滤器
│   │   │   ├── JwtAuthenticationFilter.java  # JWT 鉴权
│   │   │   └── RateLimitFilter.java          # IP 令牌桶限流
│   │   ├── repository/               # 数据访问层（Spring Data JPA）
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── AiService.java            # 同步 AI 问诊
│   │   │   ├── AiStreamService.java      # SSE 流式 AI 问诊
│   │   │   ├── AiStructuredService.java  # 结构化 AI 诊断
│   │   │   ├── ChatService.java          # 多轮对话服务
│   │   │   ├── SemanticCacheService.java # Embedding 语义缓存
│   │   │   ├── EmbeddingService.java     # Embedding API 调用
│   │   │   ├── AiCircuitBreakerService.java # 熔断器统一服务
│   │   │   ├── AiProviderRouter.java     # 多 provider 路由
│   │   │   ├── AiMetricsService.java     # AI 统计 + 断路器汇总
│   │   │   ├── RateLimitLuaService.java  # Lua 令牌桶限流
│   │   │   └── ...（8 个业务 Service）
│   │   ├── util/                     # 工具类（JWT）
│   │   └── BackendApplication.java   # 启动入口
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置
│   │   └── lua/                      # Redis Lua 脚本
│   │       ├── token_bucket.lua          # 令牌桶原子算法
│   │       └── semantic_cache_evict.lua  # LRU 淘汰
│   └── pom.xml
├── frontend/                         # Vue 3 + TypeScript 前端
│   ├── src/
│   │   ├── api/                      # Axios 接口封装
│   │   │   ├── ai.ts                     # AI 问诊 + 聊天 API
│   │   │   ├── monitor.ts                # 监控 API
│   │   │   └── ...（10 个业务 API）
│   │   ├── components/               # 公共组件
│   │   │   └── Layout.vue                # 侧边栏布局
│   │   ├── router/                   # 路由配置（含角色鉴权）
│   │   ├── utils/                    # 工具函数（Token 管理、请求拦截）
│   │   ├── views/                    # 页面视图
│   │   │   ├── AiConsultView.vue         # AI 问诊（结构化卡片）
│   │   │   ├── AiChatView.vue            # AI 聊天（ChatGPT 布局）
│   │   │   ├── MonitorView.vue           # 系统监控仪表盘
│   │   │   └── ...（10 个业务页面）
│   │   ├── App.vue                   # 根组件
│   │   └── main.ts                   # 入口文件
│   ├── vite.config.js                # Vite 配置（含 API 代理）
│   └── package.json
├── SQL_Server.sql                    # 数据库建表脚本（11 张表）
├── CHANGELOG.md                      # 完整变更记录
├── .gitignore
├── LICENSE
└── README.md
```

## API 接口

### 业务接口

| 模块 | 基础路径 | 说明 |
|------|----------|------|
| 认证 | `/api/auth` | 登录、登出 |
| 用户 | `/api/users` | 用户管理 |
| 科室 | `/api/departments` | 科室 CRUD |
| 医生 | `/api/doctors` | 医生管理 |
| 患者 | `/api/patients` | 患者管理 |
| 预约 | `/api/appointments` | 预约挂号 |
| 病历 | `/api/medical-records` | 电子病历 |
| 药品 | `/api/medicines` | 药品库存 |
| 统计 | `/api/statistics` | 数据统计 |

### AI 接口

| 端点 | 说明 |
|------|------|
| `POST /api/ai/diagnosis` | 同步文本诊断 |
| `POST /api/ai/diagnosis/stream` | SSE 流式文本诊断 |
| `POST /api/ai/diagnosis/structured` | 同步结构化诊断（支持语义缓存） |
| `POST /api/ai/diagnosis/structured/stream` | SSE 流式结构化诊断 |
| `POST /api/ai/chat` | 同步聊天 |
| `POST /api/ai/chat/stream` | SSE 流式聊天 |
| `GET /api/ai/conversations` | 对话列表 |
| `DELETE /api/ai/conversations/{id}` | 删除对话 |
| `GET /api/ai/conversations/{id}/messages` | 获取历史消息 |
| `GET /api/ai/stats` | AI 调用概览（需登录） |

### 监控接口

| 端点 | 说明 |
|------|------|
| `GET /api/health` | 增强业务健康检查 |
| `GET /api/health/circuit-breakers` | 断路器详情 JSON（需登录） |
| `GET /actuator/health` | 复合健康检查（DB + Redis + 磁盘） |
| `GET /actuator/metrics` | Micrometer 全量指标 |
| `GET /actuator/prometheus` | Prometheus 格式暴露 |
| `GET /actuator/circuitbreakers` | Resilience4j 断路器列表 |

### SSE 响应格式

所有流式端点采用统一的 SSE 事件格式：

```bash
# 示例：流式文本诊断
curl -X POST http://localhost:8080/api/ai/diagnosis/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"symptoms":"头痛发热38度，持续两天"}'

# 响应：SSE 事件流
event:token     data:头痛
event:token     data:可能
event:token     data:由
...
event:done      data:完整回复文本
event:error     data:错误信息

# 结构化流式额外事件
event:fromCache  data:true    # 语义缓存命中
```

## 分支策略

本项目采用 Git Flow 分支管理：

```
master                         # 生产分支
├── develop                    # 开发主线
│   ├── feat/ai-streaming      # SSE 流式响应 ✅
│   ├── feat/structured-output # 结构化输出 ✅
│   ├── feat/rate-limit        # 令牌桶限流 ✅
│   ├── feat/semantic-cache    # 语义缓存 ✅
│   ├── feat/ai-chat           # AI 对话管理 ✅
│   ├── feat/circuit-breaker   # 熔断与故障转移 ✅
│   ├── feat/multi-provider    # 多模型策略路由 ✅
│   ├── feat/observability     # 可观测性 ✅
│   └── feat/infra             # 基础设施升级 ✅
```

## 许可证

本项目基于 MIT 协议开源，详见 [LICENSE](./LICENSE) 文件。
