#!/usr/bin/env bash
# Fail if smali references a Java class that was migrated to src/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SMALI_DIR="light_camera/smali"
SRC_DIR="src"
errors=0

if [[ ! -d "$SMALI_DIR" ]]; then
  echo "No smali directory: $SMALI_DIR"
  exit 0
fi

while IFS= read -r smali_file; do
  base="$(basename "$smali_file" .smali)"
  if [[ "$base" != -* ]]; then
    continue
  fi

  if [[ "$base" =~ \$\$Lambda\$([^.]+)\$ ]]; then
    owner="${BASH_REMATCH[1]}"
    java_file="$(find "$SRC_DIR" -name "${owner}.java" | head -1)"
    if [[ -n "$java_file" ]]; then
      echo "ORPHAN: $smali_file (owner migrated to $java_file)"
      errors=$((errors + 1))
    fi
  fi
done < <(find "$SMALI_DIR" -name '*.smali')

if (( errors > 0 )); then
  echo "Found $errors orphan smali file(s). Remove or migrate the owner class."
  exit 1
fi

echo "No orphan smali lambdas found."