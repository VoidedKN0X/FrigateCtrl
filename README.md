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
- **Alert notifications** with the snapshot, camera, objects and time; tap one to open that moment in History. A background check every 15 minutes or more, and optional instant push through your own ntfy server
- **Notification filters**: per camera and per object, alerts only or alerts and detections, quiet hours and a test notification
- **Server exports**: export a range from the History timeline (real time or 25× time-lapse) on your Frigate server, follow its progress, then play, save, share, rename or delete it from the Exports tab
- **Camera switches**: camera on/off, detection, recording, snapshots, audio detection and notifications
- **System health**: CPU, GPU, memory, detector speed, camera FPS and disk usage
- **App lock** with its own PIN and optional fingerprint unlock; your login stays encrypted while the app is locked
- **Hide in screenshots** and **Hide in recent apps** switches keep your cameras out of screenshots, screen recordings and the recent-apps preview when you turn them on
- **Self-signed certificates** are pinned: you confirm the fingerprint once, and only that certificate is accepted after that

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Frigate 0.14 or newer
- go2rtc set up in Frigate for live video (it is by default). Without it, you get refreshing snapshots instead.
- Microphone permission, only if you use two-way talk
- Notification permission, only if you turn on alert notifications; [ntfy](https://ntfy.sh) only for instant alerts
- An admin Frigate user to rename or delete exports
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

Settings, the server and login, log out and About are behind your profile picture at the top right of the Cameras and Health tabs: tap it to open the You page, then **Settings**.

## Tips and troubleshooting

- **No sound in live view:** the camera's audio needs to be AAC. Convert it in your go2rtc config, e.g. `- "ffmpeg:your_cam#video=copy#audio=aac"`.
- **Live view shows snapshots instead of video:** the note on the screen says why. `stream not found` usually means the go2rtc stream name doesn't match; map it with `live: streams:` in your Frigate config.
- **No PTZ buttons:** they only show for cameras with an `onvif:` section in your Frigate config.
- **Talk button missing:** it only shows when the camera has a speaker, and go2rtc's WebRTC port (8555) must be reachable from your phone.
- **Talk connects at home but not away from it:** talk only uses the STUN/TURN servers listed under `go2rtc: webrtc: ice_servers:` in your Frigate config; the app never contacts a public STUN server on its own. Add one there (your own, or a public one if you're happy for it to see your IP address).

## Notifications

Turn them on under **Settings > Notifications** (tap your profile picture, then **Settings**). FrigateCtrl asks for permission to show notifications then.

- **Background check:** FrigateCtrl asks your Frigate server for new alerts (and detections, if you choose) every 15, 30 or 60 minutes or less often. Android can delay it to save battery. Only alerts from the moment you turn notifications on are notified.
- **Each notification** shows the snapshot, the camera, what was seen (person, car, ...) and the time. Tapping it opens that camera's History a few seconds before the alert. On the lock screen only "New alert" shows.
- **Filters:** switch cameras and objects off, pick alerts only or alerts and detections, and set quiet hours (alerts arrive silently). Alerts and detections have their own channels in Android's settings for sound and importance.
- **With the app lock on**, alerts wait until you unlock FrigateCtrl, because your login is encrypted with your PIN. Turn on **Alerts while locked** on the same page to keep your login encrypted with the phone's key only, so alerts arrive while the app is locked.

### Instant alerts with ntfy (optional)

Frigate can't send to ntfy itself, so instant alerts need a small forwarder. Without it, everything works through the background check.

1. Install [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) and connect it to your ntfy server.
2. In **Settings > Notifications**, turn on **Instant alerts with ntfy** and copy the **push address**. Keep it private: anyone who knows it can wake the app up (they can't make it show anything).
3. Forward Frigate's `frigate/reviews` MQTT messages to that address, for example with a Home Assistant automation:

   ```yaml
   rest_command:
     frigatectrl_push:
       url: "https://ntfy.example.com/upXXXXXXXX?up=1"   # your push address
       method: POST
       payload: "{{ review_id }}"

   automation:
     - alias: FrigateCtrl push
       triggers:
         - trigger: mqtt
           topic: frigate/reviews
       conditions:
         - condition: template
           value_template: "{{ trigger.payload_json.type == 'new' }}"
       actions:
         - action: rest_command.frigatectrl_push
           data:
             review_id: "{{ trigger.payload_json.after.id }}"
   ```

   Any MQTT-to-HTTP bridge works too, e.g. `mosquitto_sub -t frigate/reviews | while read -r m; do curl -s -d "$m" "<push address>"; done`.

A push only wakes FrigateCtrl up: it then asks your Frigate server what's new, so the alert, snapshot and filters are always your server's. The body can be empty, a review id, or Frigate's whole review message.

## App lock

Turn it on under **Settings > Privacy and security > App lock** (tap your profile picture at the top right, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your login is encrypted with the PIN while FrigateCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Sharing a clip and opening a link from FrigateCtrl don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your login. The lock screen offers to log out and forget the server, the app lock and anything kept from the server, so you can log in again; your Frigate server isn't changed.
- **With the app lock on**, the recent-apps screen always shows a blank card, and nothing of your cameras is shown or streamed while the app is locked.
- **Picture-in-picture counts as leaving.** When the live view floats in its own window, the lock timer starts as if you had left the app, and expanding the window back to full screen shows the lock screen when the lock is due. With **Lock again** set to as soon as you leave, the floating window only shows a lock, and the live view doesn't float by itself when you go home. With **Hide in screenshots** on, it doesn't float by itself either; you can still float it from the camera screen.

**Hide in screenshots** and **Hide in recent apps** are in the same section, work without the app lock too, and are both off by default. The first keeps FrigateCtrl out of screenshots and screen recordings; the second shows a blank card in the recent-apps view instead of the app. Sign-in, the lock screen and the PIN screens are always kept out of screenshots, whatever the switches say.

## Privacy

**Telemetry-free.** FrigateCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the servers you add.

- **Servers it talks to:** your own Frigate server, and, for two-way talk, only the STUN/TURN servers listed in your go2rtc config. It never contacts a public STUN server on its own.
- **Push:** only through the ntfy app and your own ntfy server, and only if you turn it on. A push is a wake-up call; nothing in it is shown.
- **Two-way talk:** the built-in web view it runs in has online page checks and usage reports turned off.
- **Links:** the Support FrigateCtrl button and the Report a bug, Source code and Licence rows on the You page (tap your profile picture) open GitHub or gnu.org in your browser, only when you tap them.
- **Crash reports stay on your phone.** If FrigateCtrl stops because of an error, the error is saved on the phone only (no logins or tokens in it) and shows as **Last crash report** in Settings, to copy into a bug report or delete. Nothing is ever sent anywhere.
- Your login is stored encrypted on your phone. **Hide in screenshots** and **Hide in recent apps** (Settings > Privacy and security, both off by default) keep your cameras out of screenshots, screen recordings and the recent-apps preview.

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

FrigateCtrl is free and open source. If it's useful to you, tap **Support FrigateCtrl** at the bottom of the You page in the app (tap your profile picture at the top right) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** under **About** on the same page, or [open an issue](https://github.com/VoidedKN0X/FrigateCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Ctrl / Signal design, in dark and light.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| **FrigateCtrl** (this app) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

FrigateCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

### Fonts

FrigateCtrl bundles two fonts, both under the [SIL Open Font License 1.1](https://openfontlicense.org) (the licence texts are in `app/src/main/assets/licenses/`):

- [Space Grotesk](https://github.com/floriankarsten/space-grotesk) by Florian Karsten and the Space Grotesk Project Authors
- [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) by JetBrains and the JetBrains Mono Project Authors

FrigateCtrl is an unofficial app and is not affiliated with the Frigate project.
