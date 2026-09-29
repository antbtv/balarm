package com.antbtv.balarm.buildlogic.featureflags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsParserTest {

    @Test
    fun `when file is valid then entries keep order and values`() {
        val entries = parseFeatureFlags(
            """
            # comment
            feature.quotes=true

            feature.mission.math = false
            """.trimIndent(),
        )
        assertEquals(
            listOf(FeatureFlagEntry("feature.quotes", true), FeatureFlagEntry("feature.mission.math", false)),
            entries,
        )
    }

    @Test
    fun `when key is camelCase and dotted then constant name is upper snake case`() {
        assertEquals("MORNING_BRIEFING", FeatureFlagEntry("feature.morningBriefing", true).constantName)
        assertEquals("MISSION_MATH", FeatureFlagEntry("feature.mission.math", true).constantName)
    }

    @Test
    fun `when value is not boolean then parsing fails`() {
        assertThrows(FeatureFlagsFormatException::class.java) { parseFeatureFlags("feature.quotes=yes") }
    }

    @Test
    fun `when key has wrong prefix or format then parsing fails`() {
        assertThrows(FeatureFlagsFormatException::class.java) { parseFeatureFlags("quotes=true") }
        assertThrows(FeatureFlagsFormatException::class.java) { parseFeatureFlags("feature.Quotes=true") }
        assertThrows(FeatureFlagsFormatException::class.java) { parseFeatureFlags("feature.quotes") }
    }

    @Test
    fun `when key is duplicated or constants clash then parsing fails`() {
        assertThrows(FeatureFlagsFormatException::class.java) {
            parseFeatureFlags("feature.quotes=true\nfeature.quotes=false")
        }
        assertThrows(FeatureFlagsFormatException::class.java) {
            parseFeatureFlags("feature.missionMath=true\nfeature.mission.math=false")
        }
    }

    @Test
    fun `when file has no flags then parsing fails`() {
        assertThrows(FeatureFlagsFormatException::class.java) { parseFeatureFlags("# empty") }
    }

    @Test
    fun `rendered enum contains every entry`() {
        val code = renderFeatureEnum("a.b", listOf(FeatureFlagEntry("feature.quotes", false)))
        assertTrue(code.contains("package a.b"))
        assertTrue(code.contains("QUOTES(\"feature.quotes\", false),"))
    }
}
