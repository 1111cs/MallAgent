# 商城 Mock 数据库

本目录提供 [商城业务契约](../../docs/BUSINESS_CONTRACT.md) 所需的确定性业务数据。`schema.sql` 在 PostgreSQL 中创建独立的 `huawei_mall_mock` Schema，不修改项目原有的 `public` 表。

## 文件说明

| 文件 | 用途 |
| --- | --- |
| `schema.sql` | 创建 12 张商城 Mock 表和约束 |
| `fixtures/seed.sql` | 写入固定演示数据 |
| `fixtures/reset_mock_seed.sql` | 仅清理 12 张 Mock 表，便于重新导入 |
| `audit.sql` | 校验表级约束和数据一致性 |
| `fixtures/case_facts.json` | 记录 Golden Case 依赖的业务事实 |
| `fixtures/case_fact_audit.sql` | 校验 Golden Case 的数据库前提 |

## Tool 与数据来源

| Tool | 主要表 |
| --- | --- |
| `searchProducts` | `t_product` |
| `getProductAvailability` | `t_product`、`t_inventory` |
| `getOrder` | `t_order`、`t_order_item`、`t_product` |
| `getLogistics` | `t_order`、`t_logistics_event` |
| `getCouponEligibility` | `t_coupon`、`t_user_coupon`、订单与商品表 |
| `getWarrantyStatus` | `t_device`、`t_product` |
| `getSparePartPrice` | `t_product`、`t_spare_part_price` |
| `getRepairProgress` | `t_repair_order`、`t_repair_progress_event`、设备与商品表 |

所有私有查询都必须使用服务端注入的用户身份，模型提供的 `userId` 或 `principalId` 不参与数据归属判断。

## 初始化与校验

在仓库根目录执行：

```bash
psql -v ON_ERROR_STOP=1 -U postgres -d ragent -f fixtures/database/schema.sql
psql -v ON_ERROR_STOP=1 -U postgres -d ragent -f fixtures/database/fixtures/seed.sql
psql -v ON_ERROR_STOP=1 -U postgres -d ragent -f fixtures/database/audit.sql
psql -v ON_ERROR_STOP=1 -U postgres -d ragent -f fixtures/database/fixtures/case_fact_audit.sql
```

两个校验脚本中的违例查询都应返回 0 行。当前数据版本为 `huawei-mall-fixture-v1.0`；修改表结构或 Seed 后，应提升版本并重新运行工具测试与 Golden Case。
