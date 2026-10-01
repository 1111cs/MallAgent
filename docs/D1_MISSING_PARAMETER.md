# D1：Missing Parameter Closed Loop 设计 V2

## 1. 目标

D1 只解决 Baseline 已由 Trace 直接证明的 Missing Parameter 问题：

```text
参数缺失
  ↓
Baseline 仍调用终态 Tool
  ↓
VALIDATION_ERROR / NOT_FOUND
  ↓
底层错误被转述给用户
  ↓
任务结束
```

D1 改造目标为：

```text
参数缺失
  ↓
Missing Parameter Gate
  ↓
阻断终态 Tool
  ↓
创建 Pending Task
  ↓
确定性澄清
  ↓
用户补参
  ↓
重新 Gate
  ├─ 仍缺参 → 更新 Pending，继续澄清
  └─ READY → 原子取得并清除 Pending
                ↓
           重新绑定 AUTH_CONTEXT
                ↓
             执行 Tool
```

D1 不处理 Ambiguous Entity、Tool Dependency、用户修改已有参数、多并发 Pending 等问题。

------

## 2. 设计原则

### 2D1：Missing Parameter Closed Loop 设计 V2

## 1. 目标

D1 只解决 Baseline 已由 Trace 直接证明的 Missing Parameter 问题：

```text
参数缺失
  ↓
Baseline 仍调用终态 Tool
  ↓
VALIDATION_ERROR / NOT_FOUND
  ↓
底层错误被转述给用户
  ↓
任务结束
```

D1 改造目标为：

```text
参数缺失
  ↓
Missing Parameter Gate
  ↓
阻断终态 Tool
  ↓
创建 Pending Task
  ↓
确定性澄清
  ↓
用户补参
  ↓
重新 Gate
  ├─ 仍缺参 → 更新 Pending，继续澄清
  └─ READY → 原子取得并清除 Pending
                ↓
           重新绑定 AUTH_CONTEXT
                ↓
             执行 Tool
```

D1 不处理 Ambiguous Entity、Tool Dependency、用户修改已有参数、多并发 Pending 等问题。

------

## 2. 设计原则

### 2.1 Missing 判断由 Schema 与 Adapter 共同定义

`ToolParameterContract` 不单独维护另一套普通必填规则，而由两部分合成：

```text
MCP Tool Schema.required
        +
Huawei Mall Adapter 业务补充约束
```

其中：

- 普通必填参数以 MCP Schema 为唯一来源；
- Schema 当前无法稳定表达的 `ANY_OF` 等业务关系，由 Huawei Mall Adapter 补充。

结构：

```java
ToolParameterContract {
    Set<String> requiredAll;
    List<RequiredAnyGroup> requiredAny;
}
```

其中：

```java
RequiredAnyGroup {
    Set<String> alternatives;
    String userFacingDescription;
}
```

例如维修进度：

```text
requiredAll = []

requiredAny =
[
  {
    alternatives = [serviceNo, phone],
    userFacingDescription = "维修服务单号或预约时填写的手机号"
  }
]
```

优惠券资格：

```text
requiredAll = [couponId]

requiredAny =
[
  {
    alternatives = [orderNo, productIds],
    userFacingDescription = "想使用优惠券的订单号或商品"
  }
]
```

这样避免 Schema 与 Adapter 维护两套相互漂移的普通 required 规则。

------

## 3. MissingParameterGate

Gate 只负责回答：

> 当前参数是否已经满足 Tool 的执行前置条件？

D1 阶段只判断“有没有”，不判断格式、语义合法性和实体真实性。

以下值统一视为未提供：

```text
null
""
"   "
empty collection
empty array
```

输出：

```java
ParameterCheckResult {
    boolean ready;
    Set<String> missingAll;
    List<RequiredAnyGroup> unsatisfiedAnyGroups;
}
```

流程：

```text
Tool Schema
   +
Adapter Contract
   ↓
ToolParameterContract
   +
当前 Arguments
   ↓
MissingParameterGate
   ├─ READY
   └─ MISSING
```

一旦为 `MISSING`：

```text
禁止调用终态 Tool
```

这是 D1 最核心的行为变化。

------

## 4. Clarification 文案生成

D1 不针对每种参数组合手写模板。

错误方案：

```text
缺 productId → 模板 A
缺 region → 模板 B
缺 productId + region → 模板 C
...
```

随着参数增加会产生组合爆炸。

改为：

```text
参数元数据
+
GateResult
+
组合式 Renderer
```

新增：

```java
ParameterDescriptor {
    String key;
    String displayName;
    String askPhrase;
}
```

例如：

```text
sn
→ 设备 SN
→ 请提供设备 SN

productId
→ 商品
→ 请提供具体商品或型号

region
→ 所在地区
→ 请提供所在地区

couponId
→ 优惠券
→ 请说明具体是哪张优惠券
```

`ClarificationPromptBuilder` 不认识具体 Tool，也不判断参数是否缺失，只负责把：

```text
missingAll
+
unsatisfiedAnyGroups
```

组合成自然语言。

例如：

```text
missingAll:
  productId
  region
```

自动生成：

> 请提供具体商品或型号，以及所在地区。

例如：

```text
missingAll:
  couponId

unsatisfiedAnyGroups:
  订单号或商品
```

生成：

> 请说明具体是哪张优惠券，以及想使用优惠券的订单号或商品。

这样实现复杂度由“参数组合数”降低为：

```text
参数数量 + requiredAny 组数量
```

------

## 5. PendingClarificationState

当 Gate 返回 `MISSING` 时，创建 Pending Task。

最小状态：

```java
PendingClarificationState {
    String pendingTaskId;
    String authenticatedUserId;
    String conversationId;

    String intent;
    String toolId;
    String originalQuery;

    Map<String, Object> resolvedArguments;
    ParameterCheckResult missingRequirements;
}
```

Pending 不保存：

```text
token
runtime proof
secret
签名材料
```

也不允许历史 Pending 中的 `principalId` 直接参与下一轮 Tool 调用。

------

## 6. Pending Key 与身份安全

Redis Key：

```text
ragent:pending:{authenticatedUserId}:{conversationId}
```

不能只使用 `conversationId`。

每轮恢复时：

```text
当前 authenticatedUserId
        ↓
读取对应 Pending
        ↓
继续任务
```

真正进入 Tool 执行前再次：

```text
AUTH_CONTEXT
    ↓
重新注入 principalId
```

历史状态只保存业务任务上下文，不作为可信身份来源。

------

## 7. Pending Store

第一版只需要：

```java
PendingClarificationStore {
    get(...)
    saveWithTtl(...)
    take(...)
    cancel(...)
}
```

一个会话最多保存一个 Pending Task。

不支持：

```text
多个并发 Pending
Pending 队列
跨会话恢复
复杂状态机
```

------

## 8. 多轮澄清

Pending 不会因为用户回复一次就自动清除。

流程：

```text
Pending 存在
   ↓
用户输入
   ↓
只抽取当前仍缺失的参数
   ↓
merge 到 resolvedArguments
   ↓
MissingParameterGate
```

### 如果仍为 MISSING

```text
更新 Pending
   ↓
重新保存 TTL
   ↓
再次生成澄清
```

例如：

第一轮：

```text
缺：
couponId
+
orderNo / productIds
```

用户只回答：

> 是优惠券 C001。

则：

```text
couponId ✅
orderNo / productIds ❌
```

Pending 继续存在，再问：

> 好的，请再告诉我想用于哪笔订单，或者哪些商品。

只有 Gate 最终变为：

```text
READY
```

才离开澄清阶段。

------

## 9. Pending 清理条件

Pending 只有以下三种正常退出方式：

### 9.1 参数补齐

```text
Gate = READY
   ↓
原子 take + clear Pending
   ↓
重新 AUTH_CONTEXT
   ↓
执行 Tool
```

注意：

> Pending 在进入执行阶段前清理，而不是等待 Tool 成功以后才清。

如果 Tool 后续出现网络错误、业务错误或 Provider 错误，应交由 Tool Error / Retry 机制处理，不能重新把用户后续消息误认为澄清输入。

### 9.2 用户显式取消

例如：

```text
取消
算了
不查了
先不查了
不用查了
停止
```

匹配后：

```text
clear Pending
→ 返回取消确认
```

### 9.3 TTL 到期

超时后由 Redis 自动失效。

------

## 10. CancelMatcher

D1 的显式取消不交给 Intent LLM 判断。

采用 Java 确定性匹配：

```java
CancelMatcher
```

原因：

- 控制语义简单；
- 需要行为确定；
- 无需额外模型调用；
- 更容易测试和解释。

第一版只支持明确取消表达。

如下复杂表达暂不处理：

> 这个先别查了，帮我看看物流。

这种属于 Task Switching，后续单独增强。

------

## 11. 用户突然问新问题

D1 核心范围不正式解决任务切换。

但入口逻辑应预留：

```text
存在 Pending
   ↓
① 是否显式取消？
   ├─ 是 → cancel
   ↓
② 当前输入能否补充至少一个 missing 参数？
   ├─ 是 → Resume Pending
   ↓
③ 当前输入是否明显属于新任务？
   ├─ 是 → 后续 Task Switching 机制处理
   └─ 否 → 继续提示原缺参
```

D1 当前只正式验收前两条：

```text
补参
显式取消
```

用户突然切换任务暂不纳入 D1 Golden。

------

## 12. Resume 入口

Pending 检查必须发生在正常 Intent 分类之前：

```text
Chat Request
   ↓
根据 authenticatedUserId + conversationId 查询 Pending
   ↓
┌───────────────────────────────┐
│ 无 Pending                    │
│ → 原 Ragent Intent 链路        │
│                               │
│ 有 Pending + 显式取消          │
│ → clear + 返回取消结果          │
│                               │
│ 有 Pending                    │
│ → 只解析当前缺失参数            │
└───────────────────────────────┘
```

这样第二轮：

> SN 是 ABC001。

不会被当成全新问题重新做 Intent 分类，而是作为原 Warranty Task 的参数补充。

------

## 13. Resume 流程

完整流程：

```text
Pending Task
   +
当前用户输入
   ↓
仅抽取 missingRequirements 对应参数
   ↓
merge resolvedArguments
   ↓
MissingParameterGate
```

### 仍缺参

```text
MISSING
↓
更新 Pending
↓
再次 Clarification
```

### 已补齐

```text
READY
↓
atomic take + clear
↓
重新注入 AUTH_CONTEXT
↓
执行原 Tool
↓
生成最终回答
```

------

## 14. Trace 增强

Checkpoint B/C 的 Trace 不做大幅扩展。

只增加：

```text
taskStatus
pendingTaskId
clarificationAction
```

其中：

```text
taskStatus =
  NEED_CLARIFICATION
  READY
  COMPLETED
  CANCELLED
```

第一轮：

```json
{
  "taskStatus": "NEED_CLARIFICATION",
  "pendingTaskId": "PT-001",
  "clarificationAction": "CREATED"
}
```

第二轮成功恢复：

```json
{
  "taskStatus": "COMPLETED",
  "pendingTaskId": "PT-001",
  "clarificationAction": "RESUMED_AND_CLEARED"
}
```

这样 Evaluator 可以直接证明两轮属于同一个业务任务。

------

## 15. D1 最小组件

D1 控制在以下六个核心组件：

```text
ToolParameterContract
ToolParameterContractRegistry
MissingParameterGate
ClarificationPromptBuilder
PendingClarificationState
PendingClarificationStore
```

辅助小组件可以包括：

```text
ParameterDescriptorRegistry
CancelMatcher
```

但不建设：

```text
Dialogue Manager
State Machine Framework
通用 Planning
Evaluator Plugin System
通用 DSL
```

------

## 16. D1 明确边界

D1 支持：

```text
单会话一个 Pending Task
单 Tool 缺参任务
多轮逐步补参
显式取消
每轮重新 AUTH_CONTEXT
确定性澄清
```

D1 暂不支持：

```text
Ambiguous Entity 候选发现
用户纠正已经解析的参数
用户突然切换任务
多个并发 Pending
跨 Tool Dependency
参数格式/合法性验证
实体真实性验证
自动 Retry
```

这些分别进入后续 D2、D3 或鲁棒性增强阶段。

------

## 17. D1 分阶段实施

### D1-A：Missing Parameter Gate

实现：

```text
ToolParameterContract
+
MissingParameterGate
```

目标：

```text
MISS-01～04
发现缺参
→ 不再调用终态 Tool
```

此阶段不要求第二轮恢复。

------

### D1-B：Clarification + Pending

实现：

```text
ClarificationPromptBuilder
PendingClarificationState
PendingClarificationStore
CancelMatcher
```

目标：

```text
MISSING
→ 用户友好澄清
→ 保存 Pending
```

------

### D1-C：Resume Closed Loop

实现：

```text
第二轮 Pending 检查
→ 缺失参数提取
→ merge
→ Gate
→ READY 后 take + clear
→ AUTH_CONTEXT 重绑定
→ Tool
```

完成后才宣布 D1 Passed。

------

## 18. D1 Golden 验收

新增 4 组两轮测试，共 8 个步骤。

| Case第一轮第二轮 |                                                |                        |
| ---------------- | ---------------------------------------------- | ---------------------- |
| MISS-01          | 缺 `serviceNo/phone`，阻断 `getRepairProgress` | 补任意一个后继续查询   |
| MISS-02          | 缺 `sn`，阻断 `getWarrantyStatus`              | 补 SN 后继续查询       |
| MISS-03          | 缺优惠券及订单/商品约束，阻断资格查询          | 分一次或多次补齐后继续 |
| MISS-04          | 已有地区但缺商品，阻断库存查询                 | 补商品/型号后继续      |

Round A 自动断言：

```text
resolutionStatus = MISSING
taskStatus = NEED_CLARIFICATION
forbiddenTool 未调用
pendingTaskId exists
clarificationAction = CREATED
无底层 VALIDATION_ERROR 暴露
```

Round B 自动断言：

```text
pendingTaskId unchanged
补参来自当前 clarification input
AUTH_CONTEXT 重新注入
expected Tool executed once
transport/business status 符合 Fixture
clarificationAction = RESUMED_AND_CLEARED
Pending 已不存在
```

如果需要多轮补参：

```text
Round B:
仍 MISSING
→ pendingTaskId unchanged

Round C:
READY
→ 执行并 clear
```

------

## 19. D1 完成标准

D1 最终验收标准冻结为：

```text
4/4 首轮成功阻断终态 Tool
4/4 返回确定性、用户可理解的澄清
4/4 支持补参恢复原任务
4/4 使用当前可信 AUTH_CONTEXT 执行
4/4 Pending 最终正确清理
支持参数仍不完整时继续多轮澄清
支持显式取消 Pending
0 次 runtime proof/token/secret 泄露
0 次底层字段错误或 VALIDATION_ERROR 直接泄露
原有正常 Tool Case 不因 D1 引入回退
```

## 20. D1 核心定位

D1 不是通用对话管理框架。

它只把原 Ragent 的：

```text
缺参 → 调 Tool → 报错
```

升级为：

```text
缺参
→ 阻断
→ 澄清
→ 保留任务
→ 多轮补参
→ 安全恢复
→ 执行
```

即：

> **Missing Parameter Closed Loop with Trusted Pending Resume**

这是 D1 唯一需要证明的能力。3. MissingParameterGate

Gate 只负责回答：

> 当前参数是否已经满足 Tool 的执行前置条件？

D1 阶段只判断“有没有”，不判断格式、语义合法性和实体真实性。

以下值统一视为未提供：

```text
null
""
"   "
empty collection
empty array
```

输出：

```java
ParameterCheckResult {
    boolean ready;
    Set<String> missingAll;
    List<RequiredAnyGroup> unsatisfiedAnyGroups;
}
```

流程：

```text
Tool Schema
   +
Adapter Contract
   ↓
ToolParameterContract
   +
当前 Arguments
   ↓
MissingParameterGate
   ├─ READY
   └─ MISSING
```

一旦为 `MISSING`：

```text
禁止调用终态 Tool
```

这是 D1 最核心的行为变化。

------

## 4. Clarification 文案生成

D1 不针对每种参数组合手写模板。

错误方案：

```text
缺 productId → 模板 A
缺 region → 模板 B
缺 productId + region → 模板 C
...
```

随着参数增加会产生组合爆炸。

改为：

```text
参数元数据
+
GateResult
+
组合式 Renderer
```

新增：

```java
ParameterDescriptor {
    String key;
    String displayName;
    String askPhrase;
}
```

例如：

```text
sn
→ 设备 SN
→ 请提供设备 SN

productId
→ 商品
→ 请提供具体商品或型号

region
→ 所在地区
→ 请提供所在地区

couponId
→ 优惠券
→ 请说明具体是哪张优惠券
```

`ClarificationPromptBuilder` 不认识具体 Tool，也不判断参数是否缺失，只负责把：

```text
missingAll
+
unsatisfiedAnyGroups
```

组合成自然语言。

例如：

```text
missingAll:
  productId
  region
```

自动生成：

> 请提供具体商品或型号，以及所在地区。

例如：

```text
missingAll:
  couponId

unsatisfiedAnyGroups:
  订单号或商品
```

生成：

> 请说明具体是哪张优惠券，以及想使用优惠券的订单号或商品。

这样实现复杂度由“参数组合数”降低为：

```text
参数数量 + requiredAny 组数量
```

------

## 5. PendingClarificationState

当 Gate 返回 `MISSING` 时，创建 Pending Task。

最小状态：

```java
PendingClarificationState {
    String pendingTaskId;
    String authenticatedUserId;
    String conversationId;

    String intent;
    String toolId;
    String originalQuery;

    Map<String, Object> resolvedArguments;
    ParameterCheckResult missingRequirements;
}
```

Pending 不保存：

```text
token
runtime proof
secret
签名材料
```

也不允许历史 Pending 中的 `principalId` 直接参与下一轮 Tool 调用。

------

## 6. Pending Key 与身份安全

Redis Key：

```text
ragent:pending:{authenticatedUserId}:{conversationId}
```

不能只使用 `conversationId`。

每轮恢复时：

```text
当前 authenticatedUserId
        ↓
读取对应 Pending
        ↓
继续任务
```

真正进入 Tool 执行前再次：

```text
AUTH_CONTEXT
    ↓
重新注入 principalId
```

历史状态只保存业务任务上下文，不作为可信身份来源。

------

## 7. Pending Store

第一版只需要：

```java
PendingClarificationStore {
    get(...)
    saveWithTtl(...)
    take(...)
    cancel(...)
}
```

一个会话最多保存一个 Pending Task。

不支持：

```text
多个并发 Pending
Pending 队列
跨会话恢复
复杂状态机
```

------

## 8. 多轮澄清

Pending 不会因为用户回复一次就自动清除。

流程：

```text
Pending 存在
   ↓
用户输入
   ↓
只抽取当前仍缺失的参数
   ↓
merge 到 resolvedArguments
   ↓
MissingParameterGate
```

### 如果仍为 MISSING

```text
更新 Pending
   ↓
重新保存 TTL
   ↓
再次生成澄清
```

例如：

第一轮：

```text
缺：
couponId
+
orderNo / productIds
```

用户只回答：

> 是优惠券 C001。

则：

```text
couponId ✅
orderNo / productIds ❌
```

Pending 继续存在，再问：

> 好的，请再告诉我想用于哪笔订单，或者哪些商品。

只有 Gate 最终变为：

```text
READY
```

才离开澄清阶段。

------

## 9. Pending 清理条件

Pending 只有以下三种正常退出方式：

### 9.1 参数补齐

```text
Gate = READY
   ↓
原子 take + clear Pending
   ↓
重新 AUTH_CONTEXT
   ↓
执行 Tool
```

注意：

> Pending 在进入执行阶段前清理，而不是等待 Tool 成功以后才清。

如果 Tool 后续出现网络错误、业务错误或 Provider 错误，应交由 Tool Error / Retry 机制处理，不能重新把用户后续消息误认为澄清输入。

### 9.2 用户显式取消

例如：

```text
取消
算了
不查了
先不查了
不用查了
停止
```

匹配后：

```text
clear Pending
→ 返回取消确认
```

### 9.3 TTL 到期

超时后由 Redis 自动失效。

------

## 10. CancelMatcher

D1 的显式取消不交给 Intent LLM 判断。

采用 Java 确定性匹配：

```java
CancelMatcher
```

原因：

- 控制语义简单；
- 需要行为确定；
- 无需额外模型调用；
- 更容易测试和解释。

第一版只支持明确取消表达。

如下复杂表达暂不处理：

> 这个先别查了，帮我看看物流。

这种属于 Task Switching，后续单独增强。

------

## 11. 用户突然问新问题

D1 核心范围不正式解决任务切换。

但入口逻辑应预留：

```text
存在 Pending
   ↓
① 是否显式取消？
   ├─ 是 → cancel
   ↓
② 当前输入能否补充至少一个 missing 参数？
   ├─ 是 → Resume Pending
   ↓
③ 当前输入是否明显属于新任务？
   ├─ 是 → 后续 Task Switching 机制处理
   └─ 否 → 继续提示原缺参
```

D1 当前只正式验收前两条：

```text
补参
显式取消
```

用户突然切换任务暂不纳入 D1 Golden。

------

## 12. Resume 入口

Pending 检查必须发生在正常 Intent 分类之前：

```text
Chat Request
   ↓
根据 authenticatedUserId + conversationId 查询 Pending
   ↓
┌───────────────────────────────┐
│ 无 Pending                    │
│ → 原 Ragent Intent 链路        │
│                               │
│ 有 Pending + 显式取消          │
│ → clear + 返回取消结果          │
│                               │
│ 有 Pending                    │
│ → 只解析当前缺失参数            │
└───────────────────────────────┘
```

这样第二轮：

> SN 是 ABC001。

不会被当成全新问题重新做 Intent 分类，而是作为原 Warranty Task 的参数补充。

------

## 13. Resume 流程

完整流程：

```text
Pending Task
   +
当前用户输入
   ↓
仅抽取 missingRequirements 对应参数
   ↓
merge resolvedArguments
   ↓
MissingParameterGate
```

### 仍缺参

```text
MISSING
↓
更新 Pending
↓
再次 Clarification
```

### 已补齐

```text
READY
↓
atomic take + clear
↓
重新注入 AUTH_CONTEXT
↓
执行原 Tool
↓
生成最终回答
```

------

## 14. Trace 增强

Checkpoint B/C 的 Trace 不做大幅扩展。

只增加：

```text
taskStatus
pendingTaskId
clarificationAction
```

其中：

```text
taskStatus =
  NEED_CLARIFICATION
  READY
  COMPLETED
  CANCELLED
```

第一轮：

```json
{
  "taskStatus": "NEED_CLARIFICATION",
  "pendingTaskId": "PT-001",
  "clarificationAction": "CREATED"
}
```

第二轮成功恢复：

```json
{
  "taskStatus": "COMPLETED",
  "pendingTaskId": "PT-001",
  "clarificationAction": "RESUMED_AND_CLEARED"
}
```

这样 Evaluator 可以直接证明两轮属于同一个业务任务。

------

## 15. D1 最小组件

D1 控制在以下六个核心组件：

```text
ToolParameterContract
ToolParameterContractRegistry
MissingParameterGate
ClarificationPromptBuilder
PendingClarificationState
PendingClarificationStore
```

辅助小组件可以包括：

```text
ParameterDescriptorRegistry
CancelMatcher
```

但不建设：

```text
Dialogue Manager
State Machine Framework
通用 Planning
Evaluator Plugin System
通用 DSL
```

------

## 16. D1 明确边界

D1 支持：

```text
单会话一个 Pending Task
单 Tool 缺参任务
多轮逐步补参
显式取消
每轮重新 AUTH_CONTEXT
确定性澄清
```

D1 暂不支持：

```text
Ambiguous Entity 候选发现
用户纠正已经解析的参数
用户突然切换任务
多个并发 Pending
跨 Tool Dependency
参数格式/合法性验证
实体真实性验证
自动 Retry
```

这些分别进入后续 D2、D3 或鲁棒性增强阶段。

------

## 17. D1 分阶段实施

### D1-A：Missing Parameter Gate

实现：

```text
ToolParameterContract
+
MissingParameterGate
```

目标：

```text
MISS-01～04
发现缺参
→ 不再调用终态 Tool
```

此阶段不要求第二轮恢复。

------

### D1-B：Clarification + Pending

实现：

```text
ClarificationPromptBuilder
PendingClarificationState
PendingClarificationStore
CancelMatcher
```

目标：

```text
MISSING
→ 用户友好澄清
→ 保存 Pending
```

------

### D1-C：Resume Closed Loop

实现：

```text
第二轮 Pending 检查
→ 缺失参数提取
→ merge
→ Gate
→ READY 后 take + clear
→ AUTH_CONTEXT 重绑定
→ Tool
```

完成后才宣布 D1 Passed。

------

## 18. D1 Golden 验收

新增 4 组两轮测试，共 8 个步骤。

| Case    | 第一轮                                         | 第二轮                 |
| ------- | ---------------------------------------------- | ---------------------- |
| MISS-01 | 缺 `serviceNo/phone`，阻断 `getRepairProgress` | 补任意一个后继续查询   |
| MISS-02 | 缺 `sn`，阻断 `getWarrantyStatus`              | 补 SN 后继续查询       |
| MISS-03 | 缺优惠券及订单/商品约束，阻断资格查询          | 分一次或多次补齐后继续 |
| MISS-04 | 已有地区但缺商品，阻断库存查询                 | 补商品/型号后继续      |

Round A 自动断言：

```text
resolutionStatus = MISSING
taskStatus = NEED_CLARIFICATION
forbiddenTool 未调用
pendingTaskId exists
clarificationAction = CREATED
无底层 VALIDATION_ERROR 暴露
```

Round B 自动断言：

```text
pendingTaskId unchanged
补参来自当前 clarification input
AUTH_CONTEXT 重新注入
expected Tool executed once
transport/business status 符合 Fixture
clarificationAction = RESUMED_AND_CLEARED
Pending 已不存在
```

如果需要多轮补参：

```text
Round B:
仍 MISSING
→ pendingTaskId unchanged

Round C:
READY
→ 执行并 clear
```

------

## 19. D1 完成标准

D1 最终验收标准冻结为：

```text
4/4 首轮成功阻断终态 Tool
4/4 返回确定性、用户可理解的澄清
4/4 支持补参恢复原任务
4/4 使用当前可信 AUTH_CONTEXT 执行
4/4 Pending 最终正确清理
支持参数仍不完整时继续多轮澄清
支持显式取消 Pending
0 次 runtime proof/token/secret 泄露
0 次底层字段错误或 VALIDATION_ERROR 直接泄露
原有正常 Tool Case 不因 D1 引入回退
```

## 20. D1 核心定位

D1 不是通用对话管理框架。

它只把原 Ragent 的：

```text
缺参 → 调 Tool → 报错
```

升级为：

```text
缺参
→ 阻断
→ 澄清
→ 保留任务
→ 多轮补参
→ 安全恢复
→ 执行
```

即：

> **Missing Parameter Closed Loop with Trusted Pending Resume**

这是 D1 唯一需要证明的能力。