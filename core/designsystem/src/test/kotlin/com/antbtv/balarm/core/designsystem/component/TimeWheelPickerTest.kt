package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class TimeWheelPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val haptics = mutableListOf<HapticFeedbackType>()
    private val changes = mutableListOf<Pair<Int, Int>>()
    private var hour by mutableIntStateOf(0)
    private var minute by mutableIntStateOf(0)
    private var is24Hour by mutableStateOf(true)

    private val fakeHaptic = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }

    /** Колесо как в редакторе: колбэк возвращает значение в состояние (управляемый компонент). */
    private fun showPicker(initialHour: Int, initialMinute: Int, twentyFour: Boolean = true, fontScale: Float? = null) {
        hour = initialHour
        minute = initialMinute
        is24Hour = twentyFour
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled, LocalHapticFeedback provides fakeHaptic) {
                BalarmTheme {
                    Box(Modifier.padding(BalarmDimens.ScreenPadding)) {
                        TimeWheelPicker(
                            hour = hour,
                            minute = minute,
                            onTimeChange = { h, m ->
                                changes += h to m
                                hour = h
                                minute = m
                            },
                            is24Hour = is24Hour,
                            hoursLabel = HOURS,
                            minutesLabel = MINUTES,
                            periodLabel = PERIOD,
                            amLabel = AM,
                            pmLabel = PM,
                            increaseLabel = INCREASE,
                            decreaseLabel = DECREASE,
                        )
                    }
                }
            }
        }
    }

    private fun column(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun SemanticsNodeInteraction.assertState(state: String): SemanticsNodeInteraction =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    private fun SemanticsNodeInteraction.setProgress(value: Float) {
        performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { actions.single { it.label == label }.action() }
        composeRule.waitForIdle()
    }

    @Test
    fun `initial value is shown with TalkBack names and states and does not vibrate`() {
        showPicker(7, 30)

        column(TimeWheelPickerTestTags.HOURS).assertContentDescriptionEquals(HOURS).assertState("07")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        column(TimeWheelPickerTestTags.MINUTES).assertContentDescriptionEquals(MINUTES).assertState("30")
        column(TimeWheelPickerTestTags.PERIOD).assertDoesNotExist()
        assertThat(haptics).isEmpty()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `scrolling one row reports the time and ticks once`() {
        showPicker(7, 30)

        column(TimeWheelPickerTestTags.MINUTES)
            .performScrollToIndex(valueToStartIndex(30, MINUTES_IN_HOUR, infinite = true) + 1)
        composeRule.waitForIdle()

        assertThat(changes).containsExactly(7 to 31)
        assertThat(haptics).containsExactly(HapticFeedbackType.SegmentFrequentTick)
        column(TimeWheelPickerTestTags.MINUTES).assertState("31")
    }

    @Test
    fun `hour wraps from 23 to 0 and minute from 59 to 0`() {
        showPicker(23, 59)

        column(TimeWheelPickerTestTags.HOURS)
            .performScrollToIndex(valueToStartIndex(23, HOURS_24, infinite = true) + 1)
        composeRule.waitForIdle()
        column(TimeWheelPickerTestTags.MINUTES)
            .performScrollToIndex(valueToStartIndex(59, MINUTES_IN_HOUR, infinite = true) + 1)
        composeRule.waitForIdle()

        assertThat(changes).containsExactly(0 to 59, 0 to 0).inOrder()
        column(TimeWheelPickerTestTags.HOURS).assertState("00")
        column(TimeWheelPickerTestTags.MINUTES).assertState("00")
    }

    @Test
    fun `set progress selects the value`() {
        showPicker(7, 30)

        column(TimeWheelPickerTestTags.HOURS).setProgress(13f)
        column(TimeWheelPickerTestTags.MINUTES).setProgress(5f)

        assertThat(changes).containsExactly(13 to 30, 13 to 5).inOrder()
        column(TimeWheelPickerTestTags.HOURS).assertState("13")
        column(TimeWheelPickerTestTags.MINUTES).assertState("05")
    }

    @Test
    fun `increase and decrease custom actions step and wrap`() {
        showPicker(0, 59)

        column(TimeWheelPickerTestTags.MINUTES).customAction(INCREASE)
        column(TimeWheelPickerTestTags.HOURS).customAction(DECREASE)

        assertThat(changes).containsExactly(0 to 0, 23 to 0).inOrder()
        assertThat(haptics).hasSize(2)
    }

    @Test
    fun `external change scrolls the wheels without callback and haptic`() {
        showPicker(7, 30)

        hour = 22
        minute = 0
        composeRule.waitForIdle()

        column(TimeWheelPickerTestTags.HOURS).assertState("22")
        column(TimeWheelPickerTestTags.MINUTES).assertState("00")
        assertThat(changes).isEmpty()
        assertThat(haptics).isEmpty()

        // После внешней смены пользовательский шаг идёт от нового значения.
        column(TimeWheelPickerTestTags.MINUTES).customAction(DECREASE)
        assertThat(changes).containsExactly(22 to 59)
    }

    @Test
    fun `12h shows clock hour and period and converts back to 24h`() {
        showPicker(13, 15, twentyFour = false)

        column(TimeWheelPickerTestTags.HOURS).assertState("1")
        column(TimeWheelPickerTestTags.PERIOD).assertContentDescriptionEquals(PERIOD).assertState(PM)

        // «12» в PM — полдень.
        column(TimeWheelPickerTestTags.HOURS).setProgress(0f)
        column(TimeWheelPickerTestTags.HOURS).assertState("12")
        // AM при «12» — полночь.
        column(TimeWheelPickerTestTags.PERIOD).customAction(DECREASE)
        column(TimeWheelPickerTestTags.PERIOD).assertState(AM)

        assertThat(changes).containsExactly(12 to 15, 0 to 15).inOrder()
    }

    @Test
    fun `12h hour 11 to 12 does not flip AM PM`() {
        showPicker(11, 0, twentyFour = false)

        column(TimeWheelPickerTestTags.HOURS).customAction(INCREASE)

        assertThat(changes).containsExactly(0 to 0)
        column(TimeWheelPickerTestTags.PERIOD).assertState(AM)
    }

    @Test
    fun `period column has two values and a TalkBack step moves it`() {
        showPicker(9, 0, twentyFour = false)

        // Шаг TalkBack у колонки из двух значений — дробный (0.05): должен всё равно переключить на PM.
        column(TimeWheelPickerTestTags.PERIOD).setProgress(0.05f)
        assertThat(changes).containsExactly(21 to 0)

        val actions = column(TimeWheelPickerTestTags.PERIOD).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        var result = true
        composeRule.runOnIdle { result = actions.single { it.label == INCREASE }.action() }
        assertThat(result).isFalse()
    }

    @Test
    fun `switching 24h to 12h keeps the time`() {
        showPicker(18, 45)

        is24Hour = false
        composeRule.waitForIdle()

        column(TimeWheelPickerTestTags.HOURS).assertState("6")
        column(TimeWheelPickerTestTags.PERIOD).assertState(PM)
        assertThat(changes).isEmpty()
        assertThat(haptics).isEmpty()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальные метрики шрифта
    fun `font scale 2 on 360dp shows three rows and all columns fit`() {
        showPicker(12, 59, twentyFour = false, fontScale = 2f)

        val picker = composeRule.onNodeWithTag(TimeWheelPickerTestTags.PICKER).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val hours = column(TimeWheelPickerTestTags.HOURS).assertIsDisplayed().getUnclippedBoundsInRoot()
        val minutes = column(TimeWheelPickerTestTags.MINUTES).assertIsDisplayed().getUnclippedBoundsInRoot()
        val period = column(TimeWheelPickerTestTags.PERIOD).assertIsDisplayed().getUnclippedBoundsInRoot()

        // Влезает в 360dp минус отступы экрана.
        assertThat(period.right).isAtMost(360.dp - BalarmDimens.ScreenPadding)
        assertThat(hours.left).isAtLeast(picker.left)
        assertThat(minutes.left).isAtLeast(hours.right)
        assertThat(period.left).isAtLeast(minutes.right)
        // Три строки: высота колонки = 3 высоты строки, строка не ниже зоны тапа.
        val rowHeight = hours.height / VISIBLE_ROWS_LARGE_FONT
        assertThat(rowHeight).isAtLeast(BalarmDimens.MinTouch)
        // Выбранные значения видны (колонки не пустые).
        composeRule.onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("59", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(PM, useUnmergedTree = true).assertIsDisplayed()
        // Соседние строки тоже видны: 3 строки на колонку.
        composeRule.onNodeWithText("11", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `default font scale shows five rows`() {
        showPicker(7, 30)

        val hours = column(TimeWheelPickerTestTags.HOURS).getUnclippedBoundsInRoot()
        val rowHeight = hours.height / VISIBLE_ROWS_DEFAULT
        assertThat(rowHeight).isAtLeast(BalarmDimens.MinTouch)
        listOf("05", "06", "07", "08", "09").forEach {
            composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private companion object {
        const val HOURS = "Hours"
        const val MINUTES = "Minutes"
        const val PERIOD = "AM or PM"
        const val AM = "AM"
        const val PM = "PM"
        const val INCREASE = "Increase"
        const val DECREASE = "Decrease"
    }
}
