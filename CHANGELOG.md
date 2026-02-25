# Changelog

## [1.2.2] - 2026-02-25
### Fixed
- Notifications: Improved reliability by handling network errors gracefully during background polling.
- Status: Prevented false recovery notifications ("All systems reachable") caused by interrupted heartbeat API requests.
- Logic: Refined background service to only trigger notifications if the data fetch was explicitly successful.

## [1.2.1] - 2026-02-24
### Fixed
- UI: Improved update dialog contrast in light mode on Android 14.
- UI: Switched to Material Design dialogs for consistent styling across system modes.

## [1.2.0] - 2026-02-24
### Added
- OTA: Restored in-app update installation capability for direct updates.
- Security: Implemented FileProvider for safe APK sharing with the system installer.
- Permissions: Re-added REQUEST_INSTALL_PACKAGES permission to support automated installation.

## [1.1.9] - 2026-02-24
### Changed
- Refactor: Removed legacy internal changelog activity and XML layouts.
- Menu: Replaced "Changelog" entry with a direct link to the Source Code on Gitea.

## [1.1.8] - 2026-02-24
### Fixed
- UI: Fixed version name reporting in the update dialog to display correct version instead of "Unknown".

## [1.1.7] - 2026-02-24
### Fixed
- UI: Improved text contrast on the Donate page for high-visibility in Android 14 light mode.
- Update: Corrected "versionCode" JSON key mapping to restore update detection.

## [1.1.4] - 2026-02-24
### Changed
- Privacy: Stripped `DEPENDENCY_INFO_BLOCK` metadata from APK signatures to eliminate third-party library telemetry.

## [1.1.3] - 2026-02-24
### Changed
- Permissions: Removed `REQUEST_INSTALL_PACKAGES` and `WRITE_EXTERNAL_STORAGE` for maximum privacy and lightweight footprint.
- Update: Redirected update notifications to manual installation via the Gitea release page.

## [1.1.1] - 2026-02-24
### Fixed
- UI: Enforced pitch-black backgrounds for OLED displays on Android 14+ devices.
- L10n: Migrated all technical documentation and changelogs to English for better accessibility.

## [1.1.0] - 2026-02-24
### Added
- Feature: Native implementation of News, System Status, and Donation pages.
- Distribution: Prepared initial F-Droid compatible branch architecture.
