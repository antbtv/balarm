package com.antbtv.balarm

import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(state: Flow<SetupState>) = object : SetupStateRepository {
        override val state = state

        override suspend fun completeOnboarding() = Unit

        override suspend fun setOemBackgroundConfirmed(confirmed: Boolean) = Unit
    }

    @Test
    fun `completed onboarding goes straight to the tabs`() = runTest(dispatcher) {
        val vm = AppViewModel(repository(flowOf(SetupState(onboardingCompleted = true))))
        assertThat(vm.showOnboarding.value).isNull()

        runCurrent()

        assertThat(vm.showOnboarding.value).isFalse()
    }

    @Test
    fun `fresh install shows onboarding`() = runTest(dispatcher) {
        val vm = AppViewModel(repository(flowOf(SetupState())))
        runCurrent()

        assertThat(vm.showOnboarding.value).isTrue()
    }

    @Test
    fun `a read error does not keep the splash and shows onboarding`() = runTest(dispatcher) {
        val vm = AppViewModel(repository(flow { throw IOException("disk") }))
        runCurrent()

        assertThat(vm.showOnboarding.value).isTrue()
    }

    @Test
    fun `a hanging read times out and shows onboarding`() = runTest(dispatcher) {
        val vm = AppViewModel(repository(MutableSharedFlow()))
        runCurrent()
        assertThat(vm.showOnboarding.value).isNull()

        dispatcher.scheduler.advanceTimeBy(2_001)
        runCurrent()

        assertThat(vm.showOnboarding.value).isTrue()
    }
}
