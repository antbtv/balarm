package com.antbtv.balarm.core.alarm.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NotificationMissedAlarmNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val uiIntents = object : AlarmUiIntents {
        override fun ringingScreen() = Intent("ring").setPackage(context.packageName)

        override fun alarmList() = Intent("list").setPackage(context.packageName)
    }
    private val notifier = NotificationMissedAlarmNotifier(context, AlarmNotifications(context, uiIntents))

    @Test
    fun `posts one missed notification per alarm under the shared tag`() {
        val alarm = Alarm(id = AlarmId(7), time = LocalTime.of(6, 30), label = "Work")

        notifier.onMissed(alarm, Instant.parse("2026-09-28T03:30:00Z"))
        notifier.onMissed(alarm, Instant.parse("2026-09-29T03:30:00Z"))

        val posted = shadowOf(manager).allNotifications
        assertThat(posted).hasSize(1)
        val tag = AlarmNotifications.missedTag(alarm.id)
        assertThat(shadowOf(manager).getNotification(tag, AlarmNotifications.MISSED_ID)).isNotNull()
        assertThat(posted.single().channelId).isEqualTo(AlarmNotificationChannels.MISSED)
    }
}
