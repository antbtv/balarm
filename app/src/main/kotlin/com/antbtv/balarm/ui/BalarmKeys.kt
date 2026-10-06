package com.antbtv.balarm.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Ключи стека навигации (ADR-009 §2). Граф живёт только в `:app`; feature-модули ключей не знают. */
@Serializable
internal sealed interface BalarmKey : NavKey

@Serializable
internal data object AlarmListKey : BalarmKey

/** [alarmId] `null` — новый будильник. `Long`, а не `AlarmId`: value class в сериализации ключа не нужен. */
@Serializable
internal data class AlarmEditKey(val alarmId: Long?) : BalarmKey
