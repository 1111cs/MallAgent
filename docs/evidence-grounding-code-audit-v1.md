# E0 Evidence Grounding Code Audit V1

状态：`E0_EVIDENCE_GROUNDING_AUDIT_COMPLETE`

## 1. 审计范围

本轮只审计现有链路，不修改运行逻辑或数据库：

```text
Document Import
  -> Chunk
  -> Vector Store
  -> Retriever
  -> Rerank
  -> RetrievalContext
  -> Context Formatter
  -> Final Answer
  -> Trace / Evaluation
```

## 2. 结论

Ragent 已经具备最小 provenance 的底层身份信息，但这些信息没有进入检索领域对象，随后又在 Rerank、Context Formatter 和 Prompt 阶段继续丢失。当前最终模型实际看到的是若干没有来源标识的纯文本片段，因此无法生成可验证引用；Trace 也无法直接判断检索、证据选择和答案使用分别是否正确。

第一版 E1 不需要修改数据库结构。现有 `chunkId`、向量 metadata 中的 `doc_id/collection_name/chunk_index`、文档表中的 `doc_name/source_*`、知识库表中的 `id/name/collection_name` 足以建立最小 EvidenceRecord。章节 `section` 当前没有可靠结构化来源，E1 应允许为空。只有在后续要求稳定章节级 provenance 时，才考虑为 Chunk 增加 metadata JSON 或专门的 section 字段。

## 3. 当前真实链路

### 3.1 文档与 Chunk 持久化

`KnowledgeDocumentDO` 已保存：

- `id`
- `kbId`
- `docName`
- `sourceType`
- `sourceLocation`
- `fileUrl`
- `fileType`

`KnowledgeChunkDO` 已保存：

- `id`，可作为稳定 chunkId
- `kbId`
- `docId`
- `chunkIndex`
- `content`
- `contentHash`

因此关系数据库已经能够表达：

```text
KnowledgeBase -> Document -> Chunk
```

但 `KnowledgeChunkDO` 没有 metadata、heading 或 section 字段。

### 3.2 解析与分块

普通 chunk 模式固定通过 `TikaDocumentParser.extractText()` 得到纯文本，再交给 ChunkingStrategy。即使源文件是 Markdown，这条入口也没有根据文件类型选择 `MarkdownDocumentParser`。

`StructureAwareTextChunker` 能识别 Markdown heading 作为分块边界，但 `VectorChunk` 最终只稳定写入 `chunkId/index/content`，没有将当前 heading 路径保存为 metadata。标题可能留在某个 Chunk 的正文中，但不能作为可靠的 section provenance。

Pipeline 模式在类型设计上拥有 `StructuredDocument.sections/tables/metadata` 和 `VectorChunk.metadata`，Enricher 也能够附加 metadata；但当前 `ParserNode` 仍强制选择 Tika，`ParseResult` 通常只有 text，且最终写关系库时 `persistChunksAndVectorsAtomically()` 只复制 chunkId、index、content。Pipeline 的丰富 metadata 没有成为稳定的关系库事实。

### 3.3 向量存储

PGVector 与 Milvus 写入时都会保存：

```text
collection_name
doc_id
chunk_index
```

同时也允许合并 `VectorChunk.metadata`。因此向量层已有最小 provenance 容器。

### 3.4 Retriever：第一次确定的信息损失

PG Retriever 查询：

```sql
SELECT id, content, score
```

没有选择 metadata。

Milvus Retriever 虽然声明了：

```text
outputFields = id, content, metadata
```

但构造 `RetrievedChunk` 时仍然只复制：

```text
id
text
score
```

`RetrievedChunk` 本身也只有上述三个字段。因此 `doc_id`、`collection_name` 和 `chunk_index` 在 Retriever 边界被丢弃。

多 Collection 或多 Channel 合并后，每条 Chunk 的来源 Collection 和 Channel 同样无法恢复。`RetrievalEngine` 已在注释中承认多通道结果无法精确对应 Intent，因此当前会把全部 Chunk 分配给每一个 KB Intent。

### 3.5 Rerank：第二次信息损失

Rerank 只向模型提交 Chunk 文本。百炼返回相关性后，会创建新的：

```java
new RetrievedChunk(src.getId(), src.getText(), rerankScore)
```

这会让同一个 `score` 从向量召回分数变成 rerank 分数。当前无法同时回答：

- 该 Chunk 的原始 retrieval score 是多少；
- rerank score 是多少；
- rerank 前后 Rank 如何变化。

### 3.6 Context Formatter：第三次信息损失

`DefaultContextFormatter` 对 Chunk 的处理最终都是：

```java
map(RetrievedChunk::getText)
joining("\n")
```

所以进入 `kbContext` 的只剩正文。Chunk 之间没有稳定分隔符，也没有 evidenceId、文档名称、章节、KB、rank 或 score。

`RetrievalContext` 和 `PromptContext` 虽然仍保留 `intentChunks`，但最终生成所使用的是已经格式化为字符串的 `kbContext`。LLM 无法从 `intentChunks` 直接读取 provenance。

### 3.7 Final Answer

当前 KB Prompt 强调只能根据 `<documents>` 回答，这是内容边界约束；但 `<documents>` 中没有任何来源身份。

Prompt 还明确禁止输出 document index、分块、检索等内部结构，并只允许用户追问来源时展示资料中已有的名称或链接。由于资料名称和链接通常没有进入 Prompt，这项能力事实上无法稳定实现。

因此当前系统能约束“不要使用外部事实”，但不能证明“答案中的事实来自哪条 Evidence”。

### 3.8 Trace 与 Evaluation

`ExecutionRecord` 当前记录：

- routing decision / route
- parameter resolution
- entity resolution
- tool plan / binding / execution
- task status

没有 retrievedEvidence、selectedEvidence、rank、retrievalScore 或 rerankScore。现有 Golden Evaluator 因而不能直接断言 Evidence Hit、Rank、Citation Validity 或 Citation Coverage。

## 4. Provenance 字段现状

| 字段 | 入库/底层是否存在 | 检索后是否存在 | Prompt 是否可见 | 结论 |
|---|---:|---:|---:|---|
| kbId | 文档/Chunk 表存在 | 否 | 否 | 可通过 collection 映射恢复 |
| collectionName | 向量 metadata 存在 | 否 | 否 | Retriever 丢失 |
| documentId | Chunk 表和向量 metadata 存在 | 否 | 否 | Retriever 丢失 |
| documentTitle | 文档表 `docName` 存在 | 否 | 否 | 需要 enrichment lookup |
| sourceType/location | 文档表存在 | 否 | 否 | 需要 enrichment lookup |
| chunkId | 向量主键存在 | 是，`RetrievedChunk.id` | 否 | Formatter 丢失 |
| chunkIndex | Chunk 表和向量 metadata 存在 | 否 | 否 | Retriever 丢失 |
| section/heading | 无稳定字段 | 否 | 否 | E1 允许为空 |
| retrievalScore | Retriever 中短暂存在 | 会被 rerank 覆盖 | 否 | 需要独立字段 |
| rerankScore | Rerank 后存在于 `score` | 是 | 否 | 语义不明确 |
| retrieval/rerank rank | 未显式记录 | 仅隐含于 List 顺序 | 否 | 需要显式生成 |

## 5. 信息损失点

```text
Loss 1: Parser / Chunker
结构化章节没有形成稳定 Chunk metadata

Loss 2: Vector Store -> Retriever
metadata 已保存但没有进入 RetrievedChunk

Loss 3: Retriever -> Rerank
单一 score 被覆盖，原始分数和原始排名丢失

Loss 4: RetrievedChunk -> Context Formatter
只保留 text，所有来源信息被压扁

Loss 5: Prompt -> Answer
没有 Evidence ID，模型无法生成可验证 citation

Loss 6: Retrieval -> Trace
没有证据记录，评测只能通过最终回答反推
```

其中最小且收益最高的改造点是 Loss 2、3、4、6。第一版无需先重做 Parser 或 Chunker。

## 6. Source of Truth 审计

Mixed Prompt 已经写明语义优先级：

- 实时数值、当前状态、业务记录优先 Tool；
- 制度规则、流程定义、政策条款优先 KB；
- 冲突无法判断时明确说明差异。

这说明项目已有 Knowledge Authority 的文字契约，但它目前只是一条 Prompt 规则，没有结构化 authority 标记，也没有 deterministic validation。G1 的路由契约和 Huawei Mall KB 内容边界降低了风险，却不能阻止一条错误写入 KB 的实时事实被模型引用。

建议先冻结轻量权威来源表：

| 事实类型 | 权威来源 |
|---|---|
| 商品固定规格 | Product KB / 冻结商品目录 |
| 购物、售后通用规则 | KB |
| 设备支持与故障排查 | KB |
| 当前订单与物流 | Tool |
| 当前设备保修状态 | Tool |
| 实时库存、价格、维修进度 | Tool |
| 用户优惠券及可用性 | Tool |

E1 只需要把该类型作为 EvidenceRecord 的 `authorityType` 或由资源类型推导，不建设通用 Policy Engine。

## 7. 数据库决策

### E1 最小版本：不迁表

可以使用现有数据构建：

```text
evidenceId       = E1/E2...（请求内编号）
chunkId          = RetrievedChunk.id
documentId       = vector metadata.doc_id
documentTitle    = t_knowledge_document.doc_name lookup
kbId/kbName      = document/base lookup
collectionName   = vector metadata.collection_name
chunkIndex       = vector metadata.chunk_index
section          = null
retrievalScore   = 向量召回分数
rerankScore      = Rerank 分数
retrievalRank    = 召回顺序
finalRank        = 重排后顺序
content          = Chunk text
```

文档标题与 KB 名称可以按 docId 批量查询并请求级缓存。无需为了 E1 复制到每条向量记录。

### 后续章节级 provenance：再评估迁表

如果 E2/E3 明确要求稳定 section、heading path、page 或 parser metadata，建议给 `t_knowledge_chunk` 增加 `metadata jsonb`，并让向量 metadata 从关系库的 Chunk metadata 重建。此项不进入 E1 MVP。

## 8. E1 最小改造建议

### 8.1 新建 EvidenceRecord

建议放在 RAG 领域层，而不是绑定 PG/Milvus：

```text
EvidenceRecord
  evidenceId
  kbId / kbName / collectionName
  documentId / documentTitle
  sourceType / sourceLocation
  chunkId / chunkIndex
  section (nullable)
  retrievalScore / rerankScore
  retrievalRank / finalRank
  content
  authorityType
```

### 8.2 让 Retriever 保留 metadata

- PG 查询增加 `metadata`。
- Milvus 映射已经取得的 metadata。
- 每条结果保留 collection/channel。
- 不再用只有 `id/text/score` 的对象作为 Evidence 主载体。

### 8.3 Rerank 保留原对象身份

Rerank 只更新 `rerankScore/finalRank`，保留 retrievalScore、retrievalRank 和 provenance。

### 8.4 Formatter 输出稳定 Evidence 块

建议格式：

```text
<evidence id="E1" document="配送规则" section="签收">
...
</evidence>
```

Prompt 明确要求只能引用本轮 Evidence Set 中存在的 `[E1]`、`[E2]`。

### 8.5 Trace 复用现有 ExecutionRecord

只增加：

```text
retrievedEvidence[]
  evidenceId
  kb/document/chunk
  retrievalRank/finalRank
  retrievalScore/rerankScore
```

Trace 默认不必重复存完整 content，可保存 contentHash 或短摘要，避免 extraData 膨胀。

## 9. 后续阶段边界

```text
E1 Provenance Preservation
  让 EvidenceRecord 从 Retriever 贯穿 Rerank、RetrievalContext、Prompt 与 Trace

E2 Answer Citation
  允许 [E1]/[E2] 引用，并对不存在的引用做确定性校验

E3 Grounding Evaluation
  Evidence Hit、Rank、Citation Validity、Citation Coverage、Unsupported Claim
```

E1 不做 Claim-to-Evidence Graph、不重做解析器、不要求 section 必填、不建设 Citation Platform，也不建立通用 Source-of-Truth 规则引擎。

## 10. E0 冻结结论

```text
E0_EVIDENCE_GROUNDING_AUDIT_COMPLETE

Primary loss point:
Vector Retriever -> RetrievedChunk

Secondary loss points:
Rerank score overwrite
RetrievedChunk -> DefaultContextFormatter
Retrieval -> ExecutionRecord

Database migration for E1 MVP:
NOT REQUIRED

Section provenance:
NOT RELIABLY AVAILABLE; NULLABLE IN E1
```
