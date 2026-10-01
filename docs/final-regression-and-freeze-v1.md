# Huawei Mall Reliable Agent — Final Regression & Freeze V1

状态：`CANCELLED_BY_PROJECT_SCOPE`

> 2026-09-19 决策：分阶段验收与状态冻结已足够支持成果交付；不再投入时间进行重复的大一统回归，直接进入架构图、README 与项目表达阶段。

## 1. 冻结目标

本轮只做最终回归与结果冻结，不继续扩展 Agent 能力。若未发现阻断性缺陷，回归完成后代码进入功能冻结，随后产出架构图、项目 README、4 个代表 Demo、简历与面试材料。

## 2. Before：原始 Baseline

原始 Baseline 任务级结果已由审计冻结为 **7/20 PASS（35%）**：

| 类别 | Baseline | 主要失败 |
| --- | ---: | --- |
| KB | 5/5 | 无任务级失败 |
| Normal Tool | 2/5 | 身份接入、实体标准化、相邻 Tool 路由 |
| Missing | 0/4 | 缺参后仍调用 Tool，不能补参恢复 |
| Ambiguous | 0/3 | 多候选时不能可信选择与续接 |
| Dependency | 0/3 | 无上游 Tool 输出到下游参数的绑定 |
| **Total** | **7/20（35%）** | — |

冻结证据：`huawei-mall-kb/audits/baseline-v1/golden-v0-baseline-final-audit-2026-09-17.md` 与 `baseline-failure-analysis-v1.md`。

## 3. 自动回归

所有页面均使用 `http://localhost:5174`，并要求保存对应 Principal 的登录态。

### R1：Golden V0 20 条

入口：

```text
http://localhost:5174/golden-baseline.html
```

需要：U001、U002、U003 登录态。点击“运行全部未完成”，完成后下载 JSON。

覆盖：5 KB、5 Normal Tool、4 Missing 首轮、3 Ambiguous 首轮、3 Dependency。

### R2：Routing Robustness 9 条运行时 Case

入口：

```text
http://localhost:5174/golden-baseline.html?cases=/final-routing-regression-v1.json&assertions=/final-routing-regression-assertions-v1.json
```

只需 U001。点击“运行全部未完成”，完成后下载 JSON。

原 RG-09 是分类器故障注入单元测试，不属于真实 Chat Case；继续由 `RoutingGovernanceServiceTest` / `PreRoutingPolicyTest` 覆盖。

### R3：E2 Citation 7 条

入口：

```text
http://localhost:5174/golden-baseline.html?cases=/final-e2-citation-regression-v1.json&assertions=/final-e2-citation-regression-assertions-v1.json
```

只需 U001。点击“运行全部未完成”，完成后下载 JSON。

### R4：E3 Grounding 3 条

已完成：

| Case | Trace | 结果 |
| --- | --- | --- |
| E3-KB-01 | `2101198535092584448` | PASS |
| E3-MIXED-01 | `2101201404747304960` | PASS |
| E3-TOOL-01 | `2101190407751958528` | PASS |

## 4. 人工多轮回归

每个 Case 使用一个新对话，按顺序逐句发送。只需记录最终是否符合预期；Trace 由系统保存。

### M1：D1 Missing Parameter Closed Loop（U001）

**D1-01 维修进度**

1. `帮我查维修进度。`
2. `维修单号是 WX202609001。`

预期：第一轮询问服务单号或手机号且不调用终态 Tool；第二轮恢复任务并返回维修进度。

**D1-02 保修补参**

1. `帮我看看我的设备还在不在保。`
2. `SN 是 ABC001。`

预期：第二轮恢复并返回 `IN_WARRANTY`。

**D1-03 优惠券逐步补参**

1. `帮我看看这个优惠券为什么不能用。`
2. `优惠券是 C001。`
3. `我想用于订单 202609140001。`

预期：参数未齐时继续澄清；齐全后只调用一次 `getCouponEligibility`。

**D1-04 库存补参**

1. `我想看看广州有没有货。`
2. `商品是 P-LAPTOP-001。`

预期：恢复原任务，返回广州库存为 0 / 缺货。

**D1-05 取消 Pending**

1. `帮我看看我的设备还在不在保。`
2. `算了，不查了。`

预期：返回“已取消”，不再继续索取 SN。

### M2：D2 Trusted Entity Resolution（U002，TOOL-04 使用 U001）

**D2-01 唯一商品标准化（U001）**

1. `帮我查 MateBook X Pro 换屏多少钱。`

预期：标准化为 `P-LAPTOP-001`，返回估算总价 `3299.00 CNY`。

**D2-02 手表消歧（U002）**

1. `我的手表还在不在保？`
2. `第二块。`

预期：先展示两块手表；选择后查询 `WATCH-SN-002-FIT`。

**D2-03 笔记本消歧（U002）**

1. `我的笔记本换屏多少钱？`
2. `第一台。`

预期：先展示两台笔记本；选择后返回 `3299.00 CNY`。

**D2-04 优惠券消歧 + D1 衔接（U002）**

1. `为什么我的优惠券不能用？`
2. `第一张。`
3. `用于订单 202607050001。`

预期：选择可信候选后继续询问使用对象，最终完成资格查询。

## 5. 最终结果表

| 回归集 | Case 数 | 状态 | 结果文件 / Trace |
| --- | ---: | --- | --- |
| Baseline Before | 20 | FROZEN | 7/20（35%） |
| Golden V0 Ours | 20 | 17 PASS + 3 stale assertions corrected, pending targeted rerun | `huawei-mall-golden-v0-baseline-2026-09-19T07-11-30-085Z.json` |
| D1 多轮 | 5 | PENDING | — |
| D2 多轮 | 4 | PENDING | — |
| D3 | 3 | PENDING（由 Golden V0 覆盖） | — |
| Routing Runtime | 9 | PENDING | — |
| Routing Fault Injection | 1 | IMPLEMENTED（单元测试） | — |
| E2 Citation | 7 | PENDING | — |
| E3 Grounding | 3 | PASS | 3/3 |

## 6. Freeze 条件

- Golden V0、Routing Runtime、E2、E3 自动断言全部 PASS；
- D1 与 D2 多轮 Case 人工通过；
- 无身份越权、错误 Tool 执行、Pending 泄漏、伪造 Citation 或错误 Source Authority；
- 失败若仅来自外部模型瞬时超时，可重跑一次并单独记录，不计为业务机制失败；
- 完成后状态更新为 `FINAL_REGRESSION_V1_PASSED`，冻结代码功能范围。

## 7. Golden V0 Final Run Audit（2026-09-19）

首轮最终运行完成 20/20，自动断言为 17/20。三项失败经 Structured Trace 审核均为改造后正确语义与旧断言不一致：

- `TOOL-03`：显式 SN 在执行前经过可信设备候选复核，参数来源由 `USER_QUERY` 更新为 `ENTITY_RESOLUTION`；
- `TOOL-04`：自然语言型号标准化为 `productId=P-LAPTOP-001`，参数来源更新为 `ENTITY_RESOLUTION`；
- `AMB-01`：相对时间 + 资产类别先由 `getOrder` 发现候选，返回 `MULTIPLE_MATCHES` 后进入 `NEED_SELECTION`，终态 `getWarrantyStatus` 未执行。

冻结断言已按 D2/D3 的正式契约更新。由于三种 Principal 的会话 Token 在本次长运行后失效，三条定向自动重验等待重新保存 U001、U002 登录态。
