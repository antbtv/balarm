package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Вибрация звонка (ADR-008 §5): повторяющийся паттерн с `USAGE_ALARM` — работает в беззвучном режиме. */
interface AlarmVibrator {
    fun start()

    fun stop()
}

/** Сбой вибрации не должен мешать звуку: ошибки логируются и проглатываются. */
@Suppress("TooGenericExceptionCaught") // вибратор вендора может бросить что угодно — звонок важнее
@Singleton
class SystemAlarmVibrator @Inject constructor(@ApplicationContext context: Context, private val log: AlarmEventLog) :
    AlarmVibrator {

    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    override fun start() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        try {
            vibrator.cancel() // повторный start не накладывает паттерны
            vibrator.vibrate(
                VibrationEffect.createWaveform(PATTERN, REPEAT_FROM_START),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } catch (e: RuntimeException) {
            log.log(AlarmEvent.VibrationFailed(e.javaClass.simpleName))
        }
    }

    override fun stop() {
        try {
            vibrator?.cancel()
        } catch (e: RuntimeException) {
            log.log(AlarmEvent.VibrationFailed(e.javaClass.simpleName))
        }
    }

    companion object {
        /** пауза, вибрация, пауза, вибрация… (мс) */
        val PATTERN = longArrayOf(0, 800, 600, 800, 1_200)
        private const val REPEAT_FROM_START = 0
    }
}
