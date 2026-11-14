package ir.pingscore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.pingscore.app.MainUiState
import ir.pingscore.app.NetworkEvent
import ir.pingscore.app.Screen
import ir.pingscore.app.data.TestEntity
import ir.pingscore.app.data.TestPipeline
import ir.pingscore.app.data.report
import ir.pingscore.app.diag.TestMode
import ir.pingscore.app.network.NetworkSnapshot

@Composable
fun MainScreen(
    state: MainUiState,
    last: TestEntity?,
    actions: MainActions,
) {
    AppScaffold(
        title = "PingScore",
        actions = {
            TextButton(onClick = { actions.navigate(Screen.History) }) { Text("تاریخچه") }
            TextButton(onClick = { actions.navigate(Screen.Settings) }) { Text("تنظیمات") }
        },
    ) {
        item { StatusCard(state.current) }
        item { DetailsCard(state.current, state.previous) }
        item { TestControls(state, actions) }
        state.message?.let { item { MessageCard(it) } }

        val report = last?.report()
        if (last != null && report != null) {
            item {
                Text(
                    "آخرین نتیجه — ${last.name} · ${formatTime(last.timeMillis)} · ${modeName(last.mode)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item { ScoreSummary(report.score) }
            item {
                OutlinedButton(
                    onClick = { actions.navigate(Screen.Detail(last.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("جزئیات و نام‌گذاری") }
            }
            item { MetricsCard(report.ping, report.speed) }
            item { SitesCard(report.sites) }
            item { DnsCard(report.dns) }
        } else if (state.progress == null) {
            item {
                Text(
                    "هنوز تستی انجام نشده است. برای سنجش کیفیت اتصال فعلی، دکمه‌ی تست را بزنید.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { Text("تغییرهای اخیر اتصال", style = MaterialTheme.typography.titleMedium) }
        if (state.events.isEmpty()) {
            item { Text("هنوز رویدادی ثبت نشده است.") }
        } else {
            state.events.forEach { e -> item { EventRow(e) } }
        }
    }
}

class MainActions(
    val navigate: (Screen) -> Unit,
    val startTest: () -> Unit,
    val cancelTest: () -> Unit,
    val setMode: (TestMode) -> Unit,
    val setName: (String) -> Unit,
)

@Composable
private fun TestControls(state: MainUiState, actions: MainActions) {
    val running = state.progress != null
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == TestMode.QUICK,
                    onClick = { actions.setMode(TestMode.QUICK) },
                    enabled = !running,
                    label = { Text("سریع (کم‌حجم)") },
                )
                FilterChip(
                    selected = state.mode == TestMode.FULL,
                    onClick = { actions.setMode(TestMode.FULL) },
                    enabled = !running,
                    label = { Text("کامل (دقیق‌تر)") },
                )
            }
            OutlinedTextField(
                value = state.connectionName,
                onValueChange = actions.setName,
                enabled = !running,
                singleLine = true,
                label = { Text("نام اتصال فعلی (اختیاری)") },
                modifier = Modifier.fillMaxWidth(),
            )
            val p = state.progress
            if (p != null) {
                Text(stageName(p.stage), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                if (p.liveMbps != null) {
                    Text("${fmt1(p.liveMbps)} Mbps", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = actions.cancelTest, modifier = Modifier.fillMaxWidth()) { Text("لغو تست") }
            } else {
                Button(
                    onClick = actions.startTest,
                    enabled = state.current.isConnected,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.current.isConnected) "شروع تست کیفیت اتصال" else "اتصالی برقرار نیست") }
                if (state.mode == TestMode.FULL && state.current.isMetered) {
                    Text(
                        "توجه: اتصال شما حجمی است و تست کامل چند ده مگابایت مصرف می‌کند.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(snapshot: NetworkSnapshot) {
    val title = when {
        !snapshot.isConnected -> "متصل نیستید"
        !snapshot.isValidated -> "متصل، ولی اینترنت تأیید نشده"
        else -> "متصل"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (snapshot.isConnected && snapshot.isValidated)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun DetailsCard(current: NetworkSnapshot, previous: NetworkSnapshot?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabelValue("اتصال فعلی", TestPipeline.describe(current))
            LabelValue("اتصال قبلی", previous?.let { TestPipeline.describe(it) } ?: "—")
            LabelValue("نوع مصرف", if (current.isMetered) "حجمی" else "نامحدود")
            LabelValue(
                "برآورد سیستم (دانلود)",
                if (current.downstreamKbps > 0) "${current.downstreamKbps / 1000} Mbps" else "—",
            )
        }
    }
}

@Composable
private fun EventRow(event: NetworkEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(TestPipeline.describe(event.snapshot), style = MaterialTheme.typography.bodyMedium)
            Text(formatClock(event.timeMillis), style = MaterialTheme.typography.bodySmall)
        }
    }
}
