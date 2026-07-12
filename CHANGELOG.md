# Changelog

All notable changes to Notesapp are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses simple date-tagged releases rather than strict SemVer
(it's a sideloaded personal app, not a library).

## [v2.0.0] - 2026-07-12 - The Redesign

Major redesign, designed by a 4-lens design council and hardened by full code + UX audits.

### Added
- Traveling bubble nav indicator: a circle that glides between tabs and settles with a springy sideways sway
- Bottom-right contextual floating action button — capture panel on Notes, straight to today's entry on Diary, new event on Calendar
- Compact capture panel anchored to the + button (replaces the full-width sheet; can no longer fling too high or show a clipped edge)
- Editorial typography: Fraunces serif display + Hanken Grotesk body, bundled variable fonts
- Warm depth system: tinted paper-layer shadows, hairline outlines
- Press feedback on every control
- One-time coach tip explaining the editor AI buttons
- Distinct icons for the four capture actions
- Undo for editor and calendar deletes; danger confirms on replace-import, delete-forever, tag merge
- Form validation: no blank-title or past-dated reminders
- Backup progress indicators
- Per-tab state preservation (scroll positions survive tab switching)

### Changed
- Unified icon grammar across all glyphs
- Notification + battery setup prompts merged into one dismissible card
- Settings grouped into sections
- Calendar deletes, tag operations and backup errors got friendlier copy

### Fixed
- AI-extracted reminders now actually arm alarms (previously saved but never fired)
- Recurring reminders no longer burst-fire after downtime; snooze no longer shifts the schedule
- Dead space at the top of every screen (double status-bar inset)
- Search indexing no longer runs on the main thread while typing
- Voice transcription retries no longer double-bill or duplicate transcripts
- Content no longer hides behind the floating nav bar
- Back button closes panels/editor instead of exiting the app
- Status bar icons follow the in-app theme
- First frame matches your theme (no dark flash on light themes)

## [v1.0.1] - 2026-07-11

### Added
- Proper app icon: cream note + terracotta tick on deep charcoal (adaptive + themed icon support)
- Warm paper-and-ink palette across all four themes, terracotta default accent
- New earthy accent set: ochre, sage, olive, oxblood, slate, taupe, forest

### Changed
- Warmer dark themes (no blue-black); splash screen matches the new palette
- De-AI'd visual identity — dropped the generic periwinkle-blue look for something with more character

### Fixed
- First-launch crash on devices without SQLite FTS5 support — search index rebuilt on FTS4

## [v1.0.0] - 2026-07-11 - Initial Release

First full release, built in milestones.

### Added
- Notes with live-markdown editor, full-text search, tags & folders, pin/archive, 30-day trash with undo
- AI clean-up and extract-to-calendar via OpenRouter (DeepSeek V4 Flash default, bring your own key)
- Voice rambles with Qwen3 ASR Flash transcription and inline audio chips
- Custom calendar with exact-alarm reminders (reboot-safe, snooze, Done/Snooze notification actions)
- Diary with moods, streaks, heat-map, "on this day", optional biometric lock
- 4 themes (Light / Dark / Pure Black / Grey) with 8 accents and animated switching
- Quick-settings tile, app-icon shortcuts, share-to-note capture
- Checksummed ZIP backup/restore (manual + scheduled)
- Signing config and CI workflow

[v2.0.0]: https://github.com/TheFadGhost/Notesapp/releases/tag/v2.0.0
[v1.0.1]: https://github.com/TheFadGhost/Notesapp/releases/tag/v1.0.1
[v1.0.0]: https://github.com/TheFadGhost/Notesapp/releases/tag/v1.0.0
