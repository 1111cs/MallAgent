# MallAgent Architecture

## 1. Responsibility boundary

MallAgent separates probabilistic decisions from deterministic business governance.

| Responsibility | Owner |
| --- | --- |
| Understand user language and propose intent candidates | LLM intent classifier |
| Filter confidence and cap candidates | Java intent resolver |
| Decide whether a route may use KB, Tool or System | Routing policy and resource contract guard |
| Obtain authenticated fixture principal | Trusted runtime context |
| Validate tool parameters and ownership | Java application and MCP Server |
| Ask for missing parameters and resume a pending task | Missing-parameter closed loop |
| Select among trusted entity candidates | Entity-resolution closed loop |
| Bind upstream tool output to downstream input | Dependency coordinator |
| Preserve retrieval and tool provenance | Trace and evidence components |
| Validate citations and Golden Case assertions | Deterministic evaluator |

## 2. Request path

```text
HTTP/SSE request
→ authenticated user context
→ conversation memory and query rewrite
→ intent classification
→ pre-routing policy and resource contract
→ KB retrieval and/or MCP execution
→ clarification, entity selection or dependency continuation
→ evidence-aware prompt assembly
→ model answer
→ citation validation, Trace and evaluation record
```

## 3. Retrieval path

The retrieval engine runs global vector search and intent-directed search, merges candidates, removes duplicates, optionally reranks them, and records which chunks were selected for the final prompt. A retrieved chunk retains its knowledge base, collection, document and chunk identity through post-processing.

## 4. Tool path

The Bootstrap service exposes model-visible tool schemas but removes all model-provided identity fields before private tool execution. It injects an authenticated fixture principal, issuance time and HMAC proof. The MCP Server verifies the proof and then queries the isolated `huawei_mall_mock` schema.

## 5. Closed-loop execution

- Missing parameters create a pending task. A later user turn can fill the missing fields and resume the same task.
- Ambiguous entities produce trusted candidates from tools or databases. The user selects a candidate instead of allowing the model to invent an identifier.
- Dependent tools execute in stages. Only declared output fields can be bound to declared downstream parameters.
- Cancellation, conflict and expired-task paths terminate explicitly and are recorded in Trace.

## 6. Data boundary

Stable public knowledge lives in four knowledge collections. Dynamic and user-specific facts live in twelve synthetic PostgreSQL tables and are available only through eight read-only tools. Unsupported mutations such as cancelling an order are routed to a fixed system response.

