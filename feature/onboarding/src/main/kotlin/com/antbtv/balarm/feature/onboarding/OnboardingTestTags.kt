package com.antbtv.balarm.feature.onboarding

/**
 * Теги экрана онбординга. Раскладка шага (заголовок, кнопки, индикатор, предупреждение) —
 * `OnboardingStepTestTags` дизайн-системы.
 */
object OnboardingTestTags {
    /** Пустой фон, пока шаг не вычислен (загрузка или завершение). */
    const val BLANK = "onboardingBlank"
    const val OEM_OPEN_SETTINGS = "onboardingOemOpenSettings"
    const val OEM_GUIDE = "onboardingOemGuide"
    const val OEM_CONFIRM = "onboardingOemConfirm"

    /** Иллюстрация показанного шага: `onboardingIllustration_NOTIFICATIONS`. */
    fun illustration(step: OnboardingStep): String = "onboardingIllustration_${step.name}"
}
