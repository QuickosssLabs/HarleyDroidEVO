# HarleyDroid EVO 3.2.2 — Release notes

## Summary

Open-source Harley-Davidson J1850 / CAN analyser for Android (ELM327 / HDI).
Evolution of [stelian42/HarleyDroid](https://github.com/stelian42/HarleyDroid)
with a modern Material UI, dual-bus support, log charts, and module-based DTCs.

**APK:** `HarleyDroidEVO-v3.2.2-EVO-debug.apk` (sideload; debug signing).

### 3.2.2
- **Test liaison ELM** (menu ⋮) : ATRV / ATDP / ATMA + verdict (lien, tension, trafic bus)
- Poller ATMA plus tolérant : ignore le bruit ELM (`OK`, `SEARCHING`, echo…)
- Relance automatique d’`ATMA` sur `BUFFER FULL` / `STOPPED` ou silence temporaire
- Réduit les coupures « Pas de données » / « Trop d’erreurs » sur clones Wi‑Fi

### 3.2.1
- Fix crash in **CAN 6-pin simulation** (fuel average ÷0 with absolute odometer)
- Economy uses CAN trip distance, not full bike odometer
- AGP 9 cleanup: built-in Kotlin, drop deprecated gradle.properties opt-outs
- `LogChartView` / live buffer hardening

### 3.2
- In-app log browser + RPM/speed (and more) replay charts
- Live sliding-window graph (~60 s) while connected
- Diagnostics by module (ECM / ABS / TSM) with selective clear
- Expanded DTC dictionary (incl. B-codes); unknown codes show a clear fallback

### 3.1
Ships the full Material UI / gauges / simulation / Wi‑Fi ELM work that was
missing from the initial `v3.0-EVO` artifact.

## Highlights

### ELM link diagnostics (3.2.2)
- Menu → **Test liaison ELM** / **ELM link test**
- Standalone TCP/Bluetooth probe without the full capture service
- Shareable AT log for support

### Logging & charts
- **View logs** — list `.log.gz`, share, delete, open replay
- Replay chart: up to 2 metrics (RPM, SPD, ETP, GER, ODO, FUL, FGE) + scrub
- **Live graph** — same chart fed by the live bus (60 s window)

### Diagnostics (J1850)
- DTCs grouped by **ECM / ABS / TSM** (node `60` no longer dropped)
- Clear dialog: choose which modules to erase, then re-scan
- Tap a code for description (EN/FR dictionary)

### UI & gauges (since 3.1)
- Material 3 dark theme, branding, status pills, section cards
- Modern circular gauges + FUEL / TEMP bars + cluster self-test
- Simulation mode; FR / EN; selectable accent themes

### Platform & protocol
- Android 5.0+ (minSdk 21), target / compile SDK 35, JDK 17
- Bluetooth or WiFi TCP ELM327; dual bus J1850 + passive CAN V1
- **Active DTC / VIN remain J1850-only** until community CAN request/response
  captures are validated (see README § CAN)

## Screenshots

See [README.md § Screenshots](README.md#screenshots) (`docs/screenshots/`).

## Licence

GPL-3.0 — see `COPYING`.  
Original work © Stelian Pop; EVO maintenance © Quickosss.
