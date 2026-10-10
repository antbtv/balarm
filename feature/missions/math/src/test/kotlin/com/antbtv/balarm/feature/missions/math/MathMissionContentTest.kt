package com.antbtv.balarm.feature.missions.math

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.SliderRowTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.feature.missions.api.MissionFrame
import com.antbtv.balarm.feature.missions.api.MissionSession
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class MathMissionContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val config = MathConfig(level = 2, count = 2)
    private val problems = MathGenerator(Random(SEED)).sequence(config.level, config.count)

    private var interactions = 0
    private var completions = 0
    private val progress = mutableListOf<Pair<Int, Int>>()

    private fun session(resetSignal: Int = 0) = MissionSession(
        seed = SEED,
        isPreview = false,
        resetSignal = resetSignal,
        onInteraction = { interactions++ },
        onProgress = { done, total -> progress += done to total },
        onCompleted = { completions++ },
    )

    private var currentSession by mutableStateOf(session())

    private fun show() {
        composeRule.setContent {
            BalarmTheme {
                MathMission.Content(config.encode(), currentSession, Modifier)
            }
        }
    }

    private fun type(text: String) {
        text.forEach { char ->
            composeRule.onNodeWithTag(MathTestTags.key(MathKey.Digit(char.digitToInt()))).performClick()
        }
    }

    private fun enter() = composeRule.onNodeWithTag(MathTestTags.key(MathKey.Enter)).performClick()

    private fun problemText() = composeRule.onNodeWithTag(MathTestTags.PROBLEM)

    @Test
    fun `right answers report progress and complete the mission once`() {
        show()
        problemText().assertTextEquals(problems[0].text)

        type(problems[0].answer.toString())
        enter()
        problemText().assertTextEquals(problems[1].text)

        type(problems[1].answer.toString())
        enter()

        composeRule.waitForIdle()
        assertThat(completions).isEqualTo(1)
        assertThat(progress).containsExactly(0 to 2, 1 to 2, 2 to 2).inOrder()
        val presses = problems.sumOf { it.answer.toString().length + 1 }
        assertThat(interactions).isEqualTo(presses)

        // Лишние нажатия после победы не завершают миссию повторно.
        enter()
        assertThat(completions).isEqualTo(1)
    }

    @Test
    fun `wrong answer clears the input and keeps the same problem`() {
        show()
        type((problems[0].answer + 1).toString())
        composeRule.onNodeWithTag(MathTestTags.ANSWER)
            .assertContentDescriptionEquals("Answer: ${problems[0].answer + 1}")

        enter()

        composeRule.onNodeWithTag(MathTestTags.ANSWER).assertContentDescriptionEquals("Answer: empty")
        problemText().assertTextEquals(problems[0].text)
        assertThat(completions).isEqualTo(0)
        assertThat(progress.last()).isEqualTo(0 to 2)
    }

    @Test
    fun `erase removes the last digit`() {
        show()
        type("47")
        composeRule.onNodeWithTag(MathTestTags.key(MathKey.Erase)).performClick()

        composeRule.onNodeWithTag(MathTestTags.ANSWER).assertContentDescriptionEquals("Answer: 4")
    }

    @Test
    fun `idle reset signal clears the current input but keeps solved problems`() {
        show()
        type(problems[0].answer.toString())
        enter()
        type("12")

        currentSession = session(resetSignal = 1)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MathTestTags.ANSWER).assertContentDescriptionEquals("Answer: empty")
        problemText().assertTextEquals(problems[1].text)
        assertThat(completions).isEqualTo(0)
    }

    @Test
    fun `progress and input survive recreation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            BalarmTheme {
                MathMission.Content(config.encode(), currentSession, Modifier)
            }
        }
        type(problems[0].answer.toString())
        enter()
        type("3")

        restoration.emulateSavedInstanceStateRestore()

        problemText().assertTextEquals(problems[1].text)
        composeRule.onNodeWithTag(MathTestTags.ANSWER).assertContentDescriptionEquals("Answer: 3")
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian descriptions`() {
        show()
        composeRule.onNodeWithTag(MathTestTags.ANSWER).assertContentDescriptionEquals("Ответ: пусто")
        composeRule.onNodeWithTag(MathTestTags.key(MathKey.Erase)).assertContentDescriptionEquals("Стереть")
        composeRule.onNodeWithTag(MathTestTags.key(MathKey.Enter)).assertContentDescriptionEquals("Проверить ответ")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `keypad fits a narrow screen at font scale 2 with large keys`() {
        composeRule.setContent {
            BalarmTheme {
                MissionFrame(done = 0, total = config.count, idleKey = 0) {
                    MathMission.Content(MathConfig(level = 5, count = 10).encode(), currentSession, Modifier)
                }
            }
        }
        val root = composeRule.onNodeWithTag(MathTestTags.ROOT).getUnclippedBoundsInRoot()
        val keys = (0..9).map { MathKey.Digit(it) } + MathKey.Erase + MathKey.Enter
        keys.forEach { key ->
            val node = composeRule.onNodeWithTag(MathTestTags.key(key))
            node.assertIsDisplayed().assertHeightIsAtLeast(BalarmDimens.ButtonHeight)
            assertThat(node.getUnclippedBoundsInRoot().bottom).isAtMost(root.bottom + 1.dp)
        }
        problemText().assertIsDisplayed()
    }

    @Test
    fun `keys are announced as buttons`() {
        show()
        composeRule.onNodeWithTag(MathTestTags.key(MathKey.Digit(7)))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assertContentDescriptionEquals("7")
    }

    @Test
    fun `definition contract`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(MathMission.type).isEqualTo(MissionTypeId.MATH)
        assertThat(MathMission.feature).isEqualTo(Feature.MISSION_MATH)
        assertThat(MathConfig.parse(MathMission.defaultConfig())).isEqualTo(MathConfig())
        assertThat(MathMission.normalize("""{"level":9,"count":0}"""))
            .isEqualTo(MathConfig(level = 5, count = 1).encode())
        assertThat(MathMission.normalize("garbage")).isEqualTo(MathConfig().encode())
        assertThat(summary(context, level = 2, count = 3)).isEqualTo("Level 2, 3 problems")
        assertThat(summary(context, level = 1, count = 1)).isEqualTo("Level 1, 1 problem")
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian summary uses plurals`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(summary(context, level = 2, count = 3)).isEqualTo("Уровень 2, 3 примера")
        assertThat(summary(context, level = 4, count = 5)).isEqualTo("Уровень 4, 5 примеров")
        assertThat(summary(context, level = 1, count = 1)).isEqualTo("Уровень 1, 1 пример")
    }

    @Test
    fun `config editor changes level and count through the sliders`() {
        var config by mutableStateOf(MathConfig().encode())
        composeRule.setContent {
            BalarmTheme {
                MathMission.ConfigEditor(config = config, onChange = { config = it }, modifier = Modifier)
            }
        }
        fun slider(row: String) = composeRule.onNode(
            hasTestTag(SliderRowTestTags.SLIDER) and hasAnyAncestor(hasTestTag(row)),
        )

        slider(MathTestTags.EDITOR_LEVEL).performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        slider(MathTestTags.EDITOR_COUNT).performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        composeRule.waitForIdle()

        assertThat(MathConfig.parse(config)).isEqualTo(MathConfig(level = 4, count = 7))
        composeRule.onNodeWithTag(MathTestTags.EDITOR_EXAMPLE).assertIsDisplayed()
    }

    private fun summary(context: Context, level: Int, count: Int) =
        MathMission.summary(context, MathConfig(level = level, count = count).encode())

    private companion object {
        const val SEED = 2026L
    }
}
