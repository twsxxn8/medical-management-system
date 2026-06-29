# 智慧医疗管理系统

基于 Spring Boot + Vue 3 的全栈医疗管理系统，提供预约挂号、医生管理、药品库存、电子病历等核心功能，并集成 AI 智能问诊助手。

## 功能概览

- **预约挂号**：患者在线预约、医生排班管理、多状态流转（待确认/已确认/已完成/已取消）
- **医生管理**：科室分配、职称管理、出诊状态控制
- **药品管理**：药品信息维护、库存出入库记录、库存预警
- **电子病历**：病历创建与维护、诊断与处方管理
- **患者管理**：患者信息管理、就诊历史追溯
- **AI 智能问诊**：基于 DeepSeek 大模型的智能预问诊
- **系统管理**：用户管理、角色权限、操作日志

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 |
| 语言 | Java | 11 |
| 数据库 | SQL Server | - |
| 缓存 | Redis | - |
| ORM | Spring Data JPA | - |
| 安全 | Spring Security + JWT | 0.11.5 |
| 前端框架 | Vue 3 | - |
| 构建工具 | Maven / Vite | - |

## 快速启动

### 环境要求

- JDK 11+
- Maven 3.6+
- Node.js 16+
- SQL Server（或更高版本）
- Redis

### 1. 初始化数据库

在 SQL Server 中执行 `SQL_Server.sql` 创建数据库和表结构。

### 2. 配置环境变量

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

### 3. 启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

后端默认运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，已配置 API 代理转发到后端。

## 项目结构

```
sprinboot-web/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/example/backend/
│   │   ├── Entity/                   # JPA 实体类
│   │   ├── common/                   # 通用返回封装
│   │   ├── config/                   # 配置类（CORS、Redis、Security）
│   │   ├── controller/               # RESTful 接口层
│   │   ├── dto/                      # 数据传输对象
│   │   ├── exception/                # 全局异常处理
│   │   ├── filter/                   # 过滤器（JWT 鉴权、接口限流）
│   │   ├── repository/               # 数据访问层
│   │   ├── service/                  # 业务逻辑层
│   │   └── BackendApplication.java   # 启动入口
│   └── pom.xml
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── api/                      # Axios 接口封装
│   │   ├── components/               # 公共组件
│   │   ├── router/                   # 路由配置
│   │   ├── utils/                    # 工具函数
│   │   ├── views/                    # 页面视图
│   │   ├── App.vue                   # 根组件
│   │   └── main.ts                   # 入口文件
│   ├── vite.config.js                # Vite 配置（含 API 代理）
│   └── package.json
├── SQL_Server.sql                    # 数据库建表脚本
├── .gitignore
└── README.md
```

## API 接口

项目提供 RESTful API，主要模块包括：

| 模块 | 基础路径 | 说明 |
|------|----------|------|
| 认证 | `/api/auth` | 登录、注册 |
| 用户 | `/api/users` | 用户管理 |
| 科室 | `/api/departments` | 科室 CRUD |
| 医生 | `/api/doctors` | 医生管理 |
| 患者 | `/api/patients` | 患者管理 |
| 预约 | `/api/appointments` | 预约挂号 |
| 病历 | `/api/medical-records` | 电子病历 |
| 药品 | `/api/medicines` | 药品库存 |
| AI | `/api/ai` | AI 智能问诊 |
| 统计 | `/api/statistics` | 数据统计 |

## 许可证

本项目基于 MIT 协议开源，详见 [LICENSE](./LICENSE) 文件。
