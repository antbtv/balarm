package com.antbtv.balarm.feature.sounds

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.component.LabelFieldTestTags
import com.antbtv.balarm.core.designsystem.component.TextInputDialogTestTags
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** «Мои мелодии» на Robolectric (M4-T10): список, прослушивание, диалоги, пустое состояние, импорт, флаг. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class SoundLibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sounds = FakeSoundRepository(listOf(customSound(7, "Birds", seconds = 65), customSound(3, "Rain")))
    private val preview = RecordingSoundPreview()
    private val store = ViewModelStore()
    private val registry = FakeDocumentRegistry()
    private var closed = 0

    private val birdsId = CustomSoundId(7)

    @After
    fun tearDown() = store.clear()

    private fun show(customSounds: Boolean = true, fontScale: Float? = null) {
        val created = SoundLibraryViewModel(sounds, preview, flags(customSounds))
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        val viewModel = ViewModelProvider(store, factory)[SoundLibraryViewModel::class.java]
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                TestTheme(fontScale = fontScale) {
                    SoundLibraryRoute(viewModel = viewModel, onClose = { closed++ }, windowInsets = WindowInsets(0))
                }
            }
        }
    }

    @Test
    fun `lists sounds with duration and size`() {
        show()

        composeRule.onNodeWithTag(SoundsTestTags.play(birdsId)).assertTextContains("Birds")
        composeRule.onNodeWithTag(SoundsTestTags.play(birdsId)).assertTextContains("1:05", substring = true)
        composeRule.onNodeWithContentDescription("1 minute 5 seconds", substring = true).assertExists()
        composeRule.onNodeWithTag(SoundsTestTags.LIBRARY_EMPTY).assertDoesNotExist()
    }

    @Test
    fun `play toggles and is announced`() {
        show()
        val play = composeRule.onNodeWithTag(SoundsTestTags.play(birdsId))

        play.performClick()
        play.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
        assertThat(preview.playing.value).isEqualTo(SoundRef.Custom(birdsId))

        play.performClick()
        play.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        assertThat(preview.playing.value).isNull()
    }

    @Test
    fun `empty state`() {
        sounds.sounds.value = emptyList()
        show()

        composeRule.onNodeWithTag(SoundsTestTags.LIBRARY_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No ringtones yet").assertIsDisplayed()
        composeRule.onNodeWithTag(SoundsTestTags.ADD).assertIsDisplayed()
    }

    @Test
    fun `rename dialog saves the new title`() {
        show()

        composeRule.onNodeWithTag(SoundsTestTags.rename(birdsId)).performClick()
        composeRule.onNodeWithTag(SoundsTestTags.RENAME_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performTextClearance()
        composeRule.onNodeWithTag(TextInputDialogTestTags.CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performTextInput("Morning birds")
        composeRule.onNodeWithTag(TextInputDialogTestTags.CONFIRM).performClick()

        composeRule.onNodeWithTag(SoundsTestTags.RENAME_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.play(birdsId)).assertTextContains("Morning birds")
        assertThat(sounds.renamed).containsExactly(birdsId to "Morning birds")
    }

    @Test
    fun `deleting a used sound warns, then reports switched alarms`() {
        sounds.usage = mapOf(birdsId to 2)
        show()

        composeRule.onNodeWithTag(SoundsTestTags.delete(birdsId)).performClick()

        composeRule.onNodeWithText("Delete “Birds”?").assertIsDisplayed()
        composeRule.onNodeWithText("Used by 2 alarms. They will switch to “Classic”.").assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM).performClick()

        composeRule.onNodeWithTag(SoundsTestTags.custom(birdsId)).assertDoesNotExist()
        composeRule.onNodeWithText("“Birds” deleted. 2 alarms switched to “Classic”").assertIsDisplayed()
    }

    @Test
    fun `deleting an unused sound has no warning, cancel keeps it`() {
        show()

        composeRule.onNodeWithTag(SoundsTestTags.delete(birdsId)).performClick()
        composeRule.onNodeWithText("The file will be removed from the app.").assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DISMISS).performClick()

        composeRule.onNodeWithTag(SoundsTestTags.DELETE_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.custom(birdsId)).assertExists()
        assertThat(sounds.deleted).isEmpty()
    }

    @Test
    fun `import error is shown`() {
        sounds.importResult = ImportResult.NoSpace
        registry.result = Uri.parse("content://docs/1")
        show()

        composeRule.onNodeWithTag(SoundsTestTags.ADD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Not enough storage space").assertIsDisplayed()
    }

    @Test
    fun `flag off - the screen closes itself`() {
        show(customSounds = false)
        composeRule.waitForIdle()

        assertThat(closed).isEqualTo(1)
        composeRule.onNodeWithTag(SoundsTestTags.LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(SoundsTestTags.ADD).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h640dp")
    fun `fontScale 2 on 360dp - nothing is cut`() {
        show(fontScale = 2f)

        composeRule.assertNoTextOverflow()
        composeRule.onNodeWithTag(SoundsTestTags.rename(birdsId)).assertIsDisplayed()
    }
}
