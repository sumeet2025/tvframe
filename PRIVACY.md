# TVFrame Privacy Policy

_Effective date: November 19, 2026_

I built TVFrame, a photo gallery and screensaver for Android TV, as a hobby project. This page explains what the app does with data. The short version: **TVFrame has no accounts, no ads, no analytics and no tracking, and I don't collect any data about you.**

This policy covers both the Google Play version and the APK I publish on GitHub.

## What TVFrame stores on your TV

Everything TVFrame keeps stays on your TV:

- **Settings** you choose (categories, slideshow and overlay options, screensaver mode).
- **Photos and photo details** it has downloaded or found (image cache, photographer, license, location text, which photos you've marked as favorites, and a list of photos it has already shown so it doesn't download them again).
- **Network share (SMB) login**, if you set one up: the address, share name, username and password. The username and password are stored encrypted with the Android Keystore.

None of this is sent to me. App backup is turned off, so it isn't copied to cloud backups or to a new device either.

## What TVFrame sends over the internet

- **Wikimedia Commons.** To find and download photos, TVFrame sends requests to Wikimedia Commons (`commons.wikimedia.org` and `upload.wikimedia.org`) over HTTPS. A request includes the categories you picked and any Custom Search text you type, plus a User-Agent that names the app and its version. Like any website, Wikimedia sees your TV's IP address. Wikimedia's handling of that is covered by the [Wikimedia Foundation Privacy Policy](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy). TVFrame also refreshes photos in the background about every 6 hours, using the same kind of request.
- **Your own network share.** If you turn on the SMB source, TVFrame connects to the computer or NAS you entered and sends your SMB login only to it. This stays on your home network unless you entered an address outside it.
- **USB drives** are read directly on the TV. Nothing from them is uploaded anywhere.

TVFrame doesn't include any analytics, advertising, crash-reporting or tracking libraries, and it doesn't send data to me or to anyone else.

## Permissions

- **Internet and network state:** to download photos and check whether you're online.
- **Wake lock and start at boot:** used by Android's WorkManager for the background photo refresh.
- **All files access (GitHub version only, optional):** lets TVFrame read photos from a USB drive on TVs that hide the system folder picker. You have to turn it on yourself in Android settings. The Google Play version doesn't ask for it.

## Deleting your data

Uninstall TVFrame, or go to **Settings → Apps → TVFrame → Clear data** on your TV. That removes everything listed above. Inside the app you can also clear photos and reset the "already shown" history at any time.

## Children

TVFrame isn't directed at children under 13 and doesn't knowingly collect any personal information from anyone.

## Changes

If this policy changes, I'll update this page and the effective date above. The full history is visible in this repository.

## Contact

Questions or feedback: message me on Nostr ([primal.net/s](https://primal.net/s)) or open an issue at [github.com/sumeet2025/tvframe/issues](https://github.com/sumeet2025/tvframe/issues).
