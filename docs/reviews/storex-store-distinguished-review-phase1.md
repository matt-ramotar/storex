# Store vs StoreX Phase 1 Review

## Reviewer Lens
Distinguished software engineering review for public API durability, migration risk, and developer adoption readiness.

## Executive Summary
StoreX Phase 1 now has a clearer read-core contract than the pre-freeze baseline, primarily due to the explicit non-destructive `invalidate*` vs destructive `clear*` split and new contract-focused test coverage.  
Primary remaining risks are seam leaks (`core.internal`) in extension modules and inconsistent reactive SoT guarantees outside core.

## Store vs StoreX Delta (Phase 1)
| Area | Upstream Store (reference) | StoreX after Phase 1 | Risk |
|---|---|---|---|
| Read contracts | Mature read semantics and freshness story | Explicitly frozen in docs/tests | Low |
| Invalidation semantics | Historically nuanced/implementation-sensitive | Now explicitly split: invalidate vs clear | Low |
| Cross-target confidence | Mature CI history | Added Linux + macOS iOS-simulator lanes | Medium |
| Extension seams | Established conceptually | Still partially coupled to internals | High |
| Mutation/read parity | Cohesive but evolving | Read/Mutation invalidation semantics aligned | Medium |

## What Improved in Phase 1
- API contract drift reduced by introducing clear destructive methods.
- Runtime now treats invalidation as stale-marking and refetch signaling.
- Dedicated read-core contract suite added with platform wrappers.
- CI now has explicit macOS iOS simulator validation lane.

## Critical Follow-up Risks (Phase 2 candidates)
1. `mutations` and `normalization` expose or rely on `core.internal`.
2. Some SoT implementations in extension builders are one-shot and weaker than read-core reactive expectations.
3. Namespace/all destructive operations are best-effort for unknown persisted keys unless SoT-specific enumeration/deletion hooks are provided.

## Developer Experience Guidance
- Keep docs explicit about `invalidate*` vs `clear*` semantics.
- Keep examples extension-agnostic for read-core primitives.
- Avoid promoting internal types in public snippets and typealiases.

## Recommendation
Proceed with Phase 1 closure and immediately prioritize seam extraction (`STOREX-2`) to prevent Phase 1 guarantees from being diluted by extension coupling.
