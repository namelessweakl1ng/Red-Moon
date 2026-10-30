# MASTER PROMPT — RED MOON 2.0

You are a senior Android engineer, Rust security engineer, product designer, and application security reviewer.

Upgrade my existing project, **Red Moon**, into a premium Android password manager and encrypted personal vault.

**Repository:** https://github.com/namelessweakl1ng/Red-Moon

**Target device:** Samsung Galaxy M31, Android 12, ARM64. Continue supporting Android 8+ where practical.

**Existing stack:** Native Kotlin, Jetpack Compose, Rust encrypted vault core, JNI, Gradle.

## Primary goal

The current Android interface is too basic. I want a completely redesigned, professional, intuitive application where I can securely store all my personal information.

I want the ease of use of a modern password manager, with a polished dark interface, but I want complete control over my encrypted data.

The application must work offline. Google Drive connectivity should be optional and used only for encrypted backup and recovery.

**Important:** Do not create a fake UI, empty buttons, placeholder features, or a prototype. Implement working functionality, persistent storage, recovery, and tests.

## 1. Completely redesign the Android UI

Replace the current plain screens and dropdown-based navigation with a cohesive, premium Material 3 interface.

Design identity:
- App name: Red Moon
- Deep black and charcoal backgrounds.
- Tasteful crimson-red accents.
- Excellent typography, spacing, contrast, and hierarchy.
- Clean cards, icons, subtle rounded corners, and intuitive actions.
- Smooth but restrained animations.
- Responsive layouts for smaller Android phones.
- Light and dark themes, with dark as the default.
- Preserve the recognizable Red Moon branding.

Main application navigation:
- Vault
- Notes
- Categories
- Settings

The home screen should display:
- Greeting and vault status.
- Prominent search bar.
- Passwords, notes, identities, and favorites.
- Category shortcuts.
- Recently updated entries.
- A floating Add button for creating records.
- Empty states that explain what the user can do.

Make every screen attractive and usable with one hand.

Use appropriate icons, polished dialogs, visual feedback, loading states, and accessible touch targets.

Do not simply change colors in the current UI. Redesign the information architecture and interactions.

## 2. Fully functional password manager

I must be able to create, edit, view, search, copy, organize, and delete password entries.

Each login entry should support:
- Title or service name.
- Website or application name.
- Username.
- Email address.
- Password.
- Website URL.
- Category.
- Tags.
- Encrypted notes.
- Custom fields.
- Favorite status.
- Creation and modification dates.

Provide show/hide password controls, secure copy buttons, duplicate detection, and an integrated strong password generator.

Passwords must remain encrypted at rest and masked by default.

Implement automatic vault locking, secure clipboard handling, and authentication before sensitive operations when appropriate.

## 3. Custom categories and folders

I need to organize my information however I like.

Provide default categories such as:
- Email accounts
- Social media
- Banking
- Finance
- Government documents
- Work
- Personal
- Shopping
- Education
- Other

Let me create unlimited custom categories within reasonable documented storage limits.

Each category should support a custom name, icon, and color.

I must be able to rename, delete, reorder, and organize categories, and move existing entries between them.

Deleting a category must not silently delete the records inside it.

Include category filters, counts, and searchable category lists.

Category names and organization metadata must be included within the encrypted vault.

## 4. Standalone encrypted notes

Build a proper Secure Notes section.

Notes must be independent records, not just fields attached to passwords.

Each note should support:
- Title.
- Multiline note editor.
- Long-form text.
- Optional checklist.
- Category and tags.
- Favorite or pin.
- Search.
- Editing and deletion.
- Created and modified timestamps.

Support notes for recovery instructions, important personal information, private reminders, and other confidential text.

All note content and sensitive metadata must be encrypted using the existing Rust vault security model.

Do not store notes in plaintext SQLite databases, preferences, cache files, logs, or cloud storage.

Avoid leaking note content through notifications, keyboard suggestions, screenshots, recents previews, or clipboard history.

## 5. Secure identity and custom records

I want to store virtually any type of sensitive information, including Indian identity documents.

Create flexible record types:

**Login:** Websites, apps, email accounts, usernames, passwords.

**Identity:** Full name, addresses, phone numbers, date of birth, identification numbers.

**Indian documents:** Aadhaar number, PAN number, passport details, driving licence, voter ID, and other identification information.

**Financial information:** Bank account details, IFSC, account numbers, and relevant account information.

**Secure note:** Free-form encrypted text.

**Custom record:** Any user-defined fields.

Support custom fields of the following types:
- Plain text
- Secret/masked text
- Multiline text
- Number
- Date
- URL

Allow users to add, rename, reorder, and delete fields.

Sensitive identifiers such as Aadhaar, PAN, account numbers, and passwords should be masked by default and revealed only when requested.

Never transmit this information to third-party analytics, advertising, logging, or crash-reporting services.

Do not use real personal identifiers in development, tests, screenshots, or sample data.

## 6. Google Drive encrypted backups

This feature is essential because I do not want to lose everything if my phone is lost, damaged, reset, or stolen.

Add a dedicated **Backup & Recovery** screen.

### Manual Google Drive backup

Provide a button called "Back up to Google Drive."

Use the Android system document picker so I can select my Google Drive account and destination.

Upload or save an encrypted `.rm` backup containing the complete vault: passwords, notes, identities, categories, custom fields, and necessary recovery metadata.

Only encrypted bytes may leave the application.

Never upload plaintext secrets, encryption keys, or my master password.

Show the destination, backup status, timestamp, and errors accurately. A local write is not proof of cloud synchronization.

### Automatic cloud backup

After manual backup and restore are proven reliable, design optional automatic backups.

If direct Google Drive API integration is necessary:
- Use Google's supported account authorization flow.
- Request the least privileged practical Drive permissions.
- Never request full Drive access unnecessarily.
- Never embed OAuth client secrets.
- Allow me to select, disconnect, and change my backup account.
- Schedule backups safely with Android WorkManager.
- Keep recoverable versioned backups.
- Never overwrite or delete the last verified good backup after a failed upload.
- Retry temporary failures without corrupting data.

Display the last successful and last verified backup status.

Explain which backup features work only when the phone is online.

## 7. Recovery on a new phone

This is a critical acceptance requirement.

Imagine my Samsung M31 has completely died.

I purchase another Android phone, install Red Moon, and select "Restore existing vault."

I must be able to:
1. Select my encrypted backup from Google Drive or local storage.
2. Enter the master password associated with that backup.
3. Restore all passwords, categories, notes, identities, and custom fields.
4. Verify that the restored data is intact.
5. Configure biometrics again on the new phone.

Recovery must not depend on access to the old device's Android Keystore or fingerprint hardware.

Wrong passwords, tampered backups, interruptions, and failed restores must never destroy a valid existing vault.

Provide explicit confirmation before replacing an existing vault and keep a safe encrypted recovery copy.

Ensure backward compatibility with existing Red Moon `.rm` backups wherever possible.

Clearly explain that older backup versions may require the old master password after a password change.

## 8. Biometric unlock

Add fingerprint unlock and other secure biometrics supported by the device.

Use:
- AndroidX BiometricPrompt
- Android Keystore
- Hardware-backed key protection when available
- Supported strong biometric authenticators

Biometric unlock must be optional.

The first unlock and initial biometric setup must require the master password.

Do not implement biometric unlock as a simple UI check that bypasses encryption.

Design a genuine cryptographic unlocking mechanism using an authentication-protected Android Keystore key to protect a vault-key wrapping mechanism.

Never store the plaintext master password.

If biometrics change, the protected key becomes invalid, the app is reinstalled, the device is reset, or biometric authentication fails, safely fall back to master-password authentication.

Allow me to disable and re-enable biometrics.

Preserve secure background locking and session expiration.

The master password must remain the independent recovery method for encrypted backups.

## 9. Preserve and strengthen encryption

Inspect the existing Rust encrypted vault architecture before making changes.

Preserve established protections, including Argon2id key derivation, authenticated encryption, secure randomness, atomic encrypted writes, and zeroization wherever practical.

Do not invent custom cryptographic algorithms.

If a new data schema or biometric key-wrapping architecture requires migration:
- Design a versioned, authenticated migration.
- Preserve existing vault data.
- Test migration using real legacy-format fixtures containing synthetic data.
- Do not silently reset or replace an existing vault.
- Keep the original encrypted backup until migration is verified.
- Document compatibility and limitations.

Make sure all new record types and metadata are encrypted.

Avoid exposing full vault contents to Compose state when only a record summary is needed.

Do not claim that an application is perfectly secure, independently audited, or immune to compromised operating systems.

## 10. Security settings

Include:
- Change master password.
- Enable/disable biometric unlock.
- Auto-lock timeout.
- Immediate lock button.
- Screenshot and recent-app preview protection.
- Clipboard clearing where supported.
- Backup and restore.
- Encryption and recovery information.
- Data deletion with confirmation.
- App version and security documentation.

No analytics, trackers, advertisements, or unnecessary permissions.

Google Drive integration must not make the offline password manager dependent on internet access.

## 11. Architecture and quality

Keep Kotlin and Jetpack Compose responsible for Android UI and platform integration.

Keep the Rust vault core responsible for encryption, validation, persistence, and sensitive vault operations.

Use clean, maintainable modules and lifecycle-aware state management.

Maintain desktop compatibility with the shared Rust core.

Provide reliable handling for process death, activity recreation, app backgrounding, failed biometrics, interrupted backup operations, and low-storage conditions.

Avoid large single-file screens. Separate UI components and features into understandable modules.

## 12. Testing requirements

Use test-driven development wherever practical.

Test:
- Adding, editing, deleting, and reopening every record type.
- Category creation, renaming, deletion, and assignment.
- Custom-field persistence.
- Secure-note persistence.
- Existing vault migration without data loss.
- Encryption and authentication failure handling.
- Fresh-install restore using an old encrypted backup.
- Incorrect backup password and corrupted backup handling.
- Biometric enrollment, unlock, cancellation, and invalidation.
- Master-password recovery after biometric keys become unavailable.
- Background locking and process recreation.
- Manual Drive backup and restore through real Android providers.
- Automatic backup retries and version retention if implemented.

Run Rust tests, Kotlin tests, lint, Android builds, and supported instrumentation tests.

Test the actual Samsung M31 where connected.

A successful compilation alone is not proof that authentication, cloud backup, or recovery is reliable.

## 13. Execution instructions — Superpowers workflow

Use Superpowers skills throughout the project:

1. Inspect the existing repository and functionality.
2. Brainstorm the new user experience and secure architecture.
3. Present a design for approval.
4. Create a detailed implementation plan with file-level tasks.
5. Implement independently testable milestones, using test-driven development.
6. Perform systematic debugging when tests fail.
7. Request rigorous security and code reviews.
8. Verify implementation results before claiming completion.

Recommended milestone order:

**Milestone 1:** Redesign the Android UI and improve password entry workflows.

**Milestone 2:** Implement categories, standalone notes, identities, and custom fields with safe vault migration.

**Milestone 3:** Implement fully tested manual Google Drive backup and new-device recovery.

**Milestone 4:** Implement cryptographically sound biometric unlock.

**Milestone 5:** Implement optional automatic encrypted Drive backups, complete security regression tests, and polish.

Keep the app buildable and usable between milestones.

Do not replace working encryption with mock storage or weaken security to make features easier.

After implementation, provide:
- Summary of features completed.
- Files changed.
- Security decisions and limitations.
- Tests actually run and their results.
- APK build instructions.
- Installation and upgrade instructions.
- A complete backup and restore test procedure.
- Remaining issues that prevent safe production use.

## Final success criteria

Red Moon should feel like a professional, polished personal vault.

I should be able to open the app with fingerprint authentication, organize my accounts into categories, save passwords and encrypted notes, store Aadhaar/PAN details and arbitrary sensitive information, and create encrypted Google Drive backups.

If I lose my phone, I must be able to restore a verified backup on another compatible device using my master password, without needing the old phone.

**Prioritize data integrity, reliable recovery, and genuine security over visual polish whenever those goals conflict.**

Do not use real personal credentials until independent security review and device-level validation are completed.