# 升级改造方向

## 🧪 测试体系

### 后端测试
- **单元测试** — JUnit 5 + Mockito，覆盖 Service 层（UserService / AuditService / ConversationService / WorkflowEngine）
- **集成测试** — `@SpringBootTest` + Testcontainers（MySQL/Redis/Kafka/ES 真实容器），验证 Controller → DB 全链路
- **API 测试** — REST Assured 或 Spring MockMvc，覆盖权限校验（未登录/无权限/正常）
- **SSE 流式测试** — WebTestClient 验证 SSE 数据流正确性（`[TOKEN]` 事件、`[DONE]` 终止）

### 前端测试
- **组件测试** — Vitest + React Testing Library，覆盖 ChatInterface / Sidebar / LoginModal / FlowCanvas
- **E2E 测试** — Playwright 或 Cypress，录制完整的对话/工作流/AI 回复验证流程
- **快照测试** — 关键 UI 组件视觉回归

### 目标
- 后端覆盖率 ≥ 70%，核心 Service ≥ 90%
- 前端关键路径 100% 覆盖

---

## ⚡ 性能优化

- **Redis 缓存** — 模型配置 / 对话历史 / 用户信息热数据缓存，TTL 策略
- **ES 查询优化** — kNN + BM25 混合检索召回率调优，索引分片策略
- **Kafka 批量消费** — 文档向量化改为批量 Embedding API 调用，减少 API 费用
- **Nginx 静态资源** — CDN 加速，Vite 构建产物 hash 化
- **数据库索引** — 慢查询分析，添加复合索引

---

## 🔐 安全加固

- **API 限流** — Bucket4j 或 Spring Cloud Gateway RateLimiter
- **Token 刷新** — Refresh Token + Access Token 双令牌机制
- **API Key 加密存储** — AES 加密模型配置的 ak/sk
- **HTTPS** — 生产环境强制 TLS
- **SQL 注入防护** — 审计所有动态 SQL，全部使用参数化查询

---

## 🤖 Agent 能力扩展

- **Multi-Agent 协作** — 多 Agent 并行执行 + 结果投票/合并
- **Long-term Memory** — 向量化记忆存储 + 语义检索召回
- **Web Search 工具** — SerpAPI / Bing Search API 联网搜索
- **Code Interpreter** — 沙箱化 Python/Node 代码执行
- **本地 MCP STDIO** — 完整的 Model Context Protocol 客户端，支持 `npx` 或 `python` 启动本地 MCP Server，通过 JSON-RPC over STDIO 协议通信。典型场景：读取本地文件系统、执行 Shell 命令、访问本地数据库、调用本地 API 等。当前 `filesystem` 工具为基础实现，后续可替换为标准 MCP Filesystem Server，获得更完整的文件操作能力（写入/移动/搜索/权限校验）

---

## 📊 运营增强

- **Grafana 集成** — Prometheus + Micrometer 指标可视化
- **告警通知** — 钉钉/飞书 Webhook 推送异常告警
- **用量计费** — Token 用量统计 + 用户配额管理
- **A/B 测试** — 模型效果对比实验

---

## 🎨 前端体验

- **国际化 i18n** — 中英文切换
- **移动端适配** — 响应式布局，PWA 离线缓存
- **无障碍 a11y** — ARIA 标签，键盘导航
- **性能监控** — Lighthouse CI，Core Web Vitals
