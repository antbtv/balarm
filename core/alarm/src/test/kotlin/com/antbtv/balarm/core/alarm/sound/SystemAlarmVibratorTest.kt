package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SystemAlarmVibratorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val alarmVibrator = SystemAlarmVibrator(context) {}

    @Test
    fun `vibrates a repeating pattern with alarm usage`() {
        alarmVibrator.start()

        val shadow = shadowOf(vibrator)
        assertThat(shadow.isVibrating).isTrue()
        assertThat(shadow.pattern).isEqualTo(SystemAlarmVibrator.PATTERN)
        assertThat(shadow.repeat).isEqualTo(0)
        assertThat((shadow.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
            .isEqualTo(VibrationAttributes.USAGE_ALARM)
    }

    @Test
    fun `stop cancels vibration`() {
        alarmVibrator.start()

        alarmVibrator.stop()

        assertThat(shadowOf(vibrator).isCancelled).isTrue()
    }

    @Test
    fun `device without vibrator is a no-op`() {
        shadowOf(vibrator).setHasVibrator(false)

        alarmVibrator.start()

        assertThat(shadowOf(vibrator).isVibrating).isFalse()
    }

    @Test
    fun `restart keeps a single repeating vibration`() {
        alarmVibrator.start()
        alarmVibrator.start()

        assertThat(shadowOf(vibrator).isVibrating).isTrue()
        assertThat(shadowOf(vibrator).repeat).isEqualTo(0)
    }

    @Test
    fun `stop without start is harmless`() {
        alarmVibrator.stop()

        assertThat(shadowOf(vibrator).isVibrating).isFalse()
    }
}
