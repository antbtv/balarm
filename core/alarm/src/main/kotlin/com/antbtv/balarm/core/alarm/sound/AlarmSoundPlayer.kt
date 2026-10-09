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
import android.os.SystemClock
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.sound.SoundFileStore
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/** Звук звонка (ADR-008, ADR-017). Вызывается только с главного потока (`RingingService`). */
interface AlarmSoundPlayer {
    /**
     * Начинает звонок мелодией из [settings]. [fadeIn] `false` — без нарастания (RESUME, CATCH_UP, ранний звук).
     * [onFallback] — обе мелодии не заиграли, играет резервный тон (FR-SND-5).
     */
    fun start(settings: SoundSettings, fadeIn: Boolean = true, onFallback: () -> Unit = {})

    /** FR-RING-8: `true` — заглушить звук (вибрацию включает сервис); `false` — продолжить с нарастания. */
    fun setMuted(muted: Boolean)

    /** Останавливает звук и освобождает ресурсы; повторный вызов безопасен. */
    fun stop()
}

/**
 * `MediaPlayer` с цепочкой резервов (FR-SND-5): выбранная мелодия → встроенная по умолчанию → `ToneGenerator`;
 * у каждой ступени подготовки [PREPARE_TIMEOUT]. Встроенные мелодии лежат в APK, свои — в device-protected
 * хранилище и открываются через `FileDescriptor` (`mediaserver` не читает приватный каталог).
 * Нарастание — усилением плеера поверх индекса потока (ADR-017 §3). Отказ audio focus звонок не отменяет.
 */
@Suppress("TooGenericExceptionCaught", "TooManyFunctions") // ошибка плеера → резерв; звонок не должен молчать
@Singleton // один звонок — один плеер: stop() из любого места глушит тот же звук
class MediaAlarmSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: AlarmEventLog,
    private val files: SoundFileStore,
) : AlarmSoundPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ALARM_ATTRIBUTES)
        .build()

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var onFallback: () -> Unit = {}
    private var chain: List<SoundRef?> = emptyList() // null — резервный тон
    private var stage = 0
    private var muted = false
    private var fadeEnabled = false
    private var fadeTotalMs = 0L
    private var fadeStartedAt = -1L
    private val prepareTimeout = Runnable { fallBack("timeout") }
    private val toneLoop = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_MS)
            handler.postDelayed(this, TONE_PERIOD_MS)
        }
    }
    private val fadeTick = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.elapsedRealtime() - fadeStartedAt
            player?.takeIf { !muted }?.setVolume(fadeGain(elapsed, fadeTotalMs), fadeGain(elapsed, fadeTotalMs))
            if (elapsed < fadeTotalMs) handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    override fun start(settings: SoundSettings, fadeIn: Boolean, onFallback: () -> Unit) {
        stop()
        this.onFallback = onFallback
        chain = listOfNotNull(settings.sound, SoundRef.DEFAULT.takeIf { settings.sound != it }) + null
        fadeEnabled = fadeIn && !settings.fadeIn.isZero
        fadeTotalMs = settings.fadeIn.toMillis()
        // Результат не важен: будильник звонит и без фокуса (ADR-008 §4).
        audioManager.requestAudioFocus(focusRequest)
        runStage(0)
    }

    override fun setMuted(muted: Boolean) {
        if (this.muted == muted) return
        this.muted = muted
        handler.removeCallbacks(fadeTick)
        if (muted) {
            player?.setVolume(0f, 0f)
            handler.removeCallbacks(toneLoop)
            tone?.stopTone()
        } else if (tone != null) {
            toneLoop.run()
        } else if (player?.isPlaying == true) {
            fadeEnabled = true
            beginFade(RESUME_FADE.toMillis())
        }
    }

    override fun stop() {
        handler.removeCallbacks(prepareTimeout)
        handler.removeCallbacks(toneLoop)
        handler.removeCallbacks(fadeTick)
        releasePlayer()
        tone?.let {
            it.stopTone()
            it.release()
        }
        tone = null
        onFallback = {}
        chain = emptyList()
        stage = 0
        muted = false
        fadeEnabled = false
        fadeStartedAt = -1
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun runStage(index: Int) {
        stage = index
        val ref = chain.getOrNull(index)
        if (ref == null) {
            playTone()
            return
        }
        try {
            // В поле сразу: если setDataSource/prepareAsync бросит, fallBack() освободит и этот плеер.
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer
            mediaPlayer.apply {
                setAudioAttributes(ALARM_ATTRIBUTES)
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                setOnPreparedListener { prepared -> onPrepared(prepared, ref, index) }
                setOnErrorListener { failed, what, _ ->
                    if (failed === player) fallBack("error_$what")
                    true
                }
                if (!setSource(ref)) return
                prepareAsync()
            }
            handler.postDelayed(prepareTimeout, PREPARE_TIMEOUT.toMillis())
        } catch (e: Exception) {
            fallBack(e.javaClass.simpleName)
        }
    }

    /** `false` — источник недоступен, уже выполнен переход на следующую ступень. */
    private fun MediaPlayer.setSource(ref: SoundRef): Boolean {
        when (ref) {
            is SoundRef.Builtin -> setDataSource(context, builtinSoundUri(context, ref.sound))

            is SoundRef.Custom -> {
                val file = files.fileOf(ref.id)
                if (!file.exists()) {
                    fallBack("missing")
                    return false
                }
                FileInputStream(file).use { setDataSource(it.fd) }
            }
        }
        return true
    }

    private fun onPrepared(prepared: MediaPlayer, ref: SoundRef, index: Int) {
        if (prepared !== player) return
        handler.removeCallbacks(prepareTimeout)
        prepared.start()
        when {
            muted -> prepared.setVolume(0f, 0f)

            index == 0 && fadeEnabled -> {
                prepared.setVolume(fadeGain(0, fadeTotalMs), fadeGain(0, fadeTotalMs))
                beginFade(fadeTotalMs)
            }

            else -> prepared.setVolume(1f, 1f)
        }
        log.log(AlarmEvent.SoundStarted(sourceLabel(ref, index), alarmVolume()))
    }

    private fun beginFade(totalMs: Long) {
        fadeTotalMs = totalMs
        fadeStartedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(fadeTick)
        fadeTick.run()
    }

    private fun sourceLabel(ref: SoundRef, index: Int): String = when {
        ref is SoundRef.Custom -> SOURCE_CUSTOM
        ref == SoundRef.DEFAULT || index > 0 -> SOURCE_DEFAULT
        else -> SOURCE_BUILTIN
    }

    private fun fallBack(reason: String) {
        handler.removeCallbacks(prepareTimeout)
        releasePlayer()
        if (chain.isEmpty()) return
        log.log(AlarmEvent.SoundFallback(reason))
        runStage(stage + 1)
    }

    private fun playTone() {
        try {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            if (!muted) toneLoop.run()
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
        /** FR-SND-5: не заиграл за 1 с — следующая ступень. */
        val PREPARE_TIMEOUT: Duration = Duration.ofSeconds(1)

        /** После телефонного звонка мелодия возвращается с нарастания (ADR-017 §8). */
        val RESUME_FADE: Duration = Duration.ofSeconds(15)
        private const val FADE_STEP_MS = 200L
        private const val FADE_FROM_DB = -20.0
        private const val DB_PER_DECADE = 20.0
        private const val TONE_MS = 1_000
        private const val TONE_PERIOD_MS = 1_500L
        private const val SOURCE_BUILTIN = "builtin"
        private const val SOURCE_CUSTOM = "custom"
        private const val SOURCE_DEFAULT = "default"
        private const val SOURCE_TONE = "tone"

        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Усиление плеера через [elapsedMs] нарастания длиной [totalMs]: от −20 дБ (10 % амплитуды) до 0 дБ. */
        fun fadeGain(elapsedMs: Long, totalMs: Long): Float {
            if (totalMs <= 0 || elapsedMs >= totalMs) return 1f
            val progress = elapsedMs.coerceAtLeast(0).toDouble() / totalMs
            return 10.0.pow(FADE_FROM_DB * (1 - progress) / DB_PER_DECADE).toFloat()
        }

        /**
         * URI по имени ресурса, а не по id: id меняются между сборками, а URI `alarm_default` хранится
         * в канале уведомлений (`alarm_fallback`) после обновления приложения.
         */
        fun builtinSoundUri(context: Context, sound: BuiltinSound): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath(sound.key)
            .build()

        fun defaultSoundUri(context: Context): Uri = builtinSoundUri(context, BuiltinSound.DEFAULT)
    }
}
