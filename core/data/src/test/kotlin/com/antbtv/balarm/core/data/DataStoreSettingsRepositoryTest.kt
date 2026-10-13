package com.antbtv.balarm.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.antbtv.balarm.core.domain.settings.SettingsPolicy
import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val job = Job()
    private lateinit var file: File

    @Before
    fun setUp() {
        file = folder.newFile("app_prefs.preferences_pb").also { it.delete() }
    }

    @After
    fun tearDown() {
        job.cancel()
    }

    private fun store(scope: TestScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + job),
    ) { file }

    private fun repository(scope: TestScope) = DataStoreSettingsRepository(store(scope), FeatureFlagProvider { true })

    @Test
    fun `fresh store gives defaults`() = runTest {
        assertThat(repository(this).settings.first()).isEqualTo(SettingsPolicy.DEFAULTS)
    }

    @Test
    fun `update is stored and read back`() = runTest {
        val repository = repository(this)
        val target = AppSettings(AutoStop.NEVER, Duration.ofSeconds(45), 50, DayOfWeek.SUNDAY)

        repository.update { target }

        assertThat(repository.settings.first()).isEqualTo(target)
    }

    @Test
    fun `update transforms the current value and can clear first day`() = runTest {
        val repository = repository(this)
        repository.update { it.copy(firstDayOfWeek = DayOfWeek.MONDAY, autoStop = AutoStop.MIN_5) }
        repository.update { it.copy(firstDayOfWeek = null) }

        val s = repository.settings.first()
        assertThat(s.firstDayOfWeek).isNull()
        assertThat(s.autoStop).isEqualTo(AutoStop.MIN_5)
    }

    @Test
    fun `out of range update is replaced by defaults`() = runTest {
        val repository = repository(this)

        repository.update { it.copy(missionIdleTimeout = Duration.ofSeconds(7), missionDuckPercent = 99) }

        val s = repository.settings.first()
        assertThat(s.missionIdleTimeout).isEqualTo(SettingsPolicy.DEFAULTS.missionIdleTimeout)
        assertThat(s.missionDuckPercent).isEqualTo(SettingsPolicy.DEFAULTS.missionDuckPercent)
    }

    @Test
    fun `garbage in the store reads as defaults`() = runTest {
        val store = store(this)
        store.edit {
            it[intPreferencesKey("settings.mission_idle_seconds")] = 9999
            it[intPreferencesKey("settings.mission_duck_percent")] = -5
            it[intPreferencesKey("settings.first_day_of_week")] = 42
        }

        val s = DataStoreSettingsRepository(store, FeatureFlagProvider { true }).settings.first()

        assertThat(s).isEqualTo(SettingsPolicy.DEFAULTS)
    }

    @Test
    fun `corrupted file reads as defaults`() = runTest {
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))

        assertThat(repository(this).settings.first()).isEqualTo(SettingsPolicy.DEFAULTS)
    }

    @Test
    fun `flag off shows defaults but keeps saved values`() = runTest {
        val target = AppSettings(AutoStop.MIN_5, Duration.ofSeconds(30), 20, DayOfWeek.MONDAY)
        val store = store(this)
        DataStoreSettingsRepository(store, FeatureFlagProvider { true }).update { target }

        assertThat(DataStoreSettingsRepository(store, FeatureFlagProvider { false }).settings.first())
            .isEqualTo(SettingsPolicy.DEFAULTS)
        assertThat(DataStoreSettingsRepository(store, FeatureFlagProvider { true }).settings.first())
            .isEqualTo(target)
    }
}
