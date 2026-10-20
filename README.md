# FrigateCtrl

**Watch and review your Frigate cameras from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/FrigateCtrl?label=download)](https://github.com/VoidedKN0X/FrigateCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

FrigateCtrl is an Android app for [Frigate NVR](https://frigate.video). Watch your cameras live, scrub through recordings on a precise timeline, and control PTZ cameras.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FrigateCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Camera grid** with live snapshots, camera-group filters and a badge for new alerts
- **Live view** about one second behind, with sound, zoom and picture-in-picture
- **Two-way talk** on cameras with a speaker
- **PTZ controls**: joystick, zoom, focus and presets
- **History** with a precise timeline you can pinch to zoom, showing recordings, motion and alerts
- **Events list**: swipe to mark reviewed, mark all, filter by unreviewed or alerts
- **Save or share clips** of any event or time range
- **Camera switches**: camera on/off, detection, recording, snapshots, audio detection and notifications
- **System health**: CPU, GPU, memory, detector speed, camera FPS and disk usage
- **App lock** with PIN and fingerprint
- **Private by default**: cameras are hidden from screenshots, screen recordings and the recent-apps preview (**Hide content in screenshots & recents**, on by default)
- **Self-signed certificates** are pinned: you confirm the fingerprint once, and only that certificate is accepted after that

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Frigate 0.14 or newer
- go2rtc set up in Frigate for live video (it is by default). Without it, you get refreshing snapshots instead.
- Microphone permission, only if you use two-way talk
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/FrigateCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds FrigateCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FrigateCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/FrigateCtrl` in Obtainium by hand.

## Getting started

1. Enter your Frigate address, e.g. `https://192.168.1.20:8971` or `https://frigate.example.com`.
2. Enter your Frigate username and password. Leave the username empty if your Frigate has no login (port 5000).
3. If Frigate uses its built-in self-signed certificate, turn on **Allow self-signed certificate**.
4. Tap **Connect**. With a self-signed certificate, check that the SHA-256 fingerprint shown matches your server's and tap **Trust**. With a plain `http://` address, the app warns you first; tap **Connect anyway** only on your own network.

## Tips and troubleshooting

- **No sound in live view:** the camera's audio needs to be AAC. Convert it in your go2rtc config, e.g. `- "ffmpeg:your_cam#video=copy#audio=aac"`.
- **Live view shows snapshots instead of video:** the note on the screen says why. `stream not found` usually means the go2rtc stream name doesn't match; map it with `live: streams:` in your Frigate config.
- **No PTZ buttons:** they only show for cameras with an `onvif:` section in your Frigate config.
- **Talk button missing:** it only shows when the camera has a speaker, and go2rtc's WebRTC port (8555) must be reachable from your phone.
- **Talk connects at home but not away from it:** talk only uses the STUN/TURN servers listed under `go2rtc: webrtc: ice_servers:` in your Frigate config; the app never contacts a public STUN server on its own. Add one there (your own, or a public one if you're happy for it to see your IP address).

## Privacy

**Telemetry-free.** FrigateCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the servers you add.

- **Servers it talks to:** your own Frigate server, and, for two-way talk, only the STUN/TURN servers listed in your go2rtc config. It never contacts a public STUN server on its own.
- **Two-way talk:** the built-in web view it runs in has online page checks and usage reports turned off.
- **Links:** the Support, Report a bug, Source code and Licence rows in Settings open GitHub or gnu.org in your browser, only when you tap them.
- Cameras are hidden from screenshots, screen recordings and the recent-apps preview by default, and your login is stored encrypted on your phone.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/FrigateCtrl.git
   cd FrigateCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

FrigateCtrl is free and open source. If it's useful to you, tap **Support FrigateCtrl** under **Settings → About** in the app (the gear button on the Cameras screen) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** in the same section, or [open an issue](https://github.com/VoidedKN0X/FrigateCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| **FrigateCtrl** (this app) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

FrigateCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

FrigateCtrl is an unofficial app and is not affiliated with the Frigate project.
