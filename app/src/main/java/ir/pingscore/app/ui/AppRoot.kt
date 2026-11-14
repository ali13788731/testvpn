package ir.pingscore.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pingscore.app.MainViewModel
import ir.pingscore.app.Screen

/** ناوبری ساده‌ی مبتنی بر state (بدون وابستگی اضافه‌ی Navigation). */
@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    BackHandler(enabled = state.screen != Screen.Main) { vm.back() }

    when (val screen = state.screen) {
        Screen.Main -> {
            val actions = remember(vm) {
                MainActions(
                    navigate = vm::navigate,
                    startTest = vm::startManualTest,
                    cancelTest = vm::cancelTest,
                    setMode = vm::setMode,
                    setName = vm::setConnectionName,
                )
            }
            MainScreen(state, history.firstOrNull(), actions)
        }

        Screen.History -> HistoryScreen(
            tests = history,
            onBack = vm::back,
            onOpen = { vm.navigate(Screen.Detail(it)) },
            onCompare = { vm.navigate(Screen.Compare(it)) },
            onDeleteAll = { vm.deleteAll() },
        )

        is Screen.Detail -> {
            val entity by remember(screen.id) { vm.observeEntity(screen.id) }.collectAsState(initial = null)
            DetailScreen(
                entity = entity,
                onBack = vm::back,
                onRename = { vm.rename(screen.id, it) },
                onSetBaseline = { vm.setBaseline(screen.id) },
                onClearBaseline = { vm.clearBaseline() },
                onDelete = {
                    vm.delete(screen.id)
                    vm.back()
                },
            )
        }

        is Screen.Compare -> CompareScreen(
            ids = screen.ids,
            load = { vm.entities(it) },
            onBack = vm::back,
        )

        Screen.Settings -> SettingsScreen(
            settings = settings,
            onBack = vm::back,
            update = { vm.updateSettings(it) },
        )
    }
}
