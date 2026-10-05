package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DayChipsRow, PresetChips, SettingRow, SingleChoiceDialog, LabelField. */
@RunWith(AndroidJUnit4::class)
class EditorComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(fontScale: Float? = null, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled) { BalarmTheme(content = content) }
        }
    }

    // region DayChipsRow

    /** Строка дней с собственным состоянием, как в редакторе: тап инвертирует день. */
    @Composable
    private fun StatefulDayChips(
        initial: Set<DayOfWeek>,
        labels: List<Pair<String, String>>,
        toggles: MutableList<DayOfWeek>,
    ) {
        var selected by remember { mutableStateOf(initial) }
        val days = DayOfWeek.entries.mapIndexed { i, day ->
            DayChipUi(day, labels[i].first, labels[i].second, selected = day in selected)
        }
        DayChipsRow(
            days = days,
            onToggle = { day ->
                toggles += day
                selected = if (day in selected) selected - day else selected + day
            },
            selectedDescription = SELECTED,
            notSelectedDescription = NOT_SELECTED,
        )
    }

    @Test
    fun `day chip toggles and exposes checked state and description to TalkBack`() {
        val toggles = mutableListOf<DayOfWeek>()
        setContent { StatefulDayChips(setOf(DayOfWeek.MONDAY), EN_DAYS, toggles) }

        val monday = composeRule.onNodeWithTag(DayChipsTestTags.chip(DayOfWeek.MONDAY))
        val tuesday = composeRule.onNodeWithTag(DayChipsTestTags.chip(DayOfWeek.TUESDAY))
        monday
            .assertIsOn()
            .assertContentDescriptionEquals("Monday")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, SELECTED))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        tuesday
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, NOT_SELECTED))

        tuesday.performClick()
        monday.performClick()

        assertThat(toggles).containsExactly(DayOfWeek.TUESDAY, DayOfWeek.MONDAY).inOrder()
        tuesday.assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, SELECTED))
        monday.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, NOT_SELECTED))
    }

    @Test
    fun `day chip TalkBack click toggles`() {
        val toggles = mutableListOf<DayOfWeek>()
        setContent { StatefulDayChips(emptySet(), EN_DAYS, toggles) }

        composeRule.onNodeWithTag(DayChipsTestTags.chip(DayOfWeek.SUNDAY))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(toggles).containsExactly(DayOfWeek.SUNDAY)
    }

    @Test
    fun `day chip label is not a separate TalkBack node`() {
        setContent { StatefulDayChips(emptySet(), EN_DAYS, mutableListOf()) }

        composeRule.onNodeWithText("Mo").assertDoesNotExist()
        composeRule.onNodeWithText("Mo", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `day chips keep the given order`() {
        val sundayFirst = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        setContent {
            DayChipsRow(
                days = sundayFirst.map { DayChipUi(it, it.name.take(2), it.name, selected = false) },
                onToggle = {},
                selectedDescription = SELECTED,
                notSelectedDescription = NOT_SELECTED,
            )
        }

        val lefts = sundayFirst.map {
            composeRule.onNodeWithTag(DayChipsTestTags.chip(it)).getUnclippedBoundsInRoot().left
        }
        assertThat(lefts).isInStrictOrder()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `day chip touch targets are at least 48dp on a 360dp screen with 12dp row padding`() {
        setContent {
            Box(Modifier.padding(horizontal = ROW_PADDING)) { StatefulDayChips(emptySet(), RU_DAYS, mutableListOf()) }
        }

        DayOfWeek.entries.forEach {
            composeRule.onNodeWithTag(DayChipsTestTags.chip(it))
                .assertHeightIsAtLeast(BalarmDimens.MinTouch)
                .assertWidthIsAtLeast(BalarmDimens.MinTouch)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `day chip cells are 1 of 7 of the row, about 46dp with the 20dp screen padding`() {
        setContent {
            Box(Modifier.padding(horizontal = BalarmDimens.ScreenPadding)) {
                StatefulDayChips(emptySet(), RU_DAYS, mutableListOf())
            }
        }

        // Фиксирует фактический минимум: (360 - 2 × 20) / 7 ≈ 45.7dp < 48dp. Для 48dp экран редактора (T15)
        // обязан дать строке дней горизонтальный отступ ≤ 12dp (тест выше).
        DayOfWeek.entries.forEach {
            composeRule.onNodeWithTag(DayChipsTestTags.chip(it))
                .assertHeightIsAtLeast(BalarmDimens.MinTouch)
                .assertWidthIsAtLeast(NARROW_CELL_MIN)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальные метрики шрифта, иначе ширина текста фиктивная
    fun `RU day labels are displayed unclipped at font scale 2 on a narrow screen`() {
        setContent(fontScale = 2f) {
            Box(Modifier.padding(BalarmDimens.ScreenPadding)) {
                StatefulDayChips(setOf(DayOfWeek.MONDAY), RU_DAYS, mutableListOf())
            }
        }

        val row = composeRule.onNodeWithTag(DayChipsTestTags.ROW).assertIsDisplayed().getUnclippedBoundsInRoot()
        RU_DAYS.forEach { (label, _) ->
            val node = composeRule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            assertTextNotClipped(node, label)
            val bounds = node.getUnclippedBoundsInRoot()
            assertThat(bounds.left).isAtLeast(row.left)
            assertThat(bounds.right).isAtMost(row.right)
        }
    }

    // endregion

    // region PresetChips

    @Composable
    private fun StatefulPresets(initial: Set<DayOfWeek>, results: MutableList<Set<DayOfWeek>>) {
        var days by remember { mutableStateOf(initial) }
        PresetChips(
            selectedDays = days,
            onSelect = {
                results += it
                days = it
            },
            weekdaysLabel = "Weekdays",
            weekendLabel = "Weekends",
            everyDayLabel = "Every day",
            selectedDescription = SELECTED,
            notSelectedDescription = NOT_SELECTED,
        )
    }

    @Test
    fun `preset is selected only when the days match`() {
        setContent { StatefulPresets(DayPreset.WEEKDAYS.days, mutableListOf()) }

        composeRule.onNodeWithTag(PresetChipsTestTags.WEEKDAYS)
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, SELECTED))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertTextEquals("Weekdays")
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
        composeRule.onNodeWithTag(PresetChipsTestTags.WEEKEND)
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, NOT_SELECTED))
        composeRule.onNodeWithTag(PresetChipsTestTags.EVERY_DAY).assertIsOff()
    }

    @Test
    fun `no preset is selected for a custom set`() {
        setContent { StatefulPresets(DayPreset.WEEKDAYS.days + DayOfWeek.SATURDAY, mutableListOf()) }

        composeRule.onNodeWithTag(PresetChipsTestTags.WEEKDAYS).assertIsOff()
        composeRule.onNodeWithTag(PresetChipsTestTags.WEEKEND).assertIsOff()
        composeRule.onNodeWithTag(PresetChipsTestTags.EVERY_DAY).assertIsOff()
    }

    @Test
    fun `tap on preset sets its days and a second tap clears them`() {
        val results = mutableListOf<Set<DayOfWeek>>()
        setContent { StatefulPresets(emptySet(), results) }

        val weekend = composeRule.onNodeWithTag(PresetChipsTestTags.WEEKEND)
        weekend.performClick().assertIsOn()
        weekend.performClick().assertIsOff()
        composeRule.onNodeWithTag(PresetChipsTestTags.EVERY_DAY).performClick().assertIsOn()

        assertThat(results)
            .containsExactly(DayPreset.WEEKEND.days, emptySet<DayOfWeek>(), DayOfWeek.entries.toSet())
            .inOrder()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальные метрики шрифта: перенос зависит от ширины текста
    fun `presets wrap to the next line and stay reachable at font scale 2 on a narrow screen`() {
        val results = mutableListOf<Set<DayOfWeek>>()
        setContent(fontScale = 2f) {
            Box(Modifier.padding(BalarmDimens.ScreenPadding)) { StatefulPresets(emptySet(), results) }
        }

        val tops = listOf(PresetChipsTestTags.WEEKDAYS, PresetChipsTestTags.WEEKEND, PresetChipsTestTags.EVERY_DAY)
            .map { composeRule.onNodeWithTag(it).assertIsDisplayed().getUnclippedBoundsInRoot().top }
        assertThat(tops.distinct().size).isGreaterThan(1)
        composeRule.onNodeWithTag(PresetChipsTestTags.EVERY_DAY).performClick()

        assertThat(results).containsExactly(DayOfWeek.entries.toSet())
    }

    // endregion

    // region SettingRow

    @Test
    fun `setting row is one button reading title and value`() {
        var clicks = 0
        setContent { SettingRow(title = "Snooze", value = "5 min", onClick = { clicks++ }, onClickLabel = "Change") }

        composeRule.onNodeWithTag(SettingRowTestTags.ROW)
            .assertTextEquals("Snooze", "5 min")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click labelled Change") {
                    it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Change"
                },
            )
            .assertHeightIsAtLeast(BalarmDimens.ListRowMinHeight)
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `setting row uses explicit description and stays clickable`() {
        var clicks = 0
        setContent {
            SettingRow(
                title = "Snooze",
                value = "5 min",
                onClick = { clicks++ },
                contentDescription = "Snooze, 5 minutes",
            )
        }

        composeRule.onNodeWithTag(SettingRowTestTags.ROW)
            .assertContentDescriptionEquals("Snooze, 5 minutes")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assertHasClickAction()
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `setting row with long texts stays reachable at font scale 2`() {
        var clicks = 0
        setContent(fontScale = 2f) {
            SettingRow(title = "Количество повторов", value = "Без ограничений", onClick = { clicks++ })
        }

        composeRule.onNodeWithTag(SettingRowTestTags.ROW).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Без ограничений", useUnmergedTree = true).assertIsDisplayed()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальные метрики шрифта, иначе ширина текста фиктивная
    fun `title takes the free space and does not wrap next to a short value`() {
        setContent {
            Box(Modifier.padding(horizontal = BalarmDimens.ScreenPadding)) {
                SettingRow(title = "Количество повторов", value = "5 мин", onClick = {})
            }
        }

        val title = composeRule.onNodeWithText("Количество повторов", useUnmergedTree = true).assertIsDisplayed()
        assertThat(textLayout(title).lineCount).isEqualTo(1)
    }

    @Test
    fun `caller test tag overrides the default one`() {
        setContent { SettingRow(title = "Snooze", value = null, onClick = {}, modifier = Modifier.testTag(TAG)) }

        composeRule.onNodeWithTag(TAG).assertHasClickAction()
    }

    // endregion

    // region SingleChoiceDialog

    private fun showDialog(selected: Int, calls: MutableList<String>) {
        setContent {
            SingleChoiceDialog(
                title = "Snooze interval",
                options = OPTIONS,
                selectedIndex = selected,
                onSelect = { calls += "select $it" },
                onDismiss = { calls += "dismiss" },
                dismissText = "Cancel",
            )
        }
    }

    @Test
    fun `dialog shows options as radio buttons with the selected one`() {
        showDialog(selected = 1, calls = mutableListOf())

        composeRule.onNodeWithText("Snooze interval")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        OPTIONS.indices.forEach { index ->
            val option = composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(index))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertTextEquals(OPTIONS[index])
                .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            if (index == 1) option.assertIsSelected() else option.assertIsNotSelected()
        }
    }

    @Test
    fun `tap on option reports its index`() {
        val calls = mutableListOf<String>()
        showDialog(selected = 1, calls = calls)

        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(2)).performClick()

        assertThat(calls).containsExactly("select 2")
    }

    @Test
    fun `cancel dismisses without selecting`() {
        val calls = mutableListOf<String>()
        showDialog(selected = 0, calls = calls)

        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DISMISS)
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()

        assertThat(calls).containsExactly("dismiss")
    }

    @Test
    fun `out of range selection selects nothing`() {
        showDialog(selected = -1, calls = mutableListOf())

        OPTIONS.indices.forEach {
            composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(it)).assertIsNotSelected()
        }
    }

    // endregion

    // region LabelField

    private fun showLabelField(initial: String = "", values: MutableList<String> = mutableListOf()): () -> String {
        var current = initial
        setContent {
            var text by remember { mutableStateOf(initial) }
            LabelField(
                value = text,
                onValueChange = {
                    values += it
                    text = it
                    current = it
                },
                label = "Label",
                maxLength = MAX,
                counterDescription = "${labelLength(text)} of $MAX characters",
            )
        }
        return { current }
    }

    @Test
    fun `typing updates value and counter`() {
        val value = showLabelField()

        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performTextInput("Gym")

        assertThat(value()).isEqualTo("Gym")
        composeRule.onNodeWithTag(LabelFieldTestTags.COUNTER, useUnmergedTree = true)
            .assertTextEquals("3/$MAX")
            .assertContentDescriptionEquals("3 of $MAX characters")
    }

    @Test
    fun `counter counts an emoji as one character`() {
        showLabelField(initial = "Gym $EMOJI")

        composeRule.onNodeWithTag(LabelFieldTestTags.COUNTER, useUnmergedTree = true).assertTextEquals("5/$MAX")
    }

    @Test
    fun `pasting 50 code points with an emoji on the boundary keeps 40 without splitting the pair`() {
        val values = mutableListOf<String>()
        val value = showLabelField(values = values)
        val pasted = "a".repeat(MAX - 1) + EMOJI + "b".repeat(10)

        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performTextReplacement(pasted)

        val expected = "a".repeat(MAX - 1) + EMOJI
        assertThat(value()).isEqualTo(expected)
        assertThat(values).containsExactly(expected)
        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).assert(hasEditableText(expected))
        composeRule.onNodeWithTag(LabelFieldTestTags.COUNTER, useUnmergedTree = true).assertTextEquals("$MAX/$MAX")
    }

    @Test
    fun `typing at the limit is ignored`() {
        val values = mutableListOf<String>()
        val full = "a".repeat(MAX)
        val value = showLabelField(initial = full, values = values)

        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performTextInput("b")

        assertThat(value()).isEqualTo(full)
        assertThat(values).isEmpty()
        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).assert(hasEditableText(full))
    }

    @Test
    fun `pasted line breaks become spaces`() {
        val value = showLabelField()

        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performTextReplacement("Wake\nup\r\nnow")

        assertThat(value()).isEqualTo("Wake up now")
    }

    @Test
    fun `cursor stands right after the accepted part of a paste into the middle`() {
        val value = showLabelField(initial = "start-end")
        val field = composeRule.onNodeWithTag(LabelFieldTestTags.FIELD)

        field.performTextInputSelection(TextRange("start-".length))
        field.performTextInput("x".repeat(50))

        val accepted = "x".repeat(MAX - "start-end".length)
        assertThat(value()).isEqualTo("start-" + accepted + "end")
        field.assert(hasSelection(TextRange("start-".length + accepted.length)))
    }

    @Test
    fun `rejected input in a full field keeps the cursor in place`() {
        val values = mutableListOf<String>()
        val full = "a".repeat(MAX)
        showLabelField(initial = full, values = values)
        val field = composeRule.onNodeWithTag(LabelFieldTestTags.FIELD)

        field.performTextInputSelection(TextRange(CURSOR))
        field.performTextInput("b")

        assertThat(values).isEmpty()
        field.assert(hasEditableText(full)).assert(hasSelection(TextRange(CURSOR)))
    }

    // endregion

    private fun hasSelection(range: TextRange) =
        SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, range)

    private fun hasEditableText(text: String) = SemanticsMatcher.expectValue(
        SemanticsProperties.EditableText,
        AnnotatedString(text),
    )

    /** Текст целиком умещается в своём узле: нет переполнения и строка не шире узла. */
    private fun assertTextNotClipped(node: SemanticsNodeInteraction, text: String) {
        val semantics = node.fetchSemanticsNode()
        val layout = textLayout(node)
        assertWithMessage("\"$text\" overflows its node").that(layout.hasVisualOverflow).isFalse()
        assertWithMessage("\"$text\" line is wider than its node")
            .that(layout.getLineRight(0) - layout.getLineLeft(0))
            .isAtMost(semantics.size.width.toFloat())
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        const val TAG = "subject"
        const val CURSOR = 10
        val NARROW_CELL_MIN = 45.dp
        const val SELECTED = "Selected"
        const val NOT_SELECTED = "Not selected"
        const val MAX = 40
        const val EMOJI = "\uD83D\uDE00"
        val ROW_PADDING = 12.dp
        val OPTIONS = listOf("1 min", "5 min", "10 min")

        val EN_DAYS = listOf(
            "Mo" to "Monday",
            "Tu" to "Tuesday",
            "We" to "Wednesday",
            "Th" to "Thursday",
            "Fr" to "Friday",
            "Sa" to "Saturday",
            "Su" to "Sunday",
        )

        val RU_DAYS = listOf(
            "Пн" to "понедельник",
            "Вт" to "вторник",
            "Ср" to "среда",
            "Чт" to "четверг",
            "Пт" to "пятница",
            "Сб" to "суббота",
            "Вс" to "воскресенье",
        )
    }
}
