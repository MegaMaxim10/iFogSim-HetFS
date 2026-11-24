#!/usr/bin/env bash
# Maven-free build for iFogSim-HetFS (JDK 21 only). Expects the jars produced by
# scripts/install-ifogsim.sh in lib/. Tests additionally need junit-4.13.2.jar and
# hamcrest-core-1.3.jar in lib/.
#
#   ./build.sh compile      compile the library and examples into target/classes
#   ./build.sh test         compile and run every unit test (one JVM per test class)
#   ./build.sh jar          compile and package target/ifogsim-hetfs.jar
#   ./build.sh run <class> [args...]
#                           run a main class on the library classpath
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="$HERE/lib"
OUT="$HERE/target"
CP_LIB="$(find "$LIB" -name '*.jar' 2>/dev/null | sort | tr '\n' ':')"
if [[ -z "$CP_LIB" ]]; then
  echo "lib/ is empty: run scripts/install-ifogsim.sh first" >&2
  exit 1
fi

compile_main() {
  mkdir -p "$OUT/classes"
  find "$HERE/src/main/java" -name '*.java' > "$OUT/main-sources.txt"
  javac -nowarn --release 21 -encoding UTF-8 -cp "$CP_LIB" -d "$OUT/classes" @"$OUT/main-sources.txt"
}

compile_tests() {
  mkdir -p "$OUT/test-classes"
  find "$HERE/src/test/java" -name '*.java' > "$OUT/test-sources.txt"
  javac -nowarn --release 21 -encoding UTF-8 -cp "$OUT/classes:$CP_LIB" \
        -d "$OUT/test-classes" @"$OUT/test-sources.txt"
}

run_tests() {
  local failed=0 total=0
  while read -r cls; do
    total=$((total + 1))
    if java -cp "$OUT/test-classes:$OUT/classes:$CP_LIB" org.junit.runner.JUnitCore "$cls" \
         > "$OUT/test-$cls.log" 2>&1; then
      echo "  PASS  $cls ($(grep -o 'OK ([0-9]* test[s]*)' "$OUT/test-$cls.log"))"
    else
      echo "  FAIL  $cls (see target/test-$cls.log)"
      failed=$((failed + 1))
    fi
  done < <(cd "$OUT/test-classes" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort)
  echo "$((total - failed))/$total test classes passed"
  [[ $failed -eq 0 ]]
}

case "${1:-compile}" in
  compile) compile_main ;;
  test)    compile_main; compile_tests; run_tests ;;
  jar)     compile_main; jar cf "$OUT/ifogsim-hetfs.jar" -C "$OUT/classes" . ;;
  run)     shift; java -cp "$OUT/classes:$CP_LIB" "$@" ;;
  *)       echo "unknown command: $1" >&2; exit 2 ;;
esac
