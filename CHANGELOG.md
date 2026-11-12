# Changelog

All notable changes to TVFrame are listed here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and TVFrame uses [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-12

This release is all about **Sync**: being able to see what it's doing, choosing what it downloads, and getting new photos instead of the same ones again.

### Added
- **Sync status card** in the Gallery tab: "Searching…", **"Downloading 12 of 30"** with a progress bar, then the result ("Added 12 new photos", "No new photos…", "Nothing matched…", or a clear error). It also shows when the last sync ran, what started it, and roughly when the next automatic sync will run. Background and screensaver syncs show up there too. ([#1](https://github.com/sumeet2025/tvframe/pull/1))
- **Pick several categories at once.** The category buttons are now checkboxes, and a sync mixes the results round-robin with duplicates removed. ([#4](https://github.com/sumeet2025/tvframe/pull/4))
- **"Photos per sync" picker** (10 / 20 / 30 / 40 / 50, default 30). It's remembered and shown on the button ("⟳ Sync 30 New"). The sync pages through Commons results until it finds that many new photos. ([#6](https://github.com/sumeet2025/tvframe/pull/6))
- **Only new photos.** TVFrame remembers every photo it has downloaded, even after "Clear Non-Favorites", and each search continues where the last sync stopped. A **"↺ Reset seen history"** button starts over. ([#7](https://github.com/sumeet2025/tvframe/pull/7))
- A **"Downloading: …"** label that always shows what Sync will fetch. ([#3](https://github.com/sumeet2025/tvframe/pull/3))
- Unit tests for the sync and cache logic.

### Changed
- **Minimum resolution** is now its own clearly labelled, single-choice row: 720p / 1080p / 2K / 4K. Commons filters by width on its side, and downloaded images are sized to the minimum and your screen. ([#5](https://github.com/sumeet2025/tvframe/pull/5))
- **The search box only appears when "Custom Search" is selected.** Preset buttons no longer overwrite the custom text, and the leftover "Alpine mountains sunset" default is gone. ([#3](https://github.com/sumeet2025/tvframe/pull/3))
- **"Clear Non-Favorites" now starts a sync right away** to refill the gallery with new photos. ([#8](https://github.com/sumeet2025/tvframe/pull/8))
- Changing categories, resolution or shape no longer starts a download on every tap. Press Sync when you're ready. Opening the app or starting the screensaver only syncs if the last sync was more than 6 hours ago or the gallery is empty. ([#8](https://github.com/sumeet2025/tvframe/pull/8))
- New photos are downloaded during the sync, so they show instantly and work offline.
- "Night Sky & Aurora" is now "Night Sky & Space".

### Fixed
- **Category presets that returned few or no photos.** Pictures of the Day, the default, matched 0 photos, and Rain & Storms and Night Sky did too. Every preset now returns hundreds to thousands. ([#2](https://github.com/sumeet2025/tvframe/pull/2))
- **Cache cleanup deleted gallery photos.** It trimmed the gallery to 300 photos even when there was plenty of space, and could empty it completely when over quota. ([#9](https://github.com/sumeet2025/tvframe/pull/9))
- A favorite toggled during a sync could be undone. Photo updates now run in a database transaction. ([#9](https://github.com/sumeet2025/tvframe/pull/9))
- Sync no longer fails silently when nothing matches or the TV is offline.

### Upgrade notes
- The app database moves from version 1 to 2 (new seen-history tables). Your photos, favorites and settings are kept, and the categories you had selected carry over. Photos already in your gallery count as "seen".
- The APK is signed with the same key as 1.2.0, so it installs over 1.2.0 without uninstalling.
- The download link now has a stable name: `https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame.apk` always gets the newest version.

## [1.2.0] - 2026-11-11

### Fixed
- The slideshow order no longer jumps or repeats when you favorite a photo or the gallery refreshes.
- "Favorites only" now applies to the screensaver only, not the in-app gallery.
- Remote focus is more reliable, including the Back button.
- Lower memory use: images are decoded at screen size, and prefetching is disk-only and size-capped.

## 1.1.0 - 2026-11-10

### Changed
- I made TVFrame brand-neutral so it works on any Android TV or Google TV device running Android 11 or newer.
- New app icon, launcher icons and TV banner.

### Added
- A signed release APK, an install guide (Downloader app, adb, or USB stick) and an `install_to_tv.sh` helper script.

### Fixed
- Manifest and USB receiver issues (USB events were handled twice).

[1.3.0]: https://github.com/sumeet2025/tvframe/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/sumeet2025/tvframe/releases/tag/v1.2.0
