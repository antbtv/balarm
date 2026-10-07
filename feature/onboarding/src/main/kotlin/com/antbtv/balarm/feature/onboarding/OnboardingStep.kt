package com.antbtv.balarm.feature.onboarding

import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthReport
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.health.Severity

/** Шаги онбординга в порядке показа (PRD §3.7, ADR-013 §3). */
enum class OnboardingStep {
    NOTIFICATIONS,
    EXACT_ALARMS,
    FULL_SCREEN_INTENT,
    OVERLAY,

    /** Оптимизация батареи; при режиме «Ограничено» — вариант «снимите ограничение». */
    BATTERY,
    OEM_BACKGROUND,
    DO_NOT_DISTURB,
}

private val OnboardingStep.item: HealthItem
    get() = when (this) {
        OnboardingStep.NOTIFICATIONS -> HealthItem.NOTIFICATIONS
        OnboardingStep.EXACT_ALARMS -> HealthItem.EXACT_ALARMS
        OnboardingStep.FULL_SCREEN_INTENT -> HealthItem.FULL_SCREEN_INTENT
        OnboardingStep.OVERLAY -> HealthItem.OVERLAY
        OnboardingStep.BATTERY -> HealthItem.BATTERY_OPTIMIZATION
        OnboardingStep.OEM_BACKGROUND -> HealthItem.OEM_BACKGROUND
        OnboardingStep.DO_NOT_DISTURB -> HealthItem.DO_NOT_DISTURB
    }

private fun OnboardingStep.items(): List<HealthItem> =
    if (this == OnboardingStep.BATTERY) listOf(HealthItem.BACKGROUND_RESTRICTION, item) else listOf(item)

/** Пункт здоровья, который «Разрешить» этого шага исправляет; режим «Ограничено» важнее оптимизации батареи. */
internal fun OnboardingStep.fixItem(report: HealthReport): HealthItem =
    if (this == OnboardingStep.BATTERY && report.status(HealthItem.BACKGROUND_RESTRICTION) == HealthStatus.PROBLEM) {
        HealthItem.BACKGROUND_RESTRICTION
    } else {
        item
    }

/** Статус шага: `OK` только если все его пункты `OK`; иначе худший (PROBLEM важнее UNCONFIRMED). */
internal fun OnboardingStep.status(report: HealthReport): HealthStatus {
    val statuses = items().map(report::status)
    return when {
        statuses.any { it == HealthStatus.PROBLEM } -> HealthStatus.PROBLEM
        statuses.any { it == HealthStatus.UNCONFIRMED } -> HealthStatus.UNCONFIRMED
        else -> HealthStatus.OK
    }
}

/** Важность шага — по его пунктам в состоянии проблемы. */
internal fun OnboardingStep.severity(report: HealthReport): Severity =
    items().filter { report.status(it) != HealthStatus.OK }.map { it.severity }.minOrNull() ?: Severity.INFO

/**
 * Текущий шаг вычисляется, а не хранится (ADR-013 §3): первый шаг, который не `OK` и не пропущен.
 * `null` — онбординг закончен.
 */
internal fun currentStep(report: HealthReport, skipped: Set<OnboardingStep>): OnboardingStep? =
    OnboardingStep.entries.firstOrNull { it !in skipped && it.status(report) != HealthStatus.OK }

/**
 * «Позже» у рекомендуемых и информационных шагов — сразу; у критичных — только после попытки («Продолжить без
 * этого»): отказаться можно, застрять нельзя.
 */
internal fun canPostpone(step: OnboardingStep, report: HealthReport, attempted: Set<OnboardingStep>): Boolean =
    step.severity(report) != Severity.CRITICAL || step in attempted
