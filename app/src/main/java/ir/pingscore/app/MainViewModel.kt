package ir.pingscore.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.pingscore.app.data.AppGraph
import ir.pingscore.app.data.AppSettings
import ir.pingscore.app.data.SettingsStore
import ir.pingscore.app.data.TestEntity
import ir.pingscore.app.data.TestPipeline
import ir.pingscore.app.data.TestRepository
import ir.pingscore.app.diag.TestMode
import ir.pingscore.app.diag.TestProgress
import ir.pingscore.app.diag.TestSource
import ir.pingscore.app.network.NetworkMonitor
import ir.pingscore.app.network.NetworkSnapshot
import ir.pingscore.app.work.BackgroundScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** یک رویداد در تاریخچه‌ی تغییر اتصال. */
data class NetworkEvent(
    val timeMillis: Long,
    val snapshot: NetworkSnapshot,
)

sealed interface Screen {
    data object Main : Screen
    data object History : Screen
    data object Settings : Screen
    data class Detail(val id: Long) : Screen
    data class Compare(val ids: List<Long>) : Screen
}

data class MainUiState(
    val current: NetworkSnapshot = NetworkSnapshot.DISCONNECTED,
    val previous: NetworkSnapshot? = null,
    val events: List<NetworkEvent> = emptyList(),
    val mode: TestMode = TestMode.QUICK,
    /** غیر null یعنی تست در حال اجراست. */
    val progress: TestProgress? = null,
    val connectionName: String = "",
    val message: String? = null,
    val screen: Screen = Screen.Main,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val monitor = NetworkMonitor(application)
    private val pipeline = TestPipeline(application)
    private val repo = TestRepository(AppGraph.db(application).testDao())
    private val settingsStore = SettingsStore(application)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val history: StateFlow<List<TestEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var testJob: Job? = null
    private var testSeq = 0

    init {
        BackgroundScheduler.apply(application, _settings.value)
        observeNetwork()
    }

    @OptIn(FlowPreview::class)
    private fun observeNetwork() {
        viewModelScope.launch {
            // debounce: هنگام تغییر شبکه چند callback پشت‌سرهم می‌آید؛ فقط وضعیت نهایی مهم است.
            monitor.snapshots
                .debounce(DEBOUNCE_MS)
                .collect { snapshot ->
                    val old = _state.value
                    val changed = old.events.isEmpty() || old.current.signature != snapshot.signature
                    _state.update { cur ->
                        if (!changed) {
                            cur.copy(current = snapshot)
                        } else {
                            val event = NetworkEvent(System.currentTimeMillis(), snapshot)
                            cur.copy(
                                current = snapshot,
                                previous = if (cur.events.isEmpty()) null else cur.current,
                                events = (listOf(event) + cur.events).take(MAX_EVENTS),
                                connectionName = "", // اتصال عوض شد؛ نام قبلی دیگر معتبر نیست
                            )
                        }
                    }
                    if (changed) {
                        if (!snapshot.isConnected) {
                            cancelTest()
                        } else if (_settings.value.autoTestOnChange && snapshot.isValidated) {
                            startTest(TestSource.NETWORK_CHANGE)
                        }
                    }
                }
        }
    }

    // ---------- تست ----------

    fun startManualTest() = startTest(TestSource.MANUAL)

    private fun startTest(source: TestSource) {
        val mode = if (source == TestSource.NETWORK_CHANGE) TestMode.QUICK else _state.value.mode
        val name = _state.value.connectionName.trim().ifBlank { null }
        testJob?.cancel()
        val seq = ++testSeq
        _state.update { it.copy(progress = TestProgress(ir.pingscore.app.diag.TestStage.PING, 0f), message = null) }
        testJob = viewModelScope.launch {
            var message: String? = null
            try {
                val id = pipeline.run(mode, source, name, includeSpeed = true, settings = _settings.value) { p ->
                    if (seq == testSeq) _state.update { it.copy(progress = p) }
                }
                if (id == null) message = "اتصالی برقرار نیست."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "تست کامل نشد. دوباره تلاش کنید."
            } finally {
                if (seq == testSeq) _state.update { it.copy(progress = null, message = message) }
            }
        }
    }

    fun cancelTest() {
        testSeq++
        testJob?.cancel()
        _state.update { it.copy(progress = null) }
    }

    fun setMode(mode: TestMode) = _state.update { it.copy(mode = mode) }
    fun setConnectionName(name: String) = _state.update { it.copy(connectionName = name.take(40)) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------- ناوبری ----------

    fun navigate(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun back() = _state.update {
        it.copy(
            screen = when (it.screen) {
                is Screen.Compare -> Screen.History
                is Screen.Detail -> Screen.History
                else -> Screen.Main
            },
        )
    }

    // ---------- تاریخچه ----------

    fun observeEntity(id: Long) = repo.observe(id)
    suspend fun entities(ids: List<Long>): List<TestEntity> = repo.getByIds(ids)

    fun rename(id: Long, name: String) = viewModelScope.launch { repo.rename(id, name) }
    fun setBaseline(id: Long) = viewModelScope.launch { repo.setBaseline(id) }
    fun clearBaseline() = viewModelScope.launch { repo.clearBaseline() }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    // ---------- تنظیمات ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val new = transform(_settings.value)
        _settings.value = new
        settingsStore.save(new)
        BackgroundScheduler.apply(getApplication(), new)
    }

    private companion object {
        const val DEBOUNCE_MS = 700L
        const val MAX_EVENTS = 20
    }
}
