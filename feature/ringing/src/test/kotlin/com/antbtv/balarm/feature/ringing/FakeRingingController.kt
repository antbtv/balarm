package com.antbtv.balarm.feature.ringing

import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow

/** Контроллер звонка для тестов UI: состояние двигает тест, команды считаются. */
class FakeRingingController(initial: RingingState = RingingState.Idle) : RingingController {
    override val state = MutableStateFlow(initial)
    var dismissCalls = 0
        private set
    var snoozeCalls = 0
        private set

    override fun dismiss() {
        dismissCalls++
    }

    override fun snooze() {
        snoozeCalls++
    }
}

fun ringing(label: String = "Morning run", canSnooze: Boolean = true, snoozesLeft: Int? = 2): RingingState.Ringing =
    RingingState.Ringing(
        alarm = Alarm(id = AlarmId(1), time = LocalTime.of(6, 30), label = label),
        startedAt = Instant.parse("2026-10-03T06:30:00Z"),
        canSnooze = canSnooze,
        snoozesLeft = snoozesLeft,
    )
