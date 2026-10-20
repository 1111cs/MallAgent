# MallAgent

MallAgent 是一个基于 Java、Spring Boot、RAG 与 MCP 的商城业务 Agent，重点解决大模型接入真实业务后出现的缺参追问、实体消歧、多工具依赖、身份隔离和可重复评测问题。

- 5 个业务域、24 个物理叶子 Intent
- 4 个公共知识库、8 个只读 MCP Tool
- 覆盖知识问答、工具调用、缺参、消歧、多工具依赖 5 类场景
- 固定 Golden Case：原始机制 `7/20` → 闭环治理后 `20/20`
- 默认 Maven 测试：`36` 条通过、`0` 失败

> Golden Case 结果对应固定业务契约、Fixture、身份、时钟和模型组合，不代表真实生产环境的通用准确率。

## 做到了什么

| 能力 | 实现结果 |
| --- | --- |
| RAG 与工具分流 | 公开稳定知识进入 4 个知识库，私有或动态状态进入 8 个只读工具 |
| 缺参补问 | Redis 保存结构化 Pending Task，下一轮恢复、取消或放弃 |
| 实体消歧 | SN、优惠券和商品等关键实体从可信业务候选中绑定 |
| 多工具依赖 | 上游结果按契约绑定到下游参数，模型不能猜测业务主键 |
| 身份与权限 | 用户身份由服务端注入，MCP Server 再校验运行时证明和数据归属 |
| 可重复评测 | 固定身份、时钟与 Mock 数据，逐项断言 Intent、工具、参数来源和任务状态 |

逐 Case 结果见 [精简评测结果](docs/evaluation-result-v1.json)，评测方法见 [Agent 评测设计](docs/EVALUATION.md)。

## 快速验证

快速验证只运行不依赖外部模型和完整基础设施的确定性测试：

```bash
git clone https://github.com/1111cs/MallAgent.git
cd MallAgent
mvn test
```

前端生产构建：

```bash
cd frontend
npm ci
npm run build
```

完整启动还需要 PostgreSQL + pgvector、Redis、RocketMQ、对象存储和模型服务，具体步骤见 [本地运行指南](docs/LOCAL_SETUP.md)。

## 核心执行链路

```text
用户问题
  ↓
LLM 生成候选 Intent 与置信度
  ↓
Java 路由治理
  ├─ KB Intent ─────→ 定向检索 → 重排 → 证据化回答
  ├─ MCP Intent ────→ 参数门禁 → 身份/实体绑定 → 工具执行
  ├─ 信息不足 ─────→ 缺参补问或实体选择
  ├─ 公共知识兜底 ─→ 4 个公共知识库白名单检索
  └─ 超出业务范围 ─→ 明确拒绝
  ↓
引用校验、执行 Trace 与 Golden Case 断言
```

系统的职责边界是：**模型负责语义理解，Java 负责确定性治理，MCP Server 负责最终业务查询。**

## 代表性场景

用户询问“上个月买的手表还在不在保”时，系统不会让模型直接生成 SN：

```text
WARRANTY_STATUS
  → getOrder 查询当前用户上月订单
  → 从订单结果绑定设备 SN
  → getWarrantyStatus 查询保修状态
  → Trace 记录 Intent、调用顺序和参数来源
```

如果存在多块手表，系统保存可信候选并让用户选择；如果缺少必要信息，则保存待处理任务并在下一轮续接。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot、MyBatis-Plus |
| Agent 主链 | Intent 路由、RAG、闭环任务、Trace、Golden Case |
| 工具协议 | MCP、HTTP、HMAC 运行时证明 |
| 检索 | PostgreSQL、pgvector、HNSW、Cosine、Rerank |
| 状态与基础设施 | Redis、RocketMQ、RustFS / S3 |
| 模型 | OpenAI Compatible Chat、Embedding、Rerank API |
| 前端 | React、TypeScript、Vite |

模型选择、检索链路与降级策略见 [系统架构](docs/ARCHITECTURE.md)。

## 项目结构

```text
MallAgent
├─ bootstrap       HTTP、会话、RAG、闭环执行、Trace 与评测
├─ framework       用户上下文、领域约定和通用基础组件
├─ infra-ai        Chat、Embedding、Rerank 与供应商适配
├─ mcp-server      8 个只读工具和 Mock 业务查询
├─ frontend        React 页面与评测 Runner
├─ fixtures        数据库、知识材料和评测用例
└─ docs            架构、契约、运行和验证文档
```

## 文档导航

| 想了解的内容 | 文档 |
| --- | --- |
| 系统组件、RAG、工具链和模型策略 | [系统架构](docs/ARCHITECTURE.md) |
| Intent、工具、参数与权限规则 | [商城业务契约](docs/BUSINESS_CONTRACT.md) |
| Golden Case 设计、断言与结果边界 | [Agent 评测设计](docs/EVALUATION.md) |
| 数据库、MCP Server、Bootstrap 和前端启动 | [本地运行指南](docs/LOCAL_SETUP.md) |
| 实际执行过的测试与构建结果 | [发布验证报告](docs/VERIFICATION.md) |
| Mock 数据、知识材料和评测用例 | [Fixture 说明](fixtures/README.md) |

## 数据与项目边界

本项目选择消费电子商城，是因为它同时包含公开稳定知识与用户私有、动态业务状态，适合验证“RAG + 工具 + Java 治理”的完整链路。

项目以华为商城公开可见的商品分类、购物和售后场景作为业务原型，但不连接华为生产系统。商品、订单、设备 SN、库存、优惠券和维修记录均为重新构造的确定性 Mock 数据；知识材料经过整理和改写，不代表官网实时内容或内部实现。本项目与华为不存在隶属、合作或官方授权关系。

当前版本只开放查询能力，不支持取消订单、修改订单、领取优惠券或创建维修单等写操作。

## 安全与许可证

- 仓库不包含真实 API Key、生产账号或登录 Token；
- 密钥、数据库密码和 MCP 运行时密钥必须通过环境变量传入；
- 安全配置见 [SECURITY.md](SECURITY.md) 和 [.env.example](.env.example)。

MallAgent 基于 [Ragent](https://gitee.com/nageoffer/ragent) 修改，并在此基础上扩展商城业务契约、确定性 Fixture、闭环治理、证据追踪和 Golden Case 评测。详细归属见 [NOTICE](NOTICE)，项目采用 [Apache License 2.0](LICENSE)。
