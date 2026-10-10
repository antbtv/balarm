package com.antbtv.balarm.feature.missions.api

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature

/**
 * Миссия как плагин (FR-MIS-4, ADR-018): ядро хранит только [MissionTypeId] и непрозрачный JSON-конфиг,
 * всё остальное знает сама миссия. Добавление новой миссии не требует правок ядра (скилл `add-mission`).
 */
interface MissionDefinition {
    val type: MissionTypeId

    /** Флаг, закрывающий миссию (FR-FLAG): выключен → миссия недоступна. */
    val feature: Feature

    @get:StringRes
    val titleRes: Int

    fun defaultConfig(): String

    /** Приводит любой (в т. ч. повреждённый) конфиг к допустимому; не бросает исключений. */
    fun normalize(config: String): String

    /** Короткая сводка для строки в редакторе, например «Уровень 2, 3 примера». */
    fun summary(context: Context, config: String): String

    /** Редактор настроек миссии; изменения отдаются через [onChange] уже нормализованными. */
    @Composable
    fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier)

    /** Сама миссия: полноэкранное содержимое под [MissionFrame]-ом хоста. */
    @Composable
    fun Content(config: String, session: MissionSession, modifier: Modifier)
}

/**
 * Связь миссии с хостом: хост (звонок или превью) создаёт сессию, миссия только сообщает о событиях.
 * [resetSignal] увеличивается при бездействии (FR-MIS-2) — миссия сбрасывает ввод текущей задачи.
 */
class MissionSession(
    val seed: Long,
    val isPreview: Boolean,
    val resetSignal: Int,
    val onInteraction: () -> Unit,
    val onProgress: (done: Int, total: Int) -> Unit,
    val onCompleted: () -> Unit,
)
