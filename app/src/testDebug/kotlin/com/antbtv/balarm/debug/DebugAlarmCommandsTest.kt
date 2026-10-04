package com.antbtv.balarm.debug

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.model.Alarm
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.DayOfWeek
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DebugAlarmCommandsTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var commands: DebugAlarmCommands

    @Inject lateinit var repository: FakeAlarmRepository

    @Before
    fun setUp() = hilt.inject()

    @Test
    fun `schedule in rounds up to the next whole minute and plans the alarm`() = runTest {
        val id = commands.scheduleIn(Duration.ofSeconds(30), "Work", emptySet())

        val alarm = repository.get(id)!!
        assertThat(alarm.time.second).isEqualTo(0)
        assertThat(alarm.label).isEqualTo("Work")
        assertThat(repository.getRuntime(id)?.nextTriggerAt).isNotNull()
        assertThat(ShadowLog.getLogsForTag("Balarm").map { it.msg }.single { it.startsWith("DEBUG_SCHEDULED") })
            .contains("id=${id.value}")
    }

    @Test
    fun `label is cut by code points not in the middle of an emoji`() = runTest {
        val emoji = "\uD83D\uDE00"

        val id = commands.scheduleIn(Duration.ofMinutes(2), emoji.repeat(Alarm.MAX_LABEL_LENGTH + 5), emptySet())

        assertThat(repository.get(id)!!.label).isEqualTo(emoji.repeat(Alarm.MAX_LABEL_LENGTH))
    }

    @Test
    fun `list and clear all report every alarm`() = runTest {
        commands.scheduleIn(Duration.ofMinutes(2), "", emptySet())
        commands.scheduleIn(Duration.ofMinutes(3), "", setOf(DayOfWeek.MONDAY))

        commands.list()
        commands.clearAll()

        val logs = ShadowLog.getLogsForTag("Balarm").map { it.msg }
        assertThat(logs).contains("DEBUG_LIST count=2")
        assertThat(logs.count { it.startsWith("DEBUG_ALARM") }).isEqualTo(2)
        assertThat(logs).contains("DEBUG_CLEARED count=2")
        assertThat(repository.loadAll()).isEmpty()
    }

    @Test
    fun `dismiss without a ringing alarm is reported, not silently dropped`() {
        commands.dismiss()

        assertThat(ShadowLog.getLogsForTag("Balarm").map { it.msg }).contains("DEBUG_DISMISS ignored=no_ringing")
    }

    @Test
    fun `days are parsed from short names`() {
        assertThat(DebugAlarmCommands.parseDays("MON, tu,SUN,xx,"))
            .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)
        assertThat(DebugAlarmCommands.parseDays(null)).isEmpty()
    }
}
