# Local Setup

## Prerequisites

- JDK 17
- Maven 3.9+
- Node.js 18+
- PostgreSQL 15+ with pgvector when `rag.vector.type=pg`
- Redis 7
- RocketMQ for document ingestion
- RustFS or another compatible object store for uploaded documents

## Database initialization

Create an isolated development database named `ragent`, then initialize the upstream application tables and the synthetic commerce schema:

```bash
psql -v ON_ERROR_STOP=1 -d ragent -f resources/database/schema_pg.sql
psql -v ON_ERROR_STOP=1 -d ragent -f resources/database/init_data_pg.sql
psql -v ON_ERROR_STOP=1 -d ragent -f fixtures/database/schema.sql
psql -v ON_ERROR_STOP=1 -d ragent -f fixtures/database/fixtures/seed.sql
psql -v ON_ERROR_STOP=1 -d ragent -f fixtures/database/audit.sql
psql -v ON_ERROR_STOP=1 -d ragent -f fixtures/database/fixtures/case_fact_audit.sql
```

The last two commands are read-only audits. Their violation queries should return no rows.

## Configuration

Copy `.env.example` to a local untracked `.env` file or configure the variables directly in the IDE. At minimum, Bootstrap and MCP Server must share:

```text
HUAWEI_MALL_RUNTIME_SECRET
```

The value must contain at least 32 characters. Configure `HUAWEI_MALL_AUTH_MAP` with authenticated application user IDs mapped to synthetic principals, for example `1001=U001,1002=U002,1003=U003`.

The legacy `HUAWEI_MALL_*` property prefix is retained for compatibility with the frozen experiment; it does not indicate a production Huawei connection.

## Build and test

```bash
mvn clean test
```

默认测试排除需要数据库、Redis、Milvus 或真实模型的 `external` 测试。完整配置外部依赖后可运行：

```bash
mvn -Pexternal test
```

Start the MCP Server:

```bash
mvn -pl mcp-server spring-boot:run
```

Start Bootstrap in another terminal:

```bash
mvn -pl bootstrap -am spring-boot:run
```

The legacy-compatible backend base path is `http://localhost:9090/api/ragent`.

## Frontend

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. The Vite proxy forwards `/api` requests to the Bootstrap port configured in `frontend/vite.config.ts`.

## External models

Configure only the model providers you use. Do not place API keys in YAML or commit `.env`. External model runs may incur cost and are not required for deterministic unit tests.

