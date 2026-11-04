package com.profalikotb.airpods.core

/**
 * Apple audio models as identified by the 2-byte "device model" field of the
 * Apple Continuity "Proximity Pairing" BLE advertisement (type 0x07).
 *
 * ID provenance (community reverse engineering — NOT an Apple specification):
 *  - furiousMAC/continuity (Wireshark dissector, BSD-3):  https://github.com/furiousMAC/continuity
 *  - OpenPods (GPL-3, logic re-implemented here, no code copied): https://github.com/adolfintel/OpenPods
 *  - CAPod (GPL-3, model table cross-checked):            https://github.com/d4rken-org/capod
 *
 * [idConfidence] states how well the ID is established. Unknown IDs are surfaced as
 * [UNKNOWN] with the raw hex so the app never mislabels hardware.
 */
enum class IdConfidence { CROSS_CHECKED, SINGLE_SOURCE, UNKNOWN }

enum class FormFactor { EARBUDS_WITH_CASE, OVER_EAR }

enum class ControlStyle {
    /** AirPods 1/2: double-tap on the bud; action stored in bud firmware (set from an Apple device). */
    DOUBLE_TAP,
    /** AirPods 3, 4, Pro family: force sensor on the stem. */
    FORCE_SENSOR,
    /** AirPods Max: Digital Crown + noise-control button. */
    DIGITAL_CROWN
}

enum class AirPodsModel(
    val modelId: Int,
    val displayEn: String,
    val displayAr: String,
    val formFactor: FormFactor,
    val controls: ControlStyle,
    val hasAnc: Boolean,
    val hasSwipeVolume: Boolean,
    val idConfidence: IdConfidence,
) {
    AIRPODS_1(0x0220, "AirPods (1st gen)", "AirPods (الجيل الأول)", FormFactor.EARBUDS_WITH_CASE, ControlStyle.DOUBLE_TAP, false, false, IdConfidence.CROSS_CHECKED),
    AIRPODS_2(0x0F20, "AirPods (2nd gen)", "AirPods (الجيل الثاني)", FormFactor.EARBUDS_WITH_CASE, ControlStyle.DOUBLE_TAP, false, false, IdConfidence.CROSS_CHECKED),
    AIRPODS_3(0x1320, "AirPods (3rd gen)", "AirPods (الجيل الثالث)", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, false, false, IdConfidence.CROSS_CHECKED),
    AIRPODS_4(0x1920, "AirPods 4", "AirPods 4", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, false, false, IdConfidence.SINGLE_SOURCE),
    AIRPODS_4_ANC(0x1B20, "AirPods 4 (ANC)", "AirPods 4 (إلغاء الضوضاء)", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, true, false, IdConfidence.SINGLE_SOURCE),
    AIRPODS_PRO(0x0E20, "AirPods Pro", "AirPods Pro", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, true, false, IdConfidence.CROSS_CHECKED),
    AIRPODS_PRO_2(0x1420, "AirPods Pro 2 (Lightning)", "AirPods Pro 2 (Lightning)", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, true, true, IdConfidence.CROSS_CHECKED),
    AIRPODS_PRO_2_USBC(0x2420, "AirPods Pro 2 (USB-C)", "AirPods Pro 2 (USB-C)", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, true, true, IdConfidence.CROSS_CHECKED),
    AIRPODS_MAX(0x0A20, "AirPods Max (Lightning)", "AirPods Max (Lightning)", FormFactor.OVER_EAR, ControlStyle.DIGITAL_CROWN, true, false, IdConfidence.CROSS_CHECKED),
    AIRPODS_MAX_USBC(0x1F20, "AirPods Max (USB-C)", "AirPods Max (USB-C)", FormFactor.OVER_EAR, ControlStyle.DIGITAL_CROWN, true, false, IdConfidence.SINGLE_SOURCE),

    /**
     * Any 0x07 advertisement whose model ID is not in this table
     * (e.g. AirPods Pro 3, AirPods 5, AirPods Max 2, Beats). Battery nibbles are still shown,
     * labelled with the raw model ID, but model-specific gesture claims are withheld.
     */
    UNKNOWN(-1, "Unrecognised Apple audio device", "جهاز صوتي من Apple غير معروف", FormFactor.EARBUDS_WITH_CASE, ControlStyle.FORCE_SENSOR, false, false, IdConfidence.UNKNOWN);

    val isOverEar get() = formFactor == FormFactor.OVER_EAR

    fun display(arabic: Boolean) = if (arabic) displayAr else displayEn

    companion object {
        fun fromId(id: Int): AirPodsModel = entries.firstOrNull { it.modelId == id } ?: UNKNOWN
        fun hex(id: Int): String = "0x%04X".format(id and 0xFFFF)
    }
}
