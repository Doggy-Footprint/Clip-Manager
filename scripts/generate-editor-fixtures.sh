#!/usr/bin/env bash
# Generates the editor test fixtures (contract: agent-docs/contracts/editor-engine.md, C29-C36).
#
# Fixture A: 10s, 640x360, 30fps, H.264 with a keyframe exactly every 1s (30 frames),
#            ten distinct 1s solid-color segments, AAC 48kHz 1kHz sine wave audio, mp4.
# Fixture B: fixture A's video re-muxed with Vorbis audio instead of AAC, mkv container.
set -euo pipefail

FFMPEG="${FFMPEG:-/opt/homebrew/bin/ffmpeg}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_DIR="$REPO_ROOT/core/editor/src/androidTest/assets"
mkdir -p "$ASSET_DIR"

FIXTURE_A="$ASSET_DIR/fixture_a.mp4"
FIXTURE_B="$ASSET_DIR/fixture_b.mkv"

# Ten distinct ffmpeg/X11 color names, one per second, in order.
COLORS=(red orange yellow green cyan blue purple magenta white gray)

FILTER=""
for i in "${!COLORS[@]}"; do
  FILTER+="color=c=${COLORS[$i]}:s=640x360:r=30:d=1[c${i}];"
done
for i in "${!COLORS[@]}"; do
  FILTER+="[c${i}]"
done
FILTER+="concat=n=${#COLORS[@]}:v=1:a=0[outv]"

"$FFMPEG" -y \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000:duration=10" \
  -filter_complex "$FILTER" \
  -map "[outv]" -map 0:a \
  -c:v libx264 -pix_fmt yuv420p -g 30 -keyint_min 30 -sc_threshold 0 \
  -c:a aac -b:a 128k -ar 48000 \
  -movflags +faststart \
  "$FIXTURE_A"

"$FFMPEG" -y -i "$FIXTURE_A" \
  -c:v copy -c:a vorbis -ac 2 -q:a 4 -strict -2 \
  "$FIXTURE_B"

echo "Generated $FIXTURE_A"
echo "Generated $FIXTURE_B"
