package com.antbtv.balarm.feature.alarmlist

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.alarmRingsInText
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class AlarmListEffectsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val until = TimeUntil(days = 0, hours = 7, minutes = 12)

    @Test
    @Config(qualifiers = "en-rUS")
    fun `english messages`() {
        assertThat(alarmListEffectText(context, AlarmListEffect.RingsIn(until)))
            .isEqualTo(alarmRingsInText(context, until))
        assertThat(alarmListEffectText(context, AlarmListEffect.RingsIn(until))).startsWith("Alarm rings in ")
        assertThat(alarmListEffectText(context, AlarmListEffect.ScheduleFailed))
            .isEqualTo("Couldn't schedule the alarm")
        assertThat(alarmListEffectText(context, AlarmListEffect.DisableFailed))
            .isEqualTo("Couldn't turn off the alarm")
        assertThat(alarmListEffectText(context, AlarmListEffect.DeleteFailed))
            .isEqualTo("Couldn't delete the alarm")
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian messages`() {
        assertThat(alarmListEffectText(context, AlarmListEffect.RingsIn(until))).startsWith("Будильник зазвонит через ")
        assertThat(alarmListEffectText(context, AlarmListEffect.ScheduleFailed))
            .isEqualTo("Не удалось запланировать будильник")
        assertThat(alarmListEffectText(context, AlarmListEffect.DisableFailed))
            .isEqualTo("Не удалось выключить будильник")
        assertThat(alarmListEffectText(context, AlarmListEffect.DeleteFailed))
            .isEqualTo("Не удалось удалить будильник")
    }

    // Что тост создан именно на applicationContext (переживает уход с экрана), Robolectric не проверяет:
    // ShadowToast не хранит контекст. Это держится на коде AlarmListEffectsHandler и ревью.
    @Test
    fun `every effect becomes a toast, a new one replaces the previous`() {
        composeRule.setContent {
            AlarmListEffectsHandler(flowOf(AlarmListEffect.RingsIn(until), AlarmListEffect.DeleteFailed))
        }
        composeRule.waitForIdle()

        val toasts = shadowOf(RuntimeEnvironment.getApplication()).shownToasts
        assertThat(toasts).hasSize(2)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Couldn't delete the alarm")
        assertThat(shadowOf(toasts[0]).isCancelled).isTrue()
        assertThat(shadowOf(toasts[1]).isCancelled).isFalse()
    }
}
