package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CodePointsTest {

    private val emoji = "\uD83D\uDE00" // 😀: суррогатная пара

    @Test
    fun `short string is returned as is`() {
        assertThat("abc".takeCodePoints(5)).isEqualTo("abc")
        assertThat("abc".takeCodePoints(3)).isEqualTo("abc")
    }

    @Test
    fun `long ascii string is cut to n`() {
        assertThat("abcdef".takeCodePoints(4)).isEqualTo("abcd")
        assertThat("abc".takeCodePoints(0)).isEmpty()
    }

    @Test
    fun `surrogate pair is never split`() {
        val text = "a" + emoji + emoji + "b"

        assertThat(text.takeCodePoints(2)).isEqualTo("a" + emoji)
        assertThat(text.takeCodePoints(3)).isEqualTo("a" + emoji + emoji)
        assertThat(text.takeCodePoints(1).last().isHighSurrogate()).isFalse()
    }

    @Test
    fun `lone surrogates are counted as one code point each and never throw`() {
        val broken = "a\uD83D".repeat(50)

        val cut = broken.takeCodePoints(Alarm.MAX_LABEL_LENGTH)

        assertThat(cut.codePointCount(0, cut.length)).isEqualTo(Alarm.MAX_LABEL_LENGTH)
        assertThat("\uD83D".takeCodePoints(1)).isEqualTo("\uD83D")
    }

    @Test
    fun `negative n is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { "abc".takeCodePoints(-1) }
    }
}
