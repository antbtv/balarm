package com.antbtv.balarm.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Ключи стеков навигации (ADR-009 §2, ADR-014 §1). Граф живёт только в `:app`; feature-модули ключей не знают.
 * Каждый новый ключ регистрируется в `NavConfiguration` (`NavKeysTest`).
 */
@Serializable
internal sealed interface BalarmKey : NavKey

/** Корень вкладки «Будильники». */
@Serializable
internal data object AlarmListKey : BalarmKey

/** Корень вкладки «Настройки». */
@Serializable
internal data object SettingsKey : BalarmKey

/** [alarmId] `null` — новый будильник. `Long`, а не `AlarmId`: value class в сериализации ключа не нужен. */
@Serializable
internal data class AlarmEditKey(val alarmId: Long?) : BalarmKey

/** «Здоровье будильника»: из баннера списка — в стеке будильников, из настроек — в стеке настроек. */
@Serializable
internal data object HealthKey : BalarmKey

@Serializable
internal data object AboutKey : BalarmKey

/** Онбординг — отдельный режим без вкладок (ADR-013 §2). */
@Serializable
internal data object OnboardingKey : BalarmKey

/**
 * Пикер мелодии из редактора (ADR-016 §8). [selected] — `SoundRef.encode()` текущей мелодии черновика: строка,
 * а не `SoundRef`, — ключу не нужен сериализатор модели. Выбор возвращается в редактор результатом
 * [BalarmNavState.pickSound] (ADR-009 §3).
 */
@Serializable
internal data class SoundPickerKey(val selected: String) : BalarmKey

/** «Мои мелодии»: из пикера — в стеке будильников, из настроек — в стеке настроек. */
@Serializable
internal data object SoundLibraryKey : BalarmKey
