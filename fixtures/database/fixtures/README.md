# Huawei Mall 固定 Mock Seed V1.0

`seed.sql` 是可重复审阅的固定数据定义，数据版本为 `huawei-mall-fixture-v1.0`。事务开始时检查 Mock 表为空；不删除数据，也不触碰 `public`。重导入只使用 `reset_mock_seed.sql` 中显式列出的 12 张 Mock 表，不能重置 Ragent 会话、用户或 KB 表。

当前草案覆盖 20 个商品、4 个用户、12 笔订单、10 台带 SN 的设备、四地区库存、4 种券、4 张维修单、物流与维修进度事件、以及屏幕和电池的固定备件价格。所有数据与事件时间都以 `2026-09-14T10:00:00+08:00` 为实验视点，不含随机生成。物流事件从各订单的固定创建时间和地区确定性生成；价格则按 `effective_at <= asOf` 选最近版本。全国 `CN_DEFAULT` 与广州缺货记录均在 Seed 中固定。

`case_facts.json` 为 20 条 Case 定义 principal 与数据前提：U001 承担正常 Tool 与无唯一实体的 Missing 场景；U002 在 2026 年 8 月有两块手表，并拥有两台不同笔记本、两张未使用券；U003 同期恰好有一块手表和一台笔记本，能提供下游 SN 与 productId。U004 仅用于直接 Tool 验收，提供过保、未知保修与 8 月起点/9 月终点订单；`AX3` 的两个 SPU 用于验证 model 多候选，不影响 TOOL-04 的唯一型号。KB Case 的文档事实仍须在四个 KB 建好后验证。TOOL-04 使用“MateBook X Pro 2026”作为唯一匹配型号。

不维护单独的 `manifest.json`。在线 PostgreSQL 检查要求 DDL 约束通过，`../audit.sql` 与 `case_fact_audit.sql` 的所有违例查询返回 0 行。重载通过只列出 Mock 表的事务重置完成，不使用 CASCADE。Case Fact 审计核对的是数据库前提，不验证 KB 内容或 Agent 行为。

八个 Tool 的业务服务、MCP handler、Bootstrap 身份绑定、四个 KB、登录 principal 映射、固定时钟、证据采集、KB Case 与 Intent 路由均应通过自动化测试或审计。该 Fixture 用于运行 20 条 Golden Case，但当前文件本身不代表 Agent 评测结果。
