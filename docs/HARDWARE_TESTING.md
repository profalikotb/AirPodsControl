# Real-hardware test record

## Tested so far

| Area | Environment | Result |
|---|---|---|
| BLE parser, nibble/flip/charging logic, malformed & foreign frames (iBeacon) | JVM unit tests | ✅ 20/20 pass |
| Ownership filter (connection, model lock, RSSI, ambiguity window) | JVM unit tests | ✅ |
| Freshness / stale / clock-skew | JVM unit tests | ✅ |
| Disconnect logic (ACL vs profile drops, BT off, permission loss, late duplicates) | JVM unit tests | ✅ |
| Duplicate gesture events (down/up, repeat, key + transport echo) | JVM unit tests | ✅ |
| Low-battery alert hysteresis & restart persistence | JVM unit tests | ✅ |
| **Anything on a real phone or real AirPods** | — | ❌ **Not yet tested. No hardware was available to the build environment.** |

No result in this project claims hardware verification. Model IDs come from published reverse-engineering, cross-checked between sources.

## Checklist for the first S24 Ultra session (≈15 min)

Record pass/fail + your AirPods model. Settings → Controls tab model line shows the BLE ID.

1. Install, grant *Nearby devices*, select AirPods → model line shows correct model.
2. Open case lid next to phone → Refresh → L/R/case appear within 15 s.
3. Compare with an iPhone if available (should match within one 10 % step).
4. Put one bud in the case, lid open → that bud shows ⚡ Charging.
5. Close case, wear buds → case becomes *Unavailable* or *Last known*, never a fake value.
6. Turn AirPods off (in case, lid closed 1 min) → status *Not connected*, values *Last known · N min ago*.
7. Second person's AirPods (same model) opened beside yours → readings pause with the "other AirPods nearby" notice, or keep showing yours only.
8. Screen off 10 min while listening → widget/notification still updates (if not: set app battery to *Unrestricted*, retest).
9. Controls → Test my gestures: single, double, triple press, swipe (Pro 2) → each row shows *Observed ✓*. Note long-press behaviour on non-ANC models.
10. Incoming call during normal use: press stem → call answered normally (app must not interfere).
11. Toggle Bluetooth off/on; revoke *Nearby devices* in Android settings; reopen app → graceful recovery prompts, no crash.
12. Arabic: Settings → العربية → layout mirrors, bud gauges stay physical left/right.

Send me the results (or a screen recording) and the unknown model IDs if any; the model table and gesture rows are updated from that.
