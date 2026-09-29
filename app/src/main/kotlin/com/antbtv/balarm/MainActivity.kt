package com.antbtv.balarm

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.ui.BalarmApp
import dagger.hilt.android.AndroidEntryPoint

private val DarkBars = SystemBarStyle.dark(Color.TRANSPARENT)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема пока всегда тёмная (feature.lightTheme=false): светлые иконки системных баров.
        // При включении светлой темы (M7) стиль выбирается по теме приложения, а не системы.
        enableEdgeToEdge(statusBarStyle = DarkBars, navigationBarStyle = DarkBars)
        super.onCreate(savedInstanceState)
        setContent {
            BalarmTheme {
                BalarmApp()
            }
        }
    }
}
