package com.profalikotb.airpods.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityPairingParserTest {

    /** Builds a 0x004C payload: 07 19 | 01 | model | status | pods | chg/case | 19 trailing bytes. */
    private fun adv(model: Int, status: Int, pods: Int, chgCase: Int, len: Int = 0x19, prefixTlv: ByteArray = ByteArray(0)): ByteArray {
        val v = ByteArray(len)
        if (len >= 6) {
            v[0] = 0x01; v[1] = (model shr 8).toByte(); v[2] = model.toByte()
            v[3] = status.toByte(); v[4] = pods.toByte(); v[5] = chgCase.toByte()
        }
        return prefixTlv + byteArrayOf(0x07, len.toByte()) + v
    }

    @Test fun decodesLeftPrimary() {
        val m = ProximityPairingParser.parse(adv(0x0E20, 0x2B, 0x8A, 0x15))!!
        assertEquals(AirPodsModel.AIRPODS_PRO, m.model)
        assertTrue(m.leftIsPrimary)
        assertEquals(BatteryLevel.Known(10), m.left)
        assertEquals(BatteryLevel.Known(8), m.right)
        assertEquals(BatteryLevel.Known(5), m.case)
        assertTrue(m.leftCharging); assertFalse(m.rightCharging); assertFalse(m.caseCharging)
    }

    @Test fun decodesRightPrimaryFlipsNibblesAndChargeBits() {
        val m = ProximityPairingParser.parse(adv(0x1420, 0x0B, 0x8A, 0x15))!!
        assertFalse(m.leftIsPrimary)
        assertEquals(BatteryLevel.Known(8), m.left)
        assertEquals(BatteryLevel.Known(10), m.right)
        assertFalse(m.leftCharging); assertTrue(m.rightCharging)
        assertEquals(AirPodsModel.AIRPODS_PRO_2, m.model)
    }

    @Test fun nibble15IsUnavailableNeverZero() {
        val m = ProximityPairingParser.parse(adv(0x0F20, 0x20, 0xF7, 0x7F))!!
        assertEquals(BatteryLevel.Known(7), m.left)
        assertEquals(BatteryLevel.Unavailable, m.right)
        assertEquals(BatteryLevel.Unavailable, m.case)
        // Charging bits next to an unavailable level are suppressed.
        assertFalse(m.caseCharging); assertFalse(m.rightCharging); assertTrue(m.leftCharging)
    }

    @Test fun outOfRangeNibblesAreUnavailable() {
        val m = ProximityPairingParser.parse(adv(0x0F20, 0x20, 0xBC, 0x0D))!!
        assertEquals(BatteryLevel.Unavailable, m.left)
        assertEquals(BatteryLevel.Unavailable, m.right)
        assertEquals(BatteryLevel.Unavailable, m.case)
    }

    @Test fun zeroIsKnownZeroNotUnavailable() {
        val m = ProximityPairingParser.parse(adv(0x0F20, 0x20, 0x00, 0x00))!!
        assertEquals(BatteryLevel.Known(0), m.left)
        assertEquals(0, m.left.approxPercentOrNull())
    }

    @Test fun unknownModelIsReportedNotMislabelled() {
        val m = ProximityPairingParser.parse(adv(0x2720, 0x20, 0x55, 0x05))!!
        assertEquals(AirPodsModel.UNKNOWN, m.model)
        assertEquals(0x2720, m.modelId)
        assertEquals("0x2720", AirPodsModel.hex(m.modelId))
    }

    @Test fun walksPrecedingTlvs() {
        val nearby = byteArrayOf(0x10, 0x03, 0x01, 0x02, 0x03)
        val m = ProximityPairingParser.parse(adv(0x1320, 0x20, 0x66, 0x06, prefixTlv = nearby))
        assertEquals(AirPodsModel.AIRPODS_3, m!!.model)
    }

    @Test fun rejectsMalformed() {
        assertNull(ProximityPairingParser.parse(null))
        assertNull(ProximityPairingParser.parse(ByteArray(0)))
        assertNull(ProximityPairingParser.parse(byteArrayOf(0x07)))
        assertNull(ProximityPairingParser.parse(adv(0x0E20, 0x20, 0x55, 0x05).copyOf(10))) // truncated
        assertNull(ProximityPairingParser.parse(adv(0x0E20, 0x20, 0x55, 0x05, len = 0x05)))  // wrong length
        assertNull(ProximityPairingParser.parse(byteArrayOf(0x02, 0x15) + ByteArray(0x15)))  // iBeacon
    }

    @Test fun overEarUsesPrimaryNibble() {
        val m = ProximityPairingParser.parse(adv(0x0A20, 0x20, 0xF6, 0x0F))!!
        assertTrue(m.model.isOverEar)
        assertEquals(BatteryLevel.Known(6), m.single)
    }

    @Test fun snapshotRoundTripAndCorruptionSafety() {
        val m = ProximityPairingParser.parse(adv(0x0E20, 0x2B, 0x8A, 0x15))!!
        val s = BatterySnapshot.from(m, -50, 1_700_000_000_000)
        assertEquals(s, BatterySnapshot.decode(s.encode()))
        assertNull(BatterySnapshot.decode("garbage"))
        assertNull(BatterySnapshot.decode(null))
        assertNull(BatterySnapshot.decode("9|1|2|3|4|0|0|0|0|x|-50|1"))
        assertTrue(s.sameContentAs(s.copy(timestampMs = 5, rssi = -60)))
        assertFalse(s.sameContentAs(s.copy(left = BatteryLevel.Known(3))))
        assertEquals(80, s.lowestBudPercent())
    }
}
