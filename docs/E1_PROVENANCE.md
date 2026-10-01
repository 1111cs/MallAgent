# E1：Provenance Preservation 设计 V2

状态：`E1_IMPLEMENTATION_DESIGN_V2`

## 1. 目标

E1 只解决一个核心问题：

> **让每一条进入最终 Prompt 的 KB Evidence，都能够稳定追溯到本轮 RoutingDecision、检索来源、Collection、Document、Chunk，以及完整的召回与重排过程。**

当前链路：

```text
Vector Store
  ↓
RetrievedChunk(id, text, score)
  ↓
Rerank 覆盖 score
  ↓
Formatter 只保留 text
  ↓
Prompt
```

E1 改造后：

```text
RoutingDecision / AllowedRoute
  ↓
SearchContext
  ↓
Vector Retriever
  ↓
原始检索结果 + Vector Metadata
  ↓
SearchChannel 绑定 Route / Channel 来源
  ↓
EvidenceRecord
  ↓
Provenance Enrichment
  ↓
Rerank
  ↓
Final Evidence Selection
  ↓
promptOrder + evidenceId
  ↓
Evidence Formatter
  ↓
Prompt
  ↓
Trace
```

E1 不负责用户可见 Citation。

------

# 2. 阶段边界

E1 做：

```text
Vector metadata 保留
Route / Channel provenance 绑定
Document / KB provenance enrichment
Retrieval Score 与 Rerank Score 分离
Retrieval Rank / Final Rank / Prompt Order 分离
最终 Prompt Evidence 身份稳定化
Retrieved 与 Selected Evidence 可区分
Trace 可直接观察 Evidence 生命周期
```

E1 不做：

```text
最终答案 [E1]/[E2] Citation
Citation Validity
Citation Coverage
Unsupported Claim
Claim-to-Evidence Graph
Parser V2
章节/Page 强制追踪
数据库迁表
Citation Platform
通用 Source-of-Truth Engine
```

其中：

```text
section = nullable
```

属于正式设计。

------

# 3. 模块依赖原则

当前依赖方向：

```text
bootstrap
   ↓
infra-ai
   ↓
framework
```

因此完整业务对象：

```text
EvidenceRecord
```

应位于：

```text
bootstrap / RAG Domain
```

不能让：

```text
infra-ai
```

反向依赖 bootstrap 的：

```text
EvidenceRecord
RoutingDecision
AllowedRoute
KnowledgeBase
Document
```

等业务概念。

------

# 4. Retriever 与 Rerank 的职责边界

底层 Vector Retriever 只负责：

```text
query
collection
topK
vector search
vector metadata recovery
```

它返回的最小信息：

```text
chunkId
content
retrievalScore

documentId
collectionName
chunkIndex
```

它不知道：

```text
RoutingDecision
AllowedRoute
routeId
intentId
subQuestion
retrievalChannel
```

这些信息必须在更高层绑定。

------

# 5. RerankService 保持纯排序能力

`infra-ai` 中的 RerankService 不接收 `EvidenceRecord`。

推荐接口语义：

```text
Input:
candidate texts

Output:
candidateIndex
rerankScore
```

例如：

```java
RerankResult {
    int candidateIndex;
    double rerankScore;
}
```

调用链：

```text
bootstrap EvidenceRecord[]
        ↓
提取 content
        ↓
infra-ai RerankService
        ↓
candidateIndex + rerankScore
        ↓
bootstrap 按 index 写回 EvidenceRecord
```

Rerank Provider 不感知：

```text
KB
Document
Route
Collection
Evidence ID
```

因此未来更换：

```text
百炼
本地 reranker
Noop reranker
```

不影响 Evidence Domain。

------

# 6. EvidenceRecord

E1 冻结完整业务证据对象：

```java
EvidenceRecord {
    String evidenceId;
    Integer promptOrder;

    RoutingDecision routingDecision;
    String routeId;
    String subQuestion;
    String intentId;
    String retrievalChannel;

    String kbId;
    String kbName;
    String collectionName;

    String documentId;
    String documentTitle;
    String sourceType;
    String sourceLocation;

    String chunkId;
    Integer chunkIndex;
    String section;

    Double retrievalScore;
    Integer retrievalRank;

    Double rerankScore;
    Integer finalRank;

    boolean selectedForPrompt;
    ProvenanceStatus provenanceStatus;

    String content;
    String contentHash;
}
```

其中：

```text
section
kbName
documentTitle
sourceType
sourceLocation
rerankScore
finalRank
evidenceId
promptOrder
```

在不同生命周期阶段允许为空。

------

# 7. ProvenanceStatus

E1 只定义两级：

```java
ProvenanceStatus {
    FULL,
    PARTIAL
}
```

例如：

```text
chunkId
documentId
collectionName
documentTitle
kbName
均成功恢复
→ FULL
```

如果：

```text
chunkId
documentId
collectionName
存在

但 document lookup 失败
→ PARTIAL
```

重要原则：

> **Provenance enrichment 失败不得导致有效 Evidence 被丢弃。**

------

# 8. Routing Provenance

Evidence 的最高层来源不是统一的 AllowedRoute，而是：

```text
RoutingDecision
```

因为：

```text
ROUTED
```

和：

```text
GLOBAL_KB_FALLBACK
```

结构不同。

------

# 9. ROUTED Evidence

普通定向检索：

```text
routingDecision = ROUTED
routeId = R1
intentId = PRODUCT_RECOMMEND_KB
subQuestion = ...
retrievalChannel = INTENT_DIRECTED
collectionName = product-kb
```

必须满足：

> Evidence 能追溯到最终 AllowedRoute。

链路：

```text
AllowedRoute
  ↓
SearchContext
  ↓
SearchChannel
  ↓
EvidenceRecord
```

------

# 10. GLOBAL_KB_FALLBACK Evidence

Global Fallback 当前：

```text
allowedRoutes = []
```

因此 Evidence 形式为：

```text
routingDecision = GLOBAL_KB_FALLBACK
routeId = null
intentId = null
subQuestion = 原问题
retrievalChannel = GLOBAL_VECTOR
collectionName = shopping-policy-kb
```

其 provenance 仍然完整，只是不绑定 AllowedRoute。

因此 E1 验收标准冻结为：

> **每条 Evidence 必须追溯至本轮 RoutingDecision；当 `routingDecision=ROUTED` 时，还必须追溯至具体 AllowedRoute。**

------

# 11. Route ID

`routeId` 只需要请求内稳定。

例如：

```text
R1
R2
R3
```

不进入数据库。

用途：

```text
AllowedRoute
↔ Evidence
↔ Trace
```

方便多子问题、多 Intent 情况下建立稳定对应关系。

------

# 12. Route provenance 的绑定位置

PG / Milvus Retriever 不负责绑定 Route。

正确边界：

```text
AllowedRoute / SearchContext
        +
Retriever Result
        ↓
SearchChannel / Retrieval orchestration
        ↓
EvidenceRecord
```

职责冻结：

```text
Vector Retriever
→ 恢复存储层 provenance

SearchChannel
→ 绑定运行时检索 provenance

RAG Domain
→ 管理完整 Evidence 生命周期
```

------

# 13. PGVector Retriever 改造

当前：

```sql
SELECT id, content, score
```

改为同时读取 metadata。

至少恢复：

```text
chunkId
content
retrievalScore

metadata.doc_id
metadata.collection_name
metadata.chunk_index
```

PG Retriever 不负责：

```text
intentId
routeId
subQuestion
retrievalChannel
```

------

# 14. Milvus Retriever 改造

Milvus 已经请求：

```text
id
content
metadata
```

当前问题主要在映射阶段。

必须恢复：

```text
chunkId
content
retrievalScore

documentId
collectionName
chunkIndex
```

而不是只构造：

```text
RetrievedChunk(id, text, score)
```

------

# 15. Retriever 传输对象

底层 Retriever 可以继续使用 framework 中的轻量对象，例如：

```java
RetrievedChunk {
    String chunkId;
    String text;
    double score;

    String documentId;
    String collectionName;
    Integer chunkIndex;
}
```

这个对象只是：

> Vector Retrieval Transport Object。

不能演化成完整的 Evidence Domain Model。

------

# 16. SearchChannel 形成 EvidenceRecord

SearchChannel 获得：

```text
SearchContext
+
RetrievedChunk
```

以后构造：

```text
EvidenceRecord
```

并补充：

```text
routingDecision
routeId
subQuestion
intentId
retrievalChannel
```

这样不会让：

```text
PG Retriever
Milvus Retriever
```

知道 G1 Routing 模型。

------

# 17. Rank 作用域

E1 正式冻结三种顺序。

## 17.1 retrievalRank

定义：

> **当前 `route + collection + retrievalChannel` 范围内的原始向量召回排名。**

例如：

```text
R1
product-kb
INTENT_DIRECTED

retrievalRank:
1
2
3
```

另一个 Route 可以再次存在：

```text
retrievalRank = 1
```

这是合法的。

------

## 17.2 finalRank

定义：

> **当前 Route 的候选集合经过 Rerank 后的最终排名。**

例如：

```text
Route R1

retrievalRank=1 → finalRank=3
retrievalRank=2 → finalRank=1
```

`finalRank` 不表示整轮 Prompt 的全局顺序。

------

## 17.3 promptOrder

定义：

> **本轮最终 Prompt 中所有 Evidence 的全局顺序。**

例如：

```text
promptOrder = 1
promptOrder = 2
promptOrder = 3
```

这个顺序跨 Route 唯一。

------

# 18. Evidence ID

Evidence ID 按：

```text
promptOrder
```

生成。

例如：

```text
promptOrder 1 → E1
promptOrder 2 → E2
promptOrder 3 → E3
```

因此：

```text
Evidence ID
```

表示的是：

> 本轮实际进入 Prompt 的 Evidence 身份。

不是：

```text
数据库 ID
Retrieval Rank
Final Rank
```

------

# 19. 未进入 Prompt 的 Evidence

对于：

```text
selectedForPrompt = false
```

必须：

```text
evidenceId = null
promptOrder = null
```

例如：

```text
retrievalRank = 2
finalRank = 7
selectedForPrompt = false

evidenceId = null
promptOrder = null
```

而进入 Prompt 的：

```text
selectedForPrompt = true
promptOrder = 1
evidenceId = E1
```

这样对象生命周期语义保持清晰。

------

# 20. Provenance Enrichment

新增薄层：

```text
EvidenceProvenanceEnricher
```

职责是根据：

```text
documentId
collectionName / kbId
```

批量补充：

```text
documentTitle
kbId
kbName
sourceType
sourceLocation
```

------

# 21. Enrichment 必须批量执行

禁止：

```text
for each Evidence
→ query document
```

必须：

```text
collect distinct documentIds
↓
batch query documents
↓
request-level cache
```

KB 同理：

```text
collect distinct kbIds / collections
↓
batch resolve
```

避免 N+1。

------

# 22. Enrichment 降级规则

正常：

```text
Document lookup success
KB lookup success
→ provenanceStatus = FULL
```

异常：

```text
Document 已删除
Document lookup 暂时失败
KB 名称无法恢复
```

则：

```text
保留：

chunkId
documentId
collectionName
content
retrievalScore

provenanceStatus = PARTIAL
```

不得：

```text
删除 Evidence
阻止整个 RAG 回答
```

E1 的 provenance 是增强能力，不应该变成新的单点故障。

------

# 23. Retrieval Score

Retriever 输出时立即冻结：

```text
retrievalScore
retrievalRank
```

后续任何阶段不得覆盖。

------

# 24. Rerank Score

RerankService 只返回：

```text
candidateIndex
rerankScore
```

bootstrap 根据 index 写回：

```text
EvidenceRecord.rerankScore
```

随后根据当前 Route 候选集合生成：

```text
finalRank
```

因此同一 Evidence 最终可以拥有：

```text
retrievalScore
retrievalRank

rerankScore
finalRank
```

四个独立字段。

------

# 25. Rerank Fallback

如果 Rerank Provider 不可用并回退：

```text
noop
```

则：

```text
rerankScore = null
```

并保持：

```text
finalRank
```

按照 retrieval 顺序生成。

不得：

```text
把 retrievalScore 复制成 rerankScore
```

避免伪造重排结果。

------

# 26. Prompt Evidence Selection

E1 必须明确区分：

```text
Retrieved
```

和：

```text
Selected For Prompt
```

因此：

```text
selectedForPrompt
```

为核心字段。

最终 Top-K 选择完成以后：

```text
selectedForPrompt = true
```

然后才分配：

```text
promptOrder
evidenceId
```

------

# 27. 多 Route Prompt 合并

不同 Route 的 Evidence 可以分别完成：

```text
retrieval
rerank
finalRank
```

最后在 Prompt Composer 中统一确定：

```text
promptOrder
```

例如：

```text
Route R1 finalRank 1
→ promptOrder 1
→ E1

Route R2 finalRank 1
→ promptOrder 2
→ E2

Route R1 finalRank 2
→ promptOrder 3
→ E3
```

因此不会把多个：

```text
retrievalRank = 1
```

误解成同一全局排名。

------

# 28. RetrievalContext 改造

当前：

```text
intentChunks
kbContext
```

不足以表达 provenance。

E1 增加：

```text
evidenceRecords
```

或者按 Route：

```text
routeEvidence
```

其结构只要保证：

```text
EvidenceRecord
```

能够一直贯穿：

```text
Search
→ Rerank
→ Prompt Selection
→ Formatter
→ Trace
```

即可。

------

# 29. Evidence Formatter

原来：

```java
map(RetrievedChunk::getText)
    .joining("\n")
```

改为稳定 Evidence Block。

例如：

```xml
<evidence id="E1"
          document="Huawei Mall 配送与签收规则"
          kb="Shopping Policy">
……
</evidence>
```

有可靠 section 时：

```xml
section="签收规则"
```

没有时：

```text
直接省略 section
```

不得生成：

```text
Unknown
N/A
未识别章节
```

等伪 provenance。

------

# 30. Prompt 中暴露哪些字段

LLM 生成阶段可见：

```text
evidenceId
documentTitle
section（若存在）
kbName
content
```

通常不需要给模型看到：

```text
chunkId
documentId
collectionName
sourceLocation
retrievalScore
rerankScore
routeId
```

这些属于 Trace / Evaluation provenance。

------

# 31. E1 明确禁止用户可见 Citation

虽然 Prompt 中已经存在：

```text
<evidence id="E1">
```

E1 阶段必须明确告诉最终模型：

```text
Evidence ID 和来源属性仅用于内部保持证据身份。

当前阶段最终回答不得输出：
[E1]
[E2]
Evidence ID
document 名称
KB 名称
chunkId
collectionName
或其他内部 provenance 标识。
```

这用于严格保持：

```text
E1 = Provenance Preservation
E2 = User-visible Citation
```

阶段边界。

------

# 32. 用户可见信息与内部 provenance 分离

即使未来进入 E2，也不能默认暴露：

```text
数据库 ID
本地文件路径
Collection 内部名称
Chunk ID
```

特别是：

```text
sourceLocation
```

如果包含内部路径，只能留在 Trace。

------

# 33. Trace

继续复用：

```text
ExecutionRecord
```

新增：

```text
retrievedEvidence[]
```

Trace 不保存完整 Chunk Content。

建议记录：

```text
evidenceId
promptOrder

routingDecision
routeId
intentId
retrievalChannel

kbId
kbName
collectionName

documentId
documentTitle

chunkId
chunkIndex

retrievalScore
retrievalRank

rerankScore
finalRank

selectedForPrompt
provenanceStatus

contentHash
```

------

# 34. 为什么 Trace 保留未进入 Prompt 的候选

如果只保存最终：

```text
E1 / E2 / E3
```

就无法区分：

```text
Retriever 没找到
```

和：

```text
Retriever 找到了
但 Reranker / Top-K Selection 淘汰了
```

因此：

```text
retrievedEvidence[]
```

必须覆盖进入 Rerank 的候选集合。

------

# 35. Trace 关系

E1 完成后应能观察：

```text
RoutingDecision
      ↓
AllowedRoute / Global Fallback
      ↓
Collection
      ↓
Retrieved Evidence
      ↓
Rerank
      ↓
Selected Evidence
      ↓
Prompt Order
```

这将成为 E3 Grounding Evaluation 的基础。

------

# 36. Knowledge Authority

E1 不建设 Authority Policy Engine。

继续冻结轻量规则：

| 事实类型           | 权威来源                  |
| ------------------ | ------------------------- |
| 商品固定规格       | Product KB / 冻结商品目录 |
| 购物规则           | KB                        |
| 售后规则           | KB                        |
| 使用指南与故障处理 | KB                        |
| 当前订单           | Tool                      |
| 当前物流           | Tool                      |
| 当前保修状态       | Tool                      |
| 当前库存           | Tool                      |
| 实时价格           | Tool                      |
| 用户优惠券状态     | Tool                      |

需要时可由：

```text
RoutingDecision
AllowedRoute.kind
Resource Type
```

确定性推导。

E1 不让模型判断 authority。

------

# 37. 最小新增组件

建议新增：

```text
EvidenceRecord
ProvenanceStatus

EvidenceProvenanceEnricher
EvidenceIdAssigner
EvidenceFormatter
EvidenceTraceMapper
```

可增加：

```text
RerankResult
```

作为 infra-ai 与 bootstrap 之间的简单排序返回对象。

------

# 38. 需要改造的现有组件

重点：

```text
PG Vector Retriever
Milvus Retriever
SearchChannel / Parallel Retrieval
Rerank Adapter / Service
RetrievalContext
DefaultContextFormatter / Prompt Composer
ExecutionRecord
Golden Evaluator
```

不修改：

```text
数据库 Schema
Parser
Chunker 主流程
G1 Routing 模型
D1/D2/D3 执行模型
```

------

# 39. 实施顺序

第一步先修改 PG / Milvus Retriever，使向量 metadata 不再在 Retriever 边界丢失。

必须恢复：

```text
chunkId
documentId
collectionName
chunkIndex
retrievalScore
```

然后在 SearchChannel / Retrieval Orchestration 层绑定：

```text
routingDecision
routeId
subQuestion
intentId
retrievalChannel
```

形成完整 `EvidenceRecord`。

随后实现：

```text
EvidenceProvenanceEnricher
```

批量补：

```text
documentTitle
kbName
source
```

再修改 Rerank：

```text
candidateIndex + rerankScore
```

由 bootstrap 写回：

```text
rerankScore
finalRank
```

之后进行：

```text
Final Prompt Selection
↓
selectedForPrompt
↓
promptOrder
↓
evidenceId
```

最后改造：

```text
EvidenceFormatter
ExecutionRecord
Evaluator
```

------

# 40. 单元测试

## Retriever Test

验证：

```text
PG metadata 不丢
Milvus metadata 不丢

documentId
collectionName
chunkIndex
retrievalScore
```

正确。

------

## SearchChannel Test

验证：

```text
ROUTED
→ routeId / intentId / channel 正确绑定
```

以及：

```text
GLOBAL_KB_FALLBACK
→ routeId = null
→ intentId = null
→ routingDecision 正确
```

------

## Enricher Test

验证：

```text
正常文档
→ FULL
```

以及：

```text
Document lookup fail
→ PARTIAL
→ Evidence 不被删除
```

------

## Rerank Test

验证：

```text
retrievalScore 不变
retrievalRank 不变

rerankScore 正确
finalRank 正确
```

Rerank fallback 时：

```text
rerankScore = null
```

------

## Formatter Test

验证：

```text
只有 selectedForPrompt Evidence 被格式化

promptOrder 唯一

E1/E2/... 与 promptOrder 一致

无 section 时不伪造

Evidence Blocks 有稳定边界
```

------

# 41. Integration Test

使用现有 KB Golden Cases。

验证普通定向检索：

```text
RoutingDecision
→ AllowedRoute
→ SearchChannel
→ Collection
→ Document
→ Chunk
→ Prompt Evidence
→ Trace
```

一致。

同时增加 Global Fallback Case：

```text
GLOBAL_KB_FALLBACK
→ Public KB
→ Evidence
→ Prompt
```

验证：

```text
无 AllowedRoute
但仍有完整 RoutingDecision provenance
```

------

# 42. E1 验收标准

E1 PASS 冻结为：

```text
数据库迁移次数 = 0

PG / Milvus 均保留 vector metadata

100% Retrieved Evidence 可追溯到：
RoutingDecision
Collection
DocumentId
ChunkId

ROUTED Evidence：
100% 可追溯到具体 AllowedRoute

GLOBAL_KB_FALLBACK Evidence：
允许 routeId / intentId 为 null
但必须追溯到 RoutingDecision 与实际 Collection

不存在多 Intent 下：
全部 Chunk 分配给全部 Intent

retrievalScore 不再被 rerank 覆盖

retrievalRank 作用域明确为：
route + collection + channel

finalRank 作用域明确为：
当前 Route 候选集合

promptOrder 为：
本轮 Prompt 全局顺序

Evidence ID 仅分配给 selectedForPrompt Evidence

未选 Evidence：
evidenceId = null
promptOrder = null

Rerank fallback：
rerankScore = null

Document enrichment 失败：
Evidence 保留
provenanceStatus = PARTIAL

Trace 能区分：
Retrieved
vs
Selected For Prompt

Trace 不保存完整正文

section 缺失保持 null

E1 最终回答不输出用户可见 Citation

KB Golden Cases 无回退

G1 / D1 / D2 / D3 无回退
```

------

# 43. E1 完成后的架构

完成后形成：

```text
RoutingDecision
   ↓
AllowedRoute / Global Fallback
   ↓
SearchChannel
   ↓
Vector Retrieval
   ↓
EvidenceRecord
   ↓
Document / KB Enrichment
   ↓
Rerank
   ↓
Final Evidence Selection
   ↓
Prompt Order / Evidence ID
   ↓
Evidence Formatter
   ↓
LLM Context
   ↓
Structured Trace
```

系统不再只是：

```text
搜出几段文本
→ 拼起来
→ 给 LLM
```

而是：

```text
每一段知识
都有来源身份
都有路由来源
都有召回位置
都有重排位置
都知道最终有没有进入 Prompt
```

------

# 44. E1 与 E2 的明确边界

E1 完成时：

```text
LLM 已经看到内部 Evidence ID
但禁止向用户输出 Citation
```

E2 才开始：

```text
允许输出 [E1]/[E2]
Citation Parser
Citation Validation
非法 Citation 拦截
用户可见来源展示
```

因此：

> **E1 建立可信证据身份链，E2 才把该身份转化成用户可验证引用。**

------

# 45. E1 核心定位

E1 的正式定位：

> **Provenance Preservation across Retrieval, Rerank and Prompt Construction**

中文可以概括为：

> **贯穿检索、重排与 Prompt 构造的证据来源保持。**

它解决的不是：

> 模型会不会引用。

而是：

> **模型所看到的每一条知识证据，系统是否知道它从哪里来、为什么被选中、经历了什么排序，并能在 Trace 中重新证明这一过程。**