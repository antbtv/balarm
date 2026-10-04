package com.antbtv.balarm.core.alarm.ring

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.antbtv.balarm.core.alarm.AlarmIntents
import com.antbtv.balarm.core.alarm.notification.AlarmNotifications
import com.antbtv.balarm.core.alarm.notification.RingingActions
import com.antbtv.balarm.core.alarm.sound.AlarmSoundPlayer
import com.antbtv.balarm.core.alarm.sound.AlarmVibrator
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.AlarmScheduler
import com.antbtv.balarm.core.domain.alarm.DismissReason
import com.antbtv.balarm.core.domain.alarm.FireDecision
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.RingingPolicy
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.domain.alarm.SnoozeResult
import com.antbtv.balarm.core.model.AlarmId
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Сессия звонка (ADR-007 §2–7): foreground service `systemExempted`, звук и вибрация, WakeLock на весь звонок.
 *
 * * Срабатывания идут по одному через канал: каждое сразу отдаётся движку (он фиксирует его и планирует
 *   следующее), даже если сейчас звонит другой будильник — в очередь (FR-RING-7) встаёт готовое решение.
 * * «Отключить», «Отложить» и автостоп (FR-RING-6) действуют сразу, не дожидаясь решения по другому срабатыванию.
 * * Если решение не готово за [FIRST_SOUND_DEADLINE] (холодная БД после загрузки), звук начинается раньше.
 * * Ошибка любой команды логируется и не роняет процесс; падение процесса посреди звонка возвращает звонок
 *   через [CRASH_RESUME_DELAY] (crash re-arm, NFR-5).
 * * Состояние меняется только на главном потоке; сервис останавливается только в [stopIfIdle].
 */
@AndroidEntryPoint
@Suppress("TooManyFunctions")
class RingingService : Service() {

    @Inject lateinit var engine: AlarmEngine

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var sound: AlarmSoundPlayer

    @Inject lateinit var vibrator: AlarmVibrator

    @Inject lateinit var notifications: AlarmNotifications

    @Inject lateinit var controller: RingingControllerImpl

    @Inject lateinit var log: AlarmEventLog

    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fires = Channel<ScheduleRequest>(Channel.UNLIMITED)
    private val waiting = ArrayDeque<FireDecision.Ring>()
    private var current: FireDecision.Ring? = null
    private var startedAt: Instant = Instant.EPOCH
    private var autoStop: Job? = null

    /** Срабатывание, по которому движок сейчас решает. */
    private var deciding: ScheduleRequest? = null

    /** Звук пущен до решения движка (watchdog); решение его подхватывает или гасит. */
    private var earlySound = false
    private var sessionLock: PowerManager.WakeLock? = null
    private var lastStartId = 0

    /** Принятые срабатывания и незаписанные результаты: пока они есть, сервис не останавливается. */
    private var unhandled = 0
    private var destroyed = false
    private var crashGuard: CrashGuard? = null

    /** Будильники сессии для crash re-arm: обработчик падения может читать с любого потока. */
    @Volatile private var rearmIds: List<AlarmId> = emptyList()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (request in fires) {
                safely("ring") { fire(request) }
                unhandled--
                stopIfIdle()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            // Пришёл через startForegroundService — startForeground обязателен, даже для битого интента.
            AlarmIntents.ACTION_RING -> if (enterForeground(AlarmIntents.alarmId(intent))) {
                AlarmIntents.parse(intent)?.let {
                    unhandled++
                    fires.trySend(it)
                }
            }

            AlarmIntents.ACTION_DISMISS -> AlarmIntents.alarmId(intent)?.let { id ->
                scope.launch { command("dismiss") { dismiss(id) } }
            }

            AlarmIntents.ACTION_SNOOZE -> AlarmIntents.alarmId(intent)?.let { id ->
                scope.launch { command("snooze") { snooze(id) } }
            }
        }
        stopIfIdle()
        return START_NOT_STICKY // после гибели процесса звонок возвращает crash re-arm, а не рестарт сервиса
    }

    override fun onDestroy() {
        destroyed = true
        scope.cancel()
        current?.let { log.log(AlarmEvent.RingingStopped(it.alarm.id, "destroyed")) }
        waiting.forEach { log.log(AlarmEvent.RingingStopped(it.alarm.id, "destroyed")) }
        current = null
        waiting.clear()
        rearmIds = emptyList()
        silence()
        crashGuard?.uninstall()
        controller.publish(RingingState.Idle)
        sessionLock?.let { if (it.isHeld) it.release() }
        RingingWakeLocks.releaseDelivery()
        super.onDestroy()
    }

    // region Foreground и защита от падения

    /**
     * Самое первое действие — до любого I/O, иначе система убьёт сервис за неуспевший startForeground.
     * При идущем звонке показывает его полное уведомление, а не пустое — кнопки не пропадают.
     */
    @Suppress("TooGenericExceptionCaught") // любой отказ системы → fallback-уведомление, а не тишина
    private fun enterForeground(id: AlarmId?): Boolean {
        try {
            startForeground(
                AlarmNotifications.RINGING_ID,
                currentNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } catch (e: Exception) {
            notifyFallback(this) { notifications }
            log.log(AlarmEvent.ForegroundStartFailed(id ?: AlarmId.UNSAVED, e.javaClass.simpleName))
            RingingWakeLocks.releaseDelivery()
            stopIfIdle() // идущий звонок не трогаем
            return false
        }
        val lock = sessionLock ?: RingingWakeLocks.newSessionLock(this).also { sessionLock = it }
        lock.acquire(RingingWakeLocks.SESSION_TIMEOUT.toMillis())
        RingingWakeLocks.releaseDelivery()
        if (crashGuard == null) crashGuard = CrashGuard(::rearmAfterCrash).also { it.install() }
        return true
    }

    /**
     * Вызывается из обработчика необработанного исключения (ADR-007 §7): синхронно ставит звонящий, ожидающий
     * и решаемый будильники на RESUME через [CRASH_RESUME_DELAY]. RESUME заменяет в системе обычное следующее
     * срабатывание этого будильника — его вернёт `dismiss` (в т.ч. автостоп) после возобновлённого звонка.
     */
    private fun rearmAfterCrash() {
        val at = clock.instant() + CRASH_RESUME_DELAY
        rearmIds.forEach { id ->
            scheduler.schedule(ScheduleRequest(id, at, FireKind.RESUME))
            log.log(AlarmEvent.CrashRearmed(id, at))
        }
    }

    // endregion

    // region Срабатывание и очередь

    private suspend fun fire(request: ScheduleRequest) {
        deciding = request
        updateRearmIds()
        // deciding снимается после ring/queue: снимок для crash re-arm не пустеет между решением и звонком.
        try {
            val watchdog = if (current == null) scope.launch { startEarly(request.alarmId) } else null
            val decision = try {
                engine.onFired(request.alarmId, request.triggerAt, request.kind) // сам не бросает: деградирует
            } finally {
                watchdog?.cancel()
            }
            when (decision) {
                is FireDecision.Skip -> if (earlySound) {
                    earlySound = false
                    silence()
                    next()
                }

                is FireDecision.Ring -> {
                    if (current == null) ring(decision) else queue(decision)
                    // Движок был занят — срабатывание не записано: дописать, когда освободится.
                    if (decision.degraded) {
                        scope.launch {
                            record("record_fire") {
                                engine.recordDegraded(request.alarmId, request.triggerAt, request.kind)
                            }
                        }
                    }
                }
            }
        } finally {
            deciding = null
            updateRearmIds()
        }
    }

    private suspend fun startEarly(id: AlarmId) {
        delay(FIRST_SOUND_DEADLINE.toMillis())
        if (current != null) return
        earlySound = true
        sound.start(onFallback = vibrator::start)
        vibrator.start()
        log.log(AlarmEvent.RingingStarted(id, degraded = true))
    }

    private fun queue(decision: FireDecision.Ring) {
        waiting.addLast(decision)
        updateRearmIds()
        log.log(AlarmEvent.RingingQueued(decision.alarm.id))
        publish()
    }

    private fun ring(decision: FireDecision.Ring) {
        current = decision
        // Каждый звонок очереди — свои 30 мин до автостопа: WakeLock продлевается, а не тянется от первого.
        sessionLock?.acquire(RingingWakeLocks.SESSION_TIMEOUT.toMillis())
        updateRearmIds()
        startedAt = clock.instant()
        cancelMissed(decision.alarm.id)
        if (earlySound) {
            earlySound = false // звук уже идёт; вибрация — по настройке будильника
            if (!decision.alarm.vibrate) vibrator.stop()
        } else {
            sound.start(onFallback = vibrator::start) // FR-SND-5: резервный тон всегда с вибрацией
            if (decision.alarm.vibrate) vibrator.start()
            log.log(AlarmEvent.RingingStarted(decision.alarm.id, decision.degraded))
        }
        val id = decision.alarm.id
        autoStop = scope.launch {
            delay(RingingPolicy.AUTO_STOP_AFTER.toMillis()) // CPU держит сессионный WakeLock
            // Отдельная корутина: finishCurrent отменяет таймер, а запись в движок отменяться не должна.
            scope.launch { command("auto_stop") { autoStop(id) } }
        }
        publish()
    }

    /** Заканчивает текущий звонок и переходит к следующему в очереди. */
    private fun finishCurrent(reason: String) {
        val ring = current ?: return
        autoStop?.cancel()
        autoStop = null
        silence()
        log.log(AlarmEvent.RingingStopped(ring.alarm.id, reason))
        next()
    }

    private fun next() {
        current = null
        updateRearmIds()
        val decision = waiting.removeFirstOrNull()
        if (decision != null) ring(decision) else controller.publish(RingingState.Idle)
    }

    // endregion

    // region Команды

    private suspend fun dismiss(id: AlarmId) {
        if (!isCurrent(id)) return
        finishCurrent("dismiss") // звук стихает сразу, запись в БД — следом
        record("dismiss") { engine.dismiss(id, DismissReason.USER) }
    }

    private suspend fun snooze(id: AlarmId) {
        if (!isCurrent(id)) return
        // Сбой движка = отложить не удалось: звонок продолжается.
        val result = record("snooze") { engine.snooze(id) } ?: SnoozeResult.NotAllowed
        if (!isCurrent(id)) return // пока ждали движок, звонок уже закончился (автостоп, «Отключить»)
        when (result) {
            is SnoozeResult.Snoozed -> finishCurrent("snooze")

            // Отложить нельзя (лимит, флаг, отказ системы) — продолжаем звонить без кнопки.
            SnoozeResult.NotAllowed -> {
                current = current?.copy(canSnooze = false)
                publish()
            }
        }
    }

    /** FR-RING-6: никто не отреагировал — замолкаем, уведомляем о пропуске, расписание — как после «Отключить». */
    private suspend fun autoStop(id: AlarmId) {
        val ring = current?.takeIf { it.alarm.id == id } ?: return
        finishCurrent("auto_stop")
        if (!id.isTest) notifyMissed(ring) // тест запущен самим пользователем — «пропущенным» он не бывает
        record("auto_stop") { engine.dismiss(id, DismissReason.AUTO_STOP) }
    }

    @Suppress("TooGenericExceptionCaught") // одна сломанная команда не должна ронять процесс посреди звонка
    private suspend fun safely(command: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(AlarmEvent.RingingCommandFailed(command, e.javaClass.simpleName))
        }
    }

    /** Команда вне очереди срабатываний; после неё сервис останавливается, если звонить больше нечему. */
    private suspend fun command(name: String, block: suspend () -> Unit) {
        safely(name, block)
        stopIfIdle()
    }

    /** Запись в движок; ошибка логируется, звонок от неё не зависит. Сервис ждёт, пока запись не закончится. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> record(command: String, block: suspend () -> T): T? {
        unhandled++
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(AlarmEvent.RingingCommandFailed(command, e.javaClass.simpleName))
            null
        } finally {
            unhandled--
            stopIfIdle()
        }
    }

    // endregion

    // region Уведомления и состояние

    private fun silence() {
        sound.stop()
        vibrator.stop()
    }

    @Suppress("TooGenericExceptionCaught") // без уведомления звук всё равно должен идти
    private fun publish() {
        val ring = current ?: return
        controller.publish(
            RingingState.Ringing(
                alarm = ring.alarm,
                startedAt = startedAt,
                canSnooze = ring.canSnooze,
                snoozesLeft = ring.snoozesLeft,
                queued = waiting.size,
            ),
        )
        try {
            getSystemService(NotificationManager::class.java)
                .notify(AlarmNotifications.RINGING_ID, currentNotification())
        } catch (e: Exception) {
            log.log(AlarmEvent.RingingCommandFailed("notify", e.javaClass.simpleName))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun notifyMissed(ring: FireDecision.Ring) {
        try {
            getSystemService(NotificationManager::class.java).notify(
                AlarmNotifications.missedTag(ring.alarm.id),
                AlarmNotifications.MISSED_ID,
                notifications.missed(ring.alarm.time, ring.alarm.label),
            )
        } catch (e: Exception) {
            log.log(AlarmEvent.RingingCommandFailed("notify_missed", e.javaClass.simpleName))
        }
    }

    private fun currentNotification(): Notification {
        val ring = current ?: return notifications.ringing()
        return notifications.ringing(ring.alarm.time, ring.alarm.label, actionsFor(ring))
    }

    private fun actionsFor(ring: FireDecision.Ring) = RingingActions(
        dismiss = commandIntent(AlarmIntents.ACTION_DISMISS, ring.alarm.id),
        snooze = if (ring.canSnooze) commandIntent(AlarmIntents.ACTION_SNOOZE, ring.alarm.id) else null,
    )

    private fun commandIntent(action: String, id: AlarmId): PendingIntent = PendingIntent.getService(
        this,
        0,
        AlarmIntents.command(this, action, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun updateRearmIds() {
        rearmIds = buildList {
            current?.let { add(it.alarm.id) }
            waiting.forEach { add(it.alarm.id) }
            deciding?.let { add(it.alarmId) }
        }.distinct()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun cancelMissed(id: AlarmId) {
        try {
            getSystemService(NotificationManager::class.java)
                .cancel(AlarmNotifications.missedTag(id), AlarmNotifications.MISSED_ID)
        } catch (_: Exception) {
            // нечего снимать
        }
    }

    private fun isCurrent(id: AlarmId) = current?.alarm?.id == id

    private val ringing get() = current != null || earlySound

    private val hasWork get() = waiting.isNotEmpty() || unhandled > 0

    /** Единственное место остановки: только если нечего звонить, ждать и записывать. */
    private fun stopIfIdle() {
        if (destroyed || ringing || hasWork) return
        // Сначала остановка: если уже пришёл новый старт, сервис остаётся в foreground и с WakeLock.
        if (!stopSelfResult(lastStartId)) return
        controller.publish(RingingState.Idle)
        crashGuard?.uninstall()
        crashGuard = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionLock?.let { if (it.isHeld) it.release() }
    }

    // endregion

    internal companion object {
        /** FR-RING-1: звук не позже чем через 2 с после срабатывания, даже если БД ещё открывается. */
        val FIRST_SOUND_DEADLINE: Duration = Duration.ofSeconds(2)

        /** ADR-007 §7: через сколько возвращается звонок после падения процесса. */
        val CRASH_RESUME_DELAY: Duration = Duration.ofSeconds(3)

        /** Уведомление, которое звонит само (ADR-002 §6): без сервиса звук играет system_server. */
        @Suppress("TooGenericExceptionCaught") // показать fallback — последний шанс; падать здесь нельзя
        fun notifyFallback(context: Context, notifications: () -> AlarmNotifications) {
            try {
                context.getSystemService(NotificationManager::class.java)
                    .notify(AlarmNotifications.FALLBACK_ID, notifications().fallback())
            } catch (_: Exception) {
                // без POST_NOTIFICATIONS уведомление молча не показывается — сделать больше нечего
            }
        }
    }
}
