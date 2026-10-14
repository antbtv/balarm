package com.antbtv.balarm.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.data.di.DataModule
import com.antbtv.balarm.core.domain.settings.SettingsPolicy
import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M9-T-test (ревью T01): настройки читаются в Direct Boot — реальный провайдер DataStore строится на
 * device-protected контексте, а битый файл `ReplaceFileCorruptionHandler` превращает в умолчания.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDataStoreProviderTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val deContext = app.createDeviceProtectedStorageContext()

    private fun deFile() = File(deContext.filesDir, "datastore/app_prefs.preferences_pb")

    private fun ceFile() = File(app.filesDir, "datastore/app_prefs.preferences_pb")

    @Test
    fun `when settings are updated then they are stored in the device protected file only`() {
        val repository = DataStoreSettingsRepository(
            DataModule.providePreferencesDataStore(deContext),
            FeatureFlagProvider { true },
        )
        val target = AppSettings(AutoStop.MIN_5, Duration.ofSeconds(30), 50, DayOfWeek.SUNDAY)

        runBlocking { repository.update { target } }

        assertThat(deFile().exists()).isTrue()
        assertThat(deFile().absolutePath).isNotEqualTo(ceFile().absolutePath)
        assertThat(runBlocking { repository.settings.first() }).isEqualTo(target)
    }

    @Test
    fun `when the device protected file is corrupted then provider gives defaults and stays writable`() {
        deFile().parentFile!!.mkdirs()
        deFile().writeBytes(byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x55, 0x66, 0x77, 0x13, 0x37))
        val repository = DataStoreSettingsRepository(
            DataModule.providePreferencesDataStore(deContext),
            FeatureFlagProvider { true },
        )

        assertThat(runBlocking { repository.settings.first() }).isEqualTo(SettingsPolicy.DEFAULTS)

        val target = AppSettings(AutoStop.NEVER, Duration.ofSeconds(60), 10, null)
        runBlocking { repository.update { target } }
        assertThat(runBlocking { repository.settings.first() }).isEqualTo(target)
    }
}
