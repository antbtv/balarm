package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmIdTest {

    @Test
    fun `when value is positive then id is created`() {
        assertThat(AlarmId(42).value).isEqualTo(42)
    }

    @Test
    fun `when value is zero or negative then creation fails`() {
        assertThrows(IllegalArgumentException::class.java) { AlarmId(0) }
        assertThrows(IllegalArgumentException::class.java) { AlarmId(-1) }
    }
}
