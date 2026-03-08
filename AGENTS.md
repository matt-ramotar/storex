# StoreX AGENTS Overview

## Mission and Positioning

StoreX is the incubation track for the next evolution of `MobileNativeFoundation/Store`.

The guiding architecture is:

- `Store6` read operations are the stable, composable core.
- Writes, sync, normalization, paging, and resilience are optional extension layers on top of that core.
- Capabilities incubate in StoreX first, then only stable primitives graduate upstream.

## Current State Review and Assessment

### Strengths

- **Modular KMP structure**: clear modules for `core`, `mutations`, `resilience`, `paging`, and `normalization` (`runtime` + `ksp`).
- **Core read contract exists**: `Store`, keys, freshness policies, and stream/get APIs provide a strong base for standardizing read semantics.
- **Extension direction is established**: write/sync and advanced data-shaping concerns are already separated into dedicated modules.
- **Tooling baseline is in place**: publishing and CI paths exist, enabling iterative alpha maturation.

### Gaps and Risks

- **Contract vs implementation drift**: some behaviors described by policy/config surfaces are only partially enforced in runtime paths.
- **Maturity uneven across extensions**: normalization and some builder/runtime surfaces still contain TODO-level completeness gaps.
- **Current execution focus**: `STOREX-2` is migrating extensions onto `dev.mattramotar.storex.core.seams`, with mutations first and paging/normalization following the same contracts.
- **Reactive semantics need hard guarantees**: long-lived stream behavior and invalidation/freshness expectations need contract-test enforcement.
- **Ecosystem readiness gap**: docs, migration guidance, and release narrative are not yet at parity with upstream Store expectations.

## Architectural Boundaries

### Store6 Read Core (stable boundary)

The read core should remain small and explicit:

- `Store` read APIs (`get`, `stream`) and invalidation semantics.
- `StoreKey` and namespace identity rules.
- `Freshness` semantics and evaluation rules.
- `StoreResult` success/loading/error behavior contracts.
- Core cache/source-of-truth/fetch orchestration invariants.

The read core should not own:

- Write queueing, retries, conflict resolution, or idempotency orchestration.
- Background sync lifecycle management.
- Normalization graph behavior, paging policy behavior, or resilience policy internals.

### Extension Seam Expectations

- Extensions depend on stable seam contracts only, not core internals.
- Extensions are independently adoptable and removable.
- Core upgrades should not require extension rewrites unless seam contracts change.
- Cross-extension composition (for example, paging + normalization + sync) must be explicit and testable.

## Roadmap Overview (Store6-first)

### Phase 1 - Freeze Store6 Read Core

- Lock read-core boundaries and semantics.
- Add cross-target contract tests for read/invalidation/freshness behavior.
- Linear anchor: `STOREX-1` (Done).

### Phase 2 - Extension Seams

- Introduce explicit extension seam contracts and lifecycle hooks.
- Publish seam contracts in `dev.mattramotar.storex.core.seams`.
- Migrate mutations first, then paging and normalization, to the seam package.
- Linear anchor: `STOREX-2` (In Progress).

### Phase 3 - Sync/Write Extension Track (RFC697-aligned)

- Deliver mutation queueing, replay, idempotency, and conflict handling as extension behavior.
- Integrate resilience and reconciliation behavior without expanding read-core scope.
- Linear anchor: `STOREX-3`.

### Phase 4 - Normalization + Paging Add-ons

- Complete normalization runtime/DSL track as optional add-on.
- Harden paging behavior as optional add-on that composes with read freshness.
- Linear anchor: `STOREX-4`.

### Phase 5 - Upstream Stable Read Primitives

- Promote proven read primitives to `MobileNativeFoundation/Store`.
- Publish migration notes and compatibility mapping.
- Linear anchor: `STOREX-5`.

### Phase 6 - Graduation Policy

- Apply objective gates for selective extension promotion upstream.
- Keep incubating tracks in StoreX until gates are met.
- Linear anchor: `STOREX-6`.

### Dependency Flow

```mermaid
flowchart LR
  p1["STOREX-1 FreezeReadCore"] --> p2["STOREX-2 ExtensionSeams"]
  p2 --> p3["STOREX-3 SyncWriteExtensions"]
  p3 --> p4["STOREX-4 NormalizationPaging"]
  p4 --> p5["STOREX-5 UpstreamReadCore"]
  p5 --> p6["STOREX-6 GraduationPolicy"]
```

## Promotion Gates for Upstreaming

An item should graduate from StoreX to upstream Store only when all gates pass:

- API stability across at least two alpha cuts.
- Contract-test coverage passes on supported KMP targets.
- Reliability/performance thresholds are met in stress and failure-path scenarios.
- Migration docs and examples are published and reviewed.
- Maintainer sign-off exists in both StoreX and upstream Store workflows.

## Execution Anchors

- Project: Linear `6.0` (`storex` team).
- Issues: `STOREX-1` through `STOREX-6`.
- Current sequencing: `STOREX-1 -> STOREX-2 -> STOREX-3 -> STOREX-4 -> STOREX-5 -> STOREX-6`.
- Active seam package: `dev.mattramotar.storex.core.seams`.

## Maintenance Guidance

Update this file when:

- a phase starts, scope changes, or completes,
- read-core boundaries are changed,
- seam contracts are added/removed,
- an extension passes/fails graduation gates,
- or upstream promotion decisions are made.

Keep this file decision-oriented and stable: record directional changes and promotion outcomes, not implementation minutiae.
