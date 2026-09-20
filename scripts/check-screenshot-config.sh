#!/usr/bin/env bash
# Screenshot tests are only an oracle while verify mode is on; a commit that drops it would make
# every golden-backed test pass unconditionally.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

fail() { echo "check-screenshot-config: $1" >&2; exit 1; }

props="$(git show ":gradle.properties" 2>/dev/null || cat gradle.properties)"

grep -qx 'roborazzi.test.verify=true' <<<"$props" ||
  fail "gradle.properties must keep 'roborazzi.test.verify=true'"

grep -qE '^(roborazzi\.test\.record|clip\.goldens\.record)=true' <<<"$props" &&
  fail "record mode must not be committed in gradle.properties; use scripts/record-goldens.sh"

exit 0
