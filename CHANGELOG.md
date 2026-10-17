# Changelog

What changed in each FrigateCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FrigateCtrl/releases) also lists the exact changes in that build.

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
