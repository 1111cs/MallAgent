# D2：Tool-scoped Entity Resolution with Trusted Candidate Selection

## 1. 目标

D2 只解决 Baseline 已被 Trace 直接证明的两类问题：

1. 用户提供自然语言实体名称，但系统无法稳定映射到 Tool 所需的标准业务 ID；
2. 当前用户范围内存在多个合法候选时，系统没有候选发现和选择机制，而是直接退化成缺参或错误 Tool 调用。

D2 不建设通用 NER、知识图谱、向量实体库或通用实体理解平台。

D2 的目标链路为：

```text
用户问题
  ↓
Intent / Tool 已确定
  ↓
LLM 参数提取
  ↓
Tool-scoped Entity Resolution
  ↓
┌──────────────────────────┐
│ UNIQUE                   │
│ → 标准化并绑定参数         │
│                          │
│ AMBIGUOUS                │
│ → NEED_SELECTION         │
│                          │
│ NOT_FOUND                │
│ → 保持 unresolved        │
└──────────────────────────┘
  ↓
MissingParameterGate
  ↓
READY / NEED_PARAMETERS
```

D2 必须复用 D1 已建立的 Pending、Clarification 与 MissingParameterGate，不另建一套任务恢复体系。

------

## 2. 核心原则

### 2.1 Entity Resolution 必须由 Tool 和 Parameter 限定

D2 不允许进行全局实体搜索。

同一个自然语言表达在不同 Tool 参数下，对应的合法实体空间不同。

例如：

```text
getSparePartPrice.productId
→ 标准商品目录
→ 或当前用户设备绑定的标准商品

getWarrantyStatus.sn
→ 只能来自当前登录用户拥有的设备

getCouponEligibility.couponId
→ 只能来自当前登录用户拥有的优惠券
```

因此实体解析请求定义为：

```java
EntityResolutionRequest {
    String toolId;
    String parameter;
    String expression;
    String authenticatedUserId;
}
```

EntityResolver 不是回答：

> “这个实体是什么？”

而是回答：

> “对于当前 Tool 的这个参数，这段用户表达能否映射到一个合法业务实体？”

------

## 3. 数据域与可信边界

D2 的候选实体必须来自可信业务数据。

禁止：

```text
LLM 自己生成 sn
LLM 自己生成 productId
LLM 自己生成 couponId
```

然后直接作为 Tool 参数使用。

正确流程：

```text
用户自然语言
   ↓
理解表达
   ↓
限定 Tool + Parameter
   ↓
查询合法候选域
   ↓
匹配 / 唯一化
   ↓
得到标准业务 ID
```

核心原则：

> LLM 可以帮助理解表达，但实体事实必须经过真实业务数据验证。

------

## 4. EntityCandidateProvider

Agent Core 不直接访问 Huawei Mall 数据表。

新增一个很薄的候选提供接口：

```java
EntityCandidateProvider {
    List<EntityCandidate> findProducts(...);
    List<EntityCandidate> findOwnedDevices(...);
    List<EntityCandidate> findOwnedCoupons(...);
}
```

第一版实现可以直接复用当前 Fixture Repository。

Agent 核心只依赖接口，不依赖具体数据源。

未来候选来源即使替换成：

```text
MCP
REST API
Remote Service
Database Adapter
```

EntityResolver 与消歧逻辑无需修改。

------

## 5. EntityCandidate

候选实体结构：

```java
EntityCandidate {
    String candidateId;
    String displayLabel;
    Map<String, Object> bindings;
}
```

例如设备候选：

```json
{
  "candidateId": "DEVICE-001",
  "displayLabel": "HUAWEI WATCH GT 6 黑色",
  "bindings": {
    "sn": "WATCH-SN-001",
    "productId": "P-WATCH-001"
  }
}
```

优惠券候选：

```json
{
  "candidateId": "USER-COUPON-001",
  "displayLabel": "MateBook 满5000减500优惠券",
  "bindings": {
    "couponId": "C001"
  }
}
```

候选可以携带多个标准字段，但进入具体 Tool 时：

```text
只能读取当前 Tool / Parameter 所需字段
```

禁止把整个 `bindings` 无条件写入 Tool 参数。

------

## 6. EntityResolver

新增：

```java
EntityResolver
```

输入：

```java
EntityResolutionRequest
```

输出：

```java
EntityResolutionResult {
    EntityResolutionStatus status;
    EntityCandidate selected;
    List<EntityCandidate> candidates;
}
```

状态：

```text
UNIQUE
AMBIGUOUS
NOT_FOUND
```

第一版匹配策略保持简单、确定：

```text
1. exact ID match
2. normalized exact name match
3. configured alias match
4. category / type constrained match
```

暂不使用：

```text
Embedding Entity Search
知识图谱
复杂 Ontology
LLM 直接生成标准 ID
```

------

## 7. Tool-scoped Normalization

D2-A 优先解决已知的一对一标准化。

例如：

```text
MateBook X Pro
      ↓
getSparePartPrice.productId
      ↓
商品域
      ↓
唯一匹配
      ↓
P-LAPTOP-001
```

最终记录：

```text
productId = P-LAPTOP-001
source = ENTITY_RESOLUTION
```

该能力首先用于修复：

```text
TOOL-04
```

目标：

```text
MateBook X Pro
→ P-LAPTOP-001
→ getSparePartPrice
→ 返回 Golden 价格
```

------

## 8. Ambiguity Resolution

如果候选集合只有一个：

```text
UNIQUE
→ 直接绑定
```

如果候选数量大于 1：

```text
AMBIGUOUS
→ 不选择
→ 不执行终态 Tool
→ NEED_SELECTION
```

例如：

```text
“我的手表还在不在保？”
        ↓
tool = getWarrantyStatus
parameter = sn
        ↓
当前用户设备域
        ↓
WATCH-SN-001
WATCH-SN-002
        ↓
AMBIGUOUS
        ↓
NEED_SELECTION
```

系统必须向用户列出真实候选。

例如：

> 您名下有两块手表，请选择要查询的设备：
>
> 1. HUAWEI WATCH GT 6 黑色
> 2. HUAWEI WATCH GT 6 Pro 银色

在用户选择之前：

```text
getWarrantyStatus 调用次数必须为 0
```

------

## 9. PendingReason

D2 不新建独立 Pending Store。

扩展 D1 的 Pending：

```java
PendingReason {
    NEED_PARAMETERS,
    NEED_SELECTION
}
```

D1：

```text
NEED_PARAMETERS
```

D2：

```text
NEED_SELECTION
```

这样整个系统只有一套 Pending Task。

------

## 10. PendingClarificationState 扩展

在现有状态基础上增加：

```java
String selectionTarget;
String entityType;
List<EntityCandidate> candidates;
PendingReason pendingReason;
```

例如：

```json
{
  "pendingReason": "NEED_SELECTION",
  "selectionTarget": "sn",
  "entityType": "DEVICE",
  "candidates": [
    {
      "candidateId": "DEVICE-001",
      "displayLabel": "HUAWEI WATCH GT 6 黑色"
    },
    {
      "candidateId": "DEVICE-002",
      "displayLabel": "HUAWEI WATCH GT 6 Pro 银色"
    }
  ]
}
```

Pending 中保存的是：

```text
候选快照
```

不是最终执行凭证。

------

## 11. Selection Resume

当存在：

```text
PendingReason = NEED_SELECTION
```

时，下一轮用户输入优先进入选择恢复逻辑。

例如：

> 第二台。

流程：

```text
Pending 存在
   ↓
Selection Resolver
   ↓
匹配 Pending 中的 candidateId / index / label
   ↓
确认用户选择的是候选列表中的成员
   ↓
重新验证候选
   ↓
绑定标准参数
```

不能直接：

```text
第二台
→ candidates[1]
→ Tool Execute
```

必须经过重新验证。

------

## 12. 候选重新验证

选择成功后、进入 Tool 之前，必须再次确认：

```text
当前 authenticatedUserId
仍然拥有该设备 / 优惠券
候选仍然有效
候选 ID 属于原 Pending 候选集合
```

流程：

```text
用户选择
   ↓
校验 selection 属于 Pending candidates
   ↓
EntityCandidateProvider 重新查询
   ↓
当前身份下重新确认
   ↓
VALID
   ├─ YES → 生成 bindings
   └─ NO  → 重新候选发现 / 返回实体已失效
```

这样可以防止：

```text
Pending 过期
身份变化
候选失效
伪造 candidateId
```

------

## 13. Selection 后的参数绑定

候选验证通过后：

```text
bindings
  ↓
只读取当前 Tool 需要的字段
```

例如：

```text
tool = getWarrantyStatus
parameter = sn
```

候选：

```json
{
  "bindings": {
    "sn": "WATCH-SN-001",
    "productId": "P-WATCH-001"
  }
}
```

实际只绑定：

```text
sn = WATCH-SN-001
```

并记录：

```text
ParameterSource = ENTITY_RESOLUTION
```

不自动把 `productId` 一并灌入 Tool。

------

## 14. 与 D1 的协同

D2 完成实体解析或用户选择后，必须重新进入：

```text
MissingParameterGate
```

而不是直接调用 Tool。

流程：

```text
Entity Resolution
   ↓
绑定标准参数
   ↓
MissingParameterGate
   ├─ READY
   │    ↓
   │  Tool Execute
   │
   └─ MISSING
        ↓
      D1
        ↓
      NEED_PARAMETERS
```

------

## 15. AMB-03 作为 D1 + D2 联合用例

AMB-03：

> 为什么我的优惠券不能用？

当前用户存在两张优惠券：

```text
→ AMBIGUOUS
→ NEED_SELECTION
```

第一轮：

```text
列出两张真实优惠券
```

用户：

> 第一张。

D2：

```text
couponId = C001
source = ENTITY_RESOLUTION
```

然后重新 Gate：

```text
couponId ✅

orderNo / productIds ❌
```

因此：

```text
PendingReason
NEED_SELECTION
        ↓
NEED_PARAMETERS
```

D1 接管：

> 请再告诉我这张优惠券想用于哪笔订单或哪些商品。

用户：

> 用于订单 202609140001。

再 Gate：

```text
READY
```

最终：

```text
getCouponEligibility
```

所以 AMB-03 至少是三轮任务：

```text
原问题
→ 选择优惠券
→ 补充订单
→ Tool
```

该 Case 是 D1 与 D2 协同工作的核心验收用例。

------

## 16. Selection Prompt

D2 不让 LLM 自由生成候选。

候选列表必须由真实 Candidate 数据渲染。

例如：

```text
您名下有两块设备，请选择：
1. HUAWEI WATCH GT 6 黑色
2. HUAWEI WATCH GT 6 Pro 银色
```

Prompt Builder 只负责渲染：

```text
candidate displayLabel
```

禁止把内部 ID：

```text
WATCH-SN-001
P-WATCH-001
C001
```

作为唯一面向用户的信息。

内部 ID 可以用于 Trace 与执行，不应成为默认交互文案。

------

## 17. Trace 最小扩展

继续复用 Checkpoint B/C 的 `ExecutionRecord`。

只增加：

```text
entityResolution.status
entityResolution.target
entityResolution.candidateCount
selectedCandidateId
```

例如第一轮：

```json
{
  "entityResolution": {
    "status": "AMBIGUOUS",
    "target": "sn",
    "candidateCount": 2
  },
  "taskStatus": "NEED_SELECTION"
}
```

第二轮：

```json
{
  "entityResolution": {
    "status": "UNIQUE",
    "target": "sn",
    "candidateCount": 2,
    "selectedCandidateId": "DEVICE-001"
  }
}
```

参数：

```json
{
  "sn": {
    "value": "WATCH-SN-001",
    "source": "ENTITY_RESOLUTION"
  }
}
```

不新建 Trace 体系。

------

## 18. D2 最小组件

D2 建议控制在：

```text
EntityResolutionRequest
EntityResolutionResult
EntityResolutionStatus
EntityCandidate
EntityResolver
EntityCandidateProvider
SelectionResolver
```

复用已有：

```text
PendingClarificationState
PendingClarificationStore
MissingParameterGate
ClarificationPromptBuilder
ParameterSource.ENTITY_RESOLUTION
```

不建设：

```text
通用 NER
向量实体库
知识图谱
Entity Memory Platform
LLM Autonomous Entity Generator
复杂 Ontology
```

------

## 19. D2 实施阶段

### D2-A：Tool-scoped Entity Normalization

目标：

```text
标准自然语言名称
→ Tool-scoped 数据域
→ 唯一标准 ID
```

重点修复：

```text
TOOL-04
```

成功标准：

```text
MateBook X Pro
→ productId = P-LAPTOP-001
→ source = ENTITY_RESOLUTION
→ getSparePartPrice
→ Golden PASS
```

------

### D2-B：Trusted Candidate Discovery

实现：

```text
EntityCandidateProvider
EntityResolver
AMBIGUOUS
NEED_SELECTION
```

目标：

```text
AMB-01～03
能够发现多个真实候选
```

并保证：

```text
未选择前终态 Tool 调用次数 = 0
```

------

### D2-C：Selection Resume

实现：

```text
用户选择
→ 只允许 Pending 中候选
→ 当前身份下重新验证
→ 标准参数绑定
→ ENTITY_RESOLUTION
→ MissingParameterGate
```

如果 Gate：

```text
READY
→ Tool
```

如果：

```text
MISSING
→ 回落 D1
```

完成后才宣布 D2 Passed。

------

## 20. D2 Golden 验收

### TOOL-04

单轮：

```text
MateBook X Pro 换屏多少钱？
```

期望：

```text
EntityResolution = UNIQUE
productId = P-LAPTOP-001
source = ENTITY_RESOLUTION
expected Tool executed
Golden price returned
```

------

### AMB-01

Round A：

```text
我的手表还在不在保？
```

期望：

```text
entityResolution.status = AMBIGUOUS
candidateCount = 2
taskStatus = NEED_SELECTION
getWarrantyStatus 调用次数 = 0
```

Round B：

```text
第二块
```

期望：

```text
selectedCandidateId 合法
候选重新验证 PASS
sn source = ENTITY_RESOLUTION
getWarrantyStatus executed once
```

------

### AMB-02

Round A：

```text
我的笔记本换屏多少钱？
```

期望：

```text
AMBIGUOUS
candidateCount = 2
NEED_SELECTION
getSparePartPrice 调用次数 = 0
```

Round B：

```text
第一台
```

期望：

```text
candidate revalidated
productId source = ENTITY_RESOLUTION
getSparePartPrice executed once
```

------

### AMB-03

Round A：

```text
为什么我的优惠券不能用？
```

期望：

```text
AMBIGUOUS
candidateCount = 2
NEED_SELECTION
终态 Tool = 0
```

Round B：

```text
第一张
```

期望：

```text
couponId source = ENTITY_RESOLUTION
PendingReason 从 NEED_SELECTION
→ NEED_PARAMETERS
仍不执行终态 Tool
```

Round C：

```text
用于订单 202609140001
```

期望：

```text
Gate = READY
AUTH_CONTEXT 重新注入
getCouponEligibility executed once
Pending cleared
```

------

## 21. 回归约束

D2 完成后必须保持：

```text
MISS-01～04 全部 PASS
Normal Tool 已通过 Case 不回退
D1 Pending Resume 继续有效
显式取消继续有效
AUTH_CONTEXT 每轮重新绑定
```

禁止出现：

```text
为了修 Ambiguous
→ 破坏 Missing
```

------

## 22. D2 完成标准

D2 最终验收标准冻结为：

```text
TOOL-04：FAIL → PASS

AMB-01：PASS
AMB-02：PASS
AMB-03：PASS

3/3 Ambiguous Case 在选择前终态 Tool 调用 = 0

3/3 候选来自可信 EntityCandidateProvider

3/3 用户只能选择 Pending 中真实候选

3/3 选择后重新验证当前身份与候选有效性

实体绑定参数来源均记录为 ENTITY_RESOLUTION

未经候选验证的 LLM 生成实体 ID 接受次数 = 0

MISS-01～04 不回退

无 runtime proof / token / secret 泄露
```

------

## 23. D2 明确边界

D2 支持：

```text
Tool-scoped Entity Normalization
可信业务候选发现
唯一实体自动绑定
多候选选择
候选重新验证
Selection Resume
回落 D1 继续补参
```

D2 暂不支持：

```text
跨 Tool Entity Discovery
Tool Dependency
模糊向量实体检索
自由文本实体记忆
用户主动纠正已绑定实体
多个实体同时联合消歧
跨会话候选恢复
复杂 Task Switching
```

上述能力进入后续 D3 或独立鲁棒性阶段。

------

## 24. D2 核心定位

D2 不是“让 LLM 更会识别实体”。

它解决的是：

```text
开放自然语言表达
        ↓
限定 Tool + Parameter
        ↓
真实业务候选域
        ↓
唯一？
 ├─ 是 → 标准参数
 └─ 否 → 显式选择
        ↓
重新验证
        ↓
可信参数绑定
```

即：

> **Tool-scoped Entity Resolution with Trusted Candidate Selection**

中文可概括为：

> **面向 Tool 参数的可信实体标准化与选择式消歧。**

D2 的核心价值不是“模型猜得更准”，而是：

> **系统只接受经过业务数据验证的实体；无法唯一确认时不猜、不执行，而是暂停任务并让用户选择。**