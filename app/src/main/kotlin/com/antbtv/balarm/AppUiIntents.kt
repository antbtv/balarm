package com.antbtv.balarm

import android.content.Context
import android.content.Intent
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.feature.ringing.RingingActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Экраны приложения для уведомлений и `AlarmClockInfo` (ADR-007 §9): `:core:alarm` не знает feature-модулей. */
class AppUiIntents @Inject constructor(@ApplicationContext private val context: Context) : AlarmUiIntents {
    override fun ringingScreen(): Intent =
        Intent(context, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun alarmList(): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
