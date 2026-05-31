# Changelog

## 2026-05-31

### 生产级可观测性 (Grafana + Prometheus + Loki)
- 移除自定义日志系统（LogController + LogViewer），改用标准可观测性栈
- 新增 Prometheus 指标采集（Spring Boot Actuator + Micrometer）
- 新增 Grafana 可视化面板，预置 JVM 监控 Dashboard
- 新增 Loki + Promtail 日志聚合，替代文件日志查看
- `docker-compose.yml` 新增 4 个服务: prometheus / grafana / loki / promtail

### 限流（Bucket4j 令牌桶）
- 新增 `RateLimitFilter`，基于 IP + Token 双重限流
- 默认 60 req/min，Chat/Agent 20 req/min，Admin 120 req/min
- 超限返回 HTTP 429 + JSON 响应

### 熔断（AI 模型调用保护）
- `AiApiClient` 内置熔断器：连续 5 次失败自动熔断 30 秒
- 添加 Resilience4j 依赖，为后续服务熔断预留

### 日志监控页面（Loki 数据源）
- `/admin/logs` 日志监控页改为查询 Loki API，替代旧的文件日志方式
- 统计卡片：总日志数 / ERROR / WARN / INFO / DEBUG 计数 + 错误率
- 日志级别分布条形图，实时可视化
- 日志级别筛选改为胶囊分段按钮，选中态实色高亮（红/琥珀/蓝/灰）
- 时间范围切换 5m / 15m / 1h，5 秒自动刷新
- 右上角 "Grafana →" 快捷跳转
- Nginx 新增 `/loki/` 代理路径

### 修复
- ES / Kafka 容器因 hostname 无法解析启动失败 → 添加固定 `hostname`
- Kafka `CONTROLLER_QUORUM_VOTERS` 从 `localhost` 改为容器名 `kafka`
- 消除 Spring Security `UserDetailsServiceAutoConfiguration` 启动 WARN
- SQL 注入防护：`DocumentChunkMapper` `${topK}` 改为 `#{topK}` 参数化
- SQL 日志：`StdOutImpl` 改为 `Slf4jImpl`，生产环境不输出敏感数据
- 游客模式聊天不写审计日志 → 补充 `auditService.log()`，游客调用也记录
- 运营监控"今日调用/Token消耗"始终为 0 → MySQL 时区 UTC → `Asia/Shanghai`
- 延迟出现负值 → `System.currentTimeMillis()` 换 `System.nanoTime()`
- 审计日志全显示"游客" → 前端 SSE 请求不带 `Authorization` header，后端从 JWT 提取 userId 并自动查 username
- 对话列表排序错误 → `saveMessage()` 时更新 `conversation.updated_at` + 同步逐出 Redis 缓存
- 日志监控统计数据为 0 → Loki `count_over_time` 窗口太窄 + 默认时间范围扩到 2h
- 日志级别筛选按钮太丑 → 改实色胶囊按钮（红/琥珀/蓝/灰）

### 测试体系
- 新增 5 个单元测试文件，27 个用例覆盖：JwtUtil / UserServiceImpl / ConversationService / AuditService / CacheConsistencyService
- 持久化测试容器 `aicodehub-test`，Maven 依赖缓存在 `maven-cache` 卷，全量测试 < 11 秒
- logback-test.xml + surefire JVM 参数消除测试噪音

### Web Search 工具
- 新增 `WebSearchTool`：基于 SerpAPI Google Search，Agent 可联网搜索实时信息
- 返回结构化结果（标题 + 摘要 + 链接），支持自定义数量（默认5，最大10）
- 配置 `SERPAPI_API_KEY` 环境变量启用

### 缓存一致性（企业级）
- 新增 `CacheConsistencyService`：DB 写入后同步逐出 Redis，失败则异步重试 5 次（指数退避 200ms→3.2s）
- 全部重试失败后记录毒化 key，`@Scheduled` 每 60 秒扫描恢复
- `ConversationService.saveMessage()` 同步驱逐 `conversations_user` 缓存

### 前端体验
- Token 显示 `↑123 ↓456` 改为 `入 123　出 456`
- 24h 调用趋势图新增小时标签（0h 3h 6h 9h 12h 15h 18h 21h）
- 日志监控新增 2h/3h 时间范围，默认 2 小时
- SSE 请求新增 `apiFetch` 自动续期，Access Token 过期自动用 RefreshToken 换新

### 会话管理（Redis 滑动会话）
- 新增 `SessionService`：登录创建 Redis `session:{userId}`，每次请求滑动续期 1h
- 退出登录调用 `/api/v1/auth/logout` 删除 session
- 聊天 / Agent 请求通过 JWT `Authorization` header 续 session
- Access Token 1h + Refresh Token 7d，`apiFetch` 和 `useChatStream` 自动续期

### 运营监控增强
- 24h 调用趋势图改为 SVG 面积折线图 + 数值标签
- 最近调用日志改为 MyBatis-Plus 分页（10 条/页）
- Token 调试面板：显示 Access/Refresh 类型、过期时间（去重头部，仅末 20 位）
- `MyBatisPlusConfig` 新增分页插件（`PaginationInnerInterceptor`）

### 时区统一
- 全部 Docker 容器添加 `TZ: Asia/Shanghai`
- MySQL `--default-time-zone='+08:00'`，`CURDATE()` 正确返回北京时间

### 文档
- 新增 `docs/PRODUCTION.md` — 开发 vs 生产差异清单（7 个维度，含快速 Checklist）
- ROADMAP 同步更新

### 安全：Refresh Token 双令牌机制
- Access Token 15分钟 + Refresh Token 7天
- 登录返回 `token` + `refreshToken`，前端使用 refreshToken 换取新 accessToken
- `POST /api/v1/auth/refresh` 新增刷新端点
- Refresh Token BCrypt 哈希后存储到 `user.refresh_token` 字段
- 使用 JWT `type` claim 区分 access/refresh，防止混用

### 性能优化：Redis 缓存
- `RedisConfig` 新增 `RedisCacheManager`，`@EnableCaching` 启用 Spring Cache
- `ConversationService.getById()` / `getContext()` / `listByUser()` 添加 `@Cacheable`
- `ModelConfigController` list/create/update/delete 添加缓存注解
- 缓存 TTL: conversation 30min / messages 5min / model_configs 30min
- 新消息插入时自动 `@CacheEvict` 驱逐对应缓存

### 性能优化：ES 混合检索 + 批量处理
- `EmbeddingService` 新增 `embedBatch()` — 一次 API 调用嵌入所有 chunks
- `VectorStoreService` 新增 `bulkIndexChunks()` — ES `_bulk` API 批量索引
- 搜索从纯 kNN 升级为 kNN+BM25 混合检索（RRF 融合，`rank_constant=60`）
- 修复双重索引 bug：`DocumentService.upload()` 移除重复的逐 chunk 索引
- 移除 `DocumentProcessor`(Kafka 消费者)，文档处理改为同步批量
- 上传文档：分块 → 批量 Embedding(1次API) → 批量 ES(1次bulk)

### 性能优化：数据库索引
- `conversation` 新增 `uk_slug` UNIQUE + `idx_user_updated(user_id, updated_at)`
- `message` 新增 `idx_conv_created(conversation_id, created_at)`
- `user` 新增 `idx_user_role_created(role, created_at)`
- `document` 新增 `idx_doc_user_created(user_id, created_at)`
- `audit_log` 新增 `idx_status_created(status, created_at)`
- `model_config` 新增 `idx_mc_user_updated` + `idx_mc_user_provider`
- `workflow` 新增 `idx_wf_user_updated(user_id, updated_at)`
- 共 9 个索引，覆盖所有高频 WHERE + ORDER BY 查询

### 高并发 & 性能优化
- HikariCP 连接池调优：max 20 / min-idle 5 / 泄漏检测 60s
- Tomcat 线程池：max 200 / min-spare 10 / accept 100
- Lettuce Redis 连接池增强：max-active 16 / min-idle 4 / max-wait 3s
- JVM 调优：G1GC / 256m-512m / MaxGCPauseMillis=200ms
- Nginx 增强：IP 限流 10r/s burst 20、连接数限制 20、安全响应头、body size 50m

### 对话 URL 优化
- 对话 URL 从 `/chat/1` 改为 `/chat/a3f2c8e1`（8 位 UUID 随机短码）
- `conversation` 表新增 `slug` 字段，创建时自动生成
- 侧边栏点击、新建对话、URL 分享均使用 slug
- 顶部 `AI-CodeHub` Logo 点击回到 `/chat` 新建页

### 表格渲染优化
- 圆角容器 + 隔行斑马纹 + 大写表头 + 柔和分隔线
- 反引号全部移除，纯文本显示

### 用户头像
- 侧边栏底部显示用户头像+用户名，点击弹出编辑弹窗
- 支持 20 个 emoji 头像选择 + 本地上传图片
- 上传图片存入 MinIO `images` 公开桶，nginx 代理 `/minio/` 路径
- 登录接口返回头像字段，authStore 持久化到 localStorage

### Spring Security RBAC
- `JwtAuthFilter` 替代 `AuthInterceptor`，`OncePerRequestFilter` + `SecurityContextHolder`
- `@EnableMethodSecurity` + `@PreAuthorize` 替代 `@RequireRole`
- 三角色：admin / test / user（注册默认 user，审核可升级 test）
- 6 个权限点 + role_permission 映射，DataInitializer 启动自动写入
- 权限矩阵：admin 全权限 / test 含工作流+模型配置 / user 含知识库+文档预览

### MCP STDIO 客户端
- `McpClient` 实现 JSON-RPC 2.0 over STDIO 协议通信
- 后端 Docker 镜像预装 Node.js + `@modelcontextprotocol/server-filesystem`
- `McpTool` 注册为 Agent 工具，连接后获得 14 个文件操作能力
- 旧 `FileSystemTool` 已移除，统一走 MCP
- `MCP_COMMAND` 环境变量配置

### 修复
- RBAC 改造后 `UserContext` 未设置导致 API 返回空数据 — `JwtAuthFilter` 补上 `UserContext.set()`
- MCP 连接超时 — `sendRpc` 轮询等待 60 秒 + 首次 npx 下载异步化
- Agent 工具参数类型转换 — String → Map JSON 解析

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
- JWT + BCrypt 无状态鉴权
- admin / beta / user / applicant 四角色（后简化为 admin / user / test）
- 注册审核流程：注册 → pending → 管理员 approve/reject → active
- 运营监控大盘：调用量/活跃用户/Token消耗/延迟/错误率/24h趋势/模型分布
- audit_log 表记录每次 API 调用

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
