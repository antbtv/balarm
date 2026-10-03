package com.antbtv.balarm.core.alarm.ring

/**
 * Обработчик необработанных исключений на время звонка (ADR-007 §7, NFR-5): перед гибелью процесса
 * синхронно выполняет [rearm], затем отдаёт исключение прежнему обработчику (крэш-репорт, завершение).
 * Может сработать на любом потоке — [rearm] должен читать только потокобезопасное состояние.
 */
internal class CrashGuard(private val rearm: () -> Unit) : Thread.UncaughtExceptionHandler {
    private var previous: Thread.UncaughtExceptionHandler? = null

    /** Снятый гард, оставшийся в цепочке под чужим обработчиком, только передаёт исключение дальше. */
    @Volatile private var active = false

    fun install() {
        if (active) return
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        active = true
    }

    /** Снимает себя, только если после нас никто не поставил свой обработчик. */
    fun uninstall() {
        if (!active) return
        active = false
        if (Thread.getDefaultUncaughtExceptionHandler() === this) Thread.setDefaultUncaughtExceptionHandler(previous)
    }

    @Suppress("TooGenericExceptionCaught") // re-arm — best effort; исходное исключение важнее
    override fun uncaughtException(thread: Thread, error: Throwable) {
        if (active) {
            try {
                rearm()
            } catch (rearmError: Throwable) {
                error.addSuppressed(rearmError)
            }
        }
        previous?.uncaughtException(thread, error)
    }
}
