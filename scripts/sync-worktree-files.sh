#!/usr/bin/env bash
# Copies the paths listed in .worktreeinclude from the main worktree into the current worktree.
# Existing files are never overwritten.
set -euo pipefail

target="$(git rev-parse --show-toplevel)"
main="$(git worktree list --porcelain | awk '/^worktree /{print substr($0, 10); exit}')"
[ "$main" = "$target" ] && exit 0

list="$main/.worktreeinclude"
[ -f "$list" ] || exit 0

while IFS= read -r entry || [ -n "$entry" ]; do
  case "$entry" in ''|'#'*) continue ;; esac
  path="${entry%/}"
  src="$main/$path"
  [ -e "$src" ] || continue
  mkdir -p "$(dirname "$target/$path")"
  if [ -d "$src" ]; then
    mkdir -p "$target/$path"
    cp -Rn "$src/." "$target/$path/"
  else
    cp -n "$src" "$target/$path"
  fi
  echo "worktree: copied $path"
done < "$list"
