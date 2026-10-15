# Frigate Remote

Native Android client for a self-hosted [Frigate NVR](https://frigate.video).

- **First-run setup** – Frigate URL + username/password (or no login for auth-disabled port 5000), optional self-signed certificate trust. Password and session token are encrypted with the Android Keystore.
- **Live view** – low-latency go2rtc MSE stream (same `/live/mse` endpoint as the web UI) with audio toggle and stream picker; falls back to fast snapshots if MSE can't start.
- **History** – plays recordings via Frigate's VOD HLS endpoint.
- **Precision timeline** – drag to scrub, pinch to zoom from ~20 s to ~2 days per screen, fling for long jumps, tap to jump. Shows recording coverage, motion, detections and alerts. Haptic ticks on each labelled mark, frame previews while scrubbing, ±0.1 s / 1 s / 10 s / 30 s steps, previous/next event, 0.5×–16× speed, date picker and an events list.
- **PTZ / ONVIF** – hold-to-move pan/tilt, zoom, focus and presets for cameras with `onvif` configured (sent over Frigate's websocket like the web UI).

## Build

GitHub Actions builds `app-release.apk` on every push to `main` (Actions → latest run → *FrigateRemote-apk*).

Optional release signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with the debug key.
