package com.antbtv.balarm.debug

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.antbtv.balarm.R
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.model.feature.Feature
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug-only экран переключения feature flags (FR-FLAG-5).
 * `adb shell am start -n com.antbtv.balarm/.debug.FeatureFlagsActivity`.
 * Изменения применяются к экранам, открытым после переключения (или после перезапуска приложения).
 */
@AndroidEntryPoint
class FeatureFlagsActivity : ComponentActivity() {

    @Inject lateinit var flags: DebugFeatureFlagProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = DarkBars, navigationBarStyle = DarkBars)
        super.onCreate(savedInstanceState)
        setContent {
            BalarmTheme {
                FeatureFlagsScreen(flags)
            }
        }
    }
}

private val DarkBars = SystemBarStyle.dark(Color.TRANSPARENT)

@Composable
private fun FeatureFlagsScreen(flags: DebugFeatureFlagProvider) {
    val state =
        remember {
            mutableStateMapOf<Feature, Boolean>().apply { Feature.entries.forEach { put(it, flags.isEnabled(it)) } }
        }
    Surface(modifier = Modifier.fillMaxSize(), color = BalarmTheme.colors.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            Text(text = stringResource(R.string.debug_flags_title), style = BalarmTheme.typography.title)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(Feature.entries, key = { it.key }) { feature ->
                    FlagRow(
                        feature = feature,
                        enabled = state.getValue(feature),
                        overridden = state.getValue(feature) != feature.defaultEnabled,
                        onToggle = { value ->
                            flags.setOverride(feature, value)
                            state[feature] = value
                        },
                    )
                }
            }
            Button(
                onClick = {
                    flags.resetAll()
                    Feature.entries.forEach { state[it] = it.defaultEnabled }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("debug_flags_reset"),
            ) {
                Text(stringResource(R.string.debug_flags_reset))
            }
        }
    }
}

@Composable
private fun FlagRow(feature: Feature, enabled: Boolean, overridden: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BalarmDimens.CardGap / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = feature.key, style = BalarmTheme.typography.body)
            if (overridden) {
                Text(
                    text = stringResource(R.string.debug_flags_overridden, feature.defaultEnabled.toString()),
                    style = BalarmTheme.typography.caption,
                    color = BalarmTheme.colors.warning,
                )
            }
        }
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("debug_flag_${feature.key}"))
    }
}
