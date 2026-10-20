# MallAgent 测试数据与评测用例

`fixtures` 保存本地演示和确定性回归所需的数据，不包含真实用户、真实订单或华为生产数据。

## 目录说明

| 目录 | 内容 | 是否参与运行 |
| --- | --- | --- |
| `database/` | Mock Schema、固定 Seed、重置与一致性校验脚本 | 是 |
| `knowledge/` | 四类公共知识材料和 Intent—资源映射 | 是 |
| `evaluation/` | Agent Golden Case 与 Intent 路由用例 | 评测时使用 |

## 数据版本

- 数据集：`huawei-mall-fixture-v1.0`
- 固定业务时钟：`2026-09-14T10:00:00+08:00`
- 时区：`Asia/Shanghai`

固定时间和知识快照日期用于保证回归结果可复现，不代表实时商城状态。

## 使用原则

- 修改数据库 Schema、Seed、知识正文或 Intent 映射时，应同步更新受影响的评测用例。
- 不在 Fixture 中保存密钥、登录 Token 或生产数据。
- 评测结果应记录数据集版本、模型配置和运行时间，不能把固定用例结果解释为生产准确率。

数据库初始化见 [database/README.md](database/README.md)，知识数据说明见 [knowledge/mall-fixture-v1/README.md](knowledge/mall-fixture-v1/README.md)。
