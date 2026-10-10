package com.antbtv.balarm.feature.missions.math

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class MathGeneratorTest {

    @Test
    fun `every level gives positive integer answers matching the text over many seeds`() {
        for (level in MathConfig.LEVELS) {
            for (seed in 0 until SEEDS) {
                val problem = MathGenerator(Random(seed)).next(level)
                assertThat(problem.answer).isGreaterThan(0)
                assertThat(evaluate(problem.text)).isEqualTo(problem.answer)
            }
        }
    }

    @Test
    fun `no two identical problems in a row`() {
        for (level in MathConfig.LEVELS) {
            for (seed in 0 until SEQUENCES) {
                val list = MathGenerator(Random(seed)).sequence(level, MathConfig.COUNTS.last)
                assertThat(list.zipWithNext().none { (a, b) -> a.text == b.text }).isTrue()
            }
        }
    }

    @Test
    fun `sequence has the requested size and is deterministic for a seed`() {
        val a = MathGenerator(Random(7)).sequence(3, 5)
        val b = MathGenerator(Random(7)).sequence(3, 5)

        assertThat(a).hasSize(5)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `unknown level falls back to the hardest generator without throwing`() {
        assertThat(MathGenerator(Random(1)).next(99).answer).isGreaterThan(0)
    }

    @Test
    fun `config parses garbage to defaults and clamps ranges`() {
        assertThat(MathConfig.parse("not json")).isEqualTo(MathConfig())
        assertThat(MathConfig.parse("")).isEqualTo(MathConfig())
        assertThat(MathConfig.parse("[1,2]")).isEqualTo(MathConfig())
        assertThat(MathConfig.parse("""{"level":99,"count":-4}""")).isEqualTo(MathConfig(level = 5, count = 1))
        assertThat(MathConfig.parse("""{"level":"x"}""")).isEqualTo(MathConfig())
    }

    @Test
    fun `config round trips through json`() {
        val config = MathConfig(level = 4, count = 7)

        assertThat(MathConfig.parse(config.encode())).isEqualTo(config)
    }

    /** Независимый вычислитель текста примера: × и ÷ раньше + и −, скобки. */
    private fun evaluate(text: String): Int {
        val tokens = Regex("""\d+|[()+−×]""").findAll(text).map { it.value }.toList()
        var pos = 0

        fun peek() = tokens.getOrNull(pos)

        lateinit var expr: () -> Int

        fun atom(): Int {
            val t = tokens[pos++]
            if (t == "(") {
                val v = expr()
                pos++ // ")"
                return v
            }
            return t.toInt()
        }

        fun term(): Int {
            var v = atom()
            while (peek() == "×") {
                pos++
                v *= atom()
            }
            return v
        }
        expr = {
            var v = term()
            while (peek() == "+" || peek() == "−") {
                val op = tokens[pos++]
                val r = term()
                v = if (op == "+") v + r else v - r
            }
            v
        }
        return expr()
    }

    private companion object {
        const val SEEDS = 10_000
        const val SEQUENCES = 200
    }
}
