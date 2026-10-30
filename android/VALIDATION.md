# Red Moon 2.0 validation — 2026-10-31

## Verified in this cloud instance

- Rust core/mobile suite: 28 passing tests after the final record-lifecycle regression was added; two independent fixture-generation tests deliberately ignored in normal runs. Zero-test library/doc-test targets are not counted.
- Coverage includes all six record kinds through create/edit/reopen/backup/restore/delete; typed fields, checklists, categories/reorder/delete/legacy-name preservation, authentic v1 fixtures, current-file biometric key authentication, stale-key failure after password changes, wrong/tampered backups, fresh-install recovery, exclusive writing and failed saves.
- `cargo fmt --all --check` and `cargo clippy -p red-moon-core -p red-moon-android --all-targets -- -D warnings` passed.
- `scripts/test-host-jni.sh`: real JVM→Rust JNI login and all-new-record operations, secret-free summaries, private body search, reveal, lock/unlock and full encrypted backup/restore passed. This is a host JVM test, not a phone test.
- Eight Kotlin JVM tests passed: bounded transfer, write failure, unavailable/mismatched read-back, optional metadata lookup failure and preservation of last successful/verified evidence after later failures/unverified writes.
- Android `testDebugUnitTest`, `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest` passed; final lint XML has zero issues. Instrumentation APK compiled but was not executed.
- ARM64 and x86_64 Rust release libraries compiled. APK signature, 16KB ZIP alignment and 16KB ELF LOAD alignment for all packaged native libraries verified.
- Manifest inspection: package `com.redmoon.vault`, versionCode2/versionName2.0.0, API26 minimum/API35 target; biometric/fingerprint permissions and private receiver permission only, no Internet permission.
- Updated desktop Tauri consumer `cargo check -p red-moon` passed. This does not verify desktop runtime or new-record editing in the old desktop UI.
- Source security review identified custom legacy-category visibility, backup-history overwrite and optional provider-metadata error handling. Regression failures were observed and corrected; affected suites/builds rerun.

## Not verified

`adb devices -l` returned no connected device. The Samsung M31, biometric hardware/enrollment/cancellation/invalidation, OEM lifecycle behavior, document providers including Google Drive, cloud synchronization, clipboard timing/history, IME learning and screenshots have not been tested on a phone. Instrumentation envelope tests compile but remain unrun.

Follow `docs/ANDROID_RECOVERY_TEST.md` with synthetic data on your phone and an independent fresh installation before using real credentials. No independent external security audit has been performed.

RustSec reports zero findings in its vulnerability category, while still reporting the existing `glib` unsoundness advisory RUSTSEC-2024-0429 and unmaintained `proc-macro-error` advisory RUSTSEC-2024-0370 in the desktop dependency graph. These are unresolved; GTK/GLib are not shipped in the Android dependency graph. A complete Android/Maven dependency vulnerability audit remains outstanding.

Automatic cloud backup is deliberately not implemented/enabled until real-provider manual backup and new-device recovery are proven. See `docs/AUTOMATIC_BACKUP_DESIGN.md` for the gated follow-up. Do not interpret successful provider write/read-back or compilation as proof of remote synchronization or reliable fingerprint authentication.
