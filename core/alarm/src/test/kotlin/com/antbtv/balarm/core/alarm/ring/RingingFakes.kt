package com.antbtv.balarm.core.alarm.ring

import android.content.Intent
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.alarm.sound.AlarmSoundPlayer
import com.antbtv.balarm.core.alarm.sound.AlarmVibrator

class FakeSoundPlayer : AlarmSoundPlayer {
    var playing = false
        private set
    var starts = 0
        private set
    private var onFallback: () -> Unit = {}

    override fun start(onFallback: () -> Unit) {
        playing = true
        starts++
        this.onFallback = onFallback
    }

    override fun stop() {
        playing = false
    }

    fun failOver() = onFallback()
}

class FakeVibrator : AlarmVibrator {
    var vibrating = false
        private set

    override fun start() {
        vibrating = true
    }

    override fun stop() {
        vibrating = false
    }
}

class FakeUiIntents : AlarmUiIntents {
    override fun ringingScreen() = Intent(RINGING_SCREEN)

    override fun alarmList() = Intent(ALARM_LIST)

    companion object {
        const val RINGING_SCREEN = "test.RINGING_SCREEN"
        const val ALARM_LIST = "test.ALARM_LIST"
    }
}
