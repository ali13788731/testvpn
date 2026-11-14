package ir.pingscore.app.score

import ir.pingscore.app.diag.DnsDomainStatus
import ir.pingscore.app.diag.DnsResult
import ir.pingscore.app.diag.DnsRules
import ir.pingscore.app.diag.PingStats
import ir.pingscore.app.diag.SiteCategory
import ir.pingscore.app.diag.SiteResult
import ir.pingscore.app.diag.SiteStatus
import ir.pingscore.app.diag.SpeedStats
import ir.pingscore.app.diag.jitter
import ir.pingscore.app.diag.median
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {

    private fun site(cat: SiteCategory, status: SiteStatus) =
        SiteResult("s", cat, "https://x", status, 200, 100)

    private fun allSites(status: SiteStatus) = SiteCategory.values().map { site(it, status) }

    private fun ping(median: Double?, jitter: Double?, loss: Double) =
        PingStats(median, jitter, loss, 10, (10 - loss / 10).toInt(), emptyList())

    private fun speed(dl: Double?, ul: Double?) = SpeedStats(dl, ul, emptyList(), emptyList())

    private val perfect = ScoreInput(
        ping = ping(20.0, 2.0, 0.0),
        speed = speed(100.0, 50.0),
        sites = allSites(SiteStatus.OPEN),
        dns = null,
    )

    private val dead = ScoreInput(
        ping = ping(null, null, 100.0),
        speed = speed(0.0, 0.0),
        sites = allSites(SiteStatus.FAILED),
        dns = null,
    )

    @Test
    fun perfectConnectionScores100ForAllUseCases() {
        val r = ScoreEngine.evaluate(perfect)
        assertEquals(100, r.overall)
        r.useCases.forEach {
            assertEquals(100, it.score)
            assertEquals(ScoreLabel.EXCELLENT, it.label)
        }
    }

    @Test
    fun deadConnectionScoresZero() {
        val r = ScoreEngine.evaluate(dead)
        assertEquals(0, r.overall)
        r.useCases.forEach { assertEquals(ScoreLabel.POOR, it.label) }
    }

    @Test
    fun labelThresholds() {
        assertEquals(ScoreLabel.EXCELLENT, ScoreEngine.labelFor(75))
        assertEquals(ScoreLabel.MEDIUM, ScoreEngine.labelFor(74))
        assertEquals(ScoreLabel.MEDIUM, ScoreEngine.labelFor(50))
        assertEquals(ScoreLabel.POOR, ScoreEngine.labelFor(49))
        assertEquals(ScoreLabel.POOR, ScoreEngine.labelFor(0))
    }

    @Test
    fun blockedAiSitesHurtAiMoreThanVideo() {
        val sites = allSites(SiteStatus.OPEN).map {
            if (it.category == SiteCategory.AI) it.copy(status = SiteStatus.IP_BLOCKED) else it
        }
        val r = ScoreEngine.evaluate(perfect.copy(sites = sites))
        val ai = r.of(UseCase.AI)!!.score
        val video = r.of(UseCase.VIDEO)!!.score
        assertTrue("ai=$ai video=$video", ai < video)
        assertEquals(100, video)
    }

    @Test
    fun slowDownloadHurtsVideoMoreThanChat() {
        val r = ScoreEngine.evaluate(perfect.copy(speed = speed(2.0, 1.0)))
        val video = r.of(UseCase.VIDEO)!!.score
        val chat = r.of(UseCase.CHAT)!!.score
        assertTrue("video=$video chat=$chat", video < chat)
    }

    @Test
    fun highLatencyHurtsChatMoreThanVideo() {
        val r = ScoreEngine.evaluate(perfect.copy(ping = ping(250.0, 50.0, 0.0)))
        val chat = r.of(UseCase.CHAT)!!.score
        val video = r.of(UseCase.VIDEO)!!.score
        assertTrue("chat=$chat video=$video", chat < video)
    }

    @Test
    fun missingSpeedRedistributesWeights() {
        val r = ScoreEngine.evaluate(perfect.copy(speed = null))
        val ai = r.of(UseCase.AI)!!
        assertEquals(100, ai.score)
        val speedComp = ai.components.first { it.component == ScoreComponent.SPEED_LOSS }
        // loss هنوز اندازه‌گیری شده، پس مؤلفه حذف نمی‌شود
        assertNotNull(speedComp.score)
        assertEquals(1.0, ai.components.sumOf { it.weight }, 1e-9)
    }

    @Test
    fun missingPingAndSpeedDropsComponents() {
        val r = ScoreEngine.evaluate(perfect.copy(ping = null, speed = null))
        val ai = r.of(UseCase.AI)!!
        assertNull(ai.components.first { it.component == ScoreComponent.SPEED_LOSS }.score)
        assertNull(ai.components.first { it.component == ScoreComponent.LATENCY }.score)
        assertEquals(0.0, ai.components.first { it.component == ScoreComponent.LATENCY }.weight, 1e-9)
        assertEquals(100, ai.score)
    }

    @Test
    fun nothingMeasuredGivesZero() {
        val r = ScoreEngine.evaluate(ScoreInput(null, null, emptyList(), null))
        assertEquals(0, r.overall)
    }

    @Test
    fun dnsProblemsReduceAccessScore() {
        val dns = DnsResult(
            domains = listOf(
                ir.pingscore.app.diag.DnsDomainResult("a.com", DnsDomainStatus.SUSPICIOUS, listOf("10.10.34.34"), emptyList(), 10),
            ),
            okCount = 0, suspiciousCount = 1, failedCount = 0, medianMs = null,
            leakSuspected = true, dnsServers = emptyList(),
        )
        val clean = ScoreEngine.evaluate(perfect).of(UseCase.AI)!!.score
        val withDns = ScoreEngine.evaluate(perfect.copy(dns = dns)).of(UseCase.AI)!!.score
        assertTrue(withDns < clean)
    }

    @Test
    fun scoresAlwaysInRange() {
        val weird = ScoreInput(ping(-5.0, -1.0, 250.0), speed(1e9, 1e9), allSites(SiteStatus.SLOW), null)
        val r = ScoreEngine.evaluate(weird)
        r.useCases.forEach { assertTrue(it.score in 0..100) }
        assertTrue(r.overall in 0..100)
    }

    @Test
    fun customConfigChangesResult() {
        val base = ScoreConfig.Default
        val aiProfile = base.profiles.getValue(UseCase.AI)
        val onlyAccess = base.copy(
            profiles = base.profiles + (UseCase.AI to aiProfile.copy(weights = ComponentWeights(1.0, 0.0, 0.0))),
        )
        val input = perfect.copy(ping = ping(900.0, 200.0, 50.0), speed = speed(0.1, 0.1))
        assertEquals(100, ScoreEngine.evaluate(input, onlyAccess).of(UseCase.AI)!!.score)
        assertTrue(ScoreEngine.evaluate(input, base).of(UseCase.AI)!!.score < 100)
    }

    @Test
    fun summaryMentionsWeakestComponentWhenNotExcellent() {
        val r = ScoreEngine.evaluate(perfect.copy(ping = ping(450.0, 120.0, 0.0)))
        val chat = r.of(UseCase.CHAT)!!
        assertTrue(chat.label != ScoreLabel.EXCELLENT)
        assertTrue(chat.summary.contains("ping"))
    }

    @Test
    fun statsHelpers() {
        assertEquals(3.0, median(listOf(5.0, 1.0, 3.0))!!, 1e-9)
        assertEquals(2.5, median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(median(emptyList()))
        assertEquals(10.0, jitter(listOf(10.0, 20.0, 10.0))!!, 1e-9)
        assertNull(jitter(listOf(10.0)))
    }

    @Test
    fun dnsRules() {
        assertTrue(DnsRules.isBogon("10.10.34.35"))
        assertTrue(DnsRules.isBogon("192.168.1.1"))
        assertTrue(DnsRules.isBogon("0.0.0.0"))
        assertTrue(!DnsRules.isBogon("8.8.8.8"))
        assertEquals(DnsDomainStatus.SUSPICIOUS, DnsRules.classify(listOf("10.10.34.34"), listOf("1.2.3.4")))
        assertEquals(DnsDomainStatus.SUSPICIOUS, DnsRules.classify(emptyList(), listOf("1.2.3.4")))
        assertEquals(DnsDomainStatus.FAILED, DnsRules.classify(null, listOf("1.2.3.4")))
        assertEquals(DnsDomainStatus.OK, DnsRules.classify(listOf("1.2.3.4"), listOf("1.2.3.4")))
        assertEquals(DnsDomainStatus.MISMATCH, DnsRules.classify(listOf("5.6.7.8"), listOf("1.2.3.4")))
        assertEquals(DnsDomainStatus.OK, DnsRules.classify(listOf("198.18.0.5"), listOf("1.2.3.4")))
    }
}
