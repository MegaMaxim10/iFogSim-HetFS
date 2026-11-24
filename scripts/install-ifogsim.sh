#!/usr/bin/env bash
# Builds iFogSim from the official repository at the commit used throughout the
# iFogSim-HetFS evaluation, then
#   1. copies the resulting jar and iFogSim's own dependency jars into lib/
#      (used by build.sh, which needs no Maven), and
#   2. installs the jar into the local Maven repository as
#      org.cloudbus:ifogsim:5f68d39 (used by pom.xml), when Maven is available.
#
# Usage: scripts/install-ifogsim.sh [path-to-existing-iFogSim-checkout]
set -euo pipefail

COMMIT="5f68d3947e450d8d2b4af42670be819206be68c9"
SHORT="5f68d39"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB="$HERE/lib"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ $# -ge 1 ]]; then
  SRC="$(cd "$1" && pwd)"
else
  SRC="$WORK/iFogSim"
  git clone --quiet https://github.com/Cloudslab/iFogSim.git "$SRC"
fi
git -C "$SRC" checkout --quiet "$COMMIT"

mkdir -p "$LIB" "$WORK/classes"
DEPS="$(find "$SRC/jars" -name '*.jar' | tr '\n' ':')"
find "$SRC/src" -name '*.java' > "$WORK/sources.txt"
javac -nowarn --release 21 -encoding UTF-8 -cp "$DEPS" -d "$WORK/classes" @"$WORK/sources.txt"
jar cf "$LIB/ifogsim-$SHORT.jar" -C "$WORK/classes" .
find "$SRC/jars" -name '*.jar' -exec cp {} "$LIB/" \;
echo "iFogSim $SHORT built into $LIB"

if command -v mvn >/dev/null 2>&1; then
  if mvn -q install:install-file -Dfile="$LIB/ifogsim-$SHORT.jar" \
       -DgroupId=org.cloudbus -DartifactId=ifogsim -Dversion="$SHORT" -Dpackaging=jar; then
    echo "Installed org.cloudbus:ifogsim:$SHORT in the local Maven repository"
  else
    echo "Maven installation failed (offline?); build.sh still works with lib/" >&2
  fi
fi
