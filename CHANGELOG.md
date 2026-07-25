# Changelog

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
