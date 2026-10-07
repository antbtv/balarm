package com.antbtv.balarm.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.health.HealthItem

// Превью шагов онбординга: тёмная тема (светлая — бэклог, PRD §2; одно светлое — контроль), 360dp, fontScale 2, RU.

private fun previewState(
    step: OnboardingStep,
    fixItem: HealthItem,
    canPostpone: Boolean = false,
    warnOnSkip: Boolean = false,
    batteryRestricted: Boolean = false,
) = OnboardingUiState(
    loading = false,
    step = step,
    stepNumber = step.ordinal + 1,
    fixItem = fixItem,
    batteryRestricted = batteryRestricted,
    canPostpone = canPostpone,
    warnOnSkip = warnOnSkip,
)

private val NotificationsFirst = previewState(OnboardingStep.NOTIFICATIONS, HealthItem.NOTIFICATIONS)
private val OverlayLater = previewState(OnboardingStep.OVERLAY, HealthItem.OVERLAY, canPostpone = true)
private val FullScreenWarn = previewState(
    OnboardingStep.FULL_SCREEN_INTENT,
    HealthItem.FULL_SCREEN_INTENT,
    canPostpone = true,
    warnOnSkip = true,
)
private val BatteryRestricted = previewState(
    OnboardingStep.BATTERY,
    HealthItem.BACKGROUND_RESTRICTION,
    batteryRestricted = true,
)
private val Oem = previewState(OnboardingStep.OEM_BACKGROUND, HealthItem.OEM_BACKGROUND, canPostpone = true)
private val Dnd = previewState(OnboardingStep.DO_NOT_DISTURB, HealthItem.DO_NOT_DISTURB, canPostpone = true)

@Composable
private fun Onboarding(state: OnboardingUiState) {
    OnboardingScreen(state = state, onEvent = {})
}

@Preview(name = "Onboarding — notifications (critical, first)", widthDp = 360, heightDp = 640)
@Composable
private fun NotificationsPreview() {
    BalarmTheme { Onboarding(NotificationsFirst) }
}

@Preview(
    name = "Onboarding — notifications, RU, fontScale 2",
    widthDp = 360,
    heightDp = 640,
    fontScale = 2f,
    locale = "ru",
)
@Composable
private fun NotificationsLargeFontPreview() {
    BalarmTheme { Onboarding(NotificationsFirst) }
}

@Preview(name = "Onboarding — overlay (recommended, Later), RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun OverlayPreview() {
    BalarmTheme { Onboarding(OverlayLater) }
}

@Preview(name = "Onboarding — full screen after attempt (warning)", widthDp = 360, heightDp = 640)
@Composable
private fun FullScreenWarnPreview() {
    BalarmTheme { Onboarding(FullScreenWarn) }
}

@Preview(
    name = "Onboarding — full screen warning, RU, fontScale 2",
    widthDp = 360,
    heightDp = 640,
    fontScale = 2f,
    locale = "ru",
)
@Composable
private fun FullScreenWarnLargeFontPreview() {
    BalarmTheme { Onboarding(FullScreenWarn) }
}

@Preview(name = "Onboarding — battery restricted, RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun BatteryRestrictedPreview() {
    BalarmTheme { Onboarding(BatteryRestricted) }
}

@Preview(name = "Onboarding — OEM", widthDp = 360, heightDp = 900)
@Composable
private fun OemPreview() {
    BalarmTheme { Onboarding(Oem) }
}

@Preview(name = "Onboarding — OEM, RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun OemLargeFontPreview() {
    BalarmTheme { Onboarding(Oem) }
}

@Preview(name = "Onboarding — DND (info)", widthDp = 360, heightDp = 640)
@Composable
private fun DndPreview() {
    BalarmTheme { Onboarding(Dnd) }
}

@Preview(name = "Onboarding — loading (blank)", widthDp = 360, heightDp = 640)
@Composable
private fun LoadingPreview() {
    BalarmTheme { Onboarding(OnboardingUiState()) }
}

@Preview(name = "Onboarding — light", widthDp = 360, heightDp = 640)
@Composable
private fun LightPreview() {
    BalarmTheme(darkTheme = false) { Onboarding(FullScreenWarn) }
}
