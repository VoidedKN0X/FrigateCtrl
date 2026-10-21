# Changelog

What changed in each FrigateCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FrigateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 3.4.0 (2026-10-21)

Settings now open from your profile picture, the same in every Ctrl app.

### New

- **Your profile picture at the top right** of the Cameras and Health tabs. It shows the first letter of your Frigate username, or the round placeholder picture the other Ctrl apps use when your Frigate has no login (port 5000). Frigate has no profile pictures of its own. Tapping it opens the new **You** page
- **The You page** shows who you're logged in as, your Frigate server's address and Frigate version, then **Server & login** (change the address, login or certificate), **Settings** and **Log out**, and **About** (Support FrigateCtrl, Report a bug, Source code, Licence) with the version underneath, in the same layout as NaviCtrl, MemoCtrl, ProxCtrl and TrueCtrl
- **System health has the profile picture too**, so you can open the You page from either tab

### Changed

- **Settings moved to your profile picture**: before, Settings opened from the gear button at the top right of the Cameras screen. The gear is gone; tap your profile picture, then **Settings** for Appearance, Privacy and security, Cameras, Live view and History. It's the only way in, as in every Ctrl app
- **Server & login, Log out and About moved from Settings to the You page.** Log out still asks first. Cancelling or going back from Server & login now returns to the You page
- **README**: Getting started says where Settings, the server and login, log out and About are; the App lock, Privacy and Support sections say "tap your profile picture" instead of the gear button

## 3.3.4 (2026-10-21)

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in Settings › Privacy and security, both off by default, the same in every Ctrl app. Hide in screenshots makes screenshots and screen recordings show a blank screen; Hide in recent apps shows a blank card in the recent-apps view instead of the app (screenshots still work). Before, one setting hid your cameras from screenshots, recordings and the recent-apps preview and was on by default, and the recent-apps view always showed a blank card. **What changes for you:** if you had turned that setting on yourself, both new switches are on. If you never changed it, both are now off, so screenshots of your cameras work and the recent-apps view shows FrigateCtrl; turn the switches on if you want the old behaviour. With the app lock on, the recent-apps view always shows a blank card.
- **Always protected, whatever the switches say:** sign-in, the lock screen and the PIN screens are never in screenshots, screen recordings or casting.
- The app lock's text and the Privacy and security rows now come from the app's translatable text resources, with the same wording as the other Ctrl apps. Nothing else changes in how they work.
- **README**: the App lock section now uses the same wording as the other Ctrl apps and describes the two switches, and the feature list and Privacy section no longer say cameras are hidden by default.

## 3.3.3 (2026-10-21)

### Security

- **Sign-in, the lock screen and the PIN screens are never in screenshots, screen recordings or casting**, even with "Hide in screenshots and recent apps" turned off. This covers the server and login screen where you type your password, the lock screen, and choosing, changing or confirming your app PIN. A recording would otherwise show which keys of the PIN pad were pressed.

## 3.3.2 (2026-10-21)

The app lock and Settings now look and work the same as in every other Ctrl app.

### Changed

- **New lock screen look**, the same as in the other Ctrl apps: a round lock badge, PIN dots that show a spinner while your PIN is checked, and **Forgot PIN?** (it still asks first, then logs out and resets).
- **Turning the app lock on, changing the PIN and turning the lock off now use the same full-screen PIN pad** as the lock screen, instead of a dialog with text boxes. Choosing a PIN asks for it twice; changing it asks for your current PIN first; turning the lock off asks for your PIN.
- **Settings are grouped like in the other Ctrl apps**: **Appearance** first, then **Privacy and security** (formerly "Security"), Cameras, Live view, History, Server and **About** at the bottom. Rows have the same look as the other apps, and choices such as Theme, Lock again, Grid columns and Thumbnail refresh open a list to pick from.
- **Privacy and security** has the same rows, wording and icons as every Ctrl app: **App lock**, **Change app PIN**, **Unlock with fingerprint**, **Lock again** (now worded "As soon as you leave the app", "After 1 minute away" … "Only when the app restarts") and **Hide in screenshots and recent apps** (formerly "Hide content in screenshots & recents"). A note under the section says FrigateCtrl only talks to your own Frigate server.
- **Theme choices are the same as in the other Ctrl apps**: Follow system, Dark, Light or **Pure black**. Pure black is now one of the themes instead of a separate switch: if you had Dark with pure black on, you now get the Pure black theme; with "System" and pure black on, FrigateCtrl follows the system with the normal dark colours (pick Pure black again if you want it). "Material You colours" is now called **Use wallpaper colours**.
- **The fingerprint prompts** now say what they're for ("Confirm it's you to see your cameras" when unlocking, "Confirm your fingerprint" when turning fingerprint unlock on), and say so if fingerprint unlock couldn't be set up or didn't unlock.
- **README**: a new App lock section that explains the PIN, fingerprint unlock, the wrong-PIN wait, Lock again, hiding the app in screenshots and recent apps, and Forgot PIN. The Ctrl series table now lists AdCtrl and MemoCtrl.

## 3.3.1 (2026-10-21)

### Security

- **Trips out of the app no longer skip the lock if you stay away.** Sharing a clip and opening links still don't count as leaving the app, but if you don't come back within 5 minutes (or your "Lock again" time, if that's longer), FrigateCtrl locks anyway. Before, such a trip never locked, however long you were gone.

## 3.3.0 (2026-10-21)

The app lock now works the same way in every Ctrl app, with the strongest protection of the options we compared.

### Changed

- **Your PIN is upgraded the first time you unlock.** Nothing to do if your PIN already has 6 or more digits. If it's shorter (or as easy to guess as 1111 or 1234), FrigateCtrl asks you to choose a new PIN of 6 to 12 digits right after unlocking, before the app opens.
- **Fingerprint unlock has to be turned on again** after the upgrade (**Settings > Security > Unlock with fingerprint**), because it now unlocks the key that encrypts your login. It still only accepts strong biometrics such as the fingerprint sensor. Your PIN is now also needed after the phone restarts and at least every 72 hours, the same as Android does for your screen lock.
- **More "Lock again" choices**: instantly (still the default), after 1, 5 or 15 minutes, after 1 hour, or only when the app restarts. "30 s" is gone; if you had it, the lock now locks instantly.
- **Sharing a clip and opening links no longer count as leaving the app.** Coming back from the share sheet or the browser doesn't ask for your PIN again, even with "Instantly". Going to the home screen or another app yourself still does. Picture-in-picture also still counts as being in the app.
- **New PINs need 6 to 12 digits**, and very obvious ones (one repeated digit, or a run like 123456) are refused.
- **"Forgot PIN? Log out" now asks first**, and explains what's removed.
- **Logging out turns the app lock off**, together with removing the server and login.

### Security

- **Your login is encrypted with your PIN while FrigateCtrl is locked.** The saved password and session token are encrypted with a key that only exists in memory while the app is unlocked. It's wiped the moment the app locks, and live streams, talk and open connections to your server are closed. A locked app, or a copy of its files, can't use your login.
- **The PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 (5 times more than before) and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying FrigateCtrl's files to a computer isn't enough to try PINs there.
- **Wrong PINs still lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait. A wait that was running when you updated still counts.
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock a key in the phone's secure hardware, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again.

## 3.2.13 (2026-10-20)

### Changed

- **64-bit ARM phones only.** FrigateCtrl now ships only the 64-bit ARM (arm64-v8a) build that nearly every phone from the last several years uses, like every Ctrl app, so the download is smaller. Old 32-bit phones and x86 devices (emulators, some Chromebooks) are no longer supported.
- **README**: Requirements say 64-bit ARM phones only

## 3.2.12 (2026-10-20)

FrigateCtrl is now Google-free and de-Googled. Nothing changes in how you use it.

### Privacy

- **No mention of Google left in the app's own code.** FrigateCtrl has no Google Play services, Firebase or other Google services and never contacts Google: it only talks to your Frigate server and the STUN/TURN servers in your go2rtc config. The last mentions of Google in the app's code and setup file (notes about the emoji font, the built-in web view and the old public STUN server) are gone. It works the same on de-Googled phones such as GrapheneOS, CalyxOS, /e/OS and LineageOS without Google apps.

### Changed

- **README**: the Google-free line now reads "Google-free and de-Googled": no Google Play services, Firebase or other Google services, the app never contacts Google, and it works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps). It's the only place Google is mentioned in the README, and the Privacy section no longer repeats it.
- **README**: the note that two-way talk's built-in web view has online page checks and usage reports turned off moved from Requirements to the Privacy section.

## 3.2.11 (2026-10-20)

FrigateCtrl is now telemetry-free.

### Privacy

- **No more usage stats: FrigateCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting and the one-time question. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. FrigateCtrl has no analytics, crash reporting or tracking of any kind: it only talks to your own Frigate server (and, for two-way talk, the STUN/TURN servers from your go2rtc config).

### Changed

- **README**: a new Privacy section and a "Telemetry-free" badge replace the old Usage stats section
- **README**: a Screenshots section (screenshots are coming soon), and a link preview image (`.github/assets/social-preview.png`) with FrigateCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere

## 3.2.10 (2026-10-19)

### Changed

- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of Settings (the gear button at the top of the Cameras screen). Every Ctrl app now has the same four rows, in the same order, with the same wording and icons.
  - **Support FrigateCtrl** opens VKNXWorks' GitHub Sponsors page in your browser.
  - **Report a bug** (new) opens FrigateCtrl's GitHub issues page, where you can report a problem or suggest an idea.
  - **Source code** (new) opens FrigateCtrl's GitHub repository.
  - **Licence** (new) opens the GNU General Public License v3.0, the licence FrigateCtrl is released under.
- The version is no longer a row of its own under About: it now sits in a small line below the section, reading "FrigateCtrl 3.2.10 · VKNXWorks".

## 3.2.9 (2026-10-19)

### New

- **Support FrigateCtrl** under Settings → About opens VKNXWorks' GitHub Sponsors page in your browser. FrigateCtrl is free and open source, and this is the place to sponsor its development if you'd like to. Settings opens from the gear button at the top of the Cameras screen; the new row is the first one under About, above the version.
- The README's **Support the project** section now also points to the **Support FrigateCtrl** button.

## 3.2.8 (2026-10-19)

FrigateCtrl is now completely Google-free. Nothing changes in how you use it.

### Privacy

- **Emoji no longer make FrigateCtrl ask Google for a font.** A standard Android library inside the app asked Google Play services for a downloadable emoji font every time FrigateCtrl started, which could make Google Play services fetch that font from Google. This lookup is now switched off: emoji are drawn with your phone's own emoji font. On phones without Google Play services nothing changes; on other phones, emoji can look slightly different if the phone's built-in emoji font is older.
- **The built-in web view no longer reports to Google.** Two-way talk runs in Android's built-in web view, which by default checks pages with Google Safe Browsing and may send usage reports to Google. Both are now turned off for FrigateCtrl. It works exactly as before.
- **Checked: FrigateCtrl is Google-free.** It contains no Google Play services, Firebase, ML Kit, Google Cast or Play Core, and it doesn't load fonts or anything else from Google's servers. It also never switches server addresses by itself: it always uses the address you set up, and only you can change it.

### Changed

- **README: new Google-free line** under Requirements: works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font, and the web view used for two-way talk has Google Safe Browsing and usage reports turned off.
- **README brought up to date with 3.2.7.** The feature list now also mentions camera-group filters, **mark all** and the **Unreviewed** filter in the events list, the camera on/off and audio detection switches, memory and camera FPS on the Health tab, hiding cameras from screenshots and the recent-apps preview (on by default, separate from the app lock), and certificate pinning for self-signed certificates. It no longer says screenshots are only blocked while the app is locked. **Getting started** now explains the **Trust** step for a self-signed certificate and the warning for plain `http://` addresses, and **Requirements** notes the microphone permission is only needed for two-way talk. Nothing in the app has changed.

## 3.2.7 (2026-10-19)

Fourth security and privacy review, focused on what the app sends and to whom.

### Security

- **Two-way talk no longer contacts Google.** When your go2rtc config doesn't list any STUN/TURN servers, talk used to fall back to Google's public STUN server (`stun.l.google.com`), which let Google see your phone's IP address every time you used talk. Now talk only uses the servers in your own go2rtc config (`go2rtc: webrtc: ice_servers:`), and none otherwise. At home, and with go2rtc's WebRTC port (8555) reachable, talk works as before. If talk stops connecting away from home, add a STUN server to that list; the README explains how
- **Switching usage stats off takes effect straight away.** Turning off **Settings > Usage stats** now also throws away any stats still waiting to be sent and stops one that's being sent at that moment, and the next time you switch them on starts a new random session. Before, a stat that was already on its way could still go out

### Changed

- The README's **Usage stats** section now says that, apart from those opt-in stats, FrigateCtrl only talks to your own Frigate server (and, for talk, the STUN/TURN servers from your go2rtc config)

## 3.2.6 (2026-10-19)

### Changed

- **New app icon, matching the rest of the Ctrl series.** The icon is now the same dark keycap as the other Ctrl apps, with a red video camera with a lens and a record light on its screen. The themed (monochrome) icon on Android 13+ home screens uses the same design.
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates.

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
