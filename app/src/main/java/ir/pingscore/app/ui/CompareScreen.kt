package ir.pingscore.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pingscore.app.data.TestEntity
import ir.pingscore.app.data.report
import ir.pingscore.app.diag.TestReport

private class Row3(val label: String, val value: (TestEntity, TestReport) -> Double?, val higherIsBetter: Boolean, val decimals: Int)

@Composable
fun CompareScreen(
    ids: List<Long>,
    load: suspend (List<Long>) -> List<TestEntity>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<Pair<TestEntity, TestReport>>>(emptyList()) }
    LaunchedEffect(ids) {
        val byId = load(ids).associateBy { it.id }
        items = ids.mapNotNull { id -> byId[id]?.let { e -> e.report()?.let { r -> e to r } } }
            .sortedWith(compareByDescending<Pair<TestEntity, TestReport>> { it.first.isBaseline }.thenByDescending { it.first.timeMillis })
    }

    val rows = listOf(
        Row3("کیفیت اتصال", { e, _ -> e.overall.toDouble() }, true, 0),
        Row3("کار با هوش مصنوعی", { e, _ -> e.aiScore.toDouble() }, true, 0),
        Row3("ویدیو و دانلود", { e, _ -> e.videoScore.toDouble() }, true, 0),
        Row3("چت و تماس", { e, _ -> e.chatScore.toDouble() }, true, 0),
        Row3("ping (ms)", { _, r -> r.ping?.medianMs }, false, 0),
        Row3("jitter (ms)", { _, r -> r.ping?.jitterMs }, false, 1),
        Row3("packet loss (٪)", { _, r -> r.ping?.lossPercent }, false, 0),
        Row3("دانلود (Mbps)", { _, r -> r.speed?.downloadMbps }, true, 1),
        Row3("آپلود (Mbps)", { _, r -> r.speed?.uploadMbps }, true, 1),
        Row3("سایت‌های باز", { _, r -> r.sites.count { it.status == ir.pingscore.app.diag.SiteStatus.OPEN }.toDouble() }, true, 0),
    )
    val baseline = items.firstOrNull { it.first.isBaseline }

    AppScaffold(title = "مقایسه‌ی اتصال‌ها", onBack = onBack) {
        if (items.isEmpty()) {
            item { Text("در حال بارگذاری…") }
            return@AppScaffold
        }
        if (items.map { it.second.mode }.distinct().size > 1) {
            item {
                MessageCard("حالت این تست‌ها یکسان نیست (سریع/کامل)؛ برای مقایسه‌ی منصفانه، تست‌ها را در یک حالت بگیرید.")
            }
        }
        if (baseline == null) {
            item {
                Text(
                    "اتصال پایه‌ای در این مقایسه نیست؛ برای دیدن اختلاف با مبنا، یک تست را به‌عنوان اتصال پایه ثبت کنید.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(" ", style = MaterialTheme.typography.titleSmall)
                    Text(" ", style = MaterialTheme.typography.labelSmall)
                    rows.forEach { Text(it.label, style = MaterialTheme.typography.bodySmall) }
                }
                items.forEach { (e, r) ->
                    Column(modifier = Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(e.name + if (e.isBaseline) " ★" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${modeName(r.mode)} · ${formatTime(e.timeMillis).substring(5)}", style = MaterialTheme.typography.labelSmall)
                        rows.forEach { row ->
                            val v = row.value(e, r)
                            val base = baseline?.let { row.value(it.first, it.second) }
                            Text(cell(v, base, row, e.isBaseline), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { shareText(context, compareText(items)) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("خروجی متنی مقایسه") }
        }
    }
}

private fun cell(v: Double?, base: Double?, row: Row3, isBaseline: Boolean): String {
    if (v == null) return "—"
    val text = if (row.decimals == 0) String.format(java.util.Locale.US, "%.0f", v) else String.format(java.util.Locale.US, "%.1f", v)
    if (isBaseline || base == null) return text
    val d = v - base
    val better = if (row.higherIsBetter) d > 0 else d < 0
    val sign = if (d > 0) "+" else ""
    val dText = if (row.decimals == 0) String.format(java.util.Locale.US, "%.0f", d) else String.format(java.util.Locale.US, "%.1f", d)
    val mark = when {
        d == 0.0 -> ""
        better -> " ▲"
        else -> " ▼"
    }
    return "$text ($sign$dText)$mark"
}
