package com.antbtv.balarm.feature.missions.api

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Превью миссии (FR-MIS-5): прогресс, «Закрыть», сброс по бездействию, «Готово!» и автозакрытие. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class MissionPreviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var closed = 0

    /** Часы — вручную: `waitForIdle` с автопрокруткой промотал бы таймер бездействия. */
    private fun show(config: String = "cfg") {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BalarmTheme {
                MissionPreviewScreen(
                    definition = PreviewFake,
                    config = config,
                    seed = SEED,
                    onClose = { closed++ },
                    idleTimeout = IDLE,
                )
            }
        }
        frames()
    }

    private fun frames() = repeat(3) { composeRule.mainClock.advanceTimeByFrame() }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    @Test
    fun `the mission runs as a preview with the normalized config and its seed`() {
        show(config = " cfg ")

        node(PreviewFake.INFO).assertTextEquals("seed=$SEED preview=true reset=0 config=cfg")
        composeRule.onNodeWithText("1/3").assertIsDisplayed()
    }

    @Test
    fun `close button closes the preview and is a large tap target`() {
        show()

        node(MissionPreviewTestTags.CLOSE)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()
        frames()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `inactivity resets the current task, touches postpone the reset`() {
        show()

        composeRule.mainClock.advanceTimeBy(IDLE.toMillis() - STEP)
        node(PreviewFake.TOUCH).performClick()
        frames()
        composeRule.mainClock.advanceTimeBy(IDLE.toMillis() - STEP)
        node(PreviewFake.INFO).assertTextEquals("seed=$SEED preview=true reset=0 config=cfg")

        composeRule.mainClock.advanceTimeBy(2 * STEP)
        node(PreviewFake.INFO).assertTextEquals("seed=$SEED preview=true reset=1 config=cfg")

        // Без нового касания второго сброса нет.
        composeRule.mainClock.advanceTimeBy(2 * IDLE.toMillis())
        node(PreviewFake.INFO).assertTextEquals("seed=$SEED preview=true reset=1 config=cfg")
    }

    @Test
    fun `solving shows done and closes the preview after a short pause`() {
        show()

        node(PreviewFake.SOLVE).performClick()
        frames()
        node(MissionPreviewTestTags.DONE).assertIsDisplayed()
        node(PreviewFake.INFO).assertDoesNotExist()
        assertThat(closed).isEqualTo(0)

        composeRule.mainClock.advanceTimeBy(PREVIEW_DONE_DELAY.toMillis() + STEP)
        assertThat(closed).isEqualTo(1)
    }

    private companion object {
        const val SEED = 42L
        const val STEP = 100L
        val IDLE: Duration = Duration.ofSeconds(15)
    }
}

/** Миссия для тестов превью: показывает seed/режим/сброс/конфиг, «touch» — касание, «solve» — победа. */
private object PreviewFake : MissionDefinition {
    const val INFO = "preview_fake_info"
    const val TOUCH = "preview_fake_touch"
    const val SOLVE = "preview_fake_solve"

    override val type = MissionTypeId("fake")
    override val feature = Feature.MISSION_MATH
    override val titleRes = R.string.mission_preview_close

    override fun defaultConfig() = "cfg"

    override fun normalize(config: String) = config.trim()

    override fun summary(context: Context, config: String) = config

    @Composable
    override fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier) = Unit

    @Composable
    override fun Content(config: String, session: MissionSession, modifier: Modifier) {
        LaunchedEffect(session.seed) { session.onProgress(1, 3) }
        Column(modifier) {
            Text(
                "seed=${session.seed} preview=${session.isPreview} reset=${session.resetSignal} config=$config",
                Modifier.testTag(INFO),
            )
            Button(onClick = session.onInteraction, modifier = Modifier.testTag(TOUCH)) { Text("touch") }
            Button(onClick = session.onCompleted, modifier = Modifier.testTag(SOLVE)) { Text("solve") }
        }
    }
}
