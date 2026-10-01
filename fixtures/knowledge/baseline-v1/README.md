# Huawei Mall KB Baseline V1 (Frozen)

本目录用于 Huawei Mall Agent Baseline 的固定知识库内容。

- dataset_version: `huawei-mall-fixture-v1.0`
- source_snapshot: `2026-09-15`
- status: `FROZEN_BASELINE_ADAPTER_V1`
- KB 数量: 4
- Markdown 文档数: 16

## 边界

1. Product KB：稳定商品知识、项目 Fixture 中的固定样例规格、场景与兼容性原则。
2. Shopping Policy KB：公共交易规则，不承载某个用户的订单/物流/优惠券实时状态。
3. After-sales KB：公共保修与维修流程，不承载某台设备的实时保修/维修状态。
4. Support KB：通用使用指导与故障排查。
5. 私有、实时、结构化状态继续走 Tool，例如 `getOrder`、`getLogistics`、`getCouponEligibility`、`getWarrantyStatus`、`getSparePartPrice`、`getRepairProgress`。
6. 本目录是项目固定测试知识，不应被表述为华为商城内部真实知识库或当前官网实时政策。

## 文件约定

- 本版本仅以 product/、shopping-policy/、after-sales/、support/ 下的 16 份 Markdown 为 KB 正文来源；README.md 与 golden-kb-cases-suggested.json 不参与导入。
- golden-kb-cases-suggested.json 是补充诊断问题，不能替代项目的 Golden Use Case V0。
- 不维护静态文件哈希 manifest；导入时记录实际 chunk、向量及最终 KB 快照校验值，以确保 Baseline 与改造组使用同一份知识。

## 导入核验（2026-09-16）

- 本版本的 16 份文档已导入隔离库 ragent_huawei_baseline；原四份平铺文档及向量已从隔离库移除。
- 分块策略：以每个 Markdown 二级标题及其正文形成一张知识卡片，并在卡片前重复文档一级标题。无重叠，不跨主题合并。使用 Ragent 原有的手工 Chunk 创建接口完成 embedding 与向量写入。
- YAML 前言不进入 embedding 正文；其中的 kb_id、dataset_version、source_snapshot、content_scope 与 category/type 写入向量 metadata，并附上 topic、source_path 和快照校验值。
- 核验结果：16 文档、51 Chunk、51 个 1536 维向量；Product 14、Shopping Policy 11、After-sales 12、Support 14。每张卡片仅有一个二级标题，Chunk 与向量正文一致，metadata 无缺项。
- 导入脚本：../ingestion/import_baseline_v1.py；逐文件对象存储 URL、文档/Chunk ID、checksum 与核验结果：../audits/kb-import-2026-09-16.json。

## 冻结结论

Golden V0 的 5 个 KB Case 已完成真实检索与人工 Groundedness 审计，结果为 5/5 PASS。Baseline Adapter V1 于 2026-09-16 冻结；后续 Baseline 与改造组必须复用这 51 个知识单元，除非创建新的显式数据版本。

