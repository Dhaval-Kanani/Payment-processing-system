#!/usr/bin/env bash
# Compile main sources into out/production/test
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out/production/test
javac -Xlint:all,-serial -d out/production/test $(find src -name '*.java')
echo "BUILD OK"
