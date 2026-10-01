# E3 Grounding Evaluation & Source Alignment

状态：`E3_GROUNDING_EVALUATION_PASSED`

## 实现边界

- 默认线上关闭；请求参数 `groundingEvaluation=true` 或全局配置开启时运行。
- E2 清理后的答案进入 E3，评测完成后统一写入同一 `execution-record`，答案内容不被修改。
- 每个事实 Claim 独立记录 Grounding、Citation Alignment、Authority 三个维度。
- Judge 只能看到 selected Evidence 与本轮安全 Tool Context；Source ID 经过确定性白名单校验。
- Trace 只保存 Source ID，不复制 KB / Tool 正文。
- 零分母指标记录为 `null`。

## 当前 Tool Source 边界

当前 Answer Generation 使用的 `mcpContext` 是安全 Tool 结果投影。E3 将其与本轮成功 Tool Execution ID 关联；首轮验收聚焦单 Tool 与 KB+单 Tool Mixed Case。多 Tool 的逐结果独立投影将在需要扩展 E3 到 D3 组合回答时再细化。

## 运行

Golden 页面使用：`/golden-baseline.html?groundingEvaluation=true`。

状态通过条件以 V2 设计中的 KB、Mixed、Tool-only 与故障注入断言为准。

## 首轮 Runtime Audit 与修复

首轮 3 Case 已证明 E3 请求开关、Trace 持久化与 Mixed Source Alignment 链路有效，同时发现：

- KB-only Judge 输出存在 JSON 解析失败；
- Tool-only Judge 可能使用 Tool ID 而非 Tool Execution ID，导致整条评测被拒绝；
- Mixed 回答事实全部有依据且来源正确，但 KB Citation Coverage 为 0。

对应修复保持在 Eval / Prompt 边界内：

- Judge JSON 兼容对象或数组根节点、尾逗号、单引号和未转义控制字符；失败时保存错误摘要与截断输出；
- Tool Source 支持 `T1` 与唯一 Tool ID 的确定性归一，单 Tool Claim 可绑定唯一成功执行；
- 无效 Source ID 不再使整条评测直接失败；
- Answer Prompt 明确禁止前置 Citation、独立 Citation 行及答案末尾集中引用。

此外，运行验收暴露了两种合法 Citation 的格式差异：独立 Citation 行，以及位于句号后的 Citation。E2 现会在不改变 Evidence ID 的前提下确定性规范为句末 Citation；E3 将段末 Citation 视为同段作用域，但仅在 Judge 确认同一 Evidence 支撑该 Claim 时计入覆盖率。编号标题被确定性排除出事实 Claim。

## 最终验收

3 条 E3 Case 均由真实 Chat 链路完成结构断言：

| Case | Source 组合 | Trace | 结果 |
| --- | --- | --- | --- |
| E3-KB-01 | KB | `2101198535092584448` | PASS |
| E3-MIXED-01 | KB + Tool | `2101201404747304960` | PASS |
| E3-TOOL-01 | Tool | `2101190407751958528` | PASS |

验收结论：Grounding Evaluation、Citation Coverage、Citation Support、Authority Alignment 与 Tool-only Grounding 已形成可自动回归的最小闭环。
