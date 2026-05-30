# AI-CodeHub

全栈 AI 对话平台，支持多模型切换与流式输出。前端 React + TypeScript + Tailwind CSS，后端 Spring Boot 3 策略模式对接多厂商大模型，Docker Compose 一键部署。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端框架 | React + TypeScript | 18 / 5.4 |
| 构建工具 | Vite | 5 |
| CSS 方案 | Tailwind CSS | 3.4 |
| 后端框架 | Spring Boot | 3.2 |
| ORM | MyBatis-Plus | 3.5 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7 |
| 容器化 | Docker + Docker Compose | 3.9 |

## 项目结构

```
AI-CodeHub/
├── docker-compose.yml              # 四服务编排
├── .env                            # 环境变量
├── README.md
├── db/
│   └── init.sql                    # 初始化 SQL
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aicodehub/
│       │   ├── AiCodeHubApplication.java
│       │   ├── controller/         # REST 接口
│       │   ├── service/
│       │   │   ├── factory/        # 模型工厂（自动注册）
│       │   │   └── strategy/       # 模型策略接口 + 实现
│       │   ├── common/             # 统一响应、SSE 工具
│       │   ├── config/             # Redis / MyBatis-Plus 配置
│       │   ├── mapper/             # MyBatis 映射
│       │   └── entity/             # 实体类
│       └── resources/
│           └── application.yml
└── frontend/
    ├── Dockerfile                  # 多阶段构建
    ├── nginx/nginx.conf            # Nginx 配置
    ├── vite.config.ts
    ├── tailwind.config.js
    └── src/
        ├── main.tsx
        ├── App.tsx                 # 路由入口
        ├── pages/
        │   └── ChatInterface.tsx   # 对话主界面
        ├── hooks/
        │   └── useChatStream.ts    # SSE 流式读取 Hook
        ├── types/                  # TypeScript 类型
        └── services/               # API 封装（预留）
```

## 本地开发

### 1. 环境要求

- Docker Desktop 或 Docker Engine
- 确保以下端口未被占用：`3308` / `6388` / `8081` / `3001`

### 2. 启动

```bash
# 克隆项目
cd AI-CodeHub

# 构建并启动全部服务
docker compose up -d --build
```

### 3. 验证

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:3001 |
| 后端 API | http://localhost:8081/api/v1/chat/stream?prompt=你好&modelType=gpt |
| MySQL | localhost:3308 |
| Redis | localhost:6388 |

### 4. 停止

```bash
docker compose down
```

## 核心功能

### 多模型切换

采用策略模式 + 工厂模式，遵循开闭原则：

```
┌──────────────────┐
│  ChatController  │  GET /api/v1/chat/stream?prompt=...&modelType=xxx
└────────┬─────────┘
         ▼
┌──────────────────┐
│  AiModelFactory  │  构造函数注入 List<AiModelStrategy>
│  Map<type, impl> │  Spring 自动发现所有 @Component 策略
└────────┬─────────┘
         ▼
┌─────────────────────────────────────────────┐
│  <<interface>> AiModelStrategy              │
│  + getModelType(): String                   │
│  + chatStream(prompt, emitter): void        │
└───┬─────────┬──────────┬──────────┬─────────┘
    ▼         ▼          ▼          ▼
  [GPT]    [智谱]     [千问]    [DeepSeek]  ...
```

**扩展新模型只需两步**，无需修改已有代码：
1. 创建类实现 `AiModelStrategy`，添加 `@Component`
2. Spring 自动注入到工厂的 `Map` 中完成注册

当前已接入模型：

| modelType | 策略类 | 厂商 |
|-----------|--------|------|
| `gpt` | GptModelStrategy | OpenAI GPT-4 |
| `zhipu` | ZhipuModelStrategy | 智谱 GLM |
| `qwen` | QwenModelStrategy | 阿里通义千问 |
| `deepseek` | DeepseekModelStrategy | DeepSeek |
| `openai` | OpenAiModelStrategy | OpenAI 兼容 |

### 流式输出（SSE）

```
浏览器 (fetch + ReadableStream)
   │
   │  GET /api/v1/chat/stream?prompt=...&modelType=deepseek
   ▼
后端 (SseEmitter)
   │
   │  emitter.send("第一句话")
   │  Thread.sleep(400ms)
   │  emitter.send("第二句话")
   │  Thread.sleep(400ms)
   │  emitter.send("第三句话")
   │  emitter.complete() → 发 [DONE]
   ▼
前端 useChatStream Hook
   │
   │  TextDecoder 逐块解码 response.body
   │  解析 SSE data: 行
   │  onChunk → useState 追加 → 打字机效果
   ▼
React 组件实时渲染
```

**前端流式读取核心流程**（`useChatStream.ts`）：

1. `fetch(url)` 获取 `response.body`
2. `getReader()` 拿出 `ReadableStreamDefaultReader`
3. `TextDecoder` 逐 chunk 解码，按 `\n` 拆行
4. 匹配 `data:` 前缀行，提取文本回调 `onChunk()`
5. 收到 `[DONE]` 触发 `onDone()` 结束

**打字机效果**：`onChunk` 回调中直接拼接到 `messages` 最后一条 assistant 消息的 `content` 尾部，React 自动 diff 渲染，实现逐句出现的效果。末位闪烁光标由 Tailwind `animate-pulse` 实现。

## Nginx 配置说明

前端容器使用 Nginx 托管静态资源，关键配置：

```nginx
location / {
    try_files $uri $uri/ /index.html;   # SPA fallback，解决刷新 404
}

location /api/ {
    proxy_pass http://backend:8080/api/; # 反向代理到后端
}
```

Docker 网络内各服务通过容器名（`backend`、`mysql`、`redis`）互访，无需硬编码 IP。
