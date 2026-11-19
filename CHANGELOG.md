# Changelog

All notable changes to TVFrame are listed here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and TVFrame uses [Semantic Versioning](https://semver.org/).

## [1.4.1] - 2026-11-19

### Changed
- **Custom Search is back in the Google Play version, and it's family-safe there.** On Play it only searches Wikimedia's reviewed Featured, Quality and Valued images. It refuses adult words before searching ("That search isn't available. Try another word."), and it drops any photo whose categories or title point to nudity, sexual content, anatomy/medical, gore or violence. The GitHub version (this APK) keeps open, unfiltered search. ([#40](https://github.com/sumeet2025/tvframe/pull/40), fixes [#38](https://github.com/sumeet2025/tvframe/issues/38))

### Fixed
- **Sync now reads past the first page of results.** TVFrame asked Commons for too few categories, so Commons stopped sending the next-page marker and every sync looked at just one page. "Photos per sync" now fills up much more reliably, and searches keep going deeper over time. ([#40](https://github.com/sumeet2025/tvframe/pull/40), fixes [#39](https://github.com/sumeet2025/tvframe/issues/39))

### Upgrade notes
- It installs over 1.4.0 and earlier without uninstalling. Photos, favorites and settings are kept, and there's no database change.

## [1.4.0] - 2026-11-19

This release gets TVFrame ready for Google Play on Android TV, and fixes the remote-control and privacy issues I found while checking it against Play's rules.

### Added
- **Two builds: GitHub and Google Play.** Same package name and signing key, so either installs over the other and keeps your data. The GitHub APK (this release) keeps everything. The Play build is TV-only and leaves out All Files Access, Custom Search and the Lightning zap, which Play doesn't allow. ([#28](https://github.com/sumeet2025/tvframe/pull/28), fixes [#20](https://github.com/sumeet2025/tvframe/issues/20))
- **Photo license next to the photographer**, for example "📷 Name · CC BY-SA 4.0", in the slideshow, the screensaver and on Gallery cards. ([#34](https://github.com/sumeet2025/tvframe/pull/34), fixes [#26](https://github.com/sumeet2025/tvframe/issues/26))
- **[Privacy policy](PRIVACY.md)** and an **[MIT license](LICENSE)**. The About tab has a short Privacy summary. ([#35](https://github.com/sumeet2025/tvframe/pull/35), fixes [#27](https://github.com/sumeet2025/tvframe/issues/27))
- README section on getting the full GitHub version and installing it with a USB stick. ([#36](https://github.com/sumeet2025/tvframe/pull/36))

### Changed
- TVFrame is now marked as TV-only (`android.software.leanback` required).
- Removed two storage permissions that were declared but never used (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`). USB folders still work through the folder picker, and the GitHub build still has optional All Files Access. ([#28](https://github.com/sumeet2025/tvframe/pull/28))
- App backup is off, so the saved SMB login and photo database never leave the TV. Release builds no longer log network requests. ([#33](https://github.com/sumeet2025/tvframe/pull/33), fixes [#25](https://github.com/sumeet2025/tvframe/issues/25))
- Settings are no longer read with a blocking call on the main thread, which could make the app stutter. ([#32](https://github.com/sumeet2025/tvframe/pull/32), fixes [#24](https://github.com/sumeet2025/tvframe/issues/24))
- About now shows the correct minimum Android version (Android TV 11+).

### Fixed
- **The USB banner's Dismiss button** now actually dismisses it. ([#29](https://github.com/sumeet2025/tvframe/pull/29), fixes [#21](https://github.com/sumeet2025/tvframe/issues/21))
- **The SMB password** is masked instead of shown in clear text. ([#30](https://github.com/sumeet2025/tvframe/pull/30), fixes [#22](https://github.com/sumeet2025/tvframe/issues/22))
- **♥ and 🗑 on photo cards** now show a clear outline when the remote is on them, and buttons no longer have a hidden second focus stop. ([#31](https://github.com/sumeet2025/tvframe/pull/31), fixes [#23](https://github.com/sumeet2025/tvframe/issues/23))

### Upgrade notes
- It's signed with the same key, so it installs over 1.3.1, 1.3.0 or 1.2.0 without uninstalling. Photos, favorites and settings are kept.
- The database moves from v2 to v3 (a new license column). The upgrade is automatic and keeps everything. Photos downloaded before 1.4.0 show the photographer without a license name; new ones show both.
- Because backup is now off, TVFrame's data won't move to a new TV through Android's backup or transfer.

## [1.3.1] - 2026-11-12

A small follow-up to 1.3.0 that fixes what I ran into using it on my own TV, plus one screensaver option I wanted.

### Added
- **"Favorites (Random)" screensaver mode.** The Favorites tab now has a single-choice "Screensaver:" row with All Sources, Favorites Only and Favorites (Random). Random shuffles your favorites every time the screensaver starts and shows each one once before any repeats, then reshuffles. ([#18](https://github.com/sumeet2025/tvframe/pull/18), fixes [#14](https://github.com/sumeet2025/tvframe/issues/14))

### Changed
- **"Clear Non-Favorites" only clears now.** It no longer starts a sync, and an empty gallery right after a clear doesn't trigger one when the app opens or the screensaver starts. The status card says to press Sync for new photos. New photos arrive only when you press ⟳ Sync, or from the automatic sync roughly every 6 hours. ([#15](https://github.com/sumeet2025/tvframe/pull/15), fixes [#11](https://github.com/sumeet2025/tvframe/issues/11))
- **Favorites are no longer pinned to the top of the Gallery.** They stay in their normal place, newest first, marked with ♥ and a coral outline. The Favorites tab is where they're shown together. The slideshow and the "All Sources" screensaver follow the same order. ([#16](https://github.com/sumeet2025/tvframe/pull/16), fixes [#12](https://github.com/sumeet2025/tvframe/issues/12))

### Fixed
- **Tapping a photo in the Gallery** now opens the slideshow on that photo and continues from there, instead of starting from the first photo. ([#17](https://github.com/sumeet2025/tvframe/pull/17), fixes [#13](https://github.com/sumeet2025/tvframe/issues/13))

### Upgrade notes
- It's signed with the same key, so it installs over 1.3.0 or 1.2.0 without uninstalling. Photos, favorites and settings are kept. If you had "Favorites Only" on, it stays on.
- There's no database change in this release.

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

[1.3.1]: https://github.com/sumeet2025/tvframe/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/sumeet2025/tvframe/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/sumeet2025/tvframe/releases/tag/v1.2.0
