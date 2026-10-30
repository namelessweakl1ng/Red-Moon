# Red Moon for Android

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
