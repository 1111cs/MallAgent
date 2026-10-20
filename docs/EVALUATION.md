# Agent 评测设计

MallAgent 的评测目标不是判断回答“像不像客服”，而是验证 Agent 是否沿受控路径完成了正确的业务动作。

## 1. Golden Case 组成

固定用例集包含 20 条端到端 Case：

| 场景 | 数量 | 主要检查内容 |
| --- | ---: | --- |
| 知识库问答 | 5 | Intent、知识库路由、证据与引用 |
| 普通工具调用 | 5 | 工具选择、参数、身份与业务结果 |
| 缺参补问 | 4 | 缺参识别、Pending Task 和续接状态 |
| 实体消歧 | 3 | 可信候选、用户选择和最终实体绑定 |
| 多工具依赖 | 3 | 调用顺序、上游结果和下游参数来源 |

用例定义见 [`fixtures/evaluation/agent-golden-cases-v1.json`](../fixtures/evaluation/agent-golden-cases-v1.json)。

## 2. 单条 Case 包含什么

每条 Case 至少固定：

- 用户身份；
- 用户问题和必要的多轮补充；
- 业务时钟与 Fixture 版本；
- 预期 Intent 和资源；
- 预期工具及调用顺序；
- 预期参数值与参数来源；
- 预期任务状态和关键业务事实。

评测通过真实接口执行完整链路，并从 execution-record 与 Trace 中读取实际结果。只有路由、工具、参数、顺序、状态和关键事实断言全部通过，该 Case 才算通过。

## 3. 参数来源断言

评测不仅检查参数值，还检查参数来自哪里：

| 来源 | 示例 |
| --- | --- |
| `USER_QUERY` | 用户明确提供的普通查询条件 |
| `CLARIFICATION_INPUT` | 用户对补问的回答 |
| `AUTH_CONTEXT` | 服务端注入的登录身份 |
| `ENTITY_RESOLUTION` | 从可信候选绑定的 SN、优惠券或商品 ID |
| `TOOL_OUTPUT` | 从上游工具结果绑定到下游的参数 |
| `DEFAULT` | 业务契约规定的默认区域等参数 |

这样可以发现“结果碰巧正确，但模型猜了一个业务主键”之类的隐性错误。

## 4. 对比结果

在相同业务契约、Fixture、身份映射、业务时钟和模型组合下，历史对比结果为：

| 系统阶段 | 通过数 |
| --- | ---: |
| 仅完成商城业务适配的原始机制 | 7/20 |
| 加入缺参、消歧、依赖与路由治理后的 MallAgent | 20/20 |

精简后的逐 Case 结果见 [`evaluation-result-v1.json`](evaluation-result-v1.json)。完整原始 Trace 体积较大且包含运行时 ID，因此不再放入公开仓库。

该结果只说明系统在固定用例和固定环境中满足业务契约，不代表任意模型、任意提问或真实生产环境下的准确率。

## 5. Intent 路由评测

除 20 条端到端 Case 外，项目还提供 24 条 Intent 路由用例，每个物理叶子 Intent 对应一条改写问题：

[`fixtures/evaluation/intent-routing-cases-v1.json`](../fixtures/evaluation/intent-routing-cases-v1.json)

这组用例只检查顶层 Intent 和最终资源，不评价工具参数、检索证据或回答质量。Intent 标签正确率和资源路由正确率应分别统计，因为相邻 Intent 可能映射到同一个安全资源。

## 6. 自动化测试与端到端评测的区别

默认执行：

```bash
mvn test
```

当前默认测试运行 36 条确定性测试，不需要真实模型和完整基础设施。

需要外部模型、PostgreSQL、Redis、向量数据或 MCP 运行环境的测试使用：

```bash
mvn -Pexternal test
```

Golden Case 属于完整业务链路评测，会受到模型服务、Embedding、Rerank、网络和已导入向量数据的影响；它不能替代单元测试，单元测试也不能替代真实 Agent 链路评测。

## 7. 主观体验评估

结构化 Golden Case 主要回答“Agent 有没有做对事情”。以下指标仍需人工体验评估：

- 回答是否自然、简洁；
- 补问是否容易理解；
- 候选实体是否便于选择；
- 错误提示是否友好；
- 等待时间是否可以接受。

自动断言和人工体验评估应分开记录，避免用语言流畅度掩盖错误工具调用，也避免把表达偏好误判为业务失败。
