package com.antbtv.balarm.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antbtv.balarm.core.domain.health.SetupState
import com.google.common.truth.Truth.assertThat
import java.io.File
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

class DataStoreSetupStateRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopeJob = Job()
    private val reopenedJob = Job()
    private lateinit var file: File

    @Before
    fun setUp() {
        file = folder.newFile("app_prefs.preferences_pb").also { it.delete() }
    }

    private fun repository(scope: TestScope): DataStoreSetupStateRepository {
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + scopeJob),
        ) { file }
        return DataStoreSetupStateRepository(store)
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
        reopenedJob.cancel()
    }

    @Test
    fun `fresh store reports defaults`() = runTest {
        assertThat(repository(this).state.first()).isEqualTo(SetupState())
    }

    @Test
    fun `completed onboarding and oem confirmation are stored independently`() = runTest {
        val repository = repository(this)

        repository.completeOnboarding()
        assertThat(repository.state.first()).isEqualTo(SetupState(onboardingCompleted = true))

        repository.setOemBackgroundConfirmed(true)
        assertThat(repository.state.first()).isEqualTo(SetupState(true, true))

        repository.setOemBackgroundConfirmed(false)
        assertThat(repository.state.first()).isEqualTo(SetupState(true, false))
    }

    @Test
    fun `state survives a new store over the same file`() = runTest {
        repository(this).completeOnboarding()
        scopeJob.cancel()

        val reopened = DataStoreSetupStateRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(StandardTestDispatcher(testScheduler) + reopenedJob),
            ) { file },
        )

        assertThat(reopened.state.first().onboardingCompleted).isTrue()
    }

    @Test
    fun `a corrupted file reads as defaults instead of failing`() = runTest {
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))

        assertThat(repository(this).state.first()).isEqualTo(SetupState())
    }
}
