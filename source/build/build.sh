#!/bin/bash
# בנייה וחתימה: build.sh <תיקיית המקור> <קובץ APK ביעד> <קובץ המפתח>
set -e
export LANG=C.UTF-8 LC_ALL=C.UTF-8
DIR=$(cd "$1" && pwd); OUT=$2; KEY=${3:-/home/claude/kv-signing/kv-key.jks}
T=/home/claude/tools
bash $DIR/build/test.sh $DIR || { echo "הבנייה נעצרה: בדיקה נכשלה"; exit 1; }
cd $DIR
rm -rf out gen bld assets; mkdir -p out gen bld assets/examples
cp examples/משולש.xml assets/examples/triangle.xml
cp examples/צעצועים.xml assets/examples/toys.xml
aapt package -f -m -M AndroidManifest.xml -S res -A assets -I $T/android.jar -J gen -F bld/res.apk
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn -bootclasspath $T/android.jar -classpath $T/android.jar -d out $(find src gen -name "*.java") 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
test -f out/com/aviccc/kvutzot/Main.class || { echo "הידור נכשל"; exit 1; }
dalvik-exchange --dex --min-sdk-version=21 --output=bld/classes.dex out
cd bld
cp res.apk unsigned.apk
aapt add -f unsigned.apk classes.dex > /dev/null
zipalign -f -p 4 unsigned.apk aligned.apk
apksigner sign --ks $KEY --ks-pass pass:android --key-pass pass:android --ks-key-alias kv --out final.apk aligned.apk
apksigner verify final.apk && echo "SIGN OK"
aapt dump badging final.apk | grep -E "^package:|application-label:|sdkVersion"
echo "classes.dex: $(stat -c %s classes.dex) בתים"
cp final.apk "$OUT"
cd $DIR; rm -rf out gen bld assets
ls -la "$OUT"
