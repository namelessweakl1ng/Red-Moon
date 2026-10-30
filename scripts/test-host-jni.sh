#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
cargo build -p red-moon-android --release
red_moon_test_dir=$(mktemp -d)
trap 'rm -rf -- "$red_moon_test_dir"' EXIT
javac -d "$red_moon_test_dir" scripts/tests/NativeVault.java
red_moon_native_name=libred_moon_android.so
if [[ $(uname) == Darwin ]]; then red_moon_native_name=libred_moon_android.dylib; fi
java "-Dredmoon.native=$PWD/target/release/$red_moon_native_name" -cp "$red_moon_test_dir" com.redmoon.vault.NativeVault "$red_moon_test_dir"
