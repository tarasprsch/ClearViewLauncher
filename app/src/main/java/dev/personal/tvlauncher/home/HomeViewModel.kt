package dev.personal.tvlauncher.home

import dev.personal.tvlauncher.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.personal.tvlauncher.apps.*
import dev.personal.tvlauncher.channels.*
import dev.personal.tvlauncher.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeState(
    val apps: List<TvApp> = emptyList(),
    val channelState: ChannelLoadState = ChannelLoadState.Loading,
    val lastRows: List<ChannelRow> = emptyList(),
    val preferences: LauncherPreferences = LauncherPreferences(),
    val preferencesLoaded: Boolean = false,
    val error: Int? = null,
)

class HomeViewModel(
    private val apps: AppsRepository,
    private val channels: ChannelRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    var focus: FocusAnchor? = null
    var allAppsFocus: FocusAnchor? = null
    var favouriteAppsFocus: FocusAnchor? = null
    private var snapshot = emptyList<ChannelRow>()
    private var cachedPreferences = LauncherPreferences()
    private val notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = notices.asSharedFlow()
    private data class PrefState(val data: LauncherPreferences, val loaded: Boolean, val error: Int? = null)
    private data class AppState(val data: List<TvApp>, val error: Int? = null)

    val state: StateFlow<HomeState> = combine(
        apps.observeApps().map { AppState(it) }.catch { emit(AppState(emptyList(), R.string.error_apps_read)) }.onStart { emit(AppState(emptyList())) },
        channels.observeRows().map { load ->
            snapshot = retainSnapshot(snapshot, load)
            if (load is ChannelLoadState.Ready) mutate { preferences.rememberTitles(load.rows) }
            load to snapshot
        },
        preferences.data.map { cachedPreferences = it; PrefState(it, true) }
            .catch { emit(PrefState(cachedPreferences, false, R.string.error_preferences_read)) }
            .onStart { emit(PrefState(cachedPreferences, false)) },
    ) { discovered, channel, saved ->
        HomeState(discovered.data, channel.first, channel.second, saved.data, saved.loaded, saved.error ?: discovered.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeState())

    fun setShowApps(visible: Boolean) = mutate { preferences.setShowApps(visible) }
    fun setAppsLayout(layout: AppsLayout) = mutate { preferences.setAppsLayout(layout) }
    fun setBackground(theme: BackgroundTheme) = mutate { preferences.setBackground(theme) }
    fun refresh() { apps.refresh(); channels.refresh() }
    fun favourite(component: String, enabled: Boolean) = mutate { preferences.favourite(component, enabled) }
    fun moveApp(component: String, delta: Int) = mutate { preferences.moveApp(component, delta) }
    fun select(row: ChannelRow) = mutate { preferences.select(row) }
    fun show(key: String, visible: Boolean) = mutate { preferences.show(key, visible) }
    fun move(key: String, delta: Int) = mutate { preferences.move(key, delta) }
    fun forget(key: String) = mutate { preferences.forget(key) }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.emit(R.string.error_preferences_write) }
        }
    }
}
