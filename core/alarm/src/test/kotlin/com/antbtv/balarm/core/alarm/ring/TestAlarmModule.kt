package com.antbtv.balarm.core.alarm.ring

import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.alarm.di.AlarmModule
import com.antbtv.balarm.core.alarm.sound.AlarmSoundPlayer
import com.antbtv.balarm.core.alarm.sound.AlarmVibrator
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Singleton

/**
 * Граф Hilt-тестов `:core:alarm`: вместо [AlarmModule] — настоящий [AlarmEngine] на фейках из
 * testFixtures `:core:domain` плюс фейковые звук/вибрация. Синглтоны новые на каждый тест;
 * тест получает их через `@Inject` и настраивает.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AlarmModule::class])
object TestAlarmModule {
    val NOW: Instant = Instant.parse("2026-10-03T05:30:00Z")

    @Provides @Singleton
    fun repository() = FakeAlarmRepository()

    @Provides @Singleton
    fun scheduler() = FakeAlarmScheduler()

    @Provides @Singleton
    fun mutableClock() = MutableClock(NOW, ZoneOffset.UTC)

    @Provides
    fun clock(clock: MutableClock): Clock = clock

    @Provides @Singleton
    fun recordingLog() = RecordingEventLog()

    @Provides
    fun log(recording: RecordingEventLog): AlarmEventLog = recording

    @Provides @Singleton
    fun engine(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
        clock: MutableClock,
        log: RecordingEventLog,
    ) = AlarmEngine(repository, scheduler, clock, { true }, log)

    @Provides @Singleton
    fun fakeSound() = FakeSoundPlayer()

    @Provides
    fun sound(fake: FakeSoundPlayer): AlarmSoundPlayer = fake

    @Provides @Singleton
    fun fakeVibrator() = FakeVibrator()

    @Provides
    fun vibrator(fake: FakeVibrator): AlarmVibrator = fake

    @Provides
    fun uiIntents(): AlarmUiIntents = FakeUiIntents()
}
