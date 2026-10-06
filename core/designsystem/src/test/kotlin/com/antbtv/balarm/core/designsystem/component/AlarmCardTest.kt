package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.designsystem.theme.DarkBalarmColors
import com.antbtv.balarm.core.designsystem.theme.DefaultBalarmTypography
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class AlarmCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0
    private var longClicks = 0
    private val toggles = mutableListOf<Boolean>()

    private fun showCard(active: Boolean = true, fontScale: Float? = null, days: List<DayPillUi> = EN_WEEKDAYS) {
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled) {
                BalarmTheme {
                    // Как в списке: карточка с отступами экрана.
                    Box(Modifier.padding(BalarmDimens.ScreenPadding)) {
                        AlarmCard(
                            time = TIME,
                            amPm = null,
                            label = "Gym",
                            days = days,
                            active = active,
                            subtitle = "Tomorrow",
                            contentDescription = CARD_DESCRIPTION,
                            toggleDescription = TOGGLE_DESCRIPTION,
                            onToggle = { toggles += it },
                            onClick = { clicks++ },
                            onClickLabel = "Edit",
                            onLongClick = { longClicks++ },
                            onLongClickLabel = "Delete",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `tap opens the alarm`() {
        showCard()

        composeRule.onNodeWithTag(AlarmCardTestTags.CARD).assertHasClickAction().performClick()

        assertThat(clicks).isEqualTo(1)
        assertThat(longClicks).isEqualTo(0)
        assertThat(toggles).isEmpty()
    }

    @Test
    fun `long tap calls onLongClick only`() {
        showCard()

        composeRule.onNodeWithTag(AlarmCardTestTags.CARD).performTouchInput { longClick() }

        assertThat(longClicks).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `click and long click are exposed to TalkBack with labels`() {
        showCard()

        composeRule.onNodeWithTag(AlarmCardTestTags.CARD)
            .assert(
                SemanticsMatcher("click labelled Edit") {
                    it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Edit"
                },
            )
            .assert(
                SemanticsMatcher("long click labelled Delete") {
                    it.config.getOrElseNullable(SemanticsActions.OnLongClick) { null }?.label == "Delete"
                },
            )
    }

    @Test
    fun `long tap on the switch does not trigger the card long click`() {
        showCard()

        composeRule.onNodeWithTag(AlarmCardTestTags.SWITCH).performTouchInput { longClick() }

        assertThat(longClicks).isEqualTo(0)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `switch semantics click action (TalkBack path) toggles`() {
        showCard(active = true)

        composeRule.onNodeWithTag(AlarmCardTestTags.SWITCH).performSemanticsAction(SemanticsActions.OnClick)

        assertThat(toggles).containsExactly(false)
        assertThat(clicks).isEqualTo(0)
        assertThat(longClicks).isEqualTo(0)
    }

    @Test
    fun `switch sends the new value and does not open the card`() {
        showCard(active = true)

        composeRule.onNodeWithTag(AlarmCardTestTags.SWITCH).assertIsOn().performClick()

        assertThat(toggles).containsExactly(false)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `inactive card switch is off and turning it on sends true`() {
        showCard(active = false)

        composeRule.onNodeWithTag(AlarmCardTestTags.SWITCH).assertIsOff().performClick()

        assertThat(toggles).containsExactly(true)
    }

    @Test
    fun `inactive time and label use textSecondary`() {
        assertThat(alarmCardTextColor(active = false, colors = DarkBalarmColors))
            .isEqualTo(DarkBalarmColors.textSecondary)
        assertThat(alarmCardTextColor(active = true, colors = DarkBalarmColors))
            .isEqualTo(DarkBalarmColors.textPrimary)
    }

    @Test
    fun `card is a single TalkBack node with the given description`() {
        showCard()

        // Внутренние тексты (время, метка, дни) не попадают в объединённый узел — TalkBack читает только описание.
        composeRule.onNodeWithTag(AlarmCardTestTags.CARD)
            .assertContentDescriptionEquals(CARD_DESCRIPTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        composeRule.onNodeWithText(TIME).assertDoesNotExist()
    }

    @Test
    fun `switch has Switch role, own description and a 48dp touch target`() {
        showCard()

        composeRule.onNodeWithTag(AlarmCardTestTags.SWITCH)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertContentDescriptionEquals(TOGGLE_DESCRIPTION)
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .assertWidthIsAtLeast(BalarmDimens.MinTouch)
    }

    @Test
    fun `card touch target is at least 48dp`() {
        showCard()

        composeRule.onNodeWithTag(AlarmCardTestTags.CARD).assertHeightIsAtLeast(BalarmDimens.MinTouch)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `switch stays reachable at font scale 2 on a narrow screen`() {
        showCard(fontScale = 2f)

        composeRule.onNodeWithTag(AlarmCardTestTags.SWITCH).assertIsDisplayed().performClick()

        assertThat(toggles).containsExactly(false)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальные метрики шрифта, иначе ширина текста фиктивная
    fun `RU day labels and time are displayed unclipped at font scale 2 on a narrow screen`() {
        showCard(fontScale = 2f, days = RU_WEEKDAYS)

        assertDayLabelsFitUnderTheirDots(RU_WEEKDAYS, fontScale = 2f)
        assertTextNotClipped(composeRule.onNodeWithText(TIME, useUnmergedTree = true).assertIsDisplayed(), TIME)
    }

    // Регрессия смока 2 (эмулятор, fontScale 2): «Mon» → «Mor», «Wed» → «Wec». Двухбуквенные тестовые подписи
    // («Mo», «Пн») влезали, реальные трёхбуквенные из :core:format — нет.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальные метрики шрифта, иначе ширина текста фиктивная
    fun `EN three-letter day labels are displayed unclipped at font scale 2 on a narrow screen`() {
        showCard(fontScale = 2f, days = EN_SHORT_WEEKDAYS)

        assertDayLabelsFitUnderTheirDots(EN_SHORT_WEEKDAYS, fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `day labels keep the caption size at font scale 1`() {
        showCard(days = EN_SHORT_WEEKDAYS)

        EN_SHORT_WEEKDAYS.forEach { day ->
            val layout = textLayout(composeRule.onNodeWithText(day.label, useUnmergedTree = true))
            assertThat(layout.layoutInput.style.fontSize)
                .isEqualTo(DefaultBalarmTypography.captionStrong.fontSize)
        }
    }

    /**
     * Каждая подпись: целиком, внутри строки, не залезает на соседнюю, точка — по центру под ней; размер у всех
     * подписей один и не меньше [BalarmDimens.DayPillLabelMinSize].
     */
    private fun assertDayLabelsFitUnderTheirDots(days: List<DayPillUi>, fontScale: Float) {
        val row = composeRule.onNodeWithTag(DayPillsTestTags.ROW, useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val dots = composeRule.onAllNodesWithTag(DayPillsTestTags.DOT, useUnmergedTree = true)
        dots.assertCountEquals(days.size)
        var previousRight = row.left
        val fontSizes = days.mapIndexed { index, day ->
            val label = composeRule.onNodeWithText(day.label, useUnmergedTree = true).assertIsDisplayed()
            assertTextNotClipped(label, day.label)
            val bounds = label.getUnclippedBoundsInRoot()
            assertWithMessage("\"${day.label}\" inside the row").that(bounds.left).isAtLeast(row.left)
            assertWithMessage("\"${day.label}\" inside the row").that(bounds.right).isAtMost(row.right)
            assertWithMessage("\"${day.label}\" overlaps the previous label").that(bounds.left).isAtLeast(previousRight)
            previousRight = bounds.right
            val dot = dots[index].getUnclippedBoundsInRoot()
            val labelCenter = (bounds.left + bounds.right) / 2
            val dotCenter = (dot.left + dot.right) / 2
            assertWithMessage("dot under \"${day.label}\"").that((labelCenter - dotCenter).value)
                .isWithin(CENTER_TOLERANCE_DP)
                .of(0f)
            assertWithMessage("dot below \"${day.label}\"").that(dot.top).isAtLeast(bounds.bottom)
            textLayout(label).layoutInput.style.fontSize
        }
        assertWithMessage("all day labels share one size").that(fontSizes.toSet()).hasSize(1)
        // Размер в sp × fontScale = размер на экране в dp; минимум задан в dp (без учёта fontScale).
        assertThat(fontSizes.first().value * fontScale).isAtLeast(BalarmDimens.DayPillLabelMinSize.value)
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /**
     * Текст целиком умещается в своём узле: строка не шире узла, нет лишних строк и переполнения по высоте.
     * `hasVisualOverflow` не используем: при `softWrap = false` абзац раскладывается на всю ширину ограничения,
     * и флаг ширины срабатывает, даже когда узел по ширине текста и ничего не обрезано.
     */
    private fun assertTextNotClipped(node: SemanticsNodeInteraction, text: String) {
        val semantics = node.fetchSemanticsNode()
        val layout = textLayout(node)
        assertWithMessage("\"$text\" overflows its node height").that(layout.didOverflowHeight).isFalse()
        assertWithMessage("\"$text\" is cut to one line").that(layout.multiParagraph.didExceedMaxLines).isFalse()
        assertWithMessage("\"$text\" line is wider than its node")
            .that(layout.getLineRight(0) - layout.getLineLeft(0))
            .isAtMost(semantics.size.width.toFloat())
    }

    private companion object {
        const val TIME = "07:30"
        const val CENTER_TOLERANCE_DP = 1f
        const val CARD_DESCRIPTION = "Alarm 07:30, Gym, weekdays, on"
        const val TOGGLE_DESCRIPTION = "Alarm 07:30"

        val EN_WEEKDAYS = listOf(
            DayPillUi("Mo", selected = true, description = "Monday"),
            DayPillUi("Tu", selected = true, description = "Tuesday"),
            DayPillUi("We", selected = true, description = "Wednesday"),
            DayPillUi("Th", selected = true, description = "Thursday"),
            DayPillUi("Fr", selected = true, description = "Friday"),
            DayPillUi("Sa", selected = false, description = "Saturday"),
            DayPillUi("Su", selected = false, description = "Sunday"),
        )

        val EN_SHORT_WEEKDAYS = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        ).map { day ->
            DayPillUi(
                label = day.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH),
                selected = day != DayOfWeek.SUNDAY && day != DayOfWeek.SATURDAY,
                description = day.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ENGLISH),
            )
        }

        val RU_WEEKDAYS = listOf(
            DayPillUi("Пн", selected = true, description = "понедельник"),
            DayPillUi("Вт", selected = true, description = "вторник"),
            DayPillUi("Ср", selected = true, description = "среда"),
            DayPillUi("Чт", selected = true, description = "четверг"),
            DayPillUi("Пт", selected = true, description = "пятница"),
            DayPillUi("Сб", selected = false, description = "суббота"),
            DayPillUi("Вс", selected = false, description = "воскресенье"),
        )
    }
}
