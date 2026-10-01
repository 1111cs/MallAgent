# E2 Answer Citation & Deterministic Validation

状态：`E2_CITATION_IDENTITY_VALIDATION_PASSED`

## 已完成

```text
KB / Mixed LLM stream
  → CitationBufferedCallback
  → complete answer
  → CitationParser
  → current-request AllowedCitationIds
  → CitationValidator
  → deterministic invalid-token cleanup
  → first user-visible answer delivery
  → ExecutionRecord.citation
```

- KB 与 Mixed 回答在首次交付用户前完成缓冲和校验。
- Tool-only、SYSTEM、AUTH_CONTEXT、确定性澄清继续原交付方式，并记录 `NOT_APPLICABLE`。
- 合法 ID 只能来自本轮 `selectedForPrompt=true` 的 Evidence。
- Parser 保留全部出现顺序和首次出现顺序。
- 严格合法格式只有 `[E1]`、`[E2]` 等。
- `[E01]`、`[e1]`、`[E 1]`、`[E1-E3]`、`[E1, E2]` 等 Citation-like Token 会被识别为非法并清理，不能绕过 Validator。
- 不实现 Citation Repair、自动重绑定、Claim 重写或语义 Grounding 判断。
- Trace 同时保存模型原始状态和系统最终交付状态。
- Golden Evaluator 支持 Citation 适用性、最小合法引用数、最终状态、禁止 ID 和非法引用不可见断言。

## 核心文件

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/citation/CitationParser.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/citation/CitationValidator.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/citation/CitationCleanupService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/citation/CitationBufferedCallback.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/pipeline/StreamChatPipeline.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/trace/ExecutionRecord.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/eval/GoldenCaseEvaluator.java`
- `bootstrap/src/main/resources/prompt/answer-chat-kb.st`
- `bootstrap/src/main/resources/prompt/answer-chat-mcp-kb-mixed.st`

验收 Case：`frontend/public/golden-e2-citation-v1.json`。

## 编译

`mvn -pl bootstrap -am -DskipTests compile`：`BUILD SUCCESS`。

## 人工验收

重启后端后运行 `golden-e2-citation-v1.json`：

- 5 条 KB 回答至少出现一个 `[E<number>]`，Trace 为 `VALID`。
- Tool-only 回答不要求 Citation，Trace 为 `NOT_APPLICABLE`。
- Mixed 回答至少有一个合法 Citation；本阶段只检查 ID 合法性。
- 用户首次看到的就是完整校验后答案，不会先流出非法 Citation。
- Trace 中 `validEvidenceIds` 能反查 E1 的 Selected Evidence。

已冻结为：`E2_CITATION_IDENTITY_VALIDATION_PASSED`。
## 首轮人工验收修正

首轮 KB 人工测试返回了正确内容但没有 Citation。Validator 将这种结果识别为 `MISSING_REQUIRED_CITATION`，按 V2 契约保留原答案且不伪造引用。现已把 KB / Mixed Prompt 从建议性措辞收紧为强制输出契约，并增加 Citation 校验结果的 INFO 日志。若收紧后模型仍不输出 Citation，应单独决定采用 fail-closed 或受约束重生成，不能静默追加未经语义验证的 Evidence ID。
## 人工验收结果

- KB-only：回答输出本轮合法 Evidence ID，Citation 身份校验通过。
- Tool-only：`getWarrantyStatus` 回答未输出 KB Citation，符合 `NOT_APPLICABLE` 边界。
- Mixed：KB 通用政策与 Tool 实时保修状态同时返回，回答只使用本轮合法 Citation，E2 身份合法性通过。
- Mixed 回答将 `[E2] [E3]` 集中放在末尾。Citation 位置、Claim 对应关系与覆盖率不属于 E2，留给 E3 Grounding Evaluation。