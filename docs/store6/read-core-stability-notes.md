# Store6 Read-Core Stability Notes

## Milestone
- Linear issue: `STOREX-1`
- Phase: `Phase 1 complete`, `Phase 2 in progress`

## Frozen Surface
The following API surface is frozen for the first 6.0 alpha contract:
- `Store.get`
- `Store.stream`
- `Store.invalidate`, `Store.invalidateNamespace`, `Store.invalidateAll`
- `Store.clear`, `Store.clearNamespace`, `Store.clearAll`
- `StoreKey` and `StoreNamespace`
- `Freshness`
- `StoreResult`

## Semantic Guarantees
- `invalidate*` is non-destructive:
  - evicts memory
  - clears SoT cache state
  - does not delete persisted SoT data
  - triggers refetch for active streams
- `clear*` is destructive:
  - evicts memory
  - clears SoT cache state
  - deletes persisted SoT data for impacted keys
- `MustBeFresh` remains blocking and terminal on fetch failure.
- `CachedOrFetch`, `MinAge`, and `StaleIfError` retain current runtime behavior.

## Coverage/Validation Expectations
- Read-core contract tests in `core/src/commonTest/kotlin/contract`.
- Explicit target smoke wrappers in:
  - `core/src/jvmTest/kotlin/contract`
  - `core/src/jsTest/kotlin/contract`
  - `core/src/nativeTest/kotlin/contract`
- CI requires:
  - Linux: `./gradlew clean build koverXmlReport --stacktrace`
  - macOS: iOS simulator test lane for core + extension modules.

## Known Limitations Carried Forward
- `StoreResult.Loading(fromCache=true)` remains unexercised in read-core runtime.
- Full upstream migration narrative remains phase-gated to `STOREX-5`.

## Phase 2 Seam Note
- Stable extension contracts now live in `dev.mattramotar.storex.core.seams`.
- Public builder surfaces may expose seam types, but concrete runtimes remain internal implementation details.
- `STOREX-2` migrates mutations first, then paging and normalization, without changing the frozen read-core semantics above.
