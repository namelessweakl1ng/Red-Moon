# Optional automatic encrypted backups — gated follow-up

Automatic cloud backup is not implemented/enabled in this build. The requirements explicitly place it after proven manual backup and fresh-install recovery. No connected phone or Google Drive provider was available here, so that gate has not been met. The manual picker workflow remains functional offline for local providers and online as the selected Drive provider requires.

After the device acceptance procedure proves recovery:

- Prefer a persisted document-tree grant only for providers that actually expose suitable create/read/list semantics. Google Drive's SAF implementation must not be assumed to provide tree grants or unattended background writes.
- If direct Drive API access is necessary, configure a public Android OAuth client linked to package/signing certificate (no embedded client secret), use supported account authorization with appDataFolder/drive.file as appropriate, and request the least privileges. App-owned backup browsing and new-phone account recovery must be designed and tested together. A valid OAuth client configuration and actual account/provider tests are prerequisites, not invented credentials.
- Let the user explicitly select/disconnect/change the account. Store refresh authorization using supported platform SDKs, outside encrypted recovery files. Offline vault operation must never require account access.
- Schedule unique WorkManager work with online/battery constraints. Copy only an already committed encrypted snapshot; never persist a master password or open the vault silently. Do not put plaintext or key material in WorkManager input data/logs.
- Upload to a new versioned object, verify server/downloaded ciphertext digest and successful password-based fresh-install restore during acceptance before calling any version verified. Keep last verified backups; never overwrite/delete them on failed upload or retry.
- Keep bounded version retention only after multiple verified versions exist, with explicit user policy. Retry transient failures with backoff; handle revoked grants/account changes with clear status and no destructive retries.
- Test worker interruption, duplicated delivery, offline retry, provider/account revocation, low storage, partial upload and new-phone recovery before exposing a working automatic-backup switch.
