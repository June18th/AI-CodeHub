# Changelog

## 2026-05-31

### 动态 RBAC 权限系统
- 数据库驱动的权限模型：`permission` + `role_permission` 表存储角色-权限映射，不再硬编码
- `PermissionMapper` / `RolePermissionMapper` 实体化，`PermissionService` 负责缓存与刷新
- Redis 缓存权限（`rbac:perm:<role>`，24h TTL），多实例共享，读取零延迟
- 新增 `user:profile`、`conversation:access` 权限码，Seed 脚本自动初始化
- `UserDetailsServiceImpl` 实现 Spring Security `UserDetailsService`，加载 ROLE_ + 细粒度权限
- `UserPrincipal` 扩展 UserDetails，存储 DB 实时角色（非 JWT 签死值）
- `JwtAuthFilter` 接入 UserDetailsService，移除重复 PUBLIC_PATHS，异常日志化
- `@PreAuthorize("hasRole('dashboard:view')")` 方法级权限校验，替换类级注解
- `POST /api/v1/admin/permissions/reload` 运行时刷新权限缓存
- 移除死代码：`AuthInterceptor` / `@RequireRole`

### Agent ReAct 决策循环 + 预算控制
- `while(true)` 循环：每轮大模型根据完整对话历史（含工具执行结果）自主决定继续调工具还是输出答案
- `AgentBudget` 预算控制：最大 5 轮迭代 + 16000 token 上限，超限强制退出
- System Prompt 护栏：告知模型"不确定时直接问用户，不要猜测"，工具失败时分析原因不重复失败调用
- `FunctionCallHandler` 完整支持 system/user/assistant(tool_calls)/tool(tool_call_id) 多轮对话格式
- 工具执行异常不中断循环，异常信息作为 tool message 返回让模型重新思考
- 结构化 SSE 事件：`{"type":"tool_call","tool":"xxx","status":"executing"}` / `tool_result` / `budget_exceeded`
- 新增 3 个知识库 Agent Tool：
  - `summarize` — 接收文档内容生成结构化摘要（独立 LLM 调用）
  - `save_feedback` — 用户评价存入 Redis（rating 1-5 + 意见），90 天保留
  - `knowledge_stats` — 知识库统计（文档数/类型分布/最近上传）

### 对话历史 Redis 热缓存
- `MessageCacheService`：Redis List 存储最近 20 条消息（`msg_list:<conversationId>`，2h TTL）
- 写入路径：消息持久化 MySQL → 同步 `LPUSH` 到 Redis + `LTRIM` 保持滑动窗口
- 读取路径：Redis 热路径（命中率高）→ MySQL 兜底 → 自动 `backfill` 回填 Redis
- 访问自动续期 TTL，对话删除时同步清除 Redis key
- 修复 `ChatWebSocketHandler` 预存编译错误（`var` 参数类型 + 静态内部类引用实例字段）
- WebSocket 支持 JWT 认证（`ws://` 连接通过 `?token=` 查询参数传递，`JwtAuthFilter` 自动提取）
- WebSocket 消息兼容完整 Agent/Crew 模式（`chat` / `agent` / `crew` / `resume` 四种 type）

### 可观测性（Grafana + Prometheus + Loki）
- 移除自定义日志系统（LogController + LogViewer），改用标准可观测性栈
- 新增 Prometheus（actuator + micrometer）、Grafana（JVM Dashboard）、Loki + Promtail
- `docker-compose.yml` 新增 4 个服务: prometheus / grafana / loki / promtail

### 限流 + 熔断
- 新增 `RateLimitFilter`（Bucket4j 令牌桶），默认 60/20/120 rpm
- `AiApiClient` 内置熔断器：连续 5 次失败 → 30 秒熔断
- 添加 Resilience4j 依赖

### 日志监控页面
- `/admin/logs` 改为查询 Loki API，统计卡片 + 级别分布条形图
- 日志级别胶囊分段按钮（红/琥珀/蓝/灰实色高亮）
- 时间范围 15m/1h/2h/3h，5 秒自动刷新
- Nginx `/loki/` 代理 + 24h 折线趋势图

### SQL 注入 + 日志安全
- `DocumentChunkMapper` `${topK}` → `#{topK}` 参数化
- `StdOutImpl` → `Slf4jImpl`，生产不输出敏感 SQL

### 审计日志修复
- 游客模式聊天补充 `auditService.log()`
- MySQL 时区 UTC → `Asia/Shanghai`（`CURDATE()` 修复）
- 延迟负值：`System.currentTimeMillis()` → `System.nanoTime()`
- 前端 SSE 不带 `Authorization` header → `useChatStream` 添加 Bearer token
- `AuditService.log()` 自动从 userId 查 username

### 缓存一致性
- `RedisConfig` 新增 `RedisCacheManager` + `@EnableCaching`
- `ConversationService` / `ModelConfigController` 热点查询加 `@Cacheable`
- `CacheConsistencyService`：逐出失败 → 异步重试 5 次（指数退避）→ 毒化 key → 60 秒定时修复
- `saveMessage()` 同步驱逐 `conversations_user` 缓存 + 更新 `updated_at`

### ES 混合检索 + 批量处理
- `EmbeddingService.embedBatch()` — 一次 API 调用嵌入所有 chunks
- `VectorStoreService.bulkIndexChunks()` — ES `_bulk` 批量索引
- kNN → kNN+BM25 混合检索（RRF 融合）
- 移除 `DocumentProcessor`(Kafka)，改为同步批量处理

### 数据库索引
- 9 个复合索引：`conversation.slug` UNIQUE、`message(conversation_id,created_at)` 等

### Refresh Token + HTTPS
- Access Token 1h + Refresh Token 7d，`POST /api/v1/auth/refresh`
- BCrypt 哈希存储 `user.refresh_token`
- Nginx HTTPS（TLS 1.2/1.3 + HSTS），自签证书 → `config/certs/`
- `apiFetch` / `useChatStream` 自动续期

### Redis 滑动会话
- `SessionService`：登录创建 `session:{userId}`，每次请求续期 1h
- `POST /api/v1/auth/logout` 删除 session
- 聊天/Agent 请求通过 `Authorization` header 续 session

### 运营监控增强
- 24h 趋势图改为 SVG 面积折线图 + 数值标签
- 最近调用日志分页（10 条/页），`MyBatisPlusConfig` 分页插件
- Token 调试面板（类型、过期时间、去重头部仅末 20 位）
- Metric 卡片颜色改为完整 Tailwind 类名

### 时区 + 测试 + Web Search
- 全部 Docker 容器 `TZ: Asia/Shanghai`，MySQL `--default-time-zone='+08:00'`
- 27 个单元测试（JwtUtil / UserServiceImpl / ConversationService / AuditService / CacheConsistencyService）
- 持久化测试容器 `aicodehub-test`，全量 < 11 秒
- `WebSearchTool`（SerpAPI Google Search）

### 记忆系统（三层架构 + 双压缩策略 + 三维度检索）
- **三层架构**：短期记忆（token 预算）、长期记忆（ES 向量检索）、外部记忆（RAG 文档）
- **Map-Reduce 压缩**：旧消息分片→每片 AI 摘要→合并为精炼要点，写入 ES
- **ConversationHistory 压缩**：保留 system + 最近 3 轮 user（不切断 tool_call 对），中间压缩为摘要
- **Agent 主动存**：`save_memory` 工具，用户说"记住..."时 Agent 自动调用，标记 source="agent"
- **Agent 主动检索**：`search_memory` 工具，按语义查找已保存的记忆
- **三维度评分检索**：`MemoryEntry.combinedScore()` — keyword(0.4) + time(0.3) + source(0.3)
- 记忆注入 system prompt（不混入 user message），标注来源 `[主动记忆]` / `[历史摘要]`

### Multi-Agent（CrewAI 风格）
- 新增 `CrewOrchestrator`：架构师拆分→开发者并行→审查者汇总
- `AgentRole` + `AgentWorker` + `CrewController`：角色定义、单 Agent 执行
- WebSocket 集成 `handleCrew`，过滤空行和标号前缀
- 聊天界面 "🤖 多Agent" 开关，Agent 模式下可见

### SSE → WebSocket 全双工通信
- 新增 `spring-boot-starter-websocket`，`WebSocketConfig` + `WebSocketAuthInterceptor`
- `ChatWebSocketHandler` 统一处理 chat/agent/crew 三种模式
- JwtAuthFilter 同时支持 Header `Bearer` 和 Query `?token=` 两种传 token
- Nginx `/ws/` 代理（HTTP 3080 + HTTPS 443），`proxy_read_timeout 3600s`
- 前端 `useWebSocket`：指数退避重连（1s→30s）+ 30s 心跳 + 60s 超时检测
- 替换 `useChatStream`（SSE），打字机效果不变

### Agent Runtime（任务调度）
- 新增 `agent_task` 表 + `AgentTask` 实体 + `AgentTaskService` + `AgentTaskExecutor`
- `POST /api/v1/tasks` 提交后台任务，线程池异步执行，状态追踪 pending→running→done/failed
- 前端 `/copilot/tasks` 任务中心：提交表单 + 实时列表 + 结果查看
- Copilot 页面 Runtime 卡片上线可点击

### Token 分析
- `audit_log` 新增 `token_breakdown` JSON 字段，记录 user_prompt / system_prompt / memory_context / history / output
- `breakdownJson()` 在 chat/agent 模式下自动采集各阶段 token
- 前端 `/admin/tokens` Token 分析页：消耗分布条形图 + 分页明细表
- 管理后台导航新增 "Token 分析" 入口

### 压缩优化
- `MemoryService.buildContext()` 改为 token 预算触发压缩（历史对话超过预算 80% 自动压缩）
- 从消息条数阈值（20条）改为 token 占比阈值，更精准

### 图表优化
- 24h 趋势图：纵轴从 0 起始 + 6 等分标注 + 两小时区间聚合 + 数值标签移到时间轴下方
- Copilot 核心能力卡片全部上线可点击（RAG / Runtime / Workflow 可跳转）

### 文档
- `docs/*.png` 重命名为英文：`admin-panel.png` / `knowledge-base-design.png` / `copilot-workspace.png` / `chat.png`
- 管理后台支持亮色主题切换

### 文档 + 杂项
- 新增 `docs/PRODUCTION.md`（7 维度生产清单）
- 对话标题过滤 Markdown 符号（`##✅` → `今日收获`）
- Token 显示 `↑↓` → `入/出`，前端未登录自动弹框，旧格式 token 自动清除
- `.gitignore` 添加 `minio-data/`、`es-data/` 等数据卷
- 全部 Docker 容器统一 `TZ: Asia/Shanghai`

### 性能优化汇总
- HikariCP max 20 / min-idle 5、Tomcat max 200、Lettuce max-active 16
- JVM G1GC 256m-512m、Nginx IP 限流 + 连接数限制 + 安全头

## 2026-05-30

### 工作流编辑器
- React Flow (`@xyflow/react`) 可视化 DAG 画布，三栏布局
- `NodePanel` 左侧节点库：输入/输出/LLM/Agent/工具/条件分支，分三类可折叠
- `FlowCanvas` 自定义节点卡片 `WorkflowNodeCard`，含 handle、条件多分支输出
- Inspector 面板：按节点类型显示不同配置表单（LLM 模型选择+提示词模板+输入输出参数）
- 工作流 CRUD + SSE 流式运行 + 调试面板（独立输入+日志区）
- 保存时完整保留节点数据（inputParams/outputParams/model/configId/agentStrategy 等）
- WorkflowEngine 拓扑排序 + 并行层执行，输出节点支持引用/输入/透传三种模式

### LangGraph4j 集成
- pom.xml 添加 `org.bsc.langgraph4j:langgraph4j-core:1.6.5`
- WorkflowEngine 重构为分层并行执行架构

### Agent 工具调用
- `FunctionCallHandler` 处理 LLM ↔ 工具执行循环
- 内置工具：calculator / datetime / get_weather（高德 API）/ filesystem / rag_search
- 工具结果 `📋` 前缀显示 + 代码块包裹
- AgentController 支持 conversationId 持久化 + token 统计

### RAG 检索增强
- 千问 text-embedding-v4 2048 维向量化
- ES 8.13 dense_vector + kNN 余弦相似度检索
- Kafka 异步文档处理：文本 → 分块 → Embedding → ES 索引
- MinIO 大文件对象存储，文档上传支持拖拽 + 文本输入
- 文档预览模态框，分页展示内容

### 权限系统
- JWT + BCrypt 无状态鉴权，admin / beta / user / applicant 四角色
- 注册审核流程，运营监控大盘，audit_log 表

### 对话管理
- 侧边栏对话列表，支持搜索、重命名、删除
- 对话消息持久化到 MySQL，上下文窗口限制 20 条
- 双击标题编辑，SSE 流式打字机效果
- Markdown 渲染：标题/列表/表格/代码块/加粗/斜体
- Token 统计显示（输入/输出），持久化存储

### 多模型支持
- 策略模式 + 工厂模式，5 个模型策略：GPT / 智谱 / 千问 / DeepSeek / OpenAI 兼容
- `.env` 环境变量配置 API Key + Base URL + Model
- SSE 流式输出，nginx `proxy_buffering off` 保证实时性

### 模型配置管理
- `/model-config` 页面，CRUD + 设为默认
- `model_config` 表存储供应商/API地址/密钥/模型/温度/能力标签
- 工作流 LLM 节点可选择已存配置或手动输入

### UI/UX
- 深色/浅色主题切换，Tailwind `dark:` class 模式
- Sidebar 毛玻璃效果 + 靛蓝配色，登出按钮移至左下角
- 登录弹窗 + 注册申请
- 全屏按钮，实时时钟，消息时间戳
- 表格 Markdown 渲染，代码块样式
- 节点库边框分组，选中高亮

### 基础设施
- Docker Compose 7 服务：MySQL / Redis / ES / Kafka / MinIO / Backend / Frontend
- 命名卷持久化，端口统一管理
- `.env` / `.env.example` 分离，API Key 安全
- `docs/databases/init.sql` 12 张表完整建表语句
- `docs/nginx.conf` + `docs/ROADMAP.md`

### 项目结构
```
AI-CodeHub/
├── docker-compose.yml
├── .env / .env.example
├── README.md
├── docs/
│   ├── databases/init.sql
│   ├── nginx.conf
│   ├── ROADMAP.md
│   └── CHANGELOG.md
├── backend/ (Spring Boot 3 + Java 21 + MyBatis-Plus)
└── frontend/ (React 18 + TypeScript + Tailwind + Vite)
```
