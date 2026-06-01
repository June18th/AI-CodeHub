# 企业级 AI 协作平台

## 平台定位

AI-CodeHub 提供知识库管理、智能对话、可视化工作流三大核心功能。

## 知识库权限

文档上传时设定可见范围，通过组织标签层级穿透实现细粒度权限控制。

| 级别 | 可见范围 | 典型场景 |
|------|---------|---------|
| PRIVATE | 仅上传者本人 | 个人笔记、草稿、未定稿方案 |
| DEPARTMENT | 同组织标签成员（含父级标签） | 部门周报、技术方案、内部流程 |
| PUBLIC | 全公司所有人（含管理员） | 公司制度、公告、通用规范 |

ADMIN 和 TEST 角色可查看所有文档（鉴权过滤跳过）。

## 权限矩阵

| 角色 | PRIVATE(他人) | DEPARTMENT(其他标签) | PUBLIC |
|------|:---:|:---:|:---:|
| ADMIN / TEST | 可见全部 | 可见全部 | 可见 |
| 普通成员 | 不可见 | 仅同组织标签及父级可见 | 可见 |

## 技术实现

### 文件上传（分片 + 合并）
```
前端 (spark-md5)
  ├─ 计算文件 MD5 → 5MB 分片
  ├─ POST /chunk/init     → MySQL file_upload 表 (uploading)
  ├─ POST /chunk           → MinIO chunks/{md5}/{index} + Redis bitmap
  ├─ POST /chunk/{md5}/merge → MinIO composeObject 合并
  └─ Kafka document-ready  → 异步处理

后端异步处理
  ├─ DocumentProcessingConsumer 消费 Kafka 消息
  ├─ MinIO 下载合并后文件 → 分块 (500字)
  ├─ EmbeddingService → DashScope text-embedding-v4 批量向量化
  └─ ES doc_chunks 索引 (kNN+BM25 混合检索)
```

### RAG 检索

## 系统提示词

平台内置 4 套提示词（`resources/prompts/`），按场景选用：

| 提示词 | 适用场景 |
|------|------|
| `assistant.md` | 通用助手，默认使用 |
| `knowledge.md` | 知识库问答，强调来源标注 |
| `workflow.md` | 工作流设计与优化 |
| `agent.md` | Agent 模式，含工具列表与预算状态 |

提示词外置为 `.md` 文件（`backend/src/main/resources/prompts/`），修改无需重编译。

## 运营管理（管理员）

管理员拥有独立后台（`/admin`），覆盖用户审核、权限管控、可观测性三大维度。

### 用户审核
- 注册默认 `pending` 状态，管理员在后台审批（通过/拒绝）
- 审批后自动分配组织标签与主标签（`PRIVATE_{username}`）

### 权限管理（RBAC）
- 数据库驱动权限模型：`permission` + `role_permission` 表存储角色-权限映射
- Redis 缓存权限（`rbac:perm:{role}`，24h TTL），多实例共享
- `POST /api/v1/admin/permissions/reload` 运行时刷新缓存，无需重启
- `@PreAuthorize` 方法级鉴权（如 `hasRole('dashboard:view')`）

### 可观测性
| 组件 | 用途 |
|------|------|
| Prometheus | JVM 指标采集（Actuator + Micrometer） |
| Grafana | JVM Dashboard + 业务指标仪表盘 |
| Loki + Promtail | 日志聚合检索 |

- `/admin/logs` — 日志级别分布 + 24h 折线趋势图，5 秒自动刷新
- `/admin/tokens` — Token 消耗分布条形图 + 分页明细表

### 限流与熔断
- `RateLimitFilter`（Bucket4j 令牌桶），默认 60/20/120 rpm
- `AiApiClient` 内置熔断器：连续 5 次失败 → 30 秒熔断

### 审计日志
- `audit_log` 表记录每次 API 调用：用户、模型、token 消耗、延迟、状态
- 游客模式聊天同样写入审计日志
