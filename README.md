# AI-CodeHub

全栈 AI 智能平台，支持多模型流式对话、Agent 工具调用、RAG 检索增强、可视化工作流编排。前端 React + TypeScript + Tailwind CSS，后端 Spring Boot 3 + MyBatis-Plus，Docker Compose 一键部署。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端框架 | React + TypeScript | 18 / 5.4 |
| 构建工具 | Vite | 5 |
| CSS 方案 | Tailwind CSS | 3.4 |
| 图编辑器 | @xyflow/react (React Flow) | 12 |
| 后端框架 | Spring Boot | 3.2 |
| ORM | MyBatis-Plus | 3.5 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7 |
| 搜索引擎 | Elasticsearch | 8.13 |
| 消息队列 | Kafka (KRaft) | 3.6 |
| 对象存储 | MinIO | latest |
| 向量模型 | 千问 text-embedding-v4 | 2048维 |
| 鉴权 | Spring Security RBAC + JWT + BCrypt | - |
| 容器化 | Docker + Docker Compose | - |

## 项目结构

```
AI-CodeHub/
├── docker-compose.yml              # 7 服务编排（MySQL/Redis/ES/Kafka/MinIO/Backend/Frontend）
├── .env                            # 环境变量
├── README.md
├── docs/                           # 文档
│   ├── databases/init.sql          # 完整建表语句
│   └── nginx.conf                  # Nginx 配置备份
├── db/
│   └── init.sql                    # 数据库初始化
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aicodehub/
│       │   ├── AiCodeHubApplication.java
│       │   ├── controller/         # Chat / Agent / Workflow / Admin / Documents / ModelConfig
│       │   ├── service/
│       │   │   ├── strategy/       # 多模型策略 (GPT/DeepSeek/千问/智谱/OpenAI)
│       │   │   ├── tool/           # Agent 工具 (天气/计算器/日期/文件系统/RAG检索)
│       │   │   ├── workflow/       # Workflow 引擎 (DAG调度+并行执行)
│       │   │   ├── rag/            # RAG 服务 (Embedding + ES向量检索)
│       │   │   └── factory/        # 模型工厂
│       │   ├── common/             # 统一响应、SSE、JWT、DTO
│       │   └── config/             # Security / Redis / Kafka / MyBatis-Plus
│       └── resources/
│           └── application.yml
└── frontend/
    ├── Dockerfile                  # 多阶段构建
    ├── nginx.conf                  # Nginx 配置
    ├── vite.config.ts
    ├── tailwind.config.js
    └── src/
        ├── App.tsx                 # 路由入口
        ├── pages/
        │   ├── ChatInterface.tsx   # 对话主界面
        │   ├── CopilotOverview.tsx # Copilot 工作台概览
        │   ├── CopilotWorkspace.tsx# 工作流编辑器
        │   ├── AdminPanel.tsx      # 运营管理
        │   ├── RagPanel.tsx        # 知识库管理
        │   └── ModelConfigPage.tsx # 模型配置管理
        ├── components/
        │   ├── Sidebar.tsx         # 侧边栏（对话管理+用户信息）
        │   ├── FlowCanvas.tsx      # React Flow 画布
        │   ├── NodePanel.tsx       # 节点库
        │   ├── ModelSelector.tsx   # 模型选择器
        │   ├── LoginModal.tsx      # 登录/注册弹窗
        │   ├── ProfileModal.tsx    # 用户资料编辑
        │   └── Icons.tsx           # SVG 图标组件
        ├── store/                  # Zustand 状态管理
        ├── hooks/                  # useChatStream 等
        └── utils/                  # 工具函数
```

## 本地开发

### 1. 环境要求

- Docker Desktop
- 复制 `.env.example` 为 `.env`，填写 API Key（DeepSeek / 千问必填）

### 2. 端口

| 端口 | 服务 |
|:--|:--|
| 3000 | Nginx 前端 |
| 8080 | Spring Boot 后端 |
| 3306 | MySQL |
| 6379 | Redis |
| 9200 | Elasticsearch |
| 9092 | Kafka |
| 9000 | MinIO API |
| 9001 | MinIO 控制台 |

端口冲突时修改 `.env` 中对应变量即可。

### 3. 启动

```bash
cd AI-CodeHub
cp .env.example .env   # 首次运行，填写 API Key
docker compose up -d --build
```

### 4. 访问

| 页面 | 地址 |
|------|------|
| 对话界面 | http://localhost:3000 |
| Copilot 概览 | http://localhost:3000/copilot |
| 工作流编辑器 | http://localhost:3000/copilot/workflow |
| 知识库管理 | http://localhost:3000/rag |
| 模型配置 | http://localhost:3000/model-config |
| 管理后台 | http://localhost:3000/admin |
| MinIO 控制台 | http://localhost:9001 |

### 4. 停止

```bash
docker compose down
```

## 核心功能

### 1. 多模型流式对话

- 5 个模型策略：GPT / 智谱 / 千问 / DeepSeek / OpenAI 兼容
- SSE 流式输出，Nginx `proxy_buffering off` 保证逐字显示
- Markdown 渲染（标题/列表/表格/代码块/加粗）
- Token 统计（输入/输出），持久化存储

### 2. Agent 工具调用

内置 6 个工具，Agent 模式下 AI 自主选择调用：

| 工具 | 功能 |
|------|------|
| `calculator` | 数学表达式计算 |
| `datetime` | 日期时间查询 |
| `get_weather` | 高德地图实时天气 |
| `filesystem` | 本地文件读写（挂载 /workspace） |
| `rag_search` | ES 向量语义检索 |
| `rag_search` | 知识库文档检索 |

### 3. RAG 检索增强

- 文档上传 → Kafka 异步处理 → 千问 text-embedding-v4 向量化 → ES dense_vector 索引
- kNN 余弦相似度检索，Agent 模式下自动调用
- MinIO 存储大文件，支持拖拽上传

### 4. 可视化工作流

- React Flow 画布，拖拽式 DAG 编排
- 节点类型：输入/输出/LLM/Agent/工具/条件分支
- 节点配置面板（输入输出参数、模型选择、提示词模板）
- Kepler 拓扑排序 + 并行层执行 + SSE 流式输出
- 调试面板：独立输入+日志区，与主输出分离

### 5. 权限系统（Spring Security RBAC）

| 角色 | 权限 |
|------|------|
| admin | 全部功能 + 用户审核 + 运营监控 |
| test | Agent + 知识库 + 工作流 + 模型配置 |
| user | 知识库 + 工作流 + 模型配置（使用自有 Key） |

注册后默认 user + pending 状态，管理员审核通过后激活。`@PreAuthorize` + JWT 无状态鉴权，权限种子启动时自动写入。

### 6. Agent 工具 + MCP 本地文件

Agent 模式下可调用 6 个工具：计算器 / 日期时间 / 天气查询 / **文件系统**（`list_dir`/`read_file`）/ ES 向量检索 / 知识库文档检索。文件系统工具挂载 `/workspace`（即项目根目录），可通过 `/minio/` 代理访问对象存储。

### 7. 运营监控

- 今日调用量 / 活跃用户 / Token 消耗 / 平均延迟 / 错误率
- 24h 调用趋势图 + 模型用量分布
- 最近调用日志表

## Nginx 配置

```nginx
# SPA fallback
location / { try_files $uri $uri/ /index.html; }

# API 代理（SSE 无缓冲）
location /api/ {
    proxy_pass http://backend:8080/api/;
    proxy_buffering off;
    proxy_read_timeout 180s;
}

# MinIO 对象存储代理
location ^~ /minio/ {
    proxy_pass http://minio:9000/;
}
```
