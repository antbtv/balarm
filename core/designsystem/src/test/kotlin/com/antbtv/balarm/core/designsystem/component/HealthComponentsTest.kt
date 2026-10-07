package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M3: HealthBanner, HealthStatusRow, OnboardingStepLayout, BalarmNavigationBar. */
@RunWith(AndroidJUnit4::class)
class HealthComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(fontScale: Float? = null, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled) { BalarmTheme(content = content) }
        }
    }

    // region HealthBanner

    @Test
    fun `banner is one TalkBack button reading the warning with the action as click label`() {
        var clicks = 0
        setContent { HealthBanner(text = BANNER_TEXT, actionLabel = FIX, onClick = { clicks++ }) }

        composeRule.onNodeWithTag(HealthBannerTestTags.BANNER)
            .assertContentDescriptionEquals(BANNER_TEXT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher("click label is \"$FIX\"") { it.config[SemanticsActions.OnClick].label == FIX })
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()

        assertThat(clicks).isEqualTo(1)
        // Подпись действия видна, но отдельным узлом TalkBack не читается.
        composeRule.onNodeWithText(FIX).assertDoesNotExist()
        composeRule.onNodeWithText(FIX, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `banner TalkBack click opens health`() {
        var clicks = 0
        setContent { HealthBanner(text = BANNER_TEXT, actionLabel = FIX, onClick = { clicks++ }) }

        composeRule.onNodeWithTag(HealthBannerTestTags.BANNER).performSemanticsAction(SemanticsActions.OnClick)

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `RU banner text is unclipped at font scale 2 on a narrow screen`() {
        setContent(fontScale = 2f) {
            Box(Modifier.padding(BalarmDimens.ScreenPadding)) {
                HealthBanner(text = BANNER_TEXT_RU, actionLabel = FIX_RU, onClick = {})
            }
        }

        val banner = composeRule.onNodeWithTag(HealthBannerTestTags.BANNER).getUnclippedBoundsInRoot()
        listOf(BANNER_TEXT_RU, FIX_RU).forEach { text ->
            val node = composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertTextNotClipped(node, text)
            val bounds = node.getUnclippedBoundsInRoot()
            assertThat(bounds.left).isAtLeast(banner.left)
            assertThat(bounds.right).isAtMost(banner.right)
            assertThat(bounds.bottom).isAtMost(banner.bottom)
        }
    }

    // endregion

    // region HealthStatusRow

    @Test
    fun `status row reads title, status and description as one node`() {
        setContent {
            HealthStatusRow(
                title = NOTIFICATIONS,
                description = WHY,
                status = HealthStatusUi.Problem,
                actionLabel = FIX,
                onAction = {},
            )
        }

        composeRule.onNodeWithTag(HealthStatusRowTestTags.INFO)
            .assertContentDescriptionEquals("$NOTIFICATIONS. Needs attention. $WHY")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        composeRule.onNodeWithText(NOTIFICATIONS).assertDoesNotExist()
        composeRule.onNodeWithText(NOTIFICATIONS, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `status row describes every status`() {
        val expected = mapOf(
            HealthStatusUi.Ok to "$NOTIFICATIONS. OK",
            HealthStatusUi.Problem to "$NOTIFICATIONS. Needs attention",
            HealthStatusUi.Unconfirmed to "$NOTIFICATIONS. Not confirmed",
        )
        var status by mutableStateOf(HealthStatusUi.Ok)
        setContent {
            HealthStatusRow(
                title = NOTIFICATIONS,
                description = null,
                status = status,
                actionLabel = null,
                onAction = {},
            )
        }

        expected.forEach { (value, description) ->
            status = value
            composeRule.onNodeWithTag(HealthStatusRowTestTags.INFO).assertContentDescriptionEquals(description)
        }
    }

    @Test
    @Config(qualifiers = "ru")
    fun `status row reads Russian status words`() {
        setContent {
            HealthStatusRow(
                title = "Уведомления",
                description = null,
                status = HealthStatusUi.Problem,
                actionLabel = FIX_RU,
                onAction = {},
            )
        }

        composeRule.onNodeWithTag(HealthStatusRowTestTags.INFO)
            .assertContentDescriptionEquals("Уведомления. Требует внимания")
        composeRule.onNodeWithTag(HealthStatusRowTestTags.ACTION)
            .assertContentDescriptionEquals("Исправить: Уведомления")
    }

    @Test
    fun `status row action names the item, is a 48dp button and clicks`() {
        var clicks = 0
        setContent {
            HealthStatusRow(
                title = NOTIFICATIONS,
                description = WHY,
                status = HealthStatusUi.Problem,
                actionLabel = FIX,
                onAction = { clicks++ },
            )
        }

        composeRule.onNodeWithTag(HealthStatusRowTestTags.ACTION)
            .assertContentDescriptionEquals("$FIX: $NOTIFICATIONS")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `status row without action label has no button`() {
        setContent {
            HealthStatusRow(
                title = NOTIFICATIONS,
                description = WHY,
                status = HealthStatusUi.Ok,
                actionLabel = null,
                onAction = {},
            )
        }

        composeRule.onNodeWithTag(HealthStatusRowTestTags.ACTION).assertDoesNotExist()
        composeRule.onNodeWithTag(HealthStatusRowTestTags.ROW).assertHeightIsAtLeast(BalarmDimens.ListRowMinHeight)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `RU status row texts are unclipped at font scale 2 on a narrow screen`() {
        val title = "Автозапуск и фоновая работа"
        val why = "На некоторых телефонах фоновую работу нужно разрешить отдельно."
        val action = "Открыть настройки"
        setContent(fontScale = 2f) {
            Box(Modifier.padding(BalarmDimens.ScreenPadding)) {
                HealthStatusRow(
                    title = title,
                    description = why,
                    status = HealthStatusUi.Unconfirmed,
                    actionLabel = action,
                    onAction = {},
                )
            }
        }

        val row = composeRule.onNodeWithTag(HealthStatusRowTestTags.ROW).getUnclippedBoundsInRoot()
        listOf(title, why, action).forEach { text ->
            val node = composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertTextNotClipped(node, text)
            val bounds = node.getUnclippedBoundsInRoot()
            assertThat(bounds.left).isAtLeast(row.left)
            assertThat(bounds.right).isAtMost(row.right)
            assertThat(bounds.bottom).isAtMost(row.bottom)
        }
    }

    // endregion

    // region OnboardingStepLayout

    @Composable
    private fun Step(
        secondaryLabel: String? = LATER,
        currentStep: Int = 2,
        calls: MutableList<String> = mutableListOf(),
        title: String = STEP_TITLE,
        why: String = WHY,
        primaryLabel: String = ALLOW,
        extra: @Composable () -> Unit = {},
    ) {
        OnboardingStepLayout(
            illustration = { OnboardingIllustration(icon = BalarmIcons.Alarm) },
            title = title,
            why = why,
            primaryLabel = primaryLabel,
            onPrimary = { calls += "primary" },
            secondaryLabel = secondaryLabel,
            onSecondary = { calls += "secondary" },
            currentStep = currentStep,
            totalSteps = TOTAL_STEPS,
        ) { extra() }
    }

    @Test
    fun `onboarding step exposes progress, heading and both buttons`() {
        val calls = mutableListOf<String>()
        setContent { Step(calls = calls, extra = { Text(EXTRA) }) }

        composeRule.onNodeWithTag(OnboardingStepTestTags.PROGRESS)
            .assertContentDescriptionEquals("Step 2 of 7")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        composeRule.onNodeWithTag(OnboardingStepTestTags.TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(hasText(STEP_TITLE))
        composeRule.onNodeWithText(WHY).assertIsDisplayed()
        composeRule.onNodeWithText(EXTRA).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(OnboardingStepTestTags.PRIMARY)
            .assert(hasText(ALLOW))
            .assertHeightIsAtLeast(BalarmDimens.ButtonHeight)
            .performClick()
        composeRule.onNodeWithTag(OnboardingStepTestTags.SECONDARY)
            .assert(hasText(LATER))
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()

        assertThat(calls).containsExactly("primary", "secondary").inOrder()
    }

    @Test
    fun `onboarding step without secondary label hides the button`() {
        setContent { Step(secondaryLabel = null) }

        composeRule.onNodeWithTag(OnboardingStepTestTags.SECONDARY).assertDoesNotExist()
        composeRule.onNodeWithTag(OnboardingStepTestTags.PRIMARY).assertIsDisplayed()
    }

    @Test
    fun `onboarding step clamps the current step into range`() {
        setContent { Step(currentStep = TOTAL_STEPS + 2) }

        composeRule.onNodeWithTag(OnboardingStepTestTags.PROGRESS).assertContentDescriptionEquals("Step 7 of 7")
    }

    @Test
    @Config(qualifiers = "ru")
    fun `onboarding progress reads in Russian`() {
        setContent { Step(currentStep = 3) }

        composeRule.onNodeWithTag(OnboardingStepTestTags.PROGRESS).assertContentDescriptionEquals("Шаг 3 из 7")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `RU onboarding buttons stay on screen and unclipped at font scale 2 on a narrow screen`() {
        val primary = "Открыть настройки"
        val secondary = "Продолжить без этого"
        setContent(fontScale = 2f) {
            Step(
                title = "Полноэкранные уведомления",
                why = "Без этого экран звонка не откроется на заблокированном телефоне, " +
                    "и вы можете проспать. Разрешите полноэкранные уведомления в настройках.",
                primaryLabel = primary,
                secondaryLabel = secondary,
            )
        }

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        listOf(primary, secondary).forEach { text ->
            val node = composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertTextNotClipped(node, text)
            val bounds = node.getUnclippedBoundsInRoot()
            assertThat(bounds.bottom).isAtMost(root.bottom)
            assertThat(bounds.right).isAtMost(root.right)
        }
        // Заголовок вверху прокручиваемой части виден и не обрезан.
        val title = composeRule.onNodeWithText("Полноэкранные уведомления", useUnmergedTree = true)
        assertTextNotClipped(title, "Полноэкранные уведомления")
        composeRule.onNodeWithTag(OnboardingStepTestTags.PROGRESS).assertIsDisplayed()
    }

    // endregion

    // region BalarmNavigationBar

    private val navItems = listOf(NavBarItem(ALARMS, BalarmIcons.Alarm), NavBarItem(SETTINGS, BalarmIcons.Settings))

    @Test
    fun `navigation bar tabs are selectable tabs with labels and report the tapped index`() {
        val selected = mutableListOf<Int>()
        setContent { BalarmNavigationBar(items = navItems, selectedIndex = 0, onSelect = { selected += it }) }

        val alarms = composeRule.onNodeWithTag(NavigationBarTestTags.item(0))
        val settings = composeRule.onNodeWithTag(NavigationBarTestTags.item(1))
        alarms
            .assertIsSelected()
            .assert(hasText(ALARMS))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .assertWidthIsAtLeast(BalarmDimens.MinTouch)
        settings
            .assertIsNotSelected()
            .assert(hasText(SETTINGS))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

        settings.performClick()
        alarms.performClick()

        assertThat(selected).containsExactly(1, 0).inOrder()
    }

    @Test
    fun `navigation bar with out of range index selects nothing`() {
        setContent { BalarmNavigationBar(items = navItems, selectedIndex = -1, onSelect = {}) }

        composeRule.onNodeWithTag(NavigationBarTestTags.item(0)).assertIsNotSelected()
        composeRule.onNodeWithTag(NavigationBarTestTags.item(1)).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `RU navigation labels are unclipped at font scale 2 on a narrow screen`() {
        val items = listOf(NavBarItem(ALARMS_RU, BalarmIcons.Alarm), NavBarItem(SETTINGS_RU, BalarmIcons.Settings))
        setContent(fontScale = 2f) { BalarmNavigationBar(items = items, selectedIndex = 0, onSelect = {}) }

        val bar = composeRule.onNodeWithTag(NavigationBarTestTags.BAR).getUnclippedBoundsInRoot()
        listOf(ALARMS_RU, SETTINGS_RU).forEach { text ->
            val node = composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertTextNotClipped(node, text)
            val bounds = node.getUnclippedBoundsInRoot()
            assertWithMessage("\"$text\" inside the bar").that(bounds.top).isAtLeast(bar.top)
            assertWithMessage("\"$text\" inside the bar").that(bounds.bottom).isAtMost(bar.bottom)
            assertThat(bounds.left).isAtLeast(bar.left)
            assertThat(bounds.right).isAtMost(bar.right)
        }
    }

    // endregion

    /**
     * Текст виден целиком: нет переполнения по высоте, нет многоточия, каждая строка не шире узла.
     * `hasVisualOverflow` не используется: у текста уже своих ограничений (обёртка по содержимому) ширина абзаца
     * равна ограничению, а размер узла — фактической ширине строки, и `didOverflowWidth` ложно срабатывает.
     * У центрированного текста границы строки округляются до пикселя наружу — допуск [LINE_ROUNDING_PX].
     */
    private fun assertTextNotClipped(node: SemanticsNodeInteraction, text: String) {
        val semantics = node.fetchSemanticsNode()
        val layout = textLayout(node)
        assertWithMessage("\"$text\" overflows its node in height").that(layout.didOverflowHeight).isFalse()
        for (line in 0 until layout.lineCount) {
            assertWithMessage("\"$text\" line $line is ellipsized").that(layout.isLineEllipsized(line)).isFalse()
            assertWithMessage("\"$text\" line $line is wider than its node")
                .that(layout.getLineRight(line) - layout.getLineLeft(line))
                .isAtMost(semantics.size.width + LINE_ROUNDING_PX)
        }
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        const val BANNER_TEXT = "Alarm may not ring"
        const val BANNER_TEXT_RU = "Будильник может не сработать"
        const val FIX = "Fix"
        const val FIX_RU = "Исправить"
        const val NOTIFICATIONS = "Notifications"
        const val WHY = "Without notifications the ringing screen cannot appear."
        const val STEP_TITLE = "Allow notifications"
        const val ALLOW = "Allow"
        const val LATER = "Later"
        const val EXTRA = "dontkillmyapp.com"
        const val TOTAL_STEPS = 7
        const val ALARMS = "Alarms"
        const val SETTINGS = "Settings"
        const val ALARMS_RU = "Будильники"
        const val SETTINGS_RU = "Настройки"
        const val LINE_ROUNDING_PX = 1f
    }
}
