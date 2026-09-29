package com.antbtv.balarm.buildlogic.featureflags

internal data class FeatureFlagEntry(val key: String, val enabled: Boolean) {
    /** `feature.mission.math` → `MISSION_MATH`, `feature.morningBriefing` → `MORNING_BRIEFING`. */
    val constantName: String
        get() = key.removePrefix(PREFIX)
            .split('.')
            .joinToString("_") { part -> part.replace(CAMEL_BOUNDARY, "$1_$2").uppercase() }
}

internal class FeatureFlagsFormatException(message: String) : IllegalArgumentException(message)

private const val PREFIX = "feature."
private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
private val KEY_PATTERN = Regex("""feature(\.[a-z][A-Za-z0-9]*)+""")

/** Разбирает config/features.properties; любая неоднозначность — ошибка сборки. */
internal fun parseFeatureFlags(text: String): List<FeatureFlagEntry> {
    val entries = mutableListOf<FeatureFlagEntry>()
    text.lineSequence().forEachIndexed { index, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        val lineNo = index + 1
        val parts = line.split('=', limit = 2)
        if (parts.size != 2) throw FeatureFlagsFormatException("line $lineNo: expected key=value, got '$line'")
        val key = parts[0].trim()
        val value = parts[1].trim()
        if (!KEY_PATTERN.matches(key)) {
            throw FeatureFlagsFormatException("line $lineNo: invalid key '$key', expected feature.<camelCase>[.<camelCase>]")
        }
        val enabled = when (value) {
            "true" -> true
            "false" -> false
            else -> throw FeatureFlagsFormatException("line $lineNo: value of '$key' must be true or false, got '$value'")
        }
        if (entries.any { it.key == key }) throw FeatureFlagsFormatException("line $lineNo: duplicate key '$key'")
        entries += FeatureFlagEntry(key, enabled)
    }
    val clashes = entries.groupBy { it.constantName }.filterValues { it.size > 1 }
    if (clashes.isNotEmpty()) {
        throw FeatureFlagsFormatException("keys map to the same constant: ${clashes.values.flatten().map { it.key }}")
    }
    if (entries.isEmpty()) throw FeatureFlagsFormatException("no feature flags declared")
    return entries
}

internal fun renderFeatureEnum(packageName: String, entries: List<FeatureFlagEntry>): String = buildString {
    appendLine("// Сгенерировано из config/features.properties задачей generateFeatureFlags. Не редактировать.")
    appendLine("package $packageName")
    appendLine()
    appendLine("/** Пользовательские фичи, которые можно выключить (PRD §3.12). [defaultEnabled] — значение из конфига. */")
    appendLine("enum class Feature(val key: String, val defaultEnabled: Boolean) {")
    entries.forEach { appendLine("    ${it.constantName}(\"${it.key}\", ${it.enabled}),") }
    appendLine("}")
}
