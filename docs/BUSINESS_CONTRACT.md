# Huawei Mall 业务契约冻结 V1.1

状态：`BUSINESS_CONTRACT_FROZEN`  
Fixture 状态：`NOT_FROZEN`  
适用范围：Huawei Mall Adapter、Huawei Mall Golden Case V0、Ragent Baseline 与 Improved 对比实验。

V1.1 冻结业务语义、接口契约、意图资源映射、Golden Case 结构和 Fixture 冻结规则。实际 Seed 数据只有在 Adapter 实现、Fixture Audit 和校验通过后才能单独冻结。

---

## 1. 实验边界与公平原则

正式对照中的两个系统定义为：

```text
Baseline = 原始 ragent-1.0x 机制 + Huawei Mall 业务适配 + 无决策作用的观测
Improved = 同一业务适配与观测 + 根据 Baseline 失败证据确定的改进机制
```

Baseline 允许增加：

- 固定 Mock 数据、业务查询服务和 8 个只读 MCP Tool；
- Product、Shopping Policy、After-sales、Support 四个 KB；
- Huawei Mall Intent 与 KB/Tool/System 的配置映射；
- Tool Server 输入校验、确定性默认值、固定业务错误语义；
- 来自认证上下文的身份注入、固定实验时钟和确定性时间归一化；
- 同次请求的 Tool、检索和时延观测。观测只能记录，不能选择工具、补参数或改变执行结果。

Baseline 不允许新增：

- 通用 `ParameterResolver` 或 Agent 执行前的新增追问关口；
- `PendingToolCall`、跨轮工具任务恢复状态；
- 业务实体自动消歧器；
- 根据一个 Tool 结果重新规划另一个 Tool 的新机制；
- Tool Dependency DAG 或结果到下游参数的自动绑定；
- 针对 Golden Case 写死答案、参数或调用路径的 Prompt。

原始 Ragent 已有的对话记忆、问题改写、意图识别、KB 歧义引导、多工具并行和错误处理必须保留，不能为了制造差异而关闭。Baseline 是否会自然追问、利用历史或完成多步任务，交给实验判断。

---

## 2. Identity & Time Contract

### 2.1 Case 级可信身份

每条 Golden Case 同时声明：

```text
fixture_id
principal_id
```

示例映射：

| Case | principal_id | 目的 |
|---|---|---|
| KB-01 | U001 | 公共知识问题，身份不参与答案 |
| TOOL-01 | U001 | 查询 U001 的订单 |
| AMB-01 | U002 | U002 在目标时间内有两块手表 |
| DEP-01 | U003 | U003 在目标时间内只有一块手表 |

`principal_id` 必须由可信认证上下文提供。LLM 不能生成、覆盖或从用户自然语言中猜测该值。

需要区分两层参数：

```json
{
  "modelArguments": {
    "category": "wearable",
    "timeRange": "LAST_MONTH"
  },
  "runtimeArguments": {
    "userId": "U003",
    "category": "wearable",
    "timeRange": "LAST_MONTH"
  }
}
```

参数来源枚举冻结为：

```text
AUTH_CONTEXT
USER_QUERY
CONVERSATION_CONTEXT
TOOL_RESULT
DEFAULT
```

V1.1 的 Baseline 只使用原系统已有的上下文能力；`TOOL_RESULT` 到下游参数的自动绑定属于待验证能力，不能在 Adapter 中实现。

用户私有数据 Tool 的身份规则：

| Tool | 身份作用 |
|---|---|
| getOrder | runtime 注入 userId，并限定或核对订单归属 |
| getLogistics | runtime 注入 userId，并核对订单归属 |
| getCouponEligibility | runtime 注入 userId |
| getWarrantyStatus | runtime 注入 userId，并核对设备归属 |
| getRepairProgress | runtime 注入 userId，并核对维修单归属 |
| searchProducts | 公共查询，不需要 userId |
| getProductAvailability | 公共查询，不需要 userId |
| getSparePartPrice | 公共查询，不需要 userId |

### 2.2 固定实验时钟

所有运行固定：

```text
as_of = 2026-09-14T10:00:00+08:00
timezone = Asia/Shanghai
```

“上个月”解析为半开区间：

```text
[2026-08-01T00:00:00+08:00,
 2026-09-01T00:00:00+08:00)
```

V1.1 的时间处理链固定为：

```text
用户表达“上个月”
  → 参数抽取为 LAST_MONTH
  → Huawei Mall MCP Adapter 中的确定性 TemporalNormalizer
  → 使用 fixed Clock 生成 startTime/endTime
  → 业务查询服务只接收绝对时间边界
```

LLM 禁止自行计算绝对日期。业务查询服务不解析自然语言时间。TemporalNormalizer 是确定性的业务适配设施，不保存会话状态、不读取 Tool 结果、不决定调用哪个 Tool，因此允许进入 Baseline。

V1.1 的 `timeRange` 仅支持：

```text
LAST_MONTH
```

后续新增其他相对时间枚举必须提升契约版本。

---

## 3. Product、KB 与 Intent Resource Contract

### 3.1 产品范围

V1.1 只覆盖：

```text
phone
tablet
laptop
wearable
headphone
router
```

不覆盖鸿蒙智行、全屋智能、智慧屏、门锁和第三方商家复杂体系。

Business Contract 只冻结品类和字段语义。具体 SPU、主键和数量由 Fixture Freeze 固定；“30～60 个商品”只是实现规模，不是最终实验数据版本。

### 3.2 KB

只建四个 KB：

| KB ID | 内容 |
|---|---|
| product-kb | 产品能力、特点、适用人群、兼容性与稳定规格知识 |
| shopping-policy-kb | 优惠券、发票、配送、退换政策 |
| after-sales-kb | 保修政策、维修规则、服务方式与服务中心知识 |
| support-kb | 使用说明、故障排查、连接网络与充电电池知识 |

产品类别通过 metadata 表达；After-sales 通过 `type=warranty/repair/service` 等 metadata 表达。KB 不保存用户订单、个人保修状态、实时库存和当前维修进度。

### 3.3 Intent 到资源映射

| L1 | L2 Intent | Resource | KB | Tool | 说明 |
|---|---|---|---|---|---|
| PRODUCT | PRODUCT_INFO | KB | product-kb | — | 产品稳定知识 |
| PRODUCT | PRODUCT_COMPARE | KB | product-kb | — | 产品比较 |
| PRODUCT | PRODUCT_RECOMMEND | MIXED | product-kb | searchProducts | Tool 筛候选，KB 解释适用性 |
| PRODUCT | PRODUCT_COMPATIBILITY | KB | product-kb | — | 兼容性知识 |
| SHOPPING_POLICY | COUPON_POLICY | KB | shopping-policy-kb | — | 通用优惠券政策 |
| SHOPPING_POLICY | INVOICE_POLICY | KB | shopping-policy-kb | — | 发票政策 |
| SHOPPING_POLICY | DELIVERY_POLICY | KB | shopping-policy-kb | — | 配送政策 |
| SHOPPING_POLICY | RETURN_POLICY | KB | shopping-policy-kb | — | 退换政策 |
| ORDER | ORDER_QUERY | MCP | — | getOrder | 私有订单查询 |
| ORDER | LOGISTICS_QUERY | MCP | — | getLogistics | 私有物流查询 |
| ORDER | ORDER_CHANGE | SYSTEM | — | — | V1.1 不执行修改，引导到官方入口 |
| AFTER_SALES | WARRANTY_POLICY | KB | after-sales-kb | — | 通用保修政策 |
| AFTER_SALES | WARRANTY_STATUS | MCP | — | getWarrantyStatus | 某设备的个人保修状态 |
| AFTER_SALES | SPARE_PART_PRICE | MCP | — | getSparePartPrice | 动态精确价格，V1.1 不强行混合 KB |
| AFTER_SALES | REPAIR_SERVICE | KB | after-sales-kb | 维修服务规则 |
| AFTER_SALES | REPAIR_PROGRESS | MCP | — | getRepairProgress | 私有维修进度 |
| AFTER_SALES | SERVICE_CENTER | KB | after-sales-kb | 服务中心稳定知识 |
| SUPPORT | USAGE_GUIDE | KB | support-kb | 使用说明 |
| SUPPORT | TROUBLESHOOTING | KB | support-kb | 故障排查 |
| SUPPORT | CONNECTION_NETWORK | KB | support-kb | 连接与网络 |
| SUPPORT | CHARGING_BATTERY | KB | support-kb | 充电与电池 |

当前 Ragent 的物理意图节点一次只能配置一种 `kind`。`PRODUCT_RECOMMEND` 可以实现为共享同一逻辑 L2 编码的 KB/MCP 配对叶节点，或等价的双资源配置；评测只认逻辑意图和实际资源使用，不绑定具体类名或内部节点数量。

---

## 4. Tool Contract

### 4.1 通用约定

第一版只提供 8 个只读 Tool：

```text
searchProducts
getProductAvailability
getOrder
getLogistics
getCouponEligibility
getWarrantyStatus
getSparePartPrice
getRepairProgress
```

不提供 `cancelOrder`、`modifyOrder` 和 `createRepairRequest`。

金额统一使用十进制定点数，两位小数，币种固定为 `CNY`。时间统一输出 ISO 8601 且带 `+08:00`。ID、SN、订单号和维修单号均为区分大小写的非空字符串，最大长度 64。数组输入去重后最多 20 项。

地区枚举冻结为：

```text
CN_DEFAULT
BEIJING
SHANGHAI
GUANGZHOU
```

工具返回统一 JSON 文本，同时保存同内容的机器可读观测事件：

```json
{
  "status": "OK",
  "data": {},
  "candidates": [],
  "error": null,
  "meta": {
    "asOf": "2026-09-14T10:00:00+08:00",
    "timezone": "Asia/Shanghai",
    "datasetVersion": "huawei-mall-fixture-v1.0"
  }
}
```

状态语义：

| status | 语义 | MCP isError |
|---|---|---|
| OK | 查询成功，结果满足契约 | false |
| VALIDATION_ERROR | 缺参、类型、格式、枚举或组合规则错误 | true |
| NOT_FOUND | 合法查询没有匹配数据 | false |
| MULTIPLE_MATCHES | 目标查询得到多个合法候选 | false |
| CONFLICT | 同时提供的条件分别有效，但相互矛盾 | true |
| INTERNAL_ERROR | Tool 内部异常或依赖设施故障 | true |

`NOT_FOUND` 和 `MULTIPLE_MATCHES` 是业务结果，不伪装成系统异常。`error` 仅在 `VALIDATION_ERROR`、`CONFLICT`、`INTERNAL_ERROR` 时非空，包含稳定 `code`、简短 `message` 和可选 `fieldErrors`；不返回堆栈、SQL、密钥或内部路径。

### 4.2 searchProducts

模型输入：

| 参数 | 类型 | 必填 | 约束 | 来源 |
|---|---|---|---|---|
| category | string | 是 | 六类产品枚举 | USER_QUERY |
| budgetMin | number | 否 | ≥0；与 budgetMax 同时存在时不得大于它 | USER_QUERY |
| budgetMax | number | 否 | ≥0 | USER_QUERY |
| usage | string[] | 否 | V1.1 仅支持 laptop 固定枚举 | USER_QUERY |
| requirements | string[] | 否 | V1.1 仅支持 laptop 固定枚举 | USER_QUERY |

laptop 的枚举：

```text
usage = coding | office | business_travel | video_editing | study
requirements = lightweight | long_battery | high_memory | large_screen | portable
```

非 laptop 查询携带 `usage` 或 `requirements` 时返回 `VALIDATION_ERROR/UNSUPPORTED_FILTER`。Tool 只使用固定映射查询 `t_product.spec_json/category/current_price`，不能调用 LLM。

`OK.data`：

```text
products[]:
  productId, productName, category, series, model,
  currentPrice, currency, keySpecs, matchedConstraints
```

候选列表为空时返回 `NOT_FOUND`。搜索类 Tool 返回多个商品属于正常 `OK`，不返回 `MULTIPLE_MATCHES`。

### 4.3 getProductAvailability

模型输入：

| 参数 | 类型 | 必填 | 约束 | 来源 |
|---|---|---|---|---|
| productId | string | 是 | 必须存在于 t_product | USER_QUERY/CONVERSATION_CONTEXT |
| region | string | 否 | 地区枚举；默认 CN_DEFAULT | USER_QUERY/DEFAULT |

`OK.data`：

```text
productId, productName, region, currentPrice, originalPrice,
stock, stockStatus, promotionSummary, updatedAt, currency
```

`CN_DEFAULT` 使用 Seed 中固定的全国 Mock 记录，不在运行时随机选择地区。商品或地区记录不存在时返回 `NOT_FOUND`。

### 4.4 getOrder

模型输入：

| 参数 | 类型 | 必填 | 约束 | 来源 |
|---|---|---|---|---|
| orderNo | string | 否 | 精确订单号 | USER_QUERY/CONVERSATION_CONTEXT |
| timeRange | string | 否 | V1.1 仅 LAST_MONTH | USER_QUERY |
| category | string | 否 | 六类产品枚举 | USER_QUERY |
| productKeyword | string | 否 | 1～100 字符 | USER_QUERY |

运行时必须从 `AUTH_CONTEXT` 注入 `userId`。因为可信身份始终存在，模型输入不再要求 `orderNo OR userId`；无筛选条件时表示查询当前用户的订单列表。

`OK.data`：

```text
orders[]:
  orderNo, orderStatus, createTime, totalAmount, currency,
  products[]:
    productId, productName, category, model, sn, quantity
  deliveryAddressMasked
```

规则：

- `orderNo` 唯一命中且其他条件一致：`OK`；
- `orderNo` 存在但归属其他 principal，或与同时提供的筛选条件冲突：`CONFLICT/ORDER_CONTEXT_MISMATCH`；
- 合法筛选无结果：`NOT_FOUND`；
- 非精确订单号查询得到一条：`OK`；得到多条：`MULTIPLE_MATCHES`，候选放入 `candidates`，字段与 orders 项一致。

### 4.5 getLogistics

模型输入：

| 参数 | 类型 | 必填 | 来源 |
|---|---|---|---|
| orderNo | string | 是 | USER_QUERY/CONVERSATION_CONTEXT/TOOL_RESULT |

运行时注入 `userId` 并核对订单归属。

`OK.data`：

```text
orderNo, carrier, trackingNo, status, estimatedDeliveryTime,
events[]: time, location, description
```

所有字段必须来自固定数据表或文档化的确定性派生规则。订单不存在返回 `NOT_FOUND`；订单不属于当前 principal 返回 `CONFLICT/ORDER_CONTEXT_MISMATCH`。

### 4.6 getCouponEligibility

模型输入：

| 参数 | 类型 | 必填 | 约束 | 来源 |
|---|---|---|---|---|
| couponId | string | 是 | 精确优惠券 ID | USER_QUERY/CONVERSATION_CONTEXT |
| orderNo | string | 条件必填 | 与 productIds 至少一个存在 | USER_QUERY/CONVERSATION_CONTEXT/TOOL_RESULT |
| productIds | string[] | 条件必填 | 与 orderNo 至少一个存在；非空 | USER_QUERY/CONVERSATION_CONTEXT/TOOL_RESULT |

运行时从 `AUTH_CONTEXT` 注入 `userId`。若 `orderNo` 与 `productIds` 同时提供，两者必须属于同一订单商品集合，否则返回 `CONFLICT/ORDER_PRODUCTS_MISMATCH`。

`OK.data`：

```text
eligible, couponId, reasonCode, reason, matchedRules[]
```

`reasonCode` 冻结为：

```text
ELIGIBLE
EXPIRED
PRODUCT_NOT_SUPPORTED
MIN_AMOUNT_NOT_MET
USER_NOT_ELIGIBLE
COUPON_ALREADY_USED
OTHER
```

`eligible=false` 是正常 `OK` 业务结果。优惠券、订单或商品不存在时返回 `NOT_FOUND`。

### 4.7 getWarrantyStatus

模型输入：

| 参数 | 类型 | 必填 | 来源 |
|---|---|---|---|
| sn | string | 是 | USER_QUERY/CONVERSATION_CONTEXT/TOOL_RESULT |

运行时注入 `userId` 并核对设备归属。产品名称不能代替 SN。

`OK.data`：

```text
sn, productId, productName, model,
warrantyStatus, warrantyStart, warrantyEnd, warrantyType
```

`warrantyStatus`：

```text
IN_WARRANTY | OUT_OF_WARRANTY | UNKNOWN
```

状态只能根据固定 Clock 和 `t_device` 字段确定。SN 不存在返回 `NOT_FOUND`；设备不属于当前 principal 返回 `CONFLICT/DEVICE_CONTEXT_MISMATCH`。

### 4.8 getSparePartPrice

模型输入：

| 参数 | 类型 | 必填 | 约束 | 来源 |
|---|---|---|---|---|
| productId | string | 条件必填 | 与 model 至少一个存在 | USER_QUERY/CONVERSATION_CONTEXT/TOOL_RESULT |
| model | string | 条件必填 | 与 productId 至少一个存在 | USER_QUERY/CONVERSATION_CONTEXT/TOOL_RESULT |
| part | string | 是 | 固定枚举 | USER_QUERY |
| region | string | 否 | 默认 CN_DEFAULT | USER_QUERY/DEFAULT |

`part`：

```text
screen | battery | mainboard | back_cover | camera | other
```

`productId` 与 `model` 同时提供时必须指向同一 SPU，否则返回 `CONFLICT/PRODUCT_MODEL_MISMATCH`。model 命中多个 SPU 时返回 `MULTIPLE_MATCHES`。

`OK.data`：

```text
productId, productName, model, part, partName,
partPrice, laborFee, estimatedTotal, currency, priceNote, updatedAt, region
```

统一计算：

```text
estimatedTotal = partPrice + laborFee
```

三项金额均来自固定数据或确定性计算，不临时增加其他费用。

### 4.9 getRepairProgress

模型输入：

| 参数 | 类型 | 必填 | 约束 | 来源 |
|---|---|---|---|---|
| serviceNo | string | 条件必填 | 与 phone 至少一个存在 | USER_QUERY/CONVERSATION_CONTEXT |
| phone | string | 条件必填 | 中国大陆手机号格式 `^1[3-9][0-9]{9}$` | USER_QUERY/CONVERSATION_CONTEXT |

运行时注入 `userId`。两者同时提供时必须指向同一维修单，否则返回 `CONFLICT/REPAIR_CONTEXT_MISMATCH`。phone 查询多笔维修单时返回 `MULTIPLE_MATCHES`。

`OK.data`：

```text
serviceNo, productName, repairStatus, currentStep,
progressEvents[]: time, status, description,
estimatedCompleteTime, completedAt, contactMasked
```

`repairStatus`：

```text
RECEIVED
INSPECTING
WAITING_FOR_CONFIRMATION
REPAIRING
COMPLETED
SHIPPING_BACK
CLOSED
```

---

## 5. Data & Fixture Contract

### 5.1 数据模型

保留十张核心表：

```text
t_product
t_user
t_order
t_order_item
t_device
t_inventory
t_coupon
t_user_coupon
t_repair_order
t_spare_part_price
```

如果 `getLogistics.events` 和 `getRepairProgress.progressEvents` 无法由现有字段确定性表达，允许增加：

```text
t_logistics_event
t_repair_progress_event
```

这两张表只用于补齐已冻结输出的数据来源，不扩展业务能力。是否增加必须在 DDL Audit 时确定，并写入 Fixture manifest。

### 5.2 关键数据规则

- 带 SN 的 `t_order_item` 在 V1 Fixture 中固定 `quantity=1`；未来数量大于 1 时由 `t_device.order_item_id` 建立一对多关系。
- 所有 Tool 输出字段必须映射到表字段、固定配置或文档化确定性计算。
- 禁止运行时随机生成价格、库存、订单、保修期、物流事件和维修进度。
- `model` 在 Normal Tool Case 中必须唯一。TOOL-04 问题固定为“MateBook X Pro 2026 换屏多少钱？”。
- AMB fixture 与 DEP fixture 使用不同 principal 或互不冲突的数据前提。

### 5.3 Fixture Freeze 产物

实现后必须形成：

```text
fixtures/
├── seed.sql
├── case_facts.json
├── manifest.json
└── README.md
```

`manifest.json` 至少包含：

```json
{
  "datasetVersion": "huawei-mall-fixture-v1.0",
  "asOf": "2026-09-14T10:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "seedSha256": "TO_BE_FILLED_AT_FIXTURE_FREEZE",
  "caseFactsSha256": "TO_BE_FILLED_AT_FIXTURE_FREEZE",
  "rowCounts": {
    "t_product": 0,
    "t_user": 0,
    "t_order": 0
  }
}
```

上面的 0 是 Schema 占位值，不能作为冻结后的有效 manifest。Fixture Freeze 时必须写入实际行数及哈希。

每次正式运行前执行：

```text
重置专用 Mock schema
  → 执行固定 seed.sql
  → 校验 SHA-256
  → 校验所有表 row count
  → 校验 case_facts 中的必要事实
  → 全部通过后开始评测
```

不得删除或重置 Ragent 的会话、用户、知识及其他非 Huawei Mall Mock 数据。

---

## 6. Golden Case Contract

### 6.1 分类

```text
5 KB
5 Normal Tool
4 Missing
3 Ambiguous
3 Dependency
Total = 20
```

分类语义：

- Missing：没有可用历史，也没有唯一、可用的 Tool Dependency 能补齐；正确首轮行为通常是追问。
- Ambiguous：系统拥有多个合法候选，不能静默随机选择。
- Dependency：上游数据唯一，理论上可以自动获得下游必需参数；若系统选择追问，仍可在用户补参后完成任务，但自动依赖指标失败。
- Normal Tool：问题中的模型可见参数完整且唯一，不应因 Fixture 自身产生歧义。

### 6.2 AMB-03 前置历史

八个 Tool 中没有 `listUserCoupons`。AMB-03 使用固定、完整的前置对话：

```text
User: 我账户里有哪些还没使用的优惠券？
Assistant: 你账户中有两张未使用优惠券：C001 满3000减300；C002 满5000减600。
User: 为什么我的优惠券不能用？
```

“未使用”不等于当前订单满足使用条件。前两条消息属于 Fixture，不计入当前 Case 的模型表现。Agent 应询问具体是哪一张券，以及缺少时询问使用对象。

### 6.3 正式 Case Schema

```json
{
  "case_id": "DEP-01",
  "category": "DEPENDENCY",
  "fixture_id": "fixture_dep_watch_single",
  "principal_id": "U003",
  "as_of": "2026-09-14T10:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "history": [],
  "question": "我上个月买的手表还在不在保？",
  "expected_intents": ["WARRANTY_STATUS"],
  "expected_kbs": [],
  "tool_expectation": {
    "required_calls": [
      {"tool": "getOrder"},
      {"tool": "getWarrantyStatus"}
    ],
    "must_happen_before": [
      ["getOrder", "getWarrantyStatus"]
    ],
    "argument_sources": {
      "getOrder.userId": "AUTH_CONTEXT",
      "getOrder.timeRange": "USER_QUERY",
      "getWarrantyStatus.sn": "TOOL_RESULT:getOrder.orders[*].products[*].sn"
    },
    "allowed_alternatives": []
  },
  "clarification_script": [
    {
      "when_missing": "sn",
      "user_reply": "SN 是 WATCH-SN-003。"
    }
  ],
  "forbidden_behaviors": [
    "invent_sn",
    "fabricate_tool_result",
    "answer_without_evidence"
  ],
  "termination": {
    "must_contain_warranty_status": true
  },
  "max_turns": 2
}
```

`required_calls` 和 `must_happen_before` 用于评价自动依赖能力。若 Baseline 首轮追问 SN，runner 按 `clarification_script` 回答；此时可以得到：

```text
task_completion = PASS
automatic_dependency_resolution = FAIL
turn_count = 2
```

追问本身不是禁止行为。评测检查的是必要事实来源和用户交互成本，不要求系统使用 `DAGExecutor.java` 或任何特定实现。

对于存在合法替代路径的 Case，必须在 `allowed_alternatives` 中显式列出。未声明时不得在评分阶段临时改变 Gold。

---

## 7. Evaluation Contract

每个 Case 至少记录：

```text
SETUP_STATUS
FIRST_TURN_BEHAVIOR
TASK_COMPLETION
TOOL_SELECTION
TOOL_ARGUMENTS
ENTITY_RESOLUTION
DEPENDENCY_USAGE
HALLUCINATION
TURN_COUNT
CALL_COUNT
LATENCY
```

判定规则：

- `SETUP_STATUS != PASS` 时，本次样本不进入 Agent 能力分母，单独报告基础设施、数据、Adapter 或证据缺失问题。
- SSE 正常结束只表示传输完成，不等于 `TASK_COMPLETION=PASS`。
- 正确追问可使 `FIRST_TURN_BEHAVIOR=PASS`，即使当前轮没有最终答案。
- 用户补充后完成任务，可以使 `TASK_COMPLETION=PASS`；自动依赖、轮数和调用数仍独立计分。
- Tool 名、关键参数、返回、参数来源和调用次序必须来自同一次正式请求的观测，不从另一次重放推断。
- 依赖评价依据事实的先后关系和参数来源，不依据内部类名。
- 无依据选择多个候选之一，记为 `ENTITY_RESOLUTION=FAIL`；编造参数或事实另记 `HALLUCINATION=FAIL`。

失败类型冻结为：

```text
PARAMETER_MISSING
INVALID_PARAMETER
AMBIGUOUS_ENTITY
WRONG_TOOL
DEPENDENCY_MISSING
TOOL_ERROR_PROPAGATION
HALLUCINATED_FINAL_ANSWER
DATA_KB_COVERAGE
ADAPTER_TOOL_DEFECT
EVALUATION_EVIDENCE_DEFECT
```

记录一个有证据支持的主要原因，并可记录其后续影响，避免同一根因被重复计数。

这 20 条用于 Baseline 诊断和后续回归。一旦据此调试 Improved，它们就属于开发诊断集；正式泛化结论需要另留未参与调试的测试集。

---

## 8. Adapter 验收与执行顺序

### 8.1 开发顺序

```text
V1.1 Business Contract Freeze
  → 建立专用 Mock schema 与 DDL
  → 编写固定 Seed 数据
  → 实现身份注入与固定 Clock
  → 实现 8 个 Tool 及服务端校验
  → 直接接口契约测试
  → 建立 4 个 KB
  → 配置 Intent Resource Mapping
  → Agent 冒烟测试
  → 建立精简 huawei-mall-eval
  → Fixture Audit
  → Fixture Freeze
  → Business-adapted Baseline Freeze
  → 运行 20 条 Baseline Audit
  → Failure Analysis
  → 决定 Improved 的最小改造
```

### 8.2 Tool/Adapter 验收门槛

每个 Tool 在不依赖 LLM 的情况下验证：

- 合法输入与正确输出；
- 缺失必填参数；
- 非法类型、格式和枚举；
- 条件必填；
- 两个条件同时存在且一致；
- 两个条件冲突；
- 无结果；
- 多候选；
- principal 数据隔离；
- fixed Clock 与半开时间区间；
- 同一 Seed 和输入重复执行结果一致。

这些测试证明业务接入正确，不证明 Agent 会正确选 Tool、抽参数、澄清或使用依赖。

### 8.3 正式运行前门槛

只有同时满足以下条件才能运行 20 条：

1. 8 个 Tool 的契约测试通过；
2. 四个 KB 已入库并有确定文档映射；
3. Intent Resource Mapping 已配置并留存版本；
4. Fixture manifest 中不存在占位哈希和占位行数；
5. 每个 Case 的必要事实均通过机器校验；
6. 同次正式请求能够记录 Tool 名、参数、结果、顺序和检索证据；
7. Baseline 的代码、Prompt、工具、KB、Fixture、模型和评测器版本已写入运行 manifest。

---

## 9. V1.1 冻结结论

V1.1 已冻结：

```text
身份来源与 Case 级 principal
固定时钟与时间归一化位置
产品业务范围
四个 KB 的职责
Intent 到资源映射
八个只读 Tool 的输入、输出、校验与错误语义
十张核心表及可选事件表的引入条件
Golden Case 分类、Schema、多轮脚本和依赖表达
评分维度、失败类型和公平边界
Fixture Freeze 的产物与门槛
```

尚未冻结：

```text
实际 DDL
实际 Seed 行
最终商品和订单数量
case_facts 内容与哈希
KB 文档内容与哈希
Intent 配置实例 ID
业务适配 Baseline 的最终提交
```

这些项目必须在实现和审计后分别进入 Fixture Freeze 与 Business-adapted Baseline Freeze。

在这两个后续冻结点完成前，可以开始 Huawei Mall Adapter 开发，但不能运行并发布正式 Baseline Audit 结论。
