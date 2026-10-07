package com.antbtv.balarm.feature.onboarding

import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.health.PermissionSnapshot
import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.health.Severity
import com.antbtv.balarm.core.domain.health.healthReport
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingStepTest {

    private fun report(snapshot: PermissionSnapshot = HEALTHY_SNAPSHOT, oem: Boolean = true) =
        healthReport(snapshot, SetupState(oemBackgroundConfirmed = oem), unscheduledAlarms = 0)

    @Test
    fun `nothing left when everything is fine and oem is confirmed`() {
        assertThat(currentStep(report(), emptySet())).isNull()
    }

    @Test
    fun `unconfirmed oem is the last step that always remains`() {
        assertThat(currentStep(report(oem = false), emptySet())).isEqualTo(OnboardingStep.OEM_BACKGROUND)
    }

    @Test
    fun `granted steps are skipped and the first problem is current`() {
        val snapshot = HEALTHY_SNAPSHOT.copy(notificationsEnabled = true, fullScreenIntent = false, overlay = false)

        assertThat(currentStep(report(snapshot), emptySet())).isEqualTo(OnboardingStep.FULL_SCREEN_INTENT)
        assertThat(currentStep(report(snapshot), setOf(OnboardingStep.FULL_SCREEN_INTENT)))
            .isEqualTo(OnboardingStep.OVERLAY)
    }

    @Test
    fun `battery step covers both optimization and restricted mode`() {
        val optimized = report(HEALTHY_SNAPSHOT.copy(ignoringBatteryOptimizations = false))
        val restricted = report(HEALTHY_SNAPSHOT.copy(backgroundRestricted = true))

        assertThat(currentStep(optimized, emptySet())).isEqualTo(OnboardingStep.BATTERY)
        assertThat(currentStep(restricted, emptySet())).isEqualTo(OnboardingStep.BATTERY)
        assertThat(OnboardingStep.BATTERY.fixItem(optimized)).isEqualTo(HealthItem.BATTERY_OPTIMIZATION)
        assertThat(OnboardingStep.BATTERY.fixItem(restricted)).isEqualTo(HealthItem.BACKGROUND_RESTRICTION)
        assertThat(OnboardingStep.BATTERY.severity(restricted)).isEqualTo(Severity.CRITICAL)
        assertThat(OnboardingStep.BATTERY.severity(optimized)).isEqualTo(Severity.RECOMMENDED)
    }

    @Test
    fun `critical step can be postponed only after an attempt, others at once`() {
        val noNotifications = report(HEALTHY_SNAPSHOT.copy(notificationsEnabled = false))
        val noOverlay = report(HEALTHY_SNAPSHOT.copy(overlay = false))

        assertThat(canPostpone(OnboardingStep.NOTIFICATIONS, noNotifications, emptySet())).isFalse()
        assertThat(canPostpone(OnboardingStep.NOTIFICATIONS, noNotifications, setOf(OnboardingStep.NOTIFICATIONS)))
            .isTrue()
        assertThat(canPostpone(OnboardingStep.OVERLAY, noOverlay, emptySet())).isTrue()
    }

    @Test
    fun `step status is the worst of its items`() {
        val both = report(HEALTHY_SNAPSHOT.copy(ignoringBatteryOptimizations = false, backgroundRestricted = true))

        assertThat(OnboardingStep.BATTERY.status(both)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(OnboardingStep.OEM_BACKGROUND.status(report(oem = false))).isEqualTo(HealthStatus.UNCONFIRMED)
    }
}
