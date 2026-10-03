package com.antbtv.balarm.core.alarm

import android.util.Log
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import javax.inject.Inject

/** События в logcat с тегом `Balarm` — и в release: по ним диагностируются сценарии надёжности. */
class LogcatAlarmEventLog @Inject constructor() : AlarmEventLog {
    override fun log(event: AlarmEvent) {
        Log.i(TAG, event.toLogLine())
    }

    companion object {
        const val TAG = "Balarm"
    }
}
