# Huawei Mall Adapter：身份、时钟与只读 Tool（草案）

本实现让原始 Ragent 机制使用 Huawei Mall 业务皮肤。原有天气、销售和工单 Tool、意图识别、参数抽取、多 Tool 执行与对话记忆保持原样。新增的只有 8 个只读 MCP Tool、固定 Mock SQL 查询、业务校验、可信身份注入和固定业务时钟。原始源码提交 `5500546c` 已标记 `baseline/ragent-1.0x`；Fixture 仍为 `DRAFT_UNFROZEN`。

## 身份链

Ragent 的 `UserContextInterceptor` 从 Sa-Token 登录态读取原系统用户 ID。`HuaweiMallRuntimeArguments` 仅在五个私有 Tool 调用前，用 `HUAWEI_MALL_AUTH_MAP` 把这个**认证 ID**确定性映射到 U001/U002/U003；它在模型抽参结束后删除模型可能提供的身份字段，注入 `_runtimePrincipal`、当前签发时间和 HMAC 证明。MCP Tool 的公开 JSON Schema 不包含这些字段。Tool Server 验证证明后才把 principal 作为业务服务的独立参数；缺少或伪造证明时拒绝私有查询，未配置运行时密钥时返回基础设施错误。

这条映射是身份适配设施，不读取 Tool 结果、不补齐业务参数，也不改变选 Tool 逻辑。当前原 Ragent `public.t_user` 只有两个账号；正式三 principal 运行前需准备三个**已认证的测试登录身份**，并通过外部配置将其登录 ID 映射到 U001、U002、U003。不能把案例的 `principal_id` 或用户问题直接当作 MCP 模型参数。`ragentLocation` 的配置值未复制进新源码。

## 固定时钟与数据路径

业务 `asOf` 固定为 `2026-09-14T10:00:00+08:00`，时区 `Asia/Shanghai`。V1.1 只接受 `LAST_MONTH`，解析为 `[2026-08-01T00:00:00+08:00, 2026-09-01T00:00:00+08:00)`；SQL 查询只处理绝对边界。业务查询显式限定 `huawei_mall_mock`，原应用仍使用 `public`。两个 schema 都有 `t_user`，不能整体切换 Bootstrap 的搜索路径。

## 启动配置

两个应用进程须提供相同且至少 32 字符的 `HUAWEI_MALL_RUNTIME_SECRET`。MCP Server 还需要 `HUAWEI_MALL_DB_PASSWORD`；可用 `HUAWEI_MALL_DB_USER` 和 `HUAWEI_MALL_JDBC_URL` 覆盖默认目标。Bootstrap 使用 `HUAWEI_MALL_AUTH_MAP`，格式为 `认证登录ID=U001,另一个认证登录ID=U002,第三个认证登录ID=U003`。凭据从进程环境传入，不写入本仓库。现有 9090/9099 端口分别属于 Bootstrap/MCP Server；正式 Agent 联调前需确认两个进程健康。

## 直接验收

八个 Tool 的数据库服务与 MCP handler 直接测试在固定 Seed 上通过 14 个测试；认证 ID 映射、模型身份覆盖和签名的 Bootstrap 定向测试通过 2 个测试。测试覆盖正常结果、缺参和非法值、条件一致/冲突、无结果、多候选、跨 principal 隔离、价格版本、保修状态、半开时间边界和重复查询确定性。MCP 同时返回 JSON 文本与同内容的 `structuredContent`；`VALIDATION_ERROR`、`CONFLICT`、`INTERNAL_ERROR` 的 `isError=true`，业务 `NOT_FOUND`、`MULTIPLE_MATCHES` 为 false。

此机器使用 JDK 25，原项目的 Lombok 构建需 `-Dmaven.compiler.proc=full`；原 Surefire fork 配置在该环境失败，定向测试使用 `-DforkCount=0`。这些测试不调用 LLM，不验证 Agent 会选对 Tool、追问或完成 Tool 依赖。四个 KB、Intent Resource Mapping、真实登录联调和同请求证据采集完成前，不能运行正式 20 条 Baseline Audit。
