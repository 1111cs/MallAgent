# Mall Knowledge Fixture V1

本目录保存 MallAgent 用于本地演示和可复现评测的固定知识库内容。资料根据公开页面和项目业务场景整理，不是华为商城内部知识库，也不代表官网的实时政策或商品状态。

- dataset_version: `huawei-mall-fixture-v1.0`
- source_snapshot: `2026-09-15`
- status: `MALL_FIXTURE_V1`
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
- 不维护静态文件哈希 manifest；导入时应记录实际 Chunk、向量模型和知识库快照版本，保证不同运行使用同一份知识数据。

## 当前切分口径

- 分块策略：以每个 Markdown 二级标题及其正文形成一张知识卡片，并在卡片前重复文档一级标题；无重叠，不跨主题合并。
- YAML 前言不进入 embedding 正文；其中的 kb_id、dataset_version、source_snapshot、content_scope 与 category/type 写入向量 metadata，并附上 topic、source_path 和快照校验值。
- 固定数据共 16 份文档、51 个 Chunk：Product 14、Shopping Policy 11、After-sales 12、Support 14。
- 这种按二级标题切分的方式优先保证数据稳定和评测可复现；生产化时可进一步采用长度控制、重叠窗口、父子块或章节级重排降低长段噪声与跨标题信息丢失。

## 使用约束

回归评测应复用同一数据集版本、Embedding 模型和检索配置。修改正文、切分方式或向量模型时，应创建新的数据版本并重新运行知识库与 Agent 回归用例。

