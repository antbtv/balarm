package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.ring.FakeRingVolumeStore
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.sound.SavedVolume
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AlarmVolumeControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val store = FakeRingVolumeStore()
    private val events = mutableListOf<AlarmEvent>()
    private val controller = AlarmVolumeController(context, store, AlarmEventLog { events += it })

    init {
        shadowOf(audio).setStreamMaxVolume(MAX)
        shadowOf(audio).setStreamMinVolume(1)
    }

    private var volume: Int
        get() = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        set(value) = audio.setStreamVolume(AudioManager.STREAM_ALARM, value, 0)

    @Test
    fun `target index rounds up and respects the device minimum`() {
        assertThat(AlarmVolumeController.targetIndex(80, 1, 7)).isEqualTo(6)
        assertThat(AlarmVolumeController.targetIndex(100, 1, 7)).isEqualTo(7)
        assertThat(AlarmVolumeController.targetIndex(10, 1, 7)).isEqualTo(1)
        assertThat(AlarmVolumeController.targetIndex(10, 3, 7)).isEqualTo(3)
        assertThat(AlarmVolumeController.targetIndex(50, 0, 15)).isEqualTo(8)
    }

    @Test
    fun `ringing raises a quiet alarm stream and restores it afterwards`() {
        volume = 1

        controller.acquire(VolumeOwner.RINGING, 80)
        assertThat(volume).isEqualTo(6)
        assertThat(store.value).isEqualTo(SavedVolume(original = 1, applied = 6))
        assertThat(events).contains(AlarmEvent.VolumeApplied(original = 1, applied = 6, max = MAX))

        controller.release(VolumeOwner.RINGING)
        assertThat(volume).isEqualTo(1)
        assertThat(store.value).isNull()
        assertThat(events).contains(AlarmEvent.VolumeRestored(1))
    }

    @Test
    fun `volume the user changed during the ring is left alone`() {
        volume = 1
        controller.acquire(VolumeOwner.RINGING, 80)
        volume = 3

        controller.release(VolumeOwner.RINGING)

        assertThat(volume).isEqualTo(3)
        assertThat(store.value).isNull()
    }

    @Test
    fun `second alarm of the queue keeps the very first original volume`() {
        volume = 2
        controller.acquire(VolumeOwner.RINGING, 50)
        controller.acquire(VolumeOwner.RINGING, 100)

        assertThat(volume).isEqualTo(7)
        assertThat(store.value).isEqualTo(SavedVolume(original = 2, applied = 7))
        controller.release(VolumeOwner.RINGING)
        assertThat(volume).isEqualTo(2)
    }

    @Test
    fun `volume returns only after the last owner lets go`() {
        volume = 1
        controller.acquire(VolumeOwner.PREVIEW, 30)
        controller.acquire(VolumeOwner.RINGING, 80)

        controller.release(VolumeOwner.PREVIEW)
        assertThat(volume).isEqualTo(6)

        controller.release(VolumeOwner.RINGING)
        assertThat(volume).isEqualTo(1)
    }

    @Test
    fun `snapshot left by a crashed process is not overwritten by the resumed ring`() {
        volume = 6 // громкость, выставленная упавшим процессом
        store.value = SavedVolume(original = 1, applied = 6)

        controller.acquire(VolumeOwner.RINGING, 80)
        controller.release(VolumeOwner.RINGING)

        assertThat(volume).isEqualTo(1)
    }

    @Test
    fun `original is persisted before the stream is touched`() {
        volume = 1
        val order = mutableListOf<String>()
        val recording = object : com.antbtv.balarm.core.domain.sound.RingVolumeStore by store {
            override suspend fun save(volume: SavedVolume) {
                order += "save original=${volume.original} stream=${this@AlarmVolumeControllerTest.volume}"
                store.save(volume)
            }
        }
        AlarmVolumeController(context, recording, AlarmEventLog { events += it }).acquire(VolumeOwner.RINGING, 80)

        assertThat(order.first()).isEqualTo("save original=1 stream=1")
    }

    @Test
    fun `pending volume is restored when the ui starts and nothing rings`() {
        volume = 6
        store.value = SavedVolume(original = 1, applied = 6)

        controller.restorePendingIfIdle()

        assertThat(volume).isEqualTo(1)
        assertThat(store.value).isNull()
    }

    @Test
    fun `pending volume is kept while a ring is active`() {
        volume = 1
        controller.acquire(VolumeOwner.RINGING, 80)

        controller.restorePendingIfIdle()

        assertThat(volume).isEqualTo(6)
        assertThat(store.value).isNotNull()
    }

    @Test
    fun `broken store does not stop the volume from being set`() {
        volume = 1
        val failing = object : com.antbtv.balarm.core.domain.sound.RingVolumeStore {
            override suspend fun load(): SavedVolume? = error("disk")

            override suspend fun save(volume: SavedVolume) = error("disk")

            override suspend fun clear() = error("disk")
        }
        val broken = AlarmVolumeController(context, failing, AlarmEventLog { events += it })

        broken.acquire(VolumeOwner.RINGING, 80)
        broken.release(VolumeOwner.RINGING)

        assertThat(events.filterIsInstance<AlarmEvent.VolumeApplied>()).hasSize(1)
        assertThat(volume).isEqualTo(1)
    }

    private companion object {
        const val MAX = 7
    }
}
