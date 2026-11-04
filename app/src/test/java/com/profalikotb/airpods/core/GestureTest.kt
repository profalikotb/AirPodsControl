package com.profalikotb.airpods.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTest {
    @Test fun downUpAndRepeatYieldOneCommand() {
        val d = GestureDeduplicator()
        assertEquals(MediaCommand.NEXT, d.onKey(Keys.MEDIA_NEXT, Keys.ACTION_DOWN, 0, 1_000))
        assertNull(d.onKey(Keys.MEDIA_NEXT, Keys.ACTION_UP, 0, 1_050))
        assertNull(d.onKey(Keys.MEDIA_NEXT, Keys.ACTION_DOWN, 1, 1_100))
    }

    @Test fun keyThenSynthesisedCallbackIsDeduplicated() {
        val d = GestureDeduplicator()
        assertEquals(MediaCommand.PLAY_PAUSE, d.onKey(Keys.MEDIA_PAUSE, Keys.ACTION_DOWN, 0, 0))
        assertNull(d.onCommand(MediaCommand.PLAY_PAUSE, GestureDeduplicator.Path.TRANSPORT_CALLBACK, 40))
        assertNull(d.onKey(Keys.HEADSETHOOK, Keys.ACTION_DOWN, 0, 80)) // same press via HEADSETHOOK alias
    }

    @Test fun genuineRepeatPressesAfterWindowCount() {
        val d = GestureDeduplicator(windowMs = 350)
        assertEquals(MediaCommand.NEXT, d.onKey(Keys.MEDIA_NEXT, Keys.ACTION_DOWN, 0, 0))
        assertEquals(MediaCommand.NEXT, d.onKey(Keys.MEDIA_NEXT, Keys.ACTION_DOWN, 0, 600))
        assertEquals(MediaCommand.PREVIOUS, d.onKey(Keys.MEDIA_PREVIOUS, Keys.ACTION_DOWN, 0, 650)) // different cmd passes
        assertNull(d.onKey(999, Keys.ACTION_DOWN, 0, 2_000)) // unknown key ignored
    }

    @Test fun catalogNeverClaimsReassignmentAndMatchesHardware() {
        AirPodsModel.entries.forEach { m -> GestureCatalog.rowsFor(m).forEach { assertFalse(it.reassignable) } }
        val pro2 = GestureCatalog.rowsFor(AirPodsModel.AIRPODS_PRO_2).map { it.gesture }
        assertTrue(Gesture.SWIPE in pro2)
        assertFalse(Gesture.SWIPE in GestureCatalog.rowsFor(AirPodsModel.AIRPODS_4).map { it.gesture })
        assertEquals(Exposure.FIRMWARE_ONLY, GestureCatalog.rowsFor(AirPodsModel.AIRPODS_PRO).first { it.gesture == Gesture.LONG }.exposure)
        assertEquals(listOf(Gesture.DOUBLE), GestureCatalog.rowsFor(AirPodsModel.AIRPODS_2).map { it.gesture })
        assertNull(GestureCatalog.noiseGuidance(AirPodsModel.AIRPODS_3, false))
        assertTrue(GestureCatalog.noiseGuidance(AirPodsModel.AIRPODS_MAX, true)!!.isNotBlank())
    }
}
