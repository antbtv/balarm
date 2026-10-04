package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.TriggerKind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import org.junit.Test

class AlarmScheduleTest {

    private val now = Instant.parse("2026-09-28T05:00:00Z")
    private val id = AlarmId(1)
    private val alarm = Alarm(id = id, time = LocalTime.of(6, 30))

    private fun item(
        enabled: Boolean = true,
        next: Instant? = now.plusSeconds(3_600),
        kind: TriggerKind = TriggerKind.REGULAR,
        hasRuntime: Boolean = true,
    ) = AlarmWithRuntime(
        alarm.copy(enabled = enabled),
        AlarmRuntimeState(id, next, kind).takeIf { hasRuntime },
    )

    @Test
    fun `enabled alarm shows its planned regular trigger`() {
        val item = item()

        assertThat(item.upcomingTrigger(now)).isEqualTo(now.plusSeconds(3_600))
        assertThat(item.isActive(now)).isTrue()
    }

    @Test
    fun `a trigger in the past is ignored until it is rescheduled`() {
        val item = item(next = now.minusSeconds(1))

        assertThat(item.upcomingTrigger(now)).isNull()
        assertThat(item.upcomingTrigger(now.minusSeconds(10))).isEqualTo(now.minusSeconds(1))
        assertThat(item(next = now).upcomingTrigger(now)).isNull() // «ровно сейчас» — уже не будущее
    }

    @Test
    fun `without runtime or planned trigger there is nothing upcoming`() {
        assertThat(item(hasRuntime = false).upcomingTrigger(now)).isNull()
        assertThat(item(next = null).upcomingTrigger(now)).isNull()
    }

    @Test
    fun `a disabled alarm without a snooze is inactive`() {
        val item = item(enabled = false)

        assertThat(item.upcomingTrigger(now)).isNull()
        assertThat(item.isActive(now)).isFalse()
    }

    @Test
    fun `a disabled one shot with a pending snooze is still active`() {
        val item = item(enabled = false, next = now.plusSeconds(300), kind = TriggerKind.SNOOZE)

        assertThat(item.upcomingTrigger(now)).isEqualTo(now.plusSeconds(300))
        assertThat(item.isActive(now)).isTrue()
    }

    @Test
    fun `a pending catch up counts like a snooze`() {
        val item = item(enabled = false, next = now.plusSeconds(3), kind = TriggerKind.CATCH_UP)

        assertThat(item.isActive(now)).isTrue()
    }

    @Test
    fun `a snooze that already rang no longer keeps a disabled alarm active`() {
        val item = item(enabled = false, next = now.minusSeconds(5), kind = TriggerKind.SNOOZE)

        assertThat(item.isActive(now)).isFalse()
    }

    @Test
    fun `next trigger is the earliest upcoming one across alarms`() {
        val list = listOf(
            item(next = now.plusSeconds(7_200)),
            item(next = now.plusSeconds(600)),
            item(enabled = false, next = null),
            item(next = now.minusSeconds(60)),
        )

        assertThat(list.nextTrigger(now)).isEqualTo(now.plusSeconds(600))
    }

    @Test
    fun `next trigger is null for an empty list or when nothing is active`() {
        assertThat(emptyList<AlarmWithRuntime>().nextTrigger(now)).isNull()
        assertThat(listOf(item(enabled = false, next = null)).nextTrigger(now)).isNull()
    }

    @Test
    fun `a pending snooze beats a later regular trigger of another alarm`() {
        val list = listOf(
            item(next = now.plusSeconds(3_600)),
            item(enabled = false, next = now.plusSeconds(120), kind = TriggerKind.SNOOZE),
        )

        assertThat(list.nextTrigger(now)).isEqualTo(now.plusSeconds(120))
    }
}
