package com.wolf.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** همه‌ی کارهایی که روی خود گوشی انجام می‌شود (معادل بخش‌های ویندوزی کد پایتون) */
class PhoneActions(private val ctx: Context) {

    private var torchOn = false

    private data class AppInfo(val label: String, val pkg: String)

    companion object {
        // اسم فارسی → بخشی از اسم انگلیسی برنامه یا پکیج
        private val ALIASES = mapOf(
            "واتساپ" to "whatsapp", "واتس اپ" to "whatsapp", "تلگرام" to "telegram",
            "اینستاگرام" to "instagram", "اینستا" to "instagram", "کروم" to "chrome",
            "یوتیوب" to "youtube", "دوربین" to "camera", "تنظیمات" to "settings",
            "نقشه" to "maps", "گالری" to "photos", "مخاطبین" to "contacts",
            "ماشین حساب" to "calculator", "ساعت" to "clock", "جیمیل" to "gmail",
            "گوگل" to "google", "اسپاتیفای" to "spotify", "بله" to "bale", "ایتا" to "eitaa",
            "روبیکا" to "rubika", "دیوار" to "divar", "اسنپ" to "snapp", "تپسی" to "tapsi",
            "پلی استور" to "vending", "فایل" to "files", "شماره گیر" to "dialer",
            "تلفن" to "dialer", "پیامک" to "messaging", "پیام" to "messaging"
        )
    }

    private fun fold(s: String) =
        s.lowercase(Locale.ROOT).replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک').trim()

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ برنامه‌ها
    private fun installedApps(): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map {
            AppInfo(fold(it.loadLabel(pm).toString()), it.activityInfo.packageName)
        }
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return 1.0 - dp[a.length][b.length].toDouble() / maxOf(a.length, b.length)
    }

    private fun findApp(name: String): AppInfo? {
        val n = fold(name)
        val apps = installedApps()
        apps.firstOrNull { it.label == n }?.let { return it }
        val target = ALIASES[n] ?: n
        apps.firstOrNull { it.label.contains(target) || it.pkg.lowercase(Locale.ROOT).contains(target) }?.let { return it }
        apps.firstOrNull { it.label.contains(n) }?.let { return it }
        return apps.map { it to similarity(it.label, n) }
            .filter { it.second >= 0.7 }
            .maxByOrNull { it.second }?.first
    }

    fun openApp(name: String): String {
        val app = findApp(name) ?: return "قربان، برنامه‌ای به نام $name پیدا نکردم."
        val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
            ?: return "قربان، نتوانستم ${app.label} را باز کنم."
        return if (launch(intent)) "در حال باز کردن ${app.label}" else "قربان، نتوانستم ${app.label} را باز کنم."
    }

    // ------------------------------------------------------------------ سخت‌افزار
    fun torch(on: Boolean?): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "قربان، این گوشی چراغ قوه ندارد."
            torchOn = on ?: !torchOn
            cm.setTorchMode(id, torchOn)
            if (torchOn) "چراغ قوه روشن شد." else "چراغ قوه خاموش شد."
        } catch (e: Exception) {
            "قربان، نتوانستم چراغ قوه را کنترل کنم."
        }
    }

    fun volume(direction: Int): String {   // 1 بالا ، -1 پایین ، 0 بی‌صدا
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (direction) {
            1 -> { repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }; "صدا زیاد شد." }
            -1 -> { repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }; "صدا کم شد." }
            else -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); "صدا قطع شد." }
        }
    }

    fun battery(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (level < 0) return "قربان، نتوانستم وضعیت باتری را بخوانم."
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return "قربان، باتری ${level * 100 / scale} درصد است" + if (charging) " و در حال شارژ است." else "."
    }

    fun openWifiPanel(): String =
        if (launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))) "پنل اینترنت باز شد." else "نتوانستم تنظیمات را باز کنم."

    fun openBluetoothSettings(): String =
        if (launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) "تنظیمات بلوتوث باز شد." else "نتوانستم تنظیمات را باز کنم."

    // ------------------------------------------------------------------ تماس
    private fun findContact(name: String): Pair<String, String>? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )
        c?.use { if (it.moveToFirst()) return Pair(it.getString(0), it.getString(1)) }
        return null
    }

    fun call(target: String): String {
        val digits = target.replace(" ", "")
        val number: String
        val label: String
        if (digits.length >= 5 && digits.all { it.isDigit() || it == '+' }) {
            number = digits; label = digits
        } else {
            val found = findContact(target.trim())
                ?: return "قربان، مخاطبی به نام ${target.trim()} پیدا نکردم (یا اجازه‌ی مخاطبین داده نشده)."
            label = found.first; number = found.second
        }
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val action = if (granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return if (launch(Intent(action, uri))) "در حال تماس با $label" else "قربان، نتوانستم تماس بگیرم."
    }

    // ------------------------------------------------------------------ زمان
    fun timer(seconds: Int, label: String): String {
        val s = seconds.coerceIn(1, 86400)
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (launch(i)) "تایمر تنظیم شد." else "قربان، نتوانستم تایمر بگذارم."
    }

    fun alarm(hour: Int, minute: Int): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) "آلارم برای ساعت $hour و $minute دقیقه تنظیم شد." else "قربان، نتوانستم آلارم بگذارم."
    }

    // ------------------------------------------------------------------ جستجو و یادداشت
    fun search(q: String): String {
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
        return if (launch(Intent(Intent.ACTION_VIEW, uri))) "نتیجه‌ی جستجو باز شد." else "قربان، نتوانستم مرورگر را باز کنم."
    }

    private val notesFile get() = File(ctx.filesDir, "notes.txt")

    fun saveNote(text: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        notesFile.appendText("[$stamp] $text\n")
        return "یادداشت شد."
    }

    fun readNotes(): String {
        if (!notesFile.exists()) return "قربان، هنوز یادداشتی ندارید."
        val last = notesFile.readLines().filter { it.isNotBlank() }.takeLast(5)
        if (last.isEmpty()) return "قربان، هنوز یادداشتی ندارید."
        return "آخرین یادداشت‌ها: " + last.joinToString(" . ") { it.substringAfter("] ") }
    }
}
