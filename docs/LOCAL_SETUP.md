# 本地运行指南

本文档用于在本地启动 MallAgent 的后端、MCP Server 和前端。项目使用公开页面整理后的演示知识与 Mock 业务数据，不连接华为生产系统。

## 1. 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- PostgreSQL 15+，并安装 `pgvector` 扩展
- Redis 7+
- RocketMQ
- RustFS 或兼容 S3 的对象存储

如只阅读代码或运行不依赖外部基础设施的单元测试，可按测试配置使用仓库内的 Mock 实现。

## 2. 初始化数据库

先创建本地数据库，例如 `ragent`，再按顺序执行：

```bash
psql -U postgres -d ragent -f ragent-bootstrap/src/main/resources/database/schema_pg.sql
psql -U postgres -d ragent -f ragent-bootstrap/src/main/resources/database/init_data_pg.sql
psql -U postgres -d ragent -f fixtures/database/schema.sql
psql -U postgres -d ragent -f fixtures/database/fixtures/seed.sql
```

下面两个脚本只做数据一致性检查，不会写入业务数据：

```bash
psql -U postgres -d ragent -f fixtures/database/audit.sql
psql -U postgres -d ragent -f fixtures/database/fixtures/case_fact_audit.sql
```

## 3. 配置环境变量

复制示例配置：

```bash
cp .env.example .env
```

至少需要设置数据库、Redis、RocketMQ、对象存储和模型服务相关变量。不要把真实密钥提交到 Git。

Bootstrap 与 MCP Server 之间使用共享运行时密钥：

```dotenv
HUAWEI_MALL_RUNTIME_SECRET=请替换为至少32位的随机字符串
```

两端必须使用相同的值。MCP Server 会校验请求中的 `Authorization` 信息；校验失败时不会进入 `tools/list` 或 `tools/call` 的业务处理。

本地演示账号与业务用户的映射也通过配置提供，例如：

```dotenv
HUAWEI_MALL_AUTH_MAP=authenticated-user-id=U001
```

请根据 `.env.example` 中的实际变量名和示例调整本地配置。

## 4. 构建与测试

在仓库根目录执行：

```bash
mvn clean test
```

需要连接外部模型、数据库或其他基础设施时，可运行外部集成测试 Profile：

```bash
mvn -Pexternal test
```

外部集成测试依赖本地环境和有效凭据，不建议在未完成配置时直接运行。

## 5. 启动 MCP Server

```bash
mvn -pl mcp-server spring-boot:run
```

MCP Server 提供工具发现与调用能力，并执行最终的参数、身份和业务查询校验。

## 6. 启动 Bootstrap 服务

另开一个终端，在仓库根目录执行：

```bash
mvn -pl bootstrap -am spring-boot:run
```

Bootstrap 默认监听：

```text
http://localhost:9090
```

仓库当前的 Vite 开发代理目标是 `http://localhost:19090`。联调前请选择一种方式保持一致：启动 Bootstrap 时设置 `SERVER_PORT=19090`，或把 `frontend/vite.config.ts` 的代理目标改为 `http://localhost:9090`。

## 7. 启动前端

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

开发环境下，Vite 会把后端请求代理到配置的 Bootstrap 地址。

## 8. 配置模型服务

项目通过 OpenAI Compatible 接口接入模型。不同任务可以配置不同模型，例如意图识别、参数抽取、回答生成和向量化模型。需要使用真实模型时，请在 `.env` 中配置对应的 Base URL、模型名和 API Key。

模型不可用时，依赖模型的功能和外部集成测试会失败；工具服务和确定性业务测试仍可单独验证。

## 9. 常见检查

- 后端无法连接 MCP Server：检查 MCP 地址及双方的 `HUAWEI_MALL_RUNTIME_SECRET` 是否一致。
- 数据查询为空：确认四个 SQL 初始化脚本均已按顺序执行。
- 向量检索报错：确认 PostgreSQL 已安装并启用 `pgvector`。
- 前端请求失败：检查 Bootstrap 端口与 Vite 代理配置是否一致。
- 模型返回 401：检查模型 API Key、Base URL 和模型服务权限。
