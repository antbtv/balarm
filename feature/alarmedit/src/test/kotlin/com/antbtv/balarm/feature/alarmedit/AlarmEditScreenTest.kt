package com.antbtv.balarm.feature.alarmedit

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.component.DayChipsTestTags
import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.designsystem.component.LabelFieldTestTags
import com.antbtv.balarm.core.designsystem.component.PresetChipsTestTags
import com.antbtv.balarm.core.designsystem.component.SingleChoiceDialogTestTags
import com.antbtv.balarm.core.designsystem.component.TimeWheelPickerTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.formatTimeUntil
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Экран без ViewModel: события записываются и прогоняются через настоящий [AlarmEditReducer] — экран ведёт себя
 * как с ViewModel (диалоги открываются и закрываются), а побочные действия видны как события.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AlarmEditScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AlarmEditEvent>()
    private var state by mutableStateOf(existing())
    private var outerBacks = 0

    private fun existing(alarm: Alarm = GYM, snoozeVisible: Boolean = true) = AlarmEditUiState(
        loading = false,
        isNew = false,
        initial = alarm,
        draft = alarm,
        snoozeVisible = snoozeVisible,
    )

    private fun new(snoozeVisible: Boolean = true) = AlarmEditUiState(
        loading = false,
        isNew = true,
        initial = NEW,
        draft = NEW,
        snoozeVisible = snoozeVisible,
    )

    /**
     * Экран с явными форматами. Снаружи — свой `BackHandler`, как у навигации: он получает Back, только если
     * редактор его не перехватил.
     */
    private fun show(
        initial: AlarmEditUiState = existing(),
        clockFormat: ClockFormat = ClockFormat(Locale.US, is24Hour = true),
        fontScale: Float? = null,
    ) {
        state = initial
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            BackHandler { outerBacks++ }
            CompositionLocalProvider(LocalDensity provides scaled) {
                BalarmTheme {
                    AlarmEditScreen(
                        state = state,
                        clockFormat = clockFormat,
                        weekdayFormat = WeekdayFormat.forLocale(clockFormat.locale),
                        onEvent = ::onEvent,
                        windowInsets = WindowInsets(0.dp),
                    )
                }
            }
        }
    }

    /** Как в приложении: форматы из конфигурации и системных настроек. */
    private fun showFromSystem(initial: AlarmEditUiState = existing()) {
        state = initial
        composeRule.setContent {
            BalarmTheme { AlarmEditScreen(state = state, onEvent = ::onEvent) }
        }
    }

    private fun onEvent(event: AlarmEditEvent) {
        events += event
        state = AlarmEditReducer.reduce(state, event)
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** Тап; элемент в прокручиваемой части сначала прокручивается в зону видимости. */
    private fun click(tag: String) {
        val scrollable = composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction()))
            .fetchSemanticsNodes().isNotEmpty()
        node(tag).apply { if (scrollable) performScrollTo() }.performClick()
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun text(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    // --- Snooze ---

    @Test
    fun `snooze section is shown when the flag is on`() {
        show(existing(snoozeVisible = true))

        val wide = formatTimeUntil(TimeUntil(0, 0, 5), Locale.US, wide = true)
        node(AlarmEditTestTags.SNOOZE_INTERVAL).performScrollTo().assertIsDisplayed()
            .assertContentDescriptionEquals("Snooze, $wide")
        node(AlarmEditTestTags.SNOOZE_LIMIT).performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasText("3 times") and hasAnyAncestor(hasTestTag(AlarmEditTestTags.SNOOZE_LIMIT)),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `snooze section is hidden when the flag is off`() {
        show(existing(snoozeVisible = false))

        node(AlarmEditTestTags.SNOOZE_INTERVAL).assertDoesNotExist()
        node(AlarmEditTestTags.SNOOZE_LIMIT).assertDoesNotExist()
        node(AlarmEditTestTags.TEST).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `snooze off hides the limit row and reads off`() {
        show(existing(GYM.copy(snooze = SnoozeSettings.DISABLED)))

        node(AlarmEditTestTags.SNOOZE_INTERVAL).performScrollTo().assertContentDescriptionEquals("Snooze, turned off")
        node(AlarmEditTestTags.SNOOZE_LIMIT).assertDoesNotExist()
    }

    @Test
    fun `interval dialog - choosing off sends the choice and the reducer closes the dialog`() {
        show()

        click(AlarmEditTestTags.SNOOZE_INTERVAL)
        node(SingleChoiceDialogTestTags.DIALOG).assertIsDisplayed()
        // «Выкл» + 7 стандартных; выбрано текущее 5 мин (индекс 3: off, 1, 3, 5).
        node(SingleChoiceDialogTestTags.option(3)).assertIsSelected()
        click(SingleChoiceDialogTestTags.option(0))

        assertThat(events).containsExactly(
            AlarmEditEvent.ShowSnoozeIntervalDialog,
            AlarmEditEvent.SnoozeIntervalSelected(null),
        ).inOrder()
        node(SingleChoiceDialogTestTags.DIALOG).assertDoesNotExist()
        node(AlarmEditTestTags.SNOOZE_LIMIT).assertDoesNotExist()
    }

    @Test
    fun `limit dialog - unlimited is the last option`() {
        show()

        click(AlarmEditTestTags.SNOOZE_LIMIT)
        composeRule.onNodeWithText("Unlimited").assertIsDisplayed()
        click(SingleChoiceDialogTestTags.option(SnoozeSettings.LIMIT_OPTIONS.lastIndex))

        assertThat(events.last()).isEqualTo(AlarmEditEvent.SnoozeLimitSelected(null))
        node(SingleChoiceDialogTestTags.DIALOG).assertDoesNotExist()
        composeRule.onNode(
            hasText("Unlimited") and hasAnyAncestor(hasTestTag(AlarmEditTestTags.SNOOZE_LIMIT)),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `cancel in a snooze dialog dismisses it`() {
        show()

        click(AlarmEditTestTags.SNOOZE_INTERVAL)
        click(SingleChoiceDialogTestTags.DISMISS)

        assertThat(events.last()).isEqualTo(AlarmEditEvent.DialogDismissed)
        node(SingleChoiceDialogTestTags.DIALOG).assertDoesNotExist()
    }

    // --- Метка ---

    @Test
    fun `label is limited to 40 characters, an emoji counts as one`() {
        show(new())
        val pasted = "😀".repeat(50)

        node(LabelFieldTestTags.FIELD).performScrollTo().performTextInput(pasted)
        composeRule.waitForIdle()

        assertThat(state.draft.label).isEqualTo("😀".repeat(Alarm.MAX_LABEL_LENGTH))
        composeRule.onNode(hasContentDescription("40 of 40 characters"), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("40/40", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `typing a label sends it and marks the editor dirty`() {
        show(new())

        node(LabelFieldTestTags.FIELD).performScrollTo().performTextInput("Gym")
        composeRule.waitForIdle()

        assertThat(events).containsExactly(AlarmEditEvent.LabelChanged("Gym"))
        assertThat(state.isDirty).isTrue()
        composeRule.onNode(hasContentDescription("3 of 40 characters"), useUnmergedTree = true).assertExists()
    }

    // --- Время и дни ---

    @Test
    fun `wheel change sends the new time`() {
        show()

        node(TimeWheelPickerTestTags.HOURS).performSemanticsAction(SemanticsActions.SetProgress) { it(13f) }
        composeRule.waitForIdle()

        assertThat(events).containsExactly(AlarmEditEvent.TimeChanged(LocalTime.of(13, 30)))
    }

    @Test
    fun `24-hour wheel has no AM-PM column, 12-hour has one`() {
        show(clockFormat = ClockFormat(Locale.US, is24Hour = true))
        node(TimeWheelPickerTestTags.PERIOD).assertDoesNotExist()
        node(TimeWheelPickerTestTags.HOURS).assert(stateIs("06"))
    }

    @Test
    fun `12-hour wheel shows the clock hour and AM-PM`() {
        show(existing(GYM.copy(time = LocalTime.of(18, 30))), clockFormat = ClockFormat(Locale.US, is24Hour = false))

        node(TimeWheelPickerTestTags.HOURS).assert(stateIs("6"))
        node(TimeWheelPickerTestTags.PERIOD).assertIsDisplayed().assert(stateIs("PM"))
        node(TimeWheelPickerTestTags.PERIOD).assertContentDescriptionEquals("AM or PM")
    }

    @Test
    fun `system 12-hour setting reaches the wheel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")

        showFromSystem()

        node(TimeWheelPickerTestTags.PERIOD).assertIsDisplayed()
    }

    @Test
    fun `system 24-hour setting reaches the wheel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")

        showFromSystem()

        node(TimeWheelPickerTestTags.PERIOD).assertDoesNotExist()
    }

    @Test
    fun `presets send the pressed preset, a second tap clears the days`() {
        show(new())

        click(PresetChipsTestTags.WEEKDAYS)
        assertThat(state.draft.repeatDays).isEqualTo(DayPreset.WEEKDAYS.days)
        click(PresetChipsTestTags.WEEKDAYS)

        assertThat(events).containsExactly(
            AlarmEditEvent.PresetSelected(DayPreset.WEEKDAYS),
            AlarmEditEvent.PresetSelected(DayPreset.WEEKDAYS),
        )
        assertThat(state.draft.repeatDays).isEmpty()
    }

    @Test
    fun `day chip toggles its day`() {
        show(new())

        click(DayChipsTestTags.chip(DayOfWeek.SUNDAY))

        assertThat(events).containsExactly(AlarmEditEvent.DayToggled(DayOfWeek.SUNDAY))
        click(PresetChipsTestTags.WEEKEND)
        assertThat(state.draft.repeatDays).isEqualTo(DayPreset.WEEKEND.days)
    }

    @Test
    fun `days follow the first day of the week of the locale`() {
        show(clockFormat = ClockFormat(Locale.US, is24Hour = true))

        node(DayChipsTestTags.ROW).performScrollTo()
        val sunday = node(DayChipsTestTags.chip(DayOfWeek.SUNDAY)).getBoundsInRoot()
        val monday = node(DayChipsTestTags.chip(DayOfWeek.MONDAY)).getBoundsInRoot()
        assertThat(sunday.left).isLessThan(monday.left)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun `day chips are at least 48dp wide on a 360dp screen`() {
        show()

        DayOfWeek.entries.forEach { day ->
            val bounds = node(DayChipsTestTags.chip(day)).performScrollTo().getBoundsInRoot()
            assertWithMessage("$day width").that(bounds.width).isAtLeast(BalarmDimens.MinTouch)
            assertWithMessage("$day height").that(bounds.height).isAtLeast(BalarmDimens.MinTouch)
        }
    }

    // --- Кнопки ---

    @Test
    fun `save sends save`() {
        show()

        click(AlarmEditTestTags.SAVE)

        assertThat(events).containsExactly(AlarmEditEvent.Save)
    }

    @Test
    fun `test sends test`() {
        show()

        click(AlarmEditTestTags.TEST)

        assertThat(events).containsExactly(AlarmEditEvent.Test)
    }

    @Test
    fun `delete asks for confirmation, confirm sends confirm delete`() {
        show()

        click(AlarmEditTestTags.DELETE)
        node(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Alarm 06:30 will be deleted.").assertIsDisplayed()
        click(ConfirmDialogTestTags.CONFIRM)

        assertThat(events).containsExactly(AlarmEditEvent.Delete, AlarmEditEvent.ConfirmDelete).inOrder()
    }

    @Test
    fun `cancel in the delete dialog does not delete`() {
        show()

        click(AlarmEditTestTags.DELETE)
        click(ConfirmDialogTestTags.DISMISS)

        assertThat(events).containsExactly(AlarmEditEvent.Delete, AlarmEditEvent.DialogDismissed).inOrder()
        node(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun `delete is offered only for an existing alarm`() {
        show(new())

        node(AlarmEditTestTags.DELETE).assertDoesNotExist()
        node(AlarmEditTestTags.TEST).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `while saving the buttons are disabled`() {
        show(existing().copy(saving = true))

        node(AlarmEditTestTags.SAVE).assertIsNotEnabled()
        node(AlarmEditTestTags.TEST).performScrollTo().assertIsNotEnabled()
        node(AlarmEditTestTags.DELETE).performScrollTo().assertIsNotEnabled()
        node(AlarmEditTestTags.SNOOZE_INTERVAL).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `while loading neither the form nor the buttons flash with defaults`() {
        show(existing().copy(loading = true))

        node(AlarmEditTestTags.TITLE).assertIsDisplayed()
        node(TimeWheelPickerTestTags.PICKER).assertDoesNotExist()
        node(LabelFieldTestTags.FIELD).assertDoesNotExist()
        node(AlarmEditTestTags.SAVE).assertDoesNotExist()
        node(AlarmEditTestTags.TEST).assertDoesNotExist()
    }

    // --- Back ---

    @Test
    fun `back without changes is left to the navigation`() {
        show()

        back()

        assertThat(events).isEmpty()
        assertThat(outerBacks).isEqualTo(1)
    }

    @Test
    fun `back with changes asks to discard`() {
        show()
        node(LabelFieldTestTags.FIELD).performScrollTo().performTextInput("!")
        composeRule.waitForIdle()

        back()

        assertThat(events.last()).isEqualTo(AlarmEditEvent.Back)
        assertThat(outerBacks).isEqualTo(0)
        node(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
    }

    @Test
    fun `discard dialog - confirm discards, dismiss keeps editing`() {
        val edited = existing().let { it.copy(draft = it.draft.copy(label = "Run")) }
        show(edited.copy(dialog = EditDialog.ConfirmDiscard))

        click(ConfirmDialogTestTags.DISMISS)
        assertThat(events).containsExactly(AlarmEditEvent.DialogDismissed)

        back()
        click(ConfirmDialogTestTags.CONFIRM)
        assertThat(events).containsExactly(
            AlarmEditEvent.DialogDismissed,
            AlarmEditEvent.Back,
            AlarmEditEvent.DiscardConfirmed,
        ).inOrder()
    }

    @Test
    fun `back while saving does not leave before the save result`() {
        show(existing().copy(saving = true))

        back()

        assertThat(events).containsExactly(AlarmEditEvent.Back)
        assertThat(outerBacks).isEqualTo(0)
    }

    // --- Тексты, a11y, раскладка ---

    @Test
    fun `title is a heading - new alarm vs alarm`() {
        show(new())
        node(AlarmEditTestTags.TITLE).assertTextEquals("New alarm")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `existing alarm title`() {
        show()
        node(AlarmEditTestTags.TITLE).assertTextEquals("Alarm")
    }

    @Test
    fun `english strings`() {
        show()

        composeRule.onNodeWithText("Save").assertIsDisplayed()
        composeRule.onNodeWithText("Test").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Weekdays").performScrollTo().assertIsDisplayed()
        node(TimeWheelPickerTestTags.HOURS).assertContentDescriptionEquals("Hours")
        node(DayChipsTestTags.chip(DayOfWeek.MONDAY)).assert(stateIs("Selected"))
        node(DayChipsTestTags.chip(DayOfWeek.SUNDAY)).assert(stateIs("Not selected"))
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian strings`() {
        show(existing(GYM.copy(label = "")), clockFormat = ClockFormat(Locale.forLanguageTag("ru-RU"), true))

        node(AlarmEditTestTags.TITLE).assertTextEquals("Будильник")
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Тест").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Удалить").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Будни").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Метка", useUnmergedTree = true).assertExists()
        composeRule.onNode(hasContentDescription("0 из 40 символов"), useUnmergedTree = true).assertExists()
        composeRule.onNode(
            hasText("3 раза") and hasAnyAncestor(hasTestTag(AlarmEditTestTags.SNOOZE_LIMIT)),
            useUnmergedTree = true,
        ).assertExists()
        node(TimeWheelPickerTestTags.HOURS).assertContentDescriptionEquals("Часы")
        // Неделя в RU — с понедельника.
        val monday = node(DayChipsTestTags.chip(DayOfWeek.MONDAY)).getBoundsInRoot()
        val sunday = node(DayChipsTestTags.chip(DayOfWeek.SUNDAY)).getBoundsInRoot()
        assertThat(monday.left).isLessThan(sunday.left)
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian dialogs`() {
        val edited = existing().let { it.copy(draft = it.draft.copy(label = "Бег")) }
        show(edited.copy(dialog = EditDialog.ConfirmDiscard), ClockFormat(Locale.forLanguageTag("ru-RU"), true))

        composeRule.onNodeWithText("Отменить изменения?").assertIsDisplayed()
        composeRule.onNodeWithText("Не сохранять").assertIsDisplayed()
        composeRule.onNodeWithText("Продолжить").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun `font scale 2 on a narrow screen - save stays on screen, the rest is reachable by scrolling`() {
        show(fontScale = 2f)

        val root = node(AlarmEditTestTags.ROOT).getBoundsInRoot()
        val save = node(AlarmEditTestTags.SAVE).assertIsDisplayed().getBoundsInRoot()
        assertThat(save.bottom).isAtMost(root.bottom)
        assertThat(save.height).isAtLeast(BalarmDimens.ButtonHeight)

        listOf(
            AlarmEditTestTags.SNOOZE_INTERVAL,
            AlarmEditTestTags.SNOOZE_LIMIT,
            AlarmEditTestTags.TEST,
            AlarmEditTestTags.DELETE,
        ).forEach { tag ->
            node(tag).performScrollTo().assertIsDisplayed()
            val bounds = node(tag).getBoundsInRoot()
            // Прокрученная кнопка не прячется под закреплённой «Сохранить».
            assertWithMessage(tag).that(bounds.bottom).isAtMost(save.top)
        }
        click(AlarmEditTestTags.DELETE)
        assertThat(events).containsExactly(AlarmEditEvent.Delete)
        node(AlarmEditTestTags.SAVE).assertIsEnabled()
    }

    @Test
    fun `delete dialog text uses the saved time, not the edited one`() {
        val edited = existing().let { it.copy(draft = it.draft.copy(time = LocalTime.of(9, 0))) }
        show(edited.copy(dialog = EditDialog.ConfirmDelete))

        composeRule.onNodeWithText("Alarm 06:30 will be deleted.").assertIsDisplayed()
        assertThat(text(R.string.alarm_edit_delete_title)).isEqualTo("Delete alarm?")
    }

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private companion object {
        val GYM = Alarm(
            id = AlarmId(1),
            time = LocalTime.of(6, 30),
            repeatDays = DayPreset.WEEKDAYS.days,
            label = "Gym",
        )
        val NEW = Alarm(time = LocalTime.of(7, 0))
    }
}
