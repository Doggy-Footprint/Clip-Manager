#!/usr/bin/env bash
# core.hooksPath points at the harness hooks, which chain to $GIT_COMMON_DIR/hooks/<name> for
# project checks. That directory is not tracked, so a fresh clone starts without them; the Gradle
# build calls this so no manual setup step can be skipped. Worktrees share the common dir and are
# therefore covered by a single install.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
hooks_dir="$(git rev-parse --git-common-dir)/hooks"
mkdir -p "$hooks_dir"

stub='#!/usr/bin/env bash
set -euo pipefail
exec "$(git rev-parse --show-toplevel)/scripts/check-screenshot-config.sh"'

target="$hooks_dir/pre-commit"
if [ -e "$target" ] && [ "$(cat "$target")" != "$stub" ]; then
  echo "install-git-hooks: $target exists and differs; leaving it alone" >&2
  exit 0
fi

printf '%s\n' "$stub" > "$target"
chmod +x "$target"
