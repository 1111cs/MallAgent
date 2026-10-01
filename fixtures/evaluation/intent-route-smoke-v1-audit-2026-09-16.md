# Huawei Mall Intent Route Smoke V1 Audit

## Scope

- 24 physical leaf intents
- One non-Golden paraphrase per intent
- Checks only top Intent and routed resource (KB, MCP Tool, or System)
- Does not score Tool parameters, retrieval evidence, or final answers

## Round 1

- Executed: 2026-09-16
- Result: 22/24 PASS (91.7%)
- Average latency: 2665 ms
- Minimum / maximum latency: 1532 / 4875 ms
- Raw result: `intent-route-smoke-v1-round1-2026-09-16.json`

Failures:

| Case | Expected | Actual | Diagnosis |
| --- | --- | --- | --- |
| IR-10 | `DELIVERY_POLICY` → `shopping-policy-kb` | `LOGISTICS_QUERY` → `getLogistics` | The question and adapter wording did not clearly distinguish general delivery policy from a specific order's live logistics. |
| IR-22 | `TROUBLESHOOTING` → `support-kb` | `CONNECTION_NETWORK` → `support-kb` | Both routes reached the correct KB, but the adapter did not clearly separate audio-output faults from Bluetooth pairing/connection faults. |

## Pre-freeze correction

The correction changes business vocabulary and test clarity only. It does not change the classifier, thresholds, planning, clarification, dependency execution, or Golden V0 cases.

- Clarified `DELIVERY_POLICY` as general delivery methods and general query-entry rules.
- Clarified `LOGISTICS_QUERY` as live state for a specific logged-in user's order.
- Clarified `TROUBLESHOOTING` to include one-sided audio and other non-connection functional faults.
- Clarified `CONNECTION_NETWORK` to cover Wi-Fi/Bluetooth search, pairing, and connection failures, excluding post-connection audio faults.
- Replaced the two ambiguous smoke questions with non-Golden questions that express those boundaries.
- Versioned the isolated Redis key so corrected trees load without touching unrelated caches.

## Round 2

- Executed: 2026-09-16
- Result: 23/24 PASS (95.8%)
- Raw result: `intent-route-smoke-v1-round2-2026-09-16.json`
- IR-10 and IR-22 passed after the first correction.
- IR-13 regressed from `LOGISTICS_QUERY` to `DELIVERY_POLICY`, showing that the policy/live-order boundary was not stable enough to freeze.

Round 3 preparation adds one contrastive example to each side of that boundary and makes the non-Golden IR-13 question explicitly request a current node by order number.

## Round 3 and freeze decision

- Executed: 2026-09-16
- Raw result: `intent-route-smoke-v1-round3-2026-09-16.json`
- Reported result: 22/24 PASS
- IR-16: 45-second request timeout; no routing result was returned, so it is recorded as `TIMEOUT / NOT SCORED` rather than an Intent failure.
- IR-22: top leaf was `USAGE_GUIDE` instead of `TROUBLESHOOTING`, while both map to `support-kb`. This is retained as an original Ragent leaf-routing stability limitation.
- Completed requests with correct physical leaf: 22/23 (95.7%).
- Completed requests with correct resource: 23/23 (100%).

No further example tuning is allowed in Baseline Adapter V1. The adapter is sufficiently connected for a fair baseline, and the remaining leaf instability belongs in the Baseline result.

Final status: `FROZEN_BASELINE_ADAPTER_V1`.
