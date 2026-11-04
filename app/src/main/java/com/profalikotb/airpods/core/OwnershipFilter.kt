package com.profalikotb.airpods.core

/**
 * Decides whether a proximity-pairing advertisement belongs to the user's own AirPods.
 *
 * Limitation (documented, not hidden): the advertisement uses a rotating random BLE address
 * and its identifying part is encrypted with keys Android cannot obtain without the AAP
 * protocol (root-only on Samsung). Ownership is therefore inferred from four conditions,
 * all of which must hold:
 *   1. the selected AirPods are currently connected to this phone (classic Bluetooth),
 *   2. the advert's model ID matches the selected AirPods' model (learned on first lock),
 *   3. RSSI ≥ threshold (default −65 dBm ≈ within ~1 m in practice),
 *   4. no second strong advertiser of the same model is nearby (else: AMBIGUOUS, show nothing new).
 */
class OwnershipFilter(
    private val rssiThreshold: Int = DEFAULT_RSSI_THRESHOLD,
    private val windowMs: Long = 10_000,
    /** Two candidates within this many dB of each other cannot be told apart. */
    private val ambiguityMarginDb: Int = 10,
) {
    sealed interface Decision {
        data object Accept : Decision
        data class Reject(val reason: Reason) : Decision
        data object Ambiguous : Decision
    }
    enum class Reason { NOT_CONNECTED, WEAK_SIGNAL, MODEL_MISMATCH, NOT_STRONGEST }

    private data class Seen(val rssi: Int, val at: Long, val modelId: Int)
    private val recent = HashMap<String, Seen>()

    fun evaluate(
        address: String,
        rssi: Int,
        modelId: Int,
        expectedModelId: Int?,
        selectedConnected: Boolean,
        now: Long,
    ): Decision {
        recent.entries.removeAll { now - it.value.at > windowMs }
        if (!selectedConnected) return Decision.Reject(Reason.NOT_CONNECTED)
        if (expectedModelId != null && expectedModelId != modelId) return Decision.Reject(Reason.MODEL_MISMATCH)
        if (rssi < rssiThreshold) return Decision.Reject(Reason.WEAK_SIGNAL)

        recent[address] = Seen(rssi, now, modelId)
        val sameModel = recent.filter { it.value.modelId == modelId }
        val best = sameModel.maxBy { it.value.rssi }
        val rivals = sameModel.filter { it.key != best.key && best.value.rssi - it.value.rssi < ambiguityMarginDb }
        if (rivals.isNotEmpty()) return Decision.Ambiguous
        return if (best.key == address) Decision.Accept else Decision.Reject(Reason.NOT_STRONGEST)
    }

    fun reset() = recent.clear()

    companion object { const val DEFAULT_RSSI_THRESHOLD = -65 }
}
