# Installing TVFrame on your TV

TVFrame is a photo gallery and screensaver for Android TV and Google TV.
It isn't on the Play Store yet, so you install it from an APK file (`TVFrame.apk`).

## What you need

- A TV or streaming box running **Android TV or Google TV with Android 11 or newer**.
  This includes most smart TVs from Sony, TCL, Hisense, Philips and others, plus Chromecast with Google TV, NVIDIA Shield and Google TV Streamer.
  To check your version, go to **Settings → System → About → Android TV OS version** (menu names vary a little by brand).
- The APK file. The [latest release](https://github.com/sumeet2025/tvframe/releases/latest) is always available at this direct link:
  `https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame.apk`
  Each release also has a copy with the version in its name (for example `TVFrame-v1.3.1.apk`). Both files are identical.

Pick **one** of the three methods below.

---

## Method A: Install on the TV itself (easiest, no computer needed)

### Option 1: "Downloader by AFTVnews"

1. On the TV, open the **Google Play Store**, search for **Downloader** (by AFTVnews) and install it.
2. Allow Downloader to install apps:
   **Settings → Apps → Security & restrictions → Unknown sources** (or **Install unknown apps**) → turn on **Downloader**.
   On some TVs this is under **Settings → Device Preferences → Security & restrictions**.
3. Open Downloader and type in the APK link. This always gets the latest version:
   `https://github.com/sumeet2025/tvframe/releases/latest/download/TVFrame.apk`
4. When the download finishes, choose **Install**, then **Open** or **Done**.

### Option 2: "Send Files to TV"

1. Install **Send Files to TV** from the Play Store on **both** your TV and your phone. Both devices need to be on the same Wi-Fi.
2. Allow it to install apps: **Settings → Apps → Security & restrictions → Unknown sources** → turn on **Send Files to TV**.
3. On the phone, open the app, tap **Send**, pick the APK file and choose your TV.
4. On the TV, open the received file (you may need a file manager such as **File Commander** or **X-plore**) and choose **Install**.

---

## Method B: Install from a computer (adb)

Works on Mac, Windows and Linux.

1. **Turn on Developer options on the TV**: go to **Settings → System → About** (or **Device Preferences → About**), scroll to **Build** and press OK on it **7 times**. You'll see "You are now a developer".
2. **Turn on debugging**: go to **Settings → System → Developer options** (or **Device Preferences → Developer options**) and turn on **USB debugging**. Also turn on **Network debugging** / **ADB debugging** if your TV has it.
3. **Find the TV's IP address**: **Settings → Network & Internet →** your network (for example `192.168.1.50`).
4. **Install adb on the computer**:
   - Mac: `brew install --cask android-platform-tools`
   - Windows/Linux: download "SDK Platform-Tools" from https://developer.android.com/tools/releases/platform-tools
5. In a terminal on the computer, run:
   ```bash
   adb connect <TV-IP>:5555
   adb install -r TVFrame.apk
   ```
   The first time, the TV shows **"Allow USB debugging?"**. Tick **Always allow from this computer** and choose **OK**, then run the install command again if it failed.

> **Google TV Streamer / newer Google TV:** some newer devices only offer **Wireless debugging** with a pairing code instead of port 5555. In Developer options open **Wireless debugging → Pair device with pairing code**, then run `adb pair <IP>:<pairing-port>` and enter the code. After that, `adb connect <IP>:<port shown on screen>`.

---

## Method C: USB stick + a file manager

1. Copy the APK onto a USB stick from your computer.
2. Plug the stick into the TV.
3. Install a file manager from the Play Store, such as **File Commander** or **X-plore File Manager**.
4. Allow the file manager to install apps: **Settings → Apps → Security & restrictions → Unknown sources** → turn on your file manager.
5. Open the file manager, browse to the USB stick, select the APK and choose **Install**.

---

## Opening TVFrame

Find **TVFrame** in your TV's apps row or in **Apps → See all apps**. On Google TV you can also go to **Settings → Apps → See all apps**.

## Using TVFrame as your screensaver

1. Open TVFrame, go to the **📺 TV Setup** tab and choose **Open Screensaver Settings**.
   Or go there manually. The menu name depends on your brand:
   - Most Android TVs: **Settings → Device Preferences → Screen saver**
   - Sony: **Settings → System → Ambient mode** or **Screen saver**
   - Google TV: **Settings → System → Ambient mode**
2. Choose **TVFrame Photo Screensaver**.
3. Set **When to start** (for example 10 minutes).

**If TVFrame isn't in the list:** some Google TV devices only show Google's own Ambient mode. You can still set TVFrame from a computer with adb (see Method B):

```bash
adb shell settings put secure screensaver_components com.nostr.tvframe/.service.PhotoDreamService
adb shell settings put secure screensaver_enabled 1
```

To try it right away: `adb shell cmd dreams start-dreaming`

**What the screensaver shows:** pick it in TVFrame's **♥ Favorites** tab under "Screensaver:": **All Sources**, **Favorites Only**, or **Favorites (Random)**, which shuffles your favorites each time the screensaver starts.

**Remote controls during the screensaver:** Left/Right change photo, Center/Up adds to Favorites, Down shows or hides photo info, and **Back** wakes the TV.

## Updating to a new version

Install the new APK the same way you installed the first one: open the same link in Downloader, or run `adb install -r TVFrame.apk`. It installs over the old version and keeps your photos, favorites and settings. What's new in each version is in the [CHANGELOG](https://github.com/sumeet2025/tvframe/blob/main/CHANGELOG.md).

## Uninstalling

- **On the TV**: **Settings → Apps → See all apps → TVFrame → Uninstall**.
- **With adb**: `adb uninstall com.nostr.tvframe`

If you set TVFrame as the screensaver, pick another screensaver afterwards. Most TVs switch back to the default automatically.

## Troubleshooting

| Problem | What to do |
|---|---|
| **TVFrame isn't in the launcher** | Look in **Apps → See all apps** or **Settings → Apps**. You can add it to your favorites row from there. |
| **"App not installed"** | An older TVFrame signed with a different key is probably installed already. Uninstall it first (see above), then install again. |
| **"Install blocked" / no Install button** | Turn on **Unknown sources / Install unknown apps** for the app you're installing from (Downloader, file manager, etc.). |
| **"There was a problem parsing the package"** | The download is incomplete or your TV runs Android 10 or older. Download the file again and check your Android version. |
| **`adb connect` fails** | Check that the TV and computer are on the same network, that Developer options → debugging is on, and the IP address is correct. Some TVs need a restart after you turn on debugging. |
| **USB photos don't show up** | In TVFrame open **💾 USB & SMB NAS** and pick the USB folder. If the TV has no folder picker, grant **All files access** when TVFrame asks. |
