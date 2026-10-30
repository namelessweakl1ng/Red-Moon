# Red Moon 2.0 design

Implements the uploaded requirements alongside the user's explicit instruction to begin without approval prompts. Primary device Samsung Galaxy M31, Android 12 ARM64; minimum API26 retained. This design is an implementation decision record, not evidence of passing device tests.

## User experience

Replace the old dropdown application with four bottom destinations: Vault, Notes, Categories and Settings. Use one-hand actions, a floating add button, searchable summaries, category shortcuts, recency ordering, favorites and real empty states. Material3 rounded cards now supersede the earlier square-corner requirement. Dark is default with light/system preferences. Separate auth, vault, editor, detail, category, settings and recovery modules. Sensitive fields stay masked; explicit reveal/edit retrieves only the selected record. Do not retain secrets in saved-instance state.

## Encrypted schema and migration

Keep legacy login Entry decoding and desktop commands. Add encrypted Record collections for enhanced login, secure_note, identity, indian_document, financial and custom records, typed user fields and checklists. Add encrypted category IDs/name/icon/color/order. New authenticated file version2 keeps the same RustCrypto primitives and bounded header while preventing old binaries from silently rewriting extended payloads. Updated shared desktop core reads and preserves these collections even when old desktop UI cannot edit them.

On existing v1 writes, preserve the exact encrypted original as a safety file before committing an authenticated v2 file. Validate and reopen after migration; never normalize away unknown schema versions. A fresh install can restore a synthetic legacy backup using its password, independently of any Android Keystore. Record/category deletion must not silently delete associated records. Document bounds and failure behavior.

## Biometrics

Use AndroidX BiometricPrompt with BIOMETRIC_STRONG and an authentication-per-use AES256/GCM Android Keystore key, invalidated on biometric changes. Reauthenticate the master password to export a zeroizing derived vault key from the unlocked Rust core; wrap it with an authenticated Cipher CryptoObject. Persist only IV/authenticated wrapped material in noBackupFilesDir. Biometric decrypt returns the key to Rust, which must authenticate the actual vault ciphertext before unlocking. No UI-check bypass or saved master password. Reenrollment, key invalidation, file/key mismatch and cancellation leave the vault locked with master-password fallback. Change-password/restore/data-deletion invalidate biometric enrollment. Hardware backing is requested through supported platform behavior, not assumed or falsely claimed.

Activity/ViewModel epoch guards reject stale results. Lock requests run on the application-lifetime serialized native queue. Cancel prompts and lock on actual background onStop and screen-off. An OEM biometric overlay that pauses its host may preserve the session only while the controlled prompt is active; leaving the Activity fully stops and locks it. Keystore and biometric state are deliberately excluded from backups.

## Manual backup and recovery

Use SAF CreateDocument/OpenDocument. The label Back up to Google Drive opens the system provider picker; Drive is chosen by the user if installed and signed in. No Drive OAuth or Internet permission is required in Red Moon. Only Rust-produced ciphertext crosses SAF. Buffer size is bounded at 16MiB; no plaintext staging.

Report provider destination and timestamp only after successful close. Where supported, reopen and compare SHA256 of ciphertext to verify provider read-back. Distinguish write success, read-back verification and actual remote cloud synchronization (which the app cannot prove). Persist only nonsecret operation metadata and validate schema/counts/authentication on restore. Preserve existing encrypted vault and a safety copy on failed restore. Do not overwrite an earlier known-good backup; create versioned filenames.

Automatic Drive backups are conditional on real-provider manual recovery being proven. The cloud host has no attached phone/Drive provider, so do not ship a pretend toggle or claim automatic synchronization. Document a separate gated WorkManager/least-privilege Drive design. This sequencing follows the uploaded requirement rather than silently treating an untested provider save as proven recovery.

## Security boundaries and limits

Keep Argon2id, authenticated XChaCha20-Poly1305, fresh nonces/salts, bounded decoding, exclusive writers, encrypted atomic replacement and zeroization. No plaintext SQLite/preferences/cache, analytics, crash reporting or credentials in logs. All private identifiers are synthetic in tests. Android Kotlin/JNI strings and keyboards cannot promise memory erasure; FLAG_SECURE/backup exclusions remain. Locked-file theft is in scope; OS compromise, malware and rollback to an older valid vault remain out of scope. No independent audit claim.

## Acceptance and evidence

Test each new record lifecycle, category operations, typed field persistence, migration using genuine encrypted v1 fixtures, fresh-install restore, wrong/tampered backups, and stale biometric key rejection in Rust. Verify real JNI boundary, Kotlin gate/envelope tests, Android lint/build and compile instrumentation tests. Run connected tests only if a device is available; never count compilation as device authentication or Drive validation. Produce an APK and a reproducible Samsung M31 manual acceptance/recovery procedure. Keep the existing 0.1.0 app ID and increment Android versionCode for in-place upgrade.
