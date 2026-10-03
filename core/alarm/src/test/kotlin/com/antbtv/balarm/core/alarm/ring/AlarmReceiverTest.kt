package com.antbtv.balarm.core.alarm.ring

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.AlarmIntents
import com.antbtv.balarm.core.alarm.AlarmReceiver
import com.antbtv.balarm.core.alarm.notification.AlarmNotifications
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Instant
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AlarmReceiverTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var recording: RecordingEventLog

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val request = ScheduleRequest(AlarmId(5), Instant.parse("2026-10-03T05:30:00Z"), FireKind.SNOOZE)

    @Before
    fun setUp() {
        hilt.inject()
        RingingWakeLocks.resetForTest()
    }

    @After
    fun tearDown() = ShadowPowerManager.clearWakeLocks()

    @Test
    fun `fire starts the ringing service with the same request and holds the cpu`() {
        AlarmReceiver().onReceive(app, AlarmIntents.fire(app, request))

        val started = shadowOf(app).nextStartedService
        assertThat(started.component).isEqualTo(ComponentName(app, RingingService::class.java))
        assertThat(started.action).isEqualTo(AlarmIntents.ACTION_RING)
        assertThat(AlarmIntents.parse(started)).isEqualTo(request)
        assertThat(RingingWakeLocks.isDeliveryHeld()).isTrue()
    }

    @Test
    fun `foreign intent is ignored`() {
        AlarmReceiver().onReceive(app, Intent("x"))

        assertThat(shadowOf(app).nextStartedService).isNull()
        assertThat(RingingWakeLocks.isDeliveryHeld()).isFalse()
    }

    @Test
    fun `refused service start falls back to the self-ringing notification`() {
        val refusing = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent) =
                throw IllegalStateException("ForegroundServiceStartNotAllowed")
        }

        AlarmReceiver().onReceive(refusing, AlarmIntents.fire(app, request))

        val manager = app.getSystemService(NotificationManager::class.java)
        assertThat(shadowOf(manager).getNotification(AlarmNotifications.FALLBACK_ID)).isNotNull()
        assertThat(recording.events)
            .containsExactly(AlarmEvent.ForegroundStartFailed(request.alarmId, "IllegalStateException"))
        assertThat(RingingWakeLocks.isDeliveryHeld()).isFalse()
    }
}
