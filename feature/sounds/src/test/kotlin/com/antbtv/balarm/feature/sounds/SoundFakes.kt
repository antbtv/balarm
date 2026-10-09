package com.antbtv.balarm.feature.sounds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundPreview
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Превью без звука: запоминает, что и сколько раз просили сыграть и заглушить. */
internal class RecordingSoundPreview : SoundPreview {
    private val current = MutableStateFlow<SoundRef?>(null)
    override val playing: StateFlow<SoundRef?> = current.asStateFlow()

    val played = mutableListOf<SoundSettings>()
    var stops = 0
        private set

    override fun play(settings: SoundSettings) {
        played += settings
        current.value = settings.sound
    }

    override fun stop() {
        stops++
        current.value = null
    }
}

/**
 * Библиотека в памяти. [importResult] — исход следующего импорта; `Imported` добавляет мелодию в список.
 * [importGate] — не `null`: импорт ждёт его (проверка индикатора прогресса).
 */
internal class FakeSoundRepository(initial: List<CustomSound> = emptyList()) : SoundRepository {
    val sounds = MutableStateFlow(initial)
    var importResult: ImportResult = ImportResult.Unsupported
    var importGate: CompletableDeferred<Unit>? = null
    val imported = mutableListOf<String>()
    var usage: Map<CustomSoundId, Int> = emptyMap()
    var renameSucceeds = true
    val renamed = mutableListOf<Pair<CustomSoundId, String>>()
    val deleted = mutableListOf<CustomSoundId>()

    override fun observeCustomSounds(): Flow<List<CustomSound>> = sounds

    override suspend fun getCustom(id: CustomSoundId): CustomSound? = sounds.value.firstOrNull { it.id == id }

    override suspend fun import(source: SoundSource): ImportResult {
        imported += source.uri
        importGate?.await()
        val result = importResult
        if (result is ImportResult.Imported) sounds.update { listOf(result.sound) + it }
        return result
    }

    override suspend fun rename(id: CustomSoundId, title: String): Boolean {
        renamed += id to title
        if (renameSucceeds) sounds.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
        return renameSucceeds
    }

    override suspend fun usageCount(id: CustomSoundId): Int = usage[id] ?: 0

    override suspend fun delete(id: CustomSoundId): Int {
        deleted += id
        sounds.update { list -> list.filterNot { it.id == id } }
        return usage[id] ?: 0
    }

    override suspend fun cleanUp() = Unit
}

internal fun customSound(id: Long, title: String, seconds: Long = 32, size: Long = 640_000) = CustomSound(
    id = CustomSoundId(id),
    title = title,
    duration = Duration.ofSeconds(seconds),
    sizeBytes = size,
    addedAt = Instant.EPOCH,
)

internal fun flags(customSounds: Boolean) =
    FeatureFlagProvider { if (it == Feature.CUSTOM_SOUNDS) customSounds else it.defaultEnabled }

/** Тема + при необходимости увеличенный шрифт (как «Размер шрифта» в системных настройках). */
@Composable
internal fun TestTheme(fontScale: Float? = null, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
    CompositionLocalProvider(LocalDensity provides scaled) {
        BalarmTheme(content = content)
    }
}

/** Ни один показанный текст не обрезан (как `assertNoTextOverflow` в `:feature:settings`). */
internal fun ComposeTestRule.assertNoTextOverflow() {
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
    assertWithMessage("text nodes").that(nodes).isNotEmpty()
    nodes.forEach { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val text = layout.layoutInput.text
        assertWithMessage("\"$text\" overflows in height").that(layout.didOverflowHeight).isFalse()
        for (line in 0 until layout.lineCount) {
            assertWithMessage("\"$text\" line $line is ellipsized").that(layout.isLineEllipsized(line)).isFalse()
            assertWithMessage("\"$text\" line $line is wider than its node")
                .that(layout.getLineRight(line) - layout.getLineLeft(line))
                .isAtMost(node.size.width + LINE_ROUNDING_PX)
        }
    }
}

private const val LINE_ROUNDING_PX = 1f
