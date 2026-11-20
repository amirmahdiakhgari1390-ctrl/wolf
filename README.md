# Wolf AI — Android

دستیار صوتی فارسی برای اندروید (نسخه‌ی اندرویدی Wolf).

## اجرا
1. Android Studio (نسخه‌ی Koala یا جدیدتر) را باز کن ← Open ← پوشه‌ی `WolfAndroid`.
2. اگر Gradle خطا داد: File ▸ Settings ▸ Build ▸ Gradle و Gradle 8.7 با JDK 17 را انتخاب کن.
3. گوشی را با USB وصل کن (Developer options ▸ USB debugging) و Run بزن.
4. اجازه‌ها را بده (میکروفون، تماس، مخاطبین).
5. برای سؤال‌های عمومی: دکمه‌ی «تنظیمات: کلید جمینای» و کلید خودت را وارد کن.

## دستورهای پشتیبانی‌شده
باز کردن برنامه (مثلاً «تلگرام را باز کن»)، چراغ قوه، صدا، باتری، تماس با شماره یا اسم مخاطب،
آلارم، تایمر، یادداشت (نوشتن/خواندن)، تاریخ شمسی و ساعت، ماشین‌حساب، جستجوی گوگل،
پنل وای‌فای/بلوتوث. هر چیز دیگر → Gemini.

## ساختار
- `MainActivity.kt` رابط، گوش دادن (SpeechRecognizer) و صحبت (TextToSpeech)
- `CommandProcessor.kt` فهم دستور (معادل `process_command`)
- `PhoneActions.kt` کارهای روی گوشی
- `GeminiClient.kt` مغز ابری با حافظه‌ی گفتگو
- `Jalali.kt`, `Numbers.kt`, `Calc.kt` ابزارها
