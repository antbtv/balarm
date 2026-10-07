package com.antbtv.balarm.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.data.di.DataModule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Флаги состояния лежат в device-protected storage (ADR-001): файл не в credential-protected `filesDir`. */
@RunWith(AndroidJUnit4::class)
class DeviceProtectedDataStoreTest {

    @Test
    fun `app prefs file is created under the device protected files dir`() {
        val app: Context = ApplicationProvider.getApplicationContext()
        val deContext = app.createDeviceProtectedStorageContext()
        val repository = DataStoreSetupStateRepository(DataModule.providePreferencesDataStore(deContext))

        runBlocking { repository.completeOnboarding() }

        val file = java.io.File(deContext.filesDir, "datastore/app_prefs.preferences_pb")
        assertThat(file.exists()).isTrue()
        assertThat(
            file.absolutePath,
        ).isNotEqualTo(java.io.File(app.filesDir, "datastore/app_prefs.preferences_pb").absolutePath)
        assertThat(runBlocking { repository.state.first().onboardingCompleted }).isTrue()
    }
}
