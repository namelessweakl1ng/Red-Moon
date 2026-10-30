# Samsung M31 acceptance and new-phone recovery procedure

Use synthetic entries only. These steps are a test procedure, not a claim that device/provider tests have passed.

## Install and upgrade

Open `android/` in Android Studio, install SDK35/NDK27.2.12479018, make cargo/cargo-ndk available, and follow android/README.md. Keep the same signing key used for the installed app. Version2 has versionCode2 and uses the existing application ID. Build/install with Android Studio Run or `gradlew installDebug`.

Before upgrading, create two encrypted v1 backups, retain their master password offline, and test one copy on a separate test installation. Do not uninstall to resolve a signature mismatch: uninstall deletes private vault and safety files. Instead build with the original signing key or first export and independently verify a recoverable backup. After upgrade, the first save migrates to v2 while retaining the exact encrypted v1 safety file; old binaries cannot read v2. Never delete the only known-good v1 copy during acceptance.

## Basic workflows

1. Create a fresh vault with a synthetic master passphrase. Add one login, note with multiline text/checklist, identity, Indian document, financial record and custom record. Use obvious synthetic labels/values, never real Aadhaar/PAN/accounts.
2. Add all field types, reorder/rename/remove fields, favorite items, search note-body text, filter categories, edit and delete a record. Restart the process and confirm remaining values/dates persist.
3. Add a custom category/icon/color, rename/reorder it, move items between categories, then delete it. Confirm records remain unassigned and searchable. Test migration of an arbitrary legacy category name as well as default categories.
4. Reveal individual secret fields, then hide them. Copy a password; after20 seconds confirm it clears where supported. Copy unrelated newer text during the window and confirm Red Moon does not delete it. Test Samsung clipboard history separately and understand that sensitive flags cannot control every third-party clipboard manager.
5. Test Home, screen-off, recent-app switching, rotation and process recreation during an expensive unlock. No secret response should reappear after lock. Test the configured inactivity timeout while not interacting. Verify blocked screenshots/recents previews and note-editor keyboard behavior.

## Real Google Drive backup

1. Install/sign in to Google's Drive document provider on the test phone. Unlock Red Moon → Settings → Backup & Recovery → **Back up to Google Drive**.
2. Choose Drive and a destination. If Drive is absent from the picker, enable/install its provider; Red Moon does not silently switch to a direct API.
3. Confirm the displayed provider destination/timestamp. A provider-close success is not proof of upload. If read-back verification succeeds it confirms only bytes returned by that provider, not a remote server's storage.
4. Wait for Drive's own synchronization confirmation while online. On an independent device/session download the file and retain another copy. If offline/unverified/error, keep earlier known-good backups.
5. Cancel and simulate an unavailable/full provider on a later attempt. Confirm last successful and last verified destinations remain visible and no earlier file was overwritten.

## New-phone recovery (critical)

1. Use another compatible phone/test installation with no biometric envelope or keys from the old phone. Do not delete the original vault.
2. At welcome choose **Restore existing vault**, pick the independently downloaded Drive `.rm` file or local copy, and enter the password associated with that backup.
3. Confirm the restored count. Check each record kind, custom field values/order/types, notes/checklists, favorites, tags and category names/order. Lock/reopen and verify again.
4. Repeat with an authentic v1 backup. Verify preserved data and a successful v2 save/reopen.
5. Make a copy, tamper one ciphertext byte, and attempt restore into a populated test vault. Also try a wrong password, truncated/oversized file and interrupted import. Confirm the existing vault remains usable. Never tamper the only backup.
6. Change the master password, make a new backup and demonstrate that the older backup still needs its older password.

## Biometrics and failure fallback

1. First unlock with the master password. In Settings enter it again and enable biometric unlock. Confirm a strong BiometricPrompt actually occurs.
2. Lock. Scan a permitted fingerprint. Confirm unlock requires CryptoObject authentication; cancel/wrong finger must not reveal data.
3. Background or screen-off while the prompt is open and while native work is pending. On return remain locked. Test rotation and process death during setup/unlock.
4. Add/remove a device fingerprint. Old wrapped material must become unusable where enrollment invalidation is supported; master-password unlock must still work. Disable/re-enable enrollment.
5. Test restored backups on the new installation: old-phone biometric material must not be needed, and biometrics must be configured again.
6. Test password change and restore from a differently keyed vault invalidate old biometric enrollment, with master-password fallback intact.

Run `gradlew connectedDebugAndroidTest` with the test device selected. Record model/OS/provider versions and outcomes without logging secret values. A passing APK build is not a completed acceptance test or independent audit.
