package com.antbtv.balarm.feature.ringing

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.feature.ringing.ui.RingingTestTags
import com.antbtv.balarm.feature.ringing.ui.RingingViewModel
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.toJavaDuration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class RingingActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val fakeController = FakeRingingController(ringing())

    @BindValue
    @JvmField
    val controller: RingingController = fakeController

    @BindValue
    @JvmField
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-03T06:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `dismiss button sends the command to the controller`() {
        ActivityScenario.launch(RingingActivity::class.java).use {
            composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertIsDisplayed().performClick()

            assertThat(fakeController.dismissCalls).isEqualTo(1)
        }
    }

    @Test
    fun `snooze button is hidden when the controller forbids snooze`() {
        fakeController.state.value = ringing(canSnooze = false)

        ActivityScenario.launch(RingingActivity::class.java).use {
            composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertIsDisplayed()
            composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertDoesNotExist()
        }
    }

    @Test
    fun `idle after ringing finishes the activity`() {
        val activity = Robolectric.buildActivity(RingingActivity::class.java).setup().get()
        assertThat(activity.isFinishing).isFalse()

        fakeController.state.value = RingingState.Idle
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `idle without a ringing session finishes after the grace period`() {
        fakeController.state.value = RingingState.Idle
        val activity = Robolectric.buildActivity(RingingActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.isFinishing).isFalse()

        shadowOf(Looper.getMainLooper()).idleFor(RingingViewModel.WAIT_FOR_RINGING.toJavaDuration())

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `back and volume keys are absorbed`() {
        val activity = Robolectric.buildActivity(RingingActivity::class.java).setup().get()

        activity.onBackPressedDispatcher.onBackPressed()
        val volumeDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        val volumeUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)

        assertThat(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, volumeDown)).isTrue()
        assertThat(activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, volumeUp)).isTrue()
        assertThat(activity.isFinishing).isFalse()
        assertThat(fakeController.dismissCalls).isEqualTo(0)
        assertThat(fakeController.snoozeCalls).isEqualTo(0)
    }

    @Test
    fun `window shows over the lock screen, wakes the screen and keeps it on`() {
        val activity = Robolectric.buildActivity(RingingActivity::class.java).setup().get()

        assertThat(shadowOf(activity).showWhenLocked).isTrue()
        assertThat(shadowOf(activity).turnScreenOn).isTrue()
        val flags = activity.window.attributes.flags
        assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isNotEqualTo(0)
    }

    @Test
    fun `manifest entry is direct boot aware, private, single task and hidden from recents`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        // Robolectric 4.17 не реализует перегрузку с ComponentInfoFlags для activity.
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, RingingActivity::class.java),
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
        )

        assertThat(info.directBootAware).isTrue()
        assertThat(info.exported).isFalse()
        assertThat(info.launchMode).isEqualTo(ActivityInfo.LAUNCH_SINGLE_TASK)
        assertThat(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS).isNotEqualTo(0)
        assertThat(info.taskAffinity).endsWith(".ringing")
    }
}
