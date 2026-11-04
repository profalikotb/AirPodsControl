package com.profalikotb.airpods.core

// ───────────────────────────── Freshness ─────────────────────────────

sealed interface Freshness {
    /** Reading received recently while connected. */
    data object Live : Freshness
    /** Older reading, or AirPods disconnected: shown as "Last known · N min ago". */
    data class LastKnown(val ageMs: Long) : Freshness
    /** Nothing usable: shown as "Unavailable". */
    data object Unavailable : Freshness
}

object FreshnessPolicy {
    const val LIVE_MS = 3 * 60_000L          // AirPods re-advertise every few seconds when awake
    const val EXPIRE_MS = 12 * 60 * 60_000L  // after 12 h a last-known value is no longer useful

    fun classify(snapshot: BatterySnapshot?, connected: Boolean, now: Long): Freshness {
        if (snapshot == null) return Freshness.Unavailable
        val age = now - snapshot.timestampMs
        if (age < 0) return Freshness.LastKnown(0) // clock moved backwards: never claim "live"
        if (age > EXPIRE_MS) return Freshness.Unavailable
        return if (connected && age <= LIVE_MS) Freshness.Live else Freshness.LastKnown(age)
    }
}

// ───────────────────────── Connection tracking ─────────────────────────

enum class LinkState { DISCONNECTED, CONNECTED }

/**
 * Collapses the noisy stream of ACL / A2DP / HFP broadcasts into clean connect/disconnect
 * transitions for the *selected* device only. Profiles are tracked separately so that,
 * e.g., HFP dropping while A2DP stays up is not treated as a disconnect.
 */
class ConnectionTracker(private var selectedAddress: String?) {
    enum class Source { ACL, A2DP, HEADSET }
    sealed interface Event {
        data class Up(val address: String, val source: Source) : Event
        data class Down(val address: String, val source: Source) : Event
        data object BluetoothOff : Event
        data object PermissionLost : Event
    }
    data class Transition(val from: LinkState, val to: LinkState)

    private val up = mutableSetOf<Source>()
    var state: LinkState = LinkState.DISCONNECTED; private set

    fun select(address: String?) { if (address != selectedAddress) { selectedAddress = address; up.clear(); state = LinkState.DISCONNECTED } }

    /** @return a transition, or null if the event did not change the logical state. */
    fun onEvent(e: Event): Transition? {
        val before = state
        when (e) {
            is Event.Up -> if (e.address.equals(selectedAddress, true)) up += e.source else return null
            is Event.Down -> if (e.address.equals(selectedAddress, true)) {
                if (e.source == Source.ACL) up.clear() else up -= e.source
            } else return null
            Event.BluetoothOff, Event.PermissionLost -> up.clear()
        }
        state = if (up.isEmpty()) LinkState.DISCONNECTED else LinkState.CONNECTED
        return if (state != before) Transition(before, state) else null
    }

    /** Seed from a direct profile query at start-up / after process death. */
    fun seed(connectedSources: Set<Source>) { up.clear(); up += connectedSources; state = if (up.isEmpty()) LinkState.DISCONNECTED else LinkState.CONNECTED }
}

// ───────────────────────── Low-battery alerts ─────────────────────────

/**
 * One alert per component per discharge cycle. Re-arms only after the level rises
 * at least [rearmMarginPct] above the threshold or the component starts charging,
 * so 10 % broadcast steps bouncing around a threshold never spam notifications.
 */
class LowBatteryAlerter(
    var thresholdPct: Int = 20,
    var criticalPct: Int = 10,
    private val rearmMarginPct: Int = 10,
) {
    enum class Part { LEFT, RIGHT, CASE, HEADPHONES }
    enum class Severity { LOW, CRITICAL }
    data class Alert(val part: Part, val severity: Severity, val level: BatteryLevel.Known)

    private val fired = HashMap<Part, Severity>()

    fun evaluate(s: BatterySnapshot): List<Alert> {
        val parts = if (s.overEar) listOf(Triple(Part.HEADPHONES, s.single, false))
        else listOf(Triple(Part.LEFT, s.left, s.leftCharging), Triple(Part.RIGHT, s.right, s.rightCharging))
        // Case is not alerted: users rarely act on it and it is often Unavailable.
        val out = mutableListOf<Alert>()
        for ((part, lvl, charging) in parts) {
            if (lvl !is BatteryLevel.Known) continue // unknown never triggers or re-arms
            val pct = lvl.approxPercent
            if (charging || pct >= thresholdPct + rearmMarginPct) { fired.remove(part); continue }
            val sev = when { pct <= criticalPct -> Severity.CRITICAL; pct <= thresholdPct -> Severity.LOW; else -> null } ?: continue
            val prev = fired[part]
            if (prev == null || (prev == Severity.LOW && sev == Severity.CRITICAL)) {
                fired[part] = sev; out += Alert(part, sev, lvl)
            }
        }
        return out
    }

    fun exportState(): String = fired.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }
    fun importState(s: String?) {
        fired.clear()
        s?.split(",")?.filter { it.contains(':') }?.forEach {
            val (p, v) = it.split(":")
            runCatching { fired[Part.valueOf(p)] = Severity.valueOf(v) }
        }
    }
}
