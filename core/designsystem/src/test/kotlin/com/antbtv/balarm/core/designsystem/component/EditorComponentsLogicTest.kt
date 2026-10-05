package com.antbtv.balarm.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Assert.assertThrows
import org.junit.Test

/** Чистая логика компонентов редактора: пресеты дней, метка в code points, размер текста на `primary`. */
class EditorComponentsLogicTest {

    @Test
    fun `preset matches only the exact set of days`() {
        assertThat(DayPreset.WEEKDAYS.matches(WEEKDAYS)).isTrue()
        assertThat(DayPreset.WEEKDAYS.matches(WEEKDAYS + DayOfWeek.SATURDAY)).isFalse()
        assertThat(DayPreset.WEEKDAYS.matches(WEEKDAYS - DayOfWeek.FRIDAY)).isFalse()
        assertThat(DayPreset.WEEKEND.matches(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY))).isTrue()
        assertThat(DayPreset.EVERY_DAY.matches(DayOfWeek.entries.toSet())).isTrue()
        assertThat(DayPreset.entries.none { it.matches(emptySet()) }).isTrue()
    }

    @Test
    fun `at most one preset matches any set`() {
        val sets = listOf(
            emptySet(),
            WEEKDAYS,
            DayPreset.WEEKEND.days,
            DayOfWeek.entries.toSet(),
            setOf(DayOfWeek.MONDAY),
        )
        sets.forEach { days -> assertThat(DayPreset.entries.count { it.matches(days) }).isAtMost(1) }
    }

    @Test
    fun `tap on an unselected preset sets its days`() {
        assertThat(DayPreset.WEEKEND.toggle(WEEKDAYS)).isEqualTo(DayPreset.WEEKEND.days)
        assertThat(DayPreset.EVERY_DAY.toggle(emptySet())).isEqualTo(DayOfWeek.entries.toSet())
        assertThat(DayPreset.WEEKDAYS.toggle(setOf(DayOfWeek.MONDAY))).isEqualTo(WEEKDAYS)
    }

    @Test
    fun `second tap on the selected preset clears the days`() {
        assertThat(DayPreset.WEEKDAYS.toggle(WEEKDAYS)).isEmpty()
        assertThat(DayPreset.EVERY_DAY.toggle(DayOfWeek.entries.toSet())).isEmpty()
    }

    @Test
    fun `label length counts code points`() {
        assertThat(labelLength("")).isEqualTo(0)
        assertThat(labelLength("abc")).isEqualTo(3)
        assertThat(labelLength("a$EMOJI")).isEqualTo(2)
        assertThat(labelLength("\uD83D")).isEqualTo(1)
    }

    @Test
    fun `input within the limit is accepted as is, keeping the IME selection`() {
        assertThat(edit("", "Gym $EMOJI")).isEqualTo(LabelEdit("Gym $EMOJI", cursor = null))
        assertThat(edit("", "a".repeat(MAX))).isEqualTo(LabelEdit("a".repeat(MAX), cursor = null))
        assertThat(edit("Gym", "")).isEqualTo(LabelEdit("", cursor = null))
    }

    @Test
    fun `paste is cut at the limit in code points without splitting a surrogate pair`() {
        // Эмодзи — 40-й code point (символы 39–40 в UTF-16): обрезка по Char разорвала бы пару.
        val result = edit("", "a".repeat(MAX - 1) + EMOJI + "b".repeat(10))

        val expected = "a".repeat(MAX - 1) + EMOJI
        assertThat(result).isEqualTo(LabelEdit(expected, cursor = expected.length))
        assertThat(labelLength(result.text)).isEqualTo(MAX)
    }

    @Test
    fun `emoji that would be the 41st code point is dropped whole`() {
        assertThat(edit("", "a".repeat(MAX) + EMOJI).text).isEqualTo("a".repeat(MAX))
    }

    @Test
    fun `emoji-only paste keeps whole emoji`() {
        assertThat(edit("", EMOJI.repeat(50)).text).isEqualTo(EMOJI.repeat(MAX))
    }

    @Test
    fun `line breaks become spaces before the limit is applied`() {
        assertThat(edit("", "Wake\nup")).isEqualTo(LabelEdit("Wake up", cursor = 7))
        assertThat(edit("", "Wake\r\nup").text).isEqualTo("Wake up")
        assertThat(edit("", "a\rb\u2028c\u2029d\u0085e").text).isEqualTo("a b c d e")
        assertThat(edit("", "ab\ncd", max = 3).text).isEqualTo("ab ")
    }

    @Test
    fun `CRLF at the limit is one space, not two characters`() {
        val previous = "a".repeat(MAX - 1)

        assertThat(edit(previous, previous + "\r\nb")).isEqualTo(LabelEdit("$previous ", cursor = MAX))
    }

    @Test
    fun `negative limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { edit("", "a", max = -1) }
    }

    @Test
    fun `typing into a full label is rejected`() {
        val full = "a".repeat(MAX)

        assertThat(edit(full, "b$full").text).isEqualTo(full)
        assertThat(edit(full, full + "b").text).isEqualTo(full)
        assertThat(edit(full, "a".repeat(20) + "b" + "a".repeat(20)).text).isEqualTo(full)
    }

    @Test
    fun `paste into the middle is cut, the tail survives and the cursor follows the accepted part`() {
        val previous = "start-end"
        val accepted = "x".repeat(MAX - previous.length)

        val result = edit(previous, "start-" + "x".repeat(50) + "end")

        assertThat(result).isEqualTo(LabelEdit("start-" + accepted + "end", cursor = "start-".length + accepted.length))
    }

    @Test
    fun `cut pasted fragment never splits a surrogate pair`() {
        val previous = "a".repeat(MAX - 2)
        // Влезают два code point: «b» и эмодзи целиком, следующее эмодзи отбрасывается целиком.
        assertThat(edit(previous, previous + "b" + EMOJI + EMOJI).text).isEqualTo(previous + "b" + EMOJI)
    }

    @Test
    fun `prefix boundary inside a surrogate pair is moved before the pair`() {
        // Ввод совпадает с прежним текстом по старшему суррогату U+1F601, но это другое эмодзи.
        val previous = "a".repeat(MAX - 1) + EMOJI
        val result = edit(previous, "a".repeat(MAX - 1) + "\uD83D\uDE01" + EMOJI)

        assertThat(result.text).isEqualTo(previous)
    }

    @Test
    fun `suffix boundary inside a surrogate pair is moved after the pair`() {
        // U+1F200 (D83C DE00) и EMOJI U+1F600 (D83D DE00) совпадают по младшему суррогату.
        val previous = EMOJI + "a".repeat(MAX - 1)
        val result = edit(previous, "x\uD83C\uDE00" + "a".repeat(MAX - 1))

        assertThat(result).isEqualTo(LabelEdit("x" + "a".repeat(MAX - 1), cursor = 1))
    }

    @Test
    fun `edit with an over-limit previous value falls back to cutting the end`() {
        val previous = "a".repeat(MAX + 5)

        assertThat(edit(previous, previous + "b")).isEqualTo(LabelEdit("a".repeat(MAX), cursor = MAX))
    }

    @Test
    fun `text on primary never renders smaller than the base size`() {
        assertThat(largeTextFontRangeSp(BASE_SP, 1f)).isEqualTo(BASE_SP..BASE_SP)
        // fontScale 2: можно ужать до 9.5sp × 2 = 19sp на экране, верх — 19sp × 2.
        assertThat(largeTextFontRangeSp(BASE_SP, 2f)).isEqualTo(BASE_SP / 2..BASE_SP)
        // fontScale 0.85: текст не уменьшается вслед за настройкой.
        val small = largeTextFontRangeSp(BASE_SP, SMALL_SCALE)
        assertThat(small.start * SMALL_SCALE).isWithin(TOLERANCE).of(BASE_SP)
        assertThat(small.endInclusive).isEqualTo(small.start)
    }

    private fun edit(previous: String, input: String, max: Int = MAX) = sanitizeLabelEdit(previous, input, max)

    private companion object {
        const val MAX = 40
        const val EMOJI = "\uD83D\uDE00"
        const val BASE_SP = 19f
        const val SMALL_SCALE = 0.85f
        const val TOLERANCE = 1e-4f
        val WEEKDAYS = DayPreset.WEEKDAYS.days
    }
}
