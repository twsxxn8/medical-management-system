# 智慧医疗管理系统

基于 Spring Boot + Vue 3 的全栈医疗管理系统，提供预约挂号、医生管理、药品库存、电子病历等核心功能，并集成 **AI 智能问诊助手（SSE 流式响应）**。

---

> **当前分支：** `feat/ai-streaming`  
> **最新更新：** 2026-07-25 — SSE 流式问诊

---

## 功能概览

- **预约挂号**：患者在线预约、医生排班管理、多状态流转（待确认/已确认/已完成/已取消）
- **医生管理**：科室分配、职称管理、出诊状态控制
- **药品管理**：药品信息维护、库存出入库记录、库存预警
- **电子病历**：病历创建与维护、诊断与处方管理
- **患者管理**：患者信息管理、就诊历史追溯
- **AI 智能问诊**：基于 DeepSeek 大模型的智能预问诊，**支持 SSE 流式逐字输出（打字机效果）**
- **系统管理**：用户管理、RBAC 角色权限、操作日志

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 |
| 语言 | Java | 11 |
| 数据库 | SQL Server | - |
| 缓存 | Redis（Lettuce 连接池） | - |
| ORM | Spring Data JPA | - |
| 安全 | Spring Security + JWT（jjwt 0.11.5） | - |
| AI 调用 | HttpURLConnection（流式）+ RestTemplate（同步） | - |
| 前端框架 | Vue 3 + TypeScript | - |
| UI 框架 | Element Plus | - |
| 构建工具 | Maven / Vite | - |

## 快速启动

### 环境要求

- JDK 11+
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

# AI 接口密钥（如需 AI 问诊功能）
AI_API_KEY=your_api_key_here

# 可选配置（已有默认值）
AI_API_URL=https://api.siliconflow.cn/v1/chat/completions
AI_MODEL=deepseek-ai/DeepSeek-V3
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
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/example/backend/
│   │   ├── Entity/                   # JPA 实体类
│   │   ├── common/                   # 通用返回封装（Result）
│   │   ├── config/                   # 配置类（CORS、Redis、Security）
│   │   ├── controller/               # RESTful 接口层（含 SSE 端点）
│   │   ├── dto/                      # 数据传输对象
│   │   ├── exception/                # 全局异常处理
│   │   ├── filter/                   # 过滤器（JWT 鉴权、IP 限流）
│   │   ├── repository/               # 数据访问层（Spring Data JPA）
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── AiService.java        #   同步 AI 问诊
│   │   │   └── AiStreamService.java  #   SSE 流式 AI 问诊
│   │   ├── util/                     # 工具类（JWT）
│   │   └── BackendApplication.java   # 启动入口
│   └── pom.xml
├── frontend/                         # Vue 3 + TypeScript 前端
│   ├── src/
│   │   ├── api/                      # Axios 接口封装（含 SSE fetch）
│   │   ├── components/               # 公共组件
│   │   ├── router/                   # 路由配置（含角色鉴权）
│   │   ├── utils/                    # 工具函数（Token 管理、请求拦截）
│   │   ├── views/                    # 页面视图
│   │   │   └── AiConsultView.vue     #   AI 问诊（SSE 打字机效果）
│   │   ├── App.vue                   # 根组件
│   │   └── main.ts                   # 入口文件
│   ├── vite.config.js                # Vite 配置（含 API 代理）
│   └── package.json
├── SQL_Server.sql                    # 数据库建表脚本（9 张表）
├── CHANGELOG.md                      # 变更记录
├── .gitignore
├── LICENSE
└── README.md
```

## API 接口

| 模块 | 基础路径 | 说明 |
|------|----------|------|
| 认证 | `/api/auth` | 登录、注册、登出 |
| 用户 | `/api/users` | 用户管理 |
| 科室 | `/api/departments` | 科室 CRUD |
| 医生 | `/api/doctors` | 医生管理 |
| 患者 | `/api/patients` | 患者管理 |
| 预约 | `/api/appointments` | 预约挂号 |
| 病历 | `/api/medical-records` | 电子病历 |
| 药品 | `/api/medicines` | 药品库存 |
| AI 同步 | `POST /api/ai/diagnosis` | AI 智能问诊（等待完整结果） |
| AI 流式 | `POST /api/ai/diagnosis/stream` | AI 智能问诊（SSE 流式，逐字输出） |
| 统计 | `/api/statistics` | 数据统计 |

### AI 流式端点示例

```bash
curl -X POST http://localhost:8080/api/ai/diagnosis/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"symptoms":"头痛发热38度，持续两天"}'
  # 响应：SSE 事件流（event:token / event:done / event:error）
```

## 分支策略

本项目采用 Git Flow 分支管理：

```
master                         # 生产分支
├── develop                    # 开发主线
│   ├── feat/ai-streaming      # SSE 流式响应 ✅
│   ├── feat/semantic-cache    # 语义缓存 ✅
│   ├── feat/rate-limit        # 令牌桶限流 ✅
│   ├── feat/circuit-breaker   # 熔断与故障转移 ✅
│   ├── feat/multi-provider    # 多模型策略路由 ✅
│   ├── feat/structured-output # 结构化输出 ✅
│   ├── feat/ai-chat           # AI 对话管理 ✅
│   ├── feat/observability     # 可观测性 ✅
│   └── feat/infra             # 基础设施升级 ✅
```

## 许可证

本项目基于 MIT 协议开源，详见 [LICENSE](./LICENSE) 文件。
