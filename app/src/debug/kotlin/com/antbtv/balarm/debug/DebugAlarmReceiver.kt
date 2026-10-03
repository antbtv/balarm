package com.antbtv.balarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antbtv.balarm.core.domain.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug-хелпер сценариев надёжности. Только debug-сборка (в release его нет — `check-permissions.sh`).
 * ```
 * adb shell am broadcast -n com.antbtv.balarm/.debug.DebugAlarmReceiver -a com.antbtv.balarm.debug.SCHEDULE_IN \
 *     --ei minutes 2 [--ei seconds 30] [--es label Work] [--es days MON,TUE]
 * ```
 * Действия: SCHEDULE_IN, LIST, DISMISS, SNOOZE, RESCHEDULE_ALL, CLEAR_ALL, CRASH.
 */
@AndroidEntryPoint
class DebugAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var commands: DebugAlarmCommands

    @Inject @field:ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix(ACTION_PREFIX) ?: return
        when (action) {
            DISMISS -> commands.dismiss()

            SNOOZE -> commands.snooze()

            CRASH -> commands.crash()

            SCHEDULE_IN, LIST, RESCHEDULE_ALL, CLEAR_ALL -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        when (action) {
                            SCHEDULE_IN -> commands.scheduleIn(
                                delay = Duration.ofMinutes(intent.getIntExtra("minutes", 0).toLong())
                                    .plusSeconds(intent.getIntExtra("seconds", 0).toLong()),
                                label = intent.getStringExtra("label").orEmpty(),
                                days = DebugAlarmCommands.parseDays(intent.getStringExtra("days")),
                            )

                            LIST -> commands.list()

                            RESCHEDULE_ALL -> commands.rescheduleAll()

                            CLEAR_ALL -> commands.clearAll()
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val ACTION_PREFIX = "com.antbtv.balarm.debug."
        const val SCHEDULE_IN = "SCHEDULE_IN"
        const val LIST = "LIST"
        const val DISMISS = "DISMISS"
        const val SNOOZE = "SNOOZE"
        const val RESCHEDULE_ALL = "RESCHEDULE_ALL"
        const val CLEAR_ALL = "CLEAR_ALL"
        const val CRASH = "CRASH"
    }
}
