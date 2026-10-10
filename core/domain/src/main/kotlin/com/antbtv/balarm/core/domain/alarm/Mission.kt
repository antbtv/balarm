package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import java.time.Duration

/** Какие миссии сейчас доступны: тип известен и его флаг включён (FR-FLAG-3). Реализация — реестр миссий. */
fun interface MissionCatalog {
    fun isAvailable(type: MissionTypeId): Boolean
}

/** Миссия, которую надо пройти на звонке; `null` — недоступный (выключен флаг, неизвестный тип) или отсутствует. */
fun Alarm.effectiveMission(catalog: MissionCatalog): MissionSpec? = mission?.takeIf { catalog.isAvailable(it.type) }

/** Политика миссии (FR-MIS-2); в MVP — константы, экран настроек — M7. */
object MissionPolicy {
    /** Бездействие в миссии: после него звук возвращается на 100 %, ввод сбрасывается. */
    val IDLE_TIMEOUT: Duration = Duration.ofSeconds(15)

    /** Коэффициент усиления плеера на время миссии (30 %). */
    const val DUCK_GAIN = 0.3f
}
