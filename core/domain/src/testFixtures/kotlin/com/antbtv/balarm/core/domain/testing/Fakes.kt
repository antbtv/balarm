package com.antbtv.balarm.core.domain.testing

import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.AlarmScheduler
import com.antbtv.balarm.core.domain.alarm.AlarmWithRuntime
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Часы, которые тест двигает вручную; зона тоже меняется (смена TZ). */
class MutableClock(var now: Instant, var zoneId: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now += duration
    }
}

class FakeAlarmRepository : AlarmRepository {
    private val alarms = MutableStateFlow<Map<AlarmId, Alarm>>(emptyMap())
    val runtimes = mutableMapOf<AlarmId, AlarmRuntimeState>()
    private var nextId = 1L
    var failOnLoad: Throwable? = null
    var failOnDelete: Throwable? = null

    /** Бросать при сохранении runtime этих будильников. */
    val failRuntimeFor = mutableSetOf<AlarmId>()

    /** Если задан — транзакция ждёт его (моделирует занятый движок). */
    var transactionGate: CompletableDeferred<Unit>? = null

    /** Сколько транзакций выполняется одновременно (должно быть ≤ 1). */
    var activeTransactions = 0
    var maxConcurrentTransactions = 0

    override fun observeAlarms(): Flow<List<Alarm>> = MutableStateFlow(alarms.value.values.toList())

    override suspend fun get(id: AlarmId): Alarm? = alarms.value[id]

    override suspend fun save(alarm: Alarm): AlarmId {
        require(!alarm.id.isTest) { "The test alarm id is reserved and never stored" }
        val id = if (alarm.id.isSaved) alarm.id else AlarmId(nextId++)
        alarms.value = alarms.value + (id to alarm.copy(id = id))
        return id
    }

    override suspend fun setEnabled(id: AlarmId, enabled: Boolean) {
        val alarm = alarms.value[id] ?: return
        alarms.value = alarms.value + (id to alarm.copy(enabled = enabled))
    }

    override suspend fun delete(id: AlarmId) {
        failOnDelete?.let { throw it }
        alarms.value = alarms.value - id
        runtimes -= id
    }

    override suspend fun loadAll(): List<AlarmWithRuntime> {
        failOnLoad?.let { throw it }
        return alarms.value.values.map { AlarmWithRuntime(it, runtimes[it.id]) }
    }

    override suspend fun getRuntime(id: AlarmId): AlarmRuntimeState? = runtimes[id]

    override suspend fun updateRuntime(state: AlarmRuntimeState) {
        if (state.alarmId in failRuntimeFor) throw IllegalStateException("runtime write failed")
        runtimes[state.alarmId] = state
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        activeTransactions++
        maxConcurrentTransactions = maxOf(maxConcurrentTransactions, activeTransactions)
        try {
            transactionGate?.await()
            kotlinx.coroutines.yield()
            return block()
        } finally {
            activeTransactions--
        }
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    val scheduled = mutableMapOf<AlarmId, ScheduleRequest>()
    val cancelled = mutableListOf<AlarmId>()
    var scheduleCalls = 0
    var accept = true

    /** Бросать исключение при планировании этих будильников. */
    val throwFor = mutableSetOf<AlarmId>()

    override fun schedule(request: ScheduleRequest): Boolean {
        scheduleCalls++
        if (request.alarmId in throwFor) throw IllegalStateException("scheduler failed")
        if (accept) scheduled[request.alarmId] = request
        return accept
    }

    override fun cancel(alarmId: AlarmId) {
        cancelled += alarmId
        scheduled -= alarmId
    }
}

class RecordingEventLog : AlarmEventLog {
    val events = mutableListOf<AlarmEvent>()

    @Synchronized
    override fun log(event: AlarmEvent) {
        events += event
    }
}
