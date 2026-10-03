package com.antbtv.balarm.core.alarm.ring

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.AlarmIntents
import com.antbtv.balarm.core.alarm.notification.AlarmNotifications
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.DismissReason
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.domain.alarm.SkipReason
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RingingServiceTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var repository: FakeAlarmRepository

    @Inject lateinit var scheduler: FakeAlarmScheduler

    @Inject lateinit var fakeSound: FakeSoundPlayer

    @Inject lateinit var fakeVibrator: FakeVibrator

    @Inject lateinit var recording: RecordingEventLog

    @Inject lateinit var controller: RingingControllerImpl

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private lateinit var service: ServiceController<RingingService>
    private var startId = 0

    private val events get() = recording.events

    @Before
    fun setUp() {
        hilt.inject()
        RingingWakeLocks.resetForTest()
        save(ALARM)
        save(OTHER)
        service = Robolectric.buildService(RingingService::class.java).create()
    }

    @After
    fun tearDown() = ShadowPowerManager.clearWakeLocks()

    @Test
    fun `ring goes foreground with an alarm notification that opens the ringing screen`() {
        start(ringIntent(ALARM.id))

        val shadow = shadowOf(service.get())
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(AlarmNotifications.RINGING_ID)
        val notification = shadow.lastForegroundNotification
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(shadowOf(notification.fullScreenIntent).savedIntent.action).isEqualTo(FakeUiIntents.RINGING_SCREEN)
    }

    @Test
    fun `ring plays sound, vibrates and publishes the ringing state`() {
        start(ringIntent(ALARM.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat(fakeVibrator.vibrating).isTrue()
        val state = controller.state.value as RingingState.Ringing
        assertThat(state.alarm.id).isEqualTo(ALARM.id)
        assertThat(state.alarm.label).isEqualTo("Work")
        assertThat(state.startedAt).isEqualTo(NOW)
        assertThat(state.canSnooze).isTrue()
        assertThat(events).contains(AlarmEvent.RingingStarted(ALARM.id, degraded = false))
    }

    @Test
    fun `updated notification carries label, time and commands`() {
        start(ringIntent(ALARM.id))

        val notification = shadowOf(notificationManager).getNotification(AlarmNotifications.RINGING_ID)
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Work")
        val actions = notification.actions.map { shadowOf(it.actionIntent).savedIntent }
        assertThat(actions.map { it.action })
            .containsExactly(AlarmIntents.ACTION_SNOOZE, AlarmIntents.ACTION_DISMISS)
        assertThat(actions.map { AlarmIntents.alarmId(it) }).containsExactly(ALARM.id, ALARM.id)
    }

    @Test
    fun `no vibration setting - vibrates only when sound falls back`() {
        save(ALARM.copy(vibrate = false))

        start(ringIntent(ALARM.id))
        assertThat(fakeVibrator.vibrating).isFalse()

        fakeSound.failOver()
        assertThat(fakeVibrator.vibrating).isTrue()
    }

    @Test
    fun `dismiss silences, records and stops the service`() {
        start(ringIntent(ALARM.id))

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        assertThat(fakeSound.playing).isFalse()
        assertThat(fakeVibrator.vibrating).isFalse()
        assertThat(events).contains(AlarmEvent.Dismissed(ALARM.id, DismissReason.USER))
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
        assertStoppedAndReleased()
    }

    @Test
    fun `snooze stops ringing without dismissing`() {
        start(ringIntent(ALARM.id))

        start(AlarmIntents.command(app, AlarmIntents.ACTION_SNOOZE, ALARM.id))

        assertThat(fakeSound.playing).isFalse()
        assertThat(scheduler.scheduled[ALARM.id]?.kind).isEqualTo(FireKind.SNOOZE)
        assertThat(events.filterIsInstance<AlarmEvent.Dismissed>()).isEmpty()
        assertStoppedAndReleased()
    }

    @Test
    fun `refused snooze keeps ringing and hides the snooze button`() {
        start(ringIntent(ALARM.id))
        scheduler.accept = false // система не приняла отложенный будильник → SnoozeResult.NotAllowed

        start(AlarmIntents.command(app, AlarmIntents.ACTION_SNOOZE, ALARM.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat((controller.state.value as RingingState.Ringing).canSnooze).isFalse()
        val notification = shadowOf(notificationManager).getNotification(AlarmNotifications.RINGING_ID)
        assertThat(notification.actions.map { shadowOf(it.actionIntent).savedIntent.action })
            .containsExactly(AlarmIntents.ACTION_DISMISS)
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
    }

    @Test
    fun `skipped fire never makes a sound and stops the service`() {
        runBlocking { repository.delete(ALARM.id) }

        start(ringIntent(ALARM.id))

        assertThat(events).contains(AlarmEvent.FireSkipped(ALARM.id, SkipReason.DELETED))
        assertThat(fakeSound.starts).isEqualTo(0)
        assertStoppedAndReleased()
    }

    @Test
    fun `second alarm waits and rings after the first is dismissed`() {
        start(ringIntent(ALARM.id))
        start(ringIntent(OTHER.id))

        assertThat((controller.state.value as RingingState.Ringing).alarm.id).isEqualTo(ALARM.id)
        assertThat((controller.state.value as RingingState.Ringing).queued).isEqualTo(1)
        assertThat(events).contains(AlarmEvent.RingingQueued(OTHER.id))

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        assertThat((controller.state.value as RingingState.Ringing).alarm.id).isEqualTo(OTHER.id)
        assertThat(fakeSound.playing).isTrue()
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
    }

    @Test
    fun `command for another alarm is ignored`() {
        start(ringIntent(ALARM.id))

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, OTHER.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat(events.filterIsInstance<AlarmEvent.Dismissed>()).isEmpty()
    }

    @Test
    fun `stale command with nothing ringing just stops`() {
        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        assertThat(shadowOf(service.get()).isStoppedBySelf).isTrue()
        assertThat(events.filterIsInstance<AlarmEvent.Dismissed>()).isEmpty()
    }

    @Test
    fun `refused foreground start posts the self-ringing fallback`() {
        shadowOf(service.get()).setThrowInStartForeground(IllegalStateException("refused"))

        start(ringIntent(ALARM.id))

        assertThat(shadowOf(notificationManager).getNotification(AlarmNotifications.FALLBACK_ID)).isNotNull()
        assertThat(events).contains(AlarmEvent.ForegroundStartFailed(ALARM.id, "IllegalStateException"))
        assertThat(fakeSound.starts).isEqualTo(0)
        assertThat(shadowOf(service.get()).isStoppedBySelf).isTrue()
    }

    @Test
    fun `service start takes over the delivery wake lock`() {
        RingingWakeLocks.acquireDelivery(app)

        start(ringIntent(ALARM.id))

        assertThat(RingingWakeLocks.isDeliveryHeld()).isFalse()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
    }

    @Test
    fun `destroy while ringing silences and releases everything`() {
        start(ringIntent(ALARM.id))
        val sessionLock = ShadowPowerManager.getLatestWakeLock()

        service.destroy()

        assertThat(fakeSound.playing).isFalse()
        assertThat(fakeVibrator.vibrating).isFalse()
        assertThat(sessionLock.isHeld).isFalse()
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
        assertThat(events).contains(AlarmEvent.RingingStopped(ALARM.id, "destroyed"))
    }

    @Test
    fun `waiting alarm is recorded by the engine at once, not after the first ends`() {
        start(ringIntent(ALARM.id))

        start(ringIntent(OTHER.id))

        // Движок зафиксировал срабатывание и спланировал следующее, пока первый ещё звонит.
        assertThat(events.filterIsInstance<AlarmEvent.Fired>().map { it.id }).containsExactly(ALARM.id, OTHER.id)
    }

    @Test
    fun `failing dismiss record still silences and stops`() {
        start(ringIntent(ALARM.id))
        repository.failRuntimeFor += ALARM.id

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        assertThat(fakeSound.playing).isFalse()
        assertThat(events).contains(AlarmEvent.RingingCommandFailed("dismiss", "IllegalStateException"))
        assertStoppedAndReleased()
    }

    @Test
    fun `failing snooze keeps ringing`() {
        start(ringIntent(ALARM.id))
        scheduler.throwFor += ALARM.id

        start(AlarmIntents.command(app, AlarmIntents.ACTION_SNOOZE, ALARM.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat(events).contains(AlarmEvent.RingingCommandFailed("snooze", "IllegalStateException"))
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
    }

    @Test
    fun `slow database - sound starts by the deadline, decision takes over later`() {
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate

        start(ringIntent(ALARM.id))
        assertThat(fakeSound.playing).isFalse()

        idle(RingingService.FIRST_SOUND_DEADLINE)
        assertThat(fakeSound.playing).isTrue()
        assertThat(events).contains(AlarmEvent.RingingStarted(ALARM.id, degraded = true))

        gate.complete(Unit)
        idle()
        assertThat(fakeSound.starts).isEqualTo(1) // не перезапущен
        assertThat((controller.state.value as RingingState.Ringing).alarm.label).isEqualTo("Work")
    }

    @Test
    fun `garbage intent while the engine decides does not stop the service`() {
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate
        start(ringIntent(ALARM.id))

        start(Intent(app, RingingService::class.java).setAction("garbage"))
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()

        gate.complete(Unit)
        idle()
        assertThat(fakeSound.playing).isTrue()
    }

    @Test
    fun `refused foreground for a second alarm keeps the first ringing`() {
        start(ringIntent(ALARM.id))
        shadowOf(service.get()).setThrowInStartForeground(IllegalStateException("refused"))

        start(ringIntent(OTHER.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat(shadowOf(notificationManager).getNotification(AlarmNotifications.FALLBACK_ID)).isNotNull()
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
    }

    @Test
    fun `second ring keeps the full notification with commands`() {
        start(ringIntent(ALARM.id))

        start(ringIntent(OTHER.id))

        val notification = shadowOf(service.get()).lastForegroundNotification
        assertThat(notification.actions).isNotNull()
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Work")
    }

    @Test
    fun `broken ring intent still goes foreground, then stops`() {
        start(Intent(AlarmIntents.ACTION_RING, null, app, RingingService::class.java))

        assertThat(shadowOf(service.get()).lastForegroundNotificationId).isEqualTo(AlarmNotifications.RINGING_ID)
        assertThat(shadowOf(service.get()).isStoppedBySelf).isTrue()
    }

    private fun assertStoppedAndReleased() {
        val shadow = shadowOf(service.get())
        assertThat(shadow.isStoppedBySelf).isTrue()
        assertThat(shadow.isForegroundStopped).isTrue()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isFalse()
    }

    private fun save(alarm: Alarm) = runBlocking { repository.save(alarm) }

    private fun ringIntent(id: AlarmId): Intent = AlarmIntents.ring(app, ScheduleRequest(id, NOW, FireKind.REGULAR))

    private fun start(intent: Intent) {
        service.withIntent(intent).startCommand(0, ++startId)
        idle()
    }

    private fun idle(duration: Duration = Duration.ZERO) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    private companion object {
        val NOW: Instant = TestAlarmModule.NOW
        val ALARM = Alarm(id = AlarmId(1), time = LocalTime.of(7, 30), label = "Work")
        val OTHER = Alarm(id = AlarmId(2), time = LocalTime.of(7, 30))
    }
}
