package com.antbtv.balarm.feature.missions.api

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MissionRegistryTest {
    private val math = FakeMission(MissionTypeId.MATH, Feature.MISSION_MATH)
    private var mathEnabled = true
    private val registry = MissionRegistry(
        listOf(math),
        FeatureFlagProvider { it != Feature.MISSION_MATH || mathEnabled },
    )

    @Test
    fun `registered mission with enabled flag is available`() {
        assertThat(registry.isAvailable(MissionTypeId.MATH)).isTrue()
        assertThat(registry.available).containsExactly(math)
    }

    @Test
    fun `disabled flag hides the mission`() {
        mathEnabled = false

        assertThat(registry.isAvailable(MissionTypeId.MATH)).isFalse()
        assertThat(registry.available).isEmpty()
        assertThat(registry.definition(MissionTypeId.MATH)).isSameInstanceAs(math)
    }

    @Test
    fun `unknown type is not available and does not throw`() {
        assertThat(registry.isAvailable(MissionTypeId("memory"))).isFalse()
        assertThat(registry.definition(MissionTypeId("memory"))).isNull()
    }
}

private class FakeMission(override val type: MissionTypeId, override val feature: Feature) : MissionDefinition {
    override val titleRes = 0

    override fun defaultConfig() = "{}"

    override fun normalize(config: String) = config

    override fun summary(context: Context, config: String) = ""

    @Composable
    override fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier) = Unit

    @Composable
    override fun Content(config: String, session: MissionSession, modifier: Modifier) = Unit
}
