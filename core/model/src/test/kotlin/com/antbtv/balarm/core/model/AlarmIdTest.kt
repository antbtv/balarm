package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmIdTest {

    @Test
    fun `when value is positive then id is saved`() {
        assertThat(AlarmId(42).isSaved).isTrue()
    }

    @Test
    fun `unsaved id is zero and not saved`() {
        assertThat(AlarmId.UNSAVED.value).isEqualTo(0)
        assertThat(AlarmId.UNSAVED.isSaved).isFalse()
    }

    @Test
    fun `when value is negative then creation fails`() {
        assertThrows(IllegalArgumentException::class.java) { AlarmId(-1) }
    }
}
