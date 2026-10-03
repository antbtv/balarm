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
import com.antbtv.balarm.core.domain.alarm.DismissReason
import com.antbtv.balarm.core.domain.alarm.FireDecision
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.domain.alarm.SnoozeResult
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Сессия звонка (ADR-007 §2–4): foreground service `systemExempted`, звук и вибрация, WakeLock на весь звонок.
 *
 * * Все переходы — в одной корутине по очереди команд: срабатывание, «Отключить», «Отложить» не гоняются
 *   друг с другом; сервис не останавливается, пока есть необработанные команды или звонки в очереди.
 * * Ошибка любой команды логируется и не роняет процесс: звонок важнее консистентности.
 * * Срабатывание сразу отдаётся движку (он фиксирует его и планирует следующее), даже если сейчас звонит
 *   другой будильник — в очередь (FR-RING-7) встаёт уже готовое решение.
 * * Если решение не готово за [FIRST_SOUND_DEADLINE] (холодная БД после загрузки), звук начинается раньше.
 */
@AndroidEntryPoint
@Suppress("TooManyFunctions")
class RingingService : Service() {

    @Inject lateinit var engine: AlarmEngine

    @Inject lateinit var sound: AlarmSoundPlayer

    @Inject lateinit var vibrator: AlarmVibrator

    @Inject lateinit var notifications: AlarmNotifications

    @Inject lateinit var controller: RingingControllerImpl

    @Inject lateinit var log: AlarmEventLog

    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val waiting = ArrayDeque<FireDecision.Ring>()
    private var current: FireDecision.Ring? = null
    private var startedAt: Instant = Instant.EPOCH

    /** Звук пущен до решения движка (watchdog); решение его подхватывает или гасит. */
    private var earlySound = false
    private var sessionLock: PowerManager.WakeLock? = null
    private var lastStartId = 0

    /** Принятые, но ещё не обработанные команды: пока они есть, сервис не останавливается. */
    private var unhandled = 0
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (command in commands) {
                handleSafely(command)
                unhandled--
                stopIfIdle()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Любой ACTION_RING пришёл через startForegroundService — startForeground обязателен, даже для битого.
        if (intent?.action == AlarmIntents.ACTION_RING && !enterForeground(AlarmIntents.alarmId(intent))) {
            return START_NOT_STICKY
        }
        val command = intent?.let(::commandOf)
        if (command != null) {
            unhandled++
            commands.trySend(command)
        }
        stopIfIdle()
        return START_NOT_STICKY // после гибели процесса звонок возвращает crash re-arm (M1-T12), а не рестарт
    }

    override fun onDestroy() {
        destroyed = true
        scope.cancel()
        current?.let { log.log(AlarmEvent.RingingStopped(it.alarm.id, "destroyed")) }
        waiting.forEach { log.log(AlarmEvent.RingingStopped(it.alarm.id, "destroyed")) }
        current = null
        waiting.clear()
        silence()
        controller.publish(RingingState.Idle)
        sessionLock?.let { if (it.isHeld) it.release() }
        RingingWakeLocks.releaseDelivery()
        super.onDestroy()
    }

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
        return true
    }

    @Suppress("TooGenericExceptionCaught") // одна сломанная команда не должна ронять процесс посреди звонка
    private suspend fun handleSafely(command: Command) {
        try {
            when (command) {
                is Command.Ring -> fire(command.request)
                is Command.Dismiss -> if (isCurrent(command.id)) dismiss(command.id)
                is Command.Snooze -> if (isCurrent(command.id)) snooze(command.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(AlarmEvent.RingingCommandFailed(command.name, e.javaClass.simpleName))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fire(request: ScheduleRequest) {
        val idle = current == null
        val watchdog = if (idle) scope.launch { startEarly(request.alarmId) } else null
        val decision = try {
            engine.onFired(request.alarmId, request.triggerAt, request.kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Движок сам деградирует при сбоях; это — последняя страховка «в сомнении — звони».
            log.log(AlarmEvent.RingingCommandFailed("ring", e.javaClass.simpleName))
            degraded(request.alarmId)
        } finally {
            watchdog?.cancel()
        }
        when (decision) {
            is FireDecision.Skip -> if (earlySound) {
                earlySound = false
                silence()
                next()
            }

            is FireDecision.Ring -> if (current == null) ring(decision) else queue(decision)
        }
    }

    private fun degraded(id: AlarmId) = FireDecision.Ring(
        alarm = Alarm(id = id, time = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES)),
        canSnooze = false,
        snoozesLeft = 0,
        degraded = true,
    )

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
        log.log(AlarmEvent.RingingQueued(decision.alarm.id))
        publish()
    }

    private fun ring(decision: FireDecision.Ring) {
        current = decision
        startedAt = clock.instant()
        if (earlySound) {
            earlySound = false // звук уже идёт; вибрация — по настройке будильника
            if (!decision.alarm.vibrate) vibrator.stop()
        } else {
            sound.start(onFallback = vibrator::start) // FR-SND-5: резервный тон всегда с вибрацией
            if (decision.alarm.vibrate) vibrator.start()
            log.log(AlarmEvent.RingingStarted(decision.alarm.id, decision.degraded))
        }
        publish()
    }

    private fun dismiss(id: AlarmId) {
        silence() // пользователь нажал — звук стихает сразу, запись в БД — следом
        log.log(AlarmEvent.RingingStopped(id, "dismiss"))
        scope.launch { record("dismiss") { engine.dismiss(id, DismissReason.USER) } }
        next()
    }

    private suspend fun snooze(id: AlarmId) {
        // Сбой движка = отложить не удалось: звонок продолжается.
        val result = record("snooze") { engine.snooze(id) } ?: SnoozeResult.NotAllowed
        when (result) {
            is SnoozeResult.Snoozed -> {
                silence()
                log.log(AlarmEvent.RingingStopped(id, "snooze"))
                next()
            }

            // Отложить нельзя (лимит, флаг, отказ системы) — продолжаем звонить без кнопки.
            SnoozeResult.NotAllowed -> {
                current = current?.copy(canSnooze = false)
                publish()
            }
        }
    }

    /** Запись в движок; ошибка логируется, звонок от неё не зависит. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> record(command: String, block: suspend () -> T): T? = try {
        unhandled++ // сервис ждёт, пока результат не записан
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

    private fun next() {
        current = null
        val decision = waiting.removeFirstOrNull()
        if (decision != null) ring(decision) else controller.publish(RingingState.Idle)
    }

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

    private fun isCurrent(id: AlarmId) = current?.alarm?.id == id

    /** Единственное место остановки: только если нечего звонить, ждать и записывать. */
    private val ringing get() = current != null || earlySound

    private val hasWork get() = waiting.isNotEmpty() || unhandled > 0

    private fun stopIfIdle() {
        if (destroyed || ringing || hasWork) return
        controller.publish(RingingState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionLock?.let { if (it.isHeld) it.release() }
        stopSelfResult(lastStartId) // новый старт после этого вызова сервис не остановит
    }

    private fun commandOf(intent: Intent): Command? = when (intent.action) {
        AlarmIntents.ACTION_RING -> AlarmIntents.parse(intent)?.let(Command::Ring)
        AlarmIntents.ACTION_DISMISS -> AlarmIntents.alarmId(intent)?.let(Command::Dismiss)
        AlarmIntents.ACTION_SNOOZE -> AlarmIntents.alarmId(intent)?.let(Command::Snooze)
        else -> null
    }

    private sealed interface Command {
        val name: String

        data class Ring(val request: ScheduleRequest) : Command {
            override val name = "ring"
        }

        data class Dismiss(val id: AlarmId) : Command {
            override val name = "dismiss"
        }

        data class Snooze(val id: AlarmId) : Command {
            override val name = "snooze"
        }
    }

    internal companion object {
        /** FR-RING-1: звук не позже чем через 2 с после срабатывания, даже если БД ещё открывается. */
        val FIRST_SOUND_DEADLINE: Duration = Duration.ofSeconds(2)

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
