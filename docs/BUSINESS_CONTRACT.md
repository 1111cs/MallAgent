# Huawei Mall Agent 业务契约

> 契约版本：1.1
>
> 适用范围：商城问答、知识库检索、MCP 工具调用、实体解析与业务评测
>
> 数据说明：项目使用公开页面整理后的演示知识与确定性 Mock 业务数据，不连接华为生产系统。

本文档描述当前系统实际遵守的业务边界和接口约束。模型负责理解自然语言、识别意图和抽取候选参数；Java 服务负责路由决策、参数来源校验、实体绑定、身份与权限治理；MCP Server 只执行通过门禁的只读查询。

## 1. 设计原则

1. **公开知识与私有数据分开处理**：公开政策、产品知识走 RAG；订单、物流、保修、维修等用户私有或实时数据走 MCP 工具。
2. **模型只提供候选，不直接决定高风险事实**：Intent、普通参数可以由模型识别；用户身份和关键业务主键必须由可信数据源绑定。
3. **先治理，再执行**：任何工具调用都必须经过资源映射、工具白名单、参数 Schema、实体解析和身份权限校验。
4. **只读演示边界**：当前八个工具均为查询工具，不提供取消订单、修改收货信息、领取优惠券或创建维修单等写操作。
5. **执行过程可评测**：不仅检查最终文案，还检查路由、工具、参数来源、调用顺序和任务状态。

## 2. 身份、时间与参数来源

### 2.1 身份

- `userId` 由服务端根据登录态注入，模型、前端参数和用户文本都不能覆盖。
- 私有工具必须在当前用户的数据范围内查询；即使业务编号格式合法，也不能越权读取其他用户的数据。
- 未登录或身份映射失败时，不执行需要用户身份的工具。

### 2.2 业务时间

- 默认时区：`Asia/Shanghai`。
- 演示与回归评测使用固定业务时钟：`2026-09-14T10:00:00+08:00`。
- 自然语言时间先由确定性时间归一化组件转换为绝对区间，再传给业务查询。例如“上个月”转换为 `[2026-08-01, 2026-09-01)`。
- 模型不能自行生成数据库查询时间边界。

### 2.3 参数来源

执行记录中的参数来源限定为：

| 来源 | 含义 |
| --- | --- |
| `USER_QUERY` | 从本轮用户表达中抽取的普通参数 |
| `CLARIFICATION_INPUT` | 用户对缺参或消歧问题的补充 |
| `AUTH_CONTEXT` | 服务端从登录态注入的身份参数 |
| `ENTITY_RESOLUTION` | 从可信业务候选中解析并绑定的实体主键 |
| `TOOL_OUTPUT` | 从已完成的上游工具结果中绑定的依赖参数 |
| `DEFAULT` | 由业务契约定义的确定性默认值 |

下游参数只能来自上述受控来源，不接受模型凭空补全关键业务标识。

## 3. 业务范围

### 3.1 商品范围

演示数据覆盖六类商品：

- 手机 `phone`
- 平板 `tablet`
- 笔记本 `laptop`
- 穿戴设备 `wearable`
- 耳机 `headphone`
- 路由器 `router`

### 3.2 知识库范围

| 知识库 | 内容 |
| --- | --- |
| `product-kb` | 产品参数、对比、推荐与生态兼容信息 |
| `shopping-policy-kb` | 优惠、发票、配送、退换货等公开政策 |
| `after-sales-kb` | 保修、维修、备件价格与服务流程 |
| `support-kb` | 使用指导、故障排查、网络连接和电池充电 |

知识库数据位于 `fixtures/knowledge/mall-fixture-v1`。当前固定数据集按 Markdown 二级标题生成知识单元，主要用于可复现评测；它不是面向生产效果的最佳切分方案。

## 4. Intent 与资源映射

Intent 是用户任务的语义分类，资源是系统允许执行的受控能力。二者不是简单的一一对应关系：一个 Intent 可以映射到知识库、MCP 工具、系统动作，或由多个资源共同完成。

| Intent | 主要资源 | 说明 |
| --- | --- | --- |
| `PRODUCT_INFO` | `product-kb` / `searchProducts` | 公开产品知识或结构化商品查询 |
| `PRODUCT_COMPARE` | `product-kb` / `searchProducts` | 对比知识与商品候选 |
| `PRODUCT_RECOMMEND` | 混合资源 | 先检索知识，必要时查询商品数据 |
| `PRODUCT_COMPATIBILITY` | `product-kb` | 设备与生态兼容知识 |
| `COUPON_POLICY` | `shopping-policy-kb` | 公开优惠规则 |
| `INVOICE_POLICY` | `shopping-policy-kb` | 公开发票规则 |
| `DELIVERY_POLICY` | `shopping-policy-kb` | 公开配送规则 |
| `RETURN_POLICY` | `shopping-policy-kb` | 公开退换货规则 |
| `ORDER_QUERY` | `getOrder` | 用户订单查询 |
| `LOGISTICS_QUERY` | `getLogistics` | 用户物流查询 |
| `ORDER_CHANGE` | SYSTEM | 当前只读系统不执行订单修改 |
| `WARRANTY_POLICY` | `after-sales-kb` | 公开保修政策 |
| `WARRANTY_STATUS` | `getWarrantyStatus` | 指定设备的保修状态 |
| `SPARE_PART_PRICE` | `getSparePartPrice` | 备件价格查询 |
| `REPAIR_SERVICE` | `after-sales-kb` | 公开维修服务流程 |
| `REPAIR_PROGRESS` | `getRepairProgress` | 用户维修进度查询 |
| `SERVICE_CENTER` | `after-sales-kb` | 服务中心相关公开信息 |
| `USAGE_GUIDE` | `support-kb` | 产品使用指导 |
| `TROUBLESHOOTING` | `support-kb` | 常见故障排查 |
| `CONNECTION_NETWORK` | `support-kb` | 网络与连接问题 |
| `CHARGING_BATTERY` | `support-kb` | 充电与电池问题 |

Java 路由层根据候选 Intent、置信度、资源映射和业务边界决定最终执行路径。没有可信 Intent 但仍属于商城公开知识时，可进入全局知识库兜底；问题信息不完整时发起澄清；超出商城范围时明确拒绝。

## 5. MCP 工具通用契约

### 5.1 工具清单

当前只开放八个只读工具：

1. `searchProducts`
2. `getProductAvailability`
3. `getOrder`
4. `getLogistics`
5. `getCouponEligibility`
6. `getWarrantyStatus`
7. `getSparePartPrice`
8. `getRepairProgress`

工具确定后，系统只向参数抽取模型提供该工具的 Schema，不把全部工具定义同时暴露给模型。

### 5.2 统一响应

工具结果使用统一结构：

```json
{
  "status": "OK",
  "data": {},
  "candidates": [],
  "error": null,
  "meta": {}
}
```

`status` 取值包括：`OK`、`VALIDATION_ERROR`、`NOT_FOUND`、`MULTIPLE_MATCHES`、`CONFLICT`、`INTERNAL_ERROR`。

### 5.3 区域枚举

需要区域参数的工具只接受：`CN_DEFAULT`、`BEIJING`、`SHANGHAI`、`GUANGZHOU`。未提供时使用 `CN_DEFAULT`。

## 6. 八个工具的参数约束

### 6.1 `searchProducts`

- 必填：`category`
- 可选：`budgetMin`、`budgetMax`、`usage`、`requirements`
- 笔记本使用场景：`coding`、`office`、`business_travel`、`video_editing`、`study`
- 笔记本偏好：`lightweight`、`long_battery`、`high_memory`、`large_screen`、`portable`

### 6.2 `getProductAvailability`

- 必填：`productId`
- 可选：`region`，默认 `CN_DEFAULT`

### 6.3 `getOrder`

- 服务端注入：`userId`
- 可选：`orderNo`、`timeRange`、`category`、`productKeyword`
- 返回结果始终受当前用户身份约束。

### 6.4 `getLogistics`

- 服务端注入：`userId`
- 必填：`orderNo`
- `orderNo` 必须通过格式和归属校验。

### 6.5 `getCouponEligibility`

- 服务端注入：`userId`
- 必填：`couponId`
- `orderNo` 与 `productIds` 至少提供一项。
- `couponId` 必须从当前用户可见的优惠券候选中解析并绑定。

### 6.6 `getWarrantyStatus`

- 服务端注入：`userId`
- 必填：`sn`
- `sn` 必须从当前用户拥有的设备候选中解析并绑定。

### 6.7 `getSparePartPrice`

- `productId` 与 `model` 至少提供一项。
- 必填：`part`
- 可选：`region`，默认 `CN_DEFAULT`
- `part` 取值：`screen`、`battery`、`mainboard`、`back_cover`、`camera`、`other`
- 需要绑定 `productId` 时，必须通过商品候选解析，不能由模型猜测。

### 6.8 `getRepairProgress`

- 服务端注入：`userId`
- `serviceNo` 与 `phone` 至少提供一项。
- 查询必须受当前用户身份约束。

## 7. 参数门禁、实体解析与任务续接

### 7.1 参数门禁顺序

工具执行前按以下顺序处理：

1. 校验 Intent 是否映射到受控资源；
2. 校验工具是否位于当前路由白名单；
3. 按工具 Schema 抽取并校验普通参数；
4. 注入登录身份和契约默认值；
5. 对关键业务实体执行可信候选解析；
6. 绑定上游工具结果中的依赖参数；
7. 再次检查必填、格式、归属和权限后调用 MCP Server。

### 7.2 缺参补问

系统已确定工具但参数不完整时，不执行工具。当前任务、已收集参数和待补字段会保存到 Redis，并向用户发起补问。下一轮优先恢复待处理任务：

- 用户补充参数：合并后重新校验；
- 用户取消：删除待处理任务并结束；
- 用户提出新问题：放弃旧任务，重新进入正常意图路由。

任务完成后原子删除待处理状态，避免同一任务被重复执行。

### 7.3 实体消歧

当前实体契约重点覆盖：

- `getWarrantyStatus.sn`
- `getCouponEligibility.couponId`
- `getSparePartPrice.productId`

Java 从当前用户或业务数据中查询候选实体，再与用户表达匹配：唯一候选直接绑定；无候选时要求补充信息；多个候选时保存任务与候选列表并让用户选择。用户下一轮选择后，系统会重新查询业务数据校验，而不是直接信任候选序号或模型输出。

### 7.4 多工具依赖

当下游工具依赖上游结果时，参数通过 `TOOL_OUTPUT` 来源绑定，并校验调用顺序。例如先查询订单再使用其订单号查询物流。模型可以提出调用计划，但不能绕过依赖校验直接构造下游主键。

## 8. 数据与 Fixture

- 数据集版本：`huawei-mall-fixture-v1.0`
- 数据库包含 12 张商城演示表：10 张核心业务表，以及物流事件、维修事件表。
- Seed 数据是确定性的，不使用随机数或系统当前时间生成业务事实。
- Mock 数据只用于本地演示与评测，不代表真实用户、真实订单、实时库存或官方售后数据。
- 知识库与数据库 Fixture 的版本必须在评测 Trace 中记录，保证结果可复现。

## 9. 评测契约

Golden Case 不只检查最终回答，而是检查 Agent 是否沿正确路径完成任务。每条 Case 至少包含：

- 固定用户身份与业务时钟；
- 用户问题及必要的多轮补充；
- 预期 Intent 和资源；
- 预期工具与调用顺序；
- 预期参数值及参数来源；
- 预期任务状态、关键结果或拒绝原因。

运行时通过真实接口完成整条链路并记录结构化 Trace。只有路由、工具、参数、顺序、状态和结果断言全部通过，该 Case 才算通过。自然度、友好度等主观体验另由人工评估。

## 10. 变更规则

以下内容发生变化时，应同步更新契约、Fixture 版本和回归用例：

- Intent 树或 Intent 到资源的映射；
- MCP 工具名称、Schema、权限或响应结构；
- 关键参数来源和实体解析规则；
- 知识库切分、召回或重排策略；
- 数据库 Fixture、固定时钟或身份映射；
- Golden Case 的预期行为。

这样可以让业务能力演进，同时保持代码、文档和评测口径一致。
