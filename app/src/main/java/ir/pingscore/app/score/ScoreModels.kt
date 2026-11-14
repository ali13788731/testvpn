package ir.pingscore.app.score

import kotlinx.serialization.Serializable

@Serializable
enum class UseCase { AI, VIDEO, CHAT }

@Serializable
enum class ScoreComponent { ACCESS, SPEED_LOSS, LATENCY }

@Serializable
enum class ScoreLabel { EXCELLENT, MEDIUM, POOR }

/** نمره‌ی یک مؤلفه؛ score = null یعنی داده‌ای برای آن نبود و از میانگین حذف شد. */
@Serializable
data class ComponentScore(
    val component: ScoreComponent,
    val score: Int?,
    /** وزن مؤثر (بعد از نرمال‌سازی). */
    val weight: Double,
)

@Serializable
data class UseCaseScore(
    val useCase: UseCase,
    val score: Int,
    val label: ScoreLabel,
    val summary: String,
    val components: List<ComponentScore>,
)

@Serializable
data class ScoreReport(
    val overall: Int,
    val overallLabel: ScoreLabel,
    val overallSummary: String,
    val useCases: List<UseCaseScore>,
) {
    fun of(useCase: UseCase): UseCaseScore? = useCases.firstOrNull { it.useCase == useCase }
}
