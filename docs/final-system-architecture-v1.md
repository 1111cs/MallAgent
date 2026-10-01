# Huawei Mall Reliable Agent — Final System Architecture V1

状态：`FINAL_ARCHITECTURE_V1`

## 一句话定位

本项目没有从零重写 Agent，也没有只给 Ragent 更换业务数据；它先通过 Huawei Mall Baseline 暴露真实失败模式，再在 Ragent 既有能力上补充可靠执行、路由治理、证据可信和自动评测闭环。

## 手写架构图

```text
┌──────────────────────────────────────────────────────────────────────┐
│                           User Request                               │
│                    Huawei Mall UI / Chat API                         │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      Conversation Context                            │
│ principalId / conversationId / authenticated user / fixed clock     │
│                                                                      │
│ 来源：Huawei Mall Adapter + Ragent 原有会话能力                       │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Pending Task Recovery                            │
│                                                                      │
│ 有 Pending：恢复缺参、实体选择或依赖任务                              │
│ 无 Pending：作为新请求继续                                            │
│                                                                      │
│ 对应实现：D1 / D2 / D3 Resume                                        │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      Routing Governance                              │
│                                                                      │
│ AUTH_CONTEXT / SYSTEM / ROUTED / NEED_CLARIFICATION                  │
│ GLOBAL_KB_FALLBACK / OUT_OF_SCOPE / ROUTING_ERROR                    │
│                                                                      │
│ 对应实现：G1                                                         │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                  ┌─────────────┴─────────────┐
                  │                           │
                  ▼                           ▼
┌───────────────────────────────┐   ┌──────────────────────────────────┐
│            KB Path            │   │            Tool Path             │
│                               │   │                                  │
│ Evidence Retrieval            │   │ Parameter Resolution             │
│ Ragent Vector Search/Rerank    │   │ requiredAll / requiredAny        │
│              │                │   │ Missing Parameter Gate           │
│              ▼                │   │              │                   │
│ Provenance Preservation       │   │              ▼                   │
│ document / section / chunk    │   │ Trusted Entity Resolution        │
│ rank / score / Evidence ID    │   │ candidate discovery / selection  │
│ 对应实现：E1                  │   │              │                   │
│              │                │   │              ▼                   │
│              ▼                │   │ Dependency Execution             │
│ Answer Generation             │   │ tool plan / output binding       │
│ Evidence-aware Prompt         │   │              │                   │
│              │                │   │              ▼                   │
│              ▼                │   │ MCP Execution                    │
│ Citation Identity Check       │   │ Huawei Mall 8 Tools              │
│ validate / normalize / clean  │   │ trusted auth + fixture data      │
│ 对应实现：E2                  │   │                                  │
└───────────────┬───────────────┘   └────────────────┬─────────────────┘
                │                                    │
                └─────────────────┬──────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                           Final Answer                               │
│     Answer / Clarification / Candidate Selection / Capability Guide │
└──────────────────────────────────────────────────────────────────────┘


══════════════════════ 横跨整条运行链的能力 ═══════════════════════════

┌──────────────────────────────────────────────────────────────────────┐
│               Structured Trace & Golden Evaluation                  │
│                                                                      │
│ Routing / Parameter / Parameter Source / Entity / Tool Plan          │
│ Tool Binding / Tool Execution / Evidence / Citation                  │
│                                                                      │
│ 作用：                                                               │
│ 1. 将 INFERRED / NOT_OBSERVED 变为 DIRECT                            │
│ 2. 自动判断 Golden Case 的关键执行链是否正确                          │
│ 3. 支持后续改造的回归验证                                             │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│               E3 Grounding Evaluation — Eval Only                   │
│                      默认线上关闭                                    │
│                                                                      │
│ Grounding：事实是否被真实 Evidence / Tool Result 支撑                 │
│ Citation Alignment：引用是否对齐当前 Claim                            │
│ Source Authority：动态事实是否来自 Tool，稳定知识是否来自 KB           │
└──────────────────────────────────────────────────────────────────────┘
```

## 三个系统边界

```text
① Ragent Original Capability

Multi-question Rewrite
Intent Classifier
Intent-directed / Global Retrieval
Vector Search / Rerank
MCP Transport
Prompt / LLM Answer
SSE Chat
基础 Trace

────────────────────────────────────────

② Huawei Mall Business Adapter

Huawei Mall 业务契约
24 个业务 Intent
4 个 KB / 16 个文档 / 51 个知识单元
8 个 MCP Tool
Mock DB Fixture
principalId 身份映射
固定测试时钟
模型配置与独立 Redis Key

它的作用是建立公平业务 Baseline，
不提前加入需要由实验验证的 Agent 核心机制。

────────────────────────────────────────

③ Our Reliability & Governance Layer

Minimal Structured Trace + Golden Assertions
Pending Task Recovery
Missing Parameter Gate
Trusted Entity Resolution
Contract-driven Tool Dependency
Routing & Fallback Governance
Evidence Provenance Preservation
Citation Identity Validation
Eval-only Claim-level Grounding
```

## 面试时怎么讲

先讲原始 Ragent 已经提供 Intent、检索、MCP 和生成链，所以项目不是从零造 Agent。

再讲 Huawei Mall Adapter 只负责把同一套冻结业务数据、KB 和 Tool 接给 Baseline 与 Ours，保证对比公平。

然后沿运行主链说明我们的改造：请求先恢复 Pending Task，再经过显式路由治理；KB 分支保留 Evidence 来源并验证 Citation，Tool 分支对缺参、歧义实体和跨 Tool 依赖进行可恢复执行。

最后讲 Structured Trace 是横切层，它让每次路由、参数来源、Tool 调用、Evidence 和 Citation 都能被 Golden Case 自动检查。E3 是默认关闭的评测能力，只在被追问“答案为什么可信”时展开。

