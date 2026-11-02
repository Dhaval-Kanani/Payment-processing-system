#!/usr/bin/env bash
# Build main + tests, then run the hand-rolled TestRunner
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
mkdir -p out/test
javac -Xlint:all,-serial -d out/test -cp out/production/test $(find test -name '*.java')
java -cp out/production/test:out/test payment.TestRunner
