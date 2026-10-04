package com.antbtv.balarm.core.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** Будильник с его служебным состоянием одним запросом; Flow инвалидируется при изменении любой из таблиц. */
data class AlarmWithRuntimeRow(
    @Embedded val alarm: AlarmEntity,
    @Relation(parentColumn = "id", entityColumn = "alarm_id") val runtime: AlarmRuntimeEntity?,
)
