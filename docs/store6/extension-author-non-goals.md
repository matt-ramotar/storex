# Extension Author Non-Goals (Phase 1)

This document defines what extension authors should **not** depend on while StoreX remains in Phase 1.

## Non-goals
- Do not depend on `core.internal` implementation details for long-term compatibility.
- Do not assume mutation queueing or replay guarantees from read-core primitives.
- Do not assume read-core owns background sync lifecycle.
- Do not assume normalization/paging internals are stable seams.
- Do not couple extension behavior to current incidental stream timing details beyond documented `Store` contracts.

## Required Boundaries
- Depend on read-core public contracts only:
  - `Store`
  - `StoreKey`
  - `Freshness`
  - `StoreResult`
- Treat `invalidate*` as stale-marking and `clear*` as destructive.
- Keep extension-specific policies (paging windows, normalization reconciliation, retry internals) scoped to extension modules.

## Deferred to Phase 2+
- Formal seam package and lifecycle hooks (`STOREX-2`)
- write/sync queueing contracts (`STOREX-3`)
- normalization/paging composable hardening (`STOREX-4`)
