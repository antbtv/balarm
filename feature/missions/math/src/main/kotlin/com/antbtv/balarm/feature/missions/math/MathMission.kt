package com.antbtv.balarm.feature.missions.math

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import com.antbtv.balarm.feature.missions.api.MissionSession

/** Миссия Math как плагин (ADR-018): конфиг — JSON [MathConfig], разбирается толерантно. */
object MathMission : MissionDefinition {
    override val type: MissionTypeId = MissionTypeId.MATH
    override val feature: Feature = Feature.MISSION_MATH
    override val titleRes: Int = R.string.math_title

    override fun defaultConfig(): String = MathConfig().encode()

    override fun normalize(config: String): String = MathConfig.parse(config).encode()

    override fun summary(context: Context, config: String): String {
        val parsed = MathConfig.parse(config)
        return context.resources.getQuantityString(R.plurals.math_summary, parsed.count, parsed.level, parsed.count)
    }

    @Composable
    override fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier) {
        val parsed = remember(config) { MathConfig.parse(config) }
        MathConfigEditor(config = parsed, onChange = { onChange(it.encode()) }, modifier = modifier)
    }

    @Composable
    override fun Content(config: String, session: MissionSession, modifier: Modifier) {
        val parsed = remember(config) { MathConfig.parse(config) }
        MathMissionContent(config = parsed, session = session, modifier = modifier)
    }
}
