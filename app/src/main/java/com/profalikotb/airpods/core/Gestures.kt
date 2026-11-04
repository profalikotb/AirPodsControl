package com.profalikotb.airpods.core

// ─────────────────────────── Observed commands ───────────────────────────

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS, STOP, FAST_FORWARD, REWIND, VOLUME_UP, VOLUME_DOWN }

/** Android KeyEvent constants duplicated here so the core stays JVM-testable. */
object Keys {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val VOLUME_UP = 24; const val VOLUME_DOWN = 25; const val HEADSETHOOK = 79
    const val MEDIA_PLAY_PAUSE = 85; const val MEDIA_STOP = 86; const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88; const val MEDIA_REWIND = 89; const val MEDIA_FAST_FORWARD = 90
    const val MEDIA_PLAY = 126; const val MEDIA_PAUSE = 127

    fun toCommand(keyCode: Int): MediaCommand? = when (keyCode) {
        HEADSETHOOK, MEDIA_PLAY_PAUSE, MEDIA_PLAY, MEDIA_PAUSE -> MediaCommand.PLAY_PAUSE
        MEDIA_NEXT -> MediaCommand.NEXT
        MEDIA_PREVIOUS -> MediaCommand.PREVIOUS
        MEDIA_STOP -> MediaCommand.STOP
        MEDIA_FAST_FORWARD -> MediaCommand.FAST_FORWARD
        MEDIA_REWIND -> MediaCommand.REWIND
        VOLUME_UP -> MediaCommand.VOLUME_UP
        VOLUME_DOWN -> MediaCommand.VOLUME_DOWN
        else -> null
    }
}

/**
 * A single physical press can reach a MediaSession several times: as KEY DOWN + KEY UP,
 * as auto-repeat, and again through a transport-control callback (onPlay/onSkipToNext)
 * that the framework synthesises from the same key. This collapses all of those into
 * exactly one command, so nothing is ever acted on twice.
 */
class GestureDeduplicator(private val windowMs: Long = 350) {
    enum class Path { MEDIA_BUTTON, TRANSPORT_CALLBACK, VOLUME_OBSERVER }
    private var lastCmd: MediaCommand? = null
    private var lastAt = Long.MIN_VALUE / 2

    /** Key event path. Returns the command once, on the first DOWN; null otherwise. */
    fun onKey(keyCode: Int, action: Int, repeatCount: Int, at: Long): MediaCommand? {
        if (action != Keys.ACTION_DOWN || repeatCount > 0) return null
        return accept(Keys.toCommand(keyCode) ?: return null, at)
    }

    /** Callback / observer paths (already one-per-press from the framework, but may echo a key). */
    fun onCommand(cmd: MediaCommand, @Suppress("UNUSED_PARAMETER") path: Path, at: Long): MediaCommand? = accept(cmd, at)

    private fun accept(cmd: MediaCommand, at: Long): MediaCommand? {
        if (cmd == lastCmd && at - lastAt in 0..windowMs) return null
        lastCmd = cmd; lastAt = at
        return cmd
    }
}

// ───────────────────────────── Gesture catalog ─────────────────────────────

enum class Gesture { SINGLE, DOUBLE, TRIPLE, LONG, SWIPE, ROTATE, NOISE_BUTTON }

/** How a gesture surfaces on Android. */
enum class Exposure {
    /** AVRCP pass-through key delivered by Android to the media app that is currently playing. */
    AVRCP_TO_ACTIVE_PLAYER,
    /** Absolute-volume change on the music stream; visible to apps as a volume change only. */
    VOLUME_STREAM,
    /** Processed inside the earbud firmware; Android receives no event at all. */
    FIRMWARE_ONLY,
    /** Voice-assistant request over HFP; Android launches its own default assistant (if any). */
    HFP_ASSISTANT,
    /** Behaviour depends on a setting stored in the earbuds that only an Apple device can change. */
    FIRMWARE_CONFIGURED,
}

data class GestureRow(
    val gesture: Gesture,
    val nativeEn: String,
    val nativeAr: String,
    val exposure: Exposure,
    /** The command a probe is expected to observe, if any (used to label probe results). */
    val expectedCommand: MediaCommand?,
    /** Always false on stock (non-root) Android — see docs/COMPATIBILITY.md §Gestures. */
    val reassignable: Boolean = false,
    val needsDeviceTest: Boolean = false,
)

object GestureCatalog {
    fun rowsFor(model: AirPodsModel): List<GestureRow> = when (model.controls) {
        ControlStyle.DOUBLE_TAP -> listOf(
            GestureRow(Gesture.DOUBLE, "Action set on an iPhone (Siri, Play/Pause, Next, Previous or Off), per bud",
                "الإجراء المضبوط من iPhone (Siri، تشغيل/إيقاف، التالي، السابق أو إيقاف) لكل سماعة",
                Exposure.FIRMWARE_CONFIGURED, null, needsDeviceTest = true),
        )
        ControlStyle.FORCE_SENSOR -> buildList {
            add(GestureRow(Gesture.SINGLE, "Play / pause · answer call", "تشغيل / إيقاف مؤقت · الرد على المكالمة",
                Exposure.AVRCP_TO_ACTIVE_PLAYER, MediaCommand.PLAY_PAUSE))
            add(GestureRow(Gesture.DOUBLE, "Next track · end call", "المقطع التالي · إنهاء المكالمة",
                Exposure.AVRCP_TO_ACTIVE_PLAYER, MediaCommand.NEXT))
            add(GestureRow(Gesture.TRIPLE, "Previous track", "المقطع السابق",
                Exposure.AVRCP_TO_ACTIVE_PLAYER, MediaCommand.PREVIOUS))
            if (model.hasAnc) add(GestureRow(Gesture.LONG, "Cycle noise control (ANC ↔ Transparency; modes chosen on iPhone)",
                "التبديل بين أوضاع الضوضاء (إلغاء ↔ الشفافية؛ تُحدد الأوضاع من iPhone)", Exposure.FIRMWARE_ONLY, null))
            else add(GestureRow(Gesture.LONG, "Voice assistant request (Siri on Apple devices)",
                "طلب المساعد الصوتي (Siri على أجهزة Apple)", Exposure.HFP_ASSISTANT, null, needsDeviceTest = true))
            if (model.hasSwipeVolume) add(GestureRow(Gesture.SWIPE, "Swipe up/down on stem: volume",
                "السحب لأعلى/لأسفل على الساق: مستوى الصوت", Exposure.VOLUME_STREAM, MediaCommand.VOLUME_UP))
        }
        ControlStyle.DIGITAL_CROWN -> listOf(
            GestureRow(Gesture.SINGLE, "Crown press: play / pause · answer call", "ضغطة التاج: تشغيل / إيقاف · الرد",
                Exposure.AVRCP_TO_ACTIVE_PLAYER, MediaCommand.PLAY_PAUSE),
            GestureRow(Gesture.DOUBLE, "Crown double-press: next track", "ضغطتان على التاج: التالي",
                Exposure.AVRCP_TO_ACTIVE_PLAYER, MediaCommand.NEXT),
            GestureRow(Gesture.TRIPLE, "Crown triple-press: previous track", "ثلاث ضغطات: السابق",
                Exposure.AVRCP_TO_ACTIVE_PLAYER, MediaCommand.PREVIOUS),
            GestureRow(Gesture.LONG, "Crown press-and-hold: voice assistant", "ضغط مطوّل على التاج: المساعد الصوتي",
                Exposure.HFP_ASSISTANT, null, needsDeviceTest = true),
            GestureRow(Gesture.ROTATE, "Rotate crown: volume", "تدوير التاج: مستوى الصوت",
                Exposure.VOLUME_STREAM, MediaCommand.VOLUME_UP),
            GestureRow(Gesture.NOISE_BUTTON, "Noise-control button: ANC ↔ Transparency", "زر التحكم في الضوضاء: إلغاء ↔ الشفافية",
                Exposure.FIRMWARE_ONLY, null),
        )
    }

    /** Physical noise-control guidance; app-based switching requires AAP (unavailable, see docs). */
    fun noiseGuidance(model: AirPodsModel, arabic: Boolean): String? = when {
        !model.hasAnc -> null
        model.isOverEar -> if (arabic) "اضغط زر التحكم في الضوضاء على السماعة اليمنى للتبديل بين إلغاء الضوضاء والشفافية."
            else "Press the noise-control button on the right ear cup to switch between ANC and Transparency."
        else -> if (arabic) "اضغط مطولًا على ساق أي سماعة حتى تسمع النغمة للتبديل بين الأوضاع. الأوضاع المتاحة في الدورة (ومنها التكيفي) تُضبط من iPhone وتُحفظ داخل السماعات."
            else "Press and hold either stem until you hear the chime to cycle modes. Which modes are in the cycle (incl. Adaptive) is set from an iPhone and stored in the AirPods."
    }
}
