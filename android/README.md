# Red Moon 2.0 for Android

Open **this `android` directory** in Android Studio. This is a native Kotlin/Jetpack Compose app using the same Rust encrypted vault core as desktop; it does not use the desktop webview.

## Prepare your computer

1. Install Android Studio with SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018 and a JDK (Android Studio's bundled JDK is suitable).
2. Install Rust through rustup and run:

   ```sh
   rustup target add aarch64-linux-android x86_64-linux-android
   cargo install cargo-ndk --locked
   ```

3. Make `cargo` available in Android Studio's environment. On Windows it is normally in `%USERPROFILE%\.cargo\bin`; restart Android Studio after installing Rust.
4. Open `android`, allow Gradle sync, and select the `app` run configuration. Gradle builds and packages both Rust libraries automatically. The initial development build requires Internet access for dependencies; the installed app does not.

## Install on your phone

Enable Developer options and USB debugging on your Android phone, connect it by USB, approve the computer's debugging prompt, select your device in Android Studio and press **Run**. Android 8 or newer and an ARM64 phone are supported. An x86_64 emulator is also supported; 32-bit-only phones are not.

Command-line equivalents, from this directory:

```sh
./gradlew assembleDebug
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew installDebug
```

On Windows use `gradlew.bat` instead. The debug APK is generated under `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for development; create and securely retain your own release signing key before long-term distribution. Losing the signing key can prevent in-place updates. Never commit keystores or their passwords.

## Red Moon 2.0

The native app now has Vault/Notes/Categories/Settings bottom navigation, rounded light/dark/system themes, encrypted login/note/identity/document/financial/custom records, typed custom fields and checklists, custom category management, explicit masked-field reveal, private body search and optional cryptographic biometric unlock. All records use the shared Rust vault.

**Upgrade:** keep an independently verified encrypted backup and the original signing key. The first v1 save preserves `vault.v1-safety.rm` and writes authenticated format2. Old binaries cannot read format2. Do not uninstall to fix a signing-key mismatch because that removes private vault/recovery copies. New-device recovery requires only the backup's master password; configure biometrics again there.

Use Settings → **Back up to Google Drive** and select Drive in the system picker. A local provider works offline; Drive may require connectivity. Backup status distinguishes completed provider writes, verified read-back and unproven cloud synchronization, retaining earlier successful/verified evidence after failed attempts. Automatic backups are not enabled.

Detailed instructions:

- [Samsung M31 and new-phone recovery acceptance procedure](../docs/ANDROID_RECOVERY_TEST.md)
- [Cryptographic schema, biometrics and limits](../docs/RED_MOON_2_SECURITY.md)
- [Actual verification results and remaining limitations](VALIDATION.md)
- [Gated automatic backup design](../docs/AUTOMATIC_BACKUP_DESIGN.md)

## Android protections and behavior

- No Internet permission; encrypted vault in the app's private `filesDir`.
- Screenshots/recents previews blocked with `FLAG_SECURE`; OS cloud and device-transfer backups excluded.
- Master password required after backgrounding, including opening a document picker. Return from a picker and authenticate again as needed.
- Default five-minute inactivity timeout is enforced by Rust using Android monotonic elapsed time. Activity recreation cannot intentionally retain an unlocked session.
- Password copies are marked sensitive; cleanup compares current clipboard contents before clearing after 20 seconds or on lock. Android may restrict background clipboard access. Identical newer clipboard contents cannot always be distinguished.
- Secret text is masked and identified to the IME as password input. Android/Kotlin strings, IMEs and the clipboard cannot guarantee memory erasure; use a trusted keyboard and OS.
- Encrypted backup/restore uses Android's document picker. Only ciphertext is staged/exported. Restore validates authentication before replacing the vault and keeps an encrypted safety copy in private storage when applicable. Deleting/uninstalling the app deletes private copies; keep a separate encrypted backup.
- Backups remain compatible with the existing Red Moon vault format. Earlier backups retain their old password after a master-password change. Forgotten passwords cannot be reset.

## Validation limits

Check the repository's current completion report for actual build/test results. Host Rust tests and cross-compilation do not establish that lifecycle, clipboard, IME, document-provider or screenshot behavior works on a real phone. Instrumentation tests are provided and require an attached device/emulator. No independent security audit has been performed. Use synthetic credentials during initial device testing.

Current verified results and remaining device checks: [VALIDATION.md](VALIDATION.md).

## Current UI: 2.1.0

The latest Android build uses the requested **zero-radius** design: flat account rows, square controls/navigation, simpler forms and less visible field-configuration clutter. It replaces the earlier rounded Material3 presentation. Read the [design references and rationale](../docs/superpowers/specs/2026-10-31-flat-ui-design.md). Application ID and encrypted data format are unchanged; versionCode3 supports an in-place update signed with your existing key. Preserve your backup and signing key as described above.
