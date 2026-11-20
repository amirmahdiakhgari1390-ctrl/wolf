package com.wolf.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var processor: CommandProcessor

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private var listening = false     // کاربر دکمه‌ی میکروفون را روشن کرده
    private var speaking = false      // ولف در حال حرف زدن است
    private var activeUntil = 0L      // بعد از صدا زدن، تا این لحظه بدون تکرار اسم گوش می‌دهد
    private val activeWindowMs = 25_000L
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var micBtn: Button

    // ------------------------------------------------------------------ چرخه‌ی عمر
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("wolf", Context.MODE_PRIVATE)
        processor = CommandProcessor(this, PhoneActions(this), GeminiClient(prefs))
        buildUi()
        initTts()
        if (!granted(Manifest.permission.RECORD_AUDIO)) askPermissions()
    }

    override fun onStop() {
        super.onStop()
        if (listening) stopListening("متوقف شد (برنامه در پس‌زمینه رفت)")
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        tts?.shutdown()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS),
            1
        )
    }

    // ------------------------------------------------------------------ رابط کاربری
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0F1A"))
            setPadding(dp(20), dp(40), dp(20), dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val title = TextView(this).apply {
            text = "WOLF AI"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4FC3F7"))
            gravity = Gravity.CENTER
        }
        statusView = TextView(this).apply {
            text = "آماده"
            textSize = 14f
            setTextColor(Color.parseColor("#90A4AE"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }
        logView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.2f)
        }
        scroll = ScrollView(this).apply { addView(logView) }
        micBtn = Button(this).apply {
            text = "🎤  شروع گوش دادن"
            textSize = 18f
            setOnClickListener { toggleListening() }
        }
        val wake = CheckBox(this).apply {
            text = "فقط وقتی بگویم «ولف»"
            setTextColor(Color.parseColor("#B0BEC5"))
            isChecked = prefs.getBoolean("need_wake", false)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("need_wake", c).apply() }
        }
        val keyBtn = Button(this).apply {
            text = "تنظیمات: کلید جمینای"
            setOnClickListener { showKeyDialog() }
        }
        root.addView(title)
        root.addView(statusView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(wake)
        root.addView(micBtn)
        root.addView(keyBtn)
        setContentView(root)
    }

    private fun log(line: String) {
        logView.append(line + "\n\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setStatus(s: String) { statusView.text = s }

    private fun showKeyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Gemini API key"
            setText(prefs.getString("gemini_key", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("کلید API جمینای")
            .setMessage("از Google AI Studio بگیرید. فقط روی همین گوشی ذخیره می‌شود.")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit().putString("gemini_key", input.text.toString().trim()).apply()
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    // ------------------------------------------------------------------ صدا بیرون (TTS)
    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                log("موتور تبدیل متن به گفتار در دسترس نیست.")
                return@TextToSpeech
            }
            val r = tts?.setLanguage(Locale.forLanguageTag("fa-IR"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                log("⚠️ صدای فارسی روی این گوشی نصب نیست. در تنظیمات گوشی ← مدیریت کلی ← تبدیل متن به گفتار، صدای فارسی را نصب کنید.")
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { handler.post { speaking = false; resumeListening() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { handler.post { speaking = false; resumeListening() } }
            })
            ttsReady = true
        }
    }

    private fun speak(text: String) {
        if (text.isBlank() || !ttsReady) { speaking = false; resumeListening(); return }
        speaking = true
        setStatus("در حال صحبت…")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wolf")
    }

    // ------------------------------------------------------------------ گوش دادن (SpeechRecognizer)
    private fun toggleListening() {
        if (listening) { stopListening("متوقف شد"); return }
        if (!granted(Manifest.permission.RECORD_AUDIO)) { askPermissions(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "تشخیص گفتار روی این گوشی نصب نیست (Google app لازم است)", Toast.LENGTH_LONG).show()
            return
        }
        listening = true
        micBtn.text = "⏹  توقف"
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        }
        startRecognizer()
    }

    private fun stopListening(msg: String) {
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        tts?.stop()
        speaking = false
        micBtn.text = "🎤  شروع گوش دادن"
        setStatus(msg)
    }

    private fun startRecognizer() {
        if (!listening || speaking) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(i)
    }

    private fun resumeListening(delayMs: Long = 300) {
        if (!listening || speaking) return
        handler.postDelayed({ startRecognizer() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { setStatus("گوش می‌دهم…") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { setStatus("… $it") }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) { resumeListening(); return }
            handleSpeech(text)
        }

        override fun onError(error: Int) {
            if (!listening) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stopListening("اجازه‌ی میکروفون داده نشده"); askPermissions()
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> resumeListening(150)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { recognizer?.cancel(); resumeListening(800) }
                else -> resumeListening(1000)   // شبکه و خطاهای دیگر: کمی صبر و دوباره
            }
        }
    }

    // ------------------------------------------------------------------ پردازش دستور
    private fun handleSpeech(text: String) {
        val (body, heard) = processor.stripWake(processor.norm(text))
        val now = SystemClock.elapsedRealtime()
        val needWake = prefs.getBoolean("need_wake", false)
        if (needWake && !heard && now > activeUntil) { resumeListening(); return }
        if (heard) activeUntil = now + activeWindowMs

        log("شما: $text")
        if (body.isBlank()) { respond("بله قربان؟"); return }
        setStatus("در حال فکر کردن…")
        processor.process(body) { reply -> respond(reply) }
    }

    private fun respond(reply: String) {
        if (reply.isNotBlank()) log("ولف: $reply")
        speak(reply)
    }
}
