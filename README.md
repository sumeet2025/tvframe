# TVFrame — Android TV Photo Gallery & Screensaver (`DreamService`)

I built TVFrame as a photo gallery and screensaver for **any Android TV or Google TV device running Android 11 (API 30) or newer**. It works on most smart TVs and streaming boxes — including Sony, TCL, Hisense, Philips, Chromecast with Google TV, NVIDIA Shield, and Google TV Streamer.

## Download

Get the latest APK from the [Releases](https://github.com/sumeet2025/tvframe/releases/latest) page:

- [Download the latest TVFrame APK](https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame.apk) (currently v1.4.1)

This link always points to the newest release. What changed in each version is in the [CHANGELOG](CHANGELOG.md).

Step-by-step install instructions (Downloader app, adb, or a USB stick), plus how to set TVFrame as your screensaver, are in [INSTALL.md](INSTALL.md). If you use the Downloader app on your TV, paste the APK link above directly.

## Want USB photos and open search? Get the GitHub version

When TVFrame is on Google Play, the Play version won't include a few things Play doesn't allow: **All Files Access for USB drives** (needed on TVs that hide the folder picker) and the **Lightning zap** in About. Its Custom Search is also family-safe: it only searches curated photos and filters out adult content. The APK I publish here has all of them.

Easiest way to install it with a USB stick:

1. On a computer, download the latest APK: [TVFrame.apk](https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame.apk)
   (`https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame.apk`).
2. Copy `TVFrame.apk` onto a USB stick and plug the stick into the TV.
3. On the TV, install a file manager from the Play Store, such as **File Commander** or **X-plore File Manager**.
4. Allow that file manager to install apps: **Settings → Apps → Security & restrictions → Install unknown apps** (on some TVs **Unknown sources**) → turn it on for your file manager.
5. Open the file manager, go to the USB stick, select `TVFrame.apk` and choose **Install**.

**Already installed TVFrame from Google Play?** You don't need to uninstall it. Both versions have the same package name and are signed with the same key, so the GitHub APK installs right over the Play version and keeps your photos, favorites and settings. That works as long as the GitHub version number is the same as or higher than the one you have (you can see yours in TVFrame's About tab). Android won't install an older version over a newer one.

One thing to know: if Google Play later installs a TVFrame update on top, you'll be back on the Play version (your data stays, but USB All Files Access goes away and Custom Search becomes family-safe until you install the GitHub APK again).

Other install methods (Downloader app, adb) are in [INSTALL.md](INSTALL.md).

## Key Features Included

1. **Multi-Source Photo Sourcing & Sync**
   - **Wikimedia Commons API**: Curated presets (*Pictures of the Day*, *Mountains*, *Rain & Storms*, *Birds*, *Lakes & Reflections*, *Landscapes*, *Night Sky & Space*, *Architecture*) plus custom free-text search. Pick one or more categories and a sync mixes them.
   - **Clear, predictable Sync**: a status card shows what's happening ("Downloading 12 of 30"), the result, and the last and next sync times. You choose how many photos each sync brings (10–50) and the minimum resolution (720p / 1080p / 2K / 4K). TVFrame remembers what it has downloaded, so every sync brings **new** photos, even after "Clear Non-Favorites". Clearing never starts a download by itself; new photos come only from ⟳ Sync or the automatic sync about every 6 hours. "Reset seen history" starts over.
   - **Concise TV Metadata + QR Lookup**: Displays **only** the Photographer/Artist name (`📷`) and Location (`📍`) on screen for Wikimedia photos; full encyclopedic details and licensing are accessed by scanning the dynamic ZXing QR code.
   - **External USB Mass Storage (Hot-Plug Safe)**: Detects removable USB drives on Android TV / Google TV (`StorageManager` + SAF `OpenDocumentTree` + `MANAGE_EXTERNAL_STORAGE` support) to expand photo storage and Coil disk caching. `UsbHotPlugReceiver` listens for sudden USB detachment and seamlessly falls back to internal storage without crashing.
   - **SMBv2 / SMBv3 Local Network Share (`jcifs-ng`)**: Connects to NAS or PC shared folders over Wi-Fi/Ethernet using either **Guest / Anonymous** mode or **Username / Password / Domain** credentials encrypted via `EncryptedSharedPreferences`.
   - **Smart LRU Cache & Favorites Engine**: Configurable storage quota (512 MB – 8 GB) with automatic Least-Recently-Used pruning (only when over quota) that **never** deletes Favorited photos. Includes multi-select and delete-all controls in the Favorites tab, plus the screensaver mode: **All Sources**, **Favorites Only**, or **Favorites (Random)** (shuffled, no repeats until every favorite has been shown).

2. **Interactive `DreamService` Screensaver & D-Pad Controls**
   - Runs automatically when the TV goes idle (`PhotoDreamService`) or on-demand from the main app.
   - **D-Pad Left / Right**: Skip to previous/next photo.
   - **D-Pad Center / Up**: Toggle Favorite (`♥`).
   - **D-Pad Down / Info**: Toggle metadata & QR overlay visibility.
   - **Wake Prompt**: Pressing any D-Pad control displays an on-screen HUD reminder: `"Click Back to wake up TV"`. Pressing **Back** exits the screensaver and wakes the TV immediately.
   - **Auto-Hidden QR on Local Files**: QR codes automatically hide whenever a photo comes from local USB storage or an SMB network share.
   - **Ken Burns + Ambient Blur + OLED Protection**: Subtle slow pan/zoom motion, GPU-blurred pillarbox backdrop for non-16:9 photos, and automatic ±8px periodic pixel shifting to prevent OLED/LED static burn-in.

3. **About Me & Nostr Zaps**
   - Built-in **About & Zaps** tab showcasing Nostr profile on Primal (`primal.net/s` — `https://primal.net/s`), full NPUB (`npub19dfd7w8zxfdfywqmqxpt92wq6tx4p0mvfqv6yuyg0cv9d7duj4uskuccks`), and Lightning Address (`s@primal.net`) with dual scannable ZXing QR codes.

## Building

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). The Gradle wrapper is included.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleGithubDebug      # app/build/outputs/apk/github/debug/app-github-debug.apk
./gradlew assembleGithubRelease    # app/build/outputs/apk/github/release/app-github-release.apk
./gradlew bundlePlayRelease        # app/build/outputs/bundle/playRelease/app-play-release.aab
```

There are two build flavors with the same package name and signing key, so either one installs over the other:

| | `github` (APK on GitHub releases) | `play` (Google Play) |
|---|---|---|
| All Files Access for USB drives | Yes, optional | No, USB folders are picked with the system folder picker |
| Lightning zap in About | Yes | No, only the Nostr follow |
| Custom Search on Wikimedia Commons | Yes, open search | Yes, family-safe: Featured/Quality/Valued images only, with category and word blocklists |
| Devices | Android TV / Google TV | Android TV / Google TV |

### Release signing

The release build is signed only if a `keystore.properties` file is found. It is never committed. Lookup order:

1. Environment variable `TVFRAME_KEYSTORE_PROPERTIES=/path/to/keystore.properties`
2. `tvframe.keystoreProperties=/path/to/keystore.properties` in `local.properties`
3. `keystore.properties` in the repo root (git-ignored)

```properties
storeFile=/absolute/path/to/tvframe-release.jks
storePassword=...
keyAlias=tvframe
keyPassword=...
```

If no file is found, the release build still works but produces an **unsigned** APK. Keep the keystore backed up: every future update has to be signed with the same key, or users must uninstall first.

### Install to a TV from your computer

```bash
./install_to_tv.sh <TV-IP> [path/to/TVFrame.apk]   # or: ./install_to_tv.sh --build <TV-IP>
```

## Google Play

I'm preparing a Google Play version for Android TV. It's built from the `play` flavor (see the table in [Building](#building)): TV-only, without All Files Access or the Lightning zap, and with family-safe Custom Search, so it fits Play's policies. Everything else is the same app, with the same package name and signing key.

## Privacy

TVFrame has no accounts, ads, analytics or tracking, and I don't collect any data. The full policy is in [PRIVACY.md](PRIVACY.md).

## License

TVFrame is open source under the [MIT License](LICENSE). Photos from Wikimedia Commons belong to their authors and are shown under their own licenses, which TVFrame displays next to the photographer's name.

## Branding assets

`branding/` holds the source logo (`logo-1024.png`), the 512x512 Play icon, and the 1280x720 TV banner. Run `python3 branding/generate_assets.py` (needs Pillow) to regenerate the launcher mipmaps and `drawable-xhdpi/tv_banner.png` from the logo.
