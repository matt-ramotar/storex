# Extension Author Non-Goals (Phase 2)

This document defines what extension authors should **not** depend on while StoreX remains in Phase 2 seam extraction.

## Non-goals
- Do not depend on `core.internal` implementation details for long-term compatibility.
- Do not assume mutation queueing or replay guarantees from read-core primitives.
- Do not assume read-core owns background sync lifecycle.
- Do not assume normalization/paging internals widen the seam beyond `core.seams`.
- Do not couple extension behavior to current incidental stream timing details beyond documented `Store` contracts.

## Required Boundaries
- Depend on read-core public contracts and the explicit seam package only:
  - `Store`
  - `StoreKey`
  - `Freshness`
  - `StoreResult`
  - `dev.mattramotar.storex.core.seams.*`
- Treat `invalidate*` as stale-marking and `clear*` as destructive.
- Keep extension-specific policies (paging windows, normalization reconciliation, retry internals) scoped to extension modules.

## Deferred to Phase 3+
- write/sync queueing contracts (`STOREX-3`)
- normalization/paging composable hardening (`STOREX-4`)
