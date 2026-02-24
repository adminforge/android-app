# Changelog

## [1.1.5] - 2026-02-24
### Fixed
- UI: Improved text contrast on Donate page for Android 14 devices (explicit white text).
- Update: Fixed "version_code" key mismatch in UpdateChecker to restore update notifications.

## [1.1.4] - 2026-02-24
### Changed
- Privacy & Cleanup: Removed `DEPENDENCY_INFO_BLOCK` metadata from the APK signature to eliminate unnecessary library telemetry.

## [1.1.3] - 2026-02-24
### Changed
- Privacy & Security: Removed sensitive `REQUEST_INSTALL_PACKAGES` and `WRITE_EXTERNAL_STORAGE` permissions.
- Update Mechanism: Refactored in-app update notifications to redirect to the official Gitea release page for safer manual updates.
- Release Automation: Standardized APK naming convention to `adminforge-v[version].apk`.

## [1.1.2] - 2026-02-24
### Changed
- Privacy & Security: Removed sensitive permissions (Note: Re-released as v1.1.3 with correct naming).

## [1.1.1] - 2026-02-24
### Fixed
- OLED Black Regression: Enforced black background and corrected text contrasts for Android 14+ compatibility.
- Theme Synchronization: Unified dark mode enforcement across all activities.

## [1.1.0] - 2026-02-24
### Added
- **Milestone**: Official leap to version 1.1.0 following a comprehensive security and performance audit.
- **Security**: Hardened app configuration with disabled cleartext traffic and sandboxed WebViews.
- **Performance**: Optimized background polling (5-10 min) and caching mechanisms for better efficiency and battery life.

### Changed
- **Infrastructure**: Fully transitioned to a Git-centric OTA update model.
- **Cleanup**: Removed all legacy `rsync` deployment remnants.
- **Workflow**: Solidified the automated Gitea release routine with integrated changelog synchronization.

## [1.0.159] - 2026-02-24
### Added
- **Release Automation**: Integrated Gitea "latest" link in README.
- **Workflow**: Automated release notes extraction for Git releases.
- **Branding**: Added centered screenshots to the README.

## [1.0.157] - 2026-02-24
### Added
- **UI Integration**: Added centered screenshots to the README.
- **Routine**: Synchronized changelog and established automated release procedures.

## [1.0.155] - 2026-02-24
### Added
- **Release Assets**: Standardized Gitea release names to include the version number (e.g., `adminforge-v1.0.155.apk`).

## [1.0.154] - 2026-02-24
### Added
- **Gitea Automation**: Integrated Gitea API for fully automated releases with APK attachment.

## [1.0.152] - 2026-02-24
### Changed
- **Documentation**: Centered the project logo and refined the README layout.

## [1.0.151] - 2026-02-24
### Added
- **Git Routine**: Automated Git commits, tagging, and pushing within the release script.
- **Branding**: Updated the README with the official high-resolution adminForge logo.

## [1.0.150] - 2026-02-24
### Changed
- **Assets**: Updated "PDF Tools" icon for better visibility.
- **Licensing**: Added GNU GPL v3.0 license and project README.

## [1.0.149] - 2026-02-24
### Fixed
- **System Integration**: Fixed status bar visibility issues on Android 15 and 16 using `fitsSystemWindows`.
- **UI Consistency**: Applied layout fixes to all activities (Status, News, Donate, Settings, etc.).

## [1.0.147] - 2026-02-24
### Added
- **High-Frequency Polling**: Implemented a refined background update mechanism using WorkManager that polls every 5-10 minutes when required.
- **Android 16 Compliance**: Fully migrated to WorkManager 2.10.0 and targeted API 36 (Android 16 Baklava).
- **Coroutine Integration**: Refactored background tasks to use non-blocking suspend functions for better battery efficiency.
- **Exponential Backoff**: Added robust error handling with automatic retry logic for network requests.
- **Robust Fallback**: Improved the polling mechanism to act as a seamless fallback if the push distributor is unavailable.
- **Update Reliability**: Improved internal version tracking and installation flow.

## [1.0.142] - 2026-02-23

## [1.0.141] - 2026-02-23
### Added
- **Final Release Consolidation**: All recent improvements to background reliability, notification delivery, and visual branding have been merged into this stable release.
- **Notifications**: "Expedited Work" support ensures immediate alert delivery even in battery-saving mode or when the app is closed.
- **Visuals**: Modernized notification branding with a precise "aF" silhouette for the status bar and a high-resolution colored logo for the notification drawer.
- **Reliability**: Overhauled push-to-notification pipeline and implemented "All systems operational" recovery alerts.
### Fixed
- **Stability**: Cleaned up internal diagnostic tools and synchronized background/foreground tracking states.

## [1.0.136] - 2026-02-23
### Added
- **Notification Text Polish**: Implemented correct German grammar for singular/plural status alerts.

## [1.0.135] - 2026-02-23
### Added
- **Reliability Overhaul**: Consolidated all background polling and notification work into a single `NotificationWorker` session.

## [1.0.134] - 2026-02-23
### Changed
- **Menu Hierarchy**: Strategic reordering of the overflow menu items.

## [1.0.133] - 2026-02-23
### Removed
- **Menu Divider**: Removed the dashed horizontal line for a cleaner look.

## [1.0.132] - 2026-02-23
### Added
- **Menu Polish**: Replaced dashed dividers with continuous Unicode box characters.

## [1.0.131] - 2026-02-23
### Added
- **Dividers**: Implemented crisp visual separation lines in the main navigation menu.

## [1.0.130] - 2026-02-23
### Added
- **UnifiedPush**: Stabilized registration flow for ntfy and Gotify distributors.

## [1.0.129] - 2026-02-23
### Changed
- **Menu Reordering**: Grouped operational items at the top.
### Fixed
- **Permission Flow**: Resolved a race condition in the notification permission dialog.

## [1.0.128] - 2026-02-23
### Added
- **Onboarding**: Improved technical feedback for UnifiedPush distributors.
- **Manifest**: Added `<queries>` for better distributor discovery on Android 11+.

## [1.0.127] - 2026-02-23
### Added
- **Permissions**: Refined POST_NOTIFICATIONS handling for modern Android.

## [1.0.126] - 2026-02-23
### Added
- **Notification Milestone**: Initial release of background sync and notification support.
- **Markdown**: Integrated Markwon for rich text changelog rendering.

## [1.0.125] - 2026-02-23
### Added
- **Security**: Hardened WebView configuration.

## [1.0.124] - 2026-02-23
### Added
- **Security Hardening**: Globally enforced HTTPS and WebView sandboxing.
- **Safe Intent Handling**: Interceptors for `mailto:`, `tel:`, and `intent://` schemes.

## [1.0.123] - 2026-02-23
### Changed
- **Donate Page Labels**: Renamed "Direktüberweisung" to "Überweisung".
- **Donate Page Cleanup**: Removed redundant "Zahlungsmethoden" header.

## [1.0.122] - 2026-02-23
### Added
- **Unread News**: Added a "Clear All" floating chip to the news section.
- **Badge Sync**: Global update of the bottom navigation badge when clearing news.

## [1.0.121] - 2026-02-23
### Added
- **Filter Refinement**: Improved contrast for the active offline filter state.

## [1.0.120] - 2026-02-23
### Added
- **Offline Filter**: Moved the filter toggle inside the search bar as an icon.
- **Visuals**: Overall health card is now hidden during active filtering.

## [1.0.119] - 2026-02-23
### Added
- **Status Alerts**: Changed the global error message for offline services.

## [1.0.118] - 2026-02-23
### Changed
- **Performance**: Optimized polling frequencies to 10-minute intervals.
- **Battery**: Implemented delayed shutdown for network workers.

## [1.0.117] - 2026-02-23
### Added
- **Status Logic**: Fixed Speedtest status calculation for 'Pending' and 'Offline' states.
- **Icons**: Added `ic_warning` for high-visibility status warnings.

## [1.0.116] - 2026-02-23
### Added
- **Status Refresh**: Fixed a bug where data didn't refresh immediately on tab switch.

## [1.0.115] - 2026-02-23
### Added
- **Foreground Polling**: Implemented a global singleton for app-wide status updates.
- **Activity Integration**: Universal support for the status badge across all screens.

## [1.0.114] - 2026-02-23
### Added
- **Status Auto-Refresh**: Implemented periodic 60-second background fetch.

## [1.0.113] - 2026-02-23
### Fixed
- **Navigation Badge**: Corrected logic to only count actual outages (ignoring 'Unknown').

## [1.0.112] - 2026-02-23
### Changed
- **Health logic**: "Alle Systeme funktionsfähig" is now maintained even if some services are unknown.

## [1.0.111] - 2026-02-23
### Fixed
- **Reddit Status**: Resolved an edge case where missing heartbeat data caused 'Down' status.

## [1.0.110] - 2026-02-23
### Added
- **Status Consistency**: Added a gray question mark icon for paused or unknown services.

## [1.0.109] - 2026-02-23
### Fixed
- **Navigation Background**: Standardized the bottom bar to be opaque on the Home page.

## [1.0.108] - 2026-02-23
### Added
- **Navigation Polish**: Unified all activity layouts to use a shared `<include>` for bottom menus.
- **Clipping**: Fixed badge clipping issues in the navigation bar.

## [1.0.107] - 2026-02-23
### Added
- **Status Badge**: Initial implementation of the red notification badge on the "Status" tab.

## [1.0.106] - 2026-02-23
### Added
- **OLED Incident Styling**: Unified background of the top health banner to match the app theme.
- **Compact UI**: Reduced padding for a slimmer health profile.

## [1.0.105] - 2026-02-23
### Changed
- **Banner Logic**: Redesigned the health banner to match the exact website states.

## [1.0.104] - 2026-02-23
### Added
- **Status Detail**: Added "teilweise beeinträchtigt" state for partial outages.

## [1.0.103] - 2026-02-23
### Added
- **Interactive Status**: Added prominent health banner to the top of the Status page.

## [1.0.102] - 2026-02-23
### Added
- **Real-time Heartbeats**: Cross-referenced service monitors with live heartbeat API.

## [1.0.101] - 2026-02-23
### Changed
- **Visuals**: Updated all card backgrounds to the custom `#262626` gray for better contrast.

## [1.0.100] - 2026-02-23
### Added
- **Color Sync**: Standardized all navigation and toolbar icons to absolute pure white (#FFFFFF).

## [1.0.99] - 2026-02-23
### Changed
- **UX**: Matched the bottom navigation style to the YouTube dark mode aesthetic.

## [1.0.98] - 2026-02-23
### Fixed
- **Header Glitch**: Resolved duplicated "adminForge" title in the toolbar.

## [1.0.97] - 2026-02-23
### Added
- **Scrolling Headers**: Reconfigured layout architecture for scrollable native headers.
- **OLED Full Black**: Integrated system-wide OLED black theme.

## [1.0.96] - 2026-02-23
### Added
- **Pull-to-Refresh**: Integrated SwipeRefreshLayout into News and Status activities.

## [1.0.95] - 2026-02-23
### Fixed
- **Web Layout**: Synchronized the bottom menu in WebActivity.

## [1.0.94] - 2026-02-23
### Added
- **Search Polish**: Shortened hints to "Suchen" and improved field contrast.
- **Typography**: Refined app icon typography for better legibility.

## [1.0.93] - 2026-02-23
### Added
- **Logos**: Integrated high-quality logos for PayPal, Wero, Liberapay, Patreon, and Bitcoin.

## [1.0.92] - 2026-02-23
### Added
- **Donation Features**: Interactive copyable bank details in the Donate section.

## [1.0.91] - 2026-02-23
### Added
- **Update Logic**: Implemented session-level guards for update checks.

## [1.0.90] - 2026-02-23
### Changed
- **Donate UI**: Replaced the donation goal with expanded payment provider cards.

## [1.0.89] - 2026-02-23
### Fixed
- **News Stability**: Fixed crashes in the native News interface by optimizing ProgressBar logic.

## [1.0.88] - 2026-02-23
### Changed
- **Navigation Reordering**: Moved contact links to the overflow menu to free up bottom tab space.

## [1.0.87] - 2026-02-23
### Fixed
- **Adaptive Icon**: Adjusted launcher icon inset to prevent letter clipping.

## [1.0.86] - 2026-02-23
### Added
- **Donate Integration**: JSoup-based HTML parsing for the donation section.

## [1.0.85] - 2026-02-23
### Added
- **Native Status**: Ported the status board to a fully native implementation using Ping API.

## [1.0.84] - 2026-02-23
### Added
- **Native News**: Fully native RSS feed viewer with Material card layouts.

## [1.0.81] - [1.0.83] - 2026-02-23
### Added
- **Background Sync**: Implemented initial WorkManager support for periodic RSS fetching.

## [1.0.80] - 2026-02-23
### Added
- **UX**: Refined search bar focus and instant filtering.

## [1.0.66] - [1.0.79] - 2026-02-23
### Added
- **Initial Polish**: Iterative development of the native-first interface.

## [1.0.65] - 2026-02-22
### Added
- **Enhanced Status Monitoring**: Multi-API heartbeat checks for real-time service health.
- **Health Banner**: New high-visibility banner for the status page ("All systems operational").

## [1.0.61] - [1.0.64] - 2026-02-22
### Added
- **Bugfixes**: Resolved occasional NewsActivity progress bar crashes.

## [1.0.60] - 2026-02-22
### Added
- **Monitor Filtering**: First draft of the offline-only service filter.

## [1.0.56] - [1.0.59] - 2026-02-22
### Added
- **Polish**: Finalized card shadows and elevation levels.

## [1.0.55] - 2026-02-22
### Added
- **OLED Black Support**: Replaced grey backgrounds with pure #000000 for OLED efficiency.
- **Coordinate Layouts**: Nested scrolling enabled for all native pages and the WebView.

## [1.0.51] - [1.0.54] - 2026-02-22
### Added
- **UI consistency**: Matched Home and News card padding.

## [1.0.50] - 2026-02-22
### Added
- **Layout Synchronization**: Ensured the bottom navigation menu does not overlap with scrollable content.

## [1.0.46] - [1.0.49] - 2026-02-22
### Added
- **Native Logic**: Ported news date formatting to localized formats.

## [1.0.45] - 2026-02-22
### Added
- **Native News Expansion**: Fully native RSS feed viewer with custom card layouts.
- **Dynamic Card Styling**: Standardized card elevation and corner radius (#262626 background).

## [1.0.41] - [1.0.44] - 2026-02-22
### Added
- **Milestone**: Full native migration phase started.

## [1.0.40] - 2026-02-22
### Added
- **Pull-to-Refresh**: Added swipe-down gestures to Status and News pages.

## [1.0.36] - [1.0.39] - 2026-02-21
### Added
- **Refinement**: Improved service icon extraction logic.

## [1.0.35] - 2026-02-21
### Added
- **Native Status Page**: Custom implementation of the AdminForge status board using native Android views.

## [1.0.31] - [1.0.34] - 2026-02-21
### Added
- **Assets**: Added high-resolution payment brand icons.

## [1.0.30] - 2026-02-21
### Added
- **News Logic**: Implemented 2-second fetch delay on the news page for better UX.

## [1.0.26] - [1.0.29] - 2026-02-21
### Added
- **Cleanup**: Removed unused WebView assets to reduce APK size.

## [1.0.25] - 2026-02-21
### Added
- **Donate Page Redesign**: Implemented a modern card-based layout for support options.
- **Clipboard Features**: Tap-to-copy functionality for banking details (IBAN/BIC).

## [1.0.21] - [1.0.24] - 2026-02-21
### Added
- **Visuals**: Enhanced contrast for OLED dark mode.

## [1.0.20] - 2026-02-21
### Added
- **Payment Method Integration**: Added support for PayPal, Wero, and Liberapay.

## [1.0.16] - [1.0.19] - 2026-02-21
### Added
- **Maintenance**: Minor bugfixes in UpdateChecker logic.

## [1.0.15] - 2026-02-21
### Added
- **Brand Consistency**: Generated high-contrast payment logos.
- **Icon Normalization**: Standardized all branding assets to a uniform transparency and scale.

## [1.0.11] - [1.0.14] - 2026-02-20
### Added
- **Polishing**: Smoother transitions between Activities.

## [1.0.10] - 2026-02-20
### Added
- **Update Checker**: Native APK update detection via the toolbar menu.
- **Overflow Menu**: Centralized website, forum, and contact links.

## [1.0.6] - [1.0.9] - 2026-02-20
### Added
- **Stability**: Fixed a potential null pointer in the RSS worker.

## [1.0.5] - 2026-02-20
### Added
- **Native Search**: High-performance local search for the home screen (services).
- **Adaptive App Icon**: Initial implementation of the "aF" branded launcher icon.

## [1.0.1] - [1.0.4] - 2026-02-20
### Added
- **Preparation**: Finalizing semver migration steps.

## [1.0.0] - 2026-02-20
### Added
- **Initial Stable Release**: Reliable foundation with native navigation and multi-page support.

## [0.1.0] - 2026-02-18
### Added
- **Alpha Release**: Proof-of-concept WebView wrapper and initial RSS worker.
