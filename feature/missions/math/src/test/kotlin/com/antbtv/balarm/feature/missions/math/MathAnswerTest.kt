package com.antbtv.balarm.feature.missions.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MathAnswerTest {

    private val problems = listOf(MathProblem("2 + 3", 5), MathProblem("10 − 4", 6))

    private fun MathAnswer.type(vararg keys: MathKey): Pair<MathAnswer, MathPress> {
        var state = this
        var last = MathPress.IGNORED
        keys.forEach { key ->
            val (next, press) = state.press(key, problems)
            state = next
            last = press
        }
        return state to last
    }

    @Test
    fun `digits are appended and erase removes the last one`() {
        val (typed, press) = MathAnswer().type(MathKey.Digit(1), MathKey.Digit(2))
        assertThat(typed.input).isEqualTo("12")
        assertThat(press).isEqualTo(MathPress.TYPED)

        val (erased, _) = typed.type(MathKey.Erase)
        assertThat(erased.input).isEqualTo("1")
    }

    @Test
    fun `erase and enter on empty input change nothing`() {
        assertThat(MathAnswer().press(MathKey.Erase, problems)).isEqualTo(MathAnswer() to MathPress.IGNORED)
        assertThat(MathAnswer().press(MathKey.Enter, problems)).isEqualTo(MathAnswer() to MathPress.IGNORED)
    }

    @Test
    fun `input is limited in length`() {
        val keys = Array<MathKey>(MathAnswer.MAX_INPUT_LENGTH + 2) { MathKey.Digit(9) }
        val (state, press) = MathAnswer().type(*keys)

        assertThat(state.input).hasLength(MathAnswer.MAX_INPUT_LENGTH)
        assertThat(press).isEqualTo(MathPress.IGNORED)
    }

    @Test
    fun `wrong answer clears input, counts the error and keeps the problem`() {
        val (state, press) = MathAnswer().type(MathKey.Digit(4), MathKey.Enter)

        assertThat(press).isEqualTo(MathPress.WRONG)
        assertThat(state).isEqualTo(MathAnswer(solved = 0, input = "", errors = 1))
    }

    @Test
    fun `right answers move on and the last one completes`() {
        val (first, firstPress) = MathAnswer().type(MathKey.Digit(5), MathKey.Enter)
        assertThat(firstPress).isEqualTo(MathPress.SOLVED)
        assertThat(first.solved).isEqualTo(1)

        val (done, donePress) = first.type(MathKey.Digit(6), MathKey.Enter)
        assertThat(donePress).isEqualTo(MathPress.COMPLETED)
        assertThat(done.isCompleted(problems.size)).isTrue()

        // После победы клавиатура ничего не делает: двойной тап не «решает» лишнего.
        assertThat(done.press(MathKey.Digit(1), problems)).isEqualTo(done to MathPress.IGNORED)
        assertThat(done.press(MathKey.Enter, problems)).isEqualTo(done to MathPress.IGNORED)
    }

    @Test
    fun `leading zero still compares by value`() {
        val (_, press) = MathAnswer().type(MathKey.Digit(0), MathKey.Digit(5), MathKey.Enter)
        assertThat(press).isEqualTo(MathPress.SOLVED)
    }

    @Test
    fun `reset signal clears only the current input and is applied once`() {
        val (solvedOne, _) = MathAnswer().type(MathKey.Digit(5), MathKey.Enter, MathKey.Digit(3))

        val reset = solvedOne.reset(signal = 1)
        assertThat(reset).isEqualTo(MathAnswer(solved = 1, input = "", resetsSeen = 1))

        val typed = reset.copy(input = "7")
        assertThat(typed.reset(signal = 1)).isEqualTo(typed) // тот же сигнал повторно (поворот) — без сброса
    }
}
