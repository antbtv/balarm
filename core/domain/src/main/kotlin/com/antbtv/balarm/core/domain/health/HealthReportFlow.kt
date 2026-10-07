package com.antbtv.balarm.core.domain.health

import com.antbtv.balarm.core.domain.alarm.AlarmWithRuntime
import com.antbtv.balarm.core.domain.alarm.unscheduledCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Живой отчёт о здоровье: свежий [PermissionSnapshot] (его обновляет экран на `ON_RESUME`), подтверждения пользователя
 * и будильники, которые система отказалась планировать (ADR-012, ADR-015). Общий для экранов списка, настроек,
 * здоровья и онбординга.
 */
fun healthReportFlow(
    snapshots: Flow<PermissionSnapshot>,
    setup: Flow<SetupState>,
    alarms: Flow<List<AlarmWithRuntime>>,
): Flow<HealthReport> = combine(
    snapshots,
    setup,
    // Сбой хранилища не должен ронять экран: без данных о будильниках отчёт строится по разрешениям.
    alarms.map { it.unscheduledCount() }.catch { emit(0) }.distinctUntilChanged(),
    ::healthReport,
).distinctUntilChanged()
