package ir.pingscore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pingscore.app.diag.DnsResult
import ir.pingscore.app.diag.PingStats
import ir.pingscore.app.diag.SiteResult
import ir.pingscore.app.diag.SpeedStats
import ir.pingscore.app.score.ScoreEngine
import ir.pingscore.app.score.ScoreReport
import ir.pingscore.app.score.UseCaseScore

/** نمره‌ی کل و سه نمره‌ی کاربردها. */
@Composable
fun ScoreSummary(score: ScoreReport, showBreakdown: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "${score.overall}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = labelColor(score.overallLabel),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("کیفیت اتصال: ${ScoreEngine.labelName(score.overallLabel)}", style = MaterialTheme.typography.titleMedium)
                    Text(score.overallSummary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        score.useCases.forEach { UseCaseCard(it, showBreakdown) }
    }
}

@Composable
private fun UseCaseCard(u: UseCaseScore, showBreakdown: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ScoreEngine.useCaseName(u.useCase), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${u.score} — ${ScoreEngine.labelName(u.label)}",
                    color = labelColor(u.label),
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { u.score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = labelColor(u.label),
            )
            Text(u.summary, style = MaterialTheme.typography.bodySmall)
            if (showBreakdown) {
                u.components.forEach { c ->
                    LabelValue(
                        "${ScoreEngine.componentName(c.component)} (وزن ${(c.weight * 100).toInt()}٪)",
                        c.score?.toString() ?: "اندازه‌گیری نشد",
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsCard(ping: PingStats?, speed: SpeedStats?) {
    SectionCard("تأخیر و سرعت") {
        if (ping != null) {
            LabelValue("ping (میانه)", if (ping.medianMs == null) "—" else "${fmt0(ping.medianMs)} ms")
            LabelValue("jitter", if (ping.jitterMs == null) "—" else "${fmt1(ping.jitterMs)} ms")
            LabelValue("packet loss", "${fmt0(ping.lossPercent)}٪  (${ping.received}/${ping.sent})")
        } else {
            Text("تأخیر اندازه‌گیری نشد.", style = MaterialTheme.typography.bodySmall)
        }
        if (speed != null) {
            LabelValue("سرعت دانلود", if (speed.downloadMbps == null) "—" else "${fmt1(speed.downloadMbps)} Mbps")
            LabelValue("سرعت آپلود", if (speed.uploadMbps == null) "—" else "${fmt1(speed.uploadMbps)} Mbps")
        } else {
            Text("تست سرعت انجام نشده است.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SitesCard(sites: List<SiteResult>) {
    SectionCard("وضعیت سایت‌ها") {
        if (sites.isEmpty()) {
            Text("سایتی بررسی نشده است.", style = MaterialTheme.typography.bodySmall)
        } else {
            val open = sites.count { it.status == ir.pingscore.app.diag.SiteStatus.OPEN }
            Text("$open از ${sites.size} سایت به‌خوبی باز شد.", style = MaterialTheme.typography.bodySmall)
            sites.forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${s.name} · ${categoryName(s.category)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        siteStatusName(s.status) + (s.latencyMs?.let { " (${it}ms)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = siteStatusColor(s.status),
                    )
                }
            }
        }
    }
}

@Composable
fun DnsCard(dns: DnsResult?) {
    SectionCard("سلامت DNS") {
        if (dns == null) {
            Text("DNS بررسی نشد.", style = MaterialTheme.typography.bodySmall)
            return@SectionCard
        }
        LabelValue("پاسخ سالم", "${dns.okCount} از ${dns.domains.size}")
        LabelValue("پاسخ مشکوک", "${dns.suspiciousCount}")
        LabelValue("بدون پاسخ", "${dns.failedCount}")
        LabelValue("زمان پاسخ (میانه)", if (dns.medianMs == null) "—" else "${dns.medianMs} ms")
        LabelValue(
            "نشت DNS",
            when (dns.leakSuspected) {
                true -> "احتمال نشت"
                false -> "نشانه‌ای نیست"
                null -> "قابل ارزیابی نبود"
            },
        )
        dns.domains.filter { it.status != ir.pingscore.app.diag.DnsDomainStatus.OK }.forEach {
            Text("• ${it.domain}: ${dnsStatusName(it.status)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** کارت خطا/پیام ساده. */
@Composable
fun MessageCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
