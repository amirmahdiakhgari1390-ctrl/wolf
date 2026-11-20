package com.wolf.assistant

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * مغز ابری (Gemini) با حافظه‌ی گفتگو. کلید API از تنظیمات برنامه خوانده می‌شود،
 * نه از داخل کد. این کلاس باید روی thread پس‌زمینه صدا زده شود.
 */
class GeminiClient(private val prefs: SharedPreferences) {

    private val models = listOf(
        "gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-3.1-flash-lite", "gemini-flash-latest"
    )
    private val maxHistory = 20
    private val history = ArrayList<Pair<String, String>>()   // (role, text)

    private val systemPrompt =
        "تو «ولف» هستی؛ یک دستیار صوتی بسیار باهوش و مؤدب. جواب تو بلند خوانده می‌شود، " +
        "پس طبیعی و گفتگومحور و بدون فهرست، تیتر یا علائم مارک‌داون جواب بده. معمولاً حداکثر چهار جمله؛ " +
        "فقط اگر کاربر توضیح مفصل خواست بیشتر. به زبان کاربر جواب بده و او را «قربان» خطاب کن. " +
        "اگر چیزی را نمی‌دانی صادقانه بگو."

    @Synchronized
    fun clear() = history.clear()

    @Synchronized
    fun ask(text: String): String {
        val key = prefs.getString("gemini_key", "").orEmpty().trim()
        if (key.isEmpty()) {
            return "قربان، برای جواب به سؤال‌های عمومی باید کلید جمینای را از دکمه‌ی تنظیمات وارد کنید."
        }
        var lastErr = ""
        for (m in models) {
            try {
                val answer = call(m, key, text).replace(Regex("[*#`_]"), "").trim()
                if (answer.isNotEmpty()) {
                    history.add("user" to text)
                    history.add("model" to answer)
                    while (history.size > maxHistory) history.removeAt(0)
                    return answer
                }
            } catch (e: Exception) {
                lastErr = e.message ?: e.javaClass.simpleName
            }
        }
        return "قربان، اتصال به مغز ابری برقرار نشد. $lastErr"
    }

    private fun call(model: String, key: String, text: String): String {
        val contents = JSONArray()
        for ((role, t) in history) contents.put(turn(role, t))
        contents.put(turn("user", text))

        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)

        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun turn(role: String, text: String) = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))
}
