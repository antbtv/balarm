package com.antbtv.balarm.feature.missions.api

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.antbtv.balarm.core.designsystem.component.SecondaryButton
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.alarm.MissionPolicy
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Сколько висит «Готово!» после последней задачи, прежде чем превью закроется само. */
internal val PREVIEW_DONE_DELAY: Duration = Duration.ofMillis(1_500)

/**
 * Превью миссии из редактора (FR-MIS-5, ADR-018 §7): та же миссия в том же [MissionFrame], но **без звука** —
 * ни `RingingController`, ни громкости, ни сессии звонка. Кнопка «Закрыть» — справа от прогресса; системный Back
 * обрабатывает навигация `:app` (снимает экран). Последняя решённая задача → «Готово!» и закрытие через
 * [PREVIEW_DONE_DELAY].
 *
 * Тип недоступен (флаг выключили, пока превью было в стеке, или тип неизвестен сборке) — экран сразу закрывается.
 */
@Composable
fun MissionPreviewRoute(spec: MissionSpec, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<MissionPreviewViewModel>()
    val definition = remember(viewModel, spec.type) { viewModel.definition(spec.type) }
    if (definition == null) {
        val currentOnClose by rememberUpdatedState(onClose)
        LaunchedEffect(Unit) { currentOnClose() }
        return
    }
    MissionPreviewScreen(
        definition = definition,
        config = spec.config,
        seed = viewModel.seed,
        onClose = onClose,
        modifier = modifier,
    )
}

/** Держит только seed (поворот не меняет задачи) и доступ к реестру; звука и звонка не знает. */
@HiltViewModel
internal class MissionPreviewViewModel @Inject constructor(private val registry: MissionRegistry) : ViewModel() {
    /** Случайный: каждое открытие превью — новые задачи. */
    val seed: Long = Random.nextLong()

    fun definition(type: MissionTypeId): MissionDefinition? =
        registry.definition(type)?.takeIf { registry.isAvailable(type) }
}

/**
 * Хост превью без ViewModel: прогресс, счётчик касаний и сигнал сброса — локальное состояние (`rememberSaveable`).
 * Бездействие [idleTimeout] сбрасывает ввод текущей задачи, как на звонке (FR-MIS-2), но без громкости; следующий
 * сброс — только после нового касания.
 */
@Composable
internal fun MissionPreviewScreen(
    definition: MissionDefinition,
    config: String,
    seed: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    idleTimeout: Duration = MissionPolicy.IDLE_TIMEOUT,
) {
    var done by rememberSaveable { mutableIntStateOf(0) }
    var total by rememberSaveable { mutableIntStateOf(0) }
    // Растёт на каждое касание: перезапускает таймер и полоску бездействия.
    var interactions by rememberSaveable { mutableIntStateOf(0) }
    var resetSignal by rememberSaveable { mutableIntStateOf(0) }
    var completed by rememberSaveable { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)

    // Один сброс на период бездействия; следующий отсчёт — с нового касания (ввод после сброса и так пуст).
    LaunchedEffect(interactions, completed) {
        if (completed) return@LaunchedEffect
        delay(idleTimeout.toMillis())
        resetSignal++
    }
    LaunchedEffect(completed) {
        if (!completed) return@LaunchedEffect
        delay(PREVIEW_DONE_DELAY.toMillis())
        currentOnClose()
    }
    val normalized = remember(definition, config) { definition.normalize(config) }
    val session = remember(seed, resetSignal) {
        MissionSession(
            seed = seed,
            isPreview = true,
            resetSignal = resetSignal,
            onInteraction = { interactions++ },
            onProgress = { solved, all ->
                done = solved
                total = all
            },
            onCompleted = { completed = true },
        )
    }
    MissionFrame(
        done = done,
        total = total,
        idleKey = interactions,
        idleTimeout = idleTimeout,
        modifier = modifier.testTag(MissionPreviewTestTags.ROOT),
        topEndAction = {
            SecondaryButton(
                text = stringResource(R.string.mission_preview_close),
                onClick = onClose,
                modifier = Modifier.testTag(MissionPreviewTestTags.CLOSE),
            )
        },
    ) {
        if (completed) {
            PreviewDone()
        } else {
            definition.Content(config = normalized, session = session, modifier = Modifier)
        }
    }
}

@Composable
private fun PreviewDone() {
    Text(
        text = stringResource(R.string.mission_preview_done),
        style = BalarmTheme.typography.timeLarge,
        color = BalarmTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .testTag(MissionPreviewTestTags.DONE)
            .semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/** Теги для Compose UI-тестов. */
object MissionPreviewTestTags {
    const val ROOT = "missionPreview"
    const val CLOSE = "missionPreviewClose"
    const val DONE = "missionPreviewDone"
}

// --- Превью ---

private const val PREVIEW_TOTAL = 3

/** Превью-only миссия: одна кнопка «решить». */
private object PreviewMission : MissionDefinition {
    override val type = MissionTypeId("preview")
    override val feature = Feature.MISSION_MATH
    override val titleRes = R.string.mission_preview_close

    override fun defaultConfig() = "{}"

    override fun normalize(config: String) = config

    override fun summary(context: Context, config: String) = "preview"

    @Composable
    override fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier) = Unit

    @Composable
    override fun Content(config: String, session: MissionSession, modifier: Modifier) {
        LaunchedEffect(session.seed) { session.onProgress(1, PREVIEW_TOTAL) }
        Column(modifier) {
            Text(text = "7 × 8", style = BalarmTheme.typography.timeHuge, color = BalarmTheme.colors.textPrimary)
            Button(onClick = session.onCompleted) { Text("Solve") }
        }
    }
}

@Preview(name = "Mission preview — dark, 360dp", widthDp = 360, heightDp = 720)
@Composable
private fun MissionPreviewDarkPreview() {
    BalarmTheme { MissionPreviewScreen(PreviewMission, "{}", seed = 1, onClose = {}) }
}

@Preview(name = "Mission preview — light", widthDp = 360, heightDp = 720)
@Composable
private fun MissionPreviewLightPreview() {
    BalarmTheme(darkTheme = false) { MissionPreviewScreen(PreviewMission, "{}", seed = 1, onClose = {}) }
}

@Preview(name = "Mission preview — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun MissionPreviewLargeFontPreview() {
    BalarmTheme { MissionPreviewScreen(PreviewMission, "{}", seed = 1, onClose = {}) }
}
