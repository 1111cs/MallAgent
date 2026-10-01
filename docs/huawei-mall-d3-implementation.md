# Huawei Mall D3 Implementation

状态：`D3_DEPENDENCY_EXECUTION_PASSED`

D3 按冻结契约实现一个上游到一个或多个下游的顺序执行。当前仅匹配“上月/上个月购买”的手表或笔记本来源语义；未将“之前”“上次”等模糊时间擅自映射到 `LAST_MONTH`。

实现链路：`DependencySourceMatcher` → `DependencyContractRegistry` → `DependencyExecutionCoordinator` → `getOrder` 业务状态解析 → category 筛选 → 白名单 `TOOL_OUTPUT` 绑定 → `MissingParameterGate` → 下游 Tool。

支持的契约：

- `DEP-WARRANTY-BY-ORDER`
- `DEP-SPARE-BY-ORDER`
- `DEP-WARRANTY-AND-SPARE-BY-ORDER`

Trace 在现有 `ExecutionRecord` 中增加 `toolPlan` 与 `toolBindings`。Golden Evaluator 支持计划、绑定和逐 Tool 调用次数断言。多候选复用 D2 的 Pending/Selection，并在恢复前用原 `getOrder` 约束重新验证。

人工验收前不标记 D3 PASS。验收文件：`frontend/public/golden-d3-dependency.json`。

## 人工验收结果

- DEP-01：上月购买手表完成保修查询，结果为 IN_WARRANTY。
- DEP-02：上月购买笔记本完成换屏价格查询，估算总价 3299.00 CNY。
- DEP-03：同一请求完成手表保修和换屏价格查询，保修为 IN_WARRANTY，估算总价 599.00 CNY。


