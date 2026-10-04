package com.antbtv.balarm.core.domain.schedule

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTicksTest {

    /** Часы на виртуальном времени теста; зону можно менять на лету. */
    private class TestClock(private val scope: TestScope, var zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = TestClock(scope, zone)

        override fun instant(): Instant = START.plusMillis(scope.testScheduler.currentTime)
    }

    @Test
    fun `ticks on minute boundaries without drifting`() = runTest {
        val clock = TestClock(this, ZoneOffset.UTC)

        minuteTicks(clock).test {
            assertThat(awaitItem()).isEqualTo(LocalDateTime.parse("2026-09-28T05:59:00"))
            assertThat(awaitItem()).isEqualTo(LocalDateTime.parse("2026-09-28T06:00:00"))
            assertThat(testScheduler.currentTime).isEqualTo(30_000) // ждал до границы минуты, а не целую минуту
            assertThat(awaitItem()).isEqualTo(LocalDateTime.parse("2026-09-28T06:01:00"))
            assertThat(testScheduler.currentTime).isEqualTo(90_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a zone change is visible on the next tick`() = runTest {
        val clock = TestClock(this, ZoneOffset.UTC)

        minuteTicks(clock).test {
            assertThat(awaitItem().hour).isEqualTo(5)
            clock.zoneId = ZoneOffset.ofHours(3)
            assertThat(awaitItem()).isEqualTo(LocalDateTime.parse("2026-09-28T09:00:00"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-09-28T05:59:30Z")
    }
}
