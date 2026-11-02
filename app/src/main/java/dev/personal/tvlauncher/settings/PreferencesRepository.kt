package dev.personal.tvlauncher.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import dev.personal.tvlauncher.channels.ChannelRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore by preferencesDataStore(name = "launcher")

class PreferencesRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.launcherDataStore)
    private val key = stringPreferencesKey("settings_v1")
    val data: Flow<LauncherPreferences> = store.data.map { decodePreferences(it[key]) }

    private suspend fun update(transform: (LauncherPreferences) -> LauncherPreferences) {
        store.edit { values ->
            val old = decodePreferences(values[key])
            val next = transform(old)
            if (next != old) values[key] = encodePreferences(next)
        }
    }

    suspend fun setShowApps(visible: Boolean) = update { it.copy(showApps = visible) }
    suspend fun setAppsLayout(layout: AppsLayout) = update { it.copy(appsLayout = layout) }
    suspend fun setBackground(theme: BackgroundTheme) = update { it.copy(background = theme) }

    suspend fun favourite(component: String, enabled: Boolean) = update { old ->
        old.copy(favouriteApps = if (enabled) (old.favouriteApps + component).distinct() else old.favouriteApps - component)
    }

    suspend fun moveApp(component: String, delta: Int) = update { old ->
        val index = old.favouriteApps.indexOf(component)
        val target = index + delta
        if (index < 0 || target !in old.favouriteApps.indices || delta !in listOf(-1, 1)) old
        else old.copy(favouriteApps = old.favouriteApps.toMutableList().apply { add(target, removeAt(index)) })
    }

    suspend fun select(row: ChannelRow) = update { it.copy(lists = selectList(it.lists, row)) }
    suspend fun show(key: String, visible: Boolean) = update { it.copy(lists = setVisibility(it.lists, key, visible)) }
    suspend fun move(key: String, delta: Int) = update { it.copy(lists = moveList(it.lists, key, delta)) }
    suspend fun forget(key: String) = update { it.copy(lists = it.lists.filterNot { row -> row.key == key }) }

    // Update labels only on authoritative success; retain missing rows and every user's choice.
    suspend fun rememberTitles(rows: List<ChannelRow>) = update { old ->
        old.copy(lists = reconcileLists(old.lists, rows).map { it.saved })
    }
}
