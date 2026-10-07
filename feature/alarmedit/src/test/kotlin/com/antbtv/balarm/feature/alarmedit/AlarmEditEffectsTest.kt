package com.antbtv.balarm.feature.alarmedit

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.ScheduleResult
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.alarmRingsInText
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class AlarmEditEffectsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val until = TimeUntil(days = 0, hours = 7, minutes = 12)
    private val at = Instant.parse("2026-09-28T02:00:05Z")
    private val result = ScheduleResult(id = AlarmId(1), nextTriggerAt = at, scheduled = true)

    @Test
    @Config(qualifiers = "en-rUS")
    fun `english messages`() {
        assertThat(alarmEditEffectText(context, AlarmEditEffect.Saved(result, until)))
            .isEqualTo(alarmRingsInText(context, until))
        assertThat(alarmEditEffectText(context, AlarmEditEffect.Saved(result, until))).startsWith("Alarm rings in ")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.Saved(result.copy(scheduled = false), null)))
            .isEqualTo("Couldn't schedule the alarm")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.TestScheduled(at)))
            .isEqualTo("Test alarm in 5 seconds")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.TestScheduled(null)))
            .isEqualTo("Couldn't schedule the test alarm")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.SaveFailed)).isEqualTo("Couldn't save the alarm")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.DeleteFailed)).isEqualTo("Couldn't delete the alarm")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.LoadFailed)).isEqualTo("Couldn't open the alarm")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.Close)).isNull()
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian messages`() {
        assertThat(alarmEditEffectText(context, AlarmEditEffect.Saved(result, until)))
            .startsWith("Будильник зазвонит через ")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.Saved(result.copy(scheduled = false), null)))
            .isEqualTo("Не удалось запланировать будильник")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.TestScheduled(at)))
            .isEqualTo("Тестовый звонок через 5 секунд")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.TestScheduled(null)))
            .isEqualTo("Не удалось запланировать тестовый звонок")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.SaveFailed)).isEqualTo("Не удалось сохранить будильник")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.DeleteFailed)).isEqualTo("Не удалось удалить будильник")
        assertThat(alarmEditEffectText(context, AlarmEditEffect.LoadFailed)).isEqualTo("Не удалось открыть будильник")
    }

    @Test
    fun `only saved, close and load failure close the editor`() {
        assertThat(AlarmEditEffect.Saved(result, until).closesScreen).isTrue()
        assertThat(AlarmEditEffect.Close.closesScreen).isTrue()
        assertThat(AlarmEditEffect.LoadFailed.closesScreen).isTrue()
        assertThat(AlarmEditEffect.TestScheduled(at).closesScreen).isFalse()
        assertThat(AlarmEditEffect.SaveFailed.closesScreen).isFalse()
        assertThat(AlarmEditEffect.DeleteFailed.closesScreen).isFalse()
    }

    // Что тост создан на applicationContext, Robolectric не проверяет (ShadowToast не хранит контекст) — ревью.
    @Test
    @Config(qualifiers = "en-rUS")
    fun `effects become toasts one at a time and close the editor once`() {
        var closed = 0
        composeRule.setContent {
            AlarmEditEffectsHandler(
                effects = flowOf(
                    AlarmEditEffect.TestScheduled(at),
                    AlarmEditEffect.SaveFailed,
                    AlarmEditEffect.Saved(result, until),
                    AlarmEditEffect.Close,
                ),
                onClose = { closed++ },
            )
        }
        composeRule.waitForIdle()

        val toasts = shadowOf(RuntimeEnvironment.getApplication()).shownToasts
        assertThat(toasts).hasSize(3)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(alarmRingsInText(context, until))
        assertThat(toasts.take(2).map { shadowOf(it).isCancelled }).containsExactly(true, true)
        assertThat(shadowOf(toasts[2]).isCancelled).isFalse()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `load failure toasts and closes`() {
        var closed = 0
        composeRule.setContent {
            AlarmEditEffectsHandler(effects = flowOf(AlarmEditEffect.LoadFailed), onClose = { closed++ })
        }
        composeRule.waitForIdle()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Couldn't open the alarm")
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `close alone closes without a toast`() {
        var closed = 0
        composeRule.setContent {
            AlarmEditEffectsHandler(effects = flowOf(AlarmEditEffect.Close), onClose = { closed++ })
        }
        composeRule.waitForIdle()

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(closed).isEqualTo(1)
    }
}
