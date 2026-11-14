package ir.pingscore.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pingscore.app.data.TestEntity

@Composable
fun HistoryScreen(
    tests: List<TestEntity>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCompare: (List<Long>) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmClear by remember { mutableStateOf(false) }

    AppScaffold(
        title = "تاریخچه",
        onBack = onBack,
        actions = {
            if (tests.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("پاک‌کردن همه") }
        },
    ) {
        item { SectionCard("روند نمره‌ها") { ScoreChart(tests) } }

        val baseline = tests.firstOrNull { it.isBaseline }
        item {
            Text(
                if (baseline != null) "اتصال پایه: ${baseline.name} (${baseline.overall})"
                else "هنوز اتصال پایه‌ای ثبت نشده. تست را روی اتصال معمولی خود انجام دهید و در صفحه‌ی جزئیات «ثبت به‌عنوان اتصال پایه» را بزنید.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Button(
                onClick = {
                    // اتصال پایه همیشه همراه انتخاب‌ها مقایسه می‌شود
                    val ids = (listOfNotNull(baseline?.id) + selected).distinct()
                    onCompare(ids)
                },
                enabled = (selected + listOfNotNull(baseline?.id)).size >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("مقایسه‌ی ${selected.size} مورد انتخاب‌شده") }
        }

        if (tests.isEmpty()) {
            item { Text("هنوز نتیجه‌ای ذخیره نشده است.") }
        }
        tests.forEach { t ->
            item(key = t.id) {
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(t.id) }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = t.id in selected,
                            onCheckedChange = { on -> selected = if (on && selected.size < 4) selected + t.id else selected - t.id },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.name + if (t.isBaseline) "  ★" else "", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${formatTime(t.timeMillis)} · ${modeName(t.mode)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "هوش مصنوعی ${t.aiScore} · ویدیو ${t.videoScore} · چت ${t.chatScore}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("${t.overall}", fontWeight = FontWeight.Bold, color = scoreColor(t.overall), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک‌کردن تاریخچه؟") },
            text = { Text("همه‌ی نتایج (حتی اتصال پایه) از گوشی پاک می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    selected = emptySet()
                    onDeleteAll()
                }) { Text("پاک شود") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } },
        )
    }
}
