# G1 Routing Governance Audit V1

状态：`G1_AUDIT_AND_FALLBACK_CONTRACT_FROZEN`

日期：2026-09-18

## 1. 审计结论

当前 Ragent 没有独立的 Routing/Fallback 决策层。Intent 分类、Guidance、System、KB 检索和 Tool 执行分别存在，但 `Intent=[]`、低置信度、相邻意图和分类器异常没有被统一治理。

最关键的事实是：`Intent=[]` 当前会进入 `VectorGlobalSearchChannel`。这个行为不是 `RetrievalEngine` 中一个显式的“空意图分支”，而是以下链路共同形成：

```text
IntentResolver
  → 保留 SubQuestionIntent(question, nodeScores=[])
RetrievalEngine
  → 仍为该子问题构建 KB Context
MultiChannelRetrievalEngine
  → 构造 SearchContext
VectorGlobalSearchChannel.isEnabled
  → allScores 为空
  → return true
  → 查询数据库中全部启用 KB collection
```

因此，“分类器不知道”“分类器异常”“身份/系统问题”“表达不完整”“越界问题”当前都会被压成同一种全库检索行为。

## 2. 真实控制点

| 控制点 | 当前行为 | 审计判断 |
| --- | --- | --- |
| `StreamChatPipeline.execute` | Pending → Rewrite → Intent → Guidance → System → Retrieval | G1 的最小策略入口应位于 Pending 之后、普通 Intent/Retrieval 之前 |
| `IntentResolver.resolve` | 分类异常捕获后返回空 `nodeScores` | 把“分类失败”和“确实无业务 Intent”合并，必须保留原因 |
| `IntentResolver.classifyIntents` | 过滤 `<0.35`，最多保留 3 个 | 可能同时保留 KB/MCP 相邻候选 |
| `handleGuidance` | 只处理单子问题、至少两个 KB 候选、跨系统相近分数 | 不处理空 Intent、MCP 相邻意图、身份问题或越界问题 |
| `handleSystemOnly` | 只有唯一 SYSTEM Intent 时进入 System Prompt | 空 Intent 不会进入；当前 System Prompt 仍是原企业助手范围，不是 Huawei Mall 契约 |
| `RetrievalEngine` | 对每个保留的 KB/MCP candidate 执行对应资源 | 一个主 Route 正确时，次级 MCP candidate 仍可能被执行 |
| `VectorGlobalSearchChannel.isEnabled` | 空 score、最高分 `<0.6`、单意图分数 `<0.8` 均可启用 | 全局检索承担了过多语义，且可能与定向检索同时执行 |
| `VectorGlobalSearchChannel.search` | 查询数据库中全部启用 KB collection | 缺少“该问题是否允许进入公共知识检索”的业务前置条件 |
| `ExecutionRecord.routes` | Pipeline 只记录每个子问题第一个 score | 不能证明所有实际参与执行的候选资源；KB-02 即为代表问题 |

## 3. 已由实验直接证明的问题

### 3.1 No-Intent Meta Misroute

```text
我的用户编号是多少？
→ Intent=[]
→ VectorGlobalSearch
→ Support KB 等无关 Evidence
```

根因不是 Guidance，而是缺少 No-Intent Policy。身份值必须来自可信 Auth Context，不能来自 KB 或 LLM 猜测。

### 3.2 结构路由错误

- KB-02：Trace 的主 Route 为 `PRODUCT_RECOMMEND_KB → product-kb`，但同轮仍执行了次级 `searchProducts`。说明“只记录 Top-1 Route”和“执行全部保留候选”不一致。
- KB-05：`CONNECTION_NETWORK` 被识别为 `USAGE_GUIDE`。二者落到同一 `support-kb`，最终回答可能正确，但契约层仍然错误。

### 3.3 Tool 相邻意图误路由

- TOOL-01：订单号查询得到空 Intent，最终没有执行 `getOrder`。
- TOOL-02：“订单到哪了”在 Rewrite 后变为“订单状态”，以 `0.85` 路由至 `ORDER_QUERY/getOrder`，没有执行 `LOGISTICS_QUERY/getLogistics`。

这证明高置信度不是资源选择正确性的充分条件；Rewrite 也可能抹掉区分相邻 Tool 的关键谓词。

## 4. 其他关键发现

1. **空 Intent 原因丢失**：分类器返回空、JSON 解析失败、LLM 调用异常都产生同一种 `nodeScores=[]`。
2. **阈值语义不一致**：分类 Prompt 建议低于 `0.6` 返回空；Resolver 接受 `0.35`；定向 KB 接受 `0.4`；Global 在 `<0.6` 或单意图 `<0.8` 时启用。
3. **LLM 自报 confidence**：score 直接来自分类模型，没有校准，也没有资源契约二次检查。
4. **主路由 Trace 不完整**：Trace 只记录 Top-1，但 Retrieval 会执行所有保留的 KB/MCP candidate。
5. **System Prompt 业务范围过期**：仍描述人事、行政、IT 和中间件，不符合 Huawei Mall Agent。
6. **Global Retrieval 没有业务准入条件**：只看 Intent score，不看问题是否公共知识、私有状态、系统元问题或越界请求。

## 5. 冻结 RoutingDecision

```text
ROUTED
AUTH_CONTEXT
SYSTEM
NEED_CLARIFICATION
GLOBAL_KB_FALLBACK
OUT_OF_SCOPE
```

含义冻结如下：

| Decision | 适用条件 | 允许动作 | 禁止动作 |
| --- | --- | --- | --- |
| `ROUTED` | 至少一个可信叶子 Intent，且资源符合 Intent Contract | 进入指定 KB、Tool 或 System | 不得因低于补充阈值自动混入无关资源 |
| `AUTH_CONTEXT` | 查询当前登录身份的允许字段 | 从可信认证上下文确定性回答 | 禁止 KB、Tool、LLM 生成 principalId |
| `SYSTEM` | Agent 身份、能力、使用范围等系统元问题 | 使用 Huawei Mall 固定能力描述 | 禁止全库检索 |
| `NEED_CLARIFICATION` | 有业务动作但目标/动作不足以确定 | 返回确定性澄清 | 禁止猜 Intent 或调用终态 Tool |
| `GLOBAL_KB_FALLBACK` | 明确属于 Huawei Mall 公共稳定知识，但没有可靠单一 Intent | 只检索允许的公共 KB | 禁止检索用户态数据，禁止调用 Tool |
| `OUT_OF_SCOPE` | 明显不属于 Huawei Mall 能力范围 | 返回能力边界和可用能力 | 禁止全库检索和 Tool |

## 6. 冻结优先顺序

```text
User Query
  ↓
Pending D1/D2/D3 Resume?
  ├─ YES → 恢复已有任务
  ↓
Pre-routing Policy
  ├─ 当前身份问题 → AUTH_CONTEXT
  ├─ Agent 元问题 → SYSTEM
  ↓
Intent Resolver（保留 originalQuery + rewrittenQuery）
  ↓
可信 Intent 与资源契约校验
  ├─ PASS → ROUTED
  ↓
Fallback Policy
  ├─ 公共稳定商城知识 → GLOBAL_KB_FALLBACK
  ├─ 业务表达不完整 → NEED_CLARIFICATION
  └─ 明显越界 → OUT_OF_SCOPE
```

`Intent=[]` 只表示“当前没有可信 Intent”，不再直接代表任何具体 Decision。

## 7. Fallback Contract V1

1. `AUTH_CONTEXT` 仅支持当前认证用户的允许字段；V1 首个字段为业务 `principalId`。
2. `SYSTEM` 使用固定 Huawei Mall 能力清单，不读取 KB。
3. `NEED_CLARIFICATION` 必须包含可回答的问题，不返回内部 Intent 或字段名。
4. `GLOBAL_KB_FALLBACK` 只允许四个公共 KB：`product-kb`、`shopping-policy-kb`、`after-sales-kb`、`support-kb`。
5. 私有订单、物流、设备保修状态、优惠券状态、库存、维修进度不得进入 Global KB fallback。
6. `OUT_OF_SCOPE` 不调用检索或 Tool。
7. 分类器技术失败必须记录为 `CLASSIFIER_ERROR`，不能伪装成普通 No-Intent。
8. `ROUTED` 执行资源必须与最终 RoutingDecision 中记录的资源集合一致，不能只记录 Top-1 后执行隐藏的次级 Tool。
9. 相邻 Tool 冲突优先使用业务判别词和资源契约；无法唯一确定时进入 `NEED_CLARIFICATION`，不按最高自报 score 强行执行。
10. Original Query 中的强业务判别词不能被 Rewrite 覆盖；两者冲突时记录 `REWRITE_CONFLICT`。

## 8. Trace 最小扩展

只在现有 `ExecutionRecord` 增加：

```text
routingDecision
fallbackReason
```

建议 `fallbackReason` 冻结枚举：

```text
NONE
SELF_IDENTITY_QUERY
SYSTEM_META_QUERY
INCOMPLETE_BUSINESS_REQUEST
PUBLIC_KNOWLEDGE_NO_TRUSTED_INTENT
OUT_OF_BUSINESS_SCOPE
CLASSIFIER_ERROR
RESOURCE_CONTRACT_CONFLICT
REWRITE_CONFLICT
```

不新建事件总线或独立 Trace 系统。

## 9. 最小实现边界

G1 实现阶段建议增加薄层 `RoutingGovernanceService`，由它输出 `RoutingDecisionResult`。V1 不做通用语义路由平台，不训练置信度校准模型，不重建 Intent Tree，也不把所有分类交给第二个自由 LLM。

推荐最小插入点：

```text
StreamChatPipeline
  Pending Resume 之后
  普通 Retrieval 之前
```

同时让 `VectorGlobalSearchChannel` 只响应显式的 `GLOBAL_KB_FALLBACK` 决策，而不是自行把空 score 解释为准入许可。

## 10. 审计后的实施顺序

1. 在 `ExecutionRecord` 增加 `routingDecision/fallbackReason`。
2. 引入 `RoutingGovernanceService` 与确定性的 AUTH_CONTEXT/SYSTEM handler。
3. 区分 `NO_TRUSTED_INTENT` 与 `CLASSIFIER_ERROR`。
4. 将 Global Search 的启用权移交给显式 RoutingDecision。
5. 加入相邻资源 Contract Guard，先覆盖订单/物流和 KB-02 的隐藏次级 Tool。
6. 跑 `Routing Robustness Suite V1`，随后回归 Golden V0、D1、D2、D3。

验收用例位于 `frontend/public/routing-robustness-suite-v1.json`。
