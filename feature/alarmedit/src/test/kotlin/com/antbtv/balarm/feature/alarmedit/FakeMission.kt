package com.antbtv.balarm.feature.alarmedit

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import com.antbtv.balarm.feature.missions.api.MissionRegistry
import com.antbtv.balarm.feature.missions.api.MissionSession

/**
 * Миссия для тестов редактора: конфиг — число 1..5 строкой; всё вне диапазона `normalize` превращает в «2»
 * (по умолчанию). Сводка — «level N»; редактор настроек — кнопка «+1» ([UP]).
 */
internal object FakeMission : MissionDefinition {
    const val UP = "fake_mission_up"
    const val DEFAULT = "2"

    override val type = MissionTypeId.MATH
    override val feature = Feature.MISSION_MATH
    override val titleRes = R.string.alarm_edit_mission

    override fun defaultConfig() = DEFAULT

    override fun normalize(config: String): String = config.trim().toIntOrNull()?.takeIf { it in 1..5 }?.toString()
        ?: DEFAULT

    override fun summary(context: Context, config: String) = "level ${normalize(config)}"

    @Composable
    override fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier) {
        Column(modifier) {
            Button(onClick = { onChange("${config.toInt() + 1}") }, modifier = Modifier.testTag(UP)) {
                Text("+1")
            }
        }
    }

    @Composable
    override fun Content(config: String, session: MissionSession, modifier: Modifier) = Unit
}

/** Реестр с [FakeMission]; [enabled] — флаг `feature.mission.math`. */
internal fun missionRegistry(enabled: Boolean = true): MissionRegistry =
    MissionRegistry(listOf(FakeMission), FeatureFlagProvider { enabled })
