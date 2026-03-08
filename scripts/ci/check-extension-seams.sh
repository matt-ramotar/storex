#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

TARGETS=(
  "$ROOT_DIR/mutations/src/commonMain/kotlin"
  "$ROOT_DIR/paging/src/commonMain/kotlin"
  "$ROOT_DIR/normalization/runtime/src/commonMain/kotlin"
)

if command -v rg >/dev/null 2>&1; then
  search_tool="rg"
  set +e
  search_output=$(rg -n "core\\.internal" "${TARGETS[@]}" -g'*.kt' 2>&1)
  search_status=$?
  set -e
else
  search_tool="grep"
  set +e
  search_output=$(grep -R -n --include='*.kt' "core\\.internal" "${TARGETS[@]}" 2>&1)
  search_status=$?
  set -e
fi

case "$search_status" in
  0)
    printf '%s\n' "$search_output"
    echo "Extension source sets must not depend on core.internal." >&2
    exit 1
    ;;
  1)
    echo "Extension seam import guard passed."
    ;;
  *)
    echo "Extension seam import guard failed while running $search_tool." >&2
    if [ -n "$search_output" ]; then
      printf '%s\n' "$search_output" >&2
    fi
    exit "$search_status"
    ;;
esac
