#!/usr/bin/env bash
#
# Renders the *previous* CozyGrams UI (v1.2.0, commit a970d65) to PNG on a desktop JVM,
# as a like-for-like baseline for the current build's screenshots.
#
#   tools/legacy-preview/render-legacy.sh [outputDir] [width] [height]
#
# The pixels come from the real shipped CozyGameView, not from a re-implementation. Its
# source, and the model classes it draws from, are vendored verbatim under legacy-src/ and
# checked against `git show <BASELINE_COMMIT>:app/...` on every run, so the baseline cannot
# quietly drift as the new code is rewritten around it. Everything the old class needs from
# the platform — View, Context, SharedPreferences, Resources, SystemClock, R, the audio
# classes — is stubbed under stubs/; the drawing stubs are copies of tools/preview/stubs so
# both harnesses put identical Java2D behind identical android.graphics calls.
#
# Nothing here reads tools/preview/ at all, and app/ only for the backdrops — which are
# taken from the baseline commit, not from the working tree. The current tree ships those
# pictures as WebP; that WebP is a *lossy* re-encode of v1.2.0's PNGs (mean channel error
# ~1.2-1.5/255, peak 43-52), so the PNGs are used instead and the baseline shows the exact
# pixels v1.2.0 shipped.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

# The commit whose UI this baseline reproduces. The vendored sources are verified against
# it, so if it ever stops resolving the run fails loudly instead of rendering fiction.
BASELINE_COMMIT="a970d65"

BUILD_DIR="${COZY_LEGACY_BUILD_DIR:-$SCRIPT_DIR/build}"
CLASSES_DIR="$BUILD_DIR/classes"
ASSET_DIR="$BUILD_DIR/assets"
STUB_DIR="$SCRIPT_DIR/stubs"
LEGACY_SRC="$SCRIPT_DIR/legacy-src/com/cozygrams/tv"
RES_DIR="$REPO_ROOT/app/src/main/res/drawable-nodpi"

OUT_DIR="${1:-$SCRIPT_DIR/out}"
WIDTH="${2:-1920}"
HEIGHT="${3:-1080}"
case "$OUT_DIR" in
  /*) ;;
  *) OUT_DIR="$PWD/$OUT_DIR" ;;
esac

# The v1.2.0 sources this harness compiles. CozyGameView is the whole UI; the rest is the
# model it draws and the two audio helpers it owns (which never make a sound here, but do
# have to compile). MainActivity is deliberately absent: it draws nothing.
LEGACY_SOURCES=(
  CozyGameView.java
  GameState.java Puzzle.java PuzzleGenerator.java PuzzleLibrary.java
  CozyMusic.java CozySfx.java
)

fail() {
  echo "render-legacy.sh: $*" >&2
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

# ---- Provenance ------------------------------------------------------------------
#
# A baseline is only worth anything if it is the real old code. Every vendored file is
# hashed against the same file at the baseline commit. A mismatch is fatal: better no
# screenshots than screenshots of something that was never shipped.

if git -C "$REPO_ROOT" rev-parse --verify --quiet "$BASELINE_COMMIT^{commit}" >/dev/null; then
  for name in "${LEGACY_SOURCES[@]}"; do
    [[ -f "$LEGACY_SRC/$name" ]] || fail "missing vendored source $LEGACY_SRC/$name"
    original="$(git -C "$REPO_ROOT" show \
      "$BASELINE_COMMIT:app/src/main/java/com/cozygrams/tv/$name" | sha256sum | cut -d' ' -f1)"
    vendored="$(sha256sum "$LEGACY_SRC/$name" | cut -d' ' -f1)"
    [[ "$original" == "$vendored" ]] || fail \
      "$name does not match $BASELINE_COMMIT — the baseline would misrepresent v1.2.0"
  done
  echo "render-legacy.sh: ${#LEGACY_SOURCES[@]} sources verified against $BASELINE_COMMIT"
else
  echo "render-legacy.sh: warning - $BASELINE_COMMIT is not in this clone;" \
       "rendering the vendored sources unverified" >&2
fi

# ---- Backdrops -------------------------------------------------------------------
#
# v1.2.0 shipped these as PNG; the current tree ships the same two pictures as WebP. The
# conversion was lossy, so the WebP is only a near-copy — checked, not assumed: mean
# channel error ~1.2-1.5 of 255 with peaks past 40. Small, but there is no reason to eat
# even that when the originals are one `git show` away, so the exact v1.2.0 PNGs are
# extracted from the baseline commit into build/ and only the WebP path (which java.imageio
# cannot decode unaided) needs Pillow. Nothing is written back into app/src/main/res.

backdrop() {
  local name="$1"
  local dst="$ASSET_DIR/$name.png"
  local blob="$BASELINE_COMMIT:app/src/main/res/drawable-nodpi/$name.png"
  if git -C "$REPO_ROOT" cat-file -e "$blob" 2>/dev/null; then
    git -C "$REPO_ROOT" show "$blob" > "$dst"
    [[ -s "$dst" ]] || fail "extracting $blob produced nothing"
    return 0
  fi
  echo "render-legacy.sh: warning - $name.png is not in $BASELINE_COMMIT; falling back to" \
       "the current WebP, which is a lossy re-encode of it" >&2
  convert_backdrop "$RES_DIR/$name.webp" "$dst"
}

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

rm -rf "$ASSET_DIR"
mkdir -p "$ASSET_DIR"
backdrop cozy_room
backdrop moon_garden

# ---- Compile ---------------------------------------------------------------------

sources=()
while IFS= read -r -d '' stub; do
  sources+=("$stub")
done < <(find "$STUB_DIR" -name '*.java' -print0 | sort -z)

for name in "${LEGACY_SOURCES[@]}"; do
  sources+=("$LEGACY_SRC/$name")
done
sources+=("$SCRIPT_DIR/LegacyPreview.java")

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

# -sourcepath is pinned to this directory only. The current app/src/main/java also holds a
# com.cozygrams.tv package, and letting javac reach it would silently mix new classes into
# the old build — exactly the failure this whole harness exists to avoid.
echo "render-legacy.sh: compiling ${#sources[@]} sources -> $CLASSES_DIR"
"$JAVAC" -nowarn -Xlint:-options -encoding UTF-8 -source 17 -target 17 \
  -sourcepath "$STUB_DIR:$SCRIPT_DIR/legacy-src:$SCRIPT_DIR" \
  -d "$CLASSES_DIR" "${sources[@]}"

# ---- Run -------------------------------------------------------------------------

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*.png

echo "render-legacy.sh: rendering"
"$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
  -Dcozy.assets="$ASSET_DIR" -cp "$CLASSES_DIR" LegacyPreview "$OUT_DIR" "$WIDTH" "$HEIGHT"

# ---- Report ----------------------------------------------------------------------
#
# Dimensions are read straight out of each PNG's IHDR chunk (bytes 16..23), so the listing
# verifies the files on disk rather than trusting the renderer.

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
    echo "render-legacy.sh: $png is ${w}x${h}, expected ${WIDTH}x${HEIGHT}" >&2
    status=1
  fi
  count=$((count + 1))
done
shopt -u nullglob

echo
if (( count == 0 )); then
  fail "no PNGs were written"
fi
echo "render-legacy.sh: $count v1.2.0 baseline PNGs at ${WIDTH}x${HEIGHT}"
exit "$status"
