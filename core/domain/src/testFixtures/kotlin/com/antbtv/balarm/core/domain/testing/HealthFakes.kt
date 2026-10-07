package com.antbtv.balarm.core.domain.testing

import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.PermissionSnapshot
import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Всё выдано, ничего не ограничено. */
val HEALTHY_SNAPSHOT = PermissionSnapshot(
    notificationsEnabled = true,
    ringingChannelEnabled = true,
    exactAlarms = true,
    fullScreenIntent = true,
    overlay = true,
    ignoringBatteryOptimizations = true,
    backgroundRestricted = false,
    alarmsAllowedByDnd = true,
    alarmVolumeMuted = false,
)

class FakePermissionHealthChecker(var current: PermissionSnapshot = HEALTHY_SNAPSHOT) : PermissionHealthChecker {
    var calls = 0

    override fun snapshot(): PermissionSnapshot {
        calls++
        return current
    }
}

class FakeSetupStateRepository(initial: SetupState = SetupState()) : SetupStateRepository {
    private val flow = MutableStateFlow(initial)

    /** Бросать при записи (моделирует `IOException` DataStore). */
    var failOnWrite: Throwable? = null

    override val state: Flow<SetupState> = flow

    override suspend fun completeOnboarding() {
        failOnWrite?.let { throw it }
        flow.value = flow.value.copy(onboardingCompleted = true)
    }

    override suspend fun setOemBackgroundConfirmed(confirmed: Boolean) {
        failOnWrite?.let { throw it }
        flow.value = flow.value.copy(oemBackgroundConfirmed = confirmed)
    }
}
