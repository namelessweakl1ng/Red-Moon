# Android validation — 2026-10-30

Verified in this cloud instance:

- `cargo test -p red-moon-core -p red-moon-android`: 14 tests passed (12 existing core tests and two mobile boundary tests). Zero-test library/doc-test targets are not included in that count.
- `cargo fmt --all --check` and `cargo clippy -p red-moon-android --all-targets -- -D warnings` passed.
- `scripts/test-host-jni.sh`: actual JVM-to-Rust JNI create/save/list/reveal/lock/unlock/backup/restore passed, including Unicode and secret-free list responses. This uses the host JVM and is not an Android device test.
- Android ARM64 and x86_64 Rust release builds passed.
- `:app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` passed. Lint XML contains zero issues. The instrumentation APK compiled but was not executed.
- Debug APK signature and 16 KB ZIP alignment verified. Every packaged native library has 16 KB ELF LOAD alignment.
- APK manifest inspection confirms package `com.redmoon.vault`, minimum API 26, target API 35 and no Internet permission.

Independent source review identified lifecycle-lock cancellation, stale selection callbacks and password-keyboard metadata issues. Code was changed to use an application-lifetime native queue, lock on new ViewModel initialization, navigation tokens and password keyboard metadata. The lifecycle and IME behaviors still need device regression testing; compilation is not proof that those behaviors are correct.

Not verified: installation or execution on an Android device/emulator, OEM background/suspend behavior, UI accessibility on a phone, actual clipboard expiry, document-provider restore/export interactions, or screenshot blocking. No device/emulator was available in this instance. Run `connectedDebugAndroidTest` and manually exercise those flows with synthetic credentials before using real passwords.

The application is not independently audited. RustSec continues to report the pre-existing desktop GLib unsoundness and proc-macro-error maintenance warnings in the full workspace; those desktop GTK dependencies are not part of the Android native dependency graph. A separate complete security review of Android/Maven dependencies has not been performed.
