package com.antbtv.balarm.debug

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.model.feature.Feature
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugFeatureFlagProviderTest {

    private val provider = DebugFeatureFlagProvider(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = provider.resetAll()

    @Test
    fun `when nothing is overridden then config defaults are used`() {
        Feature.entries.forEach { assertThat(provider.isEnabled(it)).isEqualTo(it.defaultEnabled) }
    }

    @Test
    fun `when flag is overridden then override wins`() {
        val feature = Feature.CUSTOM_SOUNDS
        provider.setOverride(feature, !feature.defaultEnabled)

        assertThat(provider.isEnabled(feature)).isEqualTo(!feature.defaultEnabled)
        assertThat(provider.isOverridden(feature)).isTrue()
    }

    @Test
    fun `when override is set back to config value then it is not stored`() {
        val feature = Feature.CUSTOM_SOUNDS
        provider.setOverride(feature, !feature.defaultEnabled)

        provider.setOverride(feature, feature.defaultEnabled)

        assertThat(provider.isOverridden(feature)).isFalse()
    }

    @Test
    fun `when reset then all overrides are dropped`() {
        Feature.entries.forEach { provider.setOverride(it, !it.defaultEnabled) }

        provider.resetAll()

        Feature.entries.forEach {
            assertThat(provider.isEnabled(it)).isEqualTo(it.defaultEnabled)
            assertThat(provider.isOverridden(it)).isFalse()
        }
    }
}
