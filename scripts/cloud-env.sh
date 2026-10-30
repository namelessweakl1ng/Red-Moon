#!/usr/bin/env bash
# Source this in the managed cloud environment; normal machines do not need it.
export CARGO_HOME=/workspace/.cargo
export RUSTUP_HOME=/workspace/.rustup
export PATH="$CARGO_HOME/bin:/workspace/red-moon-sysroot/usr/bin:$PATH"
export PKG_CONFIG_SYSROOT_DIR=/workspace/red-moon-sysroot
export PKG_CONFIG_PATH=/workspace/red-moon-sysroot/usr/lib/x86_64-linux-gnu/pkgconfig:/workspace/red-moon-sysroot/usr/share/pkgconfig
export LD_LIBRARY_PATH=/workspace/red-moon-sysroot/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
