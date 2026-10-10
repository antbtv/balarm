package com.antbtv.balarm.feature.missions.math

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Настройки Math (PRD §3.5): сложность 1..5 и число примеров 1..10. */
data class MathConfig(val level: Int = DEFAULT_LEVEL, val count: Int = DEFAULT_COUNT) {
    init {
        require(level in LEVELS && count in COUNTS) { "Out of range: level=$level count=$count" }
    }

    fun encode(): String = buildJsonObject {
        put(KEY_LEVEL, level)
        put(KEY_COUNT, count)
    }.toString()

    companion object {
        val LEVELS = 1..5
        val COUNTS = 1..10
        const val DEFAULT_LEVEL = 2
        const val DEFAULT_COUNT = 3
        private const val KEY_LEVEL = "level"
        private const val KEY_COUNT = "count"

        /** Толерантный разбор: мусор и выход за границы → значения по умолчанию / ближайшие допустимые. */
        @Suppress("TooGenericExceptionCaught") // любой сбой разбора = конфиг по умолчанию, миссия не должна падать
        fun parse(raw: String): MathConfig = try {
            val json = Json.parseToJsonElement(raw) as? JsonObject
            fun int(key: String, default: Int) = json?.get(key)?.jsonPrimitive?.int ?: default
            MathConfig(
                level = int(KEY_LEVEL, DEFAULT_LEVEL).coerceIn(LEVELS),
                count = int(KEY_COUNT, DEFAULT_COUNT).coerceIn(COUNTS),
            )
        } catch (_: Exception) {
            MathConfig()
        }
    }
}
