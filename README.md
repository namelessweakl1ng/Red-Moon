# Red Moon

Offline encrypted password manager using Rust, Tauri 2, React/TypeScript and Tailwind. Minimal dark interface with square corners.

**Incomplete validation: do not store real credentials yet.** Linux native compilation and automated core/frontend tests have passed. Full desktop workflows and Windows behavior remain unverified. The application has not been independently audited.

## Development

Install Rust, Node.js 24 and platform Tauri 2 prerequisites (GTK3/WebKitGTK 4.1 development libraries on Linux; Visual Studio C++ tools and WebView2 on Windows).

```sh
npm ci
cargo test -p red-moon-core
npm run typecheck
npm run lint
npm test
npm run tauri dev
npm run tauri build
```

In this cloud workspace, source `scripts/cloud-env.sh` to activate the local toolchain and extracted Linux libraries. Desktop launch currently fails because WebKit expects its helper processes at a system path. Normal system package installation is recommended on a development machine.

## Vault and recovery

The vault lives in the OS per-user application-data directory for `com.redmoon.vault`, as `vault.rm`. Argon2id derives a 256-bit key; XChaCha20-Poly1305 authenticates and encrypts the complete payload and authenticates its technical header. No plaintext temporary files or exports are intended. Use at least 14 characters for a master password.

Backups are encrypted `.rm` files. Restore validates the backup password and preserves an encrypted safety copy before replacing an existing vault. Old backups retain their original passwords after a master-password change. Forgotten passwords may permanently prevent recovery; there is no reset service or backdoor.

Inactivity lock defaults to five minutes. Foreground loss provides additional locking. Windows native lock/suspend hooks are implemented but unverified on Windows. Clipboard cleanup checks content ownership after 20 seconds; identical text copied later cannot always be distinguished. JavaScript memory cannot reliably be wiped.

Protects a locked vault against file theft, not malware, keyloggers, compromised operating systems, clipboard history or rollback to an older valid vault.

## Remaining work

Real desktop integration and clipboard/dialog tests, Windows builds/ACL/suspend verification, final independent code review and cloud configuration persistence are outstanding. RustSec reports unresolved transitive GLib RUSTSEC-2024-0429 and unmaintained proc-macro-error RUSTSEC-2024-0370. Do not interpret passing tests as an audit or production-security claim.

The approved design and plan are under `docs/superpowers/`.
