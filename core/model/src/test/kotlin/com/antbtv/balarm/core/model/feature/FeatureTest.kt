package com.antbtv.balarm.core.model.feature

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Properties
import org.junit.Test

class FeatureTest {

    private val config: Map<String, Boolean> = Properties()
        .apply { File("../../config/features.properties").reader().use(::load) }
        .entries
        .associate { (key, value) -> key.toString() to value.toString().toBooleanStrict() }

    @Test
    fun `every config key has a generated feature and vice versa`() {
        assertThat(Feature.entries.map { it.key }).containsExactlyElementsIn(config.keys)
    }

    @Test
    fun `generated defaults match config values`() {
        Feature.entries.forEach { feature ->
            assertThat(feature.defaultEnabled).isEqualTo(config.getValue(feature.key))
        }
    }

    @Test
    fun `config provider returns config defaults`() {
        Feature.entries.forEach { feature ->
            assertThat(ConfigFeatureFlagProvider.isEnabled(feature)).isEqualTo(feature.defaultEnabled)
        }
    }
}
