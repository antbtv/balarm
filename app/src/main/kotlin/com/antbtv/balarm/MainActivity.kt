package com.antbtv.balarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.alarm.SafeRescheduler
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.di.ApplicationScope
import com.antbtv.balarm.ui.BalarmApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val DarkBars = SystemBarStyle.dark(Color.TRANSPARENT)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var rescheduler: SafeRescheduler

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject lateinit var ringing: RingingController

    @Inject lateinit var uiIntents: AlarmUiIntents

    // До онбординга M3 — временный запрос (ADR-007 §8): без него на звонке нет экрана, только звук.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема пока всегда тёмная (feature.lightTheme=false): светлые иконки системных баров.
        // При включении светлой темы (M7) стиль выбирается по теме приложения, а не системы.
        enableEdgeToEdge(statusBarStyle = DarkBars, navigationBarStyle = DarkBars)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // ADR-005 §2: страховка после force-stop/восстановления — идемпотентно, переживает закрытие экрана.
            appScope.launch { rescheduler.reschedule(RescheduleReason.APP_LAUNCH) }
            requestNotificationsOnce()
        }
        setContent {
            BalarmTheme {
                BalarmApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ringingRedirect(ringing.state.value, uiIntents)?.let(::startActivity)
    }

    private fun requestNotificationsOnce() {
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Без уведомлений (или после Home) к звонку не вернуться иначе — открытое во время звонка приложение
 * ведёт на экран звонка. `Ringing` держит только живой `RingingService`, поэтому зацикливания нет.
 */
internal fun ringingRedirect(state: RingingState, uiIntents: AlarmUiIntents): Intent? =
    if (state is RingingState.Ringing) uiIntents.ringingScreen() else null
