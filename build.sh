#!/usr/bin/env bash
# Maven-free build of iFogSim-HetFS (JDK 21 only).
#
# Expects the jars produced by scripts/install-ifogsim.sh in lib/; the tests also need
# junit-4.13.2.jar and hamcrest-core-1.3.jar in lib/. The layout and the conventions
# are those of the Maven build: unit tests are *Test classes, integration tests are
# *IT classes, and every test class runs in its own JVM.
#
#   ./build.sh compile          compile both modules
#   ./build.sh test             compile and run the unit tests
#   ./build.sh verify           compile and run the unit and integration tests
#   ./build.sh jar              package hetfs-core/target/ifogsim-hetfs.jar
#   ./build.sh run <class> [args...]
#                               run a main class of either module
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE="$ROOT/hetfs-core"
EXAMPLES="$ROOT/hetfs-examples"
JAVA_OPTS=(--add-opens java.base/java.lang=ALL-UNNAMED)
JAVAC_OPTS=(-nowarn --release 21 -encoding UTF-8)

LIB_CP="$(find "$ROOT/lib" -name '*.jar' 2>/dev/null | sort | tr '\n' ':')"
if [[ -z "$LIB_CP" ]]; then
  echo "lib/ is empty: run scripts/install-ifogsim.sh first" >&2
  exit 1
fi
MAIN_CP="$CORE/target/classes:$EXAMPLES/target/classes:$LIB_CP"

# compile_sources <module dir> <source root> <output dir> <classpath>
compile_sources() {
  local sources="$1/target/$(basename "$3").sources"
  mkdir -p "$3"
  find "$2" -name '*.java' > "$sources"
  if [[ -s "$sources" ]]; then
    javac "${JAVAC_OPTS[@]}" -cp "$4" -d "$3" @"$sources"
  fi
}

compile_main() {
  compile_sources "$CORE" "$CORE/src/main/java" "$CORE/target/classes" "$LIB_CP"
  compile_sources "$EXAMPLES" "$EXAMPLES/src/main/java" "$EXAMPLES/target/classes" "$CORE/target/classes:$LIB_CP"
}

compile_tests() {
  compile_sources "$CORE" "$CORE/src/test/java" "$CORE/target/test-classes" "$MAIN_CP"
  compile_sources "$EXAMPLES" "$EXAMPLES/src/test/java" "$EXAMPLES/target/test-classes" "$MAIN_CP"
}

# run_tests <suffix: Test|IT>
run_tests() {
  local suffix="$1" failed=0 total=0 module cls log
  for module in "$CORE" "$EXAMPLES"; do
    [[ -d "$module/target/test-classes" ]] || continue
    while read -r cls; do
      total=$((total + 1))
      log="$module/target/$cls.log"
      if java "${JAVA_OPTS[@]}" -cp "$module/target/test-classes:$EXAMPLES/target/test-classes:$MAIN_CP" \
           org.junit.runner.JUnitCore "$cls" > "$log" 2>&1; then
        echo "  PASS  $cls ($(grep -o 'OK ([0-9]* tests*)' "$log"))"
      else
        echo "  FAIL  $cls (see ${log#"$ROOT"/})"
        failed=$((failed + 1))
      fi
    done < <(cd "$module/target/test-classes" && find . -name "*$suffix.class" \
               | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort)
  done
  echo "$((total - failed))/$total test classes passed"
  [[ $failed -eq 0 ]]
}

case "${1:-compile}" in
  compile) compile_main ;;
  test)    compile_main; compile_tests; run_tests Test ;;
  verify)  compile_main; compile_tests; run_tests Test; run_tests IT ;;
  jar)     compile_main; jar cf "$CORE/target/ifogsim-hetfs.jar" -C "$CORE/target/classes" . ;;
  run)     shift; java "${JAVA_OPTS[@]}" -cp "$MAIN_CP" "$@" ;;
  clean)   rm -rf "$CORE/target" "$EXAMPLES/target" ;;
  *)       echo "unknown command: $1" >&2; exit 2 ;;
esac
