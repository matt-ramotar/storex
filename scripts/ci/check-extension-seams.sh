#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

TARGETS=(
  "$ROOT_DIR/mutations/src/commonMain/kotlin"
  "$ROOT_DIR/paging/src/commonMain/kotlin"
  "$ROOT_DIR/normalization/runtime/src/commonMain/kotlin"
)

if rg -n "core\\.internal" "${TARGETS[@]}" -g'*.kt'; then
  echo "Extension source sets must not depend on core.internal." >&2
  exit 1
fi

echo "Extension seam import guard passed."
