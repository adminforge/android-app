# Changelog

## [1.3.5] - 2026-07-27
### Security
- **Signing key updated.** Releases are now signed with the current key for Android 13+ and, via an APK Signature Scheme v3.1 lineage, with the previous key for Android 7-12, so existing installs keep updating. Signing credentials moved from the build script into `local.properties`.
- **Updater stages the APK in internal storage.** On Android 7-9 any app holding a storage permission could overwrite the downloaded file between download and install.
- **Reduced permissions.** Dropped SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM and WRITE_EXTERNAL_STORAGE, none of which were used. The F-Droid build additionally drops REQUEST_INSTALL_PACKAGES and FOREGROUND_SERVICE_REMOTE_MESSAGING.
- **Restricted link handling.** Pages in the in-app browser may now only hand a known set of URL schemes to other apps instead of any scheme they choose.

### Fixed
- Service icons are only cached once they decode as an image, so an HTML error page served with HTTP 200 no longer leaves an icon permanently blank.
- Icon and update downloads now use connect/read timeouts instead of blocking indefinitely.
- Icon cache keys no longer derive a file extension from the URL, which produced an invalid path for extensionless icon links.

## [1.3.4] - 2026-07-25
### Added
- Feature: Added giebelWORKSPACE service (fully managed digital workplace: files, email, office, chat & video calls) to Online Services.

## [1.3.3] - 2026-07-24
### Fixed
- Fix: Service icons now refresh after the backend.adminforge.de migration. The icon cache was keyed only by filename, so updated users kept seeing the old cached logos even though the URL changed. The cache is now keyed by the full icon URL and cleared once on update.

## [1.3.2] - 2026-07-21
### Added
- Feature: Added asciinema service (record, stream & share terminal sessions) to Büro & Produktivität.
### Fixed
- Fix: Migrated all service icon URLs to backend.adminforge.de after the wp-content move; icons were previously broken (404).

## [1.2.9] - 2026-04-29
### Added
- Feature: Added FediSuite service to Soziales & Kommunikation.
- Feature: Added Mini QR Code Generator service to Büro & Produktivität.
### Changed
- Refactor: Removed unused info_link field from all services to optimize app performance.

## [1.2.8] - 2026-04-28
### Removed
- Removed: Kategorie "Alternative Frontends" nun komplett aus der App entfernt.

## [1.2.7] - 2026-04-19
### Added
- Feature: Added adminForge Mail service to Soziales & Kommunikation.

## [1.2.6] - 2026-04-05
### Removed
- Removed: Kategorie "Alternative Frontends" mit 6 Diensten von der Startseite entfernt.

## [1.2.5] - 2026-03-28
### Added
- Feature: Added DeltaChat (Relay) service to Soziales & Kommunikation.
