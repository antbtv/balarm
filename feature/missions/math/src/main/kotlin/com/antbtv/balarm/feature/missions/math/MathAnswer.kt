package com.antbtv.balarm.feature.missions.math

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** Клавиша клавиатуры Math: ответ вводится только цифрами (все ответы целые и положительные). */
sealed interface MathKey {
    data class Digit(val value: Int) : MathKey {
        init {
            require(value in DIGITS) { "Not a digit: $value" }
        }

        private companion object {
            val DIGITS = 0..9
        }
    }

    data object Erase : MathKey

    data object Enter : MathKey
}

/** Что произошло после нажатия. */
enum class MathPress {
    /** Ввод изменился (цифра, стирание). */
    TYPED,

    /** Нажатие ничего не изменило: пустой ввод, лимит длины, миссия уже пройдена. */
    IGNORED,

    /** Неверный ответ: ввод очищен, пример тот же. */
    WRONG,

    /** Верно, следующий пример. */
    SOLVED,

    /** Верно, и это был последний пример. */
    COMPLETED,
}

/**
 * Прогресс Math (чистая логика, без Compose-эффектов): решено [solved] примеров, ввод текущего — [input].
 * [errors] растёт на каждый неверный ответ (сигнал тряски), [resetsSeen] — последний обработанный
 * `MissionSession.resetSignal`.
 */
@Immutable
data class MathAnswer(val solved: Int = 0, val input: String = "", val errors: Int = 0, val resetsSeen: Int = 0) {

    fun isCompleted(total: Int): Boolean = solved >= total

    fun press(key: MathKey, problems: List<MathProblem>): Pair<MathAnswer, MathPress> {
        if (isCompleted(problems.size)) return this to MathPress.IGNORED
        return when (key) {
            is MathKey.Digit -> if (input.length >= MAX_INPUT_LENGTH) {
                this to MathPress.IGNORED
            } else {
                copy(input = input + key.value) to MathPress.TYPED
            }

            MathKey.Erase -> if (input.isEmpty()) {
                this to MathPress.IGNORED
            } else {
                copy(input = input.dropLast(1)) to MathPress.TYPED
            }

            MathKey.Enter -> submit(problems)
        }
    }

    /** Бездействие (FR-MIS-2): ввод текущего примера стирается, решённые остаются. */
    fun reset(signal: Int): MathAnswer = if (signal == resetsSeen) this else copy(input = "", resetsSeen = signal)

    private fun submit(problems: List<MathProblem>): Pair<MathAnswer, MathPress> {
        val value = input.toIntOrNull() ?: return this to MathPress.IGNORED
        if (value != problems[solved].answer) return copy(input = "", errors = errors + 1) to MathPress.WRONG
        val next = copy(solved = solved + 1, input = "")
        return next to if (next.isCompleted(problems.size)) MathPress.COMPLETED else MathPress.SOLVED
    }

    companion object {
        /** С запасом к самому длинному ответу (3 цифры) — и без переполнения `Int`. */
        const val MAX_INPUT_LENGTH = 6

        val Saver: Saver<MathAnswer, Any> = listSaver(
            save = { listOf(it.solved, it.input, it.errors, it.resetsSeen) },
            restore = {
                MathAnswer(
                    solved = it[0] as Int,
                    input = it[1] as String,
                    errors = it[2] as Int,
                    resetsSeen = it[3] as Int,
                )
            },
        )
    }
}
