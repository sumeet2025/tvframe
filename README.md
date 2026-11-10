# TVFrame — Android TV Photo Gallery & Screensaver (`DreamService`)

Native Kotlin & Jetpack Compose for TV application built for **Android TV 12 / API 31+** and modern Android TV / Google TV devices.

## Key Features Included

1. **Multi-Source Photo Sourcing & Sync**
   - **Wikimedia Commons API**: Curated presets (*Pictures of the Day*, *Mountains*, *Rain & Storms*, *Birds*, *Lakes & Reflections*, *Landscapes*, *Night Sky & Aurora*, *Architecture*) plus custom free-text search.
   - **Concise TV Metadata + QR Lookup**: Displays **only** the Photographer/Artist name (`📷`) and Location (`📍`) on screen for Wikimedia photos; full encyclopedic details and licensing are accessed by scanning the dynamic ZXing QR code.
   - **External USB Mass Storage (Hot-Plug Safe)**: Detects removable USB drives on Android TV 12 (`StorageManager` + SAF `OpenDocumentTree` + `MANAGE_EXTERNAL_STORAGE` support) to expand photo storage and Coil disk caching. `UsbHotPlugReceiver` listens for sudden USB detachment and seamlessly falls back to internal storage without crashing.
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
   - Built-in **About & Zaps** tab showcasing your Nostr profile on Primal (`primal.net/s` — `https://primal.net/s`), your full NPUB (`npub19dfd7w8zxfdfywqmqxpt92wq6tx4p0mvfqv6yuyg0cv9d7duj4uskuccks`), and your Lightning Address (`s@primal.net`) with dual scannable ZXing QR codes.
