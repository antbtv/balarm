package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Снимок черновика для тестового звонка; живёт в памяти процесса, в БД не попадает (ADR-010 §3). */
interface TestAlarmStore {
    fun put(alarm: Alarm)

    fun get(): Alarm?

    fun clear()
}

@Singleton
class InMemoryTestAlarmStore @Inject constructor() : TestAlarmStore {
    @Volatile private var alarm: Alarm? = null

    override fun put(alarm: Alarm) {
        this.alarm = alarm
    }

    override fun get(): Alarm? = alarm

    override fun clear() {
        alarm = null
    }
}

/**
 * Тестовый звонок («Тест» в редакторе, тестовый будильник на экране здоровья — M3): настоящий `setAlarmClock`
 * с зарезервированным [AlarmId.TEST] и снимком черновика в памяти (ADR-010). Расписание пользователя и БД
 * не затрагиваются; [AlarmEngine] делегирует сюда `onFired` для [AlarmId.TEST]. Снимок после звонка не очищается:
 * он нужен только следующему срабатыванию, а очистка стёрла бы снимок уже запланированного нового теста.
 */
class TestAlarmRunner @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val store: TestAlarmStore,
    private val clock: Clock,
    private val log: AlarmEventLog,
) {
    /** Момент звонка или `null`, если система отказала (нет права на точные будильники). Повтор заменяет предыдущий. */
    fun schedule(alarm: Alarm, delay: Duration): Instant? {
        val at = clock.instant() + delay
        val previous = store.get()
        store.put(alarm.copy(id = AlarmId.TEST))
        if (!scheduler.schedule(ScheduleRequest(AlarmId.TEST, at, FireKind.REGULAR))) {
            // Прежний тест (если был) остаётся запланированным — снимок у него не отбираем.
            previous?.let(store::put) ?: store.clear()
            log.log(AlarmEvent.ScheduleFailed(AlarmId.TEST, at))
            return null
        }
        log.log(AlarmEvent.TestScheduled(at))
        return at
    }

    /**
     * Звонок без «Отложить». Снимка нет (процесс умер между «Тест» и срабатыванием) — настройки по умолчанию:
     * в сомнении — звони.
     */
    fun decision(): FireDecision.Ring = FireDecision.Ring(
        alarm = store.get() ?: Alarm(
            id = AlarmId.TEST,
            time = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES),
        ),
        canSnooze = false,
        snoozesLeft = 0,
    )

    companion object {
        /** FR-EDIT-10; для экрана здоровья (M3) — `Duration.ofMinutes(1)`. */
        val EDITOR_DELAY: Duration = Duration.ofSeconds(5)
    }
}
