package ir.pingscore.app.diag

import kotlin.math.abs

/** میانه؛ برای فهرست خالی null برمی‌گرداند. */
fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
}

fun medianLong(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
}

/** jitter = میانگین اختلاف مطلق نمونه‌های متوالی؛ با کمتر از ۲ نمونه null. */
fun jitter(rtts: List<Double>): Double? {
    if (rtts.size < 2) return null
    var sum = 0.0
    for (i in 1 until rtts.size) sum += abs(rtts[i] - rtts[i - 1])
    return sum / (rtts.size - 1)
}
