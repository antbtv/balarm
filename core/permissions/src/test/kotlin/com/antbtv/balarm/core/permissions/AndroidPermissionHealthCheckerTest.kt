package com.antbtv.balarm.core.permissions

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.notification.AlarmNotificationChannels
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(AndroidJUnit4::class)
class AndroidPermissionHealthCheckerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val checker = AndroidPermissionHealthChecker(context)
    private val notifications = context.getSystemService(NotificationManager::class.java).also {
        shadowOf(it).setNotificationPolicyAccessGranted(true)
    }

    @Test
    fun `notifications follow the app level switch`() {
        shadowOf(notifications).setNotificationsEnabled(true)
        assertThat(checker.snapshot().notificationsEnabled).isTrue()

        shadowOf(notifications).setNotificationsEnabled(false)
        assertThat(checker.snapshot().notificationsEnabled).isFalse()
    }

    @Test
    fun `ringing channel is created on demand with high importance`() {
        assertThat(checker.snapshot().ringingChannelEnabled).isTrue()
    }

    @Test
    fun `a disabled ringing channel is a problem even with notifications on`() {
        checker.snapshot() // создаёт каналы
        notifications.createNotificationChannel(
            NotificationChannel(AlarmNotificationChannels.RINGING, "r", NotificationManager.IMPORTANCE_NONE),
        )
        shadowOf(notifications).setNotificationsEnabled(true)

        val snapshot = checker.snapshot()

        assertThat(snapshot.notificationsEnabled).isTrue()
        assertThat(snapshot.ringingChannelEnabled).isFalse()
    }

    @Test
    fun `overlay follows the system setting`() {
        ShadowSettings.setCanDrawOverlays(false)
        assertThat(checker.snapshot().overlay).isFalse()

        ShadowSettings.setCanDrawOverlays(true)
        assertThat(checker.snapshot().overlay).isTrue()
    }

    @Test
    fun `battery optimization follows the power manager`() {
        val power = context.getSystemService(PowerManager::class.java)
        shadowOf(power).setIgnoringBatteryOptimizations(context.packageName, false)
        assertThat(checker.snapshot().ignoringBatteryOptimizations).isFalse()

        shadowOf(power).setIgnoringBatteryOptimizations(context.packageName, true)
        assertThat(checker.snapshot().ignoringBatteryOptimizations).isTrue()
    }

    @Test
    fun `alarm volume zero is reported as muted`() {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
        assertThat(checker.snapshot().alarmVolumeMuted).isFalse()

        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertThat(checker.snapshot().alarmVolumeMuted).isTrue()
    }

    @Test
    fun `total silence blocks alarms and the others let them through`() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertThat(checker.snapshot().alarmsAllowedByDnd).isFalse()

        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertThat(checker.snapshot().alarmsAllowedByDnd).isTrue()

        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertThat(checker.snapshot().alarmsAllowedByDnd).isTrue()
    }

    @Test
    fun `priority mode needs the alarms category in the policy`() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        notifications.setNotificationPolicy(NotificationManager.Policy(0, 0, 0))
        assertThat(checker.snapshot().alarmsAllowedByDnd).isFalse()

        notifications.setNotificationPolicy(
            NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0),
        )
        assertThat(checker.snapshot().alarmsAllowedByDnd).isTrue()
    }

    @Test
    fun `background restriction follows the activity manager`() {
        val am = context.getSystemService(ActivityManager::class.java)
        shadowOf(am).setBackgroundRestricted(true)
        assertThat(checker.snapshot().backgroundRestricted).isTrue()

        shadowOf(am).setBackgroundRestricted(false)
        assertThat(checker.snapshot().backgroundRestricted).isFalse()
    }
}
