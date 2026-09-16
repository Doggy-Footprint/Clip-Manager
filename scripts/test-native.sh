#!/usr/bin/env bash
# Compiles and runs the host-only SeekController test. Works from any cwd
# because all paths are resolved from this script's own location, not the
# caller's working directory.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PLAYER_DIR="$REPO_ROOT/core/player/src/main/cpp/player"
TEST_SRC="$REPO_ROOT/core/player/src/main/cpp/test/seek_controller_test.cpp"
IMPL_SRC="$PLAYER_DIR/SeekController.cpp"

if [ ! -f "$TEST_SRC" ]; then
    echo "test-native.sh: missing test source: $TEST_SRC" >&2
    exit 1
fi

SOURCES=("$TEST_SRC")
# SeekController may be header-only; only add the .cpp if it exists.
if [ -f "$IMPL_SRC" ]; then
    SOURCES+=("$IMPL_SRC")
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BIN="$TMP_DIR/seek_controller_test"

c++ -std=c++17 -pthread -I "$PLAYER_DIR" "${SOURCES[@]}" -o "$BIN"

"$BIN"
