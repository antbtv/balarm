package com.antbtv.balarm.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.OnboardingIllustration
import com.antbtv.balarm.core.designsystem.component.OnboardingStepLayout
import com.antbtv.balarm.core.designsystem.component.SecondaryButton
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.permissions.rememberHealthFixLauncher
import com.antbtv.balarm.core.permissions.titleRes
import com.antbtv.balarm.core.permissions.whyRes
import kotlinx.coroutines.flow.Flow

/** Сайт с инструкциями по фоновой работе для телефонов разных производителей (ADR-013 §3). */
internal const val OEM_GUIDE_URL = "https://dontkillmyapp.com"

/**
 * Онбординг (PRD §3.7, ADR-013) с ViewModel. Статусы перечитываются на каждом `ON_RESUME` — возврат из системных
 * настроек сам переводит на следующий шаг; эффекты собирает один [OnboardingEffectsHandler].
 *
 * Полноэкранный, без нижней панели: `safeDrawing` обрабатывает раскладка шага, `:app` отступов не добавляет.
 * Системный Back экран не перехватывает (его обрабатывает навигация).
 *
 * @param onFinished вызывается один раз, когда шагов не осталось и завершение записано (или запись не удалась —
 * тогда онбординг покажется ещё раз при следующем запуске).
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingEffectsHandler(effects = viewModel.effects, onEvent = viewModel::onEvent, onFinished = onFinished)
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(OnboardingEvent.Resumed)
        onPauseOrDispose {}
    }
    OnboardingScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Единственный сборщик эффектов: `receiveAsFlow` делит элементы между сборщиками. `OpenFix` — системный запрос или
 * экран настроек (после возврата — [OnboardingEvent.Resumed]); `SaveFailed` — тост через `applicationContext`
 * (не блокирует и переживает уход с экрана).
 */
@Composable
private fun OnboardingEffectsHandler(
    effects: Flow<OnboardingEffect>,
    onEvent: (OnboardingEvent) -> Unit,
    onFinished: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val fix = rememberHealthFixLauncher { currentOnEvent(OnboardingEvent.Resumed) }
    val currentFix by rememberUpdatedState(fix)
    LaunchedEffect(effects, appContext) {
        effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.OpenFix -> currentFix(effect.item)

                OnboardingEffect.Finished -> currentOnFinished()

                OnboardingEffect.SaveFailed ->
                    Toast.makeText(appContext, R.string.onboarding_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Шаг онбординга: иллюстрация, заголовок и «зачем» пункта здоровья, «Разрешить»/«Открыть настройки»,
 * «Позже» (рекомендуемые) или «Продолжить без этого» с предупреждением (критичные после попытки).
 * Пока шаг не вычислен — пустой фон (без мигания первого шага перед завершением).
 */
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    val item = state.fixItem
    if (state.loading || step == null || item == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BalarmTheme.colors.background)
                .testTag(OnboardingTestTags.BLANK),
        )
        return
    }
    // Новый шаг — своя прокрутка и свой чек-бокс «Я сделал».
    key(step) {
        if (step == OnboardingStep.OEM_BACKGROUND) {
            OemStep(state = state, onEvent = onEvent, modifier = modifier)
        } else {
            StepContent(state = state, step = step, item = item, onEvent = onEvent, modifier = modifier)
        }
    }
}

@Composable
private fun StepContent(
    state: OnboardingUiState,
    step: OnboardingStep,
    item: HealthItem,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    primaryLabel: String = stringResource(item.primaryLabelRes),
    onPrimary: () -> Unit = { onEvent(OnboardingEvent.Primary) },
    primaryEnabled: Boolean = true,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    OnboardingStepLayout(
        illustration = {
            OnboardingIllustration(
                icon = step.illustration,
                modifier = Modifier.testTag(OnboardingTestTags.illustration(step)),
            )
        },
        title = stringResource(item.titleRes),
        why = stringResource(item.whyRes),
        primaryLabel = primaryLabel,
        onPrimary = onPrimary,
        primaryEnabled = primaryEnabled,
        secondaryLabel = postponeLabel(state),
        onSecondary = { onEvent(OnboardingEvent.Postpone) },
        warning = if (state.warnOnSkip) stringResource(R.string.onboarding_skip_warning) else null,
        currentStep = state.stepNumber,
        totalSteps = state.totalSteps,
        modifier = modifier,
        extraContent = extraContent,
    )
}

/** «Позже» — рекомендуемые и информационные; «Продолжить без этого» — критичные после попытки. */
@Composable
private fun postponeLabel(state: OnboardingUiState): String? = when {
    !state.canPostpone -> null
    state.warnOnSkip -> stringResource(R.string.onboarding_continue_without)
    else -> stringResource(R.string.onboarding_later)
}

/**
 * «Разрешить» — системный диалог прямо поверх шага (уведомления, исключение из оптимизации батареи);
 * «Открыть настройки» — экран настроек, где переключатель пользователь включает сам.
 */
@get:StringRes
internal val HealthItem.primaryLabelRes: Int
    get() = when (this) {
        HealthItem.NOTIFICATIONS, HealthItem.BATTERY_OPTIMIZATION -> R.string.onboarding_allow
        else -> R.string.onboarding_open_settings
    }

@get:DrawableRes
internal val OnboardingStep.illustration: Int
    get() = when (this) {
        OnboardingStep.NOTIFICATIONS -> R.drawable.ic_onboarding_bell
        OnboardingStep.EXACT_ALARMS -> BalarmIcons.Alarm
        OnboardingStep.FULL_SCREEN_INTENT -> R.drawable.ic_onboarding_full_screen
        OnboardingStep.OVERLAY -> R.drawable.ic_onboarding_overlay
        OnboardingStep.BATTERY -> R.drawable.ic_onboarding_battery
        OnboardingStep.OEM_BACKGROUND -> R.drawable.ic_onboarding_autostart
        OnboardingStep.DO_NOT_DISTURB -> R.drawable.ic_onboarding_dnd
    }

/**
 * Шаг OEM (ADR-013 §3): проверить программно нельзя. Пояснение, «Открыть настройки приложения»
 * ([OnboardingEvent.Primary] → App details), ссылка на dontkillmyapp.com и «Я сделал». Отметка только локальная —
 * шаг держится на экране; «Готово» (доступна после отметки) шлёт `OemConfirmed(true)`, без отметки выход — «Позже».
 */
@Composable
private fun OemStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    var done by rememberSaveable { mutableStateOf(false) }
    StepContent(
        state = state,
        step = OnboardingStep.OEM_BACKGROUND,
        item = HealthItem.OEM_BACKGROUND,
        onEvent = onEvent,
        modifier = modifier,
        primaryLabel = stringResource(R.string.onboarding_done),
        onPrimary = { if (done) onEvent(OnboardingEvent.OemConfirmed(confirmed = true)) },
        primaryEnabled = done,
    ) {
        OemExtras(
            done = done,
            onDoneChange = { done = it },
            onOpenSettings = { onEvent(OnboardingEvent.Primary) },
        )
    }
}

@Composable
private fun OemExtras(done: Boolean, onDoneChange: (Boolean) -> Unit, onOpenSettings: () -> Unit) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.onboarding_oem_hint),
        style = type.body,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(
        text = stringResource(R.string.onboarding_oem_open_app_settings),
        onClick = onOpenSettings,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.OEM_OPEN_SETTINGS),
    )
    Text(
        text = stringResource(R.string.onboarding_oem_guide),
        style = type.body.copy(textDecoration = TextDecoration.Underline),
        color = colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.MinTouch)
            .clickable(
                onClickLabel = stringResource(R.string.onboarding_oem_guide_action),
                role = Role.Button,
            ) { openOemGuide(context) }
            .padding(vertical = BalarmDimens.SpacingSmall)
            .testTag(OnboardingTestTags.OEM_GUIDE),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.MinTouch)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = onDoneChange)
            .testTag(OnboardingTestTags.OEM_CONFIRM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Флажок без своего обработчика: переключает вся строка (зона тапа — строка целиком).
        Checkbox(
            checked = done,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                uncheckedColor = colors.textSecondary,
                checkmarkColor = colors.onPrimary,
            ),
        )
        Text(
            text = stringResource(R.string.onboarding_oem_confirm),
            style = type.body,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = BalarmDimens.CardGap),
        )
    }
}

private fun openOemGuide(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, OEM_GUIDE_URL.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.onboarding_no_browser, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.onboarding_no_browser, Toast.LENGTH_SHORT).show()
    }
}
