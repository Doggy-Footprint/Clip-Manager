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
FIXTURE_EFFECTS="$ASSET_DIR/fixture_effects.mp4"
FIXTURE_EFFECTS_SILENT="$ASSET_DIR/fixture_effects_silent.mp4"

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

# Static quadrants make crop and axis-flip positions independently observable:
# top-left red, top-right green, bottom-left blue, bottom-right yellow.
EFFECTS_VIDEO_FILTER="color=c=black:s=640x360:r=30:d=10,drawbox=x=0:y=0:w=320:h=180:color=red:t=fill,drawbox=x=320:y=0:w=320:h=180:color=green:t=fill,drawbox=x=0:y=180:w=320:h=180:color=blue:t=fill,drawbox=x=320:y=180:w=320:h=180:color=yellow:t=fill"

"$FFMPEG" -y \
  -f lavfi -i "$EFFECTS_VIDEO_FILTER" \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000:duration=10" \
  -map 0:v -map 1:a \
  -c:v libx264 -pix_fmt yuv420p -g 30 -keyint_min 30 -sc_threshold 0 \
  -c:a aac -b:a 128k -ar 48000 \
  -movflags +faststart \
  "$FIXTURE_EFFECTS"

"$FFMPEG" -y \
  -f lavfi -i "$EFFECTS_VIDEO_FILTER" \
  -map 0:v \
  -c:v libx264 -pix_fmt yuv420p -g 30 -keyint_min 30 -sc_threshold 0 \
  -movflags +faststart \
  "$FIXTURE_EFFECTS_SILENT"

echo "Generated $FIXTURE_A"
echo "Generated $FIXTURE_B"
echo "Generated $FIXTURE_EFFECTS"
echo "Generated $FIXTURE_EFFECTS_SILENT"
