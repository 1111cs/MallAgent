# MallAgent

MallAgent 是一个基于 Java、Spring Boot、RAG 与 MCP 构建的可靠商城业务智能体。项目重点不是让大模型自由地“扮演商城客服”，**而是研究模型进入真实业务链路后，如何在 Java 的约束下完成意图路由、知识检索、工具调用、缺参追问、实体消歧、多工具依赖和确定性评测。**

## 为什么选择华为商城场景

消费电子商城同时包含两类差异明显的信息：一类是商品介绍、购物政策、售后流程等公开且相对稳定的知识；另一类是库存、订单、物流、个人优惠券、设备保修和维修进度等私有、实时的业务状态。前者适合使用 RAG，后者必须通过受控工具查询，**因此这一场景适合验证“知识库 + 业务工具 + Java 治理”的完整 Agent 链路。**

本项目以华为商城公开可见的商品分类、购物流程和售后场景作为业务原型，但不接入华为商城生产系统。仓库中的商品、订单、设备 SN、库存、优惠券和维修记录均为重新构造的确定性 Mock Fixture；知识材料也经过抽象和改写，只用于项目演示与可重复评测，不代表华为官网当前商品、政策或内部实现。本项目与华为不存在隶属、合作或官方授权关系。

## 核心设计

MallAgent 采用“模型负责语义理解，Java 负责确定性治理，MCP Server 负责业务查询”的分工：

```text
用户问题
  ↓
问题改写与子问题拆分
  ↓
LLM 基于意图树生成候选 Intent 与置信度
  ↓
Java 路由治理
  ├─ 可信 KB Intent ─→ 定向知识检索
  ├─ 公共知识兜底 ──→ 四类公共知识库全局检索
  ├─ 可信 MCP Intent → 参数门禁 → 实体绑定 → 工具执行
  ├─ 信息不足 ──────→ 缺参追问或实体选择
  └─ 非商城问题 ────→ 明确拒绝
  ↓
证据化上下文与回答生成
  ↓
引用校验、执行 Trace 与 Golden Case 断言
```

模型可以理解“用户想做什么”，但不能决定可信身份，不能凭空生成订单号、商品 ID、优惠券 ID 或设备 SN，也不能绕过当前 Intent 对应的资源契约调用任意工具。

## 业务边界

| 信息类型 | 示例 | 处理方式 |
| --- | --- | --- |
| 公开稳定知识 | 商品特点、配送规则、退换政策、通用保修规则 | RAG 知识库 |
| 用户私有状态 | 我的订单、我的物流、我的优惠券、我的设备 | 只读 MCP Tool |
| 实时业务状态 | 库存、保修状态、维修进度、备件价格 | 只读 MCP Tool |
| 高风险写操作 | 取消订单、修改订单、创建维修单 | 当前版本不开放 |
| 非商城问题 | 与商品、购物、售后和使用支持无关的问题 | 路由治理拒绝 |

系统包含 5 个一级业务领域和 24 个物理叶子 Intent：

- `PRODUCT`：产品信息、比较、推荐、兼容性和库存；
- `SHOPPING_POLICY`：优惠券、发票、配送和退换政策；
- `ORDER`：订单查询、物流查询和订单修改入口；
- `AFTER_SALES`：保修、备件价格、维修服务、维修进度和服务中心；
- `SUPPORT`：使用说明、故障排查、网络连接和充电电池。

完整的 Intent、资源、参数和权限映射见 [业务契约](docs/BUSINESS_CONTRACT.md)。

## 模型接入与多级选择

项目不在仓库中托管远程模型权重，而是通过供应商 HTTP API 接入模型；同时保留 Ollama 作为可选的本地模型适配。API Key 只能通过环境变量配置。

### 冻结评测使用的模型

| 能力 | 平台与模型 | 作用 |
| --- | --- | --- |
| Chat / 推理 | 硅基流动 `Qwen/Qwen2.5-7B-Instruct` | 问题改写、意图识别、参数抽取和最终回答 |
| Embedding | 硅基流动 `Qwen/Qwen3-Embedding-8B` | 将知识片段和查询转换为 1536 维向量 |
| Rerank | 百炼 `qwen3-rerank` | 对向量召回候选进行语义重排 |

20 条 Golden Case 的冻结结果对应上述模型组合，不与后续调整的默认运行配置混为一谈。

### 当前默认配置

| 能力 | 首选模型 | 其他候选或降级 |
| --- | --- | --- |
| 普通对话 | 百炼 `qwen-plus-latest` | 本地 Qwen、百炼 Qwen3-Max、硅基流动 GLM、AIHubMix 模型 |
| 深度思考 | 百炼 `qwen3-max` | 仅从声明支持 Thinking 的候选中选择 |
| Embedding | 硅基流动 `Qwen/Qwen3-Embedding-8B` | 可接入本地或其他兼容服务 |
| Rerank | 百炼 `qwen3-rerank` | 失败时降级为 Noop，保留原召回顺序 |

模型选择分为四层：

1. 按 Chat、Embedding、Rerank 能力选择不同候选池；
2. Chat 再根据普通模式或深度思考模式确定首选模型；
3. 过滤未启用或能力不匹配的候选，首选模型优先，其余按 `priority` 排序；
4. 调用异常时尝试下一候选，连续失败达到阈值后暂时熔断该模型。

默认配置的失败阈值为 2 次，熔断窗口为 30 秒。Chat 模型可以跨供应商降级；Rerank 可以安全降级为不重排。Embedding 则必须与知识入库时使用的模型和向量空间保持一致，更换模型后需要重新生成索引，不能仅因为输出维度相同就直接混用。

## 知识库与 RAG

### 知识边界

项目冻结了四类公共知识库：

| Knowledge Base | 内容 |
| --- | --- |
| `product-kb` | 稳定商品知识、场景和兼容性原则 |
| `shopping-policy-kb` | 优惠券、发票、配送和退换规则 |
| `after-sales-kb` | 通用保修、维修流程和服务说明 |
| `support-kb` | 使用指导、网络连接、充电和故障排查 |

### 分块与向量化

冻结评测数据由 16 份 Markdown 文档组成。导入时以“二级标题 + 对应正文”形成独立知识卡片，并在卡片中保留文档一级标题；不同主题不跨块合并，相邻块无重叠。最终形成 51 个 Chunk 和 51 个 1536 维向量。

文档 YAML 前言不进入 Embedding 正文，其中的知识库 ID、数据版本、快照时间和内容范围写入 metadata，用于后续证据追踪。

除冻结数据采用的知识卡片方式外，通用知识入库模块还支持：

- **固定长度分块**：默认 512 字符，重叠 128 字符；
- **Markdown 结构感知分块：**默认目标 1400 字符、上限 1800、下限 600、无重叠。、

>  说明：冻结版本优先保证评测可复现和证据可追踪，不代表这是线上检索效果最优的分块方案。**生产化时应进一步采用结构感知切分，并结合长度上限、段落边界和适量重叠控制 Chunk 粒度。**

### 检索策略

使用的策略是： **“先路由、再召回、再重排、最后进 Prompt”**

```text
用户问题改写
  ↓
生成 Query Embedding
  ↓
Java 路由决定检索范围
  ├─ Intent 定向检索：只查询 Intent 映射的 Collection
  └─ 公共知识兜底：只查询四个公共 KB 白名单
  ↓
pgvector HNSW + COSINE 召回候选
  ↓
按来源与 Chunk 身份去重
  ↓
qwen3-rerank 语义重排
  ↓
选择最终 TopK 证据进入 Prompt
```

**当前向量存储默认使用 PostgreSQL + pgvector，索引类型为 HNSW，距离度量为 Cosine。**默认目标 TopK 为 10，也可以由具体 Intent 单独配置；

意图定向检索会扩大候选池后再重排，公共知识兜底会在白名单 Collection 中扩大召回范围。

全局知识库检索不是模型随意发起的：只有 Java 路由治理明确给出 `GLOBAL_KB_FALLBACK` 时才会启用。

**检索结果在整个链路中保留 Knowledge Base、Collection、Document、Chunk、召回分数、重排分数和最终顺序，便于生成引用、记录 Trace 和定位评测失败。**

> 说明：当前版本尚未接入 BM25。后续可增加 BM25 稀疏召回，与向量召回并行后通过 RRF 或加权融合合并，再交给 Rerank 模型排序

## MCP 工具与调用治理

项目提供 8 个只读 MCP Tool：

```text
searchProducts
getProductAvailability
getOrder
getLogistics
getCouponEligibility
getWarrantyStatus
getSparePartPrice
getRepairProgress
```

工具确定后，Java 依次执行以下门禁：

1. 根据 Intent 与资源契约确认允许调用的 Tool；
2. 只向模型提供已选 Tool 的参数 Schema；
3. 对普通参数执行类型、格式和必填校验；
4. 缺少参数时保存 Pending Task，并向用户追问；
5. 商品 ID、订单号、SN 等关键实体必须从可信业务候选中绑定；
6. 多个候选时保存候选列表，让用户选择后重新校验；
7. 下游依赖参数只能从身份上下文、实体解析或上游 Tool 结果中绑定；
8. 用户身份由服务端认证上下文注入，模型和请求参数均不可覆盖；
9. MCP Server 验证运行时证明后执行只读查询并返回结构化结果。

因此，模型负责理解自然语言，Java 负责约束参数和执行路径，MCP Server 负责最终业务查询。

## 可靠性闭环

| 场景 | 处理方式 |
| --- | --- |
| 缺少必要参数 | 将结构化任务保存到 Redis，结束当前请求并追问 |
| 用户下一轮补充参数 | 优先恢复 Pending Task，合并后重新校验 |
| 用户取消任务 | 原子删除 Pending 状态并结束 |
| 用户转而提出新问题 | 放弃旧 Pending Task，重新进入正常路由 |
| 多个实体候选 | 保存任务与可信候选，要求用户明确选择 |
| 多工具依赖 | 上游结果结构化绑定到下游参数，禁止模型猜测 |
| 外部模型失败 | 按候选顺序降级，并通过健康状态暂时隔离故障模型 |
| Rerank 失败 | 降级为原始向量召回顺序，不中断回答链路 |

## Golden Case 评测

项目将关键 Agent 执行过程冻结为 20 条 Golden Case，覆盖：

- 知识库问答；
- 普通工具调用；
- 缺参补问与任务续接；
- 可信实体消歧；
- 多工具依赖与参数传递。

每条 Case 固定用户身份、问题、业务时钟、Mock Fixture、预期 Intent、资源、工具、参数来源和最终任务状态。评测通过真实接口执行完整链路并记录结构化 Trace，随后逐项断言路由结果、工具调用、参数来源、证据和任务状态；全部断言通过后，该 Case 才判定为成功。

| 系统阶段 | 结果 |
| --- | ---: |
| 业务适配后的原始 Baseline | 7/20 PASS |
| 加入闭环治理后的 MallAgent | 20/20 PASS |

Golden Case 主要回答“Agent 是否做对了事情”。回答是否自然、表达是否友好仍需要人工体验评估。评测范围和限制见 [评测说明](docs/EVALUATION.md)。

## 模块结构

```text
MallAgent
├─ bootstrap       HTTP、会话、RAG 主链、闭环执行、Trace 与评测
├─ framework       用户上下文、领域约定和通用基础组件
├─ infra-ai        Chat、Embedding、Rerank 与供应商适配
├─ mcp-server      商城只读工具和 Mock 业务数据查询
├─ frontend        React 对话页面、管理后台和 Golden Case 页面
├─ fixtures
│  ├─ database     Mock Schema、Seed 和事实审计
│  ├─ knowledge    四类知识材料和 Intent 映射
│  └─ evaluation   Golden Case 与路由冒烟用例
└─ docs            架构、业务契约、运行与评测说明
```

更详细的组件关系见 [系统架构](docs/ARCHITECTURE.md)。

## 本地验证

环境要求：

- JDK 17；
- Maven 3.9+；
- Node.js 18+；
- PostgreSQL 15+，并安装 pgvector；
- Redis 7；
- 可选：RocketMQ、RustFS、Ollama；
- 完整端到端运行需要配置相应的模型 API Key。

复制环境变量模板并填写本地配置：

```bash
cp .env.example .env
```

执行 Java 测试：

```bash
mvn clean test
```

构建前端：

```bash
cd frontend
npm ci
npm run build
```

数据库、MCP Server、Bootstrap 和前端的完整启动方式见 [本地运行指南](docs/LOCAL_SETUP.md)。公开整理的验证范围见 [发布验证报告](docs/VERIFICATION.md)。

## 安全说明

- 仓库不包含真实 API Key、生产账号；
- API Key、数据库密码、登录 Token 和运行时签名密钥必须通过环境变量传入；
- MCP Server 只开放本项目定义的只读工具；

配置模板见 [.env.example](.env.example)，安全问题说明见 [SECURITY.md](SECURITY.md)。

## 上游与许可证

MallAgent 基于 [Ragent](https://gitee.com/nageoffer/ragent) 修改，保留其 Java RAG 基础设施和 Apache-2.0 许可证。商城业务适配、确定性 Fixture、Agent 闭环治理、证据追踪和 Golden Case 评测是在此基础上的扩展。

详细归属见 [NOTICE](NOTICE)。本项目采用 [Apache License 2.0](LICENSE)。
