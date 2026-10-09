package com.antbtv.balarm.feature.alarmedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.antbtv.balarm.core.designsystem.component.SettingRow
import com.antbtv.balarm.core.designsystem.component.SingleChoiceDialog
import com.antbtv.balarm.core.designsystem.component.SliderRow
import com.antbtv.balarm.core.designsystem.component.SwitchRow
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.format.titleRes
import com.antbtv.balarm.core.model.SoundSettings
import java.time.Duration
import java.util.Locale
import kotlin.math.roundToInt

/** Делений между 10 % и 100 % с шагом 10: 20, 30 … 90. */
private val VOLUME_STEPS = (SoundSettings.MAX_VOLUME - SoundSettings.MIN_VOLUME) / SoundSettings.VOLUME_STEP - 1

private val VOLUME_RANGE = SoundSettings.MIN_VOLUME.toFloat()..SoundSettings.MAX_VOLUME.toFloat()

/**
 * Секция «Звук» редактора (FR-EDIT-5, ADR-016 §7): мелодия (→ пикер), громкость 10–100 % с превью, нарастание,
 * вибрация. Показывается только при `feature.alarmSound`; решение — у вызывающего ([AlarmEditUiState.soundVisible]).
 *
 * Удалённая своя мелодия — значение «Мелодия удалена» (цветом `primary` и словами, не только цветом) и подсказка
 * выбрать другую; пока библиотека грузится, значение не показывается.
 */
@Composable
internal fun SoundSection(
    sound: SoundSettings,
    soundName: SoundName,
    vibrate: Boolean,
    locale: Locale,
    enabled: Boolean,
    onEvent: (AlarmEditEvent) -> Unit,
) {
    val resources = LocalResources.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    // Слайдер зовёт колбэк на каждом кадре перетаскивания — лямбда без пересоздания.
    val onVolumeChange = remember {
        { value: Float -> currentOnEvent(AlarmEditEvent.VolumeChanged(value.roundToInt())) }
    }
    val volume = sound.volumePercent
    val fadeIn = sound.fadeIn
    val fadeInShort = remember(fadeIn, resources, locale) { fadeInText(fadeIn, resources, locale, wide = false) }
    val fadeInWide = remember(fadeIn, resources, locale) { fadeInText(fadeIn, resources, locale, wide = true) }
    val fadeInTitle = stringResource(R.string.alarm_edit_fade_in)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding)
            .testTag(AlarmEditTestTags.SOUND_SECTION),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        Column {
            SoundRow(soundName = soundName, enabled = enabled, onClick = { onEvent(AlarmEditEvent.PickSound) })
            SliderRow(
                title = stringResource(R.string.alarm_edit_volume),
                valueText = remember(volume, locale) { volumeText(volume, locale) },
                valueDescription = remember(volume, locale) { volumeDescription(volume, locale) },
                value = volume.toFloat(),
                onValueChange = onVolumeChange,
                valueRange = VOLUME_RANGE,
                steps = VOLUME_STEPS,
                enabled = enabled,
                modifier = Modifier.testTag(AlarmEditTestTags.VOLUME),
            )
            SettingRow(
                title = fadeInTitle,
                value = fadeInShort,
                onClick = { onEvent(AlarmEditEvent.ShowFadeInDialog) },
                onClickLabel = stringResource(R.string.alarm_edit_action_change),
                contentDescription = stringResource(R.string.alarm_edit_row_description, fadeInTitle, fadeInWide),
                enabled = enabled,
                modifier = Modifier.testTag(AlarmEditTestTags.FADE_IN),
            )
            SwitchRow(
                title = stringResource(R.string.alarm_edit_vibrate),
                checked = vibrate,
                onCheckedChange = { onEvent(AlarmEditEvent.VibrateChanged(it)) },
                enabled = enabled,
                modifier = Modifier.testTag(AlarmEditTestTags.VIBRATE),
            )
        }
    }
}

@Composable
private fun SoundRow(soundName: SoundName, enabled: Boolean, onClick: () -> Unit) {
    val title = stringResource(R.string.alarm_edit_sound)
    val value = when (soundName) {
        is SoundName.Builtin -> stringResource(soundName.sound.titleRes())
        is SoundName.Custom -> soundName.title
        SoundName.Missing -> stringResource(R.string.alarm_edit_sound_missing)
        SoundName.Pending -> null
    }
    val missing = soundName == SoundName.Missing
    SettingRow(
        title = title,
        value = value,
        onClick = onClick,
        onClickLabel = stringResource(R.string.alarm_edit_action_choose),
        contentDescription = value?.let { stringResource(R.string.alarm_edit_row_description, title, it) },
        enabled = enabled,
        valueColor = if (missing) BalarmTheme.colors.primary else Color.Unspecified,
        modifier = Modifier.testTag(AlarmEditTestTags.SOUND),
    )
    if (missing) {
        Text(
            text = stringResource(R.string.alarm_edit_sound_missing_hint),
            style = BalarmTheme.typography.caption,
            color = BalarmTheme.colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BalarmDimens.CardPadding,
                    end = BalarmDimens.CardPadding,
                    bottom = BalarmDimens.SpacingSmall,
                )
                .testTag(AlarmEditTestTags.SOUND_MISSING_HINT),
        )
    }
}

/** Выбор нарастания: в диалоге — полные «15 секунд», «1 минута». Выбор закрывает диалог редуктор. */
@Composable
internal fun FadeInDialog(current: Duration, locale: Locale, onEvent: (AlarmEditEvent) -> Unit) {
    val resources = LocalResources.current
    val options = SoundSettings.FADE_IN_OPTIONS
    val labels = remember(resources, locale) {
        options.map { fadeInText(it, resources, locale, wide = !it.isZero) }
    }
    SingleChoiceDialog(
        title = stringResource(R.string.alarm_edit_fade_in_title),
        options = labels,
        selectedIndex = options.indexOf(current),
        onSelect = { onEvent(AlarmEditEvent.FadeInSelected(options[it])) },
        onDismiss = { onEvent(AlarmEditEvent.DialogDismissed) },
        dismissText = stringResource(R.string.alarm_edit_cancel),
    )
}
