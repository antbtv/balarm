package com.antbtv.balarm.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthStatus

// Превью экранов настроек: тёмная тема (светлая — бэклог, PRD §2; одно светлое — контроль), 360dp, fontScale 2,
// RU. Отступы «как у телефона» (статус-бар и панель жестов 24dp) — в превью баров нет.

private val PreviewInsets = WindowInsets(top = 24.dp, bottom = 24.dp)

private val PreviewHealth = HealthUiState(
    loading = false,
    items = HealthItem.entries.map { item ->
        val status = when (item) {
            HealthItem.NOTIFICATIONS, HealthItem.OVERLAY, HealthItem.SCHEDULING -> HealthStatus.PROBLEM
            HealthItem.OEM_BACKGROUND -> HealthStatus.UNCONFIRMED
            else -> HealthStatus.OK
        }
        HealthItemUi(item, status)
    },
    unscheduledAlarms = 2,
)

private val PreviewHealthOk = HealthUiState(
    loading = false,
    items = HealthItem.entries.map { HealthItemUi(it, HealthStatus.OK) },
)

@Composable
private fun Settings(state: SettingsUiState) {
    SettingsScreen(state = state, onOpenHealth = {}, onOpenAbout = {}, windowInsets = PreviewInsets)
}

@Composable
private fun Health(state: HealthUiState) {
    HealthScreen(state = state, onEvent = {}, onClose = {}, windowInsets = PreviewInsets)
}

@Composable
private fun About(debug: Boolean = true) {
    AboutScreen(
        versionName = "0.3.0",
        onClose = {},
        onOpenDebugFlags = if (debug) ({}) else null,
        windowInsets = PreviewInsets,
    )
}

// --- Настройки ---

@Preview(name = "Settings — problems, 360dp", widthDp = 360, heightDp = 640)
@Composable
private fun SettingsProblemsPreview() {
    BalarmTheme { Settings(SettingsUiState(loading = false, problems = 2)) }
}

@Preview(name = "Settings — OK, RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun SettingsOkRuPreview() {
    BalarmTheme { Settings(SettingsUiState(loading = false, problems = 0)) }
}

@Preview(name = "Settings — 5 problems, RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun SettingsLargeFontPreview() {
    BalarmTheme { Settings(SettingsUiState(loading = false, problems = 5)) }
}

@Preview(name = "Settings — loading", widthDp = 360, heightDp = 640)
@Composable
private fun SettingsLoadingPreview() {
    BalarmTheme { Settings(SettingsUiState()) }
}

@Preview(name = "Settings — light", widthDp = 360, heightDp = 640)
@Composable
private fun SettingsLightPreview() {
    BalarmTheme(darkTheme = false) { Settings(SettingsUiState(loading = false, problems = 1)) }
}

// --- Здоровье ---

@Preview(name = "Health — problems, 360dp", widthDp = 360, heightDp = 1600)
@Composable
private fun HealthPreview() {
    BalarmTheme { Health(PreviewHealth) }
}

@Preview(name = "Health — retrying, RU", widthDp = 360, heightDp = 1600, locale = "ru")
@Composable
private fun HealthRetryingRuPreview() {
    BalarmTheme { Health(PreviewHealth.copy(retrying = true)) }
}

@Preview(name = "Health — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun HealthLargeFontPreview() {
    BalarmTheme { Health(PreviewHealth) }
}

@Preview(name = "Health — all OK", widthDp = 360, heightDp = 1400)
@Composable
private fun HealthOkPreview() {
    BalarmTheme { Health(PreviewHealthOk) }
}

@Preview(name = "Health — loading", widthDp = 360, heightDp = 640)
@Composable
private fun HealthLoadingPreview() {
    BalarmTheme { Health(HealthUiState()) }
}

@Preview(name = "Health — light", widthDp = 360, heightDp = 1600)
@Composable
private fun HealthLightPreview() {
    BalarmTheme(darkTheme = false) { Health(PreviewHealth) }
}

// --- О приложении ---

@Preview(name = "About — 360dp", widthDp = 360, heightDp = 640)
@Composable
private fun AboutPreview() {
    BalarmTheme { About() }
}

@Preview(name = "About — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun AboutLargeFontPreview() {
    BalarmTheme { About(debug = false) }
}
