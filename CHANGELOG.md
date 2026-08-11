# Changelog

## [1.3.9] - 2026-07-27
### Fixed
- **Background polling could silently slow to a crawl.** A single failed news or status fetch used to skip rescheduling the next poll entirely and hand control to WorkManager's own exponential backoff, which can grow the interval to hours. A failed cycle now still reschedules the next attempt at the normal 5-10 minute cadence.
- **Reopening the app could reset the poll timer.** The background chain was rebuilt from scratch (with a fresh 5-minute delay) every time the app cold-started while notifications were enabled, so a user who reopened the app more often than that would never actually let a background poll fire. It now only starts the chain if one isn't already scheduled.
- **Worker self-rescheduling used the wrong WorkManager policy**, cancelling its own currently-running invocation as a side effect (usually harmless, but undefined and made the retry/backoff bookkeeping unreliable).
- **Favorites-only status could overcount.** An outage affecting several checks of the same pinned service (e.g. a mail service's IMAP and SMTP both going down together) was counted as multiple outages instead of one, inflating the badge and notification count beyond what the user actually pinned.

## [1.3.8] - 2026-07-27
### Fixed
- **Unread-news badge stopped updating.** adminforge.de/feed serves a fixed-size window, so once the site had more articles than that window, the feed's length never grew and the old count-based comparison stopped detecting new articles entirely (push notifications were unaffected, they compare differently). It now tracks the newest article's link instead.
- **Bitcoin donate card could crash the app** on a device with no Bitcoin wallet installed; now shows a toast instead, matching the in-app browser's existing handling of unhandled link schemes.
- **Interactive update check could crash** if the screen was closed while the check was still running.
- Removed a donate-page network fetch that ran on every visit but never affected what was displayed (the cards have been static for a while); also drops the now-unused Jsoup dependency.

### Security
- The in-app updater now only downloads from git.adminforge.de, regardless of what a `version.json` might specify - defense in depth alongside the existing signature check.

### Changed
- News and status network requests now time out instead of blocking indefinitely.

## [1.3.7] - 2026-07-27
### Fixed
- **Favorites-only status ordering bug.** Right after enabling the setting (or on a fresh install), the offline count could briefly use the previous poll's data instead of the one just fetched, so a real outage wasn't reflected until the next cycle.
- **False all-clear notification.** Enabling favorites-only with no pinned service (or none that resolve to a monitor) made the offline count permanently read 0, which could fire an "all clear" notification while an outage was still ongoing. It now falls back to the unrestricted count in that case.
- **Status screen crash risk.** An unexpected shape in the status-page response could crash the screen while typing in its search box; the favorites lookup that could throw is now guarded and computed once per refresh instead of on every keystroke.

## [1.3.6] - 2026-07-27
### Added
- **Favorites-only notifications.** New optional setting (off by default) to only get push notifications and see the status badge for outages of pinned favorite services, instead of every service on the status page.

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
