package com.wolf.assistant

/** خواندن عدد از جمله: رقم (۵، 5) یا حروف («بیست و پنج»، «نیم») */
object Numbers {
    private val words: Map<String, Double> = mapOf(
        "صفر" to 0.0, "یک" to 1.0, "دو" to 2.0, "سه" to 3.0, "چهار" to 4.0, "پنج" to 5.0,
        "شش" to 6.0, "شیش" to 6.0, "هفت" to 7.0, "هشت" to 8.0, "نه" to 9.0, "ده" to 10.0,
        "یازده" to 11.0, "دوازده" to 12.0, "سیزده" to 13.0, "چهارده" to 14.0, "پانزده" to 15.0,
        "شانزده" to 16.0, "هفده" to 17.0, "هجده" to 18.0, "نوزده" to 19.0,
        "بیست" to 20.0, "سی" to 30.0, "چهل" to 40.0, "پنجاه" to 50.0, "شصت" to 60.0,
        "هفتاد" to 70.0, "هشتاد" to 80.0, "نود" to 90.0,
        "صد" to 100.0, "دویست" to 200.0, "سیصد" to 300.0, "چهارصد" to 400.0, "پانصد" to 500.0,
        "نیم" to 0.5,
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "fifteen" to 15.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0, "sixty" to 60.0
    )

    fun parse(cmd: String): Double? {
        Regex("\\d+(?:\\.\\d+)?").find(cmd)?.let { return it.value.toDouble() }
        var total = 0.0
        var found = false
        for (t in cmd.split(" ").filter { it.isNotBlank() }) {
            val v = words[t]
            if (v != null) {
                total += v
                found = true
            } else if (t == "و" && found) {
                continue
            } else if (found) {
                break
            }
        }
        return if (found) total else null
    }
}
