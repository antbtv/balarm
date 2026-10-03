package com.antbtv.balarm.core.alarm.ring

import android.content.Context
import com.antbtv.balarm.core.alarm.AlarmIntents
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Мост UI ↔ `RingingService` (ADR-007 §3): состояние пишет только сервис, команды уходят ему интентами —
 * звук не зависит от жизни экрана.
 */
@Singleton
class RingingControllerImpl @Inject constructor(@ApplicationContext private val context: Context) :
    RingingController {

    private val mutableState = MutableStateFlow<RingingState>(RingingState.Idle)
    override val state: StateFlow<RingingState> = mutableState.asStateFlow()

    internal fun publish(state: RingingState) {
        mutableState.value = state
    }

    override fun dismiss() = send(AlarmIntents.ACTION_DISMISS)

    override fun snooze() = send(AlarmIntents.ACTION_SNOOZE)

    private fun send(action: String) {
        val ringing = state.value as? RingingState.Ringing ?: return
        // Сервис уже в foreground — обычный startService разрешён и из фона. Гонка с его остановкой
        // (BackgroundServiceStartNotAllowedException) означает, что звонить уже нечему.
        try {
            context.startService(AlarmIntents.command(context, action, ringing.alarm.id))
        } catch (_: IllegalStateException) {
            mutableState.value = RingingState.Idle
        }
    }
}
