# Huawei Mall D2 验收记录

- 状态：`D2_TOOL_SCOPED_ENTITY_RESOLUTION_PASSED`
- 验收日期：2026-09-18
- 范围：Tool-scoped Entity Resolution with Trusted Candidate Selection

## 真实链路结果

| Case | 结果 | 观察结果 |
| --- | --- | --- |
| TOOL-04 | PASS | `MateBook X Pro` 唯一标准化为 `P-LAPTOP-001`，换屏总价 `3299.00 CNY` |
| AMB-01 | PASS | U002 两块手表进入选择；选择第二块后查询 `WATCH-SN-002-FIT`，结果 `IN_WARRANTY` |
| AMB-02 | PASS | U002 两台笔记本进入选择；选择第一台后查询 `P-LAPTOP-001`，总价 `3299.00 CNY` |
| AMB-03 | PASS | U002 两张优惠券进入选择；选择第一张后转入 D1 补订单，订单 `202607050001` 查询成功 |

## 已证明行为

- 自然语言商品名称可以绑定可信标准 ID。
- 多候选时终态 Tool 在选择前不执行。
- 用户只能从 Pending 中的真实候选进行选择。
- 选择后按当前身份重新验证候选。
- 实体选择后重新进入 `MissingParameterGate`。
- AMB-03 完成 `NEED_SELECTION → NEED_PARAMETERS → COMPLETED` 三轮闭环。
- D1 与 D2 共用 Pending Store，没有引入第二套会话状态。

## 当前边界

Pending 期间的主动任务切换仍不在 D2 范围内。测试不同 Case 时应使用新对话，或先发送“算了，不查了”取消当前 Pending。
