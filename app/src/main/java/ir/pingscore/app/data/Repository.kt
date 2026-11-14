package ir.pingscore.app.data

import ir.pingscore.app.diag.TestReport
import ir.pingscore.app.score.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

object ReportJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(report: TestReport): String = json.encodeToString(TestReport.serializer(), report)

    fun decode(text: String): TestReport? = try {
        json.decodeFromString(TestReport.serializer(), text)
    } catch (e: Exception) {
        null
    }
}

fun TestEntity.report(): TestReport? = ReportJson.decode(reportJson)

class TestRepository(private val dao: TestDao) {

    fun observeAll(): Flow<List<TestEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<TestEntity?> = dao.observe(id)
    suspend fun getByIds(ids: List<Long>): List<TestEntity> = dao.getByIds(ids)

    suspend fun save(report: TestReport, name: String): Long {
        val score = report.score
        val id = dao.insert(
            TestEntity(
                timeMillis = report.timeMillis,
                name = name,
                mode = report.mode.name,
                source = report.source.name,
                transport = report.connection.transport,
                viaTunnel = report.connection.viaTunnel,
                overall = score.overall,
                aiScore = score.of(UseCase.AI)?.score ?: 0,
                videoScore = score.of(UseCase.VIDEO)?.score ?: 0,
                chatScore = score.of(UseCase.CHAT)?.score ?: 0,
                reportJson = ReportJson.encode(report),
            ),
        )
        dao.prune(MAX_KEEP)
        return id
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())
    suspend fun setBaseline(id: Long) = dao.setBaseline(id)
    suspend fun clearBaseline() = dao.clearBaseline()
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()

    private companion object {
        const val MAX_KEEP = 300
    }
}
