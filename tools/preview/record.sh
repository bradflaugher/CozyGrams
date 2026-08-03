#!/usr/bin/env bash
#
# Records the CozyGrams UI *in motion* to video on a desktop JVM — no emulator, no device.
#
#   tools/preview/record.sh [outputDir] [width] [height] [fps] [clip]
#
# Compiles the same Java2D-backed android.graphics stubs and pure rendering sources that
# render.sh does, then winds the harness's deterministic clock forward one device frame at
# a time while replaying a script of controller presses. Each clip is written out three
# ways: an .mp4 to judge the timing by, an animated .gif to glance at, and a contact sheet
# PNG that tiles eight evenly spaced frames with their millisecond offsets burned in.
#
# Still frames answer "does it look right". These answer "does it *feel* right", which is
# the half of a cozy interface a screenshot cannot show. Works from any working directory;
# everything is resolved relative to this script — though running it from the repository
# root is what lets the contact sheets name the commit and source fingerprint they were
# recorded from, the same line render.sh burns into the corner of every still.
#
# Deliberately self-contained rather than sharing a preamble with render.sh: the two are
# read end to end far more often than they are edited, and neither should be able to break
# the other.
#
# Environment:
#   COZY_BUILD_DIR     private scratch space for classes and decoded backdrops
#   COZY_KEEP_FRAMES   set to 1 to keep the PNG sequences instead of deleting them
#   COZY_GIF_WIDTH     width of the animated GIFs, default 960

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

# Several people may record at once. COZY_BUILD_DIR gives each their own scratch space so
# one run's `rm -rf classes` never pulls the rug from under another's compile.
BUILD_DIR="${COZY_BUILD_DIR:-$SCRIPT_DIR/build}"
CLASSES_DIR="$BUILD_DIR/classes"
ASSET_DIR="$BUILD_DIR/assets"
STUB_DIR="$SCRIPT_DIR/stubs"
APP_SRC="$REPO_ROOT/app/src/main/java/com/cozygrams/tv"
RES_DIR="$REPO_ROOT/app/src/main/res/drawable-nodpi"

OUT_DIR="${1:-$SCRIPT_DIR/clips}"
WIDTH="${2:-1920}"
HEIGHT="${3:-1080}"
FPS="${4:-30}"
ONLY="${5:-}"
case "$OUT_DIR" in
  /*) ;;
  *) OUT_DIR="$PWD/$OUT_DIR" ;;
esac

GIF_WIDTH="${COZY_GIF_WIDTH:-960}"

# The app sources that are pure rendering/model code — the same set render.sh compiles.
# Everything else in the package needs a real View, Activity, audio device or input
# handling, and the whole point of the harness is that the picture does not depend on them.
RENDER_SOURCES=(
  Theme.java Draw.java Effects.java BoardLayout.java UiState.java Comfort.java
  Backdrop.java BoardRenderer.java CursorRenderer.java HudScene.java
  HomeScene.java SettingsScene.java WinScene.java Renderer.java
  GameState.java Puzzle.java PuzzleGenerator.java PuzzleLibrary.java
  NonogramSolver.java
)

# Compile-time-only dependencies: not drawing code, but reachable from it. SettingsScene
# calls SaveStore.restoreDefaults, so SaveStore has to compile even though the harness
# never instantiates it — hence the android.content / android.os stubs.
SUPPORT_SOURCES=(
  SaveStore.java
)

fail() {
  echo "record.sh: $*" >&2
  exit 1
}

# ---- Toolchain -------------------------------------------------------------------

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAVA="$JAVA_HOME/bin/java"
elif [[ -x /root/.cache/cozy-jdk/bin/javac ]]; then
  JAVAC=/root/.cache/cozy-jdk/bin/javac
  JAVA=/root/.cache/cozy-jdk/bin/java
else
  command -v javac >/dev/null || fail "no javac found; set JAVA_HOME to a JDK 17+"
  JAVAC="$(command -v javac)"
  JAVA="$(command -v java)"
fi

command -v ffmpeg >/dev/null || fail "ffmpeg is needed to encode the clips"

# A clip is sampled out of a 60 Hz simulation, so the capture rate has to divide 60 — see
# the note on DEVICE_HZ in Preview.java for why the simulation is not simply run at the
# capture rate.
case "$FPS" in
  60|30|20|15|12|10|6|5) ;;
  *) fail "fps must divide 60 exactly (60, 30, 20, 15, 12, 10, 6, 5), not $FPS" ;;
esac

# ---- Backdrops -------------------------------------------------------------------
#
# The shipped backdrops are WebP, which java.imageio cannot decode. Convert them into
# throwaway PNGs under build/ so the Java side stays dependency-free. Nothing is ever
# written back into app/src/main/res.

convert_backdrop() {
  local src="$1" dst="$2"
  [[ -f "$src" ]] || fail "missing backdrop $src"
  if [[ -f "$dst" && "$dst" -nt "$src" ]]; then
    return 0
  fi
  if python3 -c 'from PIL import Image' >/dev/null 2>&1; then
    python3 - "$src" "$dst" <<'PY'
import sys
from PIL import Image
with Image.open(sys.argv[1]) as image:
    image.convert("RGB").save(sys.argv[2], "PNG")
PY
  else
    ffmpeg -y -loglevel error -i "$src" "$dst"
  fi
  [[ -s "$dst" ]] || fail "backdrop conversion produced nothing for $src"
}

mkdir -p "$ASSET_DIR"
convert_backdrop "$RES_DIR/cozy_room.webp" "$ASSET_DIR/cozy_room.png"
convert_backdrop "$RES_DIR/moon_garden.webp" "$ASSET_DIR/moon_garden.png"

# ---- Compile ---------------------------------------------------------------------

sources=()
while IFS= read -r -d '' stub; do
  sources+=("$stub")
done < <(find "$STUB_DIR" -name '*.java' -print0 | sort -z)

for name in "${RENDER_SOURCES[@]}" "${SUPPORT_SOURCES[@]}"; do
  [[ -f "$APP_SRC/$name" ]] || fail "missing app source $APP_SRC/$name"
  sources+=("$APP_SRC/$name")
done
sources+=("$SCRIPT_DIR/Preview.java")

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

# -sourcepath lets javac follow the app's real dependency graph if the render set picks up
# another intra-package helper, instead of hard-failing on the next refactor.
echo "record.sh: compiling ${#sources[@]} sources -> $CLASSES_DIR"
"$JAVAC" -nowarn -Xlint:-options -encoding UTF-8 -source 17 -target 17 \
  -implicit:class -sourcepath "$REPO_ROOT/app/src/main/java" \
  -d "$CLASSES_DIR" "${sources[@]}"

# ---- Encoder -----------------------------------------------------------------------
#
# H.264 first, in whichever software encoder this ffmpeg was built with, because that is
# what plays everywhere a reviewer might open the file. MPEG-4 part 2 is the fallback: an
# .mp4 that plays in VLC beats no .mp4 at all.

encoders="$(ffmpeg -hide_banner -encoders 2>/dev/null || true)"
# ~0.12 bits per pixel: visually lossless on flat cozy panels, and small enough to attach.
BITRATE_K=$(( WIDTH * HEIGHT * FPS * 12 / 100000 ))
if grep -q ' libx264 ' <<<"$encoders"; then
  VIDEO_CODEC=(-c:v libx264 -preset slow -crf 18)
  CODEC_NAME="libx264"
elif grep -q ' libopenh264 ' <<<"$encoders"; then
  VIDEO_CODEC=(-c:v libopenh264 -b:v "${BITRATE_K}k")
  CODEC_NAME="libopenh264"
else
  VIDEO_CODEC=(-c:v mpeg4 -q:v 3)
  CODEC_NAME="mpeg4"
fi

# ---- Record ------------------------------------------------------------------------

mkdir -p "$OUT_DIR"
FRAME_ROOT="$OUT_DIR/frames"

clips=()
while IFS= read -r line; do
  [[ -n "$line" ]] && clips+=("$line")
done < <("$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
  -cp "$CLASSES_DIR" Preview --list-clips)
(( ${#clips[@]} > 0 )) || fail "Preview listed no clips"

if [[ -n "$ONLY" ]]; then
  known=0
  for line in "${clips[@]}"; do
    [[ "${line%%$'\t'*}" == "$ONLY" ]] && known=1
  done
  (( known == 1 )) || fail "no clip called \"$ONLY\"; there is ${clips[*]%%$'\t'*}"
fi

require_size() {
  local file="$1" min="$2" size
  [[ -f "$file" ]] || fail "$file was never written"
  size="$(stat -c%s "$file")"
  (( size >= min )) || fail "$file is only $size bytes, expected at least $min"
  printf '%s' "$size"
}

kb() {
  printf '%d KB' $(( ($1 + 1023) / 1024 ))
}

echo "record.sh: recording at ${WIDTH}x${HEIGHT}, ${FPS} fps, video via $CODEC_NAME"
echo

report=()
for line in "${clips[@]}"; do
  name="${line%%$'\t'*}"
  duration="${line##*$'\t'}"
  if [[ -n "$ONLY" && "$name" != "$ONLY" ]]; then
    continue
  fi

  frames="$FRAME_ROOT/$name"
  rm -f "$OUT_DIR/$name.mp4" "$OUT_DIR/$name.gif" "$OUT_DIR/$name-contact.png"

  "$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
    -Dcozy.assets="$ASSET_DIR" -cp "$CLASSES_DIR" \
    Preview --clips "$OUT_DIR" "$WIDTH" "$HEIGHT" "$FPS" "$name"

  count=$(find "$frames" -name 'frame-*.png' | wc -l)
  (( count > 1 )) || fail "$name produced $count frames"

  # An even-sized picture, because yuv420p halves both axes and an odd one is rejected.
  ffmpeg -y -loglevel error -start_number 0 -framerate "$FPS" \
    -i "$frames/frame-%04d.png" \
    -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" \
    "${VIDEO_CODEC[@]}" -pix_fmt yuv420p -movflags +faststart \
    "$OUT_DIR/$name.mp4"

  # Two passes for the GIF: one palette per clip beats the 216-colour web palette by a
  # mile on warm cream paper, where banding shows up as visible contour lines. The frame
  # rate is left alone — ffmpeg alternates 3 and 4 centisecond delays around 30 fps, so
  # the GIF runs for exactly as long as the clip does rather than eleven percent fast.
  ffmpeg -y -loglevel error -start_number 0 -framerate "$FPS" \
    -i "$frames/frame-%04d.png" \
    -vf "scale=$GIF_WIDTH:-2:flags=lanczos,palettegen=stats_mode=diff" \
    "$BUILD_DIR/gif-palette.png"
  ffmpeg -y -loglevel error -start_number 0 -framerate "$FPS" \
    -i "$frames/frame-%04d.png" -i "$BUILD_DIR/gif-palette.png" \
    -lavfi "scale=$GIF_WIDTH:-2:flags=lanczos[s];[s][1:v]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle" \
    -loop 0 "$OUT_DIR/$name.gif"

  mp4_size="$(require_size "$OUT_DIR/$name.mp4" 20000)"
  gif_size="$(require_size "$OUT_DIR/$name.gif" 40000)"
  sheet_size="$(require_size "$OUT_DIR/$name-contact.png" 100000)"

  if [[ "${COZY_KEEP_FRAMES:-0}" != "1" ]]; then
    rm -rf "$frames"
  fi

  report+=("$(printf '  %-18s %5s ms  %4s frames   mp4 %-9s gif %-9s sheet %s' \
    "$name" "$duration" "$count" "$(kb "$mp4_size")" "$(kb "$gif_size")" \
    "$(kb "$sheet_size")")")
done

if [[ "${COZY_KEEP_FRAMES:-0}" != "1" ]]; then
  rmdir "$FRAME_ROOT" 2>/dev/null || true
fi

# ---- Report ------------------------------------------------------------------------

echo
echo "Clips in $OUT_DIR:"
for row in "${report[@]}"; do
  echo "$row"
done
echo
echo "record.sh: ${#report[@]} clips. Watch an .mp4 for the timing, read a -contact.png"
echo "           for the beats it is made of."
