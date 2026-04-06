#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="${1:-.}"
MODULE="${2:-app}"
VARIANT="${3:-debug}"
OUT_DIR="${4:-uml-out}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
javac --release 17 AndroidUmlExtractor.java
java AndroidUmlExtractor --project "$PROJECT_ROOT" --module "$MODULE" --variant "$VARIANT" --out "$OUT_DIR"
