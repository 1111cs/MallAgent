# MallAgent

MallAgent 是一个基于 Java、Spring Boot、RAG 与 MCP 构建的可靠商城业务智能体。项目关注的不是“模型能否聊商城”，而是模型进入业务系统后，如何完成受约束的意图路由、缺参追问、可信实体选择、多工具依赖、证据溯源和确定性评测。

> 重要声明：本项目不对接华为商城生产系统，不包含华为内部接口、账号或业务数据。仓库中的商品、订单、设备、优惠券和维修记录均为人工构造的确定性 Mock Fixture；“Huawei Mall”仅是冻结实验阶段使用的业务场景名称。

## 项目亮点

| 能力 | 实现 |
| --- | --- |
| 业务路由 | 5 个一级领域、24 个物理叶子意图，显式映射到 KB、MCP Tool 或 System |
| 知识检索 | 4 类知识库，支持全局检索、意图定向检索、去重和 Rerank |
| 业务工具 | 8 个只读 MCP Tool，覆盖商品、库存、订单、物流、优惠券、保修和维修 |
| 缺参闭环 | 参数不完整时创建 Pending Task，后续轮次补齐后恢复执行 |
| 实体消歧 | 只允许从可信工具候选中选择订单、设备和商品，避免模型猜测业务 ID |
| 工具依赖 | 将上游工具输出结构化绑定到下游参数，支持顺序和并行阶段 |
| 证据治理 | 保留检索来源、候选选择、参数来源、工具结果和引用关系 |
| 确定性评测 | 固定身份、时钟、Fixture 和业务契约；20 条 Golden Case 从 7/20 提升到 20/20 |

## 系统边界

MallAgent 采用“模型负责语义判断，Java 负责确定性治理”的分工：

```text
用户问题
→ 问题改写与子问题拆分
→ 大模型基于意图树生成候选意图和置信度
→ Java 执行阈值过滤、资源契约与权限检查
→ KB 检索或 MCP Tool 调用
→ 缺参、歧义与工具依赖闭环
→ 证据化上下文与回答生成
→ 引用、Trace 和 Golden Case 断言
```

模型不能决定可信用户身份，不能凭空生成订单号、设备 SN 或下游工具参数。用户私有工具的身份来自登录上下文，并通过运行时签名传给 MCP Server。

## 业务能力

### 意图与资源

24 个物理叶子意图分布在以下领域：

- `PRODUCT`：产品信息、比较、推荐、兼容性和库存；
- `SHOPPING_POLICY`：优惠券、发票、配送和退换政策；
- `ORDER`：订单查询、物流查询和订单修改入口；
- `AFTER_SALES`：保修、备件价格、维修服务、维修进度和服务中心；
- `SUPPORT`：使用说明、故障排查、连接网络和充电电池。

完整映射见 [intent-mapping-v1.json](fixtures/knowledge/intent-mapping-v1.json) 和 [业务契约](docs/BUSINESS_CONTRACT.md)。

### 8 个只读工具

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

本项目不提供取消订单、修改订单或创建维修单等有副作用操作。

### 确定性数据

商城 Fixture 使用独立 PostgreSQL Schema，共 12 张表，覆盖商品、库存、订单、订单项、优惠券、设备、维修单以及物流和维修事件。实验同时固定：

- 数据版本：`huawei-mall-fixture-v1.0`；
- 业务时钟：`2026-09-14T10:00:00+08:00`；
- 时区：`Asia/Shanghai`；
- 测试身份：`U001`、`U002`、`U003`。

固定时钟只服务于可重复评测，不代表真实商城当前时间。

## 模块结构

```text
MallAgent
├─ bootstrap       HTTP、会话、RAG 主链、闭环执行、Trace 与评测
├─ framework       通用上下文、领域约定和基础组件
├─ infra-ai        模型、Embedding、Rerank 与供应商适配
├─ mcp-server      商城只读工具和业务数据查询
├─ frontend        React 对话、管理后台和 Golden Case 页面
├─ fixtures
│  ├─ database     Mock Schema、Seed、事实审计
│  ├─ knowledge    4 类知识材料和 24 意图映射
│  └─ evaluation   Golden Case 与路由冒烟用例
└─ docs            架构、业务契约、实现与验收记录
```

Java 包名和部分 `huawei-mall.*` 配置名保留自冻结实验契约，以保证历史 Fixture 和评测结果可复现；它们不表示官方系统接入。

## 快速验证

环境要求：

- JDK 17；
- Maven 3.9+；
- Node.js 18+；
- PostgreSQL 15+；
- Redis 7；
- 可选：RocketMQ、RustFS、外部模型与 Embedding 服务。

执行默认 Java 测试：

```bash
mvn clean test
```

构建前端：

```bash
cd frontend
npm ci
npm run build
```

完整数据库、MCP Server、Bootstrap 与前端启动步骤见 [本地运行指南](docs/LOCAL_SETUP.md)。默认配置不包含任何真实 API Key。

本次公开整理实际执行的命令、结果和未覆盖范围见 [发布验证报告](docs/VERIFICATION.md)。

## 评测结论

同一业务契约、同一 Fixture 和同一模型条件下：

| 系统 | 结果 |
| --- | ---: |
| 原始 Baseline | 7/20 PASS |
| 加入闭环治理后的 MallAgent | 20/20 PASS |

Baseline 的主要失败集中在缺参、多候选实体和跨工具依赖。最终冻结结果见 [评测说明](docs/EVALUATION.md) 与 [20 Case 汇总](docs/huawei-mall-golden-v0-final-consolidated-2026-09-21.json)。

## 安全说明

- API Key、数据库密码和运行时签名密钥只能通过环境变量传入；
- `.env`、日志、PID、构建产物、下载文件和临时文件均被忽略；
- 用户私有工具从可信认证上下文获取身份，拒绝模型传入的身份字段；
- 本仓库仅包含合成数据，不包含真实用户隐私或订单数据。

配置模板见 [.env.example](.env.example)。提交安全问题请参考 [SECURITY.md](SECURITY.md)。

## 上游与许可证

MallAgent 基于 [Ragent](https://gitee.com/nageoffer/ragent) 修改，保留其 Java RAG 基础设施和 Apache-2.0 许可证。商城业务适配、确定性 Fixture、闭环执行治理、证据与引用校验以及 Golden Case 评测是在此基础上的扩展。

详细归属见 [NOTICE](NOTICE)。项目采用 [Apache License 2.0](LICENSE)。

