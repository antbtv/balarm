package com.antbtv.balarm.core.data.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.data.di.DataModule
import com.antbtv.balarm.core.domain.sound.SavedVolume
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreRingVolumeStoreTest {

    private val deContext = ApplicationProvider.getApplicationContext<Context>().createDeviceProtectedStorageContext()
    private val store = DataStoreRingVolumeStore(DataModule.providePreferencesDataStore(deContext))

    @Test
    fun `save load and clear round trip`() = runBlocking {
        store.clear()
        assertThat(store.load()).isNull()

        store.save(SavedVolume(original = 1, applied = 5))
        assertThat(store.load()).isEqualTo(SavedVolume(1, 5))

        store.clear()
        assertThat(store.load()).isNull()
    }
}
