package com.antbtv.balarm.feature.ringing

import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.feature.ringing.ui.RingingPhase
import com.antbtv.balarm.feature.ringing.ui.RingingRoute
import com.antbtv.balarm.feature.ringing.ui.RingingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val DarkBars = SystemBarStyle.dark(Color.TRANSPARENT)

/**
 * Экран звонка (ADR-007 §10). Поверх экрана блокировки, включает экран, не гаснет.
 * Системные бары видимы и прозрачны (edge-to-edge, отступы — `safeDrawingPadding` в `RingingScreen`): режим погружения
 * выводил подсказку «Viewing full screen» поверх кнопок, а на надёжность звонка он не влияет (M2-T07).
 * Звук живёт в сервисе: закрытие или падение Activity звонок не останавливает.
 *
 * FR-RING-4: Back и кнопки громкости поглощаются — уйти с экрана можно только командой
 * «Отключить»/«Отложить» (или Home, тогда звонок продолжается). Как только контроллер сообщает о конце
 * звонка ([RingingPhase.FINISHED]), Activity закрывается вместе со своей задачей.
 *
 * Миссия (ADR-019): экран миссии — внутри этой же Activity; его Back ведёт на звонок (`BackHandler` в
 * `MissionHost`, приоритетнее [absorbBack]). Экран скрыт при включённом дисплее (Home, Recents) — у будильника
 * с миссией сервис сразу возвращает громкость и сам открывает экран снова (FR-RING-5, R17).
 */
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    private val viewModel: RingingViewModel by viewModels()

    private val absorbBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = DarkBars, navigationBarStyle = DarkBars)
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, absorbBack)

        // Не repeatOnLifecycle: закрыться нужно и в фоне (отключили из уведомления, пока экран свёрнут).
        lifecycleScope.launch {
            viewModel.uiState.first { it.phase == RingingPhase.FINISHED }
            finishAndRemoveTask()
        }

        setContent {
            BalarmTheme {
                RingingRoute(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        if (!isChangingConfigurations && !isFinishing && interactive) viewModel.onScreenPaused()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenShown()
    }

    override fun onStop() {
        super.onStop()
        // Поворот — не уход с экрана; выключение дисплея — тоже (звонок продолжится, экран — поверх блокировки).
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        if (!isChangingConfigurations && interactive) viewModel.onScreenHidden()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in AbsorbedKeys || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in AbsorbedKeys || super.onKeyUp(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in AbsorbedKeys || super.onKeyLongPress(keyCode, event)

    private companion object {
        /** Громкость не глушит и не отключает звонок (FR-RING-4); звук — на `STREAM_ALARM` в сервисе. */
        val AbsorbedKeys = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
        )
    }
}
