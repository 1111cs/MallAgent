# G1 Routing Governance Implementation V1

状态：`G1_ROUTING_GOVERNANCE_PASSED`

已实现七种显式决策、结构化分类状态、纯 Meta Pre-routing、三组 Semantic Contract、Original/Rewrite 冲突修正、AllowedRoute 单一授权来源、公共 KB 白名单和显式 Global Search 准入。

运行时顺序：Pending D3/D2/D1 → Pre-routing → Rewrite → structured Intent Resolution → Governance → AllowedRoute → Retrieval/Tool → Trace。

`ExecutionRecord` 新增 `routingDecision` 与 `routingReason`；`routes` 由最终 AllowedRoute 生成。分类器的 `NO_MATCH`、`PARSE_ERROR`、`MODEL_ERROR` 不再坍缩为同一个空列表语义。

验收使用 `frontend/public/routing-robustness-suite-v1.json`。

## 人工验收约束

每个 Routing Robustness Case 使用新对话，并确认登录业务用户与 Case 的 `principalId` 一致。原因是运行时顺序明确规定 Pending D3/D2/D1 优先于 G1 Pre-routing；复用带 Pending 的对话会验证任务恢复，而不是当前 Case 的路由策略。

首次人工验收发现“帮我查一下”在分类器暂时失败时返回 `ROUTING_ERROR`。现已将可确定识别的不完整请求判断提前到分类器错误处理之前，使其稳定产生 `NEED_CLARIFICATION / INCOMPLETE_BUSINESS_REQUEST`。

## 最终人工验收结果

- `我的用户编号是多少？`：由可信身份上下文返回 `U001`。
- `帮我查一下。`：返回商城业务范围澄清，不进入检索或 Tool。
- `帮我查订单 202609140001。`：进入订单查询并返回订单事实。
- `订单 202609140001 到哪了？`：进入物流查询并返回轨迹及预计送达时间。

结合前一轮已经通过的 SYSTEM、OUT_OF_SCOPE、GLOBAL_KB_FALLBACK 与两条 KB 路由用例，G1 人工验收完成，冻结为 `G1_ROUTING_GOVERNANCE_PASSED`。
