#!/usr/bin/env bash
# The only sanctioned way to overwrite screenshot goldens: it opts out of the comparison that
# RoborazziConventionPlugin otherwise enforces, so review the resulting image diff before committing.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [ $# -eq 0 ]; then
  echo "usage: $0 <gradle test task> [more tasks or gradle args]" >&2
  echo "example: $0 :feature:browser:testDebugUnitTest --tests '*MediaGridScreenScreenshotTest*'" >&2
  exit 2
fi

./gradlew "$@" -Proborazzi.test.record=true -Pclip.goldens.record=true

echo
echo "Goldens rewritten. Review them before committing:"
git status --porcelain -- '*src/test/screenshots/*'
