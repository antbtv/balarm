package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.ring.FakeRingVolumeStore
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.sound.SoundFileStore
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.MediaInfo
import org.robolectric.shadows.util.DataSource

@RunWith(AndroidJUnit4::class)
class MediaSoundPreviewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val events = mutableListOf<AlarmEvent>()
    private val log = AlarmEventLog { events += it }
    private val files = SoundFileStore { File(context.filesDir, "sounds/${it.value}") }
    private val ringingState = MutableStateFlow<RingingState>(RingingState.Idle)
    private val ringing = object : RingingController {
        override val state: StateFlow<RingingState> = ringingState

        override fun dismiss() = Unit

        override fun snooze() = Unit
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = FakeRingVolumeStore()
    private val volume = AlarmVolumeController(context, store, log)
    private val preview = MediaSoundPreview(context, files, volume, log, ringing, scope)
    private val bells = DataSource.toDataSource(
        context,
        MediaAlarmSoundPlayer.builtinSoundUri(context, BuiltinSound.BELLS),
    )
    private val created = mutableListOf<MediaPlayer>()

    @Before
    fun setUp() {
        shadowOf(audio).setStreamMaxVolume(MAX)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        ShadowMediaPlayer.setCreateListener { player, _ -> created += player }
        ShadowMediaPlayer.addMediaInfo(bells, MediaInfo(LONG_DURATION_MS, 0))
    }

    @After
    fun tearDown() {
        preview.stop()
        scope.cancel()
        ShadowMediaPlayer.resetStaticState()
    }

    private fun idle(duration: Duration = Duration.ZERO) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    private fun settings(percent: Int = 50) = SoundSettings(SoundRef.Builtin(BuiltinSound.BELLS), percent)

    @Test
    fun `plays on the alarm stream at the chosen volume and restores it after stop`() {
        preview.play(settings(50))
        idle()

        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.STARTED)
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(4)
        assertThat(preview.playing.value).isEqualTo(settings().sound)

        preview.stop()

        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(1)
        assertThat(preview.playing.value).isNull()
        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun `stops by itself after ten seconds`() {
        preview.play(settings())
        idle()

        idle(MediaSoundPreview.AUTO_STOP.minusMillis(1))
        assertThat(preview.playing.value).isNotNull()
        idle(Duration.ofMillis(1))

        assertThat(preview.playing.value).isNull()
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(1)
    }

    @Test
    fun `playing again restarts with the new volume and keeps the original`() {
        preview.play(settings(30))
        idle()
        preview.play(settings(100))
        idle()

        assertThat(created).hasSize(2)
        assertThat(shadowOf(created[0]).state).isEqualTo(ShadowMediaPlayer.State.END)
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(MAX)
        preview.stop()
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(1)
    }

    @Test
    fun `a starting ring silences the preview`() {
        preview.play(settings())
        idle()

        ringingState.value = RingingState.Ringing(
            alarm = Alarm(time = LocalTime.of(7, 0)),
            startedAt = Instant.EPOCH,
            canSnooze = true,
            snoozesLeft = null,
        )

        assertThat(preview.playing.value).isNull()
        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun `missing custom file reports a failure instead of playing`() {
        preview.play(SoundSettings(SoundRef.Custom(CustomSoundId(5))))

        assertThat(preview.playing.value).isNull()
        assertThat(events.filterIsInstance<AlarmEvent.PreviewFailed>()).hasSize(1)
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(1)
    }

    @Test
    fun `player error stops the preview and logs it`() {
        ShadowMediaPlayer.addException(bells, IOException("broken"))

        preview.play(settings())

        assertThat(preview.playing.value).isNull()
        assertThat(events.filterIsInstance<AlarmEvent.PreviewFailed>()).hasSize(1)
    }

    @Test
    fun `stop without play is harmless`() {
        preview.stop()
        preview.stop()

        assertThat(preview.playing.value).isNull()
    }

    private companion object {
        const val MAX = 7
        const val LONG_DURATION_MS = 120_000
    }
}
