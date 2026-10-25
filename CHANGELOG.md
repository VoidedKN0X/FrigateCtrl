# Changelog

What changed in each FrigateCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FrigateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 3.7.2 (2026-10-25)

A cleanup and privacy pass after 3.7.1. Nothing changes in how FrigateCtrl looks or works.

### Changed

- **Less background traffic.** Camera pictures that refresh by themselves (camera tiles and the live view's snapshot fallback), System health, the Exports list and the History timeline stop asking your Frigate server for updates while FrigateCtrl is in the background, and pick up again as soon as you come back. Picture-in-picture still counts as on screen, so the floating live view keeps updating.

### Security

- **Fewer ways in for other apps.** Two debugging hooks that came with Android libraries (WorkManager's diagnostics receiver and the profile installer's receiver) could be sent broadcasts by other apps. FrigateCtrl doesn't use them, so they're removed from the app.

### Fixed

- **Old theme settings removed.** The theme choices from before 3.6.0 (light, dark, pure black and wallpaper colours) were still kept in FrigateCtrl's settings although nothing used them. They're deleted from your phone the first time 3.7.2 starts.
- **Internal cleanup:** removed unused code and text left over from the old theme picker, and System health's refresh stops cleanly when you leave it.

## 3.7.1 (2026-10-25)

Fixes for FrigateCtrl stopping with "keeps stopping" when you close or leave the app, and an on-device crash report to help find anything that's left.

### Fixed

- **Closing or leaving the app no longer crashes it.** With the app lock on, leaving FrigateCtrl (Home, Back, swiping it away, or the live view floating as picture-in-picture) locks it and closes its connections to your Frigate server. Closing an encrypted (https) connection was done on the main thread, which Android can refuse with an error that stopped the app. It's now done in the background.
- **No crash when the app is closed while it locks.** When FrigateCtrl is closed (Back on the first screen, or swiped away from recent apps) at the moment it locks, it no longer tries to draw the lock screen in a window that's going away. It still opens locked next time.
- **Fingerprint prompt.** If Android refuses to show the fingerprint prompt when you turn fingerprint unlock on (for example while the app is being left), FrigateCtrl says it couldn't set it up instead of stopping.
- **Background check and notifications can't stop the app.** If Android or WorkManager refuses to schedule the background check, a check fails in an unexpected way, Android rejects a notification, or a notification channel can't be made, FrigateCtrl carries on (the next check tries again) instead of stopping. A test notification that can't be shown just doesn't appear.
- **Pushes from ntfy and notification taps can't stop the app.** A broken broadcast from ntfy is ignored, and a broken intent that opens FrigateCtrl just opens it as usual.
- **Leaving a screen while it's busy.** Saving or sharing a clip or export, starting a server export, and the Exports list refresh stop cleanly when you leave the screen or the app locks.

### New

- **Last crash report (on this phone only).** If FrigateCtrl ever stops because of an error, the error is saved on your phone with the time, the FrigateCtrl and Android version and your phone model. **Settings > Troubleshooting > Last crash report** then shows it, with **Copy** (copied as sensitive, so it isn't previewed) and **Delete**. The section only appears when there's a report. Server addresses lose any login and query string, and tokens and passwords are masked. Nothing is ever sent anywhere, and logging out deletes it.

## 3.7.0 (2026-10-25)

Alert notifications with the snapshot, and Frigate's server-side exports in the app. FrigateCtrl had no notifications or background work before; both are off until you turn them on.

### New

- **Alert notifications.** Turn them on under **Settings > Notifications** (behind your profile picture). FrigateCtrl asks Android for permission to show notifications at that moment, and only then. Each new alert gets its own notification with the snapshot of what was seen as a large picture, the objects (person, car, dog, ...), the camera and the time. Tapping it opens FrigateCtrl at that camera's History a few seconds before the alert started (your event pre-roll), and removes the notification. With the app lock on, you unlock first and History opens right after. Several alerts are stacked together in the notification shade.
- **Background check.** FrigateCtrl asks your Frigate server for new review items every 15 minutes, or every 30 minutes, 1, 2 or 6 hours, only with a network connection. Android can delay it to save battery. Only alerts from the moment you turn notifications on are notified, at most eight per check (newest first), and a check never looks back more than six hours, so turning the phone on after a night off doesn't bring a flood.
- **Instant alerts with ntfy (optional).** Turn on **Instant alerts with ntfy** and FrigateCtrl registers with the ntfy app and shows its push address, with a copy button. Frigate can't send to ntfy itself, so a Home Assistant automation or an MQTT-to-ntfy bridge forwards Frigate's `frigate/reviews` messages to that address (the README has a short example). A push only wakes FrigateCtrl up: it then asks your Frigate server what's new, right away. A push can carry a review id or Frigate's whole review message, and that review item is fetched even if it isn't in the list yet. Pushes and the background check share one list of what was already notified, so an alert never notifies twice. The page warns when ntfy isn't installed (with a link to get it), when ntfy's server can't be reached, when registration failed (tap to try again), and when ntfy may be stopped in the background by battery saving.
- **What to notify about.** **Alerts only** (the default) or **Alerts and detections**. A detection that later becomes an alert notifies once more, as an alert.
- **Per-camera and per-object filters.** Every camera and every object label Frigate has seen has its own switch. Cameras added later notify until you switch them off. An alert notifies if at least one of its objects is switched on.
- **Quiet hours.** Pick a range (for example 23:00 to 07:00, past midnight works); alerts in it still arrive, but without sound or vibration.
- **Notification channels per severity.** Alerts (high importance) and detections (normal importance) are separate channels, so you can give them their own sound in Android's settings; **Sound and importance** opens them.
- **Test notification.** Sends a notification that looks like a real alert, with the latest picture from one of your cameras.
- **Alerts while locked** (only shown with the app lock on). Off by default, which is the most secure: your login stays encrypted with your app PIN and the background check waits until you unlock, then catches up right away. On: your login is encrypted with the phone's key only, so alerts arrive while FrigateCtrl is locked. Turning the app lock off and on again starts from the secure setting.
- **Exports tab.** A new tab between Cameras and System health lists the exports on your Frigate server, newest first, with their thumbnail, name, camera and time, and filters by camera. Exports Frigate is still making show "Being made" with a spinner and turn ready by themselves (the list refreshes every 3 seconds while one is in progress, otherwise every 30 seconds).
- **Export a range on the server.** In a camera's History, mark a range with the scissors as for a clip and tap the new cloud button, or pick **Export on server** in an event's menu. Give it a name if you like and choose real time or a 25× **time-lapse**; Frigate makes the MP4 on the server, where it also shows on Frigate's own Exports page. Errors from Frigate (such as no recordings in that range) are shown as Frigate words them.
- **Play, save, share, rename and delete exports.** Tap an export to play it full screen with seek controls. Its menu saves it to Movies/FrigateCtrl, shares it through the share sheet (only the temporary copy in FrigateCtrl's own cache is shared, and it's deleted within an hour), renames it, or deletes it from the server after you confirm. Renaming works with Frigate 0.14 and 0.15 (name in the address) and 0.16 (name in the request). Renaming and deleting need an admin Frigate user; FrigateCtrl says so if yours isn't.

### Security

- **Pushes can't make FrigateCtrl show anything.** Every broadcast from ntfy must carry the secret token of FrigateCtrl's registration (and, on Android 14 and newer, a sender that says it's another app is refused). Nothing in a push is shown: at most a review id is taken from it, and only letters, digits, dots, dashes and underscores are passed on to your server.
- **Notification taps can't be faked.** FrigateCtrl's main screen can be opened by other apps (it's the launcher), so it only opens a camera's History from an intent that carries a random secret kept in FrigateCtrl's own storage. The camera name is checked too.
- **On the lock screen**, notifications only say "New alert" or "New detection": no camera, objects or picture.
- **Logging out** turns notifications off, removes the ntfy registration, stops the background check and clears every FrigateCtrl notification.

### Changed

- **Settings** has a new **Notifications** section at the top, showing whether alert notifications are on.
- **README**: Notifications section with the optional ntfy setup, and the new features and requirements.

## 3.6.0 (2026-10-25)

Light mode that follows the device theme: FrigateCtrl is now light when your phone is set to light and dark when it's set to dark, switching by itself as soon as you change it (including on a schedule). There's no setting for it in the app, and wallpaper colours (Material You) still aren't used.

### New

- **Signal Light.** A light version of the Ctrl / Signal look: a warm off-white background (#F5F5F1), white cards with thin light-grey borders, near-black text and grey secondary text. Everything that was dark in 3.5.0 has a light counterpart, on every screen: Cameras, System health, the camera screen around the video, History around the video, the events list, PTZ controls, the camera settings sheet, the You page, Settings, sign-in and server settings, the lock screen and every PIN screen, dialogs, sheets, the date picker and menus.
- **Follows the phone, nothing to set.** The app uses your phone's dark theme setting. The status and navigation bars follow too: the same colour as the app's background, with dark icons in light mode and light icons in dark mode. The window opens in the right colour from the first frame, so there's no dark flash when the app starts in light mode.

### Changed

- **Accent in light mode.** The violet fill (#B48CFF) is the same in both modes and stays on hero cards, primary buttons such as Connect and Support FrigateCtrl, the selected tab in the bottom bar, switches that are on, filled PIN squares, the play button and the selected chips, always with dark text and icons on it. Violet text and icons on the light background (the slash in FRIGATE/CTRL, "LIVE" and other status words, status dots, selected options, links such as "Mark all", the focused text field border and cursor, the outlined fingerprint key, loading spinners) use a deeper violet (#6A3BDB) so they stay readable on white. In dark mode they keep the lighter violet.
- **Light-mode details.** Switches that are off are light grey with a grey knob. Progress meters in System health are near-black on a light track (amber from 75 %, red from 90 %, as before). Errors and "Log out" are a deeper red (#D93636). Empty PIN squares have a light-grey outline, and the keypad keys are white with a thin border. The floating bottom bar is white and slightly see-through, with a light border. The timeline colours for motion, detections and recordings are a little darker in light mode so they stand out on the light background.

### Unchanged

- **Video stays dark in both modes.** The live picture, recordings, the scrub preview, picture-in-picture and full-screen or landscape viewing keep their black background, and the chips and buttons drawn over the video (LIVE, PLAYBACK, zoom, Talking, mute, picture-in-picture, full screen, clip, date, events) stay dark with light text so they read on any picture. Camera tiles on the Cameras tab also keep their dark fade behind the camera name, so the name and the LIVE label read the same over any picture.

## 3.5.0 (2026-10-25)

FrigateCtrl gets the new Ctrl / Signal look that every Ctrl app is moving to: near-black, one violet accent, Space Grotesk for text and JetBrains Mono for numbers and labels. Only the look changes. Every screen, switch, gesture and setting works as before.

### Changed

- **New dark-only theme.** Near-black background (#08080A), dark grey cards (#141417) with thin borders, light text and a single violet accent (#B48CFF) for everything that's on, live or selected. The status and navigation bars use the same background, edge to edge, with light icons.
- **New fonts, built in.** All text is in Space Grotesk; times, counts, addresses, readouts and section labels are in JetBrains Mono. Both fonts are included in the app (nothing is downloaded, no Google font service), under the SIL Open Font License 1.1. The licence texts are in the app and credited in the README.
- **New top bar on Cameras and System health.** The FRIGATE/CTRL wordmark (with a violet slash) on the left, your profile picture on the right as a rounded square with your initial and a small violet badge while you're connected. Tapping it opens the You page as before. Under it, a small mono label ("NVR · ONLINE", "FRIGATE · LIVE") and the big tab title.
- **Alerts hero card on Cameras.** A violet card at the top shows the number of new alerts from the last 24 hours in big type, with the number of cameras beside it. These are the same counts that were on the line under the title, which is still there.
- **Camera tiles.** Rounded 18 dp tiles with a thin border. Each camera's name is now an upper-case mono label at the bottom with a square live dot (violet when the camera is on, grey when it's off) and a violet "LIVE". Alert and PTZ badges are small rounded squares. The label always sits on a dark fade now, instead of switching to dark text over bright pictures.
- **Group filters, event filters and PTZ presets** are rounded-rectangle chips, violet when selected.
- **Floating bottom bar.** The frosted-glass pill is replaced by a solid floating bar with a thin border. The selected tab is a violet block with its icon and name, the other tab is an icon only. Lists leave room for it at the bottom.
- **System health.** A violet hero card shows detection fps in big type with the Frigate version, uptime and the update notice. Camera fps and skipped fps sit in two cards below it. Every section (System, Detectors, Storage, Cameras) is a bordered card with a mono label. Meters are thin 6 dp bars (white, amber at 75 %, red at 90 %), and detector and camera rows end in a mono value and a square status dot.
- **Live view.** The camera screen has a square back button, the camera name, and a violet mono "LIVE" line under it. The stream picker and camera settings are square buttons. The video has 18 dp corners and a border. The LIVE, PLAYBACK, zoom and "Talking" chips are mono labels with square dots, and the buttons over the video are dark rounded squares. The PTZ joystick, zoom and focus controls, presets, History button (with the alert count in a violet badge) and Talk button sit in one bordered card at the bottom.
- **Camera settings sheet** (detection, recording, snapshots and the rest) uses the new rows and switches, with each switch's icon turning violet while it's on.
- **History.** Square buttons at the top for clips, date and events (with the number of unreviewed events on the events button). The time readout is in larger mono type with the date as a mono label, and the timeline's hour labels are mono too. Readout, timeline and playback controls sit in one bordered card at the bottom. The play button is a violet rounded square, the speed button is violet, and the step buttons are dark rounded rectangles in mono. The legend uses square dots and mono labels.
- **Events list.** Plain rows with thin dividers: the thumbnail, the labels, the time and length in mono, and an "ALERT" or "DETECTION" tag with a square dot (grey once reviewed). Swiping right to mark reviewed shows a violet strip.
- **New lock screen.** A violet rounded-square lock badge, "VKNXWORKS · CTRL", and "Locked" in big type. The PIN is shown as small squares (violet when filled), and the keypad has three columns of rounded keys in mono. Fingerprint unlock is now a violet-outlined key at the bottom left of the keypad (when fingerprint unlock is on and allowed), and delete is an icon at the bottom right. While the PIN can be confirmed by hand, the bottom-left key becomes a violet confirm key. Choosing, changing and turning off the PIN use the same pad. How the lock works hasn't changed.
- **New You page.** A close button with a "YOU" label, a violet rounded-square profile picture with your username and, in mono, your server address and Frigate version. Then the Server, App (Settings and Log out) and About sections as bordered cards with mono labels and thin dividers. **Support FrigateCtrl** is now a violet button at the bottom, followed by the version and "GOOGLE-FREE · TELEMETRY-FREE · ARM64".
- **Settings.** A square back button and title instead of the large collapsing bar. Sections have mono labels and sit in bordered cards with thin dividers between rows. The current choice of a setting (grid columns, refresh, lock timing and so on) is shown in mono on the right, and the choice sheet lists options as plain rows.
- **New switches everywhere.** Rounded-rectangle switches (violet with a dark knob when on, grey when off) in Settings, the camera settings sheet and sign-in. Screen readers still announce them as switches.
- **Sign-in and server settings.** The wordmark, a violet camera badge, a mono label and a big title. The fields have rounded borders that turn violet while you type, the self-signed certificate switch uses the new switch, and Connect is a large violet button. Errors show in a red-tinted box.
- **Dialogs and sheets** (log out, forgot PIN, certificate and http warnings, the date picker, the events and choice sheets) use the dark card colour with 26 dp corners and a thin border. Certificate fingerprints are shown in mono.
- **Picture-in-picture while locked** shows a violet lock on the app's background instead of a white lock on black.

### Removed

- **Theme and wallpaper colours.** The Appearance section in Settings (Follow system / Dark / Light / Pure black, and Use wallpaper colours) is gone, because FrigateCtrl is dark only now, like every Ctrl app. The light theme and Material You wallpaper colours aren't used any more, whatever you had chosen before.
- **The frosted-glass blur** behind the bottom bar, and the library that drew it (Haze). The app is a little smaller as a result.

## 3.4.4 (2026-10-21)

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left FrigateCtrl with the back gesture or by swiping home, the lock screen no longer asked for your fingerprint when you came back, and tapping **Use fingerprint** could do nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while FrigateCtrl was still going to the background or coming back on screen, the phone dropped it without answering, and the lock screen never asked again for that lock. Now the prompt only opens once FrigateCtrl is fully back on screen, a prompt that never answered can't block the next one (tapping **Use fingerprint** always opens a fresh one), and the lock screen offers your fingerprint again every time you come back to it, including when you expand the floating live view. The same applies to the prompt for turning on fingerprint unlock in **Settings**.

## 3.4.3 (2026-10-21)

### Security

- **Picture-in-picture no longer gets around the app lock.** A floating live view only pauses the app, so FrigateCtrl never noticed you had left: the lock timer didn't start, and expanding the floating window back to full screen opened the app without asking for your PIN, however long you had been away. Floating the live view (by going home or with the picture-in-picture button) now counts as leaving the app, and expanding it counts as coming back, with the usual lock check: the lock screen shows when the lock is due. With **Lock again** set to as soon as you leave, FrigateCtrl locks the moment the view floats and the floating window only shows a lock.
- **The live view no longer floats by itself when that would show it unprotected**: automatic picture-in-picture is now off while **Hide in screenshots** is on, and while the app lock is set to lock as soon as you leave. The picture-in-picture button on the camera screen still works.
- **The login is always encrypted with the app lock's PIN after unlocking.** If FrigateCtrl stopped in the split second between setting up the app lock and encrypting your saved login, the login stayed encrypted with the phone's key only. The next unlock now finishes the job.

### Privacy

- **No Google dependency metadata in the APK.** Android's build tools used to add an encrypted list of the app's libraries to the APK's signature block that only Google can read, which F-Droid flags. It's switched off now, as in the other Ctrl apps.

### Changed

- **README**: the App lock section explains how picture-in-picture and the lock work together.

## 3.4.2 (2026-10-21)

### Changed

- **Cameras and System health use the series tab title**: the big title at the top of both tabs now looks like every Ctrl app (large title on the left, your profile picture on the right) instead of the small coloured icon tile with a bold title. Nothing it showed is gone: on Cameras, your server's address, the number of cameras and the number of new alerts are on the line under the title, with the "Not encrypted (HTTP)" label under that when it applies; on System health, the live status ("Live · refreshes every 5 s", "Offline · showing last data" or "Loading…") is under the title.
- **Whole sentences instead of joined pieces, ready for translations**: event times with their length, recording storage with its rate per hour and the " · " between details are now each one piece of text. The wording stays the same.

## 3.4.1 (2026-10-21)

### Changed

- **All text is now in one place, ready for translations.** About 200 pieces of text that were written straight into the code (camera and history screens, System health, the camera settings sheet, PTZ controls, sign-in, certificate and connection warnings, Settings rows, clip and talk messages and error messages) now come from the app's text resources. The wording stays the same.

### Fixed

- **Counts read correctly in the singular**: the Cameras header now says "1 cam" and "1 new alert" instead of "1 cams" and "1 new alerts".

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
