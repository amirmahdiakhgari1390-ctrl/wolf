package com.wolf.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * فهم دستور (معادل process_command در wolf_backend.py).
 * دستورهای محلی فوری جواب می‌دهند؛ بقیه‌ی جمله‌ها به Gemini می‌روند.
 */
class CommandProcessor(
    @Suppress("unused") private val ctx: Context,
    private val phone: PhoneActions,
    private val gemini: GeminiClient
) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    companion object {
        val WAKE_WORDS = listOf("wolf", "wolfe", "woof", "wulf", "ولف", "گرگ", "jarvis", "جارویس", "جارویز")
    }

    // ------------------------------------------------------------------ ابزارهای متن
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (c in s.lowercase()) {
            sb.append(
                when (c) {
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    '\u200c' -> ' '
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
        return sb.toString().replace(Regex("[؟?!،؛,]"), " ").replace(Regex("\\s+"), " ").trim().trim('.', ' ')
    }

    fun stripWake(cmd: String): Pair<String, Boolean> {
        val tokens = cmd.split(" ")
        val kept = tokens.filter { it !in WAKE_WORDS }
        return Pair(kept.joinToString(" ").trim(), kept.size != tokens.size)
    }

    private fun has(cmd: String, vararg keys: String) = keys.any { cmd.contains(norm(it)) }
    private fun hasW(cmd: String, vararg keys: String): Boolean {
        val t = cmd.split(" ")
        return keys.any { norm(it) in t }
    }

    // ------------------------------------------------------------------ ورودی اصلی
    fun process(raw: String, onReply: (String) -> Unit) {
        val cmd = norm(raw)
        if (cmd.isBlank()) { onReply(""); return }
        val local = handleLocal(cmd)
        if (local != null) {
            onReply(local)
        } else {
            io.execute {
                val answer = gemini.ask(raw)
                main.post { onReply(answer) }
            }
        }
    }

    private fun handleLocal(cmd: String): String? {

        // --- پاک کردن حافظه‌ی گفتگو
        if (has(cmd, "گفتگو را فراموش کن", "گفتگو رو فراموش کن", "forget everything")) {
            gemini.clear(); return "حافظه‌ی گفتگو پاک شد."
        }

        // --- وای‌فای / بلوتوث (اندروید اجازه‌ی روشن/خاموش مستقیم نمی‌دهد؛ پنل باز می‌شود)
        if (has(cmd, "وای فای", "وایفای", "wifi", "wi-fi")) return phone.openWifiPanel()
        if (has(cmd, "بلوتوث", "bluetooth")) return phone.openBluetoothSettings()

        // --- چراغ قوه
        if (has(cmd, "چراغ قوه", "فلش", "flashlight", "torch")) {
            val off = has(cmd, "خاموش") || hasW(cmd, "off")
            val on = has(cmd, "روشن", "بزن") || hasW(cmd, "on")
            return phone.torch(if (off) false else if (on) true else null)
        }

        // --- صدا
        if (has(cmd, "صدا", "ولوم", "volume")) {
            if (has(cmd, "بی صدا", "قطع", "mute", "سایلنت")) return phone.volume(0)
            if (has(cmd, "زیاد", "بالا", "بلند", "افزایش", "increase") || hasW(cmd, "up")) return phone.volume(1)
            if (has(cmd, "کم", "پایین", "آهسته", "کاهش", "decrease") || hasW(cmd, "down")) return phone.volume(-1)
        }

        // --- باتری
        if (has(cmd, "باتری", "شارژ", "battery")) return phone.battery()

        // --- تماس
        val callKey = listOf("تماس بگیر با", "تماس بگیر", "زنگ بزن به", "زنگ بزن", "call").firstOrNull { has(cmd, it) }
        if (callKey != null) {
            val k = norm(callKey)
            val target = (cmd.substringAfter(k).ifBlank { cmd.substringBefore(k) })
                .replace(Regex("(^| )(با|به|شماره|لطفا)( |$)"), " ").trim()
            if (target.isEmpty()) return "قربان، با چه کسی تماس بگیرم؟"
            return phone.call(target)
        }

        // --- آلارم
        if (has(cmd, "آلارم", "الارم", "alarm", "بیدارم کن")) {
            val nums = Regex("\\d+").findAll(cmd).map { it.value.toInt() }.toList()
            var hour = nums.getOrNull(0) ?: Numbers.parse(cmd)?.toInt()
                ?: return "قربان، آلارم را برای چه ساعتی بگذارم؟"
            var minute = nums.getOrNull(1) ?: 0
            if (minute == 0 && has(cmd, "و نیم")) minute = 30
            if (hour < 12 && has(cmd, "عصر", "بعدازظهر", "بعد از ظهر", "شب") || (hour < 12 && hasW(cmd, "pm"))) hour += 12
            if (hour !in 0..23 || minute !in 0..59) return "قربان، این ساعت درست نیست."
            return phone.alarm(hour, minute)
        }

        // --- تایمر
        if (has(cmd, "تایمر", "زمان سنج", "timer", "یادآوری", "یادآور", "remind")) {
            val n = Numbers.parse(cmd) ?: return "قربان، تایمر را برای چند دقیقه بگذارم؟"
            val secs = when {
                has(cmd, "ثانیه", "second") -> n
                has(cmd, "ساعت", "hour") -> n * 3600
                else -> n * 60
            }
            val label = if (cmd.contains(" که ")) cmd.substringAfter(" که ").trim() else ""
            return phone.timer(secs.toInt(), label)
        }

        // --- یادداشت
        if (has(cmd, "یادداشت", "note")) {
            if (has(cmd, "بخون", "بخوان", "read", "چیه") || hasW(cmd, "ها")) return phone.readNotes()
            val key = listOf("یادداشت کن", "یادداشت", "note").first { has(cmd, it) }
            val k = norm(key)
            val content = cmd.substringAfter(k).ifBlank { cmd.substringBefore(k) }
                .replace(Regex("(^| )(را|رو|که)( |$)"), " ").trim()
            if (content.isEmpty()) return "قربان، چه چیزی را یادداشت کنم؟"
            return phone.saveNote(content)
        }

        // --- تاریخ و ساعت
        if (has(cmd, "تاریخ", "امروز چندم", "چندمه", "what day", "date")) return Jalali.todayText()
        if (has(cmd, "ساعت چنده", "ساعت چند", "ساعت الان", "what time", "the time") || cmd == "ساعت") {
            val now = Calendar.getInstance()
            val h = now.get(Calendar.HOUR_OF_DAY)
            val m = now.get(Calendar.MINUTE)
            return if (m == 0) "قربان، ساعت $h است." else "قربان، ساعت $h و $m دقیقه است."
        }

        // --- ماشین‌حساب
        var e = cmd
        listOf(
            "به علاوه" to "+", "بعلاوه" to "+", "plus" to "+",
            "منهای" to "-", "minus" to "-",
            "ضرب در" to "*", "ضربدر" to "*", "times" to "*", "×" to "*", " x " to "*",
            "تقسیم بر" to "/", "تقسیم" to "/", "divided by" to "/", "÷" to "/"
        ).forEach { (k, v) -> e = e.replace(k, " $v ") }
        e = e.replace(Regex("(حساب کن|محاسبه کن|چند میشه|چند می شه|چنده|برابر با|برابر|calculate|what is|=)"), " ").trim()
        if (Regex("[+\\-*/]").containsMatchIn(e) && e.matches(Regex("[\\d\\s+\\-*/().]+"))) {
            val v = Calc.eval(e)
            if (v == null || v.isNaN() || v.isInfinite()) return "قربان، این عبارت قابل محاسبه نیست."
            return "قربان، جواب ${Calc.format(v)} است."
        }

        // --- جستجو
        val searchKey = listOf("سرچ کن", "جستجو کن", "جستجو", "سرچ", "search for", "search").firstOrNull { has(cmd, it) }
        if (searchKey != null) {
            val k = norm(searchKey)
            val q = cmd.substringAfter(k).ifBlank { cmd.substringBefore(k) }.replace("درباره", " ").trim()
            if (q.isNotEmpty()) return phone.search(q)
        }

        // --- باز کردن برنامه
        val openKey = listOf("باز کن", "بازکن", "اجرا کن", "اجرا", "open", "launch").firstOrNull { has(cmd, it) }
        if (openKey != null) {
            val filler = Regex("(^| )(برنامه|اپ|اپلیکیشن|را|رو|لطفا|app|the|please|و)( |$)")
            var name = cmd.replace(norm(openKey), " ")
            repeat(2) { name = name.replace(filler, " ") }
            name = name.trim()
            return if (name.isEmpty()) "قربان، کدام برنامه را باز کنم؟" else phone.openApp(name)
        }

        // --- سلام و احوال‌پرسی (فقط جمله‌های کوتاه)
        if (cmd.split(" ").size <= 6) {
            if (has(cmd, "ممنون", "مرسی", "thank")) return "خواهش می‌کنم قربان."
            if (has(cmd, "تو کی", "اسمت چیه", "کی هستی", "who are you")) return "من ولف هستم، دستیار صوتی شما."
            if (has(cmd, "حالت چطوره", "چطوری", "how are you")) return "خوبم قربان، ممنون. در خدمتم."
            if (hasW(cmd, "سلام", "درود", "hello", "hi", "hey")) return "سلام قربان، در خدمتم."
        }

        return null   // → مغز ابری
    }
}
