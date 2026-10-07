package com.antbtv.balarm.feature.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.HealthStatusRowTestTags
import com.antbtv.balarm.core.designsystem.component.HealthStatusUi
import com.antbtv.balarm.core.designsystem.component.TopBarTestTags
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.permissions.R as PermissionsR
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class HealthScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<HealthEvent>()
    private var closed = 0
    private var outerBacks = 0

    private val problems = HealthUiState(
        loading = false,
        items = HealthItem.entries.map { item ->
            val status = when (item) {
                HealthItem.NOTIFICATIONS, HealthItem.SCHEDULING -> HealthStatus.PROBLEM
                HealthItem.OEM_BACKGROUND -> HealthStatus.UNCONFIRMED
                else -> HealthStatus.OK
            }
            HealthItemUi(item, status)
        },
        unscheduledAlarms = 2,
    )

    private var state by mutableStateOf(problems)

    /** Снаружи — свой `BackHandler`, как у навигации: получает Back, только если экран его не перехватил. */
    private fun show(initial: HealthUiState = problems, fontScale: Float? = null) {
        state = initial
        composeRule.setContent {
            BackHandler { outerBacks++ }
            TestTheme(fontScale) {
                HealthScreen(
                    state = state,
                    onEvent = { events += it },
                    onClose = { closed++ },
                    windowInsets = WindowInsets(0.dp),
                )
            }
        }
    }

    private fun card(item: HealthItem): SemanticsNodeInteraction {
        composeRule.onNodeWithTag(HealthTestTags.LIST).performScrollToNode(hasTestTag(HealthTestTags.item(item)))
        return composeRule.onNodeWithTag(HealthTestTags.item(item))
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        composeRule.onNodeWithTag(HealthTestTags.LIST).performScrollToNode(hasTestTag(tag))
        return composeRule.onNodeWithTag(tag)
    }

    private fun action(item: HealthItem): SemanticsNodeInteraction {
        card(item)
        return composeRule.onNode(
            hasTestTag(HealthStatusRowTestTags.ACTION) and hasAnyAncestor(hasTestTag(HealthTestTags.item(item))),
        )
    }

    private fun actionCount(item: HealthItem): Int {
        card(item)
        return composeRule.onAllNodes(
            hasTestTag(HealthStatusRowTestTags.ACTION) and hasAnyAncestor(hasTestTag(HealthTestTags.item(item))),
        ).fetchSemanticsNodes().size
    }

    private fun text(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `every health item is shown in order`() {
        show()

        HealthItem.entries.forEach { card(it).assertIsDisplayed() }
        scrollTo(HealthTestTags.TEST_ALARM).assertIsDisplayed()
    }

    @Test
    fun `loading shows only the top bar`() {
        show(HealthUiState())

        composeRule.onNodeWithTag(TopBarTestTags.TITLE).assertIsDisplayed()
        composeRule.onAllNodesWithTag(HealthTestTags.LIST).assertCountEquals(0)
    }

    @Test
    fun `fix is shown only for problems and sends Fix`() {
        show()

        assertThat(actionCount(HealthItem.EXACT_ALARMS)).isEqualTo(0)
        action(HealthItem.NOTIFICATIONS).performClick()

        assertThat(events).containsExactly(HealthEvent.Fix(HealthItem.NOTIFICATIONS))
    }

    @Test
    fun `unconfirmed oem item opens settings`() {
        show()

        action(HealthItem.OEM_BACKGROUND).performClick()

        assertThat(events).containsExactly(HealthEvent.Fix(HealthItem.OEM_BACKGROUND))
    }

    @Test
    fun `retry scheduling sends RetryScheduling and shows unscheduled count`() {
        show()

        card(HealthItem.SCHEDULING)
        val count = text(R.string.health_unscheduled_count, 2)
        composeRule.onNode(hasText(count, substring = true), useUnmergedTree = true)
            .assertExists()
        action(HealthItem.SCHEDULING).assertIsEnabled().performClick()

        assertThat(events).containsExactly(HealthEvent.RetryScheduling)
    }

    @Test
    fun `retry is disabled while retrying`() {
        show(problems.copy(retrying = true))

        action(HealthItem.SCHEDULING).assertIsNotEnabled().performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `test alarm sends ScheduleTest`() {
        show()

        scrollTo(HealthTestTags.TEST_ALARM).performClick()

        assertThat(events).containsExactly(HealthEvent.ScheduleTest)
    }

    @Test
    fun `oem checkbox confirms and unconfirms`() {
        show()

        card(HealthItem.OEM_BACKGROUND)
        scrollTo(HealthTestTags.OEM_CONFIRM).assertIsOff().performClick()
        assertThat(events).containsExactly(HealthEvent.SetOemConfirmed(true))

        events.clear()
        state = problems.copy(
            items = problems.items.map {
                if (it.item == HealthItem.OEM_BACKGROUND) it.copy(status = HealthStatus.OK) else it
            },
        )
        composeRule.waitForIdle()
        scrollTo(HealthTestTags.OEM_CONFIRM).assertIsOn().performClick()
        assertThat(events).containsExactly(HealthEvent.SetOemConfirmed(false))
        // ✅ — «Открыть настройки» больше не нужна.
        assertThat(actionCount(HealthItem.OEM_BACKGROUND)).isEqualTo(0)
    }

    @Test
    fun `oem guide opens the browser`() {
        show()

        card(HealthItem.OEM_BACKGROUND)
        scrollTo(HealthTestTags.OEM_GUIDE).performClick()

        val intent = shadowOf(composeRule.activity).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.dataString).isEqualTo(OEM_GUIDE_URL)
        assertThat(events).isEmpty()
    }

    @Test
    fun `talkback describes back and which item is fixed`() {
        show()

        composeRule.onNodeWithTag(TopBarTestTags.BACK)
            .assertContentDescriptionEquals(text(R.string.settings_back))
        val title = composeRule.activity.getString(PermissionsR.string.health_notifications_title)
        action(HealthItem.NOTIFICATIONS).assertContentDescriptionEquals("${text(R.string.health_fix)}: $title")
    }

    @Test
    fun `top bar back closes, system back is left to navigation`() {
        show()

        composeRule.onNodeWithTag(TopBarTestTags.BACK).performClick()
        assertThat(closed).isEqualTo(1)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertThat(outerBacks).isEqualTo(1)
        assertThat(closed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `font scale 2 on 360dp does not clip text`() {
        show(fontScale = 2f)

        composeRule.assertNoTextOverflow()
        HealthItem.entries.forEach {
            card(it).assertIsDisplayed()
            composeRule.assertNoTextOverflow()
        }
        scrollTo(HealthTestTags.TEST_ALARM).assertIsDisplayed()
        composeRule.assertNoTextOverflow()
    }

    @Test
    fun `effect texts`() {
        val context = composeRule.activity
        val format = ClockFormat(Locale.US, is24Hour = true)
        val zone = ZoneId.of("Europe/Moscow")
        val at = Instant.parse("2026-09-28T02:01:00Z") // 05:01 MSK

        assertThat(healthEffectText(context, HealthEffect.TestScheduled(at), format, zone))
            .isEqualTo(context.getString(R.string.health_test_scheduled, "05:01"))
        assertThat(healthEffectText(context, HealthEffect.TestScheduled(null), format, zone))
            .isEqualTo(context.getString(R.string.health_test_failed))
        assertThat(healthEffectText(context, HealthEffect.RetryFailed, format, zone))
            .isEqualTo(context.getString(R.string.health_retry_failed))
        assertThat(healthEffectText(context, HealthEffect.SaveFailed, format, zone))
            .isEqualTo(context.getString(R.string.health_save_failed))
        assertThat(healthEffectText(context, HealthEffect.OpenFix(HealthItem.OVERLAY), format, zone)).isNull()
    }

    @Test
    fun `status maps to design system`() {
        assertThat(HealthStatus.entries.map { it.toUi() })
            .containsExactly(HealthStatusUi.Ok, HealthStatusUi.Problem, HealthStatusUi.Unconfirmed)
            .inOrder()
    }
}
