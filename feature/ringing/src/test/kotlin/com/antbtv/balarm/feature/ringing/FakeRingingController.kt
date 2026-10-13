package com.antbtv.balarm.feature.ringing

import com.antbtv.balarm.core.domain.alarm.MissionRun
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.MissionSpec
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

    var missionStarts = 0
        private set
    var missionCompletions = 0
        private set
    var missionLeaves = 0
        private set
    var missionTouches = 0
        private set
    var screenHidden = 0
        private set

    override fun startMission() {
        missionStarts++
    }

    override fun missionInteraction() {
        missionTouches++
    }

    override fun leaveMission() {
        missionLeaves++
    }

    override fun completeMission() {
        missionCompletions++
    }

    override fun missionScreenHidden() {
        screenHidden++
    }

    var screenPaused = 0
        private set
    var screenShown = 0
        private set

    override fun missionScreenPaused() {
        screenPaused++
    }

    override fun missionScreenShown() {
        screenShown++
    }
}

fun ringing(
    label: String = "Morning run",
    canSnooze: Boolean = true,
    snoozesLeft: Int? = 2,
    mission: MissionSpec? = null,
    missionRun: MissionRun? = null,
): RingingState.Ringing = RingingState.Ringing(
    alarm = Alarm(id = AlarmId(1), time = LocalTime.of(6, 30), label = label, mission = mission),
    startedAt = Instant.parse("2026-10-03T06:30:00Z"),
    canSnooze = canSnooze,
    snoozesLeft = snoozesLeft,
    mission = mission,
    missionRun = missionRun,
)
