package com.antbtv.balarm.feature.ringing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.antbtv.balarm.feature.missions.api.MissionFrame
import com.antbtv.balarm.feature.missions.api.MissionSession

/**
 * Хост миссии на звонке (ADR-019 §1): `MissionFrame` + содержимое плагина, сессия — поверх событий экрана.
 * Back не закрывает экран, а возвращает на звонок ([RingingEvent.MissionBack], FR-MIS-3); колбэк регистрируется
 * позже поглотителя Back в `RingingActivity`, поэтому срабатывает первым.
 */
@Composable
internal fun MissionHost(mission: MissionUi, onEvent: (RingingEvent) -> Unit, modifier: Modifier = Modifier) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    BackHandler { currentOnEvent(RingingEvent.MissionBack) }
    val session = remember(mission.seed, mission.resetSignal) {
        MissionSession(
            seed = mission.seed,
            isPreview = false,
            resetSignal = mission.resetSignal,
            onInteraction = { currentOnEvent(RingingEvent.MissionInteraction) },
            onProgress = { done, total -> currentOnEvent(RingingEvent.MissionProgress(done, total)) },
            onCompleted = { currentOnEvent(RingingEvent.MissionCompleted) },
        )
    }
    MissionFrame(
        done = mission.done,
        total = mission.total,
        idleKey = mission.interactions,
        idleTimeout = mission.idleTimeout,
        modifier = modifier.testTag(RingingTestTags.MISSION),
    ) {
        mission.definition.Content(config = mission.config, session = session, modifier = Modifier)
    }
}
