package com.antbtv.balarm.core.alarm.di

import com.antbtv.balarm.core.alarm.AlarmSchedulerImpl
import com.antbtv.balarm.core.alarm.LogcatAlarmEventLog
import com.antbtv.balarm.core.alarm.ring.RingingControllerImpl
import com.antbtv.balarm.core.alarm.sound.AlarmSoundPlayer
import com.antbtv.balarm.core.alarm.sound.AlarmVibrator
import com.antbtv.balarm.core.alarm.sound.MediaAlarmSoundPlayer
import com.antbtv.balarm.core.alarm.sound.SystemAlarmVibrator
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.AlarmScheduler
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.schedule.SystemZoneClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
interface AlarmModule {
    @Binds
    fun bindScheduler(impl: AlarmSchedulerImpl): AlarmScheduler

    @Binds
    fun bindEventLog(impl: LogcatAlarmEventLog): AlarmEventLog

    @Binds
    fun bindRingingController(impl: RingingControllerImpl): RingingController

    @Binds
    fun bindSoundPlayer(impl: MediaAlarmSoundPlayer): AlarmSoundPlayer

    @Binds
    fun bindVibrator(impl: SystemAlarmVibrator): AlarmVibrator

    companion object {
        /** Часы, читающие текущую зону на каждый вызов (ADR-006 §4). */
        @Provides
        fun provideClock(): Clock = SystemZoneClock()
    }
}
