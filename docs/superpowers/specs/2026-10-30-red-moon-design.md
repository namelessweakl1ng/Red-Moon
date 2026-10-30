# Red Moon architecture and security design

Status: proposed for user approval. The user selected a single encrypted vault file with an independently tested Rust core. Other details below are implementation proposals, not claims of completed functionality.

## Intent and scope

Build an offline desktop password manager whose vault and source belong to its user. Use Rust, Tauri 2, React with strict TypeScript, and Tailwind. Target Windows first; preserve Linux and macOS compatibility. No accounts, network functionality, telemetry, browser integration, plaintext export, or password recovery service. Bundled assets must support offline first launch. Development dependency downloads are distinct from application networking.

Version one includes setup, unlock, entry CRUD, search, favorites, category/tag filters, secure notes, local duplicate detection, password generation and strength feedback, auto-lock, password changes, encrypted backup/restore, settings, and custom SVG/icon assets. No fake data or inactive controls are acceptable as delivered features.

## Approaches

The selected approach serializes and encrypts a bounded document, with atomic replacement of one vault file. It simplifies complete metadata encryption, portable backups, and adversarial tests. It incurs whole-vault encryption on saves, acceptable for a personal password vault with documented size limits.

An encrypted database could scale partial updates but adds schema, encryption, and backup complexity. It was offered as an alternative; the user selected the single-file design. A Rust core independent of Tauri allows security and storage tests without a desktop display.

## Threat model

Protect a locked vault stolen from local storage or backups against disclosure and undetected modification. Authenticate the header and all stored contents. Wrong passwords, corruption, unsupported formats, and failed writes must not destroy existing data.

Malware, keyloggers, a compromised OS/webview, privileged memory readers, clipboard managers, swap and crash dumps may defeat protection. Zeroization reduces exposure but cannot guarantee deletion of every copy. JavaScript strings cannot reliably be wiped. External copies allow rollback; authenticated encryption does not detect replacement by an older valid vault. Forgetting the master password can permanently prevent recovery. This implementation is not independently audited.

## Vault format and cryptography

Use maintained RustCrypto argon2 and chacha20poly1305, plus zeroize, secrecy, OS randomness, serde and bounded serialization. Confirm current APIs, release compatibility, and RustSec advisories before pinning releases; commit dependency lockfiles during implementation. Never implement encryption primitives.

Version 1 is a fixed binary header followed by ciphertext and its authentication tag. Define exact byte offsets and byte order in the format documentation and tests. The header holds magic, format version, fixed cipher/KDF identifiers, Argon2 version and parameters, a random 16-byte salt, random 24-byte nonce, and ciphertext length. Authenticate the entire canonical header as AEAD additional data. Reject unknown identifiers, trailing bytes, inconsistent lengths and oversized files before allocation or KDF work. Propose a 16 MiB file limit and 10,000-entry limit. Document limits as part of the format.

Derive a 32-byte key using Argon2id v19. Calibrate at creation toward roughly 500 ms, with at least 64 MiB memory, three iterations, and one lane; use conservative hard limits (256 MiB memory, ten iterations, four lanes) when reading untrusted files to bound resource use. Store calibrated parameters. Benchmark defaults on the current host and clearly distinguish Windows measurements still needed. Salt changes at creation and master-password change. Every encryption operation uses a newly generated XChaCha20-Poly1305 nonce, including saves under the same key. Random generation must fail closed.

The encrypted payload contains every entry field, IDs, timestamps, favorites, settings, categories, and tags. Creation and password changes enforce a reasonable minimum master-password length (proposed 14 characters), permit spaces and long passphrases, require confirmation, and show local strength guidance without arbitrary composition rules. Unlock must accept the original password exactly, without normalization or trimming. Bound UTF-8 input size, proposed 1 KiB. Document these decisions and test Unicode.

Keep derived keys and owned sensitive strings in zeroizing Rust containers where feasible. Do not derive Debug or log secret-bearing structures. Zeroize serialized plaintext buffers. No secret values in errors, instrumentation, test failure messages, or crash reporting. Authentication failures return a neutral wrong-password-or-damaged-vault message.

## Core state and IPC boundary

Separate core modules for format/crypto, KDF, storage, model, generation, session security, and backup. A Tauri adapter owns a synchronized session and the per-user app-data vault path. The core remains testable without Tauri. Use structured internal errors with safe UI mappings.

Expose only status, create/unlock/lock, activity, list summaries, get entry details, explicit reveal, save/delete/favorite, generate, copy username/password, settings, password change, and backup/restore operations. Summaries exclude passwords and notes. Get details excludes passwords; fetch notes only for the selected entry. Passwords cross IPC only for explicit reveal/edit or generation. Copy stored passwords in Rust without sending them to React. Do not cache a full decrypted vault in frontend state, browser storage, or service workers. Clear selected detail state and unmount secret editors on lock. Explain that clearing React state is not reliable memory erasure.

All commands validate session expiry and inputs on the backend. The backend is authoritative for inactivity locking, default five minutes; activity messages are bounded and originate from actual keyboard/pointer interactions. A backend timer drops secrets and emits locked state. Also compare elapsed time on every command and after resume. A fake clock supports deterministic tests. Lock before losing access on supported workstation-lock/suspend notifications; handle resume defensively. Windows native event hooks are required for the Windows target. Implement other OS hooks when supported and document/test their limits. Focus loss alone is not proof of workstation lock.

Generation uses OS-backed cryptographic randomness with unbiased sampling, bounded length (12–128 proposed), at least one character from each selected class, and random placement. Reject empty classes and impossible constraints. Local strength feedback is advisory, never a security guarantee. Duplicate detection uses normalized title/username/URL within Rust; never a persistent plaintext index.

Clipboard handling remains in Rust. After password copy, schedule a 20-second check and clear only if the current clipboard still equals the copied content and the app's copy generation is current. Replacing clipboard text and setting identical text again cannot always be distinguished; document this platform limitation. Clear owned clipboard content on lock where possible. Avoid keeping copied content longer than required; clipboard tests use an adapter.

## Storage, transactions, and recovery

Store the vault in Tauri's per-user application data directory. Enforce one cooperating writer with an OS-backed exclusive lock held for the session/process; reject a second writer. Constrain IPC paths to the managed vault, using native dialogs only for encrypted backup inputs/outputs. Reject symlinks or unsafe paths where supported. Unix directories use 0700 and files 0600; verify Windows per-user ACL inheritance and document the supported boundary.

Serialize and encrypt in memory; never create plaintext temporary files. Write ciphertext to an exclusively created random sibling temporary file, sync it, atomically replace using a platform-supported primitive, and sync directory metadata where available. Windows replacement must preserve the old destination until replacement succeeds. Clean only app-owned stale encrypted temporary files. Report durable-commit limitations honestly; after an ambiguous failure, reload and authenticate disk state before further mutation. Test injected failures before and after write/sync/replace. Do not update live model/key state until commit succeeds; prevent concurrent command transactions.

Backups are copies of an authenticated, committed encrypted vault, including all required header parameters. Native file dialogs select destinations. Validate bounded structure and password authentication before restore. Restore requires explicit confirmation, reauthentication of the current vault if present, and the backup password; it remains available from the locked/setup screen for recovery. Stage a verified encrypted pre-restore safety copy, then atomically replace the live vault; abort if preservation fails. Replace session state only after commit. Do not overwrite a live vault on an invalid backup or failed write. Backups may retain the old master password after a password change; explain this in UI and README.

Changing the master password verifies the current password, derives a fresh key and salt with new calibrated parameters, encrypts with a fresh nonce, and atomically commits before replacing the live key. Failure leaves the existing file/key usable. No plaintext recovery keys or backdoor.

## Desktop shell and interface

Bundle all assets; no external fonts/scripts/images. Disable production devtools, navigation to remote origins, shell/opener/network plugins, and unnecessary capabilities. Define explicit command permissions and a strict CSP allowing only bundled resources and required Tauri IPC; avoid unsafe-eval. Verify release configuration and frontend dependency behavior. Use native dialogs with narrowly scoped backend access.

Use the specified dark palette: #0B0B10, #17171F, #E11D48, #FB7185, #F8FAFC, #9CA3AF. Create a crimson crescent/keyhole SVG with generated platform icons. Implement accessible setup/unlock, sidebar dashboard with list and details, entry editor, generator, backups, and settings. Passwords start masked and require explicit reveal. Provide loading/error/empty states, keyboard focus, labels, contrast, reduced-motion support, and responsive layout. Theme preference can offer system/dark and restrained high-contrast variants while preserving the identity. Destructive actions require clear confirmation.

## Delivery and validation stages

1. Architecture and security design, then implementation plan.
2. Rust crypto core with round-trip, wrong-password, tampering/header corruption, invalid bounds, nonce uniqueness, and Unicode/input tests.
3. Atomic persistence and backups with creation/reopen/modification, lock contention, write-failure, successful/failed restore, and password-change tests.
4. Session, generator and IPC with deterministic auto-lock, input validation, generation and clipboard ownership tests.
5. Complete UI, icons, component/interaction tests and desktop integration smoke checks.
6. Format, Rust clippy/tests, strict TypeScript, frontend lint/tests/build, dependency advisory scans, and native build. Report counts and actual outcomes at each stage.

Test a first-run create → save → lock → wrong password → unlock → edit → backup → restore sequence through real IPC where desktop tooling permits. A browser-only frontend does not validate Tauri. Validate a release Linux desktop build if supported here. Windows builds and native lock/ACL/clipboard behavior require a Windows runner; do not claim they passed from cross-compilation or mocks. Do not call the product audited or production-secure.

README and format/security docs cover installation, development, commands, builds, offline runtime, backups, restore, forgotten passwords, threat model and platform limitations. Save tested reusable environment install/start instructions through the onboarding draft tools. Existing cloud tasks are isolated: reuse the checkout without adding worktrees. Snapshot publication and fresh-task restoration are separate from current-instance validation.

## Current environment and builder discretion

At inspection the checkout had no commits/files. Node 24 was available, but Rust and Linux GTK/WebKit development packages were absent. GitHub and Rust download requests failed connecting to the configured proxy even after runtime status reported running. Connectivity must be repaired before dependency installation or advisory checks can be claimed. No application functionality has been implemented or tested.

The builder may choose maintained helper libraries, exact component layout, serialization encoding inside ciphertext, test frameworks, native event adapters, and compatible pinned versions, subject to the constraints above. Changes that weaken cryptography, remove requested functionality, or expose more secrets require a documented decision rather than a silent fallback.
