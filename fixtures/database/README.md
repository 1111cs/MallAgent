# Huawei Mall Mock DB：DDL v1.0

本目录实现 [Huawei Mall 业务契约冻结 V1.1](../Huawei%20Mall%20业务契约冻结%20V1.1.md) 的固定数据表与 Fixture。`schema.sql` 只在明确选定的 PostgreSQL 测试数据库中创建 `huawei_mall_mock` schema；不修改 Ragent 现有 `public` 表。Seed 与 Fixture 已作为 Baseline Adapter V1 冻结。

## 表与已冻结 Tool 的数据来源

V1.1 规定的十张核心表均已建立。为兑现已冻结的事件数组，增加 `t_logistics_event`、`t_repair_progress_event` 两张允许的事件表。总数为 12 张。物流承运商、运单号、状态和预计送达时间存于 `t_order`，避免额外的物流主表。

| Tool | 主要表 | 确定性派生 |
|---|---|---|
| searchProducts | `t_product` | 固定 `spec_json` 筛选映射；匹配约束由实际命中筛选项组成 |
| getProductAvailability | `t_product`, `t_inventory` | 库存状态由 `stock` 计算；`CN_DEFAULT` 读取固定记录 |
| getOrder | `t_order`, `t_order_item`, `t_product` | 从可信身份限定 `user_id`；`LAST_MONTH` 使用固定半开区间 |
| getLogistics | `t_order`, `t_logistics_event` | 事件按 `event_time` 排序；无运行时虚构事件 |
| getCouponEligibility | `t_coupon`, `t_user_coupon`, `t_order`, `t_order_item`, `t_product` | 固定时钟、门槛、品类和已使用状态决定 `eligible/reasonCode` |
| getWarrantyStatus | `t_device`, `t_product` | 固定时钟与保修日期决定状态 |
| getSparePartPrice | `t_product`, `t_spare_part_price` | `estimatedTotal = part_price + labor_fee`；在固定时钟前选择最新生效版本 |
| getRepairProgress | `t_repair_order`, `t_device`, `t_product`, `t_repair_progress_event` | 历史事件按 `event_time` 排序；联系号码由 Adapter 脱敏 |

`t_user` 是 Mock 业务用户，不替换 Ragent 的认证用户表。Adapter 必须从可信认证上下文取 `principal_id`，再以该 ID 访问 Mock 数据；模型参数不能决定归属。输出 `currency=CNY`、`asOf`、`timezone`、`datasetVersion` 由固定配置给出。模型和商品名称的快照保留于订单项，历史订单不受商品当前名称影响。

## 执行与验证

在已隔离的测试数据库、且确认没有同名 Mock schema 时执行：

```powershell
psql -v ON_ERROR_STOP=1 -d <test_database> -f schema.sql
```

`schema.sql` 有事务，任一 DDL 错误都会回滚。它不会删除已有 schema，因此重复执行会因 `CREATE SCHEMA` 冲突而停止。固定 Seed 草案、Case Facts 与清单位于 [`fixtures/`](fixtures/README.md)。在同一个数据库写入 Seed 后执行 `audit.sql` 和 `fixtures/case_fact_audit.sql`；每个违例查询都应返回 0 行。审计脚本不能取代 row count 与 SHA-256 校验，也不能证明 Tool 和 KB 已验收。

2026-09-16，隔离数据库 `ragent_huawei_baseline` 中的 Mock schema、固定 Seed、身份映射、固定时钟、四个 KB、Intent Resource Mapping 与八个 Tool 已完成验收并冻结为 `huawei-mall-fixture-v1.0`。正式快照与校验记录位于 `huawei-mall-kb/freezes/baseline-adapter-v1/`。
