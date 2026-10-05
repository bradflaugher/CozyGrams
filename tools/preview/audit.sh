#!/usr/bin/env bash
#
# The text-fit audit: every screen, at every size the game ships to, at every text size
# and in the words of every kind of controller, drawn without pixels. Fails when any line
# of words leaves its pill, panel or button, collides with another, or leaves the safe area.
#
#   tools/preview/audit.sh [reportFile]
#
# `./gradlew test` runs this too (the app's textFitAudit task), so CI does.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="${COZY_BUILD_DIR:-$SCRIPT_DIR/build}/audit"
CLASSES_DIR="$BUILD_DIR/classes"
STUB_DIR="$SCRIPT_DIR/stubs"
REPORT="${1:-$BUILD_DIR/text-fit.txt}"
# tools/preview/audit.sh --show out.png <check> <device> <text-1.0|text-1.3|text-1.5> <mode>
# paints one cell of the matrix (render.sh must have decoded the backdrops once).
# COZY_AUDIT_OPTS=-Dcozy.audit.small=true also lists words drawn below the prose floor
# (informational: eyebrows and the touch pad's labels are smaller by design).
SHOW=()
if [[ "${1:-}" == "--show" ]]; then
  shift
  SHOW=(--audit-show "$@")
fi

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAVA="$JAVA_HOME/bin/java"
else
  command -v javac >/dev/null || { echo "audit.sh: no javac; set JAVA_HOME to a JDK 17+" >&2; exit 1; }
  JAVAC="$(command -v javac)"
  JAVA="$(command -v java)"
fi

sources=()
while IFS= read -r -d '' stub; do
  sources+=("$stub")
done < <(find "$STUB_DIR" -name '*.java' -print0 | sort -z)
sources+=("$SCRIPT_DIR/Preview.java" "$SCRIPT_DIR/TextAudit.java")

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"
# The app's rendering sources come in through -sourcepath, exactly as far as Preview
# reaches into them, the same way render.sh lets javac follow the dependency graph.
"$JAVAC" -nowarn -Xlint:-options -encoding UTF-8 -source 17 -target 17 \
  -implicit:class -sourcepath "$REPO_ROOT/app/src/main/java" \
  -d "$CLASSES_DIR" "${sources[@]}"

if (( ${#SHOW[@]} > 0 )); then
  exec "$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
    -Dcozy.assets="${COZY_BUILD_DIR:-$SCRIPT_DIR/build}/assets" -cp "$CLASSES_DIR" \
    Preview "${SHOW[@]}"
fi
"$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 ${COZY_AUDIT_OPTS:-} -cp "$CLASSES_DIR" \
  Preview --audit "$REPORT"
