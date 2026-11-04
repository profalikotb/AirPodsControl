package com.profalikotb.airpods.core

import com.profalikotb.airpods.core.ConnectionTracker.Event
import com.profalikotb.airpods.core.ConnectionTracker.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateLogicTest {
    private fun snap(l: Int?, r: Int?, at: Long, lc: Boolean = false) = BatterySnapshot(
        0x0E20, l?.let { BatteryLevel.Known(it) } ?: BatteryLevel.Unavailable,
        r?.let { BatteryLevel.Known(it) } ?: BatteryLevel.Unavailable, BatteryLevel.Unavailable,
        lc, false, false, false, BatteryLevel.Unavailable, -50, at,
    )

    // ── Stale readings ──
    @Test fun freshness() {
        val now = 10_000_000L
        assertEquals(Freshness.Unavailable, FreshnessPolicy.classify(null, true, now))
        assertEquals(Freshness.Live, FreshnessPolicy.classify(snap(5, 5, now - 1_000), true, now))
        assertEquals(Freshness.LastKnown(1_000), FreshnessPolicy.classify(snap(5, 5, now - 1_000), false, now))
        val old = FreshnessPolicy.LIVE_MS + 1
        assertEquals(Freshness.LastKnown(old), FreshnessPolicy.classify(snap(5, 5, now - old), true, now))
        assertEquals(Freshness.Unavailable, FreshnessPolicy.classify(snap(5, 5, now - FreshnessPolicy.EXPIRE_MS - 1), false, now))
        assertEquals(Freshness.LastKnown(0), FreshnessPolicy.classify(snap(5, 5, now + 60_000), true, now)) // clock skew
    }

    // ── Disconnections ──
    @Test fun ignoresOtherDevicesAndCollapsesProfiles() {
        val t = ConnectionTracker("AA:BB:CC:DD:EE:FF")
        assertNull(t.onEvent(Event.Up("11:22:33:44:55:66", Source.ACL)))
        assertEquals(LinkState.CONNECTED, t.onEvent(Event.Up("aa:bb:cc:dd:ee:ff", Source.ACL))!!.to)
        assertNull(t.onEvent(Event.Up("AA:BB:CC:DD:EE:FF", Source.A2DP)))
        assertNull(t.onEvent(Event.Up("AA:BB:CC:DD:EE:FF", Source.HEADSET)))
        assertNull(t.onEvent(Event.Down("AA:BB:CC:DD:EE:FF", Source.HEADSET))) // HFP drop ≠ disconnect
        assertEquals(LinkState.CONNECTED, t.state)
        val tr = t.onEvent(Event.Down("AA:BB:CC:DD:EE:FF", Source.ACL))!!
        assertEquals(LinkState.CONNECTED, tr.from); assertEquals(LinkState.DISCONNECTED, tr.to)
        assertNull(t.onEvent(Event.Down("AA:BB:CC:DD:EE:FF", Source.A2DP))) // late duplicate
    }

    @Test fun bluetoothOffAndPermissionLossDisconnect() {
        val t = ConnectionTracker("AA:BB:CC:DD:EE:FF")
        t.seed(setOf(Source.A2DP))
        assertEquals(LinkState.CONNECTED, t.state)
        assertEquals(LinkState.DISCONNECTED, t.onEvent(Event.BluetoothOff)!!.to)
        t.seed(setOf(Source.ACL))
        assertEquals(LinkState.DISCONNECTED, t.onEvent(Event.PermissionLost)!!.to)
        t.seed(setOf(Source.ACL)); t.select("11:22:33:44:55:66")
        assertEquals(LinkState.DISCONNECTED, t.state)
    }

    @Test fun ownershipRequiresConnectionModelAndSignal() {
        val f = OwnershipFilter()
        assertEquals(OwnershipFilter.Decision.Reject(OwnershipFilter.Reason.NOT_CONNECTED), f.evaluate("a", -40, 0x0E20, 0x0E20, false, 0))
        assertEquals(OwnershipFilter.Decision.Reject(OwnershipFilter.Reason.MODEL_MISMATCH), f.evaluate("a", -40, 0x1420, 0x0E20, true, 0))
        assertEquals(OwnershipFilter.Decision.Reject(OwnershipFilter.Reason.WEAK_SIGNAL), f.evaluate("a", -80, 0x0E20, 0x0E20, true, 0))
        assertEquals(OwnershipFilter.Decision.Accept, f.evaluate("a", -45, 0x0E20, 0x0E20, true, 0))
    }

    @Test fun ownershipAmbiguousWhenTwoSimilarNearby() {
        val f = OwnershipFilter()
        assertEquals(OwnershipFilter.Decision.Accept, f.evaluate("mine", -45, 0x0E20, null, true, 0))
        assertEquals(OwnershipFilter.Decision.Ambiguous, f.evaluate("theirs", -50, 0x0E20, null, true, 1_000))
        assertEquals(OwnershipFilter.Decision.Ambiguous, f.evaluate("mine", -45, 0x0E20, null, true, 2_000))
        // Neighbour clearly weaker (≥10 dB) → strongest (ours) accepted, theirs rejected.
        val g = OwnershipFilter()
        g.evaluate("mine", -42, 0x0E20, null, true, 0)
        assertEquals(OwnershipFilter.Decision.Reject(OwnershipFilter.Reason.NOT_STRONGEST), g.evaluate("theirs", -60, 0x0E20, null, true, 100))
        assertEquals(OwnershipFilter.Decision.Accept, g.evaluate("mine", -42, 0x0E20, null, true, 200))
        // Window expiry clears rotated/departed addresses.
        assertEquals(OwnershipFilter.Decision.Accept, f.evaluate("mine", -45, 0x0E20, null, true, 30_000))
    }

    @Test fun lowBatteryHysteresis() {
        val a = LowBatteryAlerter(thresholdPct = 20, criticalPct = 10)
        assertTrue(a.evaluate(snap(3, 5, 0)).isEmpty())
        val first = a.evaluate(snap(2, 5, 1))
        assertEquals(1, first.size); assertEquals(LowBatteryAlerter.Part.LEFT, first[0].part)
        assertTrue(a.evaluate(snap(2, 5, 2)).isEmpty())           // no repeat
        assertTrue(a.evaluate(snap(3, 5, 3)).isEmpty())           // bounce, not re-armed
        assertEquals(LowBatteryAlerter.Severity.CRITICAL, a.evaluate(snap(1, 5, 4)).single().severity) // escalate once
        assertTrue(a.evaluate(snap(1, 5, 5)).isEmpty())
        assertTrue(a.evaluate(snap(1, 5, 6, lc = true)).isEmpty()) // charging re-arms
        assertEquals(1, a.evaluate(snap(2, 5, 7)).size)
        assertTrue(a.evaluate(snap(null, null, 8)).isEmpty())      // unknown never alerts
        val b = LowBatteryAlerter(); b.importState(a.exportState())
        assertTrue(b.evaluate(snap(2, 5, 9)).isEmpty())           // survives process restart
    }
}
