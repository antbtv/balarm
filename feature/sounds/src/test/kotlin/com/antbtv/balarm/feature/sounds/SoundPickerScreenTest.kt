package com.antbtv.balarm.feature.sounds

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.TopBarTestTags
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Пикер мелодии на Robolectric (M4-T10): выбор, «Выбрать», импорт через SAF и его ошибки, флаг, fontScale 2. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class SoundPickerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sounds = FakeSoundRepository(listOf(customSound(7, "Birds")))
    private val preview = RecordingSoundPreview()
    private val store = ViewModelStore()
    private val registry = FakeDocumentRegistry()

    private val bells = SoundRef.Builtin(BuiltinSound.BELLS)
    private var picked: SoundRef? = null
    private var closed = 0
    private var libraryOpened = 0

    @After
    fun tearDown() = store.clear()

    private fun viewModel(selected: SoundRef, customSounds: Boolean): SoundPickerViewModel {
        val created = SoundPickerViewModel(selected.encode(), sounds, preview, flags(customSounds))
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        return ViewModelProvider(store, factory)[SoundPickerViewModel::class.java]
    }

    private fun show(selected: SoundRef = SoundRef.DEFAULT, customSounds: Boolean = true, fontScale: Float? = null) {
        val viewModel = viewModel(selected, customSounds)
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                TestTheme(fontScale = fontScale) {
                    SoundPickerRoute(
                        viewModel = viewModel,
                        onPicked = { picked = it },
                        onClose = { closed++ },
                        onOpenLibrary = { libraryOpened++ },
                        windowInsets = WindowInsets(0),
                    )
                }
            }
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(SoundsTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `selected sound is marked, tap selects and plays, Choose returns it and closes`() {
        show()
        composeRule.onNodeWithTag(SoundsTestTags.builtin(BuiltinSound.DEFAULT)).assertIsSelected()

        composeRule.onNodeWithTag(SoundsTestTags.builtin(BuiltinSound.BELLS)).performClick()

        composeRule.onNodeWithTag(SoundsTestTags.builtin(BuiltinSound.BELLS))
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
        assertThat(preview.played.last().sound).isEqualTo(bells)

        composeRule.onNodeWithTag(SoundsTestTags.CONFIRM).performClick()
        assertThat(picked).isEqualTo(bells)
        assertThat(closed).isEqualTo(1)
        assertThat(preview.playing.value).isNull()
    }

    @Test
    fun `tap on the playing row stops it`() {
        show()
        val row = composeRule.onNodeWithTag(SoundsTestTags.builtin(BuiltinSound.BELLS))

        row.performClick()
        row.performClick()

        assertThat(preview.playing.value).isNull()
        row.assertIsSelected()
    }

    @Test
    fun `my sounds block with Manage and Add when the flag is on`() {
        show()

        scrollTo(SoundsTestTags.custom(CustomSoundId(7)))
        composeRule.onNodeWithTag(SoundsTestTags.custom(CustomSoundId(7))).assertTextContains("Birds")
        scrollTo(SoundsTestTags.MANAGE)
        composeRule.onNodeWithTag(SoundsTestTags.MANAGE).performClick()
        assertThat(libraryOpened).isEqualTo(1)
        scrollTo(SoundsTestTags.ADD)
        composeRule.onNodeWithTag(SoundsTestTags.ADD).assertIsEnabled()
    }

    @Test
    fun `flag off - no my sounds block`() {
        show(customSounds = false)

        composeRule.onNodeWithTag(SoundsTestTags.CUSTOM_HEADER).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.ADD).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.MANAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.custom(CustomSoundId(7))).assertDoesNotExist()
    }

    @Test
    fun `flag off - the alarm's own custom sound is still shown`() {
        show(selected = SoundRef.Custom(CustomSoundId(7)), customSounds = false)

        scrollTo(SoundsTestTags.custom(CustomSoundId(7)))
        composeRule.onNodeWithTag(SoundsTestTags.custom(CustomSoundId(7))).assertIsSelected()
        composeRule.onNodeWithTag(SoundsTestTags.ADD).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.MANAGE).assertDoesNotExist()
    }

    @Test
    fun `add opens the audio document picker and the imported sound appears selected`() {
        val sound = customSound(9, "Motivation")
        sounds.importResult = ImportResult.Imported(sound)
        registry.result = Uri.parse("content://docs/9")
        show()

        scrollTo(SoundsTestTags.ADD)
        composeRule.onNodeWithTag(SoundsTestTags.ADD).performClick()
        composeRule.waitForIdle()

        assertThat(registry.launchedTypes).asList().containsExactly("audio/*", "application/ogg")
        assertThat(sounds.imported).containsExactly("content://docs/9")
        scrollTo(SoundsTestTags.custom(sound.id))
        composeRule.onNodeWithTag(SoundsTestTags.custom(sound.id)).assertIsSelected()
        assertThat(preview.playing.value).isEqualTo(SoundRef.Custom(sound.id))
        composeRule.onNodeWithText("“Motivation” added").assertIsDisplayed()
    }

    @Test
    fun `too large file shows the error`() {
        sounds.importResult = ImportResult.TooLarge(20L * 1024 * 1024)
        registry.result = Uri.parse("content://docs/big")
        show()

        scrollTo(SoundsTestTags.ADD)
        composeRule.onNodeWithTag(SoundsTestTags.ADD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("File is larger than 20 MB").assertIsDisplayed()
    }

    @Test
    fun `not audio shows the error`() {
        sounds.importResult = ImportResult.Unsupported
        registry.result = Uri.parse("content://docs/pdf")
        show()

        scrollTo(SoundsTestTags.ADD)
        composeRule.onNodeWithTag(SoundsTestTags.ADD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Couldn't recognize the audio").assertIsDisplayed()
    }

    @Test
    fun `cancelled document picker imports nothing`() {
        registry.result = null
        show()

        scrollTo(SoundsTestTags.ADD)
        composeRule.onNodeWithTag(SoundsTestTags.ADD).performClick()
        composeRule.waitForIdle()

        assertThat(sounds.imported).isEmpty()
    }

    @Test
    fun `deleted selected sound - hint and Choose disabled`() {
        show(selected = SoundRef.Custom(CustomSoundId(42)))

        composeRule.onNodeWithTag(SoundsTestTags.SELECTED_MISSING).assertIsDisplayed()
        composeRule.onNodeWithTag(SoundsTestTags.CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun `back closes and silences the preview`() {
        show()
        composeRule.onNodeWithTag(SoundsTestTags.builtin(BuiltinSound.BELLS)).performClick()

        composeRule.onNodeWithTag(TopBarTestTags.BACK).performClick()

        assertThat(closed).isEqualTo(1)
        assertThat(picked).isNull()
        assertThat(preview.playing.value).isNull()
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h640dp")
    fun `fontScale 2 on 360dp - nothing is cut`() {
        show(fontScale = 2f)

        composeRule.assertNoTextOverflow()
        composeRule.onNodeWithTag(SoundsTestTags.CONFIRM).assertIsDisplayed()
    }
}

/** SAF без системного UI: запоминает MIME-типы и сразу отвечает [result] (`null` — пользователь отменил). */
internal class FakeDocumentRegistry : ActivityResultRegistryOwner {
    var result: Uri? = null
    var launchedTypes: Array<String>? = null

    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            @Suppress("UNCHECKED_CAST")
            launchedTypes = input as Array<String>
            dispatchResult(requestCode, result)
        }
    }
}
