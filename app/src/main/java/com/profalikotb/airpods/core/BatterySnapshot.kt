package com.profalikotb.airpods.core

/** One accepted battery reading for the user's own AirPods. */
data class BatterySnapshot(
    val modelId: Int,
    val left: BatteryLevel,
    val right: BatteryLevel,
    val case: BatteryLevel,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val overEar: Boolean,
    val single: BatteryLevel,
    val rssi: Int,
    val timestampMs: Long,
) {
    val model: AirPodsModel get() = AirPodsModel.fromId(modelId)

    /** Same battery content, ignoring timestamp/RSSI — used to suppress redundant writes. */
    fun sameContentAs(o: BatterySnapshot?): Boolean = o != null &&
        modelId == o.modelId && left == o.left && right == o.right && case == o.case &&
        leftCharging == o.leftCharging && rightCharging == o.rightCharging &&
        caseCharging == o.caseCharging && single == o.single

    /** Lowest known bud level (used for widget colour & alerts). */
    fun lowestBudPercent(): Int? =
        if (overEar) single.approxPercentOrNull()
        else listOfNotNull(left.approxPercentOrNull(), right.approxPercentOrNull()).minOrNull()

    fun encode(): String = listOf(
        VERSION, modelId, left.enc(), right.enc(), case.enc(),
        leftCharging.b(), rightCharging.b(), caseCharging.b(), overEar.b(), single.enc(),
        rssi, timestampMs,
    ).joinToString("|")

    companion object {
        private const val VERSION = 1

        fun from(msg: ProximityMessage, rssi: Int, now: Long) = BatterySnapshot(
            modelId = msg.modelId, left = msg.left, right = msg.right, case = msg.case,
            leftCharging = msg.leftCharging, rightCharging = msg.rightCharging,
            caseCharging = msg.caseCharging, overEar = msg.model.isOverEar,
            single = msg.single, rssi = rssi, timestampMs = now,
        )

        /** Returns null for corrupt/old-format data instead of guessing. */
        fun decode(s: String?): BatterySnapshot? = runCatching {
            val p = s!!.split("|")
            require(p.size == 12 && p[0].toInt() == VERSION)
            BatterySnapshot(
                modelId = p[1].toInt(), left = dec(p[2]), right = dec(p[3]), case = dec(p[4]),
                leftCharging = p[5] == "1", rightCharging = p[6] == "1", caseCharging = p[7] == "1",
                overEar = p[8] == "1", single = dec(p[9]), rssi = p[10].toInt(), timestampMs = p[11].toLong(),
            )
        }.getOrNull()

        private fun BatteryLevel.enc() = when (this) { is BatteryLevel.Known -> tenths.toString(); BatteryLevel.Unavailable -> "x" }
        private fun dec(s: String): BatteryLevel = if (s == "x") BatteryLevel.Unavailable else BatteryLevel.Known(s.toInt())
        private fun Boolean.b() = if (this) "1" else "0"
    }
}
