#!/bin/bash
# בדיקות מבנה הנתונים והקובץ, על Java רגיל (בלי טלפון).
set -e
export LANG=C.UTF-8 LC_ALL=C.UTF-8
DIR=${1:-$(cd "$(dirname "$0")/.." && pwd)}
T=$(mktemp -d)
javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options -d $T \
  $(find $DIR/src/com/aviccc/kvutzot/model $DIR/test -name "*.java") 2>&1 | grep -v "JAVA_TOOL_OPTIONS" || true
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp $T com.aviccc.kvutzot.model.ModelTest $DIR/examples 2>&1 | grep -v "JAVA_TOOL_OPTIONS"
rc=${PIPESTATUS[0]}
rm -rf $T
exit $rc
