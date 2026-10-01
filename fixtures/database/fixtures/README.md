# Huawei Mall 固定 Mock Seed V1.0

`seed.sql` 是可重复审阅的固定数据定义，2026-09-15 已导入 `ragent_huawei_baseline.huawei_mall_mock`，并于 2026-09-16 冻结为 `huawei-mall-fixture-v1.0`。事务开始时检查 Mock 表为空；不删除数据，也不触碰 `public`。重导入只使用 `reset_mock_seed.sql` 中显式列出的 12 张 Mock 表；不能重置 Ragent 会话、用户或 KB 表。

当前草案覆盖 20 个商品、4 个用户、12 笔订单、10 台带 SN 的设备、四地区库存、4 种券、4 张维修单、物流与维修进度事件、以及屏幕和电池的固定备件价格。所有数据与事件时间都以 `2026-09-14T10:00:00+08:00` 为实验视点，不含随机生成。物流事件从各订单的固定创建时间和地区确定性生成；价格则按 `effective_at <= asOf` 选最近版本。全国 `CN_DEFAULT` 与广州缺货记录均在 Seed 中固定。

`case_facts.json` 为 20 条 Case 定义 principal 与数据前提：U001 承担正常 Tool 与无唯一实体的 Missing 场景；U002 在 2026 年 8 月有两块手表，并拥有两台不同笔记本、两张未使用券；U003 同期恰好有一块手表和一台笔记本，能提供下游 SN 与 productId。U004 仅用于直接 Tool 验收，提供过保、未知保修与 8 月起点/9 月终点订单；`AX3` 的两个 SPU 用于验证 model 多候选，不影响 TOOL-04 的唯一型号。KB Case 的文档事实仍须在四个 KB 建好后验证。Golden V0 中 TOOL-04 的“MateBook X Pro”表述以冻结 V1.1 的“MateBook X Pro 2026”为准；这里唯一匹配该型号。

不维护单独的 `manifest.json`；冻结版本的实际行数、数据库快照与 SHA-256 集中记录在 `huawei-mall-kb/freezes/baseline-adapter-v1/`。在线 PostgreSQL 检查：DDL 约束通过；`../audit.sql` 与 `case_fact_audit.sql` 的所有违例查询返回 0 行。重载通过只列出 Mock 表的事务重置完成，没有使用 CASCADE。Case Fact 审计核对的是数据库前提，不验证 KB 内容或 Agent 行为。

八个 Tool 的业务服务与 MCP handler 直接测试已通过（14 个测试），Bootstrap 身份绑定定向测试已通过（2 个测试）。四个 KB、真实登录 principal 映射、固定时钟、同次请求证据采集、5 个 KB Golden Case 与 24 条 Intent 路由冒烟均已完成冻结前验收。现在可以使用该 Fixture 运行正式 20 Case Baseline Audit；当前文件不代表那 20 条的实验结果。
