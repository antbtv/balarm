package com.antbtv.balarm.feature.sounds

import android.content.ActivityNotFoundException
import android.content.res.Resources
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.format.titleRes
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSound
import java.time.Duration
import java.util.Locale

private const val BYTES_IN_MB = 1024L * 1024
private const val SECONDS_IN_MINUTE = 60L
private const val MINUTES_IN_HOUR = 60L

/**
 * SAF-пикер аудио (ADR-016 §4): `ACTION_OPEN_DOCUMENT` с [IMPORT_MIME_TYPES]; persistable-разрешение не берём —
 * файл копируется сразу. Возвращает «открыть пикер»; [onPicked] получает `content://…`, отмена — ничего.
 * Нет приложения-пикера документов — [onUnavailable].
 */
@Composable
internal fun rememberImportLauncher(onPicked: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { currentOnPicked(it.toString()) }
    }
    return remember(launcher) {
        {
            try {
                launcher.launch(IMPORT_MIME_TYPES)
            } catch (_: ActivityNotFoundException) {
                currentOnUnavailable()
            }
        }
    }
}

/**
 * Показывает [message] снекбаром; когда он скрылся — [onShown] (сообщение снимается из состояния). Новое сообщение
 * сменяет текущее сразу: эффект перезапускается, отменённый `showSnackbar` убирает старый снекбар.
 */
@Composable
internal fun SoundMessageEffect(message: SoundMessage?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val resources = LocalResources.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message, hostState, resources) {
        if (message == null) return@LaunchedEffect
        hostState.showSnackbar(soundMessageText(resources, message), duration = SnackbarDuration.Short)
        currentOnShown()
    }
}

/** Текст снекбара; язык — из [resources]. Название «Классика» — из `:core:format`, как в редакторе. */
internal fun soundMessageText(resources: Resources, message: SoundMessage): String = when (message) {
    is SoundMessage.Imported -> resources.getString(R.string.sounds_import_done, message.title)

    is SoundMessage.TooLarge -> resources.getString(R.string.sounds_import_too_large, limitMb(message.limitBytes))

    SoundMessage.Unsupported -> resources.getString(R.string.sounds_import_unsupported)

    SoundMessage.NoSpace -> resources.getString(R.string.sounds_import_no_space)

    SoundMessage.ImportFailed -> resources.getString(R.string.sounds_import_failed)

    is SoundMessage.Deleted -> if (message.switchedAlarms > 0) {
        resources.getQuantityString(
            R.plurals.sounds_deleted_switched,
            message.switchedAlarms,
            message.title,
            message.switchedAlarms,
            resources.getString(BuiltinSound.DEFAULT.titleRes()),
        )
    } else {
        resources.getString(R.string.sounds_deleted, message.title)
    }

    SoundMessage.DeleteFailed -> resources.getString(R.string.sounds_delete_failed)

    SoundMessage.RenameFailed -> resources.getString(R.string.sounds_rename_failed)
}

/** Лимит импорта в мегабайтах для текстов («Файл больше 20 МБ»). */
internal fun limitMb(limitBytes: Long): Int = (limitBytes / BYTES_IN_MB).toInt()

/** «0:32», «12:05», «1:02:03» — цифры одинаковы во всех локалях приложения (RU/EN). */
internal fun durationText(duration: Duration): String {
    val total = duration.seconds
    val hours = total / (SECONDS_IN_MINUTE * MINUTES_IN_HOUR)
    val minutes = total / SECONDS_IN_MINUTE % MINUTES_IN_HOUR
    val seconds = total % SECONDS_IN_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Для TalkBack: «1 минута 5 секунд» вместо «1:05». Часы — в минутах: мелодии длиннее часа редки. */
internal fun durationDescription(resources: Resources, duration: Duration): String {
    val minutes = (duration.seconds / SECONDS_IN_MINUTE).toInt()
    val seconds = (duration.seconds % SECONDS_IN_MINUTE).toInt()
    val secondsText = resources.getQuantityString(R.plurals.sounds_duration_seconds, seconds, seconds)
    if (minutes == 0) return secondsText
    val minutesText = resources.getQuantityString(R.plurals.sounds_duration_minutes, minutes, minutes)
    return if (seconds == 0) minutesText else "$minutesText $secondsText"
}

/** Подпись своей мелодии: «0:32 · 1.2 MB» и её форма для TalkBack. */
internal data class SoundDetails(val text: String, val description: String)

@Composable
internal fun rememberSoundDetails(sound: CustomSound): SoundDetails {
    val context = LocalContext.current
    val resources = LocalResources.current
    return remember(sound.duration, sound.sizeBytes, context, resources) {
        val size = Formatter.formatShortFileSize(context, sound.sizeBytes)
        SoundDetails(
            text = resources.getString(R.string.sounds_details, durationText(sound.duration), size),
            description = resources.getString(
                R.string.sounds_details,
                durationDescription(resources, sound.duration),
                size,
            ),
        )
    }
}

@Composable
internal fun SoundDetailsText(details: SoundDetails, modifier: Modifier = Modifier) {
    Text(
        text = details.text,
        style = BalarmTheme.typography.caption,
        color = BalarmTheme.colors.textSecondary,
        modifier = modifier.semantics { contentDescription = details.description },
    )
}

/** Импорт идёт: полоска прогресса под заголовком, TalkBack читает «Добавляем мелодию…». */
@Composable
internal fun ImportProgress(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sounds_importing)
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .testTag(SoundsTestTags.IMPORT_PROGRESS),
        color = BalarmTheme.colors.primary,
        trackColor = BalarmTheme.colors.surfaceVariant,
    )
}
