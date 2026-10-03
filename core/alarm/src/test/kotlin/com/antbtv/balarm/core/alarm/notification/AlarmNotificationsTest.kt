package com.antbtv.balarm.core.alarm.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.domain.alarm.RingingPolicy
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
class AlarmNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val uiIntents = object : AlarmUiIntents {
        override fun ringingScreen() = Intent(RINGING_SCREEN).setPackage(context.packageName)

        override fun alarmList() = Intent(ALARM_LIST).setPackage(context.packageName)
    }
    private val notifications = AlarmNotifications(context, uiIntents)

    @Test
    fun `ringing channel is high importance and silent - the service plays the sound`() {
        AlarmNotificationChannels.ensureCreated(context)

        val channel = manager.getNotificationChannel(AlarmNotificationChannels.RINGING)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.sound).isNull()
        assertThat(channel.shouldVibrate()).isFalse()
        assertThat(channel.lockscreenVisibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
    }

    @Test
    fun `fallback channel rings through the alarm stream`() {
        AlarmNotificationChannels.ensureCreated(context)

        val channel = manager.getNotificationChannel(AlarmNotificationChannels.FALLBACK)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.sound.toString()).isEqualTo("android.resource://${context.packageName}/raw/alarm_default")
        assertThat(channel.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(channel.shouldVibrate()).isTrue()
    }

    @Test
    fun `missed channel exists and is not intrusive`() {
        AlarmNotificationChannels.ensureCreated(context)

        val channel = manager.getNotificationChannel(AlarmNotificationChannels.MISSED)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
    }

    @Test
    fun `channel creation is idempotent`() {
        AlarmNotificationChannels.ensureCreated(context)
        AlarmNotificationChannels.ensureCreated(context)

        assertThat(manager.notificationChannels.map { it.id }).containsExactly(
            AlarmNotificationChannels.RINGING,
            AlarmNotificationChannels.FALLBACK,
            AlarmNotificationChannels.MISSED,
        )
    }

    @Test
    fun `ringing notification opens the ringing screen full screen`() {
        val notification = notifications.ringing()

        assertThat(notification.channelId).isEqualTo(AlarmNotificationChannels.RINGING)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        assertThat(notification.fullScreenIntent.action()).isEqualTo(RINGING_SCREEN)
        assertThat(notification.contentIntent.action()).isEqualTo(RINGING_SCREEN)
        assertThat(notification.fullScreenIntent.isImmutable).isTrue()
        // Без IMMEDIATE Android 12+ может отложить показ уведомления FGS до 10 с (getter скрыт).
        assertWithMessage("hidden Notification.mFgsDeferBehavior — сверить с текущим SDK, если поле переименовано")
            .that(ReflectionHelpers.getField<Int>(notification, "mFgsDeferBehavior"))
            .isEqualTo(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        assertThat(notification.flags and Notification.FLAG_INSISTENT).isEqualTo(0)
        // Найдено на эмуляторе (M1-T15): «тихое» уведомление не запускает full-screen intent.
        val silentFlag = ReflectionHelpers.getStaticField<Int>(Notification::class.java, "FLAG_SILENT")
        assertWithMessage("ringing notification must not be silent")
            .that(notification.flags and silentFlag).isEqualTo(0)
    }

    @Test
    fun `base ringing notification needs no alarm data`() {
        val notification = notifications.ringing()

        assertThat(notification.title()).isEqualTo("Alarm")
        assertThat(notification.text()).isEqualTo("Ringing")
        assertThat(notification.actions).isNull()
    }

    @Test
    fun `ringing notification shows label, time and both actions`() {
        val notification = notifications.ringing(
            time = LocalTime.of(7, 30),
            label = "Work",
            actions = RingingActions(dismiss = pending("dismiss"), snooze = pending("snooze")),
        )

        assertThat(notification.title()).isEqualTo("Work")
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Snooze", "Dismiss").inOrder()
    }

    @Test
    fun `time follows the 24-hour setting`() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertThat(notifications.ringing(time = LocalTime.of(7, 30)).text()).isEqualTo("07:30")

        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertThat(notifications.ringing(time = LocalTime.of(19, 5)).text()).matches("7:05\\sPM")
    }

    @Test
    fun `channels can be created before unlock`() {
        AlarmNotificationChannels.ensureCreated(context.createDeviceProtectedStorageContext())

        assertThat(manager.getNotificationChannel(AlarmNotificationChannels.RINGING)).isNotNull()
    }

    @Test
    fun `missing snooze or dismiss means no button`() {
        val notification = notifications.ringing(actions = RingingActions(dismiss = null, snooze = pending("snooze")))

        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Snooze")
    }

    @Test
    @Config(qualifiers = "ru")
    fun `strings are localized to russian`() {
        val notification = notifications.ringing(
            actions = RingingActions(dismiss = pending("dismiss"), snooze = pending("snooze")),
        )
        AlarmNotificationChannels.ensureCreated(context)

        assertThat(notification.title()).isEqualTo("Будильник")
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Отложить", "Отключить").inOrder()
        assertThat(manager.getNotificationChannel(AlarmNotificationChannels.RINGING).name)
            .isEqualTo("Звонок будильника")
    }

    @Test
    fun `fallback notification is insistent alarm with full screen intent`() {
        val notification = notifications.fallback()

        assertThat(notification.channelId).isEqualTo(AlarmNotificationChannels.FALLBACK)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(notification.flags and Notification.FLAG_INSISTENT).isNotEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
        assertThat(notification.timeoutAfter).isEqualTo(RingingPolicy.AUTO_STOP_AFTER.toMillis())
        assertThat(notification.fullScreenIntent).isNotNull()
        assertThat(notification.contentIntent.action()).isEqualTo(ALARM_LIST)
    }

    @Test
    fun `missed notification is a quiet reminder that opens the alarm list`() {
        val notification = notifications.missed(LocalTime.of(7, 30), "Work")

        assertThat(notification.channelId).isEqualTo(AlarmNotificationChannels.MISSED)
        assertThat(notification.title()).isEqualTo("Missed alarm")
        assertThat(notification.text()).contains("Work")
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(notification.contentIntent.action()).isEqualTo(ALARM_LIST)
    }

    private fun pending(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun PendingIntent.action(): String? = shadowOf(this).savedIntent.action

    private fun Notification.title() = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

    private companion object {
        const val RINGING_SCREEN = "test.RINGING_SCREEN"
        const val ALARM_LIST = "test.ALARM_LIST"
    }
}
