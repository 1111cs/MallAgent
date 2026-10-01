# MallAgent 发布验证报告

本报告记录公开仓库整理阶段实际执行的验证，目的是区分可离线复现的默认测试、依赖外部基础设施的测试，以及项目历史冻结的 Golden Case 结果。

## 本次实际验证

验证环境：

- Windows；
- Maven Wrapper / Maven 多模块构建；
- Java 编译目标：17；
- Node.js 与 npm（前端）。

### Java 默认测试

执行命令：

```bash
mvn clean test
```

结果：

```text
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

默认测试排除了标记为 `external` 的模型、PostgreSQL、Redis、Milvus 和完整 Spring 环境测试，因此新贡献者不配置密钥或基础设施也能得到稳定结果。

### 前端检查

执行命令：

```bash
cd frontend
npm ci
npm run lint
npm run build
```

结果：

- 依赖安装成功；
- ESLint：0 个错误、16 个警告；
- Vite 生产构建成功；
- 当前主 JavaScript Chunk 约 1.9 MB，Vite 给出大 Chunk 警告，后续可按页面拆包优化。

这些警告不阻塞构建，但不是“零警告”。

## 外部测试

需要真实模型、Embedding、PostgreSQL、Redis、Milvus 或 MCP 运行环境的测试统一标记为 `external`：

```bash
mvn -Pexternal test
```

本次公开仓库整理时本机 Docker 服务未运行，因此没有重新执行完整 external Profile。请按 [本地运行指南](LOCAL_SETUP.md) 配置基础设施和环境变量后执行。

## Golden Case

仓库保留的 20 条 Golden Case 是冻结业务契约、Fixture、时钟与测试身份条件下的历史验收产物：

```text
Baseline: 7/20 PASS
MallAgent: 20/20 PASS
```

该结果及约束见 [评测说明](EVALUATION.md) 和 [冻结结果](huawei-mall-golden-v0-final-consolidated-2026-09-21.json)，不应理解为对任意模型、任意数据或真实生产环境的准确率承诺。
