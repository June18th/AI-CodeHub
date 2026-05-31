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

- [x] ~~HikariCP 连接池调优（max 20 / min-idle 5 / 泄漏检测）~~ → 已完成
- [x] ~~Tomcat 线程池调优（max 200 / min-spare 10 / accept 100）~~ → 已完成
- [x] ~~Lettuce Redis 连接池增强（max-active 16 / min-idle 4）~~ → 已完成
- [x] ~~JVM 调优 + Nginx 限流 + 安全头~~ → 已完成
- [x] ~~Redis 缓存（Conversation / ModelConfig 热点查询 @Cacheable）~~ → 已完成
- [x] ~~ES 查询优化（kNN+BM25 混合检索 RRF + 批量 Embedding + Bulk ES）~~ → 已完成
- [x] ~~Kafka 移除（文档处理改为同步批量 Embedding + Bulk ES）~~ → 已完成
- [x] ~~数据库索引（9 个复合索引，覆盖所有高频查询 + 排序字段）~~ → 已完成
- [x] ~~缓存一致性（CacheConsistencyService 重试+定时修复）~~ → 已完成

---

## 🔐 安全加固

- [x] ~~API 限流（Bucket4j 令牌桶 + RateLimitFilter）~~ → 已完成
- [x] ~~Token 刷新（Refresh Token 7天 + Access Token 15分钟）~~ → 已完成
- [x] ~~SQL 注入防护（${topK} → #{topK} + StdOutImpl → Slf4jImpl）~~ → 已完成
- [x] ~~HTTPS（TLS 1.2/1.3 + HSTS + HTTP→HTTPS 重定向）~~ → 已完成
- [ ] **API Key 加密存储** — AES 加密模型配置的 ak/sk

---

## 🤖 Agent 能力扩展

- **Multi-Agent 协作** — 多 Agent 并行执行 + 结果投票/合并
- **Long-term Memory** — 向量化记忆存储 + 语义检索召回
- [x] ~~MCP STDIO 客户端（JSON-RPC over STDIO + filesystem 工具）~~ → 已完成
- [x] ~~Web Search 工具（SerpAPI 联网搜索，返回标题+摘要+链接）~~ → 已完成
- **Code Interpreter** — 沙箱化 Python/Node 代码执行

---

## 📊 运营增强

- [x] ~~Grafana + Prometheus + Loki + Promtail 全栈监控~~ → 已完成
- [x] ~~JVM 指标仪表盘 + 日志聚合检索~~ → 已完成
- [x] ~~运营监控分页 + 折线趋势图~~ → 已完成
- [ ] **告警通知** — 钉钉/飞书 Webhook 推送异常告警
- [ ] **用量计费** — Token 用量统计 + 用户配额管理
- [ ] **A/B 测试** — 模型效果对比实验

---

> 📋 生产环境升级清单参见 [`docs/PRODUCTION.md`](PRODUCTION.md)

---

## 🎨 前端体验

- **国际化 i18n** — 中英文切换
- **移动端适配** — 响应式布局，PWA 离线缓存
- **无障碍 a11y** — ARIA 标签，键盘导航
- **性能监控** — Lighthouse CI，Core Web Vitals
