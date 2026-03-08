# Store6 Extension Seams

## Purpose
`STOREX-2` defines the stable contracts that extension modules may depend on without reaching into `core.internal`.

The seam lives in `dev.mattramotar.storex.core.seams` inside `:core` so extensions can adopt it without adding a new Gradle module.

## Stable Seam Surface
- Fetch coordination:
  - `Fetcher`
  - `FetcherResult`
  - `FetchRequest`
  - `ConditionalRequest`
  - `Urgency`
  - `fetcherOf`
  - `streamingFetcherOf`
  - `StoreException`
- Freshness planning:
  - `FreshnessContext`
  - `FetchPlan`
  - `FreshnessValidator`
  - `DefaultDbMeta`
  - `DefaultFreshnessValidator`
- Local coordination:
  - `SourceOfTruth`
  - `MemoryCache`
  - `Bookkeeper`
  - `KeyStatus`

## Phase 2 Rules
- Extension modules must import seam contracts from `core.seams`, never from `core.internal`.
- Mutations are the first migration target and define the minimum viable seam.
- Paging and normalization may add extension-local policies on top of the seam, but they must not widen the seam during Phase 2.
- Queueing, replay, sync lifecycle, and resilience orchestration remain Phase 3+ concerns.

## Validation
- Mutations and paging include seam compatibility tests.
- CI runs `bash scripts/ci/check-extension-seams.sh` to reject new `core.internal` imports in extension source sets.
- `STOREX-3` stays blocked until seam migrations are complete and these checks stay green.
