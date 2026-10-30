#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
AOSP_LOCAL_OUT=${1:-aosp/evidence/local}
mkdir -p "$AOSP_LOCAL_OUT"
# These are native host checks, not Binder/SELinux/custom-image proof.
{
  date -u '+%Y-%m-%dT%H:%M:%SZ'
  uname -sm
  clang++ --version
  pkg-config --modversion openssl
} > "$AOSP_LOCAL_OUT/environment.txt"
clang++ -std=c++17 -Wall -Wextra -Werror -pthread -fsanitize=address,undefined \
  $(pkg-config --cflags openssl) -I aosp/engine \
  aosp/engine/Engine.cpp aosp/tests/engine_test.cpp \
  $(pkg-config --libs openssl) -o "$AOSP_LOCAL_OUT/engine_test" \
  > "$AOSP_LOCAL_OUT/build.txt" 2>&1
"$AOSP_LOCAL_OUT/engine_test" > "$AOSP_LOCAL_OUT/checks.jsonl" 2> "$AOSP_LOCAL_OUT/sanitizer.txt"
cat "$AOSP_LOCAL_OUT/checks.jsonl"
