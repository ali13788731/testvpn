package ir.pingscore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.pingscore.app.data.TestEntity
import ir.pingscore.app.data.report

/** صفحه‌ی نتیجه: نمره‌ها با جزئیات مؤلفه‌ها، سایت‌ها، DNS و عملیات (نام‌گذاری، مبنا، خروجی متن). */
@Composable
fun DetailScreen(
    entity: TestEntity?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onSetBaseline: () -> Unit,
    onClearBaseline: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val report = entity?.report()

    AppScaffold(title = "نتیجه‌ی تست", onBack = onBack) {
        if (entity == null || report == null) {
            item { Text("این نتیجه پیدا نشد.") }
            return@AppScaffold
        }
        item {
            Text(entity.name + if (entity.isBaseline) "  ★ اتصال پایه" else "", style = MaterialTheme.typography.titleLarge)
            Text(
                "${formatTime(entity.timeMillis)} · حالت ${modeName(entity.mode)} · ${sourceName(report.source)} · ${report.connection.label}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { ScoreSummary(report.score, showBreakdown = true) }
        item { MetricsCard(report.ping, report.speed) }
        item { SitesCard(report.sites) }
        item { DnsCard(report.dns) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { renaming = true }, modifier = Modifier.weight(1f)) { Text("نام‌گذاری") }
                OutlinedButton(
                    onClick = { shareText(context, reportText(entity, report)) },
                    modifier = Modifier.weight(1f),
                ) { Text("خروجی متنی") }
            }
        }
        item {
            if (entity.isBaseline) {
                OutlinedButton(onClick = onClearBaseline, modifier = Modifier.fillMaxWidth()) { Text("حذف از اتصال پایه") }
            } else {
                Button(onClick = onSetBaseline, modifier = Modifier.fillMaxWidth()) { Text("ثبت به‌عنوان اتصال پایه (مبنا)") }
            }
        }
        item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف این نتیجه") }
        }
    }

    if (renaming && entity != null) {
        var text by remember(entity.id) { mutableStateOf(entity.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("نام اتصال") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) onRename(text)
                    renaming = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("انصراف") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف نتیجه؟") },
            text = { Text("این نتیجه از حافظه‌ی گوشی پاک می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } },
        )
    }
}
