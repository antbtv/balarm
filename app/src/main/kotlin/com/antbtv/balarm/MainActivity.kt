package com.antbtv.balarm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.alarm.SafeRescheduler
import com.antbtv.balarm.core.alarm.sound.AlarmVolumeController
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.di.ApplicationScope
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.ui.BalarmApp
import dagger.hilt.android.AndroidEntryPoint
import java.util.Optional
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

    @Inject lateinit var volume: AlarmVolumeController

    @Inject lateinit var sounds: SoundRepository

    @Inject lateinit var uiIntents: AlarmUiIntents

    @Inject lateinit var debugTools: Optional<DebugTools>

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема всегда тёмная (светлая — в бэклоге, PRD §2): светлые иконки системных баров.
        // При появлении светлой темы стиль выбирается по теме приложения, а не системы.
        enableEdgeToEdge(statusBarStyle = DarkBars, navigationBarStyle = DarkBars)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // ADR-005 §2: страховка после force-stop/восстановления — идемпотентно, переживает закрытие экрана.
            appScope.launch { rescheduler.reschedule(RescheduleReason.APP_LAUNCH) }
            // Громкость, оставшаяся после падения процесса посреди звонка (ADR-017 §2), и сироты в библиотеке мелодий.
            volume.restorePendingIfIdle()
            appScope.launch { runCatching { sounds.cleanUp() } }
        }
        // Разрешения запрашивает онбординг (ADR-013); временный запрос POST_NOTIFICATIONS из M1 удалён.
        val openDebugFlags: (() -> Unit)? =
            debugTools.orElse(null)?.let { tools -> { startActivity(tools.featureFlagsIntent()) } }
        setContent {
            val showOnboarding by appViewModel.showOnboarding.collectAsStateWithLifecycle()
            BalarmTheme {
                BalarmApp(showOnboarding = showOnboarding, onOpenDebugFlags = openDebugFlags)
            }
        }
        keepSplashUntilReady()
    }

    /**
     * Системный splash держится, пока не прочитан `SetupState` (ADR-013 §2): первый кадр — сразу нужный экран,
     * без мигания списка перед онбордингом. Платформенный приём вместо `core-splashscreen`.
     */
    private fun keepSplashUntilReady() {
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = appViewModel.showOnboarding.value != null
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        ringingRedirect(ringing.state.value, uiIntents)?.let(::startActivity)
    }
}

/**
 * Без уведомлений (или после Home) к звонку не вернуться иначе — открытое во время звонка приложение
 * ведёт на экран звонка. `Ringing` держит только живой `RingingService`, поэтому зацикливания нет.
 */
internal fun ringingRedirect(state: RingingState, uiIntents: AlarmUiIntents): Intent? =
    if (state is RingingState.Ringing) uiIntents.ringingScreen() else null
