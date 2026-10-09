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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
@Singleton
class TestAlarmRunner @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val store: TestAlarmStore,
    private val clock: Clock,
    private val log: AlarmEventLog,
) {
    private val _pending = MutableStateFlow<Instant?>(null)

    /** Момент запланированного теста; `null` после срабатывания, отмены или отказа системы. */
    val pending: StateFlow<Instant?> = _pending.asStateFlow()

    /**
     * Момент звонка или `null`, если система отказала или планировщик бросил исключение (одно место для редактора
     * и экрана здоровья). Повтор заменяет предыдущий.
     */
    fun schedule(alarm: Alarm, delay: Duration): Instant? {
        val at = clock.instant() + delay
        val previous = store.get()
        store.put(alarm.copy(id = AlarmId.TEST))
        val accepted = try {
            scheduler.schedule(ScheduleRequest(AlarmId.TEST, at, FireKind.REGULAR))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!accepted) {
            // Прежний тест (если был) остаётся запланированным — снимок у него не отбираем.
            previous?.let(store::put) ?: store.clear()
            // _pending не трогаем: прежний тест (если был) остаётся запланированным.
            log.log(AlarmEvent.ScheduleFailed(AlarmId.TEST, at))
            return null
        }
        _pending.value = at
        log.log(AlarmEvent.TestScheduled(at))
        return at
    }

    /** Отмена запланированного теста; снимок не трогаем (см. описание класса). */
    fun cancel() {
        try {
            scheduler.cancel(AlarmId.TEST)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Сбой отмены не должен ронять экран; звонок, если он остался, отработает как обычный тест.
        } finally {
            _pending.value = null
        }
    }

    /**
     * Звонок без «Отложить». Снимка нет (процесс умер между «Тест» и срабатыванием) — настройки по умолчанию:
     * в сомнении — звони.
     */
    fun decision(): FireDecision.Ring {
        _pending.value = null
        return FireDecision.Ring(
            alarm = store.get() ?: Alarm(
                id = AlarmId.TEST,
                time = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES),
            ),
            canSnooze = false,
            snoozesLeft = 0,
        )
    }

    companion object {
        /** FR-EDIT-10. */
        val EDITOR_DELAY: Duration = Duration.ofSeconds(5)

        /** FR-REL-7: «Тестовый будильник через 1 минуту» на экране здоровья. */
        val HEALTH_DELAY: Duration = Duration.ofMinutes(1)
    }
}
