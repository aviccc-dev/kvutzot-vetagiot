#!/bin/bash
# בניית הגרסה למחשב: build.sh [יעד.jar]
# מהדר את המוח (source/src/.../model — אותו קוד כמו בטלפון) ואת המסך (desktop/src) ל-JAR אחד.
set -e
export LANG=C.UTF-8 LC_ALL=C.UTF-8
D=$(cd "$(dirname "$0")" && pwd)
R=$(cd "$D/.." && pwd)
OUT=${1:-$D/out/kvutzot.jar}
T=$(mktemp -d)
mkdir -p "$T/classes/examples"
javac -encoding UTF-8 --release 11 -nowarn -d "$T/classes" \
  $(find "$R/source/src/com/aviccc/kvutzot/model" "$D/src" -name "*.java") 2>&1 | grep -v "JAVA_TOOL_OPTIONS" || true
test -f "$T/classes/com/aviccc/kvutzot/desktop/App.class" || { echo "הידור נכשל"; exit 1; }
cp "$D/version.txt" "$T/classes/version.txt"
cp "$D/res/icon.png" "$T/classes/icon.png"
cp "$R/source/examples/משולש.xml" "$T/classes/examples/triangle.xml"
cp "$R/source/examples/צעצועים.xml" "$T/classes/examples/toys.xml"
printf "Main-Class: com.aviccc.kvutzot.desktop.App\nImplementation-Version: %s\n" "$(cat "$D/version.txt")" > "$T/manifest.txt"
mkdir -p "$(dirname "$OUT")"
jar --create --file "$OUT" --manifest "$T/manifest.txt" -C "$T/classes" .
rm -rf "$T"
ls -la "$OUT"
