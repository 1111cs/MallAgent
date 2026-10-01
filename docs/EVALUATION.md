# MallAgent Evaluation

## Scope

The frozen Golden V0 suite contains 20 end-to-end cases:

| Category | Cases | Purpose |
| --- | ---: | --- |
| KB | 5 | Knowledge routing and grounded answers |
| Normal Tool | 5 | Direct read-only business queries |
| Missing Parameter | 4 | Clarification without premature tool execution |
| Ambiguous Entity | 3 | Trusted candidate selection and continuation |
| Tool Dependency | 3 | Upstream result binding and staged execution |

## Frozen comparison

| Version | Result |
| --- | ---: |
| Original mechanism plus the same business adapter | 7/20 PASS |
| MallAgent after closed-loop governance | 20/20 PASS |

The comparison uses the same business contract, fixed fixture, identity mapping, clock and expected facts. Business adapters may validate inputs and expose read-only data, but they may not hard-code Golden Case answers or choose tools on behalf of the system under evaluation.

The final consolidated result has status `FINAL_GOLDEN_V0_20_CASE_PASSED` and is stored in `docs/huawei-mall-golden-v0-final-consolidated-2026-09-21.json`.

## Intent smoke test

The 24 physical intent leaves also have non-Golden paraphrase smoke cases. The final frozen run produced:

- correct physical leaf: 22/23 completed requests;
- correct resource route: 23/23 completed requests;
- one timeout recorded as not scored.

This distinction matters: two adjacent intent leaves may point to the same safe resource, so resource correctness and leaf-label accuracy are reported separately.

## Reproducibility boundary

Unit and contract tests are deterministic. The historical end-to-end model runs additionally depend on an external model, Embedding/Rerank services and imported vector data. Their frozen JSON is retained as an audit artifact; users should not interpret a provider timeout as a deterministic business assertion failure.

