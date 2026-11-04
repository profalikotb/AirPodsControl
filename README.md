# Prof Ali Kotb — AirPods Control

Native Android (Kotlin + Jetpack Compose) app for Apple AirPods on Samsung Galaxy phones:
**battery dashboard** (left / right / case, charging, live vs last-known), **home-screen widget**, **low-battery & connection notifications**, and a **truthful controls screen** that shows what each AirPods gesture does on Android and measures it on your phone.

Offline · no account · no root · Arabic/English (RTL/LTR) · light/dark.

> Technical limits are documented in [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md). In short: battery comes from Apple's BLE broadcast in 10 % steps; gestures and ANC cannot be reassigned/switched by any non-root Android app, so the app shows locked labels — never fake switches.

## Install on a Samsung phone

1. On GitHub → **Actions** → latest *Android build* run → download artifact **AirPodsControl-apk** (zip) on the phone, or from **Releases** for tagged versions.
2. Open `ProfAliKotb-AirPodsControl.apk`. If blocked: *Settings → Security and privacy → Install unknown apps* → allow your browser / My Files.
3. If Play Protect warns ("unknown developer"): *More details → Install anyway* (self-signed build).
4. Pair the AirPods first in *Settings → Connections → Bluetooth* (case open, hold the back button until the light flashes white).
5. Open the app → allow **Nearby devices** → pick your AirPods → optionally allow notifications.
6. Recommended: *Settings → Apps → AirPods Control → Battery → Unrestricted* (prevents One UI from putting the app to sleep, which would freeze widget updates while the screen is off).
7. Widget: long-press the Home Screen → *Widgets* → *AirPods Control*.

Auto Blocker (One UI 6+) must be off to sideload.

## Build

```bash
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # minified, signed with debug key
```
JDK 17, Android SDK 35. CI: `.github/workflows/android.yml` runs tests and uploads both APKs on every push; tagging `v1.0.0` attaches them to a Release.

> The release APK is signed with the debug key so CI needs no secrets. For long-term use, add your own keystore (updates must keep the same signature, otherwise Android requires uninstalling first).

## Architecture

```
core/      pure Kotlin, JVM-tested — parser, ownership filter, freshness, connection tracker,
           low-battery hysteresis, gesture de-duplication, per-model gesture catalog
bt/        Android Bluetooth: permission-safe queries, bounded foreground scan (Flow, 15 s),
           OS-managed PendingIntent scan armed only while connected, receivers
data/      DataStore (settings, last snapshot, locked model, probe results)
notify/    channels, low-battery & connection notifications
widget/    Glance home-screen widget
probe/     60-s gesture probe (MediaSession + silent track, released on exit/focus loss/call)
ui/        Compose: Battery · Controls · Settings tabs, onboarding, strings (AR/EN)
```

Power profile: zero work while disconnected. While connected: one hardware-filtered low-power scan owned by the OS; the app wakes only when an Apple `07 19` frame arrives, writes only if values changed or 60 s passed.

## Tests
`app/src/test/.../core/` — battery parsing (layouts, flip, unavailable/zero, malformed, foreign frames), stale readings & clock skew, disconnections (profile vs ACL, BT off, permission loss, duplicates), ownership/ambiguity, duplicate gesture events, alert hysteresis.

## Hardware status
See [`docs/HARDWARE_TESTING.md`](docs/HARDWARE_TESTING.md) — **not yet run on a physical phone/AirPods**; a 12-step checklist is provided.

## Licence
MIT. Parser written independently from public protocol descriptions (no GPL code included).
