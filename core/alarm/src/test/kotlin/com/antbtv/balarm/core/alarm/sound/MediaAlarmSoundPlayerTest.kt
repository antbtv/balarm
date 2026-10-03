package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.MediaInfo
import org.robolectric.shadows.ShadowToneGenerator
import org.robolectric.shadows.util.DataSource

@RunWith(AndroidJUnit4::class)
class MediaAlarmSoundPlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val events = mutableListOf<AlarmEvent>()
    private val player = MediaAlarmSoundPlayer(context, AlarmEventLog { events += it })
    private val source = DataSource.toDataSource(context, MediaAlarmSoundPlayer.defaultSoundUri(context))
    private val created = mutableListOf<MediaPlayer>()
    private var fallbacks = 0

    @Before
    fun setUp() {
        ShadowMediaPlayer.setCreateListener { mediaPlayer, _ -> created += mediaPlayer }
    }

    @After
    fun tearDown() {
        player.stop()
        ShadowMediaPlayer.resetStaticState()
        ShadowToneGenerator.reset()
    }

    @Test
    fun `plays the built-in sound in a loop on the alarm stream`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, 0))

        start()
        idle()

        val mediaPlayer = created.single()
        assertThat(shadowOf(mediaPlayer).state).isEqualTo(ShadowMediaPlayer.State.STARTED)
        assertThat(mediaPlayer.isLooping).isTrue()
        assertThat(shadowOf(mediaPlayer).audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(shadowOf(mediaPlayer).audioAttributes.contentType)
            .isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        assertThat(events).containsExactly(AlarmEvent.SoundStarted("raw", alarmVolume()))
        assertThat(fallbacks).isEqualTo(0)
    }

    @Test
    fun `built-in sound resource exists in the apk`() {
        val id = context.resources.getIdentifier("alarm_default", "raw", context.packageName)

        assertThat(id).isNotEqualTo(0)
        assertThat(MediaAlarmSoundPlayer.defaultSoundUri(context).toString())
            .isEqualTo("android.resource://${context.packageName}/raw/alarm_default")
    }

    @Test
    fun `requests transient audio focus for alarm usage`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, 0))

        start()

        val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertThat(request.focusGain).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        assertThat(request.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
    }

    @Test
    fun `denied audio focus does not silence the alarm`() {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, 0))

        start()
        idle()

        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.STARTED)
    }

    @Test
    fun `unreadable sound falls back to the tone generator`() {
        ShadowMediaPlayer.addException(source, IOException("broken"))

        start()

        assertThat(ShadowToneGenerator.getPlayedTones().map { it.type() })
            .contains(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
        assertThat(events).containsExactly(
            AlarmEvent.SoundFallback("IOException"),
            AlarmEvent.SoundStarted("tone", alarmVolume()),
        )
        assertThat(fallbacks).isEqualTo(1)
        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.END) // не утёк
    }

    @Test
    fun `sound not prepared within one second falls back`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, PREPARE_NEVER_MS))

        start()
        idle(MediaAlarmSoundPlayer.PREPARE_TIMEOUT.minusMillis(1))
        assertThat(fallbacks).isEqualTo(0)

        idle(Duration.ofMillis(1))
        assertThat(fallbacks).isEqualTo(1)
        assertThat(events.first()).isEqualTo(AlarmEvent.SoundFallback("timeout"))
        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun `playback error falls back once`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, PREPARE_NEVER_MS))
        start()

        shadowOf(created.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        idle(MediaAlarmSoundPlayer.PREPARE_TIMEOUT) // таймаут после ошибки не даёт второго фолбэка

        assertThat(fallbacks).isEqualTo(1)
        assertThat(events.filterIsInstance<AlarmEvent.SoundFallback>()).containsExactly(
            AlarmEvent.SoundFallback("error_${MediaPlayer.MEDIA_ERROR_UNKNOWN}"),
        )
    }

    @Test
    fun `fallback tone repeats until stopped`() {
        ShadowMediaPlayer.addException(source, IOException("broken"))
        start()

        val first = ShadowToneGenerator.getPlayedTones().size
        idle(Duration.ofSeconds(5))
        val playedWhileRinging = ShadowToneGenerator.getPlayedTones().size
        player.stop()
        idle(Duration.ofSeconds(5))

        assertThat(playedWhileRinging).isGreaterThan(first)
        assertThat(ShadowToneGenerator.getPlayedTones()).hasSize(playedWhileRinging)
    }

    @Test
    fun `stop releases the player and abandons audio focus`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, 0))
        start()
        idle()

        player.stop()
        idle(Duration.ofSeconds(2))

        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.END)
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()
        assertThat(fallbacks).isEqualTo(0)
    }

    @Test
    fun `restart replaces the previous player`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, 0))

        start()
        idle()
        start()
        idle()

        assertThat(shadowOf(created[0]).state).isEqualTo(ShadowMediaPlayer.State.END)
        assertThat(shadowOf(created[1]).state).isEqualTo(ShadowMediaPlayer.State.STARTED)
    }

    @Test
    fun `callbacks of a replaced player are ignored`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, PREPARE_NEVER_MS))
        start()
        val stale = created.single()
        start()

        shadowOf(stale).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        shadowOf(stale).invokePreparedListener()

        assertThat(fallbacks).isEqualTo(0)
        assertThat(events).isEmpty()
    }

    @Test
    fun `error during playback switches to the tone`() {
        ShadowMediaPlayer.addMediaInfo(source, MediaInfo(DURATION_MS, 0))
        start()
        idle()

        shadowOf(created.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_SERVER_DIED, 0)

        assertThat(fallbacks).isEqualTo(1)
        assertThat(ShadowToneGenerator.getPlayedTones()).isNotEmpty()
        assertThat(shadowOf(created.single()).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun `a new start after fallback can fall back again`() {
        ShadowMediaPlayer.addException(source, IOException("broken"))
        start()
        player.stop()

        start()

        assertThat(fallbacks).isEqualTo(2)
    }

    private fun alarmVolume() = "${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
        "${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)}"

    private fun start() = player.start { fallbacks++ }

    private fun idle(duration: Duration = Duration.ZERO) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    private companion object {
        const val DURATION_MS = 1_550
        const val PREPARE_NEVER_MS = -1
    }
}
