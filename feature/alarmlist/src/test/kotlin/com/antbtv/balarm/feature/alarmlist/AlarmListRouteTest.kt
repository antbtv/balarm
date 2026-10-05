package com.antbtv.balarm.feature.alarmlist

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.AlarmCardTestTags
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.format.alarmRingsInText
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Сквозной путь Route → реальный [AlarmListViewModel] → реальный движок на фейках `:core:domain` → поток БД → экран.
 * Часы стоят: минутный тик ждёт `delay` на главном лупере Robolectric и сам не срабатывает.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AlarmListRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:00").atZone(moscow).toInstant(), moscow)
    private val database = FakeAlarmRepository()
    private val repository = GatedRepository(database)
    private val scheduler = FakeAlarmScheduler()
    private val engine = testEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, RecordingEventLog())

    /** Запись «вкл/выкл» в БД ждёт [gate]: видно, что экран не переключился раньше записи. */
    private class GatedRepository(private val delegate: AlarmRepository) : AlarmRepository by delegate {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun setEnabled(id: AlarmId, enabled: Boolean) {
            gate?.await()
            delegate.setEnabled(id, enabled)
        }
    }

    private fun save(alarm: Alarm): AlarmId = runBlocking { engine.save(alarm).id }

    private fun showRoute() {
        val viewModel = AlarmListViewModel(repository, engine, clock)
        composeRule.setContent {
            BalarmTheme { AlarmListRoute(onAddAlarm = {}, onOpenAlarm = {}, viewModel = viewModel) }
        }
    }

    private fun switch(id: AlarmId): SemanticsNodeInteraction = composeRule.onNode(
        hasTestTag(AlarmCardTestTags.SWITCH) and hasAnyAncestor(hasTestTag(AlarmListTestTags.card(id))),
    )

    @Test
    fun `switch turns on only after the database write, then the toast says when it rings`() {
        val id = save(Alarm(time = LocalTime.of(6, 30), enabled = false))
        showRoute()
        switch(id).assertIsOff()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }

        switch(id).performClick()

        // Запись ещё не прошла: тумблер не оптимистичен, в AlarmManager пусто, тоста нет.
        switch(id).assertIsOff()
        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)

        gate.complete(Unit)
        composeRule.waitForIdle()

        switch(id).assertIsOn()
        assertThat(scheduler.scheduled).containsKey(id)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(alarmRingsInText(context, TimeUntil(days = 0, hours = 1, minutes = 30)))
    }

    @Test
    fun `switching off cancels the system alarm without a toast`() {
        val id = save(Alarm(time = LocalTime.of(6, 30)))
        showRoute()
        switch(id).assertIsOn()

        switch(id).performClick()
        composeRule.waitForIdle()

        switch(id).assertIsOff()
        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `confirmed delete removes the card and the system alarm`() {
        val keep = save(Alarm(time = LocalTime.of(7, 0)))
        val id = save(Alarm(time = LocalTime.of(6, 30)))
        showRoute()

        composeRule.onNode(
            hasTestTag(AlarmCardTestTags.CARD) and hasAnyAncestor(hasTestTag(AlarmListTestTags.card(id))),
        ).performTouchInput { longClick() }
        composeRule.onNodeWithTag(AlarmListTestTags.MENU_DELETE).performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AlarmListTestTags.card(id)).assertDoesNotExist()
        composeRule.onNodeWithTag(AlarmListTestTags.card(keep)).assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
        assertThat(scheduler.cancelled).contains(id)
        assertThat(runBlocking { database.get(id) }).isNull()
    }
}
