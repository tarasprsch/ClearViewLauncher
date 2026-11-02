package dev.personal.tvlauncher.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.personal.tvlauncher.channels.ChannelRow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun savedAppAndListOrderSurviveRepositoryRecreation() = runBlocking {
        val file = temp.root.resolve("launcher.preferences_pb")
        var job = SupervisorJob()
        var store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        var repo = PreferencesRepository(store)
        repo.favourite("a.app/.Main", true); repo.favourite("b.app/.Main", true)
        repo.moveApp("b.app/.Main", -1)
        repo.setShowApps(false); repo.setAppsLayout(AppsLayout.GRID); repo.setBackground(BackgroundTheme.FOREST)
        val one = ChannelRow("one.app", 1, "stable", "One", emptyList())
        val two = ChannelRow("two.app", 2, null, "Two", emptyList())
        repo.select(one); repo.select(two); repo.show(one.key, false); repo.move(two.key, -1)
        job.cancelAndJoin()
        job = SupervisorJob()
        store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        repo = PreferencesRepository(store)
        try {
            val restored = repo.data.first()
            assertEquals(listOf("b.app/.Main", "a.app/.Main"), restored.favouriteApps)
            assertEquals(listOf(two.key, one.key), restored.lists.map { it.key })
            assertFalse(restored.lists[1].visible)
            assertFalse(restored.showApps)
            assertEquals(AppsLayout.GRID, restored.appsLayout)
            assertEquals(BackgroundTheme.FOREST, restored.background)
            // An authoritative empty refresh retains selections as unavailable.
            repo.rememberTitles(emptyList())
            assertEquals(restored, repo.data.first())
            repo.rememberTitles(listOf(one.copy(title = "Renamed")))
            assertEquals("Renamed", repo.data.first().lists[1].title)
        } finally { job.cancelAndJoin() }
    }
}
