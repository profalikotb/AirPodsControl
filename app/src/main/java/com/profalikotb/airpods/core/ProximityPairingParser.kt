package com.profalikotb.airpods.core

/**
 * Battery level exactly as broadcast. AirPods advertise each component as a 4-bit value:
 * 0..10 = tenths of full charge, 15 = not available (bud in closed case / case out of range).
 * The true level is only known to the nearest step, so the UI shows "~70%" — never "73%".
 */
sealed interface BatteryLevel {
    data class Known(val tenths: Int) : BatteryLevel {
        init { require(tenths in 0..10) }
        /** Representative percentage for thresholds / sorting (never shown as exact). */
        val approxPercent: Int get() = tenths * 10
    }
    data object Unavailable : BatteryLevel

    companion object {
        fun fromNibble(n: Int): BatteryLevel = if (n in 0..10) Known(n) else Unavailable
    }
}

fun BatteryLevel.approxPercentOrNull(): Int? = (this as? BatteryLevel.Known)?.approxPercent

/** Decoded Apple Proximity Pairing (0x07) message. Only the unencrypted header is decoded. */
data class ProximityMessage(
    val modelId: Int,
    val model: AirPodsModel,
    val left: BatteryLevel,
    val right: BatteryLevel,
    val case: BatteryLevel,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    /** True when the bud that is transmitting is the left one. */
    val leftIsPrimary: Boolean,
    val rawStatus: Int,
) {
    /** For over-ear models there is one battery; Apple places it in the primary nibble. */
    val single: BatteryLevel get() = if (leftIsPrimary) left else right
}

object ProximityPairingParser {
    const val APPLE_COMPANY_ID = 0x004C
    const val TYPE_PROXIMITY_PAIRING = 0x07
    /** Length byte of a full AirPods proximity-pairing payload. */
    const val EXPECTED_LENGTH = 0x19

    /**
     * @param appleData manufacturer-specific data for company 0x004C, i.e. bytes AFTER the
     * company identifier, as returned by android ScanRecord.getManufacturerSpecificData(0x004C).
     * Apple packs several TLVs (type, length, value…) in one record; we walk them.
     * @return null when no well-formed 0x07 message is present. Never fabricates values.
     */
    fun parse(appleData: ByteArray?): ProximityMessage? {
        if (appleData == null) return null
        var i = 0
        while (i + 1 < appleData.size) {
            val type = appleData[i].u()
            val len = appleData[i + 1].u()
            val start = i + 2
            val end = start + len
            if (end > appleData.size) return null // truncated / malformed: refuse
            if (type == TYPE_PROXIMITY_PAIRING) {
                return decode(appleData.copyOfRange(start, end), len)
            }
            i = end
        }
        return null
    }

    private fun decode(v: ByteArray, declaredLen: Int): ProximityMessage? {
        // Unencrypted header layout (offsets within value):
        // 0 prefix | 1-2 model | 3 status | 4 pod batteries | 5 charge flags (hi) + case (lo) | 6 lid …
        if (declaredLen != EXPECTED_LENGTH || v.size < 6) return null
        val modelId = (v[1].u() shl 8) or v[2].u()
        val status = v[3].u()
        val pods = v[4].u()
        val chargeCase = v[5].u()

        // Status bit 0x20 set => left bud is the broadcaster ("not flipped").
        val leftIsPrimary = (status and 0x20) != 0
        val hi = pods shr 4
        val lo = pods and 0x0F
        val leftNib = if (leftIsPrimary) lo else hi
        val rightNib = if (leftIsPrimary) hi else lo
        val chargeFlags = chargeCase shr 4
        val caseNib = chargeCase and 0x0F
        val leftChg = (chargeFlags and (if (leftIsPrimary) 0x01 else 0x02)) != 0
        val rightChg = (chargeFlags and (if (leftIsPrimary) 0x02 else 0x01)) != 0
        val caseChg = (chargeFlags and 0x04) != 0

        val left = BatteryLevel.fromNibble(leftNib)
        val right = BatteryLevel.fromNibble(rightNib)
        val case = BatteryLevel.fromNibble(caseNib)
        return ProximityMessage(
            modelId = modelId,
            model = AirPodsModel.fromId(modelId),
            left = left,
            right = right,
            case = case,
            // A charging flag next to an unavailable level is meaningless: do not show it.
            leftCharging = leftChg && left is BatteryLevel.Known,
            rightCharging = rightChg && right is BatteryLevel.Known,
            caseCharging = caseChg && case is BatteryLevel.Known,
            leftIsPrimary = leftIsPrimary,
            rawStatus = status,
        )
    }

    private fun Byte.u() = toInt() and 0xFF

    /** Manufacturer-data filter for ScanFilter: first two Apple bytes must be 07 19. */
    val SCAN_FILTER_DATA = byteArrayOf(TYPE_PROXIMITY_PAIRING.toByte(), EXPECTED_LENGTH.toByte())
    val SCAN_FILTER_MASK = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
}
