package com.antbtv.balarm.core.alarm.ring

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.AlarmIntents
import com.antbtv.balarm.core.alarm.notification.AlarmNotificationChannels
import com.antbtv.balarm.core.alarm.notification.AlarmNotifications
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.DismissReason
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.MissionPolicy
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.alarm.RingingPolicy
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.domain.alarm.SkipReason
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.alarm.TestAlarmStore
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.FakeSettingsRepository
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import org.robolectric.shadows.ShadowSettings

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
@Suppress("LargeClass") // сценарии одного сервиса делят общий Hilt-граф и хелперы
class RingingServiceTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var repository: FakeAlarmRepository

    @Inject lateinit var engine: AlarmEngine

    @Inject lateinit var scheduler: FakeAlarmScheduler

    @Inject lateinit var fakeSound: FakeSoundPlayer

    @Inject lateinit var fakeVibrator: FakeVibrator

    @Inject lateinit var recording: RecordingEventLog

    @Inject lateinit var controller: RingingControllerImpl

    @Inject lateinit var missions: FakeMissionCatalog

    @Inject lateinit var testAlarmRunner: TestAlarmRunner

    @Inject lateinit var testAlarmStore: TestAlarmStore

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

    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

    @After
    fun tearDown() {
        ShadowPowerManager.clearWakeLocks()
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

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
    fun `with the overlay permission the ringing screen is also started from the service`() {
        ShadowSettings.setCanDrawOverlays(true)

        start(ringIntent(ALARM.id))

        val started = shadowOf(service.get()).nextStartedActivity
        assertThat(started.action).isEqualTo(FakeUiIntents.RINGING_SCREEN)
        assertThat(shadowOf(service.get()).nextStartedActivity).isNull()
    }

    @Test
    fun `without the overlay permission only the full screen intent is used`() {
        ShadowSettings.setCanDrawOverlays(false)

        start(ringIntent(ALARM.id))

        assertThat(shadowOf(service.get()).nextStartedActivity).isNull()
        assertThat(fakeSound.playing).isTrue()
    }

    @Test
    fun `a failed start of the ringing screen does not stop the sound`() {
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app).checkActivities(true) // нет Activity под интент → ActivityNotFoundException

        start(ringIntent(ALARM.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat(
            events,
        ).contains(AlarmEvent.RingingCommandFailed("start_ringing_screen", "ActivityNotFoundException"))
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
    fun `ring plays the alarm's own sound with fade-in`() {
        val custom =
            SoundSettings(SoundRef.Builtin(BuiltinSound.BELLS), volumePercent = 40, fadeIn = Duration.ofSeconds(30))
        save(ALARM.copy(sound = custom))

        start(ringIntent(ALARM.id))

        assertThat(fakeSound.lastSettings).isEqualTo(custom)
        assertThat(fakeSound.lastFadeIn).isTrue()
    }

    @Test
    fun `quiet alarm stream is raised for the ring and restored on dismiss`() {
        val audio = app.getSystemService(AudioManager::class.java)
        shadowOf(audio).setStreamMaxVolume(7)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)

        start(ringIntent(ALARM.id))
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(6) // 80 % по умолчанию

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(1)
    }

    @Test
    fun `phone call mutes the melody and vibrates, then the melody returns`() {
        save(ALARM.copy(vibrate = false))
        start(ringIntent(ALARM.id))
        assertThat(fakeVibrator.vibrating).isFalse()

        service.get().onAudioMode(AudioManager.MODE_RINGTONE)
        assertThat(fakeSound.muted).isTrue()
        assertThat(fakeVibrator.vibrating).isTrue()

        service.get().onAudioMode(AudioManager.MODE_IN_CALL) // звонок принят — всё ещё разговор
        assertThat(fakeSound.muted).isTrue()

        service.get().onAudioMode(AudioManager.MODE_NORMAL)
        assertThat(fakeSound.muted).isFalse()
        assertThat(fakeVibrator.vibrating).isFalse() // вибрация выключена в будильнике
    }

    @Test
    fun `stuck call mode does not keep the melody muted forever`() {
        start(ringIntent(ALARM.id))

        service.get().onAudioMode(AudioManager.MODE_IN_COMMUNICATION)
        assertThat(fakeSound.muted).isTrue()

        idle(RingingService.MUTE_TIMEOUT.plusSeconds(1))
        assertThat(fakeSound.muted).isFalse()
    }

    @Test
    fun `alarm that fires during a call starts muted`() {
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL

        start(ringIntent(ALARM.id))

        assertThat(fakeSound.playing).isTrue()
        assertThat(fakeSound.muted).isTrue()
        assertThat(fakeVibrator.vibrating).isTrue()
    }

    @Test
    fun `audio mode changes without a ring do nothing`() {
        service.get().onAudioMode(AudioManager.MODE_IN_CALL)

        assertThat(fakeSound.muted).isFalse()
        assertThat(fakeVibrator.vibrating).isFalse()
    }

    @Test
    fun `resumed and caught up rings play without fade-in`() {
        save(ALARM.copy(sound = SoundSettings(fadeIn = Duration.ofSeconds(30))))

        start(AlarmIntents.ring(app, ScheduleRequest(ALARM.id, NOW, FireKind.RESUME)))

        assertThat(fakeSound.lastFadeIn).isFalse()
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

    private val withMission = ALARM.copy(mission = MissionSpec(MissionTypeId.MATH, "{}"))

    private fun mission(action: String) = start(AlarmIntents.command(app, action, ALARM.id))

    private fun ringState() = controller.state.value as RingingState.Ringing

    @Test
    fun `alarm with a mission publishes it and rejects dismiss from the shade`() {
        save(withMission)
        start(ringIntent(ALARM.id))

        assertThat(ringState().mission).isEqualTo(withMission.mission)
        mission(AlarmIntents.ACTION_DISMISS)

        assertThat(fakeSound.playing).isTrue()
        assertThat(events).contains(AlarmEvent.Mission(ALARM.id, "dismiss_rejected"))
        assertThat(events).doesNotContain(AlarmEvent.Dismissed(ALARM.id, DismissReason.USER))
    }

    @Test
    fun `unavailable mission dismisses like a plain alarm`() {
        save(withMission)
        missions.available = false
        start(ringIntent(ALARM.id))

        assertThat(ringState().mission).isNull()
        mission(AlarmIntents.ACTION_DISMISS)

        assertThat(events).contains(AlarmEvent.Dismissed(ALARM.id, DismissReason.USER))
    }

    @Inject lateinit var appSettings: FakeSettingsRepository

    @Test
    fun `auto stop follows the settings snapshot`() {
        runBlocking { appSettings.update { it.copy(autoStop = AutoStop.MIN_5) } }
        start(ringIntent(ALARM.id))
        idle(Duration.ofMinutes(4))
        assertThat(fakeSound.playing).isTrue()

        idle(Duration.ofMinutes(2))

        assertThat(fakeSound.playing).isFalse()
        assertThat(events).contains(AlarmEvent.RingingStopped(ALARM.id, "auto_stop"))
    }

    @Test
    fun `auto stop never keeps ringing past the default limit`() {
        runBlocking { appSettings.update { it.copy(autoStop = AutoStop.NEVER) } }
        start(ringIntent(ALARM.id))

        var elapsed = 0L
        for (minutes in listOf(31L, 60L, 95L)) {
            idle(Duration.ofMinutes(minutes - elapsed))
            elapsed = minutes
            assertThat(fakeSound.playing).isTrue()
            assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue() // продлевается, не истекает
        }
        assertThat(events).doesNotContain(AlarmEvent.RingingStopped(ALARM.id, "auto_stop"))

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isFalse()
    }

    @Test
    fun `unreadable settings fall back to defaults without delaying the sound`() {
        appSettings.hang = true

        start(ringIntent(ALARM.id))
        assertThat(fakeSound.playing).isTrue()
        idle(Duration.ofSeconds(2))
        assertThat(events).contains(AlarmEvent.SettingsDefaulted(ALARM.id))

        idle(RingingPolicy.AUTO_STOP_AFTER)
        assertThat(fakeSound.playing).isFalse()
    }

    @Test
    fun `failing settings fall back to defaults`() {
        appSettings.failure = IllegalStateException("datastore")

        start(ringIntent(ALARM.id))
        idle(Duration.ofSeconds(1))

        assertThat(fakeSound.playing).isTrue()
        assertThat(events).contains(AlarmEvent.SettingsDefaulted(ALARM.id))
    }

    @Test
    fun `mission uses idle timeout and duck level from settings`() {
        runBlocking {
            appSettings.update { it.copy(missionIdleTimeout = Duration.ofSeconds(40), missionDuckPercent = 50) }
        }
        save(withMission)
        start(ringIntent(ALARM.id))
        idle(Duration.ofSeconds(1))
        assertThat(ringState().missionIdleTimeout).isEqualTo(Duration.ofSeconds(40))

        mission(AlarmIntents.ACTION_MISSION_START)
        assertThat(fakeSound.duck).isEqualTo(0.5f)
        idle(Duration.ofSeconds(39))
        assertThat(ringState().missionRun?.idleResets).isEqualTo(0)

        idle(Duration.ofSeconds(2))
        assertThat(ringState().missionRun?.idleResets).isEqualTo(1)
    }

    @Test
    fun `each alarm of the queue takes its own snapshot`() {
        runBlocking { appSettings.update { it.copy(autoStop = AutoStop.MIN_5) } }
        start(ringIntent(ALARM.id))
        start(ringIntent(OTHER.id))
        idle(Duration.ofSeconds(1))
        runBlocking { appSettings.update { it.copy(autoStop = AutoStop.MIN_10) } }

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        idle(Duration.ofMinutes(7))

        assertThat(controller.state.value).isInstanceOf(RingingState.Ringing::class.java) // у второго — 10 мин
        idle(Duration.ofMinutes(4))
        assertThat(events).contains(AlarmEvent.RingingStopped(OTHER.id, "auto_stop"))
    }

    private fun alarmStream() = app.getSystemService(AudioManager::class.java)

    @Test
    fun `volume guard restores a lowered stream during a mission ring but leaves a raise alone`() {
        shadowOf(alarmStream()).setStreamMaxVolume(MAX_VOLUME)
        shadowOf(alarmStream()).setStreamMinVolume(1)
        save(withMission)
        start(ringIntent(ALARM.id))
        val applied = alarmStream().getStreamVolume(AudioManager.STREAM_ALARM)

        alarmStream().setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        idle(Duration.ofSeconds(2))
        assertThat(alarmStream().getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(applied)
        assertThat(events.filterIsInstance<AlarmEvent.VolumeGuard>()).hasSize(1)

        alarmStream().setStreamVolume(AudioManager.STREAM_ALARM, MAX_VOLUME, 0)
        idle(Duration.ofSeconds(2))
        assertThat(alarmStream().getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(MAX_VOLUME)
    }

    @Test
    fun `volume guard is off for a ring without a mission and after the ring ends`() {
        shadowOf(alarmStream()).setStreamMaxVolume(MAX_VOLUME)
        shadowOf(alarmStream()).setStreamMinVolume(1)
        start(ringIntent(ALARM.id))
        alarmStream().setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        idle(Duration.ofSeconds(3))
        assertThat(events.filterIsInstance<AlarmEvent.VolumeGuard>()).isEmpty()
    }

    private fun startMissionRing() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)
        assertThat(ringState().missionRun).isNotNull()
    }

    @Test
    fun `paused screen that stays paused ends the mission session and comes back`() {
        shadowOf(app).grantPermissions(android.Manifest.permission.SYSTEM_ALERT_WINDOW)
        startMissionRing()

        mission(AlarmIntents.ACTION_MISSION_PAUSED)
        idle(Duration.ofMillis(500))
        assertThat(ringState().missionRun).isNotNull() // ещё в пределах паузы
        idle(Duration.ofMillis(600))

        assertThat(ringState().missionRun).isNull()
        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(events).contains(AlarmEvent.Mission(ALARM.id, "hidden"))
    }

    @Test
    fun `pause check waits for a phone call to end`() {
        startMissionRing()
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        mission(AlarmIntents.ACTION_MISSION_PAUSED)
        idle(Duration.ofSeconds(5))
        assertThat(ringState().missionRun).isNotNull()

        service.get().onAudioMode(AudioManager.MODE_NORMAL)
        idle(Duration.ofSeconds(3))

        assertThat(events).contains(AlarmEvent.Mission(ALARM.id, "hidden"))
    }

    @Test
    fun `screen that returns quickly keeps the mission session`() {
        startMissionRing()

        mission(AlarmIntents.ACTION_MISSION_PAUSED)
        idle(Duration.ofMillis(300))
        mission(AlarmIntents.ACTION_MISSION_SHOWN)
        idle(Duration.ofSeconds(2))

        assertThat(ringState().missionRun).isNotNull()
        assertThat(events).doesNotContain(AlarmEvent.Mission(ALARM.id, "hidden"))
    }

    @Test
    fun `pause with the display off is not a hidden screen`() {
        startMissionRing()
        shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)

        mission(AlarmIntents.ACTION_MISSION_PAUSED)
        idle(Duration.ofSeconds(2))

        assertThat(events).doesNotContain(AlarmEvent.Mission(ALARM.id, "hidden"))
    }

    @Test
    fun `screen off during a mission makes the sound loud and ends the session`() {
        startMissionRing()

        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle()

        assertThat(ringState().missionRun).isNull()
        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(events).contains(AlarmEvent.Mission(ALARM.id, "screen_off"))
    }

    @Test
    fun `screen off is ignored outside of a mission session and during a call`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle()
        assertThat(events).doesNotContain(AlarmEvent.Mission(ALARM.id, "screen_off")) // сессии нет, ресивера нет

        mission(AlarmIntents.ACTION_MISSION_START)
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle()
        assertThat(ringState().missionRun).isNotNull()
    }

    @Test
    fun `screen off receiver is gone once the mission session ends`() {
        startMissionRing()
        mission(AlarmIntents.ACTION_MISSION_LEAVE)

        assertThat(shadowOf(app).registeredReceivers.filter { it.intentFilter.hasAction(Intent.ACTION_SCREEN_OFF) })
            .isEmpty()
    }

    @Test
    fun `resume for an alarm that is already ringing is ignored`() {
        start(ringIntent(ALARM.id))
        val started = events.count { it is AlarmEvent.RingingStarted }

        start(AlarmIntents.ring(app, ScheduleRequest(ALARM.id, NOW, FireKind.RESUME)))

        assertThat(events).contains(AlarmEvent.FireSkipped(ALARM.id, SkipReason.ALREADY_RINGING))
        assertThat(events.count { it is AlarmEvent.RingingStarted }).isEqualTo(started)
        assertThat(ringState().alarm.id).isEqualTo(ALARM.id)
    }

    @Test
    fun `ignored resume puts the regular schedule back instead of itself`() {
        save(DAILY)
        start(ringIntent(ALARM.id))
        scheduler.scheduled[ALARM.id] = ScheduleRequest(ALARM.id, NOW + Duration.ofSeconds(3), FireKind.RESUME)

        start(AlarmIntents.ring(app, ScheduleRequest(ALARM.id, NOW, FireKind.RESUME)))

        assertThat(scheduler.scheduled[ALARM.id]?.kind).isEqualTo(FireKind.REGULAR)
    }

    @Test
    fun `crash during a mission re-arms the alarm without the mission`() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException())

        assertThat(scheduler.scheduled[ALARM.id]?.skipMission).isTrue()
    }

    @Test
    fun `crash outside a mission keeps the mission for the resumed ring`() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        save(withMission)
        start(ringIntent(ALARM.id))

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException())

        assertThat(scheduler.scheduled[ALARM.id]?.skipMission).isFalse()
    }

    @Test
    fun `resume flagged skip-mission rings without the mission`() {
        save(withMission)

        start(AlarmIntents.ring(app, ScheduleRequest(ALARM.id, NOW, FireKind.RESUME, skipMission = true)))

        assertThat(ringState().mission).isNull()
        assertThat(events).contains(AlarmEvent.Mission(ALARM.id, "skipped_after_crash"))
        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        assertThat(events).contains(AlarmEvent.Dismissed(ALARM.id, DismissReason.USER))
    }

    @Test
    fun `starting a mission ducks the sound and pauses vibration, leaving restores them`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        assertThat(fakeVibrator.vibrating).isTrue()

        mission(AlarmIntents.ACTION_MISSION_START)

        assertThat(fakeSound.duck).isEqualTo(MissionPolicy.DUCK_GAIN)
        assertThat(fakeVibrator.vibrating).isFalse()
        assertThat(ringState().missionRun).isNotNull()

        mission(AlarmIntents.ACTION_MISSION_LEAVE)

        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(fakeVibrator.vibrating).isTrue()
        assertThat(ringState().missionRun).isNull()
        assertThat(fakeSound.playing).isTrue()
    }

    @Test
    fun `idle timeout makes the sound loud again and signals a reset, a touch ducks it again`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        idle(MissionPolicy.IDLE_TIMEOUT.plusSeconds(1))

        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(ringState().missionRun?.idleResets).isEqualTo(1)
        assertThat(events).contains(AlarmEvent.Mission(ALARM.id, "idle"))

        mission(AlarmIntents.ACTION_MISSION_TOUCH)
        assertThat(fakeSound.duck).isEqualTo(MissionPolicy.DUCK_GAIN)

        idle(MissionPolicy.IDLE_TIMEOUT.minusSeconds(1))
        assertThat(fakeSound.duck).isEqualTo(MissionPolicy.DUCK_GAIN) // касание продлило таймер
        idle(Duration.ofSeconds(2))
        assertThat(ringState().missionRun?.idleResets).isEqualTo(2)
    }

    @Test
    fun `hidden mission screen makes the sound loud immediately`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        mission(AlarmIntents.ACTION_MISSION_HIDDEN)

        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(ringState().missionRun).isNull()
    }

    @Test
    fun `hidden screen of a mission alarm comes back once after the return delay`() {
        ShadowSettings.setCanDrawOverlays(true)
        save(withMission)
        start(ringIntent(ALARM.id))
        val shadow = shadowOf(service.get())
        assertThat(shadow.nextStartedActivity).isNotNull() // старт экрана при начале звонка

        mission(AlarmIntents.ACTION_MISSION_HIDDEN)
        mission(AlarmIntents.ACTION_MISSION_HIDDEN) // повторное скрытие не копит запуски
        assertThat(shadow.nextStartedActivity).isNull()

        idle(RingingService.SCREEN_RETURN_DELAY)

        assertThat(shadow.nextStartedActivity.action).isEqualTo(FakeUiIntents.RINGING_SCREEN)
        assertThat(shadow.nextStartedActivity).isNull()
        assertThat(events.count { it == AlarmEvent.Mission(ALARM.id, "screen_returned") }).isEqualTo(1)
        assertThat(fakeSound.playing).isTrue()
        idle(RingingService.SCREEN_RETURN_DELAY.multipliedBy(5))
        assertThat(shadow.nextStartedActivity).isNull() // без нового скрытия — без повторов
    }

    @Test
    fun `hidden screen is not returned without overlay permission, without a mission or after the ring ends`() {
        ShadowSettings.setCanDrawOverlays(false)
        save(withMission)
        start(ringIntent(ALARM.id))
        val shadow = shadowOf(service.get())
        mission(AlarmIntents.ACTION_MISSION_HIDDEN)
        idle(RingingService.SCREEN_RETURN_DELAY)
        assertThat(shadow.nextStartedActivity).isNull()

        ShadowSettings.setCanDrawOverlays(true)
        mission(AlarmIntents.ACTION_MISSION_HIDDEN)
        mission(AlarmIntents.ACTION_MISSION_START)
        mission(AlarmIntents.ACTION_MISSION_DONE) // звонок закончился раньше, чем подошло время возврата
        idle(RingingService.SCREEN_RETURN_DELAY)
        assertThat(shadow.nextStartedActivity).isNull()
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
    }

    @Test
    fun `hidden screen of an alarm without a mission is not returned`() {
        ShadowSettings.setCanDrawOverlays(true)
        start(ringIntent(ALARM.id))
        val shadow = shadowOf(service.get())
        assertThat(shadow.nextStartedActivity).isNotNull()

        mission(AlarmIntents.ACTION_MISSION_HIDDEN)
        idle(RingingService.SCREEN_RETURN_DELAY)

        assertThat(shadow.nextStartedActivity).isNull()
        assertThat(fakeSound.playing).isTrue()
    }

    @Test
    fun `hidden screen is not pushed over a phone call`() {
        ShadowSettings.setCanDrawOverlays(true)
        save(withMission)
        start(ringIntent(ALARM.id))
        val shadow = shadowOf(service.get())
        assertThat(shadow.nextStartedActivity).isNotNull()

        service.get().onAudioMode(AudioManager.MODE_IN_CALL)
        mission(AlarmIntents.ACTION_MISSION_HIDDEN)
        idle(RingingService.SCREEN_RETURN_DELAY)

        assertThat(shadow.nextStartedActivity).isNull()
    }

    @Test
    fun `completing a started mission dismisses the alarm`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        mission(AlarmIntents.ACTION_MISSION_DONE)

        assertThat(fakeSound.playing).isFalse()
        assertThat(events).contains(AlarmEvent.Dismissed(ALARM.id, DismissReason.USER))
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
    }

    @Test
    fun `completion without a started mission is ignored`() {
        save(withMission)
        start(ringIntent(ALARM.id))

        mission(AlarmIntents.ACTION_MISSION_DONE)

        assertThat(fakeSound.playing).isTrue()
        assertThat(events).doesNotContain(AlarmEvent.Dismissed(ALARM.id, DismissReason.USER))
    }

    @Test
    fun `auto stop in the middle of a mission silences everything and clears the session`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        idle(RingingPolicy.AUTO_STOP_AFTER.plusSeconds(1))

        assertThat(fakeSound.playing).isFalse()
        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
    }

    @Test
    fun `snooze in the middle of a mission clears the session`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        mission(AlarmIntents.ACTION_SNOOZE)

        assertThat(fakeSound.playing).isFalse()
        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
    }

    @Test
    fun `next alarm of the queue starts without the previous mission session`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        start(ringIntent(OTHER.id))
        mission(AlarmIntents.ACTION_MISSION_START)

        mission(AlarmIntents.ACTION_MISSION_DONE)

        val next = ringState()
        assertThat(next.alarm.id).isEqualTo(OTHER.id)
        assertThat(next.missionRun).isNull()
        assertThat(fakeSound.duck).isEqualTo(1f)
    }

    @Test
    fun `touch during a phone call keeps vibrating`() {
        save(withMission)
        start(ringIntent(ALARM.id))
        mission(AlarmIntents.ACTION_MISSION_START)
        service.get().onAudioMode(AudioManager.MODE_IN_CALL)

        mission(AlarmIntents.ACTION_MISSION_TOUCH)

        assertThat(fakeVibrator.vibrating).isTrue()
    }

    @Test
    fun `mission commands on an alarm without a mission change nothing`() {
        start(ringIntent(ALARM.id))

        mission(AlarmIntents.ACTION_MISSION_START)

        assertThat(fakeSound.duck).isEqualTo(1f)
        assertThat(ringState().missionRun).isNull()
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
    fun `the test alarm rings the draft without snooze and dismiss leaves storage alone`() {
        testAlarmRunner.schedule(Alarm(time = LocalTime.of(7, 30), label = "Draft"), Duration.ofSeconds(5))

        start(ringIntent(AlarmId.TEST))

        val state = controller.state.value as RingingState.Ringing
        assertThat(state.alarm.id).isEqualTo(AlarmId.TEST)
        assertThat(state.alarm.label).isEqualTo("Draft")
        assertThat(state.canSnooze).isFalse()
        assertThat(fakeSound.playing).isTrue()
        assertThat(events).contains(AlarmEvent.RingingStarted(AlarmId.TEST, degraded = false))

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, AlarmId.TEST))

        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
        assertThat(runBlocking { repository.loadAll() }.map { it.alarm.id }).containsExactly(ALARM.id, OTHER.id)
        assertStoppedAndReleased()
    }

    @Test
    fun `the test alarm and a user alarm queue behind each other in both directions`() {
        testAlarmRunner.schedule(Alarm(time = LocalTime.of(7, 30), label = "Draft"), Duration.ofSeconds(5))

        start(ringIntent(ALARM.id))
        start(ringIntent(AlarmId.TEST))
        assertThat(events).contains(AlarmEvent.RingingQueued(AlarmId.TEST))
        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        val test = controller.state.value as RingingState.Ringing
        assertThat(test.alarm.id).isEqualTo(AlarmId.TEST)
        assertThat(test.canSnooze).isFalse()

        start(ringIntent(OTHER.id))
        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, AlarmId.TEST))

        assertThat((controller.state.value as RingingState.Ringing).alarm.id).isEqualTo(OTHER.id)
    }

    @Test
    fun `nobody reacts to the test alarm - it stops without a missed notification`() {
        testAlarmRunner.schedule(Alarm(time = LocalTime.of(7, 30)), Duration.ofSeconds(5))
        start(ringIntent(AlarmId.TEST))

        idle(RingingPolicy.AUTO_STOP_AFTER)

        assertThat(fakeSound.playing).isFalse()
        assertThat(events).contains(AlarmEvent.Dismissed(AlarmId.TEST, DismissReason.AUTO_STOP))
        assertThat(shadowOf(notificationManager).activeNotifications.map { it.tag })
            .doesNotContain(AlarmNotifications.missedTag(AlarmId.TEST))
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
    }

    @Test
    fun `crash while the test rings re-arms it and the resumed test rings with defaults`() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        testAlarmRunner.schedule(Alarm(time = LocalTime.of(7, 30), label = "Draft"), Duration.ofSeconds(5))
        start(ringIntent(AlarmId.TEST))

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException())

        val at = NOW + RingingService.CRASH_RESUME_DELAY
        assertThat(scheduler.scheduled[AlarmId.TEST]).isEqualTo(ScheduleRequest(AlarmId.TEST, at, FireKind.RESUME))
        service.destroy() // процесс «умер»: снимок черновика в памяти потерян
        testAlarmStore.clear()
        service = Robolectric.buildService(RingingService::class.java).create()

        start(AlarmIntents.ring(app, ScheduleRequest(AlarmId.TEST, at, FireKind.RESUME)))

        val state = controller.state.value as RingingState.Ringing
        assertThat(state.alarm.id).isEqualTo(AlarmId.TEST)
        assertThat(state.canSnooze).isFalse()
        assertThat(fakeSound.playing).isTrue()
    }

    @Test
    fun `while an alarm rings a safety RESUME is kept ahead so SIGKILL does not silence it`() {
        start(ringIntent(ALARM.id))

        idle(RingingService.HEARTBEAT_PERIOD.multipliedBy(2))

        val resume = scheduler.scheduled[ALARM.id]!!
        assertThat(resume.kind).isEqualTo(FireKind.RESUME)
        assertThat(resume.triggerAt).isGreaterThan(NOW + RingingService.HEARTBEAT_PERIOD)
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
    fun `each ring of the queue starts the ringing screen once`() {
        ShadowSettings.setCanDrawOverlays(true)

        start(ringIntent(ALARM.id))
        start(ringIntent(OTHER.id)) // в очереди: экран не запускается повторно
        val shadow = shadowOf(service.get())
        assertThat(shadow.nextStartedActivity).isNotNull()
        assertThat(shadow.nextStartedActivity).isNull()

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        assertThat(shadow.nextStartedActivity).isNotNull()
        assertThat(shadow.nextStartedActivity).isNull()
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

    @Test
    fun `nobody reacts for 30 minutes - stops, reports missed, keeps the regular schedule`() {
        save(DAILY)
        start(ringIntent(ALARM.id))

        idle(RingingPolicy.AUTO_STOP_AFTER)

        assertThat(fakeSound.playing).isFalse()
        assertThat(events).contains(AlarmEvent.RingingStopped(ALARM.id, "auto_stop"))
        assertThat(events).contains(AlarmEvent.Dismissed(ALARM.id, DismissReason.AUTO_STOP))
        val missed = shadowOf(notificationManager).activeNotifications
            .single { it.tag == AlarmNotifications.missedTag(ALARM.id) }
        assertThat(missed.notification.channelId).isEqualTo(AlarmNotificationChannels.MISSED)
        assertThat(scheduler.scheduled[ALARM.id]?.kind).isEqualTo(FireKind.REGULAR)
        assertStoppedAndReleased()
    }

    @Test
    fun `auto stop is counted per alarm and hands over to the queued one`() {
        start(ringIntent(ALARM.id))
        start(ringIntent(OTHER.id))

        idle(RingingPolicy.AUTO_STOP_AFTER)

        assertThat((controller.state.value as RingingState.Ringing).alarm.id).isEqualTo(OTHER.id)
        assertThat(fakeSound.playing).isTrue()
        idle(RingingPolicy.AUTO_STOP_AFTER)
        assertThat(controller.state.value).isEqualTo(RingingState.Idle)
    }

    @Test
    fun `dismissed alarm is not auto stopped later`() {
        start(ringIntent(ALARM.id))
        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        idle(RingingPolicy.AUTO_STOP_AFTER)

        assertThat(events.filterIsInstance<AlarmEvent.Dismissed>().map { it.reason })
            .containsExactly(DismissReason.USER)
    }

    @Test
    fun `dismiss works at once while the engine decides on another alarm`() {
        start(ringIntent(ALARM.id))
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate
        start(ringIntent(OTHER.id)) // движок занят решением по второму

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        assertThat(events).contains(AlarmEvent.RingingStopped(ALARM.id, "dismiss"))

        repository.transactionGate = null
        gate.complete(Unit)
        idle()
        assertThat((controller.state.value as RingingState.Ringing).alarm.id).isEqualTo(OTHER.id)
    }

    @Test
    fun `crash while ringing re-arms ringing and queued alarms, then passes the error on`() {
        val crashes = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
        start(ringIntent(ALARM.id))
        start(ringIntent(OTHER.id))
        val boom = IllegalStateException("ui crashed")

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)

        val at = NOW + RingingService.CRASH_RESUME_DELAY
        assertThat(scheduler.scheduled[ALARM.id]).isEqualTo(ScheduleRequest(ALARM.id, at, FireKind.RESUME))
        assertThat(scheduler.scheduled[OTHER.id]).isEqualTo(ScheduleRequest(OTHER.id, at, FireKind.RESUME))
        assertThat(events).contains(AlarmEvent.CrashRearmed(ALARM.id, at))
        assertThat(crashes).containsExactly(boom)
    }

    @Test
    fun `resumed ring after a crash rings again and dismiss restores the regular schedule`() {
        save(DAILY)
        start(ringIntent(ALARM.id))
        service.destroy() // процесс «умер»
        service = Robolectric.buildService(RingingService::class.java).create()

        start(AlarmIntents.ring(app, ScheduleRequest(ALARM.id, NOW, FireKind.RESUME)))
        assertThat(fakeSound.playing).isTrue()

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        assertThat(scheduler.scheduled[ALARM.id]?.kind).isEqualTo(FireKind.REGULAR)
    }

    @Test
    fun `crash guard is removed when the session ends`() {
        val original = Thread.UncaughtExceptionHandler { _, _ -> }
        Thread.setDefaultUncaughtExceptionHandler(original)
        start(ringIntent(ALARM.id))
        assertThat(Thread.getDefaultUncaughtExceptionHandler()).isNotSameInstanceAs(original)

        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))

        assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameInstanceAs(original)
    }

    @Test
    fun `crash on a background thread still re-arms`() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        start(ringIntent(ALARM.id))
        val guard = Thread.getDefaultUncaughtExceptionHandler()!!

        val worker = Thread { guard.uncaughtException(Thread.currentThread(), IllegalStateException("worker")) }
        worker.start()
        worker.join()

        assertThat(scheduler.scheduled[ALARM.id]?.kind).isEqualTo(FireKind.RESUME)
    }

    @Test
    fun `guard left under a foreign handler stops re-arming after the session`() {
        val crashes = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
        start(ringIntent(ALARM.id))
        val ours = Thread.getDefaultUncaughtExceptionHandler()!!
        val foreign = Thread.UncaughtExceptionHandler { t, e -> ours.uncaughtException(t, e) }
        Thread.setDefaultUncaughtExceptionHandler(foreign) // кто-то поставил свой поверх нашего
        start(AlarmIntents.command(app, AlarmIntents.ACTION_DISMISS, ALARM.id))
        assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameInstanceAs(foreign)

        val boom = IllegalStateException("later")
        foreign.uncaughtException(Thread.currentThread(), boom)

        assertThat(events.filterIsInstance<AlarmEvent.CrashRearmed>()).isEmpty()
        assertThat(crashes).containsExactly(boom)
    }

    @Test
    fun `ringing again clears the missed notification of that alarm`() {
        start(ringIntent(ALARM.id))
        idle(RingingPolicy.AUTO_STOP_AFTER)
        service = Robolectric.buildService(RingingService::class.java).create()

        start(AlarmIntents.ring(app, ScheduleRequest(ALARM.id, NOW, FireKind.RESUME)))

        assertThat(shadowOf(notificationManager).activeNotifications.map { it.tag })
            .doesNotContain(AlarmNotifications.missedTag(ALARM.id))
    }

    @Test
    fun `degraded ring is recorded once the busy engine frees up`() {
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate
        CoroutineScope(Dispatchers.Main).launch { engine.rescheduleAll(RescheduleReason.DEBUG) } // держит движок
        idle()

        start(ringIntent(ALARM.id))
        idle(RingingService.FIRST_SOUND_DEADLINE)
        assertThat(events).contains(AlarmEvent.FireDegraded(ALARM.id, "EngineBusy"))
        assertThat(fakeSound.playing).isTrue()

        repository.transactionGate = null
        gate.complete(Unit)
        idle()

        assertThat(events.filterIsInstance<AlarmEvent.Fired>().map { it.id }).contains(ALARM.id)
        assertThat(runBlocking { repository.get(ALARM.id) }?.enabled).isFalse() // разовый не зазвонит завтра
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
        const val MAX_VOLUME = 7
        val NOW: Instant = TestAlarmModule.NOW
        val ALARM = Alarm(id = AlarmId(1), time = LocalTime.of(7, 30), label = "Work")
        val OTHER = Alarm(id = AlarmId(2), time = LocalTime.of(7, 30))

        /** Повторяющийся — после звонка у него есть обычное следующее срабатывание (у разового — нет). */
        val DAILY = ALARM.copy(repeatDays = DayOfWeek.entries.toSet())
    }
}
