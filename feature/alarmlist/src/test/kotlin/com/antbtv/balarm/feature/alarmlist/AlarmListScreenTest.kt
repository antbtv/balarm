package com.antbtv.balarm.feature.alarmlist

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.AlarmCardTestTags
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.component.NextAlarmHeaderTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.formatTimeUntil
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class AlarmListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<AlarmListEvent>()
    private val opened = mutableListOf<AlarmId>()
    private var added = 0
    private var healthOpened = 0
    private var background = Color.Unspecified

    private val gym = AlarmItemUi(
        id = AlarmId(1),
        time = LocalTime.of(6, 30),
        label = "Gym",
        repeatDays = WEEKDAYS,
        active = true,
        subtitle = AlarmSubtitle.Tomorrow,
    )
    private val late = AlarmItemUi(
        id = AlarmId(2),
        time = LocalTime.of(23, 59),
        label = "",
        repeatDays = emptySet(),
        active = false,
        subtitle = null,
    )
    private val data = AlarmListUiState(
        loading = false,
        alarms = listOf(gym, late),
        nextIn = TimeUntil(days = 0, hours = 7, minutes = 12),
    )

    /** Экран с явными форматами (EN, 24 ч по умолчанию). */
    private fun show(
        state: AlarmListUiState = data,
        clockFormat: ClockFormat = ClockFormat(Locale.US, is24Hour = true),
        fontScale: Float? = null,
        windowInsets: WindowInsets? = null,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled) {
                BalarmTheme {
                    background = BalarmTheme.colors.background
                    AlarmListScreen(
                        state = state,
                        clockFormat = clockFormat,
                        weekdayFormat = WeekdayFormat.forLocale(clockFormat.locale),
                        onEvent = { events += it },
                        onAddAlarm = { added++ },
                        onOpenAlarm = { opened += it },
                        onOpenHealth = { healthOpened++ },
                        windowInsets = windowInsets ?: WindowInsets.safeDrawing,
                    )
                }
            }
        }
    }

    /** Экран как в приложении: форматы из конфигурации и системных настроек. */
    private fun showFromSystem(state: AlarmListUiState = data) {
        composeRule.setContent {
            BalarmTheme {
                AlarmListScreen(
                    state = state,
                    onEvent = { events += it },
                    onAddAlarm = { added++ },
                    onOpenAlarm = { opened += it },
                    onOpenHealth = { healthOpened++ },
                )
            }
        }
    }

    private fun card(id: AlarmId): SemanticsNodeInteraction = composeRule.onNode(
        hasTestTag(AlarmCardTestTags.CARD) and hasAnyAncestor(hasTestTag(AlarmListTestTags.card(id))),
    )

    private fun switch(id: AlarmId): SemanticsNodeInteraction = composeRule.onNode(
        hasTestTag(AlarmCardTestTags.SWITCH) and hasAnyAncestor(hasTestTag(AlarmListTestTags.card(id))),
    )

    private fun header(): SemanticsNodeInteraction = composeRule.onNodeWithTag(NextAlarmHeaderTestTags.HEADER)

    // --- Тумблер ---

    @Test
    fun `switch of an active alarm asks to disable it`() {
        show()

        switch(gym.id).assertIsOn().performClick()

        assertThat(events).containsExactly(AlarmListEvent.Toggle(gym.id, enabled = false))
        assertThat(opened).isEmpty()
    }

    @Test
    fun `switch of an inactive alarm asks to enable it and is not optimistic`() {
        show()

        switch(late.id).assertIsOff().performClick()

        assertThat(events).containsExactly(AlarmListEvent.Toggle(late.id, enabled = true))
        assertThat(opened).isEmpty()
        // Состояние не поменялось — тумблер ждёт БД.
        switch(late.id).assertIsOff()
    }

    // --- Навигация ---

    @Test
    fun `tap on a card opens the alarm`() {
        show()

        card(late.id).performClick()

        assertThat(opened).containsExactly(late.id)
        assertThat(events).isEmpty()
    }

    @Test
    fun `fab adds an alarm`() {
        show()

        composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertIsDisplayed().performClick()

        assertThat(added).isEqualTo(1)
        assertThat(opened).isEmpty()
    }

    // --- Удаление ---

    @Test
    fun `long tap - menu - confirm deletes the alarm`() {
        show()

        card(gym.id).performTouchInput { longClick() }
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).assertDoesNotExist()
        composeRule.onNodeWithText("Alarm 06:30 will be deleted.").assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM).performClick()

        assertThat(events).containsExactly(AlarmListEvent.Delete(gym.id))
        assertThat(opened).isEmpty()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun `cancel in the dialog does not delete`() {
        show()

        card(gym.id).performTouchInput { longClick() }
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DISMISS).performClick()

        assertThat(events).isEmpty()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun `long tap alone does not delete or open`() {
        show()

        card(late.id).performTouchInput { longClick() }

        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
        assertThat(events).isEmpty()
        assertThat(opened).isEmpty()
    }

    // --- Шапка и состояния ---

    @Test
    fun `header counts down to the next alarm, TalkBack reads the wide form`() {
        show()

        val wide = formatTimeUntil(data.nextIn!!, Locale.US, wide = true)
        header().assertIsDisplayed().assertContentDescriptionEquals("Next alarm in $wide")
    }

    @Test
    fun `header says no active alarms when nothing is scheduled`() {
        show(data.copy(nextIn = null))

        header().assertContentDescriptionEquals("No active alarms")
        composeRule.onNodeWithTag(AlarmListTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun `empty list shows the empty state and the header`() {
        show(AlarmListUiState(loading = false))

        composeRule.onNodeWithTag(AlarmListTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No alarms yet").assertIsDisplayed()
        header().assertContentDescriptionEquals("No active alarms")
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertIsDisplayed()
    }

    @Test
    fun `while loading neither empty state nor header flash`() {
        show(AlarmListUiState(loading = true))

        composeRule.onNodeWithTag(AlarmListTestTags.EMPTY).assertDoesNotExist()
        header().assertDoesNotExist()
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertIsDisplayed()
    }

    // --- TalkBack, 12/24 ч ---

    @Test
    fun `card and switch are described for TalkBack`() {
        show()

        card(gym.id).assertContentDescriptionEquals("Alarm 06:30, Gym, on weekdays, Tomorrow, on")
        switch(gym.id).assertContentDescriptionEquals("Alarm 06:30, Gym")
        card(late.id).assertContentDescriptionEquals("Alarm 23:59, once, off")
        card(gym.id).assert(hasClickLabels(click = "Edit", longClick = "Show actions"))
    }

    @Test
    fun `12-hour clock shows the AM-PM time`() {
        val format = ClockFormat(Locale.US, is24Hour = false)
        show(clockFormat = format)

        card(late.id).assertContentDescriptionEquals("Alarm ${format.time(late.time)}, once, off")
        assertThat(format.time(late.time)).contains("PM")
    }

    @Test
    fun `system 12-hour setting reaches the screen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")

        showFromSystem()

        val expected = ClockFormat(Locale.US, is24Hour = false).time(gym.time)
        assertThat(expected).contains("AM")
        switch(gym.id).assertContentDescriptionEquals("Alarm $expected, Gym")
    }

    @Test
    fun `system 24-hour setting reaches the screen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")

        showFromSystem()

        switch(late.id).assertContentDescriptionEquals("Alarm 23:59")
    }

    @Test
    fun `snoozed subtitle uses the clock format`() {
        val snoozed = gym.copy(repeatDays = emptySet(), subtitle = AlarmSubtitle.SnoozedUntil(LocalTime.of(6, 40)))
        show(data.copy(alarms = listOf(snoozed)))

        card(gym.id).assertContentDescriptionEquals("Alarm 06:30, Gym, once, Snoozed until 06:40, on")
    }

    // --- Локали ---

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian strings`() {
        Settings.System.putString(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.System.TIME_12_24,
            "24",
        )
        showFromSystem()

        val wide = formatTimeUntil(data.nextIn!!, Locale.forLanguageTag("ru-RU"), wide = true)
        header().assertContentDescriptionEquals("Следующий будильник через $wide")
        card(gym.id).assertContentDescriptionEquals("Будильник 06:30, Gym, по будням, Завтра, включён")
        card(late.id).assertContentDescriptionEquals("Будильник 23:59, однократно, выключен")
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertContentDescriptionEquals("Добавить будильник")

        card(gym.id).performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Удалить будильник?").assertIsDisplayed()
        composeRule.onNodeWithText("Будильник 06:30 будет удалён.").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian empty state`() {
        showFromSystem(AlarmListUiState(loading = false))

        header().assertContentDescriptionEquals("Нет активных будильников")
        composeRule.onNodeWithText("Пока нет будильников").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `english strings`() {
        showFromSystem(AlarmListUiState(loading = false))

        composeRule.onNodeWithText("No alarms yet").assertIsDisplayed()
        composeRule.onNodeWithText("Tap + to add your first alarm").assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertContentDescriptionEquals("Add alarm")
    }

    // --- Состояние меню и диалога ---

    @Test
    fun `open menu and delete dialog survive recreation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { ScreenUnderTest(data) }

        card(gym.id).performTouchInput { longClick() }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).assertIsDisplayed().performClick()
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Alarm 06:30 will be deleted.").assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM).performClick()
        assertThat(events).containsExactly(AlarmListEvent.Delete(gym.id))
    }

    @Test
    fun `dialog disappears when its alarm is gone and does not come back`() {
        var state by mutableStateOf(data)
        composeRule.setContent { ScreenUnderTest(state) }
        card(gym.id).performTouchInput { longClick() }
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()

        state = data.copy(alarms = listOf(late))
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()

        // Тот же id вернулся (например, отмена удаления в будущем) — устаревший диалог не всплывает.
        state = data
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
        card(gym.id).assertIsDisplayed()
        assertThat(events).isEmpty()
    }

    @Test
    fun `stale dialog is kept while the list is reloading`() {
        var state by mutableStateOf(data)
        composeRule.setContent { ScreenUnderTest(state) }
        card(gym.id).performTouchInput { longClick() }
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).performClick()

        state = AlarmListUiState(loading = true)
        state = data

        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
    }

    // --- Крупный шрифт, узкий экран ---

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `font scale 2 on a narrow screen - fab does not cover the last card`() {
        val alarms = List(6) { i ->
            gym.copy(id = AlarmId(i + 1L), label = "A very long label that does not fit into a single line, #$i")
        }
        show(data.copy(alarms = alarms), fontScale = 2f)

        // Индекс 0 — шапка, карточки — 1..6.
        composeRule.onNodeWithTag(AlarmListTestTags.LIST).performScrollToIndex(alarms.size)

        val lastCard = composeRule.onNodeWithTag(AlarmListTestTags.card(alarms.last().id))
            .assertIsDisplayed()
            .getBoundsInRoot()
        val fab = composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertIsDisplayed().getBoundsInRoot()
        assertThat(lastCard.bottom.value).isAtMost(fab.top.value)

        switch(alarms.last().id).performClick()
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).performClick()
        assertThat(events).containsExactly(AlarmListEvent.Toggle(alarms.last().id, enabled = false))
        assertThat(added).isEqualTo(1)
    }

    // --- Подложки под системными барами (edge-to-edge) ---

    private fun manyAlarms(count: Int = 10) = List(count) { i ->
        gym.copy(id = AlarmId(i + 1L), label = "A long label that wraps onto the second line of the card, #$i")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `scrims cover the status bar and the navigation bar with a fade`() {
        show(data.copy(alarms = manyAlarms()), windowInsets = BARS)

        val root = composeRule.onNodeWithTag(AlarmListTestTags.ROOT).getBoundsInRoot()
        val top = composeRule.onNodeWithTag(AlarmListTestTags.TOP_SCRIM).getBoundsInRoot()
        val bottom = composeRule.onNodeWithTag(AlarmListTestTags.BOTTOM_SCRIM).getBoundsInRoot()
        assertDp(top.top, root.top)
        assertDp(top.height, STATUS_BAR + BalarmDimens.SystemBarScrimFade)
        assertDp(top.width, root.width)
        assertDp(bottom.bottom, root.bottom)
        assertDp(bottom.height, NAVIGATION_BAR + BalarmDimens.SystemBarScrimFade)
        assertDp(bottom.width, root.width)
    }

    private fun assertDp(actual: Dp, expected: Dp) {
        assertThat(actual.value).isWithin(DP_TOLERANCE).of(expected.value)
    }

    // Регрессия смока 2: при прокрутке текст карточек ложился на время и иконки прозрачного статус-бара.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // реальная отрисовка для captureToImage
    fun `cards scrolled under the bars are hidden by the scrims`() {
        val alarms = manyAlarms()
        show(data.copy(alarms = alarms), windowInsets = BARS)
        // Индекс 0 — шапка; после прокрутки к третьей карточке вторая частично уходит под статус-бар.
        composeRule.onNodeWithTag(AlarmListTestTags.LIST).performScrollToIndex(3)

        val root = composeRule.onNodeWithTag(AlarmListTestTags.ROOT).getBoundsInRoot()
        val probeX = (root.left + root.right) / 2
        // Точки у самой кромки — там, где время/иконки статус-бара и полоска жестов.
        val topProbe = root.top + EDGE_PROBE
        val bottomProbe = root.bottom - EDGE_PROBE
        // Предусловие: под обеими точками действительно лежат карточки (иначе тест ничего не проверяет).
        assertThat(cardIdAt(alarms, probeX, topProbe)).isNotNull()
        assertThat(cardIdAt(alarms, probeX, bottomProbe)).isNotNull()

        val image = composeRule.onNodeWithTag(AlarmListTestTags.ROOT).captureToImage().toPixelMap()
        val density = composeRule.density
        fun pixel(x: Dp, y: Dp): Color = with(density) { image[x.roundToPx(), y.roundToPx() - 1] }
        assertColor(pixel(probeX, topProbe), background)
        assertColor(pixel(probeX, bottomProbe), background)
    }

    /** Id карточки, видимая часть которой лежит в точке (границы — обрезанные по видимой области), или `null`. */
    private fun cardIdAt(alarms: List<AlarmItemUi>, x: Dp, y: Dp): AlarmId? = alarms.firstOrNull { item ->
        val node = composeRule.onAllNodesWithTag(AlarmListTestTags.card(item.id)).fetchSemanticsNodes()
        node.isNotEmpty() && composeRule.onNodeWithTag(AlarmListTestTags.card(item.id)).getBoundsInRoot().let {
            x in it.left..it.right && y in it.top..it.bottom
        }
    }?.id

    private fun assertColor(actual: Color, expected: Color) {
        val channels = listOf(
            actual.red to expected.red,
            actual.green to expected.green,
            actual.blue to expected.blue,
        )
        channels.forEach { (a, e) -> assertWithMessage("$actual vs $expected").that(a).isWithin(COLOR_TOLERANCE).of(e) }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `with system bars the last card is above the fab and the bottom scrim`() {
        val alarms = manyAlarms()
        show(data.copy(alarms = alarms), fontScale = 2f, windowInsets = BARS)

        composeRule.onNodeWithTag(AlarmListTestTags.LIST).performScrollToIndex(alarms.size)

        val root = composeRule.onNodeWithTag(AlarmListTestTags.ROOT).getBoundsInRoot()
        val lastCard = composeRule.onNodeWithTag(AlarmListTestTags.card(alarms.last().id))
            .assertIsDisplayed()
            .getBoundsInRoot()
        val fab = composeRule.onNodeWithTag(AlarmListTestTags.FAB).assertIsDisplayed().getBoundsInRoot()
        val scrim = composeRule.onNodeWithTag(AlarmListTestTags.BOTTOM_SCRIM).getBoundsInRoot()
        assertThat(lastCard.bottom.value).isAtMost(fab.top.value)
        assertThat(lastCard.bottom.value).isAtMost(scrim.top.value)
        assertThat(fab.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `taps on cards under the scrims go through to the cards`() {
        val alarms = manyAlarms()
        show(data.copy(alarms = alarms), windowInsets = BARS)
        composeRule.onNodeWithTag(AlarmListTestTags.LIST).performScrollToIndex(3)

        val root = composeRule.onNodeWithTag(AlarmListTestTags.ROOT).getBoundsInRoot()
        val x = (root.left + root.right) / 2 // середина: не переключатель и не FAB
        listOf(root.top + EDGE_PROBE, root.bottom - EDGE_PROBE).forEach { y ->
            // Предусловие: точка внутри подложки, и под ней карточка.
            val scrim = if (y <
                (root.top + root.bottom) / 2
            ) {
                AlarmListTestTags.TOP_SCRIM
            } else {
                AlarmListTestTags.BOTTOM_SCRIM
            }
            val scrimBounds = composeRule.onNodeWithTag(scrim).getBoundsInRoot()
            assertThat(y in scrimBounds.top..scrimBounds.bottom).isTrue()
            val id = checkNotNull(cardIdAt(alarms, x, y)) { "no card under the $scrim at $y" }
            opened.clear()

            composeRule.onNodeWithTag(AlarmListTestTags.ROOT).performTouchInput {
                click(Offset(x.toPx(), y.toPx()))
            }

            assertThat(opened).containsExactly(id)
        }
    }

    @Composable
    private fun ScreenUnderTest(state: AlarmListUiState) {
        BalarmTheme {
            AlarmListScreen(
                state = state,
                clockFormat = ClockFormat(Locale.US, is24Hour = true),
                weekdayFormat = WeekdayFormat.forLocale(Locale.US),
                onEvent = { events += it },
                onAddAlarm = { added++ },
                onOpenAlarm = { opened += it },
                onOpenHealth = { healthOpened++ },
            )
        }
    }

    private companion object {
        val STATUS_BAR = 24.dp
        val NAVIGATION_BAR = 48.dp
        val BARS = WindowInsets(top = STATUS_BAR, bottom = NAVIGATION_BAR)
        const val COLOR_TOLERANCE = 0.01f
        const val DP_TOLERANCE = 0.5f
        val EDGE_PROBE = 8.dp

        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        fun hasClickLabels(click: String, longClick: String) =
            SemanticsMatcher("click '$click', long click '$longClick'") {
                val config = it.config
                config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == click &&
                    config.getOrElseNullable(SemanticsActions.OnLongClick) { null }?.label == longClick
            }
    }

    @Test
    fun `health banner is shown on a warning, opens the health screen and hides otherwise`() {
        show(data.copy(healthWarning = true))

        composeRule.onNodeWithTag(AlarmListTestTags.HEALTH_BANNER).assertIsDisplayed().performClick()
        assertThat(healthOpened).isEqualTo(1)
    }

    @Test
    fun `no health banner without a warning`() {
        show(data)

        composeRule.onAllNodesWithTag(AlarmListTestTags.HEALTH_BANNER).assertCountEquals(0)
    }

    @Test
    fun `banner is shown even when there are no alarms`() {
        show(AlarmListUiState(loading = false, healthWarning = true))

        composeRule.onNodeWithTag(AlarmListTestTags.HEALTH_BANNER).assertIsDisplayed()
    }

    @Test
    fun `an unscheduled alarm says so on its card`() {
        show(data.copy(alarms = listOf(gym.copy(subtitle = AlarmSubtitle.NotScheduled))))

        card(gym.id).assertContentDescriptionEquals("Alarm 06:30, Gym, on weekdays, Not scheduled, on")
    }
}
