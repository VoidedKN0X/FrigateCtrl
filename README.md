# FrigateCtrl

An unofficial Android app for watching and reviewing your [Frigate NVR](https://frigate.video) cameras from your phone, built with Kotlin and Jetpack Compose on Frigate's HTTP API and go2rtc.

## Features

**Cameras**
- Camera grid with refreshing snapshots, a red badge with each camera's alerts from the last 24 hours, and a PTZ badge on cameras with ONVIF
- Camera-group filter chips, taken from the `camera_groups` in your Frigate config
- Pull down to refresh; labels switch between light and dark text depending on the picture behind them

**Live view**
- Low-latency video (about a second behind) from go2rtc, played natively, with H.264 and H.265 support
- Audio, including cameras that send G.711 (PCMA/PCMU) audio
- Stream picker for cameras with more than one live stream (e.g. main and sub)
- Fullscreen, or just turn the phone sideways
- Shows a snapshot straight away while the video connects, and falls back to fast snapshots, with the reason shown, if video can't play

**PTZ (ONVIF)**
- Pan/tilt joystick: drag or press toward a direction to move, let go to stop
- Hold-to-zoom and hold-to-focus buttons
- One-tap presets
- Uses the same commands as the Frigate web UI, sent through Frigate, so the app never talks to the camera directly

**History**
- Plays recordings for any time, with gaps between recordings handled correctly
- Precision timeline: drag to scrub, pinch to zoom from about 20 seconds to 2 days across the screen, fling for long jumps, tap to jump
- The timeline shows recording coverage, motion, detections and alerts, with a light vibration as each time mark passes
- Frame previews while scrubbing, and steps of ±0.1 s, 1 s, 10 s and 30 s
- Previous/next event, 0.5× to 16× speed, a date picker, and an events list with thumbnails

**App**
- Material 3 design with Material You colours on Android 12+, light/dark/system theme and an optional pure-black mode
- Settings for grid columns, thumbnail refresh, preferring the sub stream, starting muted, keeping the screen on, the timeline's starting zoom, how early events start, and timeline vibration

## Requirements

- Frigate **0.14 or newer**. Tested against the 0.16–0.18 API.
- **go2rtc** set up in Frigate for live video and audio (it is by default with restreaming). Without it the app shows refreshing snapshots instead.
- Android **8.0** or newer.
- Your Frigate address can be:
  - `https://…:8971`, the authenticated port (recommended)
  - `http://…:5000`, the port without authentication
  - a reverse-proxy URL

## Install

Download the latest `FrigateCtrl-vX.Y.Z-build.N.apk` from the [Releases](../../releases) page and open it on your phone. Android may ask you to allow installing apps from your browser or file manager.

Updates install over the existing app. Every build is signed with the same release key, so you never need to uninstall or log in again.

## First run

1. Enter your Frigate address, e.g. `https://192.168.1.20:8971` or `https://frigate.example.com`. If you leave out `https://` or `http://`, the app tries HTTPS first, then HTTP.
2. Enter your Frigate **username and password**. Leave the username empty if your Frigate runs without authentication (port 5000).
3. If Frigate uses its default self-signed certificate, turn on **Allow self-signed certificate**. Only do this for a server on your own network.
4. Tap **Connect**. The app logs in and checks it can read your Frigate config before saving anything.

Your password and session token are encrypted with the Android Keystore. The app renews the session automatically when it expires.

## Tips & troubleshooting

- **No sound in live view:** go2rtc can only send AAC, or G.711 converted to FLAC, over MSE. If your camera uses another audio codec, transcode it in your go2rtc config, for example `- "ffmpeg:your_cam#video=copy#audio=aac"`. History always has sound if your recordings do.
- **"Snapshot mode" on live view:** the note on the video says why the video failed:
  - `login rejected`: log in again.
  - `stream not found`: the go2rtc stream name doesn't match the camera. Map it with `live: streams:` in your Frigate config.
  - `no frames received`: check the camera works in Frigate's own Live view.
- **Live view uses the wrong stream:** the app plays the streams listed under `live: streams:` for each camera. Pick one with the stream button, or turn on *Prefer sub stream* in Settings.
- **PTZ controls missing:** they only appear for cameras with an `onvif:` section in the Frigate config, and only show the features the camera reports (pan/tilt, zoom, focus, presets).

## Building from source

Open the project in Android Studio (Ladybug or newer) and run it, or build from a terminal:

```bash
./gradlew assembleDebug
```

Signed releases are built automatically by GitHub Actions; see `.github/workflows/build.yml` for the signing secrets it expects.

## Frigate API endpoints used

| Feature | Endpoint |
| --- | --- |
| Login | `POST /api/login` (JWT in the `frigate_token` cookie) |
| Config, cameras, groups, streams | `GET /api/config` |
| Version | `GET /api/version` |
| Snapshots | `GET /api/{camera}/latest.jpg` |
| Live video and audio | go2rtc MSE websocket at `/live/mse/api/ws?src={stream}` |
| Recordings and timeline | `GET /api/{camera}/recordings?after=&before=` |
| Playback | `GET /vod/{camera}/start/{start}/end/{end}/index.m3u8` (HLS) |
| Scrub previews | `GET /api/{camera}/recordings/{time}/snapshot.jpg` |
| Alerts and detections | `GET /api/review?cameras=&after=&before=` |
| Event thumbnails | `GET /clips/review/thumb-{camera}-{id}.webp` |
| PTZ info | `GET /api/{camera}/ptz/info` |
| PTZ control | websocket `/ws`, topic `{camera}/ptz` (`MOVE_*`, `ZOOM_*`, `FOCUS_*`, `STOP`, `preset_{name}`) |

The app only reads from Frigate and sends PTZ commands; it never changes your Frigate configuration.
