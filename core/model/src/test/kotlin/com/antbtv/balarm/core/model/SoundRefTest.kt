package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SoundRefTest {

    @Test
    fun `decode inverts encode for every builtin and a custom sound`() {
        val refs = BuiltinSound.entries.map { SoundRef.Builtin(it) } + SoundRef.Custom(CustomSoundId(42))

        refs.forEach { assertThat(SoundRef.decode(it.encode())).isEqualTo(it) }
    }

    @Test
    fun `encoded form is stable`() {
        assertThat(SoundRef.DEFAULT.encode()).isEqualTo("builtin:alarm_default")
        assertThat(SoundRef.Custom(CustomSoundId(7)).encode()).isEqualTo("custom:7")
    }

    @Test
    fun `garbage and unknown keys decode to null`() {
        listOf("", "builtin:", "builtin:nope", "custom:", "custom:abc", "custom:0", "custom:-3", "alarm_default")
            .forEach { assertThat(SoundRef.decode(it)).isNull() }
    }

    @Test
    fun `builtin keys are unique and DEFAULT keeps its resource name`() {
        assertThat(BuiltinSound.entries.map { it.key }).containsNoDuplicates()
        assertThat(BuiltinSound.DEFAULT.key).isEqualTo("alarm_default")
        assertThat(BuiltinSound.fromKey("snd_bells")).isEqualTo(BuiltinSound.BELLS)
        assertThat(BuiltinSound.fromKey("x")).isNull()
    }

    @Test
    fun `custom id must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { CustomSoundId(0) }
    }
}
