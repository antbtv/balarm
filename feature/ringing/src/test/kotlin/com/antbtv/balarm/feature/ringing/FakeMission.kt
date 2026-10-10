package com.antbtv.balarm.feature.ringing

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import com.antbtv.balarm.feature.missions.api.MissionRegistry
import com.antbtv.balarm.feature.missions.api.MissionSession

/**
 * Миссия для тестов хоста: «touch» — касание, «solve» — победа; показывает seed и сигнал сброса,
 * сразу сообщает прогресс 1/3.
 */
object FakeMission : MissionDefinition {
    const val TOUCH = "fake_mission_touch"
    const val SOLVE = "fake_mission_solve"
    const val INFO = "fake_mission_info"

    override val type = MissionTypeId("fake")
    override val feature = Feature.MISSION_MATH
    override val titleRes = R.string.ringing_dismiss

    override fun defaultConfig() = "{}"

    override fun normalize(config: String) = config

    override fun summary(context: Context, config: String) = "fake"

    @Composable
    override fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier) = Unit

    @Composable
    override fun Content(config: String, session: MissionSession, modifier: Modifier) {
        LaunchedEffect(session.seed) { session.onProgress(1, 3) }
        Column(modifier) {
            Text("seed=${session.seed} reset=${session.resetSignal}", Modifier.testTag(INFO))
            Button(onClick = session.onInteraction, modifier = Modifier.testTag(TOUCH)) { Text("touch") }
            Button(onClick = session.onCompleted, modifier = Modifier.testTag(SOLVE)) { Text("solve") }
        }
    }
}

val FakeMissionSpec = MissionSpec(FakeMission.type, "{}")

fun fakeRegistry(): MissionRegistry = MissionRegistry(listOf(FakeMission), FeatureFlagProvider { true })
