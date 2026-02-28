# Changelog

## [1.2.4] - 2026-02-28
### Added
- Feature: Pin services (favorites appear at the top in "Deine Favoriten" section).
- Feature: Remove pinned services from favorites.

## [1.2.3] - 2026-02-26
### **Fixed**
- UI: Resolved a crash when opening the update dialog due to missing Material theme attributes.
- UI: Enforced pitch-black status bar in light mode for improved contrast.
- Update: Switched to stable `androidx.appcompat.app.AlertDialog` for reliable updates on all devices.
- Permissions: Corrected `FileProvider` configuration for seamless APK installation on Android 14/15/16.
- Notifications: Improved reliability and prevented false recovery alerts.
- Sync: Fixed potential infinite loop in the update checker background routine.

## [1.2.2] - 2026-02-25
### Fixed
- UI: Resolved a crash when opening the update dialog due to missing Material theme attributes.
- Notifications: Improved reliability by handling network errors gracefully during background polling.
- Status: Prevented false recovery notifications ("All systems reachable") caused by interrupted heartbeat API requests.
- Logic: Refined background service to only trigger notifications if the data fetch was explicitly successful.
- Update: Improved in-app update discovery by using the Gitea Release API.

## [1.2.1] - 2026-02-24
### Fixed
- UI: Improved update dialog contrast in light mode on Android 14.
- UI: Switched to Material Design dialogs for consistent styling across system modes.

## [1.2.0] - 2026-02-24
### Added
- Update: Migrated update check to Gitea Release API for more reliable version detection.
- Sync: Added startup background check for pending updates.
