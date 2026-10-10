package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class EffectiveMissionTest {
    private val spec = MissionSpec(MissionTypeId.MATH, "{}")
    private val alarm = Alarm(time = LocalTime.NOON, mission = spec)

    @Test
    fun `available mission is effective`() {
        assertThat(alarm.effectiveMission { true }).isEqualTo(spec)
    }

    @Test
    fun `unavailable mission means the alarm dismisses without it`() {
        assertThat(alarm.effectiveMission { false }).isNull()
    }

    @Test
    fun `alarm without mission has none`() {
        assertThat(alarm.copy(mission = null).effectiveMission { true }).isNull()
    }
}
