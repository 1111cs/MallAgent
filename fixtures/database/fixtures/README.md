# 固定业务数据

本目录的数据版本为 `huawei-mall-fixture-v1.0`，用于本地演示、工具测试和 Agent 回归评测。

## 数据规模

- 20 个商品；
- 4 个 Mock 用户；
- 12 笔订单；
- 10 台带 SN 的设备；
- 4 个地区的库存记录；
- 4 种优惠券；
- 4 张维修单及其进度事件；
- 固定的物流事件、屏幕与电池备件价格。

所有业务事实均以 `2026-09-14T10:00:00+08:00` 为查询视点，不使用随机数据或系统当前时间。

## 文件说明

- `seed.sql`：在空的 `huawei_mall_mock` Schema 中写入固定数据；
- `reset_mock_seed.sql`：只清理明确列出的 12 张 Mock 表；
- `case_facts.json`：描述 20 条 Golden Case 所依赖的用户和业务事实；
- `case_fact_audit.sql`：检查这些业务事实是否成立。

重置脚本不使用 `CASCADE`，也不会修改项目会话、认证用户或知识库表。`case_fact_audit.sql` 只验证数据库前提，不替代 Agent 路由、工具和回答评测。
