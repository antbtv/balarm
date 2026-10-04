package com.antbtv.balarm.core.model

import java.time.Duration

/**
 * Настройки «Отложить» (FR-EDIT-7).
 * [interval] `null` — отложить нельзя; [maxCount] `null` — без ограничения.
 */
data class SnoozeSettings(val interval: Duration?, val maxCount: Int?) {
    init {
        interval?.let {
            require(it >= MIN_INTERVAL && it <= MAX_INTERVAL) { "Snooze interval out of range: $it" }
            require(it.toSecondsPart() == 0 && it.toNanosPart() == 0) { "Snooze interval must be whole minutes: $it" }
        }
        maxCount?.let { require(it in 1..MAX_COUNT) { "Snooze max count out of range: $it" } }
    }

    val isEnabled: Boolean get() = interval != null

    /** Сколько раз ещё можно отложить после [used] откладываний; `null` — без ограничения. */
    fun remaining(used: Int): Int? = if (!isEnabled) 0 else maxCount?.let { (it - used).coerceAtLeast(0) }

    companion object {
        val MIN_INTERVAL: Duration = Duration.ofMinutes(1)
        val MAX_INTERVAL: Duration = Duration.ofMinutes(30)
        const val MAX_COUNT = 10

        /** Варианты интервала в редакторе (FR-EDIT-7). */
        val INTERVAL_OPTIONS: List<Duration> = listOf(1L, 3, 5, 10, 15, 20, 30).map(Duration::ofMinutes)

        /** Варианты лимита в редакторе (FR-EDIT-7); `null` — без ограничения. */
        val LIMIT_OPTIONS: List<Int?> = listOf(1, 2, 3, 5, 10, null)

        val DEFAULT = SnoozeSettings(interval = Duration.ofMinutes(5), maxCount = 3)
        val DISABLED = SnoozeSettings(interval = null, maxCount = null)
    }
}
