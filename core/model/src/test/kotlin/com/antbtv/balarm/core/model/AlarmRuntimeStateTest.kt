package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmRuntimeStateTest {

    private val at = Instant.parse("2026-10-05T07:00:00Z")

    @Test
    fun `never fired means no fire for any moment`() {
        assertThat(AlarmRuntimeState(AlarmId(1)).hasFiredFor(at)).isFalse()
    }

    @Test
    fun `fired at or after the moment counts as fired for it`() {
        val state = AlarmRuntimeState(AlarmId(1), lastFiredAt = at)

        assertThat(state.hasFiredFor(at)).isTrue()
        assertThat(state.hasFiredFor(at.minusSeconds(1))).isTrue()
    }

    @Test
    fun `fired before the moment does not count`() {
        val state = AlarmRuntimeState(AlarmId(1), lastFiredAt = at.minusSeconds(1))

        assertThat(state.hasFiredFor(at)).isFalse()
    }

    @Test
    fun `test alarm never has runtime state`() {
        assertThrows(IllegalArgumentException::class.java) { AlarmRuntimeState(AlarmId.TEST) }
    }
}
