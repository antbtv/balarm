package com.antbtv.balarm.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.R
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentLightPreview
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Раскладка шага онбординга (PRD §3.7, ADR-013): сверху индикатор «Шаг 2 из 7», в середине — прокручиваемые
 * иллюстрация, заголовок, «Зачем это нужно» и [extraContent]; снизу закреплены [PrimaryButton] «Разрешить»
 * и необязательная второстепенная кнопка «Позже» / «Продолжить без этого». Строки готовит вызывающий.
 *
 * Полноэкранная: фон `background`, сама обрабатывает `WindowInsets.safeDrawing` (оборачивать в `Scaffold`
 * с `innerPadding` не нужно). При fontScale 2 на 360dp текст прокручивается, кнопки остаются видимыми.
 *
 * TalkBack: индикатор — один узел «Шаг N из M», заголовок — heading, иллюстрация декоративная.
 *
 * @param illustration картинка шага, обычно [OnboardingIllustration]; должна быть декоративной (без описания).
 * @param why пояснение «зачем это нужно».
 * @param primaryLabel главное действие («Разрешить», «Открыть настройки»).
 * @param secondaryLabel «Позже» / «Продолжить без этого»; `null` — кнопки нет (критичный шаг до первой попытки).
 * @param currentStep номер текущего шага, с 1; приводится к `1..totalSteps`.
 * @param totalSteps число шагов (≥ 1).
 * @param extraContent дополнительное содержимое под пояснением (ссылка, чек-бокс «Я сделал», предупреждение).
 */
@Composable
fun OnboardingStepLayout(
    illustration: @Composable () -> Unit,
    title: String,
    why: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    val total = totalSteps.coerceAtLeast(1)
    val current = currentStep.coerceIn(1, total)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BalarmTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(OnboardingStepTestTags.LAYOUT),
    ) {
        StepIndicator(
            current = current,
            total = total,
            modifier = Modifier.padding(
                start = BalarmDimens.ScreenPadding,
                end = BalarmDimens.ScreenPadding,
                top = BalarmDimens.ScreenPadding,
            ),
        )
        StepBody(
            illustration = illustration,
            title = title,
            why = why,
            extraContent = extraContent,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        StepButtons(
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            secondaryLabel = secondaryLabel,
            onSecondary = onSecondary,
        )
    }
}

/** Прокручиваемая середина шага: иллюстрация, заголовок, пояснение, дополнительное содержимое. */
@Composable
private fun StepBody(
    illustration: @Composable () -> Unit,
    title: String,
    why: String,
    extraContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(BalarmDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardPadding),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { illustration() }
        Text(
            text = title,
            style = type.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OnboardingStepTestTags.TITLE)
                .semantics { heading() },
        )
        Text(
            text = why,
            style = type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        extraContent()
    }
}

/** Закреплённые снизу кнопки: главная `primary` и необязательная текстовая «Позже». */
@Composable
private fun StepButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = BalarmDimens.ScreenPadding,
                end = BalarmDimens.ScreenPadding,
                bottom = BalarmDimens.ScreenPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        PrimaryButton(
            text = primaryLabel,
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OnboardingStepTestTags.PRIMARY),
        )
        if (secondaryLabel != null) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BalarmDimens.ButtonHeight)
                    .testTag(OnboardingStepTestTags.SECONDARY),
                shape = BalarmShapes.Button,
                colors = ButtonDefaults.textButtonColors(contentColor = BalarmTheme.colors.textPrimary),
            ) {
                Text(text = secondaryLabel, style = BalarmTheme.typography.body, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Индикатор шагов: сегменты-«таблетки», пройденные и текущий — `primary`, текущий вдвое шире (не только цвет),
 * под ними подпись «Шаг N из M». Для TalkBack — один узел с этой подписью.
 */
@Composable
private fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val label = stringResource(R.string.designsystem_onboarding_step, current, total)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(OnboardingStepTestTags.PROGRESS)
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny)) {
            for (step in 1..total) {
                Box(
                    modifier = Modifier
                        .size(
                            width = if (step == current) {
                                BalarmDimens.StepIndicatorSegmentCurrent
                            } else {
                                BalarmDimens.StepIndicatorSegment
                            },
                            height = BalarmDimens.StepIndicatorHeight,
                        )
                        .background(
                            color = if (step <= current) colors.primary else colors.surfaceVariant,
                            shape = BalarmShapes.Pill,
                        ),
                )
            }
        }
        Text(text = label, style = BalarmTheme.typography.caption, color = colors.textSecondary)
    }
}

/**
 * Стандартная иллюстрация шага онбординга: круг `surfaceVariant` с крупной иконкой `primary`. Декоративная.
 *
 * @param icon векторная иконка (`BalarmIcons` или своя CC0 в feature-модуле).
 */
@Composable
fun OnboardingIllustration(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BalarmDimens.OnboardingIllustration)
            .background(BalarmTheme.colors.surfaceVariant, BalarmShapes.Circle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = BalarmTheme.colors.primary,
            modifier = Modifier.size(BalarmDimens.OnboardingIllustrationIcon),
        )
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun OnboardingStepLayoutPreview() {
    BalarmTheme {
        OnboardingStepLayout(
            illustration = { OnboardingIllustration(icon = BalarmIcons.Alarm) },
            title = "Allow notifications",
            why = "Without notifications the ringing screen cannot appear over the lock screen.",
            primaryLabel = "Allow",
            onPrimary = {},
            secondaryLabel = "Later",
            onSecondary = {},
            currentStep = 1,
            totalSteps = 7,
        )
    }
}

@BalarmComponentPreviews
@Composable
private fun OnboardingStepLayoutRuExtraPreview() {
    BalarmTheme {
        OnboardingStepLayout(
            illustration = { OnboardingIllustration(icon = BalarmIcons.Settings) },
            title = "Автозапуск и фоновая работа",
            why = "На некоторых телефонах (Xiaomi, Huawei, Oppo, Vivo) фоновую работу разрешают отдельно.",
            primaryLabel = "Открыть настройки",
            onPrimary = {},
            secondaryLabel = "Продолжить без этого",
            onSecondary = {},
            currentStep = 6,
            totalSteps = 7,
        ) {
            Text(
                text = "dontkillmyapp.com",
                style = BalarmTheme.typography.body,
                color = BalarmTheme.colors.primary,
            )
        }
    }
}

@BalarmComponentLightPreview
@Composable
private fun OnboardingStepLayoutLightPreview() {
    BalarmTheme(darkTheme = false) {
        OnboardingStepLayout(
            illustration = { OnboardingIllustration(icon = BalarmIcons.Alarm) },
            title = "Full-screen notifications",
            why = "Lets the ringing screen open on a locked phone.",
            primaryLabel = "Allow",
            onPrimary = {},
            secondaryLabel = null,
            onSecondary = {},
            currentStep = 3,
            totalSteps = 7,
        )
    }
}
