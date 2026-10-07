package com.antbtv.balarm.core.domain.health

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HealthReportTest {

    private val healthy = PermissionSnapshot(
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
    private val confirmed = SetupState(onboardingCompleted = true, oemBackgroundConfirmed = true)

    @Test
    fun `everything fine gives all OK and no banner`() {
        val report = healthReport(healthy, confirmed, unscheduledAlarms = 0)

        assertThat(report.checks.map { it.item }).containsExactlyElementsIn(HealthItem.entries).inOrder()
        assertThat(report.checks.map { it.status }.toSet()).containsExactly(HealthStatus.OK)
        assertThat(report.needsAttention).isFalse()
    }

    @Test
    fun `each critical problem raises the banner`() {
        val broken = mapOf(
            HealthItem.NOTIFICATIONS to healthy.copy(notificationsEnabled = false),
            HealthItem.EXACT_ALARMS to healthy.copy(exactAlarms = false),
            HealthItem.FULL_SCREEN_INTENT to healthy.copy(fullScreenIntent = false),
            HealthItem.BACKGROUND_RESTRICTION to healthy.copy(backgroundRestricted = true),
        )
        broken.forEach { (item, snapshot) ->
            val report = healthReport(snapshot, confirmed, 0)
            assertThat(report.status(item)).isEqualTo(HealthStatus.PROBLEM)
            assertThat(report.needsAttention).isTrue()
        }
    }

    @Test
    fun `disabled ringing channel is a notifications problem`() {
        val report = healthReport(healthy.copy(ringingChannelEnabled = false), confirmed, 0)

        assertThat(report.status(HealthItem.NOTIFICATIONS)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(report.needsAttention).isTrue()
    }

    @Test
    fun `unscheduled alarms are a critical scheduling problem`() {
        val report = healthReport(healthy, confirmed, unscheduledAlarms = 2)

        assertThat(report.status(HealthItem.SCHEDULING)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(report.unscheduledAlarms).isEqualTo(2)
        assertThat(report.needsAttention).isTrue()
    }

    @Test
    fun `recommended and info problems do not raise the banner`() {
        val snapshot = healthy.copy(
            overlay = false,
            ignoringBatteryOptimizations = false,
            alarmsAllowedByDnd = false,
            alarmVolumeMuted = true,
        )
        val report = healthReport(snapshot, SetupState(), 0)

        assertThat(report.status(HealthItem.OVERLAY)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(report.status(HealthItem.BATTERY_OPTIMIZATION)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(report.status(HealthItem.DO_NOT_DISTURB)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(report.status(HealthItem.ALARM_VOLUME)).isEqualTo(HealthStatus.PROBLEM)
        assertThat(report.status(HealthItem.OEM_BACKGROUND)).isEqualTo(HealthStatus.UNCONFIRMED)
        assertThat(report.needsAttention).isFalse()
    }

    @Test
    fun `oem item follows the user confirmation only`() {
        assertThat(healthReport(healthy, SetupState(), 0).status(HealthItem.OEM_BACKGROUND))
            .isEqualTo(HealthStatus.UNCONFIRMED)
        assertThat(healthReport(healthy, confirmed, 0).status(HealthItem.OEM_BACKGROUND))
            .isEqualTo(HealthStatus.OK)
    }

    @Test
    fun `severities match the ADR table`() {
        val critical = HealthItem.entries.filter { it.severity == Severity.CRITICAL }
        assertThat(critical).containsExactly(
            HealthItem.NOTIFICATIONS,
            HealthItem.EXACT_ALARMS,
            HealthItem.FULL_SCREEN_INTENT,
            HealthItem.BACKGROUND_RESTRICTION,
            HealthItem.SCHEDULING,
        )
    }
}
