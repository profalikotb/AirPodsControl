package com.profalikotb.airpods.ui

import com.profalikotb.airpods.core.BatteryLevel
import com.profalikotb.airpods.core.Exposure
import com.profalikotb.airpods.core.Gesture
import com.profalikotb.airpods.core.MediaCommand
import com.profalikotb.airpods.data.Lang
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All user-facing text. Kotlin-side so language switches instantly and also covers notifications/widget. */
data class Str(
    val ar: Boolean,
    val appTitle: String, val tabBattery: String, val tabControls: String, val tabSettings: String,
    val left: String, val right: String, val case: String, val headphones: String,
    val charging: String, val unavailable: String, val lastKnown: String, val live: String,
    val connected: String, val disconnected: String, val btOff: String, val noDevice: String,
    val refresh: String, val scanning: String, val updated: String, val never: String,
    val stepNote: String, val ambiguous: String, val noReading: String, val modelUnconfirmed: String,
    val systemBattery: String, val systemBatteryNote: String,
    val selectAirPods: String, val chooseDevice: String, val noBonded: String, val pairInSettings: String,
    val openBtSettings: String, val grant: String, val permsTitle: String, val permsBody: String,
    val welcomeTitle: String, val welcomeBody: String, val start: String, val turnOnBt: String,
    val notifTitle: String, val notifBody: String,
    // controls
    val nativeFn: String, val androidEvent: String, val reassign: String, val notReassignable: String,
    val reassignWhy: String, val needsTest: String, val noiseTitle: String, val noiseApp: String,
    val probeTitle: String, val probeBody: String, val probeStart: String, val probeStop: String,
    val probeRunning: String, val probeBlockedCall: String, val observed: String, val notObserved: String,
    val modelUnknownControls: String,
    // settings
    val language: String, val theme: String, val sysDefault: String, val light: String, val dark: String,
    val lowNotif: String, val lowThreshold: String, val connNotif: String, val rssi: String, val rssiNote: String,
    val device: String, val change: String, val forget: String, val relock: String, val relockNote: String,
    val about: String, val aboutBody: String,
    // notifications
    val chLow: String, val chConn: String, val lowAlert: String, val criticalAlert: String,
) {
    fun level(l: BatteryLevel): String = when (l) {
        is BatteryLevel.Known -> when (l.tenths) { 0 -> "<10%"; else -> "~${l.approxPercent}%" }
        BatteryLevel.Unavailable -> unavailable
    }

    fun age(ms: Long): String {
        val m = ms / 60_000
        return when {
            m < 1 -> if (ar) "الآن" else "just now"
            m < 60 -> if (ar) "منذ $m د" else "$m min ago"
            else -> { val h = m / 60; if (ar) "منذ $h س" else "$h h ago" }
        }
    }

    /** dd/MM/yyyy · hh:mm a — user's standard. */
    fun stamp(at: Long): String =
        SimpleDateFormat("dd/MM/yyyy · hh:mm a", if (ar) Locale("ar", "EG") else Locale.ENGLISH).format(Date(at))

    fun gesture(g: Gesture) = when (g) {
        Gesture.SINGLE -> if (ar) "ضغطة واحدة" else "Single press"
        Gesture.DOUBLE -> if (ar) "ضغطتان" else "Double press / tap"
        Gesture.TRIPLE -> if (ar) "ثلاث ضغطات" else "Triple press"
        Gesture.LONG -> if (ar) "ضغط مطوّل" else "Press and hold"
        Gesture.SWIPE -> if (ar) "سحب للصوت" else "Swipe (volume)"
        Gesture.ROTATE -> if (ar) "تدوير التاج" else "Rotate crown"
        Gesture.NOISE_BUTTON -> if (ar) "زر الضوضاء" else "Noise-control button"
    }

    fun exposure(e: Exposure) = when (e) {
        Exposure.AVRCP_TO_ACTIVE_PLAYER -> if (ar) "نعم — يصل كأمر وسائط إلى تطبيق التشغيل النشط فقط" else "Yes — delivered as a media key to the active player app only"
        Exposure.VOLUME_STREAM -> if (ar) "نعم — كتغيير في مستوى صوت الوسائط" else "Yes — as a media-volume change"
        Exposure.FIRMWARE_ONLY -> if (ar) "لا — يُنفَّذ داخل السماعة" else "No — handled inside the earbuds"
        Exposure.HFP_ASSISTANT -> if (ar) "طلب مساعد صوتي عبر HFP — يحتاج اختبارًا على جهازك" else "Assistant request over HFP — needs testing on your phone"
        Exposure.FIRMWARE_CONFIGURED -> if (ar) "حسب الإعداد المخزّن في السماعة" else "Depends on the setting stored in the earbuds"
    }

    fun command(c: MediaCommand) = when (c) {
        MediaCommand.PLAY_PAUSE -> if (ar) "تشغيل/إيقاف" else "Play/Pause"
        MediaCommand.NEXT -> if (ar) "التالي" else "Next track"
        MediaCommand.PREVIOUS -> if (ar) "السابق" else "Previous track"
        MediaCommand.STOP -> if (ar) "إيقاف" else "Stop"
        MediaCommand.FAST_FORWARD -> if (ar) "تقديم" else "Fast-forward"
        MediaCommand.REWIND -> if (ar) "ترجيع" else "Rewind"
        MediaCommand.VOLUME_UP -> if (ar) "رفع الصوت" else "Volume up"
        MediaCommand.VOLUME_DOWN -> if (ar) "خفض الصوت" else "Volume down"
    }

    companion object {
        fun isArabic(lang: Lang) = when (lang) { Lang.AR -> true; Lang.EN -> false; Lang.SYSTEM -> Locale.getDefault().language == "ar" }
        fun of(lang: Lang): Str = if (isArabic(lang)) AR else EN

        val EN = Str(
            ar = false,
            appTitle = "AirPods Control", tabBattery = "Battery", tabControls = "Controls", tabSettings = "Settings",
            left = "Left", right = "Right", case = "Case", headphones = "Headphones",
            charging = "Charging", unavailable = "Unavailable", lastKnown = "Last known", live = "Live",
            connected = "Connected", disconnected = "Not connected", btOff = "Bluetooth is off", noDevice = "No AirPods selected",
            refresh = "Refresh", scanning = "Reading battery…", updated = "Updated", never = "No reading yet",
            stepNote = "AirPods broadcast battery in 10% steps.",
            ambiguous = "Other AirPods of the same model are nearby — readings paused to avoid showing theirs.",
            noReading = "No battery broadcast received. Open the case lid near the phone, or wear the AirPods, then refresh.",
            modelUnconfirmed = "Model not yet confirmed",
            systemBattery = "Android system reading", systemBatteryNote = "Combined value reported by the headset to Android.",
            selectAirPods = "Select your AirPods", chooseDevice = "Choose the AirPods paired with this phone",
            noBonded = "No paired AirPods found.", pairInSettings = "Pair them first: open the case lid, hold the back button until the light flashes white, then pair in Android Bluetooth settings.",
            openBtSettings = "Open Bluetooth settings", grant = "Grant permission",
            permsTitle = "Bluetooth permission needed",
            permsBody = "“Nearby devices” lets the app read the battery broadcast of your AirPods. Nothing leaves the phone.",
            welcomeTitle = "Welcome", welcomeBody = "Battery levels and controls for your AirPods on Android — offline, no account.",
            start = "Get started", turnOnBt = "Turn on Bluetooth",
            notifTitle = "Notifications (optional)", notifBody = "Allow notifications for low-battery and connection alerts.",
            nativeFn = "Built-in function", androidEvent = "Android receives event", reassign = "Reassignment",
            notReassignable = "Not available on Android",
            reassignWhy = "AirPods send these presses as standard media commands straight to the app that is playing. Android gives no third-party app a way to intercept and remap them without root, so the built-in functions are preserved.",
            needsTest = "Awaiting real-device test", noiseTitle = "Noise control",
            noiseApp = "Switching modes from the app needs Apple’s private AAP protocol, which Samsung’s Bluetooth stack blocks for non-root apps. Use the physical control:",
            probeTitle = "Test my gestures",
            probeBody = "Verifies which presses actually reach Android on this phone. Music pauses during the test; calls are never touched. Perform each gesture once.",
            probeStart = "Start 60-second test", probeStop = "Stop test", probeRunning = "Listening… perform a gesture",
            probeBlockedCall = "Unavailable during a call.", observed = "Observed", notObserved = "Not yet observed",
            modelUnknownControls = "Model not recognised — exact gesture map withheld. Use the test below to see what this phone receives.",
            language = "Language", theme = "Theme", sysDefault = "System", light = "Light", dark = "Dark",
            lowNotif = "Low-battery notifications", lowThreshold = "Alert at", connNotif = "Connection notification with battery",
            rssi = "Signal threshold", rssiNote = "Higher (e.g. −55) rejects more nearby AirPods; lower accepts readings from farther away.",
            device = "Selected AirPods", change = "Change", forget = "Forget", relock = "Re-detect model",
            relockNote = "Use if the shown model is wrong.",
            about = "About", aboutBody = "Prof Ali Kotb — AirPods Control. Battery data: Apple BLE proximity broadcast (decoded on-device). No root, no account, no network.",
            chLow = "Low battery", chConn = "AirPods connection", lowAlert = "battery low", criticalAlert = "battery critically low",
        )

        val AR = Str(
            ar = true,
            appTitle = "تحكم AirPods", tabBattery = "البطارية", tabControls = "التحكم", tabSettings = "الإعدادات",
            left = "اليسرى", right = "اليمنى", case = "العلبة", headphones = "السماعة",
            charging = "يشحن", unavailable = "غير متاح", lastKnown = "آخر قراءة", live = "مباشر",
            connected = "متصلة", disconnected = "غير متصلة", btOff = "البلوتوث مغلق", noDevice = "لم تُختر سماعة",
            refresh = "تحديث", scanning = "جارٍ قراءة البطارية…", updated = "آخر تحديث", never = "لا توجد قراءة بعد",
            stepNote = "تبث AirPods مستوى البطارية بخطوات 10٪.",
            ambiguous = "توجد سماعات AirPods أخرى من نفس الطراز بالقرب — أُوقفت القراءات حتى لا تظهر بياناتها.",
            noReading = "لم يُستقبل بث البطارية. افتح غطاء العلبة بجوار الهاتف أو ارتدِ السماعات ثم حدّث.",
            modelUnconfirmed = "لم يُؤكد الطراز بعد",
            systemBattery = "قراءة نظام Android", systemBatteryNote = "قيمة مجمّعة ترسلها السماعة إلى Android.",
            selectAirPods = "اختر سماعاتك", chooseDevice = "اختر سماعات AirPods المقترنة بهذا الهاتف",
            noBonded = "لا توجد سماعات AirPods مقترنة.", pairInSettings = "اقترن أولًا: افتح غطاء العلبة واضغط الزر الخلفي حتى يومض الضوء بالأبيض، ثم اقترن من إعدادات بلوتوث Android.",
            openBtSettings = "فتح إعدادات البلوتوث", grant = "منح الإذن",
            permsTitle = "مطلوب إذن البلوتوث",
            permsBody = "إذن «الأجهزة المجاورة» يسمح بقراءة بث بطارية سماعاتك. لا تغادر أي بيانات الهاتف.",
            welcomeTitle = "مرحبًا", welcomeBody = "مستويات البطارية والتحكم لسماعات AirPods على Android — دون إنترنت ودون حساب.",
            start = "ابدأ", turnOnBt = "تشغيل البلوتوث",
            notifTitle = "الإشعارات (اختياري)", notifBody = "اسمح بالإشعارات لتنبيهات البطارية المنخفضة والاتصال.",
            nativeFn = "الوظيفة المدمجة", androidEvent = "هل يستقبل Android الحدث", reassign = "إعادة التعيين",
            notReassignable = "غير متاحة على Android",
            reassignWhy = "ترسل AirPods هذه الضغطات كأوامر وسائط قياسية مباشرة إلى تطبيق التشغيل النشط. لا يتيح Android لأي تطبيق خارجي اعتراضها وإعادة تعيينها دون صلاحيات root، لذلك تبقى الوظائف المدمجة كما هي.",
            needsTest = "بانتظار اختبار على جهاز حقيقي", noiseTitle = "التحكم في الضوضاء",
            noiseApp = "تبديل الأوضاع من التطبيق يتطلب بروتوكول AAP الخاص بـ Apple، ونظام بلوتوث Samsung يمنعه للتطبيقات دون root. استخدم التحكم الفعلي:",
            probeTitle = "اختبر إيماءاتي",
            probeBody = "يتحقق من الضغطات التي تصل فعلًا إلى Android على هذا الهاتف. تتوقف الموسيقى أثناء الاختبار ولا تتأثر المكالمات. نفّذ كل إيماءة مرة واحدة.",
            probeStart = "بدء اختبار 60 ثانية", probeStop = "إيقاف الاختبار", probeRunning = "جارٍ الاستماع… نفّذ إيماءة",
            probeBlockedCall = "غير متاح أثناء المكالمة.", observed = "تم رصده", notObserved = "لم يُرصد بعد",
            modelUnknownControls = "الطراز غير معروف — لن تُعرض خريطة إيماءات قد تكون خاطئة. استخدم الاختبار أدناه لمعرفة ما يستقبله الهاتف.",
            language = "اللغة", theme = "المظهر", sysDefault = "النظام", light = "فاتح", dark = "داكن",
            lowNotif = "تنبيهات انخفاض البطارية", lowThreshold = "التنبيه عند", connNotif = "إشعار الاتصال مع البطارية",
            rssi = "حد قوة الإشارة", rssiNote = "قيمة أعلى (مثل −55) ترفض سماعات مجاورة أكثر؛ وأقل تقبل قراءات من مسافة أبعد.",
            device = "السماعة المختارة", change = "تغيير", forget = "إزالة", relock = "إعادة اكتشاف الطراز",
            relockNote = "استخدمه إذا كان الطراز المعروض خاطئًا.",
            about = "حول التطبيق", aboutBody = "أ.د. على قطب — تحكم AirPods. بيانات البطارية من بث Apple عبر BLE وتُفك على الجهاز. دون root ودون حساب ودون إنترنت.",
            chLow = "بطارية منخفضة", chConn = "اتصال AirPods", lowAlert = "البطارية منخفضة", criticalAlert = "البطارية منخفضة جدًا",
        )
    }
}
