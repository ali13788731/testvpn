package ir.pingscore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.pingscore.app.data.AppSettings
import ir.pingscore.app.diag.Defaults
import ir.pingscore.app.diag.PingTarget
import ir.pingscore.app.diag.SiteCategory
import ir.pingscore.app.diag.SiteDef
import ir.pingscore.app.work.BackgroundScheduler

/** متن پیوست قوانین حریم خصوصی (کوتاه) که در برنامه هم نمایش داده می‌شود. */
const val PRIVACY_TEXT =
    "PingScore هیچ حساب کاربری نمی‌خواهد، هیچ اطلاعات شخصی جمع نمی‌کند و هیچ داده‌ای به سرور ما ارسال نمی‌شود. " +
        "نتیجه‌ی تست‌ها، نام‌گذاری‌ها و تنظیمات فقط روی همین گوشی ذخیره می‌شود و با «پاک‌کردن همه» حذف می‌شود. " +
        "برای سنجش، برنامه چند درخواست سبک به سرویس‌های عمومی (مثل Cloudflare) می‌فرستد؛ این سرویس‌ها مثل هر درخواست اینترنتی " +
        "می‌توانند آدرس IP شما را ببینند. برنامه از ابزار تحلیل یا تبلیغات استفاده نمی‌کند."

private fun pingTargetsToText(list: List<PingTarget>): String =
    list.joinToString("\n") { t -> "${t.label}|${t.url ?: "${t.host}:${t.port}"}" }

private fun parsePingTargets(text: String): List<PingTarget> =
    text.lines().mapNotNull { line ->
        val parts = line.split("|", limit = 2).map { it.trim() }
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
        val target = parts[1]
        if (target.startsWith("https://")) {
            PingTarget(parts[0], url = target)
        } else {
            val hp = target.split(":")
            val host = hp[0].trim()
            val port = hp.getOrNull(1)?.trim()?.toIntOrNull() ?: 443
            if (host.isEmpty() || port !in 1..65535) null else PingTarget(parts[0], host = host, port = port)
        }
    }.take(6)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    var editPing by remember { mutableStateOf(false) }
    var addSite by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    AppScaffold(title = "تنظیمات", onBack = onBack) {
        item {
            SectionCard("تست خودکار") {
                SwitchRow("اجرای تست سریع هنگام تغییر اتصال", settings.autoTestOnChange) {
                    update { s -> s.copy(autoTestOnChange = it) }
                }
            }
        }
        item {
            SectionCard("پایش دوره‌ای در پس‌زمینه") {
                SwitchRow("پایش خودکار فعال باشد", settings.backgroundEnabled) {
                    update { s -> s.copy(backgroundEnabled = it) }
                }
                if (settings.backgroundEnabled) {
                    Text("بازه‌ی تست", style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackgroundScheduler.intervalOptions.forEach { m ->
                            FilterChip(
                                selected = settings.backgroundIntervalMinutes == m,
                                onClick = { update { s -> s.copy(backgroundIntervalMinutes = m) } },
                                label = { Text(intervalName(m)) },
                            )
                        }
                    }
                    Text("شبکه‌ی مجاز", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.backgroundWifiOnly,
                            onClick = { update { s -> s.copy(backgroundWifiOnly = true) } },
                            label = { Text("فقط Wi-Fi") },
                        )
                        FilterChip(
                            selected = !settings.backgroundWifiOnly,
                            onClick = { update { s -> s.copy(backgroundWifiOnly = false) } },
                            label = { Text("هر شبکه") },
                        )
                    }
                    SwitchRow("تست سرعت هم انجام شود (دیتای بیشتر)", settings.backgroundIncludeSpeed) {
                        update { s -> s.copy(backgroundIncludeSpeed = it) }
                    }
                }
                Text(
                    "اندروید کارهای پس‌زمینه را برای صرفه‌جویی در باتری محدود می‌کند: کمترین بازه ۱۵ دقیقه است، " +
                        "زمان دقیق اجرا را سیستم تعیین می‌کند و در حالت Doze یا باتری کم ممکن است تست‌ها عقب بیفتند. " +
                        "پایش فقط حالت «سریع» را اجرا می‌کند و در حالت پیش‌فرض بدون تست سرعت است.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SectionCard("مقصدهای پینگ") {
                Text(pingTargetsToText(settings.pingTargets), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { editPing = true }, modifier = Modifier.fillMaxWidth()) { Text("ویرایش مقصدها") }
            }
        }
        item {
            SectionCard("فهرست سایت‌ها (${settings.sites.size})") {
                settings.sites.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${s.name} · ${categoryName(s.category)}", style = MaterialTheme.typography.bodyMedium)
                            Text(s.url, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { update { cur -> cur.copy(sites = cur.sites - s) } }) { Text("حذف") }
                    }
                    HorizontalDivider()
                }
                OutlinedButton(onClick = { addSite = true }, modifier = Modifier.fillMaxWidth()) { Text("افزودن سایت") }
                TextButton(
                    onClick = { update { cur -> cur.copy(sites = Defaults.sites, pingTargets = Defaults.pingTargets) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("بازگشت به فهرست پیش‌فرض") }
            }
        }
        item {
            SectionCard("حریم خصوصی") {
                Text(PRIVACY_TEXT, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("PingScore نسخه‌ی 0.2.0", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (editPing) {
        var text by remember { mutableStateOf(pingTargetsToText(settings.pingTargets)) }
        AlertDialog(
            onDismissRequest = { editPing = false },
            title = { Text("مقصدهای پینگ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هر خط: نام|آدرس:پورت (TCP) یا نام|https://… (HTTP). حداکثر ۶ خط.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = parsePingTargets(text)
                    if (parsed.isNotEmpty()) update { s -> s.copy(pingTargets = parsed) }
                    editPing = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editPing = false }) { Text("انصراف") } },
        )
    }

    if (addSite) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("https://") }
        var cat by remember { mutableStateOf(SiteCategory.OTHER) }
        AlertDialog(
            onDismissRequest = { addSite = false },
            title = { Text("افزودن سایت") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("نام") })
                    OutlinedTextField(value = url, onValueChange = { url = it.trim() }, singleLine = true, label = { Text("آدرس https") })
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SiteCategory.values().forEach { c ->
                            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(categoryName(c)) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && url.startsWith("https://") && url.length > 10,
                    onClick = {
                        update { s -> s.copy(sites = s.sites + SiteDef(name.trim(), cat, url)) }
                        addSite = false
                    },
                ) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { addSite = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun intervalName(minutes: Int): String = when {
    minutes < 60 -> "$minutes دقیقه"
    minutes < 1440 -> "${minutes / 60} ساعت"
    else -> "روزانه"
}
