# TVFrame — Android TV Photo Gallery & Screensaver (`DreamService`)

I built TVFrame as a photo gallery and screensaver for **any Android TV or Google TV device running Android 11 (API 30) or newer**. It works on most smart TVs and streaming boxes — including Sony, TCL, Hisense, Philips, Chromecast with Google TV, NVIDIA Shield, and Google TV Streamer.

## Download

Get the latest APK from the [Releases](https://github.com/sumeet2025/tvframe/releases/latest) page:

- [Download TVFrame v1.2.0 (APK)](https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame-v1.2.0.apk)

Step-by-step install instructions (Downloader app, adb, or a USB stick), plus how to set TVFrame as your screensaver, are in [INSTALL.md](INSTALL.md). If you use the Downloader app on your TV, paste the APK link above directly.

## Key Features Included

1. **Multi-Source Photo Sourcing & Sync**
   - **Wikimedia Commons API**: Curated presets (*Pictures of the Day*, *Mountains*, *Rain & Storms*, *Birds*, *Lakes & Reflections*, *Landscapes*, *Night Sky & Aurora*, *Architecture*) plus custom free-text search.
   - **Concise TV Metadata + QR Lookup**: Displays **only** the Photographer/Artist name (`📷`) and Location (`📍`) on screen for Wikimedia photos; full encyclopedic details and licensing are accessed by scanning the dynamic ZXing QR code.
   - **External USB Mass Storage (Hot-Plug Safe)**: Detects removable USB drives on Android TV / Google TV (`StorageManager` + SAF `OpenDocumentTree` + `MANAGE_EXTERNAL_STORAGE` support) to expand photo storage and Coil disk caching. `UsbHotPlugReceiver` listens for sudden USB detachment and seamlessly falls back to internal storage without crashing.
   - **SMBv2 / SMBv3 Local Network Share (`jcifs-ng`)**: Connects to NAS or PC shared folders over Wi-Fi/Ethernet using either **Guest / Anonymous** mode or **Username / Password / Domain** credentials encrypted via `EncryptedSharedPreferences`.
   - **Smart LRU Cache & Favorites Engine**: Configurable storage quota (512 MB – 8 GB) with automatic Least-Recently-Used pruning that **never** deletes Favorited photos. Includes multi-select and delete-all controls in the Favorites tab.

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
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

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

If no file is found, `assembleRelease` still works but produces an **unsigned** APK. Keep the keystore backed up: every future update has to be signed with the same key, or users must uninstall first.

### Install to a TV from your computer

```bash
./install_to_tv.sh <TV-IP> [path/to/TVFrame.apk]   # or: ./install_to_tv.sh --build <TV-IP>
```

## Publishing to Google Play (notes)

The APK is fine to share. Here's what I still need to work on before a Play Store release:

- **`MANAGE_EXTERNAL_STORAGE` (All files access)**: Play only allows this permission for apps whose core purpose needs broad file access, and it requires a declaration form in Play Console. A photo viewer will very likely be refused. Plan to remove it and rely on the Storage Access Framework folder picker (`OpenDocumentTree`) plus `READ_MEDIA_IMAGES` for USB drives.
- **Target API level**: the app targets API 35. Play currently requires new apps and updates to target API 36, except Android TV apps, which need API 34 or higher. Because `android.software.leanback` is declared `required="false"`, Play can also offer the app to phones, where the API 36 rule applies. Either bump `targetSdk`/`compileSdk` to 36 or keep it a TV-only listing.
- **App Bundle and Play App Signing**: Play needs an `.aab` (`./gradlew bundleRelease`), not an APK. Enroll in Play App Signing and use the keystore above as the upload key.
- **Privacy policy and Data safety form**: required. The app makes network requests (Wikimedia Commons) and stores SMB credentials on the device.
- **TV app quality guidelines**: an app must pass Google's Android TV quality review before it is listed for TV. This covers full D-pad navigation, no touch-only UI, a 320x180 launcher banner, correct `LEANBACK_LAUNCHER` handling, and similar checks.
- **Store listing assets**: a 512x512 hi-res icon, a 1280x720 TV banner, and TV screenshots.
- **Donations**: the About & Zaps tab links to Lightning/Nostr tips. Check Play's payments policy for in-app donation links before publishing.


## Branding assets

`branding/` holds the source logo (`logo-1024.png`), the 512x512 Play icon, and the 1280x720 TV banner. Run `python3 branding/generate_assets.py` (needs Pillow) to regenerate the launcher mipmaps and `drawable-xhdpi/tv_banner.png` from the logo.
