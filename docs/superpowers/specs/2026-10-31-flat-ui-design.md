# Red Moon flat UI revision

User rejects rounded cards and requests a research-informed UI with radius0. This overrides the prior rounded Material3 design. Existing no-approval instruction remains in effect.

References actually inspected: KeePassDX official README/screenshot (https://github.com/Kunzisoft/KeePassDX and https://raw.githubusercontent.com/Kunzisoft/KeePassDX/master/art/screen.jpg) and Bitwarden native Android vault-screen source (https://github.com/bitwarden/android/blob/main/app/src/main/kotlin/com/x8bit/bitwarden/ui/vault/feature/vault/VaultScreen.kt). Product/store sites for Bitwarden, 1Password and Google Play were blocked by environment network policy; current ratings/popularity/design approval were not independently verified. No third-party source or assets are copied into Red Moon.

Use the useful structure: compact searchable list, clear account title/type, dedicated add action and simple navigation. Keep Red Moon colors/branding. Remove redundant greeting/counter/filter rows, raised cards and large promotional headings. Use a flat crimson/charcoal palette, clear text hierarchy, fine dividers, one primary filter row and an optional category filter. Expose standard entry fields directly; put field configuration behind explicit options without removing custom field controls. Preserve backend commands, masking, clipboard, biometric and recovery behavior.

Radius0 policy: all five theme shape sizes are0; explicit square button/chip/dialog wrappers cover Material3 independent rounded tokens; custom flat bottom navigation replaces the active pill; FAB shape is explicit square. Android-owned biometric and document-provider dialogs remain controlled by the OS.

Implementation files: ui/SquareControls.kt and RedMoonApp.kt (root/theme/nav); Vault.kt (compact search/list/filter); RecordEditor.kt, RecordDetail.kt, Auth.kt (focused forms); optional category/settings presentation cleanup. Increment Android versionCode to3/versionName2.1.0. Run Kotlin tests/lint, app and instrumentation APK builds; keep cryptographic source unchanged. No new crypto tests are warranted for this UI-only revision. Actual visual/accessibility validation on the phone remains necessary.


Review corrections: fixed-height header chrome was moved into the scrolling list to preserve search results with the IME/landscape; navigation/FAB hide for the IME. Dark accent changed to soft crimson for6.81:1 small-text contrast on its surface; filled-button text uses a dark on-primary color. Material slider/thumb and checkbox visuals are explicitly rectangular rather than relying on theme shapes. Backend/storage/authentication source is unchanged.
