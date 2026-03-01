# Store6 Read-Core Contracts

## Scope
This document defines the frozen read-core contracts for StoreX Phase 1 (`STOREX-1`).

The read-core contains:
- `Store` read and invalidation APIs
- `StoreKey` and `StoreNamespace` identity semantics
- `Freshness` planning semantics
- `StoreResult` state and metadata semantics
- cache/SoT/fetch orchestration guarantees for reads

The read-core excludes:
- mutation queueing/replay
- background sync orchestration
- normalization graph composition internals
- paging policy internals
- resilience policy internals

## Invalidation vs Clear

### Invalidate (non-destructive)
`invalidate`, `invalidateNamespace`, and `invalidateAll` are stale-marking operations:
- evict in-memory cache
- clear in-memory/flow cache state in SoT where available
- trigger active stream refetch
- do not delete persisted SoT rows

### Clear (destructive)
`clear`, `clearNamespace`, and `clearAll` are destructive operations:
- evict in-memory cache
- clear SoT cache state
- delete persisted SoT rows for affected keys

## Freshness Contract
`Freshness` policies remain:
- `CachedOrFetch`
- `MinAge`
- `MustBeFresh`
- `StaleIfError`

The contract requires that freshness planning and runtime behavior stay aligned across `get` and `stream`.

## Stream Contract
`stream` remains long-lived and emits:
- `Loading` when no cached value exists
- `Data` from SoT updates
- `Error` when fetch fails (with stale metadata where applicable)

Active streams must continue receiving updates until cancellation.

## Non-goals for Phase 1
- introducing extension seams
- removing all extension coupling to `core.internal`
- changing mutation conflict resolution policies
- changing paging policy model
