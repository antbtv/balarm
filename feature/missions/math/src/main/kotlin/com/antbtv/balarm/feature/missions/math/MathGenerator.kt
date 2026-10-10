package com.antbtv.balarm.feature.missions.math

import kotlin.random.Random

/** Пример и его ответ: всегда целый и положительный. */
data class MathProblem(val text: String, val answer: Int)

/**
 * Генератор примеров по уровням (PRD §3.5). Чистый Kotlin, детерминирован по [random]:
 * 1 — сложение/вычитание до 20; 2 — двузначные ±; 3 — таблица умножения; 4 — a×b ± c; 5 — (a+b)×c и a×b ± c×d.
 */
@Suppress("MagicNumber") // границы операндов — часть определения уровней
class MathGenerator(private val random: Random) {

    fun next(level: Int, previous: MathProblem? = null): MathProblem {
        var problem = generate(level)
        // Ограниченное число попыток: на любом уровне хватает вариантов, чтобы не повторить пример подряд.
        repeat(MAX_RETRIES) { if (problem.text == previous?.text) problem = generate(level) }
        return problem
    }

    fun sequence(level: Int, count: Int): List<MathProblem> {
        val result = mutableListOf<MathProblem>()
        repeat(count) { result += next(level, result.lastOrNull()) }
        return result
    }

    private fun generate(level: Int): MathProblem = when (level) {
        1 -> addOrSubtract(MIN_L1, MAX_L1)
        2 -> addOrSubtract(MIN_L2, MAX_L2)
        3 -> multiply()
        4 -> productPlusMinus()
        else -> if (random.nextBoolean()) sumTimes() else twoProducts()
    }

    private fun addOrSubtract(min: Int, max: Int): MathProblem {
        val a = random.nextInt(min, max + 1)
        val b = random.nextInt(min, max + 1)
        return if (random.nextBoolean()) {
            MathProblem("$a + $b", a + b)
        } else {
            val (big, small) = if (a >= b) a to b else b to a
            if (big == small) MathProblem("$a + $b", a + b) else MathProblem("$big − $small", big - small)
        }
    }

    private fun multiply(): MathProblem {
        val a = random.nextInt(2, 10)
        val b = random.nextInt(2, 13)
        return MathProblem("$a × $b", a * b)
    }

    private fun productPlusMinus(): MathProblem {
        val a = random.nextInt(2, 10)
        val b = random.nextInt(3, 10)
        val c = random.nextInt(2, a * b)
        return if (random.nextBoolean()) {
            MathProblem("$a × $b + $c", a * b + c)
        } else {
            MathProblem("$a × $b − $c", a * b - c)
        }
    }

    private fun sumTimes(): MathProblem {
        val a = random.nextInt(2, 10)
        val b = random.nextInt(2, 10)
        val c = random.nextInt(2, 10)
        return MathProblem("($a + $b) × $c", (a + b) * c)
    }

    private fun twoProducts(): MathProblem {
        val a = random.nextInt(2, 10)
        val b = random.nextInt(2, 10)
        val c = random.nextInt(2, 10)
        val d = random.nextInt(2, 10)
        val first = a * b
        val second = c * d
        return when {
            first == second -> MathProblem("$a × $b + $c × $d", first + second)
            random.nextBoolean() -> MathProblem("$a × $b + $c × $d", first + second)
            first > second -> MathProblem("$a × $b − $c × $d", first - second)
            else -> MathProblem("$c × $d − $a × $b", second - first)
        }
    }

    private companion object {
        const val MIN_L1 = 2
        const val MAX_L1 = 20
        const val MIN_L2 = 10
        const val MAX_L2 = 99
        const val MAX_RETRIES = 10
    }
}
