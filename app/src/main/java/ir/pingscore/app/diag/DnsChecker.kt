package ir.pingscore.app.diag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** قوانین خالص (بدون وابستگی به اندروید) برای تشخیص DNS مشکوک. */
object DnsRules {

    fun isBogon(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        val a = p[0]
        val b = p[1]
        return a == 0 || a == 10 || a == 127 ||
            (a == 100 && b in 64..127) ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            a >= 224
    }

    /** بازه‌ی 198.18.0.0/15؛ بعضی برنامه‌های شبکه برای مدیریت DNS از آن استفاده می‌کنند (عادی است). */
    fun isFakeIp(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        return p.size == 4 && p[0] == 198 && (p[1] == 18 || p[1] == 19)
    }

    /** systemIps = null یعنی timeout. */
    fun classify(systemIps: List<String>?, referenceIps: List<String>): DnsDomainStatus = when {
        systemIps == null -> DnsDomainStatus.FAILED
        systemIps.isEmpty() ->
            if (referenceIps.isNotEmpty()) DnsDomainStatus.SUSPICIOUS else DnsDomainStatus.FAILED
        systemIps.any { isBogon(it) } -> DnsDomainStatus.SUSPICIOUS
        referenceIps.isNotEmpty() && systemIps.none { it in referenceIps } && !systemIps.all { isFakeIp(it) } ->
            DnsDomainStatus.MISMATCH
        else -> DnsDomainStatus.OK
    }

    private val knownPublic = setOf(
        "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "149.112.112.112",
        "208.67.222.222", "208.67.220.220",
    )

    /** نشت احتمالی: DNS اتصال فعلی همان DNS شبکه‌ی زیرین (غیرعمومی) باشد. null = قابل ارزیابی نیست. */
    fun detectLeak(info: DnsInfo?): Boolean? {
        if (info == null || !info.viaTunnel) return null
        val under = info.underlyingServers.filter { it !in knownPublic }
        if (under.isEmpty()) return false
        return info.activeServers.isEmpty() || info.activeServers.any { it in under }
    }
}

/** سالم بودن DNS: مقایسه‌ی پاسخ resolver سیستم با یک DoH مرجع، تشخیص پاسخ مشکوک و نشت احتمالی. */
class DnsChecker(
    baseClient: OkHttpClient = Http.base,
    private val dnsInfoProvider: () -> DnsInfo? = { null },
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(3_500, TimeUnit.MILLISECONDS)
        .build()

    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "dns-check").apply { isDaemon = true } }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(domains: List<String>): DnsResult = coroutineScope {
        val results = domains.map { d -> async(Dispatchers.IO) { checkDomain(d) } }.awaitAll()
        val info = try {
            dnsInfoProvider()
        } catch (e: Exception) {
            null
        }
        DnsResult(
            domains = results,
            okCount = results.count { it.status == DnsDomainStatus.OK || it.status == DnsDomainStatus.MISMATCH },
            suspiciousCount = results.count { it.status == DnsDomainStatus.SUSPICIOUS },
            failedCount = results.count { it.status == DnsDomainStatus.FAILED },
            medianMs = medianLong(results.filter { it.status == DnsDomainStatus.OK }.mapNotNull { it.ms }),
            leakSuspected = DnsRules.detectLeak(info),
            dnsServers = info?.activeServers.orEmpty(),
        )
    }

    private fun checkDomain(domain: String): DnsDomainResult {
        val t0 = System.nanoTime()
        val sys = resolveSystem(domain)
        val ms = (System.nanoTime() - t0) / 1_000_000
        val ref = resolveDoh(domain)
        return DnsDomainResult(domain, DnsRules.classify(sys, ref), sys.orEmpty(), ref, if (sys == null) null else ms)
    }

    /** null = timeout، لیست خالی = پاسخ منفی/NXDOMAIN. */
    private fun resolveSystem(domain: String): List<String>? {
        val future = pool.submit(Callable<List<String>> {
            InetAddress.getAllByName(domain).filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        })
        return try {
            future.get(3, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            emptyList()
        } catch (e: TimeoutException) {
            future.cancel(true)
            null
        }
    }

    private fun resolveDoh(domain: String): List<String> {
        val urls = listOf(
            "https://cloudflare-dns.com/dns-query?name=$domain&type=A",
            "https://dns.google/resolve?name=$domain&type=A",
        )
        for (u in urls) {
            try {
                val req = Request.Builder().url(u).header("accept", "application/dns-json").build()
                val body = client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                } ?: continue
                val answers = json.parseToJsonElement(body).jsonObject["Answer"]?.jsonArray ?: return emptyList()
                return answers.mapNotNull {
                    val o = it.jsonObject
                    if (o["type"]?.jsonPrimitive?.intOrNull == 1) o["data"]?.jsonPrimitive?.contentOrNull else null
                }
            } catch (e: Exception) {
                // مرجع بعدی را امتحان کن
            }
        }
        return emptyList()
    }
}
