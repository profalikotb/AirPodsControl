# Compatibility & technical honesty

Target: Samsung Galaxy (S24 Ultra, One UI 6/7/8 · Android 14–16), stock, **no root**. Minimum Android 8.0.

## 1 · Feature status

| Status | Feature |
|---|---|
| **Verified and supported** (Android public API, behaviour documented) | Standard pairing/reconnect left to Android · connect/disconnect detection (ACL, A2DP, HFP broadcasts — exempt from background limits) · bounded foreground BLE scan (15 s, cancellable) · OS-managed PendingIntent background scan, armed only while connected · notifications · Glance home-screen widget · Arabic/English, RTL/LTR, light/dark · offline, no account |
| **Supported only on specific models** | Per-bud + case battery (earbud models) · single battery (AirPods Max) · swipe-volume row (AirPods Pro 2 only, by hardware) · noise-control guidance (ANC models only) |
| **Unavailable on stock Android (by design of Android/Apple, not of this app)** | Gesture reassignment/interception · app-based ANC/Transparency/Adaptive switching · ear detection · exact (1 %) per-bud battery · renaming AirPods · Siri · firmware updates · Apple ecosystem features |
| **Awaiting real-device testing** | Everything that touches radio hardware — see `HARDWARE_TESTING.md`. Model IDs marked *single-source*. Long-press → Android assistant via HFP. |

## 2 · Battery

**Source:** Apple Continuity "Proximity Pairing" BLE advertisement (manufacturer data, company `0x004C`, type `0x07`, length `0x19`). Only the unencrypted header is decoded:

| Offset (after `07 19`) | Meaning |
|---|---|
| 0 | prefix |
| 1–2 | model ID (big-endian, e.g. `0E 20` = AirPods Pro) |
| 3 | status; bit `0x20` = left bud is broadcasting (selects nibble order) |
| 4 | bud batteries: two 4-bit values, 0–10 = tenths, 15 = unavailable |
| 5 | high nibble: charging flags (L/R per bit 0x20, case = 0x04); low nibble: case battery |

Protocol references (reverse-engineered, not Apple-published): furiousMAC/continuity Wireshark dissector (BSD-3); OpenPods (GPL-3) and CAPod (GPL-3) used for cross-checking only — **no code was copied**, the parser is an independent implementation, so this project carries no GPL obligation.

**Precision rule:** values are shown as `~70%` / `<10%` because the broadcast resolution is 10 %. Nibble 15 or out-of-range → **Unavailable**, never 0 %. A charging flag beside an unavailable level is dropped.

**Secondary reading:** Android's own headset battery (`BluetoothDevice#getBatteryLevel`, fed by Apple's HFP `AT+IPHONEACCEV`) is shown separately, labelled "Android system reading", only when Android reports 0–100. It is a hidden API read reflectively and may be blocked on future Android releases; failure → hidden, not guessed.

### Ownership (someone else's AirPods)
The advert uses a rotating random address and an encrypted identity block. The keys needed to prove ownership are only obtainable through Apple's AAP protocol (see §4), so ownership is **inferred**; all must hold:
1. selected AirPods currently connected to this phone;
2. advert model ID = model locked from the first accepted reading;
3. RSSI ≥ threshold (default −65 dBm, adjustable −85…−45);
4. no second advertiser of the same model within 10 dB in the last 10 s → otherwise **readings are paused** and a notice is shown.

Residual risk: an identical model held closer to your phone than your own, while yours are connected and silent, could be read. Raising the threshold to −55 dBm narrows this further.

### Model ID table
| Model | ID | Confidence | Battery layout |
|---|---|---|---|
| AirPods 1 | 0x0220 | cross-checked | L/R/case |
| AirPods 2 | 0x0F20 | cross-checked | L/R/case |
| AirPods 3 | 0x1320 | cross-checked | L/R/case |
| AirPods 4 | 0x1920 | single source | L/R/case |
| AirPods 4 ANC | 0x1B20 | single source | L/R/case |
| AirPods Pro | 0x0E20 | cross-checked | L/R/case |
| AirPods Pro 2 (Lightning) | 0x1420 | cross-checked | L/R/case |
| AirPods Pro 2 (USB-C) | 0x2420 | cross-checked | L/R/case |
| AirPods Max (Lightning) | 0x0A20 | cross-checked | single |
| AirPods Max (USB-C) | 0x1F20 | single source | single |
| AirPods Pro 3, AirPods 5, AirPods Max 2, Beats | unknown | — | shown as "Unrecognised Apple audio device · 0xNNNN"; battery still shown, gesture map withheld |

Adding a new model = one line in `core/AirPodsModel.kt` once its ID is observed (the app displays it).

## 3 · Gestures

| Model family | Gesture | Built-in function | Android receives? | Reassignable |
|---|---|---|---|---|
| AirPods 1/2 | Double-tap | Set on iPhone, stored in bud (Siri/Play-Pause/Next/Prev/Off) | depends on that setting | **No** |
| AirPods 3, 4, Pro, Pro 2 | Single press | Play/pause · answer | Yes — AVRCP key to the *active player app* | **No** |
| | Double press | Next · end call | Yes — AVRCP | **No** |
| | Triple press | Previous | Yes — AVRCP | **No** |
| | Press & hold (ANC models) | Cycle noise modes | No — firmware only | **No** |
| | Press & hold (non-ANC) | Siri request | HFP voice-recognition → Android default assistant (to test) | **No** |
| | Swipe (Pro 2) | Volume | Yes — media volume change | **No** |
| AirPods Max | Crown press ×1/×2/×3 | Play-pause / next / previous | Yes — AVRCP | **No** |
| | Crown hold | Siri request | HFP (to test) | **No** |
| | Crown rotate | Volume | Yes — volume | **No** |
| | Noise button | ANC ↔ Transparency | No — firmware only | **No** |

**Why reassignment is unavailable (investigated, not assumed):**
- AVRCP pass-through keys are routed by Android's Bluetooth service directly to the media session of the app that last played audio. They do not pass through the input pipeline, so `AccessibilityService` key filtering cannot see them.
- The only way for a third-party app to receive them is to *be* the active player — which would stop the user's real player (Spotify, YouTube…) and duplicate or break controls. Rejected on the brief's "do not interfere" rule.
- Firmware-only gestures (press-and-hold, noise button) never leave the earbuds.
- Changing what a gesture does inside the earbuds requires AAP (§4).

So the Controls tab shows locked labels, not switches. The **Test my gestures** probe measures which commands actually reach this phone (60 s, music paused, blocked during calls, all resources released on exit) and marks each row "Observed ✓".

## 4 · AAP (Apple Accessory Protocol)
AAP over classic L2CAP PSM `0x1001` provides ANC switching, ear detection, 1 % battery, gesture configuration. Android exposes no public API for classic-BR/EDR L2CAP channels (only LE CoC), and Android's Bluetooth stack rejects the connection without a patch. LibrePods (GPL-3) implements AAP but **requires root/Xposed on Samsung**. Excluded per the no-root requirement. No AAP command is sent by this app.

## 5 · Android/Samsung specifics handled
- `BLUETOOTH_SCAN` with `neverForLocation` (Android 12+) → no location permission, background scans permitted. Apple `0x07` frames are not on Android's location-beacon deny-list (iBeacon `0x02` is).
- Android 8–11: fine location (+ "Allow all the time" for background) required by the OS.
- Hardware `ScanFilter` on `07 19` is mandatory — Samsung drops unfiltered scans with the screen off.
- Scan start rate-limited (Android blocks >5 starts / 30 s).
- Background scan uses `SCAN_MODE_LOW_POWER`, delivered by the OS to a receiver; nothing runs when disconnected; no foreground service, no wake-lock.
- Samsung "Sleeping apps": if updates stop with the screen off, set the app to **Unrestricted** (README).
