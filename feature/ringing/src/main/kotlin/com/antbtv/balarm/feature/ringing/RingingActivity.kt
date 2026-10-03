package com.antbtv.balarm.feature.ringing

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
 * Экран звонка (ADR-007 §10). Поверх экрана блокировки, включает экран, не гаснет, системные бары скрыты.
 * Звук живёт в сервисе: закрытие или падение Activity звонок не останавливает.
 *
 * FR-RING-4: Back и кнопки громкости поглощаются — уйти с экрана можно только командой
 * «Отключить»/«Отложить» (или Home, тогда звонок продолжается). Как только контроллер сообщает о конце
 * звонка ([RingingPhase.FINISHED]), Activity закрывается вместе со своей задачей.
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
        hideSystemBars()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in AbsorbedKeys || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in AbsorbedKeys || super.onKeyUp(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in AbsorbedKeys || super.onKeyLongPress(keyCode, event)

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        /** Громкость не глушит и не отключает звонок (FR-RING-4); звук — на `STREAM_ALARM` в сервисе. */
        val AbsorbedKeys = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
        )
    }
}
