# MallAgent 系统架构

MallAgent 将大模型的概率性语义理解与 Java 的确定性业务治理分开：模型提出 Intent、参数和执行建议，Java 决定系统允许走哪条路径，MCP Server 执行最终的只读业务查询。

## 1. 组件职责

| 职责 | 负责组件 |
| --- | --- |
| 理解用户表达、生成候选 Intent | LLM Intent 分类器 |
| 过滤低置信度候选、限制候选数量 | Java Intent Resolver |
| 决定进入知识库、工具、系统响应或拒绝 | Routing Governance 与资源契约 |
| 获取可信登录身份 | 服务端认证上下文 |
| 校验参数格式、来源和数据归属 | Bootstrap 与 MCP Server |
| 缺参补问并恢复待处理任务 | Missing Parameter 闭环 |
| 从可信候选中确定业务实体 | Entity Resolution 闭环 |
| 绑定上游工具结果与下游参数 | Dependency Coordinator |
| 保留检索证据和工具来源 | Trace 与 Evidence 组件 |
| 检查引用和 Golden Case | 确定性评测器 |

## 2. 请求主链

```text
HTTP / SSE 请求
  ↓
登录身份与会话上下文
  ↓
问题改写和子问题拆分
  ↓
LLM 生成候选 Intent 与置信度
  ↓
Java 路由治理
  ├─ KB Intent ─────→ 定向知识检索
  ├─ 公共知识兜底 ─→ 四个公共知识库
  ├─ MCP Intent ────→ 参数与权限门禁 → 工具执行
  ├─ 信息不足 ─────→ 缺参补问或实体选择
  └─ 超出范围 ─────→ 明确拒绝
  ↓
证据化上下文与回答生成
  ↓
引用校验、Trace 记录与评测断言
```

模型可以理解“用户想做什么”，但不能提供可信身份、绕过资源映射调用任意工具，也不能凭空生成商品 ID、优惠券 ID 或设备 SN。

## 3. 模块结构

```text
MallAgent
├─ bootstrap       HTTP、会话、RAG 主链、闭环执行、Trace 与评测
├─ framework       用户上下文、领域约定和通用基础组件
├─ infra-ai        Chat、Embedding、Rerank 与供应商适配
├─ mcp-server      商城只读工具和 Mock 业务查询
├─ frontend        React 对话页面、管理后台和评测 Runner
├─ fixtures        数据库、知识材料与评测用例
└─ docs            架构、业务契约、运行和验证文档
```

## 4. Intent 与路由治理

意图识别模型只接收 Intent 的名称、描述和示例，输出候选 Intent 与置信度。Java 随后完成：

1. 过滤低于阈值的候选并限制数量；
2. 检查 Intent 是否映射到受控 KB、MCP Tool 或 SYSTEM 资源；
3. 判断进入定向检索、公共知识兜底、澄清还是拒绝；
4. 工具确定后，才向参数抽取模型提供该工具的 Schema。

一个逻辑 Intent 不一定只对应一个资源。例如商品推荐可以解释选购知识，也可以根据预算筛选具体商品，因此分别映射到 KB 与 MCP 物理路由。完整映射见 [商城业务契约](BUSINESS_CONTRACT.md)。

## 5. RAG 链路

### 5.1 知识边界

系统包含四个公共知识库：

| Knowledge Base | 内容 |
| --- | --- |
| `product-kb` | 商品能力、使用场景与兼容性知识 |
| `shopping-policy-kb` | 优惠、发票、配送与退换规则 |
| `after-sales-kb` | 通用保修、维修流程与服务说明 |
| `support-kb` | 使用指导、连接、电池与故障排查 |

用户订单、物流、优惠券、设备保修等私有或动态状态不进入知识库，只能通过受控工具查询。

### 5.2 分块与向量化

固定评测数据由 16 份 Markdown 文档组成，按照“二级标题 + 对应正文”形成 51 个知识单元。YAML 前言不进入 Embedding 正文，其中的知识库 ID、数据版本、快照时间和内容范围写入 metadata。

该切分方式优先保证评测可复现，并非生产最优方案。通用入库模块还支持固定长度和 Markdown 结构感知切分；生产化时应结合长度上限、段落边界、适量重叠和父子块降低长段噪声与跨标题信息丢失。

### 5.3 检索与重排

```text
问题改写
  ↓
Query Embedding
  ↓
Java 决定检索范围
  ├─ Intent 定向检索
  └─ 公共知识白名单兜底
  ↓
pgvector HNSW + Cosine 召回
  ↓
按来源与 Chunk 身份去重
  ↓
Rerank 语义重排
  ↓
TopK 证据进入 Prompt
```

当前默认向量存储为 PostgreSQL + pgvector，索引使用 HNSW，距离度量为 Cosine；目标 TopK 默认是 10，也可由 Intent 单独配置。Rerank 失败时保留原始召回顺序，不中断回答链路。

当前没有接入 BM25。后续可以加入稀疏召回，与向量召回通过 RRF 或加权融合合并，再交给 Rerank 排序。

检索结果会保留 Knowledge Base、Collection、Document、Chunk、召回分数、重排分数和最终顺序，用于引用、Trace 和失败定位。

## 6. MCP 工具链路

系统开放八个只读工具：

```text
searchProducts              getProductAvailability
getOrder                   getLogistics
getCouponEligibility       getWarrantyStatus
getSparePartPrice          getRepairProgress
```

工具调用需要依次通过：

1. Intent—资源映射与工具白名单；
2. 单工具 Schema 参数抽取；
3. 类型、格式和必填校验；
4. 缺参补问或实体消歧；
5. 身份、实体和上游工具结果的可信参数绑定；
6. MCP Server 的运行时证明与数据归属校验。

Bootstrap 会移除模型提供的身份字段，注入登录用户映射、签发时间和 HMAC 证明。MCP Server 验证证明后，才查询隔离的 `huawei_mall_mock` Schema。

## 7. 三类执行闭环

### 7.1 缺参补问

参数不完整时，系统将结构化任务保存到 Redis 并结束本轮请求。用户下一轮可以补充参数、取消任务或提出新问题；系统根据输入恢复、删除或放弃旧任务。

### 7.2 实体消歧

关键业务主键从数据库或上游工具返回的可信候选中选择。唯一候选直接绑定；多个候选交给用户选择；无候选时要求用户提供更明确的信息。

### 7.3 多工具依赖

依赖工具按阶段执行，只有契约声明的上游字段才能绑定到下游参数。例如先查询用户订单，再从订单结果中绑定设备 SN，最后查询保修状态。

所有补问、选择、取消、冲突和过期路径都会记录到 Trace。

## 8. 模型接入

项目通过供应商 HTTP API 接入 Chat、Embedding 和 Rerank 模型，并保留 Ollama 本地适配。模型候选按照能力、是否启用和优先级选择，调用失败后可以切换候选并暂时隔离连续故障的模型。

固定 20 条 Golden Case 使用的模型组合为：

| 能力 | 模型 |
| --- | --- |
| Chat / 推理 | 硅基流动 `Qwen/Qwen2.5-7B-Instruct` |
| Embedding | 硅基流动 `Qwen/Qwen3-Embedding-8B` |
| Rerank | 百炼 `qwen3-rerank` |

Chat 可以跨供应商降级；Rerank 可以降级为不重排。Embedding 必须与知识入库时的向量空间一致，更换模型后需要重建向量索引。

## 9. 数据与安全边界

- 公开稳定知识进入四个知识库；
- 私有和动态事实位于 12 张合成业务表中；
- MCP Server 只提供只读查询，不开放取消订单等写操作；
- API Key、数据库密码和运行时密钥只从环境变量读取；
- Fixture 不包含真实用户或华为生产数据。

数据库、缓存、消息队列和模型服务的配置方式见 [本地运行指南](LOCAL_SETUP.md)。
