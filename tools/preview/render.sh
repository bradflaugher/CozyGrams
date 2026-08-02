#!/usr/bin/env bash
#
# Renders the CozyGrams UI to PNG on a desktop JVM — no emulator, no device.
#
#   tools/preview/render.sh [outputDir] [width] [height]
#
# Compiles the Java2D-backed android.graphics stubs together with the app's pure
# rendering sources, then runs the scene driver headless. Works from any working
# directory; everything is resolved relative to this script.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

# Several people may render at once. COZY_BUILD_DIR gives each their own scratch space
# so one run's `rm -rf classes` never pulls the rug from under another's compile.
BUILD_DIR="${COZY_BUILD_DIR:-$SCRIPT_DIR/build}"
CLASSES_DIR="$BUILD_DIR/classes"
ASSET_DIR="$BUILD_DIR/assets"
STUB_DIR="$SCRIPT_DIR/stubs"
APP_SRC="$REPO_ROOT/app/src/main/java/com/cozygrams/tv"
RES_DIR="$REPO_ROOT/app/src/main/res/drawable-nodpi"

OUT_DIR="${1:-$SCRIPT_DIR/out}"
WIDTH="${2:-1920}"
HEIGHT="${3:-1080}"
case "$OUT_DIR" in
  /*) ;;
  *) OUT_DIR="$PWD/$OUT_DIR" ;;
esac

# The app sources that are pure rendering/model code. Everything else in the package
# needs a real View, Activity, audio device or input handling and is deliberately
# excluded — the whole point of the harness is that the picture does not depend on them.
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
  echo "render.sh: $*" >&2
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
  elif command -v ffmpeg >/dev/null 2>&1; then
    ffmpeg -y -loglevel error -i "$src" "$dst"
  else
    fail "cannot decode WebP: install Pillow (python3-pil) or ffmpeg"
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

# -sourcepath lets javac follow the app's real dependency graph if the render set picks
# up another intra-package helper, instead of hard-failing on the next refactor. Anything
# it drags in is reported below, so the harness never quietly swallows half the app.
echo "render.sh: compiling ${#sources[@]} sources -> $CLASSES_DIR"
"$JAVAC" -nowarn -Xlint:-options -encoding UTF-8 -source 17 -target 17 \
  -implicit:class -sourcepath "$REPO_ROOT/app/src/main/java" \
  -d "$CLASSES_DIR" "${sources[@]}"

extra=""
while IFS= read -r cls; do
  case " ${RENDER_SOURCES[*]} ${SUPPORT_SOURCES[*]} " in
    *" $cls.java "*) ;;
    *) extra+=" $cls" ;;
  esac
done < <(find "$CLASSES_DIR/com/cozygrams/tv" -name '*.class' -printf '%f\n' 2>/dev/null \
  | sed 's/\.class$//' | sed 's/\$.*$//' | sort -u)
if [[ -n "$extra" ]]; then
  echo "render.sh: note - also pulled in from the app package:$extra"
fi

# ---- Run -------------------------------------------------------------------------

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*.png

echo "render.sh: rendering"
"$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
  -Dcozy.assets="$ASSET_DIR" -cp "$CLASSES_DIR" Preview "$OUT_DIR" "$WIDTH" "$HEIGHT"

# ---- Report ----------------------------------------------------------------------
#
# Dimensions are read straight out of each PNG's IHDR chunk (bytes 16..23), so the
# listing verifies the files on disk rather than trusting the renderer.

png_dimensions() {
  od -An -tu4 -j16 -N8 --endian=big "$1" 2>/dev/null | tr -s ' ' | sed 's/^ //'
}

echo
echo "PNGs in $OUT_DIR:"
count=0
status=0
shopt -s nullglob
for png in "$OUT_DIR"/*.png; do
  read -r w h <<<"$(png_dimensions "$png")"
  [[ -n "${w:-}" && -n "${h:-}" ]] || fail "cannot read PNG header of $png"
  printf '  %-30s %sx%s\n' "$(basename "$png")" "$w" "$h"
  if [[ "$w" != "$WIDTH" || "$h" != "$HEIGHT" ]]; then
    echo "render.sh: $png is ${w}x${h}, expected ${WIDTH}x${HEIGHT}" >&2
    status=1
  fi
  count=$((count + 1))
done
shopt -u nullglob

echo
if (( count == 0 )); then
  fail "no PNGs were written"
fi
echo "render.sh: $count PNGs at ${WIDTH}x${HEIGHT}"
exit "$status"
