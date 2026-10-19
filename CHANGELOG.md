# Changelog

What changed in each FrigateCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FrigateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Not in a release yet; this goes out with the next version.

### Changed

- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates. No change to the app itself.

## 3.2.5 (2026-10-19)

### New

- **Optional anonymous usage stats.** After you connect to your Frigate server, FrigateCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Settings > Usage stats**
- **What's shared:** which screens you open (for example "Cameras", "Live", "History" or "Settings", never which camera), that you opened live view, scrubbed the history timeline, used PTZ controls or played an event (and whether from the events list or the skip buttons), the app version, Android version, phone model, language, phone or tablet, and whether you prefer the sub stream
- **What's never shared:** your server address, username, passwords, camera names, zones, what was detected (people, cars and so on), event IDs, recording times or footage, PTZ commands or presets, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Builds you make from source can't send stats at all, and the setting doesn't appear in them

### Changed

- **PrintCtrl in the Ctrl series list.** The README's table of Ctrl apps now includes [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl), the app for Bambu Lab printers through a self-hosted Bambuddy server, and Spoolman spools.
- The README has a new **Usage stats** section listing exactly what's collected

## 3.2.4 (2026-10-18)

Third security review: checks that the previous version's protections can't be worked around.

### Security

- **Restarting the phone no longer shortens the wrong-PIN wait**: after a restart the full wait now counts again from when the phone started, instead of trusting the phone's date and time (which could be set forward to end the wait early). A wait that had already run out is forgotten, so a later restart doesn't bring it back
- **Fingerprint unlock can't be set up behind your back**: unlocking with a fingerprint only works with the key made when you turned fingerprint unlock on in Settings. If that key is missing (for example after updating from 3.2.2 or older) or fingerprints were added since, FrigateCtrl asks for your PIN and you turn fingerprint unlock on again; it never quietly makes a new key that would accept a newly added fingerprint
- **Wrong PINs are saved instantly**: each wrong PIN is written to storage before the app reacts, so closing the app straight after a wrong guess can't make it forget the attempt
- **The lockout applies everywhere at once**: a wait started under **Change PIN** or **Turn off app lock** now also blocks the lock screen straight away
- **Saved password only reused for the exact same server**: leaving the password empty in **Server settings** keeps the saved one only if the address uses the same http/https and port as before, so switching to plain http or to another port on the same machine needs the password typed again

## 3.2.3 (2026-10-18)

Second security review.

### Security

- **Fingerprint unlock is tied to a real fingerprint match**: unlocking now uses a key in the phone's secure hardware that only works right after a strong fingerprint match, so a faked "success" can't open the app. If you add a new fingerprint to the phone, FrigateCtrl asks for your PIN once and you turn fingerprint unlock on again in Settings
- **Changing the phone's clock no longer skips the wrong-PIN wait**: the lockout after too many wrong PINs is timed with a clock that can't be changed, and setting the date back can't stretch it either
- **Guessing the PIN in Settings counts too**: a wrong current PIN under **Change PIN** or **Turn off app lock** counts toward the same lockout as the lock screen
- **Nothing is copied to a new phone**: the server address, encrypted login, trusted certificate, PIN and settings are excluded from cloud backups and from phone-to-phone transfers
- **Saved password stays hidden**: **Server settings** no longer fills in your saved password (where the eye button could show it). Leave the field empty to keep it; it's only reused for the same server and username
- **Recorded clips don't linger**: temporary clips for sharing are deleted after an hour (also on the next start), straight away if saving or downloading fails, and all of them on logout. A failed save no longer leaves a broken video in your gallery, and camera names can't steer a clip's file name outside its folder
- Logging out also clears camera thumbnails kept in memory
- Screenshots and recents are blocked from the very first frame when **Hide content in screenshots & recents** is on

## 3.2.2 (2026-10-18)

- Version numbers are now plain, with no build number on the end, and a new version only comes out when something in the app actually changes. Nothing else in the app has changed.

## 3.2.1 (2026-10-16)

Security hardening.

- Setup no longer quietly falls back from HTTPS to HTTP. HTTP is only used after you confirm it, and the server is marked "Not encrypted (HTTP)"
- "Allow self-signed certificates" is replaced by certificate pinning: you confirm the server's certificate fingerprint once, and only that certificate is accepted after that
- Existing installs using the old setting are asked to confirm once before anything is sent
- Your login cookie is never sent to another host, even through a redirect
- **Hide content in screenshots & recents** works without a PIN (on by default)
- Smaller APK

## 3.2.0 (2026-10-16)

- **Floating glass tab bar**: the bottom tabs are now a frosted-glass pill with a springy selection, and content scrolls underneath

## 3.1.1 (2026-10-16)

- **New icon**: a classic camera with a violet lens, with a proper themed (monochrome) version

## 3.1.0 (2026-10-15)

- **Health tab** next to Cameras (moved out of Settings)

## 3.0.0 (2026-10-15)

- **Mark events reviewed** (swipe, menu or mark all), with an unreviewed filter and badges
- **Save or share clips** of events or any range you select on the timeline
- **Picture-in-picture** live view
- **Pinch and double-tap zoom** on live and recorded video
- **Per-camera switches**: camera, detection, recording, snapshots, audio detection and notifications
- **System health**: detectors, CPU/GPU, storage and camera FPS
- **Two-way talk** for cameras that support it

## 2.5.0 (2026-10-15)

- **App lock** with PIN and fingerprint, auto-lock after a delay, and hiding the app from Recents
- Live and history playback pause when the app goes to the background

## 2.4.0 (2026-10-15)

- New package name `com.vknxworks.frigatectrl`. Android treats this as a new app: uninstall the old one and set it up again

## 2.3.0 (2026-10-15)

- Requires Android 13 or newer

## 2.2.0 (2026-10-15)

- **Frigate Remote is now FrigateCtrl**. It updates over the existing install

## 2.1.1 (2026-10-15)

First release.

- Camera grid with pull to refresh, camera-group filters and 24-hour alert badges
- **Live view** with native video, sound, fullscreen, and PTZ controls: presets, a joystick and zoom in a bottom dock
- **History** with a precise scrubbing timeline and event playback
- Material You colours, light/dark/system theme and a pure-black option
- Settings for grid columns, thumbnail refresh, sub stream, start muted, keep screen on, timeline span, event pre-roll and haptic ticks
