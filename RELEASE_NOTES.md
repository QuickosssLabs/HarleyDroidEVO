# HarleyDroid EVO 3.1 — Release notes

## Summary

Open-source Harley-Davidson J1850 / CAN analyser for Android (ELM327 / HDI).
Evolution of [stelian42/HarleyDroid](https://github.com/stelian42/HarleyDroid)
with a modern Material UI, dual-bus support, and refreshed gauges.

**APK:** `HarleyDroidEVO-v3.1-EVO-debug.apk` (sideload; debug signing).

### 3.1
Ships the full Material UI / gauges / simulation / Wi‑Fi ELM work that was
missing from the initial `v3.0-EVO` artifact.

## Highlights

### UI & gauges
- Material 3 dark theme, branding, status pills, section cards
- Modern circular gauges (LCD readouts, logo mark, RPM color bands)
- Vertical **FUEL** / **TEMP** mini bar gauges + gear chip + economy strip
- Harley-style cluster self-test on connect (needle sweep + lamps)
- Text dashboard with metric rows; FR / EN; selectable accent themes

### Platform & protocol
- Android 5.0+ (minSdk 21), target / compile SDK 35, JDK 17
- Simulation mode (fake J1850 or CAN, no bike / adapter)
- **Bluetooth or WiFi TCP** ELM327 (port 35000); unified connection prefs
- Dual bus: J1850 (4-pin, full diagnostics) and CAN / HDLAN (6-pin, passive V1)
- Deferred BT / GPS permissions, log share via FileProvider, live notification

## Screenshots

See [README.md § Screenshots](README.md#screenshots) (`docs/screenshots/`).

## Licence

GPL-3.0 — see `COPYING`.  
Original work © Stelian Pop; EVO maintenance © Quickosss.
