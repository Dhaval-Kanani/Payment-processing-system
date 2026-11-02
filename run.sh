#!/usr/bin/env bash
# Build and run the demo
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -cp out/production/test payment.Main
