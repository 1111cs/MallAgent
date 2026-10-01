# E1 Evidence Provenance Implementation V1

状态：`E1_PROVENANCE_PRESERVATION_PASSED`

## 本阶段完成内容

E1 已把 KB 证据身份从向量检索贯穿到 Prompt 和 Structured Trace：

```text
RoutingDecision / AllowedRoute
  → Vector metadata
  → EvidenceRecord
  → Document / KB enrichment
  → Rerank score + final rank
  → selectedForPrompt
  → promptOrder / evidenceId
  → Evidence block
  → ExecutionRecord.retrievedEvidence
```

本阶段没有修改数据库 Schema、文档 Parser、Chunker、D1/D2/D3 或 G1 的业务状态机，也没有开放用户可见 Citation。

## 已实现的关键语义

- PGVector 与 Milvus 检索结果保留 `documentId / collectionName / chunkIndex / retrievalScore`。
- SearchChannel 绑定 `routingDecision / routeId / intentId / subQuestion / retrievalChannel`。
- `retrievalScore` 与 `rerankScore` 分离，不再互相覆盖。
- `retrievalRank` 作用域为 route、collection、channel；`finalRank` 作用域为 route 候选集合。
- 整轮 Prompt 合并后统一生成 `promptOrder` 和 `E1/E2/...`，多子问题不会重复生成 `E1`。
- 只给 `selectedForPrompt=true` 的证据分配 Evidence ID。
- Enrichment 批量查询 Document 与 KB；失败时保留 Evidence 并标记 `PARTIAL`。
- Trace 同时记录 Retrieved 和 Selected Evidence，但不保存 Chunk 正文。
- Golden Evaluator 可断言 Evidence 最小数量、Selected 最小数量和预期 Chunk ID。
- Prompt 中的 Evidence ID 仅供内部保持身份；E1 明确禁止最终回答展示引用或内部来源字段。
- 原先“全部 Chunk 分配给全部 Intent”的映射已移除，Evidence 按真实 `intentId` 归属。

## 核心代码位置

### Evidence Domain

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/evidence/EvidenceRecord.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/evidence/ProvenanceStatus.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/evidence/EvidenceProvenanceEnricher.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/evidence/EvidenceIdAssigner.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/evidence/EvidenceFormatter.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/evidence/EvidenceTraceMapper.java`

### Retrieval / Rerank / Prompt

- `framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/RetrievedChunk.java`
- `framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/RerankResult.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MultiChannelRetrievalEngine.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/dto/RetrievalContext.java`
- `bootstrap/src/main/resources/prompt/answer-chat-kb.st`
- `bootstrap/src/main/resources/prompt/answer-chat-mcp-kb-mixed.st`

### Trace / Evaluation

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/trace/ExecutionRecord.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/eval/GoldenCaseExpectation.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/eval/GoldenCaseEvaluator.java`

## 编译结果

执行：

```text
mvn -pl bootstrap -am -DskipTests compile
```

结果：`BUILD SUCCESS`。

## 人工验收建议

重启后端后运行原 5 条 KB Golden Case，并打开对应 Trace 的 `execution-record.extraData.retrievedEvidence`。每条至少确认：

1. 同时存在未入选候选与 `selectedForPrompt=true` 的证据。
2. Selected Evidence 具有连续的 `promptOrder` 和 `E1/E2/...`。
3. 未入选证据的 `evidenceId / promptOrder` 为 null。
4. `retrievalScore / retrievalRank` 与 `rerankScore / finalRank` 分开保存。
5. ROUTED Evidence 有 `routeId / intentId`，Global Fallback 允许二者为空。
6. `documentId / documentTitle / kbId / kbName / chunkId` 可追溯；正文没有写入 Trace。
7. 最终用户答案中没有出现 `[E1]`、文档名、KB 名或内部 ID。

## 人工验收结果

2026-09-18 使用原 5 条 KB Golden Case 完成人工验收：

- 5 条请求均进入预期 KB 检索链路并生成有证据支撑的回答。
- 最终回答未暴露 `[E1]`、Evidence ID、文档名、KB 名或内部存储标识。
- FreeBuds Case 的问题标题曾在人工汇总时复制错位；服务日志确认原始问题、Intent 和 `support-kb` 路由一致，不属于串证据。
- 本轮百炼 `qwen3-rerank` 返回 `400 Arrearage`，系统按设计回退到 Noop Rerank；回答链路保持可用。该项属于外部账号计费状态，不属于 E1 Provenance 实现失败。

E1 正式冻结为：`E1_PROVENANCE_PRESERVATION_PASSED`。
