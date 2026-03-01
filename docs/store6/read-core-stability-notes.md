# Store6 Read-Core Stability Notes (Phase 1)

## Milestone
- Linear issue: `STOREX-1`
- Phase: `Phase 1 - Freeze Store6 Read Core`

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
- Some extension modules still depend on `core.internal` and are addressed in `STOREX-2`.
- `StoreResult.Loading(fromCache=true)` remains unexercised in read-core runtime.
- Full upstream migration narrative remains phase-gated to `STOREX-5`.
