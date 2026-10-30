# Red Moon 2.0 security and format

Red Moon 2.0 is an implementation, not an independently audited product. Keep real credentials out until the device acceptance procedure and independent review are complete.

## Encryption and compatibility

The complete JSON document is encrypted using the existing Argon2id/XChaCha20-Poly1305 design. The 80-byte technical header is authenticated as AEAD associated data. New saves and backups set header byte8 to version2; updated code accepts versions1 and2 and rejects unknown versions. The new authenticated payload schema is version2. Cryptographic algorithms, salt/nonce sizes, KDF bounds and the 16MiB maximum encrypted file remain unchanged.

New collections contain typed records, custom fields, checklists and categories. Legacy login entries are retained until explicitly edited into records. Free-form legacy category strings are materialized into encrypted category objects. The first conversion of a version1 active file preserves its exact encrypted bytes at `vault.v1-safety.rm`; existing recovery/safety files are never silently replaced with different bytes. The new temporary encrypted file is reopened and authenticated before atomic replacement. Wrong passwords, malformed/tampered files and failed verification leave the old file in place.

Older Red Moon binaries cannot read version2 files. This is intentional: an older schema must fail closed rather than rewrite a file while dropping unknown records. The updated shared desktop Rust core reads and preserves new collections, although the existing desktop UI still edits only legacy logins. Keep a v1 encrypted backup before upgrading if downgrade recovery is needed.

## Documented storage limits

Combined legacy/new records: 10,000. Categories: 1,000. Fields per record: 100. Check items per record: 1,000. Title: 512 UTF-8 bytes. Individual notes/field values: 1MiB. Tags: 50 of at most256 bytes. Whole encrypted file:16MiB. Master-password creation/change retains the minimum14-character and maximum1024-byte rules. Unknown versions/field types/extra schema members and invalid limits are rejected. Category deletion unassigns records without deleting them.

## Secret exposure

List/search operations return summaries only. Search over note/checklist text and non-secret field values runs inside Rust; the UI receives matching IDs/summaries. Password and identifier values marked secret are blanked in selected-record detail responses; reveal/copy and explicit editing fetch selected data only. Templates mark password, document number, financial account/IFSC and PIN fields secret. Users control custom field types, so changing a field to plain text deliberately changes its display policy.

Owned Rust key/plaintext/model buffers use zeroization where practical. Kotlin/JNI strings, temporary JSON representations, the keyboard, system clipboard, swap and OS memory cannot guarantee erasure. Screenshot/recents protection and OS backup exclusions remain enabled. No analytics, advertising, crash reporting or Internet permission is added. A trusted OS and keyboard are still necessary.

## Biometrics

AndroidX BiometricPrompt requires strong biometrics and an authenticated AES256-GCM Cipher CryptoObject. An auth-per-use Android Keystore key wraps the32-byte vault key with fixed versioned associated data; no master password is persisted. Enrollment first requires an unlocked vault and master-password reauthentication in Rust. Persistent biometric material is an atomically written private wrapped-key envelope in noBackupFilesDir. Android Keystore uses hardware protection when supported; hardware backing is not asserted without device evidence.

Biometric unlock decrypts that envelope and sends the transient key to Rust, which authenticates the actual current vault header/ciphertext. A successful prompt alone never unlocks the UI. Key invalidation, changed enrollment, missing/corrupt envelope, cancellation or key mismatch falls back to master-password authentication without changing the vault. Password changes, restore and active-vault deletion clear enrollment when the UI observes success; changed vault keys independently reject stale envelopes even after an interrupted UI callback.

Native lock requests run on the application-lifetime queue and survive ViewModel cancellation. Epoch/navigation guards reject stale secret responses. Native state is locked when a fresh ViewModel initializes. Actual onStop and screen-off cancel prompts and lock; onPause also locks unless the controlled biometric system prompt is active, accommodating OEM prompt overlays. Device tests must validate these lifecycle behaviors on the Samsung M31.

## Backups and recovery

SAF versioned CreateDocument saves only Rust-produced encrypted `.rm` bytes. The app can target a local provider or Google Drive selected by the user. Provider writes and read-back hash verification are reported separately from remote synchronization, which this app cannot prove. Last attempt, last completed provider write and last verified read-back are recorded independently as nonsecret operation metadata; failure/cancellation does not erase earlier evidence.

A backup is independently decryptable using its master password and never depends on the original phone's Keystore. Restore uses bounded encrypted staging, password authentication, schema validation, an encrypted pre-restore safety copy where applicable, and authenticated atomic replacement. The result reports restored record/category counts. Old backups retain their original password after a master-password change.

Active-vault deletion requires reauthentication and intentionally retains encrypted backup/migration/recovery files. It is not a secure erase of flash storage or all backup copies. Remove unwanted external backups separately.

## Threat model and residual risks

Protect a locked vault against file theft and unauthenticated modifications. Malware, keyloggers, rooted/compromised operating systems, keyboard learning by a malicious IME, clipboard-history applications, memory access and rollback to an older valid encrypted vault remain outside that protection. TLS/platform dependencies and provider implementations add external trust. No phone was attached to this cloud instance; compilation and host tests do not verify biometric hardware, provider recovery or OEM lifecycle behavior.
