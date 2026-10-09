package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.di.ApplicationScope
import com.antbtv.balarm.core.domain.sound.SoundFileStore
import com.antbtv.balarm.core.domain.sound.SoundPreview
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Превью мелодии (ADR-017 §6): отдельный `MediaPlayer` с атрибутами будильника, громкость потока —
 * через [AlarmVolumeController] (владелец [VolumeOwner.PREVIEW]), автостоп через [AUTO_STOP]. Начался звонок —
 * превью замолкает само. Вызывается с главного потока видимого экрана.
 */
@Suppress("TooGenericExceptionCaught") // сбой превью не должен ронять экран: тишина и запись в лог
@Singleton
class MediaSoundPreview @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: SoundFileStore,
    private val volume: AlarmVolumeController,
    private val log: AlarmEventLog,
    ringing: RingingController,
    @ApplicationScope scope: CoroutineScope,
) : SoundPreview {

    private val handler = Handler(Looper.getMainLooper())
    private val _playing = MutableStateFlow<SoundRef?>(null)
    override val playing: StateFlow<SoundRef?> = _playing

    private var player: MediaPlayer? = null
    private val autoStop = Runnable { stop() }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            ringing.state.collect { if (it is RingingState.Ringing) stop() }
        }
    }

    override fun play(settings: SoundSettings) {
        stop()
        try {
            volume.acquire(VolumeOwner.PREVIEW, settings.volumePercent)
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer
            mediaPlayer.apply {
                setAudioAttributes(MediaAlarmSoundPlayer.ALARM_ATTRIBUTES)
                isLooping = true
                setOnPreparedListener { prepared ->
                    if (prepared !== player) return@setOnPreparedListener
                    prepared.start()
                    handler.postDelayed(autoStop, AUTO_STOP.toMillis())
                }
                setOnErrorListener { failed, what, _ ->
                    if (failed === player) {
                        log.log(AlarmEvent.PreviewFailed("error_$what"))
                        stop()
                    }
                    true
                }
                open(settings.sound)
                prepareAsync()
            }
            _playing.value = settings.sound
        } catch (e: Exception) {
            log.log(AlarmEvent.PreviewFailed(e.javaClass.simpleName))
            stop()
        }
    }

    override fun stop() {
        handler.removeCallbacks(autoStop)
        val current = player
        player = null
        if (current != null) {
            try {
                current.reset()
            } catch (_: Exception) {
                // уже в ошибке — release ниже освободит
            }
            current.release()
        }
        _playing.value = null
        volume.release(VolumeOwner.PREVIEW)
    }

    private fun MediaPlayer.open(ref: SoundRef) {
        when (ref) {
            is SoundRef.Builtin -> setDataSource(context, MediaAlarmSoundPlayer.builtinSoundUri(context, ref.sound))

            is SoundRef.Custom -> {
                val file = files.fileOf(ref.id)
                check(file.exists()) { "missing" }
                FileInputStream(file).use { setDataSource(it.fd) }
            }
        }
    }

    companion object {
        val AUTO_STOP: Duration = Duration.ofSeconds(10)
    }
}
