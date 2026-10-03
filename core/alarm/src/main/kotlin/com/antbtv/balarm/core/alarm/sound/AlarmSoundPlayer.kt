package com.antbtv.balarm.core.alarm.sound

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Звук звонка (ADR-008). Вызывается только с главного потока (`RingingService`). */
interface AlarmSoundPlayer {
    /** Начинает звонок; [onFallback] — основной звук не заиграл, играет резервный тон (FR-SND-5). */
    fun start(onFallback: () -> Unit = {})

    /** Останавливает звук и освобождает ресурсы; повторный вызов безопасен. */
    fun stop()
}

/**
 * `MediaPlayer` со встроенным звуком из APK (доступен до разблокировки). Не подготовился за
 * [PREPARE_TIMEOUT] или ошибка → `ToneGenerator` на `STREAM_ALARM`. Отказ audio focus звонок не отменяет.
 */
@Suppress("TooGenericExceptionCaught") // любая ошибка плеера → резервный тон, звонок не должен молчать
@Singleton // один звонок — один плеер: stop() из любого места глушит тот же звук
class MediaAlarmSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: AlarmEventLog,
) : AlarmSoundPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ALARM_ATTRIBUTES)
        .build()

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var onFallback: () -> Unit = {}
    private var fellBack = false
    private val prepareTimeout = Runnable { fallBack("timeout") }
    private val toneLoop = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_MS)
            handler.postDelayed(this, TONE_PERIOD_MS)
        }
    }

    override fun start(onFallback: () -> Unit) {
        stop()
        this.onFallback = onFallback
        // Результат не важен: будильник звонит и без фокуса (ADR-008 §4); потеря фокуса — M4 (FR-RING-8).
        audioManager.requestAudioFocus(focusRequest)
        try {
            // В поле сразу: если setDataSource/prepareAsync бросит, fallBack() освободит и этот плеер.
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer
            mediaPlayer.apply {
                setAudioAttributes(ALARM_ATTRIBUTES)
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                setOnPreparedListener { prepared ->
                    if (prepared !== player) return@setOnPreparedListener
                    handler.removeCallbacks(prepareTimeout)
                    prepared.start()
                    log.log(AlarmEvent.SoundStarted(SOURCE_RAW, alarmVolume()))
                }
                setOnErrorListener { failed, what, _ ->
                    if (failed === player) fallBack("error_$what")
                    true
                }
                setDataSource(context, defaultSoundUri(context))
                prepareAsync()
            }
            handler.postDelayed(prepareTimeout, PREPARE_TIMEOUT.toMillis())
        } catch (e: Exception) {
            fallBack(e.javaClass.simpleName)
        }
    }

    override fun stop() {
        handler.removeCallbacks(prepareTimeout)
        handler.removeCallbacks(toneLoop)
        releasePlayer()
        tone?.let {
            it.stopTone()
            it.release()
        }
        tone = null
        onFallback = {}
        fellBack = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun fallBack(reason: String) {
        handler.removeCallbacks(prepareTimeout)
        releasePlayer()
        if (fellBack) return
        fellBack = true
        log.log(AlarmEvent.SoundFallback(reason))
        try {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            toneLoop.run()
            log.log(AlarmEvent.SoundStarted(SOURCE_TONE, alarmVolume()))
        } catch (e: RuntimeException) {
            // Нет и тона (аудио-сервис недоступен): остаётся вибрация, которую включит onFallback.
            log.log(AlarmEvent.SoundFallback("tone_${e.javaClass.simpleName}"))
        }
        onFallback()
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        try {
            // Колбэки старого плеера отсекает player = null выше (проверка `=== player`); reset — остановка.
            current.reset()
        } catch (_: Exception) {
            // плеер уже в ошибке — release ниже всё равно освободит его
        }
        current.release()
    }

    private fun alarmVolume(): String = "${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
        "${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)}"

    companion object {
        /** FR-SND-5: не заиграл за 1 с — резерв. */
        val PREPARE_TIMEOUT: Duration = Duration.ofSeconds(1)
        private const val TONE_MS = 1_000
        private const val TONE_PERIOD_MS = 1_500L
        private const val SOURCE_RAW = "raw"
        private const val SOURCE_TONE = "tone"

        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /**
         * URI по имени ресурса, а не по id: id меняются между сборками, а URI хранится в канале
         * уведомлений (`alarm_fallback`) после обновления приложения.
         */
        fun defaultSoundUri(context: Context): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath("alarm_default")
            .build()
    }
}
